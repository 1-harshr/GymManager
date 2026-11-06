-- Local development data, loaded by `supabase db reset`. Never pushed to hosted projects.
--
-- Two gyms so isolation can be seen, and logins for each role (password for all: password123):
--   owner@irontemple.test   +91 90000 00001   owner of Iron Temple
--   staff@irontemple.test   +91 90000 00002   staff at Iron Temple
--   owner@shakti.test       +91 90000 00003   owner of Shakti Fitness
--   new@example.test        +91 90000 00004   no gym yet: try creating one, or join with code IRON2345
--
-- Iron Temple gets the app's own demo data (DemoData.kt): dates are relative to today, so there are
-- renewals due this week, recent lapses, dues, instalments and a member with no plan yet.
-- Members get no phone numbers, so no message can reach a real person.

-- Logins ------------------------------------------------------------------------------------------

insert into auth.users (
  instance_id, id, aud, role, email, encrypted_password, email_confirmed_at, phone, phone_confirmed_at,
  raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
  confirmation_token, recovery_token, email_change, email_change_token_new
)
select
  '00000000-0000-0000-0000-000000000000', u.id, 'authenticated', 'authenticated', u.email,
  extensions.crypt('password123', extensions.gen_salt('bf')), now(), u.phone, now(),
  '{"provider": "email", "providers": ["email", "phone"]}', jsonb_build_object('name', u.name), now(), now(),
  '', '', '', ''
from (values
  ('a1000000-0000-4000-8000-000000000001'::uuid, 'owner@irontemple.test', '919000000001', 'Harsh'),
  ('a1000000-0000-4000-8000-000000000002'::uuid, 'staff@irontemple.test', '919000000002', 'Ravi'),
  ('b1000000-0000-4000-8000-000000000001'::uuid, 'owner@shakti.test', '919000000003', 'Meena'),
  ('c1000000-0000-4000-8000-000000000001'::uuid, 'new@example.test', '919000000004', 'Naya')
) as u (id, email, phone, name);

insert into auth.identities (provider_id, user_id, identity_data, provider, last_sign_in_at, created_at, updated_at)
select u.id::text, u.id, jsonb_build_object('sub', u.id::text, 'email', u.email, 'email_verified', true),
       'email', now(), now(), now()
from auth.users u;

-- Gyms and staff -----------------------------------------------------------------------------------

insert into public.gyms (id, name, address) values
  ('a0000000-0000-4000-8000-00000000000a', 'Iron Temple Gym', 'Main Road, Ranchi'),
  ('b0000000-0000-4000-8000-00000000000b', 'Shakti Fitness', 'Lalpur, Ranchi');

insert into public.gym_staff (gym_id, user_id, role, name) values
  ('a0000000-0000-4000-8000-00000000000a', 'a1000000-0000-4000-8000-000000000001', 'owner', 'Harsh'),
  ('a0000000-0000-4000-8000-00000000000a', 'a1000000-0000-4000-8000-000000000002', 'staff', 'Ravi'),
  ('b0000000-0000-4000-8000-00000000000b', 'b1000000-0000-4000-8000-000000000001', 'owner', 'Meena');

insert into public.gym_invites (code, gym_id, role, created_by) values
  ('IRON2345', 'a0000000-0000-4000-8000-00000000000a', 'staff', 'a1000000-0000-4000-8000-000000000001');

-- Members, plans, memberships and payments ---------------------------------------------------------

-- Adds plans and members to a gym. Days are offsets from today; payments are [amount, day, mode].
-- Payments in the last week are recorded by recent_staff, older ones by the owner.
create function pg_temp.seed_gym(gym uuid, owner_id uuid, recent_staff uuid, plans jsonb, members jsonb)
returns void
language plpgsql
as $$
declare
  today date := (now() at time zone 'Asia/Kolkata')::date;
  m jsonb;
  sale jsonb;
  payment jsonb;
  member_id uuid;
  plan public.plans;
  membership_id uuid;
