-- Lets the app hear about changes while it's open, so one phone sees what another just did.
-- Realtime applies each table's row level security, so a phone only hears its own gym's inserts
-- and updates. Deletions can't be checked that way and arrive with just the row's id, which is
-- enough to prompt a reload and reveals nothing else.
alter publication supabase_realtime add table
  public.gyms, public.gym_staff, public.plans, public.members, public.memberships, public.payments;
