import { useMemo, useState } from 'react'
import { Link } from 'react-router'
import { MessageCircle, Search, UserCheck, X } from 'lucide-react'
import { toast } from 'sonner'
import { longDate } from '@/lib/dates'
import { byName, matchesQuery, type MemberView } from '@/lib/domain'
import { formatRupees, plural } from '@/lib/format'
import { useGym } from '@/data/session'
import { useMemberSummaries, useToday, useWrite } from '@/data/queries'
import { checkIn, checkInTime, undoCheckIn, useCheckIns, useGoneQuiet, type CheckIn, type QuietItem } from '@/data/attendance'
import { explain } from '@/data/errors'
import { PageHeader } from '@/components/Layout'
import { Avatar } from '@/components/Media'
import { StatusBadge } from '@/components/MemberBits'
import { Badge, DueBadge } from '@/components/bits'
import { openWhatsApp } from '@/components/WhatsApp'
import { Card, EmptyState, IconButton, Skeleton, cx } from '@/components/ui'

const DAY_MS = 24 * 60 * 60 * 1000
const MAX_MATCHES = 20

/** What staff hear after a check-in: a warning when the plan has ended or money is owed. */
function checkedInNote(m: MemberView): string {
  if (m.status === 'EXPIRED') return `${m.name} checked in · membership has ended`
  if (m.status === 'NONE') return `${m.name} checked in · no membership yet`
  if (m.amount_due > 0) return `${m.name} checked in · ${formatRupees(m.amount_due)} due`
  return `${m.name} checked in`
}

/** Who's in today, a search to check members in as they arrive, and who's gone quiet. */
export function Attendance() {
  const today = useToday()
  const { membership } = useGym()
  const members = useMemberSummaries()
  const checkIns = useCheckIns(today)
  const quiet = useGoneQuiet()
  const [query, setQuery] = useState('')

  const byId = useMemo(() => new Map((members.data ?? []).map((m) => [m.member_id, m])), [members.data])
  const inToday = useMemo(() => new Set((checkIns.data ?? []).map((c) => c.member_id)), [checkIns.data])
  const matches = useMemo(
    () => (query.trim() ? (members.data ?? []).filter((m) => !m.is_archived && matchesQuery(m, query)).sort(byName).slice(0, MAX_MATCHES) : []),
    [members.data, query],
  )
  const visits = (checkIns.data ?? []).flatMap((c) => {
    const member = byId.get(c.member_id)
    return member ? [{ checkIn: c, member }] : []
  })

  const write = useWrite((action: () => Promise<void>) => action())
  const add = (m: MemberView) => {
    if (inToday.has(m.member_id)) {
      toast(`${m.name} is already checked in today`)
      setQuery('')
      return
    }
    write.mutate(() => checkIn(membership.gym.id, m.member_id), {
      onSuccess: () => {
        toast(checkedInNote(m))
        setQuery('')
      },
      onError: (e) => toast(explain(e)),
    })
  }
  const undo = (c: CheckIn, m: MemberView) =>
    write.mutate(() => undoCheckIn(c.id), { onSuccess: () => toast(`Check-in for ${m.name} undone`), onError: (e) => toast(explain(e)) })

  return (
    <>
      <PageHeader title="Here today" subtitle={longDate(today)} />
      <div className="grid items-start gap-6 lg:grid-cols-[1fr_380px]">
        <div className="space-y-6">
          <Card className="overflow-hidden">
            <div className="border-b border-line/70 p-4">
              <div className="flex h-11 items-center gap-2 rounded-full bg-sunken px-4 ring-ink transition focus-within:ring-2">
                <Search className="size-[18px] text-muted" />
                <input
                  autoFocus
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' && matches.length === 1) add(matches[0])
                  }}
                  placeholder="Find a member to check in"
                  className="h-full min-w-0 flex-1 bg-transparent text-body outline-none placeholder:text-muted"
                />
                {query ? (
                  <button aria-label="Clear search" onClick={() => setQuery('')} className="rounded-full p-0.5 text-muted hover:text-ink">
                    <X className="size-4" />
                  </button>
                ) : null}
              </div>
            </div>
            {query.trim() ? (
              matches.length === 0 ? (
                <p className="px-5 py-6 text-body text-muted">No one matches “{query.trim()}”</p>
              ) : (
                <div className="divide-y divide-line/50">
                  {matches.map((m) => (
                    <Row key={m.member_id} member={m} today={today}>
                      {inToday.has(m.member_id) ? (
                        <Badge tone="active">In today</Badge>
                      ) : (
                        <IconButton
                          icon={UserCheck}
                          label={`Check in ${m.name}`}
                          className="bg-active-soft text-active hover:bg-active-soft"
                          disabled={write.isPending}
                          onClick={() => add(m)}
                        />
                      )}
                    </Row>
                  ))}
                </div>
              )
            ) : !members.data || !checkIns.data ? (
              <Skeleton className="m-4 h-40" />
            ) : visits.length === 0 ? (
              <EmptyState icon={UserCheck} title="No one in yet" body="Search above to check members in as they arrive." />
            ) : (
              <>
                <p className="px-5 pb-1 pt-4 text-label text-muted">{plural(visits.length, 'member', 'members')} checked in</p>
                <div className="divide-y divide-line/50">
                  {visits.map(({ checkIn: c, member: m }) => (
                    <Row key={c.id} member={m} today={today} note={`Checked in at ${checkInTime(c)}`}>
                      <IconButton icon={X} label={`Undo check-in for ${m.name}`} filled={false} disabled={write.isPending} onClick={() => undo(c, m)} />
                    </Row>
                  ))}
                </div>
              </>
            )}
          </Card>
        </div>
        <GoneQuiet items={quiet.items} loaded={quiet.loaded} />
      </div>
    </>
  )
}