begin
  insert into public.plans (gym_id, name, duration_value, duration_unit, default_price)
  select gym, p ->> 'name', (p ->> 'value')::int, (p ->> 'unit')::public.duration_unit, (p ->> 'price')::bigint
  from jsonb_array_elements(plans) p;

  for m in select * from jsonb_array_elements(members) loop
    insert into public.members (gym_id, name, gender, joined_on, created_at)
    values (gym, m ->> 'name', (m ->> 'gender')::public.gender, today + (m ->> 'joined')::int,
            now() + make_interval(days => (m ->> 'joined')::int))
    returning id into member_id;

    for sale in select * from jsonb_array_elements(coalesce(m -> 'sales', '[]')) loop
      select * into plan from public.plans p where p.gym_id = gym and p.name = sale ->> 'plan';

      insert into public.memberships (gym_id, member_id, plan_id, plan_name, start_date, end_date, price, discount, created_by)
      values (gym, member_id, plan.id, plan.name, today + (sale ->> 'start')::int, today + (sale ->> 'end')::int,
              plan.default_price, coalesce((sale ->> 'discount')::bigint, 0), owner_id)
      returning id into membership_id;

      for payment in select * from jsonb_array_elements(coalesce(sale -> 'payments', '[]')) loop
        insert into public.payments (gym_id, membership_id, amount, mode, paid_on, created_by)
        values (gym, membership_id, (payment ->> 0)::bigint, (payment ->> 2)::public.payment_mode,
                today + (payment ->> 1)::int,
                case when (payment ->> 1)::int >= -7 then recent_staff else owner_id end);
      end loop;
    end loop;
  end loop;
end;
$$;

