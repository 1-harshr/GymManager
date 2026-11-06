import { useState } from 'react'
import { Copy, LogOut, MessageCircle, Plus, ShieldCheck, UserMinus, Users } from 'lucide-react'
import { toast } from 'sonner'
import { useQueryClient } from '@tanstack/react-query'
import { supabase } from '@/lib/supabase'
import { fullDate } from '@/lib/dates'
import type { Invite, StaffMember } from '@/lib/types'
import { useGym } from '@/data/session'
import { useInvites, useStaff, useWrite } from '@/data/queries'
import { explain } from '@/data/errors'
import { PageHeader } from '@/components/Layout'
import { Avatar } from '@/components/Media'
import { Badge, Menu } from '@/components/bits'
import { Button, Card, ConfirmSheet, EmptyState, Sheet, Skeleton } from '@/components/ui'

const grouped = (code: string) => `${code.slice(0, 4)}-${code.slice(4)}`
const day = (iso: string) => fullDate(iso.slice(0, 10))

type Confirm = { kind: 'remove'; person: StaffMember } | { kind: 'leave' }

/** Who can open the gym. Owners invite with a one-time code, change roles and remove people; staff can leave. */
export function Staff() {
  const client = useQueryClient()
  const { membership, isOwner, userId } = useGym()
  const staff = useStaff()
  const invites = useInvites(isOwner)
  const [shown, setShown] = useState<Invite | null>(null)
  const [confirm, setConfirm] = useState<Confirm | null>(null)

  const write = useWrite(async (action: () => PromiseLike<{ error: unknown }>) => {
    const { error } = await action()
    if (error) throw error
  })
  const run = (action: () => PromiseLike<{ error: unknown }>, done: string, after?: () => void) =>
    write.mutate(action, {
      onSuccess: () => {
        toast(done)
        setConfirm(null)
        after?.()
      },
      onError: (e) => toast(explain(e)),
    })

  const invite = useWrite(async () => {
    const { data: code, error } = await supabase.rpc('create_invite', { role: 'staff' })
    if (error) throw error
    const { data, error: readError } = await supabase.from('gym_invites').select('code, role, expires_at').eq('code', code).single<Invite>()
    if (readError) throw readError
    return data
  })

  return (
    <div className="max-w-4xl">
      <PageHeader
        title="Staff"
        subtitle={`Everyone who can open ${membership.gym.name}`}
        actions={
          isOwner ? (
            <Button size="sm" icon={Plus} busy={invite.isPending} onClick={() => invite.mutate(undefined, { onSuccess: setShown, onError: (e) => toast(explain(e)) })}>
              Invite staff
            </Button>
          ) : null
        }
      />
      {staff.isError && !staff.data ? (
        <Card className="p-6">
          <p className="text-body-strong">Couldn’t load the staff list.</p>
          <p className="mt-1 text-caption text-muted">{explain(staff.error)}</p>
          <Button className="mt-4" kind="secondary" size="sm" onClick={() => staff.refetch()}>
            Try again
          </Button>
        </Card>
      ) : !staff.data ? (
        <Skeleton className="h-48" />
      ) : (
        <Card className="overflow-hidden">
          <table className="w-full text-left">
            <thead>
              <tr className="border-b border-line/70 text-label text-muted">
                <th className="px-5 py-3 font-semibold">Name</th>
                <th className="hidden px-3 py-3 font-semibold sm:table-cell">Joined</th>
                <th className="px-3 py-3 font-semibold">Role</th>
                <th className="w-16" />
              </tr>
            </thead>
            <tbody>
              {staff.data.map((p) => {
                const you = p.user_id === userId
                return (
                  <tr key={p.user_id} className="border-b border-line/50 last:border-0">
                    <td className="px-5 py-3.5">
                      <span className="flex items-center gap-3">
                        <Avatar name={p.name || '?'} size={36} />
                        <span className="text-body-strong">{p.name || 'No name'}</span>
                        {you ? <span className="rounded-full bg-sunken px-2 py-0.5 text-[12px] font-semibold text-muted">You</span> : null}
                      </span>
                    </td>
                    <td className="hidden px-3 py-3.5 text-body text-muted sm:table-cell">{day(p.joined_at)}</td>
                    <td className="px-3 py-3.5">
                      <Badge tone={p.role === 'owner' ? 'active' : 'expired'} dot={false}>
                        {p.role === 'owner' ? 'Owner' : 'Staff'}
                      </Badge>
                    </td>
                    <td className="pr-4 text-right">
                      {isOwner && !you ? (
                        <Menu
                          label={`More for ${p.name || 'this person'}`}
                          filled={false}
                          items={[
                            {
                              label: p.role === 'owner' ? 'Make staff' : 'Make owner',
                              icon: ShieldCheck,
                              onClick: () => run(() => supabase.from('gym_staff').update({ role: p.role === 'owner' ? 'staff' : 'owner' }).eq('user_id', p.user_id), 'Role changed'),
                            },
                            { label: 'Remove from gym', icon: UserMinus, destructive: true, onClick: () => setConfirm({ kind: 'remove', person: p }) },
                          ]}
                        />
                      ) : null}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </Card>
      )}

      {isOwner && invites.data && invites.data.length > 0 ? (
        <section className="mt-8">
          <h2 className="pb-3 text-title">Waiting to be used</h2>
          <div className="grid gap-3 sm:grid-cols-2">
            {invites.data.map((i) => (
              <Card key={i.code} className="flex items-center gap-4 p-5">
                <span className="flex-1">
                  <span className="block font-display text-[26px] font-bold tracking-[0.1em]">{grouped(i.code)}</span>
                  <span className="block text-caption text-muted">Expires {day(i.expires_at)}</span>
                </span>
                <Button size="sm" kind="ghost" onClick={() => setShown(i)}>
                  Share
                </Button>
                <Button size="sm" kind="danger" onClick={() => run(() => supabase.from('gym_invites').delete().eq('code', i.code), 'Invite cancelled')}>
                  Cancel
                </Button>
              </Card>
            ))}
          </div>
        </section>
      ) : null}

      {!isOwner && staff.data ? (
        <div className="mt-8">
          <Button kind="danger" icon={LogOut} onClick={() => setConfirm({ kind: 'leave' })}>
            Leave this gym
          </Button>
        </div>
      ) : null}
      {staff.data?.length === 0 ? <EmptyState icon={Users} title="No one here yet" /> : null}

      <Sheet open={!!shown} onClose={() => setShown(null)} title="Share this code">
        {shown ? <InviteBody invite={shown} gymName={membership.gym.name} onDone={() => setShown(null)} /> : null}
      </Sheet>
      {confirm?.kind === 'remove' ? (
        <ConfirmSheet
          open
          busy={write.isPending}
          title={`Remove ${confirm.person.name || 'them'}?`}
          body="They won’t be able to open the gym any more. You can invite them again later."
          confirmLabel="Remove from gym"
          onClose={() => setConfirm(null)}
          onConfirm={() => run(() => supabase.from('gym_staff').delete().eq('user_id', confirm.person.user_id), 'Removed from the gym')}
        />
      ) : null}
      {confirm?.kind === 'leave' ? (
        <ConfirmSheet
          open
          busy={write.isPending}
          title={`Leave ${membership.gym.name}?`}
          body="You won’t be able to open it again unless the owner invites you."
          confirmLabel="Leave this gym"
          onClose={() => setConfirm(null)}
          onConfirm={() => run(() => supabase.from('gym_staff').delete().eq('user_id', userId), 'You left the gym', () => client.invalidateQueries({ queryKey: ['membership'] }))}
        />
      ) : null}
    </div>
  )
}

function InviteBody({ invite, gymName, onDone }: { invite: Invite; gymName: string; onDone: () => void }) {
  const text = `Join ${gymName} on My Gym: install the app, sign in with your mobile number, choose “I work at a gym” and enter the code ${grouped(invite.code)}`
  return (
    <div className="pb-2">
      <div className="rounded-[18px] bg-sunken py-6 text-center font-display text-[44px] font-bold tracking-[0.14em]">{grouped(invite.code)}</div>
      <p className="mt-4 text-body text-muted">
        Whoever uses it first joins {gymName} as staff. It works once and expires on {day(invite.expires_at)}.
      </p>
      <div className="mt-6 grid gap-2 sm:grid-cols-2">
        <Button icon={MessageCircle} onClick={() => window.open(`https://wa.me/?text=${encodeURIComponent(text)}`, '_blank')}>
          Share on WhatsApp
        </Button>
        <Button
          kind="secondary"
          icon={Copy}
          onClick={async () => {
            try {
              await navigator.clipboard.writeText(invite.code)
              toast('Code copied')
            } catch {
              toast('Couldn’t copy. Select the code and copy it instead.')
            }
          }}
        >
          Copy code
        </Button>
      </div>
      <Button kind="ghost" className="mt-2 w-full" onClick={onDone}>
        Done
      </Button>
    </div>
  )
}
