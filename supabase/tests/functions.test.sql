-- Setting up a gym, inviting staff, the membership rules, the member list and the dashboard.
-- Run with `supabase test db`. Everything is rolled back afterwards.

begin;
create extension if not exists pgtap with schema extensions;
select plan(30);

insert into auth.users (id, email) values
  ('f1000000-0000-4000-8000-000000000001', 'owner-a@test'),
  ('f1000000-0000-4000-8000-000000000002', 'staff-a@test'),
  ('f2000000-0000-4000-8000-000000000001', 'owner-b@test'),
  ('f3000000-0000-4000-8000-000000000001', 'late@test');

create function pg_temp.sign_in(user_id uuid) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', user_id, 'role', 'authenticated')::text, true);
$$;
-- Ids made along the way, kept across role switches.
create function pg_temp.remember(key text, value text) returns text language sql as $$
  select set_config('test.' || key, value, true);
$$;
create function pg_temp.recall(key text) returns uuid language sql as $$
  select current_setting('test.' || key)::uuid;
$$;
create function pg_temp.member(member_name text) returns uuid language sql as $$
  select id from members where name = member_name;
$$;
grant execute on all functions in schema pg_temp to authenticated, anon;

-- The owner sets up the gym ---------------------------------------------------------------------
select pg_temp.sign_in('f1000000-0000-4000-8000-000000000001');
set local role authenticated;

select pg_temp.remember('gym', public.create_gym('Iron Temple', 'MG Road', 'Harsh')::text);
select results_eq('select role::text, name from gym_staff', $$values ('owner', 'Harsh')$$, 'create_gym makes the caller its owner');
select throws_ok($$select public.create_gym('Another')$$, 'P0001', 'already_in_gym', 'an owner cannot set up a second gym');

insert into plans (gym_id, name, duration_value, duration_unit, default_price) values
  (pg_temp.recall('gym'), 'Monthly', 1, 'MONTHS', 1000),
  (pg_temp.recall('gym'), '10 days', 10, 'DAYS', 300);
select pg_temp.remember('monthly', (select id::text from plans where name = 'Monthly'));
select pg_temp.remember('ten_days', (select id::text from plans where name = '10 days'));

select set_config('test.invite', public.create_invite(), true);
select matches(current_setting('test.invite'), '^[A-HJ-NP-Z2-9]{8}$', 'invite codes are 8 characters with no 0/O or 1/I');
reset role;

-- Staff join with the code ----------------------------------------------------------------------
select pg_temp.sign_in('f1000000-0000-4000-8000-000000000002');
set local role authenticated;

select throws_ok('select public.create_invite()', 'P0001', 'not_owner', 'staff cannot create invites');
select throws_ok($$select public.accept_invite('NOPE2345')$$, 'P0001', 'invalid_invite', 'an unknown code is rejected');
select is(
  public.accept_invite(lower(left(current_setting('test.invite'), 4) || '-' || right(current_setting('test.invite'), 4)), 'Ravi'),
  pg_temp.recall('gym'),
  'a code typed in lower case with a dash still works'
);
select results_eq(
  $$select role::text, name from gym_staff where user_id = auth.uid()$$, $$values ('staff', 'Ravi')$$,
  'accept_invite adds the caller as staff'
);
reset role;

select pg_temp.sign_in('f3000000-0000-4000-8000-000000000001');
set local role authenticated;
select throws_ok(
  format('select public.accept_invite(%L)', current_setting('test.invite')),
  'P0001', 'invalid_invite', 'each code works only once'
);
reset role;

select pg_temp.sign_in('f1000000-0000-4000-8000-000000000001');
set local role authenticated;
select set_config('test.expired_invite', public.create_invite(), true);
reset role;
update gym_invites set expires_at = now() - interval '1 second' where code = current_setting('test.expired_invite');
select pg_temp.sign_in('f3000000-0000-4000-8000-000000000001');
set local role authenticated;
select throws_ok(
  format('select public.accept_invite(%L)', current_setting('test.expired_invite')),
  'P0001', 'invalid_invite', 'expired codes are rejected'
);
reset role;

-- Membership rules ------------------------------------------------------------------------------
select is(private.membership_end_date('2026-01-15', 1, 'MONTHS'), '2026-02-14'::date, 'a month from 15 Jan ends on 14 Feb');
select is(private.membership_end_date('2026-01-31', 1, 'MONTHS'), '2026-02-27'::date, 'a month from 31 Jan ends the day before 28 Feb');
select is(private.membership_end_date('2026-03-01', 10, 'DAYS'), '2026-03-10'::date, '10 days from 1 Mar ends on 10 Mar');
select is(private.membership_end_date('2026-03-01', 1, 'DAYS'), '2026-03-01'::date, 'a day pass ends the same day');

-- Staff sell memberships ------------------------------------------------------------------------
select pg_temp.sign_in('f1000000-0000-4000-8000-000000000002');
set local role authenticated;

insert into members (gym_id, name, joined_on)
select pg_temp.recall('gym'), n, current_date from unnest(array['Asha', 'Bilal', 'Chirag', 'Dev']) n;
select pg_temp.remember('asha', pg_temp.member('Asha')::text);

