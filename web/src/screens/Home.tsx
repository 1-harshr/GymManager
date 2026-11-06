import { useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { ArrowRight, ArrowUpRight, MessageCircle, Receipt, Sparkles, TrendingDown, TrendingUp } from 'lucide-react'
import { useGym } from '@/data/session'
import { useAllMemberships, useDailyRevenue, useMemberSummaries, useRecentPayments, useToday } from '@/data/queries'
import { useReminders } from '@/data/reminders'
import { addDays, addMonths, daysBetween, endOfMonth, longDate, monthOf, relativeDay, shortDate, startOfMonth } from '@/lib/dates'
import { brief, dashboard, insights, MODE_LABEL, type Insights, type Pace, type Tone } from '@/lib/domain'
import { formatRupees, plural } from '@/lib/format'
import { usePref } from '@/lib/prefs'
import { PageHeader } from '@/components/Layout'
import { Avatar } from '@/components/Media'
import { Dot } from '@/components/bits'
import { Button, Card, EmptyState, Skeleton, cx } from '@/components/ui'

function greeting(): string {
  const h = new Date().getHours()
  return h < 12 ? 'Good morning' : h < 17 ? 'Good afternoon' : 'Good evening'
}

/** The gym today: money in, who's active and ending, who owes, the latest payments, and what needs doing. */
export function Home() {
  const { membership, isOwner } = useGym()
  const today = useToday()
  const [aiInsights] = usePref('home.aiInsights', true)
  const [showChart] = usePref('home.collectionsChart', true)
  const members = useMemberSummaries()
  const memberships = useAllMemberships()
  const daily = useDailyRevenue(today)
  const recent = useRecentPayments(8)
  const reminders = useReminders()
  const navigate = useNavigate()

  const stats = useMemo(() => (members.data ? dashboard(members.data) : null), [members.data])
  const ins = useMemo(
    () => (members.data && memberships.data && daily.data ? insights(today, members.data, memberships.data, daily.data) : null),
    [members.data, memberships.data, daily.data, today],
  )
  const firstName = membership.name?.split(' ')[0]

  return (
    <>
      <PageHeader
        title={`${greeting()}${firstName ? `, ${firstName}` : ''}`}
        subtitle={`${longDate(today)} · ${membership.gym.name}`}
        actions={
          reminders.toSend > 0 ? (
            <Button kind="secondary" size="sm" icon={MessageCircle} onClick={() => navigate('/reminders')}>
              {plural(reminders.toSend, 'reminder', 'reminders')} to send
            </Button>
          ) : null
        }
      />
      {!stats || !ins ? (
        <div className="grid gap-4 xl:grid-cols-3">
          <Skeleton className="h-32 xl:col-span-3" />
          <Skeleton className="h-80 xl:col-span-2" />
          <Skeleton className="h-80" />
        </div>
      ) : (
        <div className="space-y-4">
          <div className={cx('grid grid-cols-2 gap-3 sm:gap-4', isOwner ? 'xl:grid-cols-5' : 'xl:grid-cols-4')}>
            {isOwner ? <CollectedKpi pace={ins.pace} today={today} /> : null}
            <Kpi tone="active" label="Active members" value={String(stats.active)} to="/members?filter=ACTIVE" />
            <Kpi tone="expiring" label="Expiring this week" value={String(stats.expiringSoon)} to="/members?filter=EXPIRING_SOON" />
            <Kpi tone="expired" label="Expired" value={String(stats.expired)} to="/members?filter=EXPIRED" />
            <Kpi tone="due" label={`Dues from ${plural(stats.withDues, 'member', 'members')}`} value={formatRupees(stats.totalDue)} to="/members?filter=WITH_DUES" />
          </div>

          <div className="grid items-start gap-4 xl:grid-cols-3">
            <div className="space-y-4 xl:col-span-2">
              {isOwner && showChart ? <ChartCard pace={ins.pace} today={today} /> : null}
              <RecentPayments rows={recent.data} today={today} />
            </div>
            <div className="space-y-4">
              {isOwner && aiInsights ? <BriefCard text={brief(ins, today, monthOf)} /> : null}
              {isOwner && aiInsights ? <ForecastCard forecast={ins.forecast} /> : null}
              {aiInsights ? <RenewalsCard ins={ins} today={today} /> : null}
              {aiInsights && (ins.oldestDues.length > 0 || ins.winBack.length > 0) ? <AttentionCard ins={ins} /> : null}
            </div>
          </div>
        </div>
      )}
    </>
  )
}

function Kpi({ tone, label, value, to }: { tone: Tone; label: string; value: string; to: string }) {
  return (
    <Link to={to} className="group rounded-card bg-surface p-5 transition hover:ring-1 hover:ring-line animate-rise">
      <p className="flex items-center gap-2 text-caption text-muted">
        <Dot tone={tone} /> {label}
        <ArrowUpRight className="ml-auto size-4 opacity-0 transition group-hover:opacity-100" />
      </p>
      <p className="mt-3 truncate text-stat">{value}</p>
    </Link>
  )
}

function CollectedKpi({ pace, today }: { pace: Pace; today: string }) {
  const last = monthOf(addMonths(startOfMonth(today), -1))
  const c = pace.changePercent
  return (
    <div className="relative overflow-hidden rounded-card bg-accent p-5 text-on-accent animate-rise col-span-2 xl:col-span-1">
      <div className="pointer-events-none absolute -bottom-12 -right-8 size-32 rounded-full bg-white/10" />
      <p className="text-caption opacity-80">Collected in {monthOf(today)}</p>
      <p className="mt-3 truncate text-stat">{formatRupees(pace.thisMonthToDate)}</p>
      <p className="mt-1 flex items-center gap-1 text-caption">
        {c === null ? (
          <span className="opacity-80">Nothing to compare with in {last}</span>
        ) : c === 0 ? (
          <span className="opacity-80">Level with {last}</span>
        ) : (
          <span className="flex items-center gap-1 rounded-full bg-black/15 px-2 py-0.5 font-semibold">
            {c > 0 ? <TrendingUp className="size-4" /> : <TrendingDown className="size-4" />}
            {Math.abs(c)}% {c > 0 ? 'ahead of' : 'behind'} {last}
          </span>
        )}
      </p>
    </div>
  )
}

/** Running totals through the month, this month against last on the same scale; hover reads a day. */
function ChartCard({ pace, today }: { pace: Pace; today: string }) {
  const [hover, setHover] = useState<number | null>(null)
  const start = startOfMonth(today)
  const lastMonth = monthOf(addMonths(start, -1))
  const days = daysBetween(start, endOfMonth(today)) + 1
  const showLast = pace.lastMonthTotal > 0
  const max = Math.max(1, ...pace.thisMonth, ...(showLast ? pace.lastMonth : []))
  const W = 800
  const H = 240
  const x = (i: number) => (i / Math.max(1, days - 1)) * W
  const y = (v: number) => H - (v / max) * (H - 12) - 6
  const line = (series: number[]) => series.slice(0, days).map((v, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)},${y(v).toFixed(1)}`).join(' ')
  const at = hover ?? pace.thisMonth.length - 1
  const ticks = [0.25, 0.5, 0.75, 1].map((f) => Math.round(max * f))
  return (
    <Card className="p-6 animate-rise">
      <div className="flex flex-wrap items-start gap-4">
        <div className="flex-1">
          <h2 className="text-title">Collections</h2>
          <p className="text-caption text-muted">Running total through the month</p>
        </div>
        <div className="text-right">
          <p className="text-caption text-muted">Day {at + 1}</p>
          <p className="font-display text-[26px] font-bold leading-7 tnum">{formatRupees(pace.thisMonth[at] ?? 0)}</p>
          {showLast ? <p className="text-caption text-muted tnum">{formatRupees(pace.lastMonth[Math.min(at, pace.lastMonth.length - 1)] ?? 0)} in {lastMonth}</p> : null}
        </div>
      </div>
      <div className="relative mt-4 pl-14">
        {ticks.map((t) => (
          <span key={t} className="absolute left-0 w-12 -translate-y-1/2 text-right text-[11px] text-muted tnum" style={{ top: `${(y(t) / H) * 100}%` }}>
            {formatRupees(t)}
          </span>
        ))}
        <svg
          viewBox={`0 0 ${W} ${H}`}
          preserveAspectRatio="none"
          className="h-60 w-full touch-none overflow-visible"
          role="img"
          aria-label={`Collections through the month: ${formatRupees(pace.thisMonthToDate)} so far in ${monthOf(today)}, against ${formatRupees(pace.lastMonthToDate)} in ${lastMonth} by the same date.`}
          onPointerMove={(e) => {
            const r = e.currentTarget.getBoundingClientRect()
            setHover(Math.max(0, Math.min(pace.thisMonth.length - 1, Math.round(((e.clientX - r.left) / r.width) * (days - 1)))))
          }}
          onPointerLeave={() => setHover(null)}
        >
          <defs>
            <linearGradient id="collect-fill" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="var(--accent)" stopOpacity="0.2" />
              <stop offset="100%" stopColor="var(--accent)" stopOpacity="0" />
            </linearGradient>
          </defs>
          {ticks.map((t) => (
            <line key={t} x1="0" x2={W} y1={y(t)} y2={y(t)} stroke="var(--line)" strokeWidth="1" vectorEffect="non-scaling-stroke" />
          ))}
          {showLast ? <path d={line(pace.lastMonth)} fill="none" stroke="var(--chart-reference)" strokeWidth="2" strokeDasharray="5 5" vectorEffect="non-scaling-stroke" /> : null}
          <path d={`${line(pace.thisMonth)} L${x(pace.thisMonth.length - 1)},${H} L0,${H} Z`} fill="url(#collect-fill)" />
          <path d={line(pace.thisMonth)} fill="none" stroke="var(--accent)" strokeWidth="3" strokeLinejoin="round" strokeLinecap="round" vectorEffect="non-scaling-stroke" />
          <line x1={x(at)} x2={x(at)} y1="0" y2={H} stroke="var(--ink)" strokeOpacity="0.18" vectorEffect="non-scaling-stroke" />
        </svg>
        <div className="mt-2 flex items-center justify-between text-caption text-muted">
          <span>{shortDate(start, today)}</span>
          <span className="flex items-center gap-4">
            <span className="flex items-center gap-1.5">
              <span className="h-[3px] w-4 rounded-full bg-accent" /> {monthOf(today)}
            </span>
            {showLast ? (
              <span className="flex items-center gap-1.5">
                <span className="w-4 border-t-2 border-dashed border-chart-ref" /> {lastMonth}
              </span>
            ) : null}
          </span>
          <span>{shortDate(addDays(start, days - 1), today)}</span>
        </div>
      </div>
    </Card>
  )
}

function RecentPayments({ rows, today }: { rows: ReturnType<typeof useRecentPayments>['data']; today: string }) {
  const navigate = useNavigate()
  return (
    <Card className="overflow-hidden animate-rise">
      <div className="flex items-center justify-between px-6 pb-3 pt-5">
        <h2 className="text-title">Recent payments</h2>
      </div>
      {rows && rows.length === 0 ? (
        <EmptyState icon={Receipt} title="No payments yet" body="Payments you record show up here." />
      ) : (
        <table className="w-full text-left">
          <thead>
            <tr className="border-y border-line/70 text-label text-muted">
              <th className="px-6 py-2.5 font-semibold">Member</th>
              <th className="hidden px-3 py-2.5 font-semibold md:table-cell">Plan</th>
              <th className="hidden px-3 py-2.5 font-semibold sm:table-cell">Mode</th>
              <th className="px-3 py-2.5 font-semibold">Date</th>
              <th className="px-6 py-2.5 text-right font-semibold">Amount</th>
            </tr>
          </thead>
          <tbody>
            {(rows ?? []).map((p) => (
              <tr key={p.id} onClick={() => navigate(`/members/${p.member_id}`)} className="cursor-pointer border-b border-line/50 transition last:border-0 hover:bg-sunken/60">
                <td className="px-6 py-3">
                  <span className="flex items-center gap-3">
                    <Avatar name={p.member_name} size={32} />
                    <span className="truncate text-body-strong">{p.member_name}</span>
                  </span>
                </td>
                <td className="hidden px-3 py-3 text-body text-muted md:table-cell">{p.plan_name}</td>
                <td className="hidden px-3 py-3 text-body text-muted sm:table-cell">{MODE_LABEL[p.mode]}</td>
                <td className="px-3 py-3 text-body text-muted">{relativeDay(p.paid_on, today)}</td>
                <td className="px-6 py-3 text-right text-body-strong tnum">{formatRupees(p.amount)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </Card>
  )
}

function BriefCard({ text }: { text: string }) {
  return (
    <Card className="relative overflow-hidden p-6 animate-rise">
      <div className="pointer-events-none absolute -right-16 -top-20 size-56 rounded-full bg-accent-soft blur-3xl" />
      <p className="relative flex items-center gap-2 text-label text-on-accent-soft">
        <Sparkles className="size-4" /> Today’s brief
      </p>
      <p className="relative mt-3 text-[17px] leading-[26px]">{text}</p>
    </Card>
  )
}

function ForecastCard({ forecast }: { forecast: Insights['forecast'] }) {
  const t = forecast.total || 1
  return (
    <Card className="p-6 animate-rise">
      <p className="text-caption text-muted">On track for</p>
      <p className="mt-1 text-stat">{formatRupees(forecast.total)}</p>
      <div className="mt-4 flex h-2.5 gap-0.5 overflow-hidden rounded-full bg-sunken">
        <div className="rounded-full bg-accent transition-all duration-700" style={{ width: `${(forecast.collected / t) * 100}%` }} />
        <div className="rounded-full bg-due/50 transition-all duration-700" style={{ width: `${(forecast.outstandingDues / t) * 100}%` }} />
      </div>
      <ul className="mt-4 space-y-2 text-body">
        <Line dot="bg-accent" label="Collected" amount={forecast.collected} />
        {forecast.outstandingDues > 0 ? <Line dot="bg-due/50" label="Still due" amount={forecast.outstandingDues} /> : null}
        {forecast.renewalsCount > 0 ? <Line dot="bg-line" label={`${plural(forecast.renewalsCount, 'renewal', 'renewals')} this month`} amount={forecast.renewalsValue} /> : null}
      </ul>
      <p className="mt-3 text-caption text-muted">If every due is paid and every membership ending this month renews.</p>
    </Card>
  )
}

function Line({ dot, label, amount }: { dot: string; label: string; amount: number }) {
  return (
    <li className="flex items-center gap-2">
      <span className={cx('size-2.5 rounded-full', dot)} />
      <span className="flex-1">{label}</span>
      <span className="font-semibold tnum">{formatRupees(amount)}</span>
    </li>
  )
}

function Row({ to, name, detail, amount, tone }: { to: string; name: string; detail: string; amount: number; tone?: 'due' | 'expiring' }) {
  return (
    <li>
      <Link to={to} className="-mx-2 flex items-center gap-3 rounded-[12px] px-2 py-2 transition hover:bg-sunken">
        <span className="min-w-0 flex-1">
          <span className="block truncate text-body-strong">{name}</span>
          <span className={cx('block truncate text-caption', tone === 'expiring' ? 'text-expiring' : 'text-muted')}>{detail}</span>
        </span>
        <span className={cx('text-body-strong tnum', tone === 'due' && 'text-due')}>{formatRupees(amount)}</span>
      </Link>
    </li>
  )
}

function RenewalsCard({ ins, today }: { ins: Insights; today: string }) {
  const n = ins.endingSoon.length
  const ends = (end: string) => {
    const d = daysBetween(today, end)
    return d === 0 ? 'Ends today' : d === 1 ? 'Ends tomorrow' : `Ends ${shortDate(end, today)}`
  }
  return (
    <Card className="p-6 animate-rise">
      <div className="flex items-baseline justify-between gap-3">
        <h2 className="text-title">Renewals</h2>
        <p className="text-caption text-muted">{n === 0 ? 'None end this week' : `${n} ${n === 1 ? 'ends' : 'end'} this week · ${formatRupees(ins.atStake)}`}</p>
      </div>
      {n > 0 ? <ul className="mt-2">{ins.endingSoon.slice(0, 5).map((e) => <Row key={e.memberId} to={`/members/${e.memberId}`} name={e.name} detail={ends(e.endDate)} amount={e.value} tone="expiring" />)}</ul> : null}
      {n > 5 ? (
        <Link to="/members?filter=EXPIRING_SOON" className="mt-1 flex items-center gap-1 text-caption font-semibold text-muted hover:text-ink">
          +{n - 5} more <ArrowRight className="size-3.5" />
        </Link>
      ) : null}
      <div className="my-3 h-px bg-line" />
      <p className="text-caption text-muted">
        {ins.renewal.ended === 0 ? 'No memberships ended in the last 30 days' : `${ins.renewal.renewed} of ${ins.renewal.ended} renewed in the last 30 days (${ins.renewal.percent}%)`}
      </p>
    </Card>
  )
}

function AttentionCard({ ins }: { ins: Insights }) {
  return (
    <Card className="p-6 animate-rise">
      <h2 className="text-title">Needs attention</h2>
      {ins.oldestDues.length > 0 ? (
        <>
          <p className="mt-4 text-label text-muted">Longest unpaid</p>
          <ul className="mt-1">{ins.oldestDues.map((d) => <Row key={d.memberId} to={`/members/${d.memberId}`} name={d.name} detail={plural(d.days, 'day', 'days')} amount={d.amount} tone="due" />)}</ul>
          {ins.overdueCount > ins.oldestDues.length ? <p className="text-caption text-muted">+{ins.overdueCount - ins.oldestDues.length} more</p> : null}
        </>
      ) : null}
      {ins.winBack.length > 0 ? (
        <>
          <p className="mt-4 text-label text-muted">Win back</p>
          <ul className="mt-1">
            {ins.winBack.map((w) => <Row key={w.memberId} to={`/members/${w.memberId}`} name={w.name} detail={`Ended ${plural(w.daysAgo, 'day', 'days')} ago · ${w.planName}`} amount={w.value} />)}
          </ul>
          {ins.winBackCount > ins.winBack.length ? <p className="text-caption text-muted">+{ins.winBackCount - ins.winBack.length} more</p> : null}
        </>
      ) : null}
    </Card>
  )
}