select pg_temp.seed_gym(
  'a0000000-0000-4000-8000-00000000000a',
  'a1000000-0000-4000-8000-000000000001',
  'a1000000-0000-4000-8000-000000000002',
  '[
    {"name": "Gym Monthly",   "value": 1, "unit": "MONTHS", "price": 1200},
    {"name": "Gym 3 months",  "value": 3, "unit": "MONTHS", "price": 3300},
    {"name": "MMA Monthly",   "value": 1, "unit": "MONTHS", "price": 1500},
    {"name": "Zumba Monthly", "value": 1, "unit": "MONTHS", "price": 1000},
    {"name": "1-day trial",   "value": 1, "unit": "DAYS",   "price": 100}
  ]',
  '[
    {"name": "Rohan Verma", "gender": "MALE", "joined": -70,
     "sales": [{"plan": "Gym 3 months", "start": -70, "end": 21, "payments": [[3300, -70, "UPI"]]}]},

    {"name": "Priya Sharma", "gender": "FEMALE", "joined": -27,
     "sales": [{"plan": "Zumba Monthly", "start": -27, "end": 3, "payments": [[1000, -27, "CASH"]]}]},
    {"name": "Aman Gupta", "gender": "MALE", "joined": -29,
     "sales": [{"plan": "MMA Monthly", "start": -29, "end": 1, "payments": [[1500, -29, "UPI"]]}]},
    {"name": "Neha Kumari", "gender": "FEMALE", "joined": -30,
     "sales": [{"plan": "Gym Monthly", "start": -30, "end": 0, "payments": [[1200, -30, "CASH"]]}]},

    {"name": "Vikash Yadav", "gender": "MALE", "joined": -33,
     "sales": [{"plan": "Gym Monthly", "start": -33, "end": -3, "payments": [[1200, -33, "CASH"]]}]},
    {"name": "Sunita Devi", "gender": "FEMALE", "joined": -40,
     "sales": [{"plan": "Zumba Monthly", "start": -40, "end": -10, "payments": [[1000, -40, "CASH"]]}]},
    {"name": "Deepak Oraon", "gender": "MALE", "joined": -80,
     "sales": [{"plan": "MMA Monthly", "start": -80, "end": -51, "payments": [[1500, -80, "CASH"]]},
               {"plan": "MMA Monthly", "start": -50, "end": -20, "payments": [[1500, -50, "UPI"]]}]},
    {"name": "Sneha Raj", "gender": "FEMALE", "joined": -1,
     "sales": [{"plan": "1-day trial", "start": -1, "end": -1, "payments": [[100, -1, "CASH"]]}]},

    {"name": "Arjun Singh", "gender": "MALE", "joined": -20,
     "sales": [{"plan": "MMA Monthly", "start": -20, "end": 10, "discount": 200, "payments": [[800, -20, "UPI"]]}]},
    {"name": "Rahul Pandey", "gender": "MALE", "joined": -8,
     "sales": [{"plan": "Gym Monthly", "start": -8, "end": 22, "payments": [[600, -8, "CASH"]]}]},
    {"name": "Nisha Gupta", "gender": "FEMALE", "joined": -25,
     "sales": [{"plan": "Zumba Monthly", "start": -25, "end": 5}]},

    {"name": "Kavya Mishra", "gender": "FEMALE", "joined": -45,
     "sales": [{"plan": "Gym 3 months", "start": -45, "end": 46, "payments": [[2000, -45, "UPI"], [1300, -12, "UPI"]]}]},
    {"name": "Pooja Kumari", "gender": "FEMALE", "joined": -15,
     "sales": [{"plan": "Zumba Monthly", "start": -15, "end": 15, "payments": [[1000, -15, "CASH"]]}]},
    {"name": "Anjali Tirkey", "gender": "FEMALE", "joined": -12,
     "sales": [{"plan": "Gym Monthly", "start": -12, "end": 18, "payments": [[1200, -2, "UPI"]]}]},
    {"name": "Manish Kumar", "gender": "MALE", "joined": -5,
     "sales": [{"plan": "Gym 3 months", "start": -5, "end": 86, "discount": 300, "payments": [[3000, -5, "UPI"]]}]},
    {"name": "Suraj Mahto", "gender": "MALE", "joined": -3,
     "sales": [{"plan": "MMA Monthly", "start": -3, "end": 27, "payments": [[1500, 0, "CASH"]]}]},
    {"name": "Ritu Singh", "gender": "FEMALE", "joined": -61,
     "sales": [{"plan": "Gym Monthly", "start": -61, "end": -31, "payments": [[1200, -61, "CASH"]]},
               {"plan": "Gym Monthly", "start": -30, "end": -5, "payments": [[1200, -30, "CASH"]]},
               {"plan": "Gym Monthly", "start": -4, "end": 26, "payments": [[1200, -4, "UPI"]]}]},

    {"name": "Ravi Shankar", "gender": "MALE", "joined": -2}
  ]'
);

-- A smaller second gym, to check one gym never sees another's data.
select pg_temp.seed_gym(
  'b0000000-0000-4000-8000-00000000000b',
  'b1000000-0000-4000-8000-000000000001',
  'b1000000-0000-4000-8000-000000000001',
  '[
    {"name": "Monthly", "value": 1, "unit": "MONTHS", "price": 999},
    {"name": "Yoga 15 days", "value": 15, "unit": "DAYS", "price": 600}
  ]',
  '[
    {"name": "Meera Joshi", "gender": "FEMALE", "joined": -10,
     "sales": [{"plan": "Monthly", "start": -10, "end": 20, "payments": [[999, -10, "UPI"]]}]},
    {"name": "Karan Mehta", "gender": "MALE", "joined": -40,
     "sales": [{"plan": "Monthly", "start": -40, "end": -11, "payments": [[999, -40, "CASH"]]}]},
    {"name": "Farhan Ali", "gender": "MALE", "joined": -6,
     "sales": [{"plan": "Yoga 15 days", "start": -6, "end": 8, "payments": [[300, -6, "CASH"]]}]}
  ]'
);
