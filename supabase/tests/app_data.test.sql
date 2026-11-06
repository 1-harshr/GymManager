-- Saving memberships with their first payments, and the usage figures behind the sell form.
-- Run with `supabase test db`. Everything is rolled back afterwards.

begin;
create extension if not exists pgtap with schema extensions;
select plan(9);

insert into auth.users (id, email) values
  ('f1000000-0000-4000-8000-000000000001', 'owner-a@test'),
  ('f2000000-0000-4000-8000-000000000001', 'owner-b@test');
insert into gyms (id, name) values
  ('fa000000-0000-4000-8000-00000000000a', 'Gym A'),
  ('fb000000-0000-4000-8000-00000000000b', 'Gym B');
insert into gym_staff (gym_id, user_id, role) values
  ('fa000000-0000-4000-8000-00000000000a', 'f1000000-0000-4000-8000-000000000001', 'owner'),
  ('fb000000-0000-4000-8000-00000000000b', 'f2000000-0000-4000-8000-000000000001', 'owner');
insert into plans (id, gym_id, name, duration_value, duration_unit, default_price) values
  ('fa300000-0000-4000-8000-000000000001', 'fa000000-0000-4000-8000-00000000000a', 'Monthly', 1, 'MONTHS', 1000);
insert into members (id, gym_id, name, joined_on) values
  ('fa100000-0000-4000-8000-000000000001', 'fa000000-0000-4000-8000-00000000000a', 'Asha', current_date),
  ('fa100000-0000-4000-8000-000000000002', 'fa000000-0000-4000-8000-00000000000a', 'Bilal', current_date);

create function pg_temp.sign_in(user_id uuid) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', user_id, 'role', 'authenticated')::text, true);
$$;
grant execute on all functions in schema pg_temp to authenticated;

select pg_temp.sign_in('f1000000-0000-4000-8000-000000000001');
set local role authenticated;

select is(
  array_length(public.add_memberships($$[
    {"member_id": "fa100000-0000-4000-8000-000000000001", "plan_id": "fa300000-0000-4000-8000-000000000001",
     "plan_name": "Monthly", "start_date": "2026-11-01", "end_date": "2026-11-30", "price": 1000, "discount": 100,
     "payment": {"amount": 400, "mode": "UPI", "paid_on": "2026-11-01"}},
    {"member_id": "fa100000-0000-4000-8000-000000000002", "plan_id": "fa300000-0000-4000-8000-000000000001",
     "plan_name": "Monthly", "start_date": "2026-11-05", "end_date": "2026-12-04", "price": 1000}
  ]$$::jsonb), 1),
  2,
  'saves each membership and returns their ids'
);
select results_eq(
  'select m.name, ms.start_date, ms.price, ms.discount from memberships ms join members m on m.id = ms.member_id order by m.name',
  $$values ('Asha', '2026-11-01'::date, 1000::bigint, 100::bigint), ('Bilal', '2026-11-05'::date, 1000::bigint, 0::bigint)$$,
  'dates, prices and discounts are kept exactly as given'
);
select results_eq(
  'select amount, mode::text, paid_on, created_by from payments',
  $$values (400::bigint, 'UPI', '2026-11-01'::date, 'f1000000-0000-4000-8000-000000000001'::uuid)$$,
  'the first payment is saved with its own date and mode, under whoever saved it'
);
select throws_ok(
  $$select public.add_memberships('[{"member_id": "fa100000-0000-4000-8000-000000000001", "plan_name": "Monthly",
    "start_date": "2026-12-01", "end_date": "2026-12-31", "price": 500, "payment": {"amount": 600, "mode": "CASH", "paid_on": "2026-12-01"}}]')$$,
  'P0001', 'overpayment', 'a first payment above the price is rejected'
);
select is(
  (select count(*) from memberships)::int, 2,
  'a rejected batch saves nothing'
);
select is(
  public.usage_stats(),
  '{"plan_uses": {"fa300000-0000-4000-8000-000000000001": 2}, "payment_amounts": [400], "discounts": [100]}'::jsonb,
  'usage counts plans sold, payment amounts and discounts'
);
reset role;

select pg_temp.sign_in('f2000000-0000-4000-8000-000000000001');
set local role authenticated;
select throws_ok(
  $$select public.add_memberships('[{"member_id": "fa100000-0000-4000-8000-000000000001", "plan_name": "X",
    "start_date": "2026-12-01", "end_date": "2026-12-31", "price": 500}]')$$,
  'P0001', 'member_not_found', 'another gym cannot add memberships to these members'
);
select is(
  public.usage_stats(),
  '{"plan_uses": {}, "payment_amounts": [], "discounts": []}'::jsonb,
  'another gym''s usage is its own'
);
reset role;

set local role anon;
select throws_ok($$select public.usage_stats()$$, '42501', null, 'signed-out visitors cannot call these');
reset role;

select * from finish();
rollback;
