-- The app's membership rules, the member list and dashboard figures, and the writes that must happen
-- together: setting up a gym, inviting and joining staff, selling and bulk-renewing memberships.
-- Mirrors MembershipRules.kt, ObserveDashboardUseCase and the Room DAOs.
--
-- Errors the app should recognise are raised with a short message key:
--   not_signed_in, already_in_gym, not_owner, invalid_invite, member_not_found, plan_not_found,
--   overpayment (detail holds the amount still due).

-- Membership rules ---------------------------------------------------------------------------------

-- "Today" where the signed-in user's gym is.
create function private.gym_today() returns date
language sql stable security definer
set search_path = ''
as $$
  select (now() at time zone coalesce(
    (select g.timezone from public.gyms g where g.id = private.current_gym_id()),
    'Asia/Kolkata'
  ))::date
$$;

-- Inclusive last day, e.g. a 1-month plan from 15 Jan ends on 14 Feb.
create function private.membership_end_date(start_date date, duration_value int, duration_unit public.duration_unit)
returns date
language sql immutable
set search_path = ''
as $$
  select (start_date + case duration_unit
    when 'DAYS' then make_interval(days => duration_value)
    when 'MONTHS' then make_interval(months => duration_value)
  end)::date - 1
$$;

-- Renewals continue from a running membership; otherwise the new one starts today.
create function private.renewal_start_date(member_id uuid, today date) returns date
language sql stable
set search_path = ''
as $$
  select case when max(ms.end_date) >= today then max(ms.end_date) + 1 else today end
  from public.memberships ms
  where ms.member_id = renewal_start_date.member_id
$$;

grant execute on function private.gym_today() to authenticated;
grant execute on function private.membership_end_date(date, int, public.duration_unit) to authenticated;
grant execute on function private.renewal_start_date(uuid, date) to authenticated;

-- Payments can't exceed what is still owed. A trigger rather than a function, so it also covers payments
-- inserted directly. Locking the membership makes two staff paying at once queue rather than both pass.
create function private.check_overpayment() returns trigger
language plpgsql security definer
set search_path = ''
as $$
declare
  due bigint;
begin
  select ms.price - ms.discount into due
  from public.memberships ms
  where ms.id = new.membership_id
  for update;

  if not found then
    return new; -- the foreign key reports the missing membership
  end if;

  due := due - coalesce(
    (select sum(p.amount) from public.payments p where p.membership_id = new.membership_id and p.id <> new.id),
    0
  );
  if new.amount > due then
    raise exception 'overpayment' using detail = due::text, hint = 'Payment exceeds the amount due';
  end if;
  return new;
end;
$$;

create trigger check_overpayment before insert or update of amount, membership_id on public.payments
  for each row execute function private.check_overpayment();

-- Member list and dashboard ------------------------------------------------------------------------

-- One row per member with what the member list needs. Runs with the caller's rights, so each user
-- only ever sees their own gym. status is NONE, EXPIRED, EXPIRING_SOON (within 7 days) or ACTIVE.
create view public.member_summary with (security_invoker = true) as
select
  m.id as member_id,
  m.gym_id,
  m.name,
  m.phone,
  m.photo_path,
  m.is_archived,
  ms.latest_end_date,
  coalesce(ms.billed, 0) - coalesce(paid.total, 0) as amount_due,
  case
    when ms.latest_end_date is null then 'NONE'
    when ms.latest_end_date < private.gym_today() then 'EXPIRED'
    when ms.latest_end_date - private.gym_today() <= 7 then 'EXPIRING_SOON'
    else 'ACTIVE'
  end as status
from public.members m
left join lateral (
  select max(x.end_date) as latest_end_date, sum(x.price - x.discount) as billed
  from public.memberships x
  where x.member_id = m.id
) ms on true
left join lateral (
  select sum(p.amount) as total
  from public.payments p
  join public.memberships x on x.id = p.membership_id
  where x.member_id = m.id
) paid on true;

revoke all on public.member_summary from anon, authenticated;
grant select on public.member_summary to authenticated;

