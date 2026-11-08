-- Attendance: staff check members in when they arrive. One check-in per member per day, on the
-- gym's own date; checking in again the same day changes nothing. Anyone at the gym can undo one.

create table public.check_ins (
  id uuid primary key default gen_random_uuid(),
  gym_id uuid not null,
  member_id uuid not null,
  -- The gym's date, so a late-night visit counts for the day it happened where the gym is.
  checked_in_on date not null default private.gym_today(),
  checked_in_at timestamptz not null default now(),
  -- Which staff member checked them in.
  created_by uuid references auth.users on delete set null default auth.uid(),
  foreign key (member_id, gym_id) references public.members (id, gym_id) on delete cascade,
  unique (member_id, checked_in_on)
);

create index check_ins_gym_id_checked_in_on_idx on public.check_ins (gym_id, checked_in_on);

alter table public.check_ins enable row level security;

-- The app names only the gym and member; the date and time are always now, at the gym.
revoke all on public.check_ins from anon, authenticated;
grant select, delete on public.check_ins to authenticated;
grant insert (gym_id, member_id) on public.check_ins to authenticated;

create policy "staff read check-ins" on public.check_ins for select to authenticated
  using (gym_id = (select private.current_gym_id()));
create policy "staff check members in" on public.check_ins for insert to authenticated
  with check (gym_id = (select private.current_gym_id()) and created_by = (select auth.uid()));
create policy "staff undo check-ins" on public.check_ins for delete to authenticated
  using (gym_id = (select private.current_gym_id()));

-- Each member's latest visit, for spotting who has stopped coming. Runs with the caller's rights.
create view public.member_last_check_in with (security_invoker = true) as
select member_id, max(checked_in_on) as last_check_in_on
from public.check_ins
group by member_id;

revoke all on public.member_last_check_in from anon, authenticated;
grant select on public.member_last_check_in to authenticated;

alter publication supabase_realtime add table public.check_ins;
