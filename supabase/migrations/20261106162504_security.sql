-- Who can see and change what. Everyone works inside their own gym; owners can also change plans,
-- delete records, archive members, manage staff and edit the gym. Creating gyms, inviting and joining
-- go through functions in a later migration, so the app can't write those tables directly.

-- Helpers live outside the exposed schemas so they can't be called through the API.
create schema private;
grant usage on schema private to authenticated;

-- The gym the signed-in user works at; null if they haven't created or joined one.
create function private.current_gym_id() returns uuid
language sql stable security definer
set search_path = ''
as $$
  select gym_id from public.gym_staff where user_id = auth.uid()
$$;

create function private.is_owner() returns boolean
language sql stable security definer
set search_path = ''
as $$
  select exists (select 1 from public.gym_staff where user_id = auth.uid() and role = 'owner')
$$;

grant execute on function private.current_gym_id() to authenticated;
grant execute on function private.is_owner() to authenticated;

-- Table privileges: nothing for signed-out users, and only the columns the app may change.
-- Leaving gym_id out of every update grant stops rows moving between gyms.
revoke all on public.gyms, public.gym_staff, public.gym_invites, public.plans, public.members,
  public.memberships, public.payments from anon, authenticated;

grant select on public.gyms to authenticated;
grant update (name, address, logo_path, timezone) on public.gyms to authenticated;

grant select, delete on public.gym_staff to authenticated;
grant update (role, name) on public.gym_staff to authenticated;

grant select, delete on public.gym_invites to authenticated;

grant select, insert, delete on public.plans to authenticated;
grant update (name, duration_value, duration_unit, default_price, is_archived) on public.plans to authenticated;

grant select, insert, delete on public.members to authenticated;
grant update (name, phone, gender, date_of_birth, photo_path, notes, joined_on, is_archived)
  on public.members to authenticated;

grant select, insert, delete on public.memberships to authenticated;
grant update (plan_id, plan_name, start_date, end_date, price, discount, notes) on public.memberships to authenticated;

grant select, insert, delete on public.payments to authenticated;
grant update (amount, mode, paid_on, note) on public.payments to authenticated;

-- Record who sold a membership or took a payment.
alter table public.memberships alter column created_by set default auth.uid();
alter table public.payments alter column created_by set default auth.uid();

-- gyms
create policy "staff read their gym" on public.gyms for select to authenticated
  using (id = (select private.current_gym_id()));
create policy "owner edits the gym" on public.gyms for update to authenticated
  using (id = (select private.current_gym_id()) and (select private.is_owner()));

-- gym_staff
create policy "staff see their colleagues" on public.gym_staff for select to authenticated
  using (gym_id = (select private.current_gym_id()));
create policy "owner changes others' roles" on public.gym_staff for update to authenticated
  using (gym_id = (select private.current_gym_id()) and (select private.is_owner()) and user_id <> (select auth.uid()))
  with check (gym_id = (select private.current_gym_id()));
-- The owner removes staff; staff can leave. An owner can't remove themselves and orphan the gym.
create policy "owner removes staff, staff leave" on public.gym_staff for delete to authenticated
  using (
    gym_id = (select private.current_gym_id())
    and (
      ((select private.is_owner()) and user_id <> (select auth.uid()))
      or (user_id = (select auth.uid()) and role = 'staff')
    )
  );

-- gym_invites
create policy "owner sees invites" on public.gym_invites for select to authenticated
  using (gym_id = (select private.current_gym_id()) and (select private.is_owner()));
create policy "owner revokes invites" on public.gym_invites for delete to authenticated
  using (gym_id = (select private.current_gym_id()) and (select private.is_owner()));

-- plans: everyone reads them; only the owner sets them up.
create policy "staff read plans" on public.plans for select to authenticated
  using (gym_id = (select private.current_gym_id()));
create policy "owner adds plans" on public.plans for insert to authenticated
  with check (gym_id = (select private.current_gym_id()) and (select private.is_owner()));
create policy "owner edits plans" on public.plans for update to authenticated
  using (gym_id = (select private.current_gym_id()) and (select private.is_owner()));
