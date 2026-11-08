-- Pausing a membership while a member is away. Staff pick the day it starts and the day they're back;
-- the membership's end moves out by that many days straight away, as do any renewals already queued
-- after it, so nothing overlaps. Ending a pause early gives the unused days back. Pauses are kept, so a
-- membership's history shows them.
--
-- Errors the app should recognise: membership_not_found, invalid_pause, already_paused, not_paused.

create table public.membership_pauses (
  id uuid primary key default gen_random_uuid(),
  gym_id uuid not null,
  membership_id uuid not null,
  paused_from date not null,
  -- The day the member is back; the membership is paused up to the day before.
  resumes_on date not null check (resumes_on > paused_from),
  created_by uuid references auth.users on delete set null default auth.uid(),
  created_at timestamptz not null default now(),
  foreign key (membership_id, gym_id) references public.memberships (id, gym_id) on delete cascade
);

create index membership_pauses_membership_id_idx on public.membership_pauses (membership_id);

-- Read directly; written only through pause_membership and resume_membership, which move dates too.
alter table public.membership_pauses enable row level security;
revoke all on public.membership_pauses from anon, authenticated;
grant select on public.membership_pauses to authenticated;
create policy "staff read pauses" on public.membership_pauses for select to authenticated
  using (gym_id = (select private.current_gym_id()));

alter publication supabase_realtime add table public.membership_pauses;

-- Moves the membership's end, and the start and end of every membership of the same member queued after
-- it, by [days] (negative to move them back).
create function private.shift_membership(membership_id uuid, days int) returns void
language plpgsql
set search_path = ''
as $$
declare
  m public.memberships;
begin
  select * into m from public.memberships where id = shift_membership.membership_id;
  update public.memberships
  set start_date = start_date + days, end_date = end_date + days
  where member_id = m.member_id and start_date > m.end_date;
  update public.memberships set end_date = end_date + days where id = m.id;
end;
$$;

-- Pauses a membership of the caller's gym from [paused_from] until the member is back on [resumes_on].
-- The pause must start within the membership, and the membership can't already have a pause that's
-- running or still to come.
create function public.pause_membership(membership_id uuid, paused_from date, resumes_on date) returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  m public.memberships;
begin
  select * into m from public.memberships
  where id = pause_membership.membership_id and gym_id = private.current_gym_id()
  for update;
  if m.id is null then
    raise exception 'membership_not_found';
  end if;
  if resumes_on <= paused_from or paused_from < m.start_date or paused_from > m.end_date then
    raise exception 'invalid_pause';
  end if;
  if exists (
    select 1 from public.membership_pauses p
    where p.membership_id = m.id and p.resumes_on > private.gym_today()
  ) then
    raise exception 'already_paused';
  end if;
  perform private.shift_membership(m.id, resumes_on - paused_from);
  insert into public.membership_pauses (gym_id, membership_id, paused_from, resumes_on)
  values (m.gym_id, m.id, paused_from, resumes_on);
end;
$$;

-- Ends the membership's running pause today, or cancels one that hasn't started, giving back the days
-- not used.
create function public.resume_membership(membership_id uuid) returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  today date := private.gym_today();
  p public.membership_pauses;
  back_on date;
begin
  perform 1 from public.memberships
  where id = resume_membership.membership_id and gym_id = private.current_gym_id()
  for update;
  if not found then
    raise exception 'membership_not_found';
  end if;
  select * into p from public.membership_pauses
  where membership_pauses.membership_id = resume_membership.membership_id and resumes_on > today
  for update;
  if p.id is null then
    raise exception 'not_paused';
  end if;
  back_on := greatest(today, p.paused_from);
  perform private.shift_membership(p.membership_id, -(p.resumes_on - back_on));
  -- A pause that never got going leaves nothing to record.
  if back_on = p.paused_from then
    delete from public.membership_pauses where id = p.id;
  else
    update public.membership_pauses set resumes_on = back_on where id = p.id;
  end if;
end;
$$;

revoke all on function private.shift_membership(uuid, int) from public, anon, authenticated;
revoke all on function public.pause_membership(uuid, date, date), public.resume_membership(uuid) from public, anon;
grant execute on function public.pause_membership(uuid, date, date), public.resume_membership(uuid) to authenticated;

-- The member list also says who is paused today, and until when. Same as before, plus paused_until.
create or replace view public.member_summary with (security_invoker = true) as
select
  m.id as member_id,
  m.gym_id,
  m.name,
  m.phone,
  m.photo_path,
  m.is_archived,
  ms.latest_end_date,
  (coalesce(ms.billed, 0) - coalesce(paid.total, 0))::bigint as amount_due,
  case
    when ms.latest_end_date is null then 'NONE'
    when ms.latest_end_date < private.gym_today() then 'EXPIRED'
    when ms.latest_end_date - private.gym_today() <= 7 then 'EXPIRING_SOON'
    else 'ACTIVE'
  end as status,
  paused.resumes_on as paused_until
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
) paid on true
left join lateral (
  select max(p.resumes_on) as resumes_on
  from public.membership_pauses p
  join public.memberships x on x.id = p.membership_id
  where x.member_id = m.id and p.paused_from <= private.gym_today() and p.resumes_on > private.gym_today()
) paused on true;
