-- Who can see and change what: each gym is walled off, and owners can do more than staff.
-- Run with `supabase test db`. Everything is rolled back afterwards.

begin;
create extension if not exists pgtap with schema extensions;
select plan(33);

-- Gym A has an owner and a staff member, gym B an owner, and one user belongs to no gym.
insert into auth.users (id, email) values
  ('f1000000-0000-4000-8000-000000000001', 'owner-a@test'),
  ('f1000000-0000-4000-8000-000000000002', 'staff-a@test'),
  ('f2000000-0000-4000-8000-000000000001', 'owner-b@test'),
  ('f3000000-0000-4000-8000-000000000001', 'no-gym@test');
insert into gyms (id, name) values
  ('fa000000-0000-4000-8000-00000000000a', 'Gym A'),
  ('fb000000-0000-4000-8000-00000000000b', 'Gym B');
insert into gym_staff (gym_id, user_id, role) values
  ('fa000000-0000-4000-8000-00000000000a', 'f1000000-0000-4000-8000-000000000001', 'owner'),
  ('fa000000-0000-4000-8000-00000000000a', 'f1000000-0000-4000-8000-000000000002', 'staff'),
  ('fb000000-0000-4000-8000-00000000000b', 'f2000000-0000-4000-8000-000000000001', 'owner');
insert into gym_invites (code, gym_id, created_by) values
  ('TESTA234', 'fa000000-0000-4000-8000-00000000000a', 'f1000000-0000-4000-8000-000000000001');
insert into plans (gym_id, name, duration_value, duration_unit, default_price) values
  ('fa000000-0000-4000-8000-00000000000a', 'A Monthly', 1, 'MONTHS', 1000),
  ('fb000000-0000-4000-8000-00000000000b', 'B Monthly', 1, 'MONTHS', 900);
insert into members (id, gym_id, name, joined_on) values
  ('fa100000-0000-4000-8000-000000000001', 'fa000000-0000-4000-8000-00000000000a', 'Asha', current_date),
  ('fb100000-0000-4000-8000-000000000001', 'fb000000-0000-4000-8000-00000000000b', 'Bilal', current_date);
insert into memberships (id, gym_id, member_id, plan_name, start_date, end_date, price) values
  ('fa200000-0000-4000-8000-000000000001', 'fa000000-0000-4000-8000-00000000000a',
   'fa100000-0000-4000-8000-000000000001', 'A Monthly', current_date, current_date + 29, 1000);
insert into payments (gym_id, membership_id, amount, paid_on) values
  ('fa000000-0000-4000-8000-00000000000a', 'fa200000-0000-4000-8000-000000000001', 500, current_date);

create function pg_temp.sign_in(user_id uuid) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', user_id, 'role', 'authenticated')::text, true);
$$;
-- Rows an update or delete touched; policies hide rows rather than raise, so 0 means it was blocked.
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

-- Staff ---------------------------------------------------------------------------------------------
select pg_temp.sign_in('f1000000-0000-4000-8000-000000000002');
set local role authenticated;

