import { supabase, GYM_LOGOS, MEMBER_PHOTOS } from '@/lib/supabase'

/**
 * What deleting the signed-in person's account would do: only the account goes, the whole gym goes too
 * (they're its only owner and alone there), or someone else must be made owner first.
 */
export type AccountDeletion = 'account' | 'gym' | 'hand_over'

export async function accountDeletionPlan(): Promise<AccountDeletion> {
  const { data, error } = await supabase.rpc('account_deletion_plan')
  if (error) throw error
  return data as AccountDeletion
}

/**
 * Deletes the signed-in person's account for good, and their gym with its photos when the plan says so,
 * then signs out of this browser.
 */
export async function deleteAccount(gymId: string | null): Promise<void> {
  // Storage can't be emptied from the database, so the gym's photos go first.
  if (gymId && (await accountDeletionPlan()) === 'gym') await removeGymFiles(gymId)
  const { error } = await supabase.rpc('delete_account')
  if (error) throw error
  // The account is gone, so there's no session left on the server to end.
  await supabase.auth.signOut({ scope: 'local' })
}

/** Room for 10,000 files per bucket, far beyond one photo per member. */
const MAX_FILE_PAGES = 100

async function removeGymFiles(gymId: string): Promise<void> {
  for (const bucket of [MEMBER_PHOTOS, GYM_LOGOS]) {
    const files = supabase.storage.from(bucket)
    for (let round = 0; round < MAX_FILE_PAGES; round++) {
      const { data, error } = await files.list(gymId, { limit: 100 })
      if (error) throw error
      if (!data.length) break
      const { error: removeError } = await files.remove(data.map((f) => `${gymId}/${f.name}`))
      if (removeError) throw removeError
    }
  }
}
