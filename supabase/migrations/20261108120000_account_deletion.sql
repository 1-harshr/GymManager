-- Deleting your own account, as app stores require. Staff go and the records they made stay with the
-- gym. The gym's only owner first hands the gym to someone else if anyone else works there; if nobody
-- does, the gym goes with them.
--
-- The gym's photos and logo live in Storage, which SQL can't empty, so the app removes them (under
-- <gym_id>/ in member-photos and gym-logos) before calling delete_account when the gym is going too.
--
-- Errors the app should recognise: not_signed_in, hand_over_first.

-- What deleting the signed-in person's account would do:
--   'account'    only the account goes (staff, another owner remains, or not in a gym)
--   'gym'        the account and the whole gym go, since they're its only owner and alone there
--   'hand_over'  they're the only owner and others work there, so someone else must be made owner first
create function public.account_deletion_plan() returns text
language plpgsql stable security definer set search_path = '' as $$
declare
  me uuid := auth.uid();
  my_gym uuid;
  my_role public.staff_role;
begin
  if me is null then
    raise exception 'not_signed_in';
  end if;
  select gym_id, role into my_gym, my_role from public.gym_staff where user_id = me;
  if my_role is distinct from 'owner'
    or exists (select 1 from public.gym_staff where gym_id = my_gym and role = 'owner' and user_id <> me) then
    return 'account';
  end if;
  if exists (select 1 from public.gym_staff where gym_id = my_gym and user_id <> me) then
    return 'hand_over';
  end if;
  return 'gym';
end;
$$;

-- Deletes the signed-in person's account, and their gym when account_deletion_plan() says 'gym'.
-- Their place on the staff goes with the account; payments and check-ins they recorded keep the record
-- but forget who made them.
create function public.delete_account() returns void
language plpgsql security definer set search_path = '' as $$
declare
  me uuid := auth.uid();
  plan text := public.account_deletion_plan();
begin
  if plan = 'hand_over' then
    raise exception 'hand_over_first';
  end if;
  if plan = 'gym' then
    delete from public.gyms where id = (select gym_id from public.gym_staff where user_id = me);
  end if;
  delete from auth.users where id = me;
end;
$$;

revoke all on function public.account_deletion_plan(), public.delete_account() from public, anon;
grant execute on function public.account_deletion_plan(), public.delete_account() to authenticated;
