-- Check-ins: staff check members in to their own gym once a day, and anyone at the gym can undo one.
-- Run with `supabase test db`. Everything is rolled back afterwards.

begin;
create extension if not exists pgtap with schema extensions;
select plan(12);

insert into auth.users (id, email) values
  ('e7100000-0000-4000-8000-000000000001', 'owner-a@test'),
  ('e7100000-0000-4000-8000-000000000002', 'staff-a@test'),
  ('e7200000-0000-4000-8000-000000000001', 'owner-b@test');
insert into gyms (id, name) values
  ('e7a00000-0000-4000-8000-00000000000a', 'Gym A'),
  ('e7b00000-0000-4000-8000-00000000000b', 'Gym B');
insert into gym_staff (gym_id, user_id, role) values
  ('e7a00000-0000-4000-8000-00000000000a', 'e7100000-0000-4000-8000-000000000001', 'owner'),
  ('e7a00000-0000-4000-8000-00000000000a', 'e7100000-0000-4000-8000-000000000002', 'staff'),
  ('e7b00000-0000-4000-8000-00000000000b', 'e7200000-0000-4000-8000-000000000001', 'owner');
insert into members (id, gym_id, name, joined_on) values
  ('e7a10000-0000-4000-8000-000000000001', 'e7a00000-0000-4000-8000-00000000000a', 'Asha', current_date),
  ('e7b10000-0000-4000-8000-000000000001', 'e7b00000-0000-4000-8000-00000000000b', 'Bilal', current_date);
-- Asha also came a week ago, checked in by the owner.
insert into check_ins (gym_id, member_id, checked_in_on, created_by) values
  ('e7a00000-0000-4000-8000-00000000000a', 'e7a10000-0000-4000-8000-000000000001', current_date - 7, 'e7100000-0000-4000-8000-000000000001');

create function pg_temp.sign_in(user_id uuid) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', user_id, 'role', 'authenticated')::text, true);
$$;
create function pg_temp.affected(statement text) returns int language plpgsql as $$
declare
  n int;
begin
  execute statement;
  get diagnostics n = row_count;
  return n;
end;
$$;
grant execute on all functions in schema pg_temp to authenticated, anon;

select pg_temp.sign_in('e7100000-0000-4000-8000-000000000002');
set local role authenticated;

select lives_ok(
  $$insert into check_ins (gym_id, member_id) values ('e7a00000-0000-4000-8000-00000000000a', 'e7a10000-0000-4000-8000-000000000001')$$,
  'staff check members in'
);
select is(
  (select checked_in_on from check_ins where created_by = 'e7100000-0000-4000-8000-000000000002'),
  (now() at time zone 'Asia/Kolkata')::date,
  'a check-in is for today at the gym'
);
select is(
  (select count(*)::int from check_ins where created_by = auth.uid()), 1,
  'check-ins record who made them'
);
select throws_ok(
  $$insert into check_ins (gym_id, member_id) values ('e7a00000-0000-4000-8000-00000000000a', 'e7a10000-0000-4000-8000-000000000001')$$,
  '23505', null, 'a member is checked in once a day'
);
select lives_ok(
  $$insert into check_ins (gym_id, member_id) values ('e7a00000-0000-4000-8000-00000000000a', 'e7a10000-0000-4000-8000-000000000001')
    on conflict (member_id, checked_in_on) do nothing$$,
  'checking in again can be ignored'
);
select throws_ok(
  $$insert into check_ins (gym_id, member_id, checked_in_on) values ('e7a00000-0000-4000-8000-00000000000a', 'e7a10000-0000-4000-8000-000000000001', current_date - 1)$$,
  '42501', null, 'the app cannot backdate a check-in'
);
select throws_ok(
  $$insert into check_ins (gym_id, member_id) values ('e7b00000-0000-4000-8000-00000000000b', 'e7b10000-0000-4000-8000-000000000001')$$,
  '42501', null, 'staff cannot check in another gym''s members'
);
select throws_ok(
  $$insert into check_ins (gym_id, member_id) values ('e7a00000-0000-4000-8000-00000000000a', 'e7b10000-0000-4000-8000-000000000001')$$,
  '23503', null, 'a check-in cannot point at another gym''s member'
);
select results_eq(
  'select last_check_in_on from member_last_check_in',
  $$values ((now() at time zone 'Asia/Kolkata')::date)$$,
  'the latest visit is today'
);
select is(pg_temp.affected('delete from check_ins where checked_in_on < current_date - 1'), 1, 'staff undo any check-in, even an old one');
reset role;

select pg_temp.sign_in('e7200000-0000-4000-8000-000000000001');
set local role authenticated;
select is_empty('select 1 from check_ins union all select 1 from member_last_check_in', 'other gyms see none of them');
select is(pg_temp.affected('delete from check_ins'), 0, 'other gyms cannot undo them');
reset role;

select * from finish();
rollback;
