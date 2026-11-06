-- Tables for gyms, their staff, and the gym's members, plans, memberships and payments.
-- Mirrors the app's Room schema; money is whole rupees, as in the app.
-- Row level security is switched on here with no policies, so nothing is reachable until the policies migration.

create type public.staff_role as enum ('owner', 'staff');
create type public.gender as enum ('MALE', 'FEMALE', 'OTHER');
create type public.duration_unit as enum ('DAYS', 'MONTHS');
create type public.payment_mode as enum ('CASH', 'UPI', 'OTHER');

-- Keeps updated_at current on every update.
create function public.set_updated_at() returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

create table public.gyms (
  id uuid primary key default gen_random_uuid(),
  name text not null check (length(trim(name)) > 0),
  address text,
  -- Path in the gym-logos bucket; null shows the gym's initials.
  logo_path text,
  -- Decides "today", expiries and monthly totals.
  timezone text not null default 'Asia/Kolkata',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- Who works at which gym. A user belongs to at most one gym.
create table public.gym_staff (
  gym_id uuid not null references public.gyms on delete cascade,
  user_id uuid not null references auth.users on delete cascade,
  role public.staff_role not null,
  -- Shown in the staff list.
  name text,
  joined_at timestamptz not null default now(),
  primary key (gym_id, user_id),
  unique (user_id)
);

-- One-time codes the owner shares to bring staff in.
create table public.gym_invites (
  code text primary key,
  gym_id uuid not null references public.gyms on delete cascade,
  role public.staff_role not null default 'staff',
  created_by uuid not null references auth.users on delete cascade,
  created_at timestamptz not null default now(),
  expires_at timestamptz not null default now() + interval '7 days',
  used_by uuid references auth.users on delete set null,
  used_at timestamptz
);

create table public.plans (
  id uuid primary key default gen_random_uuid(),
  gym_id uuid not null references public.gyms on delete cascade,
  name text not null check (length(trim(name)) > 0),
  duration_value int not null check (duration_value > 0),
  duration_unit public.duration_unit not null,
  default_price bigint not null check (default_price >= 0),
  is_archived boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  -- Lets memberships check their plan belongs to the same gym.
  unique (id, gym_id)
);

create table public.members (
  id uuid primary key default gen_random_uuid(),
  gym_id uuid not null references public.gyms on delete cascade,
  name text not null check (length(trim(name)) > 0),
  phone text,
  gender public.gender,
  date_of_birth date,
  -- Path in the member-photos bucket; the image itself is never stored in the database.
  photo_path text,
  notes text,
  joined_on date not null,
  is_archived boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (id, gym_id)
);

create table public.memberships (
  id uuid primary key default gen_random_uuid(),
  gym_id uuid not null,
  member_id uuid not null,
  plan_id uuid,
  -- Copied from the plan at sale time so renaming a plan doesn't rewrite history.
  plan_name text not null,
  start_date date not null,
  end_date date not null check (end_date >= start_date),
  price bigint not null check (price >= 0),
  -- Whole rupees off price.
  discount bigint not null default 0 check (discount between 0 and price),
  notes text,
  created_by uuid references auth.users on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  -- Gym-scoped foreign keys: a membership can't point at another gym's member or plan.
  foreign key (member_id, gym_id) references public.members (id, gym_id) on delete cascade,
  foreign key (plan_id, gym_id) references public.plans (id, gym_id) on delete set null (plan_id),
  unique (id, gym_id)
);

create table public.payments (
  id uuid primary key default gen_random_uuid(),
  gym_id uuid not null,
  membership_id uuid not null,
  amount bigint not null check (amount > 0),
  mode public.payment_mode not null default 'CASH',
  paid_on date not null,
  note text,
  -- Which staff member collected it.
  created_by uuid references auth.users on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  foreign key (membership_id, gym_id) references public.memberships (id, gym_id) on delete cascade
);

create index gym_invites_gym_id_idx on public.gym_invites (gym_id);
create index plans_gym_id_idx on public.plans (gym_id);
create index members_gym_id_name_idx on public.members (gym_id, name);
create index members_gym_id_phone_idx on public.members (gym_id, phone);
create index memberships_member_id_idx on public.memberships (member_id);
create index memberships_plan_id_idx on public.memberships (plan_id);
create index memberships_gym_id_end_date_idx on public.memberships (gym_id, end_date);
create index payments_membership_id_idx on public.payments (membership_id);
create index payments_gym_id_paid_on_idx on public.payments (gym_id, paid_on);

create trigger set_updated_at before update on public.gyms
  for each row execute function public.set_updated_at();
create trigger set_updated_at before update on public.plans
  for each row execute function public.set_updated_at();
create trigger set_updated_at before update on public.members
  for each row execute function public.set_updated_at();
create trigger set_updated_at before update on public.memberships
  for each row execute function public.set_updated_at();
create trigger set_updated_at before update on public.payments
  for each row execute function public.set_updated_at();

alter table public.gyms enable row level security;
alter table public.gym_staff enable row level security;
alter table public.gym_invites enable row level security;
alter table public.plans enable row level security;
alter table public.members enable row level security;
alter table public.memberships enable row level security;
alter table public.payments enable row level security;