-- The Home figures for active (non-archived) members; the expiring list itself comes from member_summary.
create function public.dashboard_stats()
returns table (
  active_count int,
  expiring_soon_count int,
  expired_count int,
  members_with_dues_count int,
  total_due bigint,
  collected_this_month bigint
)
language sql stable
set search_path = ''
as $$
  with today as (
    select private.gym_today() as day
  ),
  month as (
    select date_trunc('month', day)::date as first_day,
           (date_trunc('month', day) + interval '1 month')::date - 1 as last_day
    from today
  ),
  members as (
    select * from public.member_summary where not is_archived
  )
  select
    (count(*) filter (where status in ('ACTIVE', 'EXPIRING_SOON')))::int,
    (count(*) filter (where status = 'EXPIRING_SOON'))::int,
    (count(*) filter (where status = 'EXPIRED'))::int,
    (count(*) filter (where amount_due > 0))::int,
    coalesce(sum(amount_due) filter (where amount_due > 0), 0)::bigint,
    (select coalesce(sum(p.amount), 0)::bigint
       from public.payments p, month
       where p.paid_on between month.first_day and month.last_day)
  from members
$$;

-- Gyms and staff -----------------------------------------------------------------------------------

-- Sets up a gym with the caller as its owner.
create function public.create_gym(name text, address text default null, owner_name text default null)
returns uuid
language plpgsql security definer
set search_path = ''
as $$
declare
  new_gym_id uuid;
begin
  if auth.uid() is null then
    raise exception 'not_signed_in';
  end if;
  if exists (select 1 from public.gym_staff s where s.user_id = auth.uid()) then
    raise exception 'already_in_gym';
  end if;

  insert into public.gyms (name, address)
  values (trim(create_gym.name), nullif(trim(create_gym.address), ''))
  returning id into new_gym_id;

  insert into public.gym_staff (gym_id, user_id, role, name)
  values (new_gym_id, auth.uid(), 'owner', nullif(trim(owner_name), ''));

  return new_gym_id;
end;
$$;

-- A one-time code the owner shares with someone joining the gym, e.g. "K7P4QXM2". Valid for 7 days.
create function public.create_invite(role public.staff_role default 'staff')
returns text
language plpgsql security definer
set search_path = ''
as $$
declare
  alphabet constant text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; -- no 0/O or 1/I to misread
  new_code text;
  bytes bytea;
begin
  if not private.is_owner() then
    raise exception 'not_owner';
  end if;

  loop
    bytes := extensions.gen_random_bytes(8);
    new_code := '';
    for i in 0..7 loop
      new_code := new_code || substr(alphabet, get_byte(bytes, i) % 32 + 1, 1);
    end loop;
    begin
      insert into public.gym_invites (code, gym_id, role, created_by)
      values (new_code, private.current_gym_id(), create_invite.role, auth.uid());
      return new_code;
    exception when unique_violation then
      -- Try another code.
    end;
  end loop;
end;
$$;

-- Joins the gym that issued the code. Each code works once, until it expires.
create function public.accept_invite(code text, staff_name text default null)
returns uuid
language plpgsql security definer
set search_path = ''
as $$
declare
  invite public.gym_invites;
begin
  if auth.uid() is null then
    raise exception 'not_signed_in';
  end if;
  if exists (select 1 from public.gym_staff s where s.user_id = auth.uid()) then
    raise exception 'already_in_gym';
  end if;

  select * into invite
  from public.gym_invites i
  where i.code = upper(replace(trim(accept_invite.code), '-', ''))
    and i.used_at is null
    and i.expires_at > now()
  for update;

  if not found then
    raise exception 'invalid_invite';
  end if;

  update public.gym_invites set used_by = auth.uid(), used_at = now() where gym_invites.code = invite.code;
  insert into public.gym_staff (gym_id, user_id, role, name)
  values (invite.gym_id, auth.uid(), invite.role, nullif(trim(staff_name), ''));

  return invite.gym_id;
end;
$$;

-- Memberships --------------------------------------------------------------------------------------

-- What the sell/renew form pre-fills: the renewal start date, the plan's end date for it, and the plan's price.
create function public.quote_membership(member_id uuid, plan_id uuid)
returns table (start_date date, end_date date, price bigint)
language sql stable
set search_path = ''
as $$
  select s.start_date,
         private.membership_end_date(s.start_date, p.duration_value, p.duration_unit),
         p.default_price
  from public.plans p,
       lateral (select private.renewal_start_date(quote_membership.member_id, private.gym_today()) as start_date) s
  where p.id = quote_membership.plan_id
    and exists (select 1 from public.members m where m.id = quote_membership.member_id)
$$;