function Row({ member, today, note, children }: { member: MemberView; today: string; note?: string; children: React.ReactNode }) {
  return (
    <div className="flex items-center gap-4 px-5 py-3.5">
      <Link to={`/members/${member.member_id}`} className="flex min-w-0 flex-1 items-center gap-3">
        <Avatar name={member.name} photoPath={member.photo_path} size={40} />
        <span className="min-w-0">
          <span className="block truncate text-body-strong hover:underline">{member.name}</span>
          {note ? <span className="block text-caption text-muted">{note}</span> : null}
        </span>
      </Link>
      <span className="hidden items-center gap-1.5 sm:flex">
        <StatusBadge member={member} today={today} />
        <DueBadge amount={member.amount_due} />
      </span>
      {children}
    </div>
  )
}

function sentNote(item: QuietItem): string {
  if (!item.message) return 'No mobile number'
  if (item.lastSentAt === null) return ''
  const days = Math.floor((Date.now() - item.lastSentAt) / DAY_MS)
  return days === 0 ? 'Messaged today' : days === 1 ? 'Messaged yesterday' : `Messaged ${plural(days, 'day', 'days')} ago`
}

/** Running plan, but no visit in two weeks: a "we miss you" each. */
function GoneQuiet({ items, loaded }: { items: QuietItem[]; loaded: boolean }) {
  const { membership } = useGym()
  return (
    <Card className="overflow-hidden lg:sticky lg:top-24">
      <div className="px-5 pb-2 pt-5">
        <h2 className="text-title">Gone quiet</h2>
        <p className="mt-0.5 text-caption text-muted">Running plan, but no visit in 2 weeks</p>
      </div>
      {!loaded ? (
        <Skeleton className="m-4 h-32" />
      ) : items.length === 0 ? (
        <p className="px-5 pb-5 pt-2 text-body text-muted">Everyone’s coming in. Members with a running plan who stop coming for 2 weeks show up here.</p>
      ) : (
        <div className="divide-y divide-line/50">
          {items.map((item) => {
            const note = sentNote(item)
            return (
              <div key={item.member.member_id} className="flex items-center gap-3 px-5 py-3">
                <Link to={`/members/${item.member.member_id}`} className="flex min-w-0 flex-1 items-center gap-3">
                  <Avatar name={item.member.name} photoPath={item.member.photo_path} size={36} />
                  <span className="min-w-0">
                    <span className="block truncate text-body-strong hover:underline">{item.member.name}</span>
                    <span className="block text-caption text-muted">Last came {plural(item.daysAway, 'day', 'days')} ago</span>
                    {note ? <span className={cx('block text-caption', item.sentRecently ? 'text-active' : 'text-muted')}>{note}</span> : null}
                  </span>
                </Link>
                {item.message ? (
                  <IconButton
                    icon={MessageCircle}
                    label={`Message ${item.member.name} on WhatsApp`}
                    className={cx(!item.sentRecently && 'bg-active-soft text-active hover:bg-active-soft')}
                    onClick={() => item.message && openWhatsApp(item.message, membership.gym.id)}
                  />
                ) : null}
              </div>
            )
          })}
        </div>
      )}
    </Card>
  )
}
