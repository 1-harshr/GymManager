import { useEffect, useRef } from 'react'
import { Link } from 'react-router'
import { Bell, CalendarClock, Clock, RefreshCw } from 'lucide-react'
import { relativeDay, shortDate } from '@/lib/dates'
import { formatRupees } from '@/lib/format'
import { useInbox, type InboxItem } from '@/data/inbox'
import { useToday } from '@/data/queries'
import { PageHeader } from '@/components/Layout'
import { Card, EmptyState, Skeleton, cx } from '@/components/ui'

/** Memberships ending today, ones that lapsed this week, and dues unpaid for two weeks; newest first. */
export function Inbox() {
  const today = useToday()
  const { items, loaded, markAllRead } = useInbox()
  // What was unread on arrival stays highlighted while the page is open, though it's now marked read.
  const unreadOnArrival = useRef<Set<string> | null>(null)
  if (unreadOnArrival.current === null && loaded) unreadOnArrival.current = new Set(items.filter((i) => !i.read).map((i) => i.eventKey))
  useEffect(() => {
    if (loaded && items.some((i) => !i.read)) markAllRead()
  }, [loaded, items, markAllRead])

  const groups = new Map<string, InboxItem[]>()
  for (const item of items) groups.set(item.createdOn, [...(groups.get(item.createdOn) ?? []), item])

  return (
    <div className="max-w-3xl">
      <PageHeader title="Alerts" subtitle="Renewals due today, lapsed members and long-unpaid dues" />
      {!loaded ? (
        <Skeleton className="h-64" />
      ) : items.length === 0 ? (
        <Card>
          <EmptyState icon={Bell} title="No alerts" body="Renewals due today, lapsed members and long-unpaid dues show up here." />
        </Card>
      ) : (
        <div className="space-y-6">
          {[...groups].map(([day, list]) => (
            <section key={day}>
              <h2 className="px-1 pb-2 text-label text-muted">{relativeDay(day, today)}</h2>
              <Card className="divide-y divide-line/50 overflow-hidden">
                {list.map((item) => (
                  <Alert key={item.eventKey} item={item} fresh={unreadOnArrival.current?.has(item.eventKey) ?? false} today={today} />
                ))}
              </Card>
            </section>
          ))}
        </div>
      )}
    </div>
  )
}

function Alert({ item, fresh, today }: { item: InboxItem; fresh: boolean; today: string }) {
  const [Icon, title, detail, tint] =
    item.kind === 'ENDS_TODAY'
      ? [CalendarClock, `${item.memberName}’s membership ends today`, `Renewal worth ${formatRupees(item.amount)}`, 'bg-expiring-soft text-expiring']
      : item.kind === 'LAPSED'
        ? [RefreshCw, `${item.memberName}’s membership ended on ${shortDate(item.date, today)}`, 'Not renewed yet', 'bg-expired-soft text-expired']
        : [Clock, `${item.memberName} has owed ${formatRupees(item.amount)} since ${shortDate(item.date, today)}`, 'Unpaid for 2 weeks or more', 'bg-due-soft text-due']
  return (
    <Link to={`/members/${item.memberId}`} className={cx('flex items-center gap-4 px-5 py-4 transition hover:bg-sunken/60', fresh && 'bg-accent-soft/50')}>
      <span className={cx('flex size-10 shrink-0 items-center justify-center rounded-full', tint)}>
        <Icon className="size-5" />
      </span>
      <span className="min-w-0 flex-1">
        <span className="block text-body-strong">{title}</span>
        <span className="block text-caption text-muted">{detail}</span>
      </span>
      {fresh ? <span className="size-2 rounded-full bg-accent" /> : null}
    </Link>
  )
}