select results_eq(
  format('select * from public.quote_membership(%L, %L)', pg_temp.member('Asha'), pg_temp.recall('monthly')),
  format('values (%L::date, %L::date, 1000::bigint)', private.gym_today(), private.membership_end_date(private.gym_today(), 1, 'MONTHS')),
  'a new member''s quote starts today at the plan''s price'
);

select pg_temp.remember('asha_membership', public.sell_membership(
  pg_temp.member('Asha'), pg_temp.recall('monthly'), first_payment_amount => 400, first_payment_mode => 'UPI'
)::text);
select results_eq(
  'select amount, mode::text, paid_on, created_by from payments',
  format('values (400::bigint, %L, %L::date, %L::uuid)', 'UPI', private.gym_today(), 'f1000000-0000-4000-8000-000000000002'),
  'the first payment is saved with the sale, dated today, under the seller'
);
select is(
  (select start_date from public.quote_membership(pg_temp.member('Asha'), pg_temp.recall('monthly'))),
  private.membership_end_date(private.gym_today(), 1, 'MONTHS') + 1,
  'a renewal continues the day after a running membership ends'
);
select throws_ok(
  format(
    'insert into payments (gym_id, membership_id, amount, paid_on) values (%L, %L, 700, current_date)',
    pg_temp.recall('gym'), pg_temp.recall('asha_membership')
  ),
  'P0001', 'overpayment', 'paying more than is due (700 of 600) is rejected'
);
select lives_ok(
  format(
    'insert into payments (gym_id, membership_id, amount, paid_on) values (%L, %L, 600, current_date)',
    pg_temp.recall('gym'), pg_temp.recall('asha_membership')
  ),
  'paying exactly what is due works'
);
select throws_ok(
  format(
    'select public.sell_membership(%L, %L, price => 500, discount => 100, first_payment_amount => 450)',
    pg_temp.member('Bilal'), pg_temp.recall('monthly')
  ),
  'P0001', 'overpayment', 'a first payment above the price after discount is rejected'
);

-- Bilal's 10 days ended 3 days ago; Chirag's end in 5 days; neither has paid. Dev has no plan.
select public.sell_membership(pg_temp.member('Bilal'), pg_temp.recall('ten_days'), start_date => private.gym_today() - 12);
select public.sell_membership(pg_temp.member('Chirag'), pg_temp.recall('ten_days'), start_date => private.gym_today() - 4);

-- Member list and dashboard ---------------------------------------------------------------------
select results_eq(
  'select name, status, amount_due from member_summary order by name',
  $$values ('Asha', 'ACTIVE', 0::bigint), ('Bilal', 'EXPIRED', 300::bigint),
           ('Chirag', 'EXPIRING_SOON', 300::bigint), ('Dev', 'NONE', 0::bigint)$$,
  'member_summary works out each member''s status and dues'
);
select results_eq(
  'select * from public.dashboard_stats()',
  'values (2, 1, 1, 2, 600::bigint, 1000::bigint)',
  'dashboard: 2 active, 1 expiring, 1 expired, 2 owing ₹600, ₹1000 collected'
);
reset role;

-- Bulk renewal ----------------------------------------------------------------------------------
select pg_temp.sign_in('f1000000-0000-4000-8000-000000000001');
set local role authenticated;
update members set is_archived = true where name = 'Dev';
reset role;

select pg_temp.sign_in('f1000000-0000-4000-8000-000000000002');
set local role authenticated;
select is(
  public.bulk_renew(
    array[pg_temp.member('Bilal'), pg_temp.member('Chirag'), pg_temp.member('Dev')], pg_temp.recall('monthly'),
    mark_paid => true
  ),
  2,
  'bulk renewal skips archived members'
);
select is(
  (select max(start_date) from memberships where member_id = pg_temp.member('Bilal')),
  private.gym_today(),
  'an expired member''s renewal starts today'
);
select is(
  (select max(start_date) from memberships where member_id = pg_temp.member('Chirag')),
  private.gym_today() + 6,
  'a running member''s renewal starts the day after their end date'
);
select results_eq(
  $$select count(*), sum(amount) from payments where amount = 1000 and paid_on = private.gym_today()
    and membership_id <> current_setting('test.asha_membership')::uuid$$,
  'values (2::bigint, 2000::numeric)',
  'mark_paid records a full payment for each renewal'
);
reset role;

-- Another gym sees none of it, and signed-out visitors can't call anything ----------------------
select pg_temp.sign_in('f2000000-0000-4000-8000-000000000001');
set local role authenticated;
select public.create_gym('Other Gym');
select is_empty('select 1 from member_summary', 'another gym sees none of the members');
select results_eq('select * from public.dashboard_stats()', 'values (0, 0, 0, 0, 0::bigint, 0::bigint)',
  'another gym''s dashboard is empty');
select throws_ok(
  format('select public.sell_membership(%L, %L)', pg_temp.recall('asha'), pg_temp.recall('monthly')),
  'P0001', 'member_not_found', 'another gym cannot sell to these members'
);
reset role;

set local role anon;
select throws_ok($$select public.create_gym('X')$$, '42501', null, 'signed-out visitors cannot create gyms');
select throws_ok('select * from public.dashboard_stats()', '42501', null, 'signed-out visitors cannot read the dashboard');
reset role;

select * from finish();
rollback;
