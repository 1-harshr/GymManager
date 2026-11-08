-- Pausing a membership: the end moves out by the paused days, queued renewals move with it, ending early
-- gives days back, and only the gym's own staff can do it. Run with `supabase test db`. Rolled back after.

begin;
create extension if not exists pgtap with schema extensions;
select plan(19);

insert into auth.users (id, email) values
  ('a5100000-0000-4000-8000-000000000001', 'staff-a@test'),
  ('a5200000-0000-4000-8000-000000000001', 'owner-b@test');
insert into gyms (id, name) values
  ('a5a00000-0000-4000-8000-00000000000a', 'Gym A'),
  ('a5b00000-0000-4000-8000-00000000000b', 'Gym B');
insert into gym_staff (gym_id, user_id, role) values
  ('a5a00000-0000-4000-8000-00000000000a', 'a5100000-0000-4000-8000-000000000001', 'staff'),
  ('a5b00000-0000-4000-8000-00000000000b', 'a5200000-0000-4000-8000-000000000001', 'owner');
insert into members (id, gym_id, name, joined_on) values
  ('a5a10000-0000-4000-8000-000000000001', 'a5a00000-0000-4000-8000-00000000000a', 'Asha', current_date - 40);

-- Asha's running month, and a renewal already queued after it.
create function pg_temp.today() returns date language sql as $$ select (now() at time zone 'Asia/Kolkata')::date $$;
insert into memberships (id, gym_id, member_id, plan_name, start_date, end_date, price) values
  ('a5a20000-0000-4000-8000-000000000001', 'a5a00000-0000-4000-8000-00000000000a', 'a5a10000-0000-4000-8000-000000000001',
   'Monthly', pg_temp.today() - 10, pg_temp.today() + 19, 1000),
  ('a5a20000-0000-4000-8000-000000000002', 'a5a00000-0000-4000-8000-00000000000a', 'a5a10000-0000-4000-8000-000000000001',
   'Monthly', pg_temp.today() + 20, pg_temp.today() + 49, 1000);

create function pg_temp.sign_in(user_id uuid) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', user_id, 'role', 'authenticated')::text, true);
$$;
create function pg_temp.end_of(id uuid) returns date language sql as $$ select end_date from memberships where memberships.id = end_of.id $$;
create function pg_temp.start_of(id uuid) returns date language sql as $$ select start_date from memberships where memberships.id = start_of.id $$;
grant execute on all functions in schema pg_temp to authenticated, anon;

-- Another gym's owner can't pause Asha.
select pg_temp.sign_in('a5200000-0000-4000-8000-000000000001');
set local role authenticated;
select throws_ok(
  $$select pause_membership('a5a20000-0000-4000-8000-000000000001', pg_temp.today(), pg_temp.today() + 14)$$,
  'P0001', 'membership_not_found', 'another gym can''t pause the membership'
);
select is((select count(*)::int from membership_pauses), 0, 'nor see any pauses');
reset role;

select pg_temp.sign_in('a5100000-0000-4000-8000-000000000001');
set local role authenticated;

select throws_ok(
  $$select pause_membership('a5a20000-0000-4000-8000-000000000001', pg_temp.today(), pg_temp.today())$$,
  'P0001', 'invalid_pause', 'a pause must last at least a day'
);
select throws_ok(
  $$select pause_membership('a5a20000-0000-4000-8000-000000000001', pg_temp.today() + 30, pg_temp.today() + 40)$$,
  'P0001', 'invalid_pause', 'a pause must start within the membership'
);
select throws_ok(
  $$insert into membership_pauses (gym_id, membership_id, paused_from, resumes_on)
    values ('a5a00000-0000-4000-8000-00000000000a', 'a5a20000-0000-4000-8000-000000000001', current_date, current_date + 1)$$,
  '42501', null, 'pauses can''t be written directly'
);

-- Staff pause it for two weeks, starting today.
select lives_ok(
  $$select pause_membership('a5a20000-0000-4000-8000-000000000001', pg_temp.today(), pg_temp.today() + 14)$$,
  'staff pause a membership'
);
select is(pg_temp.end_of('a5a20000-0000-4000-8000-000000000001'), pg_temp.today() + 33, 'its end moves out by the 14 days');
select is(pg_temp.start_of('a5a20000-0000-4000-8000-000000000002'), pg_temp.today() + 34, 'the queued renewal starts 14 days later');
select is(pg_temp.end_of('a5a20000-0000-4000-8000-000000000002'), pg_temp.today() + 63, 'and ends 14 days later');
select is(
  (select paused_until from member_summary where member_id = 'a5a10000-0000-4000-8000-000000000001'), pg_temp.today() + 14,
  'the member list says until when they''re paused'
);
select is(
  (select latest_end_date from member_summary where member_id = 'a5a10000-0000-4000-8000-000000000001'), pg_temp.today() + 63,
  'and their latest end includes the pause'
);
select throws_ok(
  $$select pause_membership('a5a20000-0000-4000-8000-000000000001', pg_temp.today() + 1, pg_temp.today() + 3)$$,
  'P0001', 'already_paused', 'a paused membership can''t be paused again'
);

-- Ending a pause that started today gives every day back and leaves no record.
select lives_ok($$select resume_membership('a5a20000-0000-4000-8000-000000000001')$$, 'staff end the pause');
select is(pg_temp.end_of('a5a20000-0000-4000-8000-000000000001'), pg_temp.today() + 19, 'the end moves back');
select is(pg_temp.start_of('a5a20000-0000-4000-8000-000000000002'), pg_temp.today() + 20, 'and so does the renewal');
select is((select count(*)::int from membership_pauses), 0, 'a pause that never got going isn''t kept');
select throws_ok(
  $$select resume_membership('a5a20000-0000-4000-8000-000000000001')$$,
  'P0001', 'not_paused', 'there''s nothing to end now'
);
reset role;

-- A pause that started 5 days ago and runs 10 more: ending it today gives back the 10 unused days.
insert into membership_pauses (gym_id, membership_id, paused_from, resumes_on) values
  ('a5a00000-0000-4000-8000-00000000000a', 'a5a20000-0000-4000-8000-000000000001', pg_temp.today() - 5, pg_temp.today() + 10);
update memberships set end_date = end_date + 15 where id = 'a5a20000-0000-4000-8000-000000000001';
update memberships set start_date = start_date + 15, end_date = end_date + 15 where id = 'a5a20000-0000-4000-8000-000000000002';
select pg_temp.sign_in('a5100000-0000-4000-8000-000000000001');
set local role authenticated;
select lives_ok($$select resume_membership('a5a20000-0000-4000-8000-000000000001')$$, 'staff end a running pause early');
select is(
  (select array[pg_temp.end_of('a5a20000-0000-4000-8000-000000000001') - pg_temp.today(), (select resumes_on - pg_temp.today() from membership_pauses)]),
  array[24, 0],
  'the 5 days used stay on the membership, and the pause now ends today'
);
reset role;

select * from finish();
rollback;
