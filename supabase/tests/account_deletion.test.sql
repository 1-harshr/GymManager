-- Deleting your own account: staff go and their records stay; the only owner hands over first, or takes
-- the gym with them when nobody else works there. Run with `supabase test db`. Everything is rolled back.

begin;
create extension if not exists pgtap with schema extensions;
select plan(16);

insert into auth.users (id, email) values
  ('ad100000-0000-4000-8000-000000000001', 'owner-a@test'),
  ('ad100000-0000-4000-8000-000000000002', 'staff-a@test'),
  ('ad200000-0000-4000-8000-000000000001', 'owner-b@test'),
  ('ad300000-0000-4000-8000-000000000001', 'owner-c1@test'),
  ('ad300000-0000-4000-8000-000000000002', 'owner-c2@test'),
  ('ad400000-0000-4000-8000-000000000001', 'nobody@test');
insert into gyms (id, name) values
  ('ada00000-0000-4000-8000-00000000000a', 'Gym A'),
  ('adb00000-0000-4000-8000-00000000000b', 'Gym B'),
  ('adc00000-0000-4000-8000-00000000000c', 'Gym C');
insert into gym_staff (gym_id, user_id, role) values
  ('ada00000-0000-4000-8000-00000000000a', 'ad100000-0000-4000-8000-000000000001', 'owner'),
  ('ada00000-0000-4000-8000-00000000000a', 'ad100000-0000-4000-8000-000000000002', 'staff'),
  ('adb00000-0000-4000-8000-00000000000b', 'ad200000-0000-4000-8000-000000000001', 'owner'),
  ('adc00000-0000-4000-8000-00000000000c', 'ad300000-0000-4000-8000-000000000001', 'owner'),
  ('adc00000-0000-4000-8000-00000000000c', 'ad300000-0000-4000-8000-000000000002', 'owner');
insert into members (id, gym_id, name, joined_on) values
  ('ada10000-0000-4000-8000-000000000001', 'ada00000-0000-4000-8000-00000000000a', 'Asha', current_date),
  ('adb10000-0000-4000-8000-000000000001', 'adb00000-0000-4000-8000-00000000000b', 'Bilal', current_date);
-- Asha was checked in by the staff member who's about to leave.
insert into check_ins (gym_id, member_id, created_by) values
  ('ada00000-0000-4000-8000-00000000000a', 'ada10000-0000-4000-8000-000000000001', 'ad100000-0000-4000-8000-000000000002');

create function pg_temp.sign_in(user_id uuid) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', user_id, 'role', 'authenticated')::text, true);
$$;
grant execute on all functions in schema pg_temp to authenticated, anon;

-- The anonymous key can't delete anyone.
set local role anon;
select throws_ok($$select delete_account()$$, '42501', null, 'the anonymous key can''t delete accounts');
reset role;

-- Gym A: the owner works with staff, so must hand over first.
select pg_temp.sign_in('ad100000-0000-4000-8000-000000000001');
set local role authenticated;
select is(account_deletion_plan(), 'hand_over', 'the only owner with staff must hand over');
select throws_ok($$select delete_account()$$, 'P0001', 'hand_over_first', 'the only owner with staff can''t delete yet');
reset role;

-- Staff go; the gym and what they recorded stay.
select pg_temp.sign_in('ad100000-0000-4000-8000-000000000002');
set local role authenticated;
select is(account_deletion_plan(), 'account', 'staff delete only their account');
select lives_ok($$select delete_account()$$, 'staff delete their account');
reset role;
select is((select count(*)::int from auth.users where id = 'ad100000-0000-4000-8000-000000000002'), 0, 'the staff account is gone');
select is((select count(*)::int from gyms where id = 'ada00000-0000-4000-8000-00000000000a'), 1, 'their gym stays');
select is(
  (select created_by from check_ins where member_id = 'ada10000-0000-4000-8000-000000000001'), null,
  'their check-ins stay but forget who made them'
);

-- With the staff gone, Gym A's owner is alone there.
select pg_temp.sign_in('ad100000-0000-4000-8000-000000000001');
set local role authenticated;
select is(account_deletion_plan(), 'gym', 'once alone, the only owner''s deletion takes the gym');
reset role;

-- Gym B: the only owner, alone, takes the gym and its records.
select pg_temp.sign_in('ad200000-0000-4000-8000-000000000001');
set local role authenticated;
select lives_ok($$select delete_account()$$, 'the only owner, alone, deletes their account');
reset role;
select is((select count(*)::int from auth.users where id = 'ad200000-0000-4000-8000-000000000001'), 0, 'their account is gone');
select is((select count(*)::int from gyms where id = 'adb00000-0000-4000-8000-00000000000b'), 0, 'their gym is gone');
select is((select count(*)::int from members where gym_id = 'adb00000-0000-4000-8000-00000000000b'), 0, 'its members are gone');

-- Gym C: with another owner, an owner goes alone.
select pg_temp.sign_in('ad300000-0000-4000-8000-000000000001');
set local role authenticated;
select lives_ok($$select delete_account()$$, 'an owner with a co-owner deletes their account');
reset role;
select is((select count(*)::int from gyms where id = 'adc00000-0000-4000-8000-00000000000c'), 1, 'the gym stays with the other owner');

-- Someone who never set up or joined a gym.
select pg_temp.sign_in('ad400000-0000-4000-8000-000000000001');
set local role authenticated;
select is(account_deletion_plan(), 'account', 'without a gym, only the account goes');
reset role;

select * from finish();
rollback;