create policy "owner deletes plans" on public.plans for delete to authenticated
  using (gym_id = (select private.current_gym_id()) and (select private.is_owner()));

-- members
create policy "staff read members" on public.members for select to authenticated
  using (gym_id = (select private.current_gym_id()));
create policy "staff add members" on public.members for insert to authenticated
  with check (gym_id = (select private.current_gym_id()));
create policy "staff edit members" on public.members for update to authenticated
  using (gym_id = (select private.current_gym_id()));
create policy "owner deletes members" on public.members for delete to authenticated
  using (gym_id = (select private.current_gym_id()) and (select private.is_owner()));

-- Archiving is the owner's call; a policy can't tell which columns changed, so a trigger checks it.
create function private.only_owner_archives() returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if new.is_archived is distinct from old.is_archived
    and auth.uid() is not null
    and not private.is_owner() then
    raise exception 'Only the gym owner can archive or restore members' using errcode = '42501';
  end if;
  return new;
end;
$$;

create trigger only_owner_archives before update on public.members
  for each row execute function private.only_owner_archives();

-- memberships
create policy "staff read memberships" on public.memberships for select to authenticated
  using (gym_id = (select private.current_gym_id()));
create policy "staff sell memberships" on public.memberships for insert to authenticated
  with check (gym_id = (select private.current_gym_id()) and created_by = (select auth.uid()));
create policy "staff edit memberships" on public.memberships for update to authenticated
  using (gym_id = (select private.current_gym_id()));
create policy "owner deletes memberships" on public.memberships for delete to authenticated
  using (gym_id = (select private.current_gym_id()) and (select private.is_owner()));

-- payments: staff record them; only the owner corrects or deletes them.
create policy "staff read payments" on public.payments for select to authenticated
  using (gym_id = (select private.current_gym_id()));
create policy "staff record payments" on public.payments for insert to authenticated
  with check (gym_id = (select private.current_gym_id()) and created_by = (select auth.uid()));
create policy "owner edits payments" on public.payments for update to authenticated
  using (gym_id = (select private.current_gym_id()) and (select private.is_owner()));
create policy "owner deletes payments" on public.payments for delete to authenticated
  using (gym_id = (select private.current_gym_id()) and (select private.is_owner()));

-- Storage: files live under a folder named after the gym's id, e.g. member-photos/<gym_id>/<member_id>.jpg.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types) values
  ('member-photos', 'member-photos', false, 5242880, array['image/jpeg', 'image/png', 'image/webp', 'image/heic']),
  ('gym-logos', 'gym-logos', false, 5242880, array['image/jpeg', 'image/png', 'image/webp', 'image/heic']);

create policy "staff read their gym's files" on storage.objects for select to authenticated
  using (
    bucket_id in ('member-photos', 'gym-logos')
    and (storage.foldername(name))[1] = (select private.current_gym_id())::text
  );
create policy "staff upload member photos" on storage.objects for insert to authenticated
  with check (
    bucket_id = 'member-photos'
    and (storage.foldername(name))[1] = (select private.current_gym_id())::text
  );
create policy "staff replace member photos" on storage.objects for update to authenticated
  using (
    bucket_id = 'member-photos'
    and (storage.foldername(name))[1] = (select private.current_gym_id())::text
  );
create policy "staff remove member photos" on storage.objects for delete to authenticated
  using (
    bucket_id = 'member-photos'
    and (storage.foldername(name))[1] = (select private.current_gym_id())::text
  );
create policy "owner uploads the logo" on storage.objects for insert to authenticated
  with check (
    bucket_id = 'gym-logos'
    and (storage.foldername(name))[1] = (select private.current_gym_id())::text
    and (select private.is_owner())
  );
create policy "owner replaces the logo" on storage.objects for update to authenticated
  using (
    bucket_id = 'gym-logos'
    and (storage.foldername(name))[1] = (select private.current_gym_id())::text
    and (select private.is_owner())
  );
create policy "owner removes the logo" on storage.objects for delete to authenticated
  using (
    bucket_id = 'gym-logos'
    and (storage.foldername(name))[1] = (select private.current_gym_id())::text
    and (select private.is_owner())
  );
