import { useState } from 'react'
import { Link } from 'react-router'
import { CheckCircle2, MessageCircle, SkipForward } from 'lucide-react'
import { firstName } from '@/lib/domain'
import { plural } from '@/lib/format'
import { useGym } from '@/data/session'
import { useReminders, type ReminderItem } from '@/data/reminders'
import { useToday } from '@/data/queries'
import { PageHeader } from '@/components/Layout'
import { Avatar } from '@/components/Media'
import { StatusBadge } from '@/components/MemberBits'
import { DueBadge } from '@/components/bits'
import { openWhatsApp } from '@/components/WhatsApp'
import { Button, Card, EmptyState, IconButton, Skeleton, cx } from '@/components/ui'

const DAY_MS = 24 * 60 * 60 * 1000

function sentNote(item: ReminderItem): string {
  if (!item.message) return 'No mobile number'
  if (item.lastSentAt === null) return ''
  const days = Math.floor((Date.now() - item.lastSentAt) / DAY_MS)
  return days === 0 ? 'Reminded today' : days === 1 ? 'Reminded yesterday' : `Reminded ${plural(days, 'day', 'days')} ago`
}

/** Everyone worth a WhatsApp nudge, and a run that opens them one by one. */
export function Reminders() {
  const today = useToday()
  const { membership } = useGym()
  const { items, loaded } = useReminders()
  const [handled, setHandled] = useState<Set<string>>(new Set())
  const remaining = items.filter((i) => i.message && !i.sentRecently && !handled.has(i.member.member_id))
  const next = remaining[0]
  const total = handled.size + remaining.length

  const send = (item: ReminderItem) => {
    if (item.message && openWhatsApp(item.message, membership.gym.id)) setHandled((h) => new Set(h).add(item.member.member_id))
  }

  return (
    <>
      <PageHeader title="Reminders" subtitle="Ending this week, ended recently or owing money" />
      {!loaded ? (
        <Skeleton className="h-64" />
      ) : items.length === 0 ? (
        <Card>
          <EmptyState icon={MessageCircle} title="No one to remind" body="Members ending this week, ended in the last month or owing money show up here." />
        </Card>
      ) : (
        <div className="grid items-start gap-6 lg:grid-cols-[1fr_340px]">
          <Card className="divide-y divide-line/50 overflow-hidden">
            {items.map((item) => {
              const note = sentNote(item)
              return (
                <div key={item.member.member_id} className="flex items-center gap-4 px-5 py-3.5">
                  <Link to={`/members/${item.member.member_id}`} className="flex min-w-0 flex-1 items-center gap-3">
                    <Avatar name={item.member.name} photoPath={item.member.photo_path} size={40} />
                    <span className="min-w-0">
                      <span className="block truncate text-body-strong hover:underline">{item.member.name}</span>
                      {note ? <span className={cx('block text-caption', item.sentRecently ? 'text-active' : 'text-muted')}>{note}</span> : null}
                    </span>
                  </Link>
                  <span className="hidden items-center gap-1.5 sm:flex">
                    <StatusBadge member={item.member} today={today} />
                    <DueBadge amount={item.member.amount_due} />
                  </span>
                  {item.message ? (
                    <IconButton
                      icon={MessageCircle}
                      label={`Remind ${item.member.name} on WhatsApp`}
                      className={cx(!item.sentRecently && 'bg-active-soft text-active hover:bg-active-soft')}
                      onClick={() => send(item)}
                    />
                  ) : (
                    <span className="size-11" />
                  )}
                </div>
              )
            })}
          </Card>
          <Card className="p-6 lg:sticky lg:top-24">
            <h2 className="text-title">Send them one by one</h2>
            <p className="mt-1 text-body text-muted">Each opens WhatsApp with the message typed in. No one is reminded more than once in 3 days.</p>
            {next ? (
              <>
                <div className="mt-5 flex items-center gap-3 rounded-[16px] bg-sunken p-3">
                  <Avatar name={next.member.name} photoPath={next.member.photo_path} size={40} />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-body-strong">{next.member.name}</span>
                    <span className="block text-caption text-muted">
                      {handled.size + 1} of {total}
                    </span>
                  </span>
                </div>
                <div className="mt-4 flex gap-2">
                  <Button className="flex-1" icon={MessageCircle} onClick={() => send(next)}>
                    Send to {firstName(next.member.name)}
                  </Button>
                  <Button kind="secondary" icon={SkipForward} onClick={() => setHandled((h) => new Set(h).add(next.member.member_id))}>
                    Skip
                  </Button>
                </div>
              </>
            ) : (
              <p className="mt-5 flex items-center gap-2 text-body-strong text-active">
                <CheckCircle2 className="size-5" /> Everyone has been reminded
              </p>
            )}
          </Card>
        </div>
      )}
    </>
  )
}