-- Sells a membership or renewal. Anything left null falls back to the quote. A first payment, dated
-- today, is saved in the same transaction. Runs with the caller's rights, so the usual rules apply.
create function public.sell_membership(
  member_id uuid,
  plan_id uuid,
  start_date date default null,
  end_date date default null,
  price bigint default null,
  discount bigint default 0,
  notes text default null,
  first_payment_amount bigint default null,
  first_payment_mode public.payment_mode default 'CASH'
)
returns uuid
language plpgsql
set search_path = ''
as $$
declare
  plan public.plans;
  member_gym_id uuid;
  start_on date;
  new_membership_id uuid;
begin
  select m.gym_id into member_gym_id from public.members m where m.id = sell_membership.member_id;
  if not found then
    raise exception 'member_not_found';
  end if;
  select * into plan from public.plans p where p.id = sell_membership.plan_id;
  if not found then
    raise exception 'plan_not_found';
  end if;

  start_on := coalesce(sell_membership.start_date, private.renewal_start_date(sell_membership.member_id, private.gym_today()));

  insert into public.memberships (gym_id, member_id, plan_id, plan_name, start_date, end_date, price, discount, notes)
  values (
    member_gym_id,
    sell_membership.member_id,
    plan.id,
    plan.name,
    start_on,
    coalesce(sell_membership.end_date, private.membership_end_date(start_on, plan.duration_value, plan.duration_unit)),
    coalesce(sell_membership.price, plan.default_price),
    coalesce(sell_membership.discount, 0),
    sell_membership.notes
  )
  returning id into new_membership_id;

  if first_payment_amount > 0 then
    insert into public.payments (gym_id, membership_id, amount, mode, paid_on)
    values (member_gym_id, new_membership_id, first_payment_amount, first_payment_mode, private.gym_today());
  end if;

  return new_membership_id;
end;
$$;

-- Renews many members on one plan at the plan's price, each continuing from their own end date if
-- still running. Archived members are skipped. With mark_paid, each also gets a full payment dated today.
-- Returns how many were renewed.
create function public.bulk_renew(
  member_ids uuid[],
  plan_id uuid,
  mark_paid boolean default false,
  payment_mode public.payment_mode default 'CASH'
)
returns int
language plpgsql
set search_path = ''
as $$
declare
  plan public.plans;
  today date := private.gym_today();
  member record;
  start_on date;
  new_membership_id uuid;
  renewed int := 0;
begin
  select * into plan from public.plans p where p.id = bulk_renew.plan_id;
  if not found then
    raise exception 'plan_not_found';
  end if;

  for member in
    select m.id, m.gym_id from public.members m
    where m.id = any (bulk_renew.member_ids) and not m.is_archived
    order by m.name
  loop
    start_on := private.renewal_start_date(member.id, today);

    insert into public.memberships (gym_id, member_id, plan_id, plan_name, start_date, end_date, price)
    values (
      member.gym_id, member.id, plan.id, plan.name, start_on,
      private.membership_end_date(start_on, plan.duration_value, plan.duration_unit),
      plan.default_price
    )
    returning id into new_membership_id;

    if mark_paid and plan.default_price > 0 then
      insert into public.payments (gym_id, membership_id, amount, mode, paid_on)
      values (member.gym_id, new_membership_id, plan.default_price, bulk_renew.payment_mode, today);
    end if;

    renewed := renewed + 1;
  end loop;

  return renewed;
end;
$$;

-- Only signed-in users call these.
revoke execute on function public.dashboard_stats() from public, anon;
revoke execute on function public.create_gym(text, text, text) from public, anon;
revoke execute on function public.create_invite(public.staff_role) from public, anon;
revoke execute on function public.accept_invite(text, text) from public, anon;
revoke execute on function public.quote_membership(uuid, uuid) from public, anon;
revoke execute on function public.sell_membership(uuid, uuid, date, date, bigint, bigint, text, bigint, public.payment_mode)
  from public, anon;
revoke execute on function public.bulk_renew(uuid[], uuid, boolean, public.payment_mode) from public, anon;

grant execute on function public.dashboard_stats() to authenticated;
grant execute on function public.create_gym(text, text, text) to authenticated;
grant execute on function public.create_invite(public.staff_role) to authenticated;
grant execute on function public.accept_invite(text, text) to authenticated;
grant execute on function public.quote_membership(uuid, uuid) to authenticated;
grant execute on function public.sell_membership(uuid, uuid, date, date, bigint, bigint, text, bigint, public.payment_mode)
  to authenticated;
grant execute on function public.bulk_renew(uuid[], uuid, boolean, public.payment_mode) to authenticated;
