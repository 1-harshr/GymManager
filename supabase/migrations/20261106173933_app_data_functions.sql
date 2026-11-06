-- What the app needs beyond plain reads and writes: saving memberships with their first payments
-- together, and the usage figures behind the sell form's suggestions.

-- Saves memberships exactly as the app worked them out (dates, price, discount), each with an optional
-- first payment, all or nothing. items is a JSON array of
--   { member_id, plan_id?, plan_name, start_date, end_date, price, discount?, notes?,
--     payment?: { amount, mode, paid_on } }
-- Returns the new memberships' ids in the same order. Runs with the caller's rights, so the usual
-- rules apply, and the overpayment check still guards each payment.
create function public.add_memberships(items jsonb)
returns uuid[]
language plpgsql
set search_path = ''
as $$
declare
  item jsonb;
  member_gym_id uuid;
  new_id uuid;
  ids uuid[] := '{}';
begin
  for item in select * from jsonb_array_elements(items) loop
    select m.gym_id into member_gym_id from public.members m where m.id = (item ->> 'member_id')::uuid;
    if not found then
      raise exception 'member_not_found';
    end if;

    insert into public.memberships (gym_id, member_id, plan_id, plan_name, start_date, end_date, price, discount, notes)
    values (
      member_gym_id,
      (item ->> 'member_id')::uuid,
      nullif(item ->> 'plan_id', '')::uuid,
      item ->> 'plan_name',
      (item ->> 'start_date')::date,
      (item ->> 'end_date')::date,
      (item ->> 'price')::bigint,
      coalesce((item ->> 'discount')::bigint, 0),
      nullif(trim(item ->> 'notes'), '')
    )
    returning id into new_id;

    if item ? 'payment' and jsonb_typeof(item -> 'payment') = 'object' then
      insert into public.payments (gym_id, membership_id, amount, mode, paid_on)
      values (
        member_gym_id,
        new_id,
        (item -> 'payment' ->> 'amount')::bigint,
        (item -> 'payment' ->> 'mode')::public.payment_mode,
        (item -> 'payment' ->> 'paid_on')::date
      );
    end if;

    ids := ids || new_id;
  end loop;
  return ids;
end;
$$;

-- What the gym does most often, most used first: memberships sold per plan, and the usual payment
-- amounts and discounts. The same figures the phone used to count from its own records.
create function public.usage_stats(top int default 6)
returns jsonb
language sql stable
set search_path = ''
as $$
  select jsonb_build_object(
    'plan_uses', coalesce((
      select jsonb_object_agg(plan_id, uses)
      from (select plan_id::text, count(*) as uses from public.memberships where plan_id is not null group by plan_id) p
    ), '{}'::jsonb),
    'payment_amounts', coalesce((
      select jsonb_agg(amount order by uses desc, amount desc)
      from (select amount, count(*) as uses from public.payments group by amount order by uses desc, amount desc limit top) a
    ), '[]'::jsonb),
    'discounts', coalesce((
      select jsonb_agg(discount order by uses desc, discount desc)
      from (
        select discount, count(*) as uses from public.memberships where discount > 0
        group by discount order by uses desc, discount desc limit top
      ) d
    ), '[]'::jsonb)
  )
$$;

revoke execute on function public.add_memberships(jsonb) from public, anon;
revoke execute on function public.usage_stats(int) from public, anon;
grant execute on function public.add_memberships(jsonb) to authenticated;
grant execute on function public.usage_stats(int) to authenticated;