select results_eq('select name from gyms', $$values ('Gym A')$$, 'staff see only their gym');
select results_eq('select name from members', $$values ('Asha')$$, 'staff see only their members');
select results_eq('select name from plans', $$values ('A Monthly')$$, 'staff see only their plans');
select lives_ok(
  $$insert into members (gym_id, name, joined_on) values ('fa000000-0000-4000-8000-00000000000a', 'New', current_date)$$,
  'staff add members'
);
select throws_ok(
  $$insert into members (gym_id, name, joined_on) values ('fb000000-0000-4000-8000-00000000000b', 'Spy', current_date)$$,
  '42501', null, 'staff cannot add members to another gym'
);
select is(pg_temp.affected($$update members set phone = '999' where name = 'Asha'$$), 1, 'staff edit members');
select throws_ok(
  $$update members set is_archived = true where name = 'Asha'$$,
  '42501', 'Only the gym owner can archive or restore members', 'staff cannot archive members'
);
select is(pg_temp.affected('delete from members'), 0, 'staff cannot delete members');
select throws_ok(
  $$update members set gym_id = 'fb000000-0000-4000-8000-00000000000b'$$,
  '42501', null, 'nobody can move a member to another gym'
);
select lives_ok(
  $$insert into payments (gym_id, membership_id, amount, paid_on)
    values ('fa000000-0000-4000-8000-00000000000a', 'fa200000-0000-4000-8000-000000000001', 100, current_date)$$,
  'staff record payments'
);
select is(
  (select created_by from payments where amount = 100),
  'f1000000-0000-4000-8000-000000000002'::uuid,
  'payments record who took them'
);
select throws_ok(
  $$insert into payments (gym_id, membership_id, amount, paid_on, created_by)
    values ('fa000000-0000-4000-8000-00000000000a', 'fa200000-0000-4000-8000-000000000001', 1, current_date,
            'f1000000-0000-4000-8000-000000000001')$$,
  '42501', null, 'staff cannot record a payment as someone else'
);
select is(pg_temp.affected('update payments set amount = 1'), 0, 'staff cannot edit payments');
select is(pg_temp.affected('delete from payments'), 0, 'staff cannot delete payments');
select is(pg_temp.affected('delete from memberships'), 0, 'staff cannot delete memberships');
select is(pg_temp.affected('update plans set default_price = 1'), 0, 'staff cannot edit plans');
select throws_ok(
  $$insert into plans (gym_id, name, duration_value, duration_unit, default_price)
    values ('fa000000-0000-4000-8000-00000000000a', 'X', 1, 'DAYS', 1)$$,
  '42501', null, 'staff cannot add plans'
);
select is(pg_temp.affected($$update gyms set name = 'Mine'$$), 0, 'staff cannot rename the gym');
select is(pg_temp.affected($$update gym_staff set role = 'owner'$$), 0, 'staff cannot promote themselves');
select is_empty('select * from gym_invites', 'staff cannot see invites');
reset role;

-- Owner ---------------------------------------------------------------------------------------------
select pg_temp.sign_in('f1000000-0000-4000-8000-000000000001');
set local role authenticated;

select is(pg_temp.affected('update members set is_archived = true'), 2, 'the owner archives members');
select is(pg_temp.affected('update plans set default_price = 1200'), 1, 'the owner edits plans');
select is(
  pg_temp.affected($$update gym_staff set role = 'staff' where user_id = 'f1000000-0000-4000-8000-000000000002'$$),
  1, 'the owner changes staff roles'
);
select is(
  pg_temp.affected($$delete from gym_staff where user_id = 'f1000000-0000-4000-8000-000000000001'$$),
  0, 'the owner cannot remove themselves'
);
select is(
  pg_temp.affected($$update plans set default_price = 1 where gym_id = 'fb000000-0000-4000-8000-00000000000b'$$),
  0, 'the owner cannot touch another gym'
);
select is(pg_temp.affected('delete from payments where amount = 100'), 1, 'the owner deletes payments');
select results_eq('select code from gym_invites', $$values ('TESTA234')$$, 'the owner sees their invites');
reset role;

-- No gym, and signed out ----------------------------------------------------------------------------
select pg_temp.sign_in('f3000000-0000-4000-8000-000000000001');
set local role authenticated;
select is_empty('select 1 from gyms union all select 1 from members union all select 1 from payments',
  'a user with no gym sees nothing');
select throws_ok(
  $$insert into members (gym_id, name, joined_on) values ('fa000000-0000-4000-8000-00000000000a', 'X', current_date)$$,
  '42501', null, 'a user with no gym cannot add members'
);
reset role;

set local role anon;
select throws_ok('select * from members', '42501', null, 'signed-out visitors cannot read members');
reset role;

-- Storage -------------------------------------------------------------------------------------------
select pg_temp.sign_in('f1000000-0000-4000-8000-000000000002');
set local role authenticated;
select lives_ok(
  $$insert into storage.objects (bucket_id, name) values ('member-photos', 'fa000000-0000-4000-8000-00000000000a/asha.jpg')$$,
  'staff upload photos to their gym''s folder'
);
select throws_ok(
  $$insert into storage.objects (bucket_id, name) values ('member-photos', 'fb000000-0000-4000-8000-00000000000b/x.jpg')$$,
  '42501', null, 'staff cannot upload to another gym''s folder'
);
select throws_ok(
  $$insert into storage.objects (bucket_id, name) values ('gym-logos', 'fa000000-0000-4000-8000-00000000000a/logo.jpg')$$,
  '42501', null, 'staff cannot change the logo'
);
reset role;

select * from finish();
rollback;
