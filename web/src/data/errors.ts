import { formatRupees } from '@/lib/format'

/**
 * Words for the failures a person can do something about; the database raises these by name
 * (see supabase/migrations). Anything else gets a plain "something went wrong".
 */
export function explain(error: unknown): string {
  const e = error as { message?: string; details?: string; code?: string; name?: string } | null
  const message = e?.message ?? ''
  if (e?.name === 'AuthRetryableFetchError' || /Failed to fetch|NetworkError|Load failed/i.test(message)) {
    return 'No internet connection. Check it and try again.'
  }
  switch (message) {
    case 'overpayment': {
      const due = Number(e?.details)
      return Number.isFinite(due) && due > 0
        ? `Amount is more than the ${formatRupees(due)} due`
        : 'Nothing is due on this membership'
    }
    case 'not_owner':
      return 'Only the gym’s owner can do that.'
    case 'already_in_gym':
      return 'You’re already part of a gym.'
    case 'invalid_invite':
      return 'That code doesn’t work. It may be used or expired; ask the owner for a new one.'
    case 'member_not_found':
      return 'That member isn’t here any more.'
    case 'plan_not_found':
      return 'That plan isn’t here any more.'
  }
  if (e?.code === '42501') return 'Only the gym’s owner can do that.'
  if (e?.code === '23503') return 'Something it depends on was removed. Refresh and try again.'
  return 'Something went wrong. Please try again.'
}
