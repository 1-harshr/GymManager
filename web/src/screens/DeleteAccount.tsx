import { useState } from 'react'
import { useNavigate } from 'react-router'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { accountDeletionPlan, deleteAccount } from '@/data/account'
import { explain } from '@/data/errors'
import { PageHeader } from '@/components/Layout'
import { Button, Card, Field, Skeleton, TextInput } from '@/components/ui'

/**
 * Deleting your own account for good. Says what goes with it first: only the account, the whole gym when
 * they're its only owner and alone there, or nothing yet when someone else must be made owner. Success
 * signs out, which takes the browser back to sign-in. [gym] is null for someone not in a gym.
 */
export function DeleteAccountPanel({ gym, onOpenStaff }: { gym: { id: string; name: string } | null; onOpenStaff?: () => void }) {
  const client = useQueryClient()
  const plan = useQuery({ queryKey: ['account-deletion'], queryFn: accountDeletionPlan, staleTime: 0 })
  const [typed, setTyped] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (plan.isPending) return <Skeleton className="h-40" />
  if (plan.isError) {
    return (
      <Card className="space-y-3 p-5">
        <p className="text-body-strong">Couldn’t check your account</p>
        <p className="text-body text-muted">{explain(plan.error)}</p>
        <Button kind="secondary" onClick={() => plan.refetch()}>
          Try again
        </Button>
      </Card>
    )
  }

  const name = gym?.name ?? ''
  if (plan.data === 'hand_over') {
    return (
      <Card className="space-y-4 p-5">
        <p className="text-body">
          You’re the only owner of {name} and others work there. Make one of them an owner from Staff, then come back to delete your account.
        </p>
        {onOpenStaff ? (
          <Button kind="secondary" onClick={onOpenStaff}>
            Open Staff
          </Button>
        ) : null}
      </Card>
    )
  }

  const withGym = plan.data === 'gym'
  // Deleting the gym asks for its name first, so it can't happen by a slip.
  const confirmed = !withGym || typed.trim().toLowerCase() === name.trim().toLowerCase()
  return (
    <Card className="space-y-5 p-5">
      <p className="text-body">
        {withGym
          ? `You’re the only one at ${name}, so the gym is deleted too: every member, plan, membership, payment, check-in and photo. This can’t be undone.`
          : gym
            ? `You’ll be signed out and won’t be able to open ${name} again unless you’re invited back. The members, payments and check-ins you recorded stay with the gym.`
            : 'You’ll be signed out, and your account and sign-in details are deleted.'}
      </p>
      {withGym ? (
        <Field label={`Type ${name} to confirm`}>
          <TextInput value={typed} onChange={(e) => setTyped(e.target.value)} placeholder={name} autoComplete="off" />
        </Field>
      ) : null}
      {error ? <p className="text-caption text-due">{error}</p> : null}
      <Button
        kind="danger"
        busy={busy}
        disabled={!confirmed}
        onClick={async () => {
          setBusy(true)
          setError(null)
          try {
            await deleteAccount(gym?.id ?? null)
            client.clear()
          } catch (e) {
            setError(explain(e))
            setBusy(false)
            // Someone may have joined or left meanwhile, changing what deleting would do.
            plan.refetch()
          }
        }}
      >
        {withGym ? 'Delete my account and gym' : 'Delete my account'}
      </Button>
    </Card>
  )
}

/** Settings → Delete account, and /delete-account for anyone signed in to a gym. */
export function DeleteAccount({ gym }: { gym: { id: string; name: string } }) {
  const navigate = useNavigate()
  return (
    <div className="max-w-xl">
      <PageHeader title="Delete your account" back="/settings" />
      <DeleteAccountPanel gym={gym} onOpenStaff={() => navigate('/settings/staff')} />
    </div>
  )
}
