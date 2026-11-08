/**
 * The gym's rules, as the phone app has them (sharedLogic/.../domain): member status, what's due, renewal
 * dates, the Home insights, reminders, alerts and suggestion chips. Pure functions of rows and "today".
 */
import { addDays, daysBetween, endOfMonth, membershipEndDate, parseIso, shortDate, startOfMonth, addMonths, type IsoDate } from './dates'
import { formatRupees } from './format'
import type { MemberStatus, MemberSummary, MembershipRecord, Plan } from './types'

export const EXPIRING_SOON_DAYS = 7
export const OVERDUE_DAYS = 14
export const LAPSED_ALERT_DAYS = 7
export const WINDOW_DAYS = 30
export const ATTENTION_DUE_DAYS = 7
export const REMIND_EXPIRED_FOR_DAYS = 30
export const REMINDER_COOLDOWN_MS = 3 * 24 * 60 * 60 * 1000
/** A member with a running plan who hasn't come in this many days has gone quiet. */
export const GONE_QUIET_DAYS = 14
export const MISS_YOU_COOLDOWN_MS = 7 * 24 * 60 * 60 * 1000

/** NONE before any membership; expiring from today to 7 days out (8 calendar days). */
export function memberStatus(latestEnd: IsoDate | null, today: IsoDate): MemberStatus {
  if (!latestEnd) return 'NONE'
  if (latestEnd < today) return 'EXPIRED'
  return daysBetween(today, latestEnd) <= EXPIRING_SOON_DAYS ? 'EXPIRING_SOON' : 'ACTIVE'
}

export type Tone = 'active' | 'expiring' | 'expired' | 'due' | 'accent'

export function statusTone(status: MemberStatus): Tone {
  return status === 'ACTIVE' ? 'active' : status === 'EXPIRING_SOON' ? 'expiring' : 'expired'
}

/** "Expires in 3 days": member detail and membership cards. */
export function statusLabel(status: MemberStatus, latestEnd: IsoDate | null, today: IsoDate): string {
  if (status === 'EXPIRING_SOON' && latestEnd) {
    const days = daysBetween(today, latestEnd)
    if (days === 0) return 'Expires today'
    if (days === 1) return 'Expires tomorrow'
    return `Expires in ${days} days`
  }
  return status === 'ACTIVE' ? 'Active' : status === 'EXPIRED' ? 'Expired' : 'No plan'
}

/** "3 days left": list rows, tiles and reminders. */
export function statusShortLabel(status: MemberStatus, latestEnd: IsoDate | null, today: IsoDate): string {
  if (status === 'EXPIRING_SOON' && latestEnd) {
    const days = daysBetween(today, latestEnd)
    if (days === 0) return 'Ends today'
    if (days === 1) return 'Ends tomorrow'
    return `${days} ${days === 1 ? 'day' : 'days'} left`
  }
  return statusLabel(status, latestEnd, today)
}

export const charged = (m: Pick<MembershipRecord, 'price' | 'discount'>) => m.price - m.discount
export const paid = (m: Pick<MembershipRecord, 'payments'>) => m.payments.reduce((sum, p) => sum + p.amount, 0)
export const due = (m: MembershipRecord) => charged(m) - paid(m)

/** Renewals carry on from a running membership (one ending today included); otherwise they start today. */
export function renewalStartDate(latestEnd: IsoDate | null, today: IsoDate): IsoDate {
  return latestEnd && latestEnd >= today ? addDays(latestEnd, 1) : today
}

export const planEnd = (plan: Pick<Plan, 'duration_value' | 'duration_unit'>, start: IsoDate) =>
  membershipEndDate(start, plan.duration_value, plan.duration_unit)

export function durationLabel(value: number, unit: 'DAYS' | 'MONTHS'): string {
  return unit === 'DAYS' ? `${value} ${value === 1 ? 'day' : 'days'}` : `${value} ${value === 1 ? 'month' : 'months'}`
}

export const MODE_LABEL = { CASH: 'Cash', UPI: 'UPI', OTHER: 'Other' } as const
export const GENDER_LABEL = { MALE: 'Male', FEMALE: 'Female', OTHER: 'Other' } as const

/** "5 Nov – 4 Dec 2026", or both dates in full when the years differ. */
export function dateRange(start: IsoDate, end: IsoDate): string {
  const full = (d: IsoDate) => parseIso(d).toLocaleDateString('en-IN', { timeZone: 'UTC', day: 'numeric', month: 'short', year: 'numeric' })
  const short = (d: IsoDate) => parseIso(d).toLocaleDateString('en-IN', { timeZone: 'UTC', day: 'numeric', month: 'short' })
  return start.slice(0, 4) === end.slice(0, 4) ? `${short(start)} – ${full(end)}` : `${full(start)} – ${full(end)}`
}

/** Indian mobile: 10 digits once a leading 91 or 0 and spreadsheet ".0" are dropped; null otherwise. */
export function normalizeIndianPhone(raw: string | null | undefined): string | null {
  if (!raw) return null
  let digits = raw.trim().replace(/\.0$/, '').replace(/\D/g, '')
  if (digits.length === 12 && digits.startsWith('91')) digits = digits.slice(2)
  else if (digits.length === 11 && digits.startsWith('0')) digits = digits.slice(1)
  return digits.length === 10 ? digits : null
}

export const firstName = (name: string) => name.trim().split(/\s+/)[0] ?? name

// Members list ------------------------------------------------------------------------------------

export type MemberFilter = 'ALL' | 'ACTIVE' | 'EXPIRING_SOON' | 'EXPIRED' | 'WITH_DUES' | 'NO_MEMBERSHIP' | 'ARCHIVED'

export const MEMBER_FILTERS: { value: MemberFilter; label: string }[] = [
  { value: 'ALL', label: 'All' },
  { value: 'ACTIVE', label: 'Active' },
  { value: 'EXPIRING_SOON', label: 'Expiring' },
  { value: 'EXPIRED', label: 'Expired' },
  { value: 'WITH_DUES', label: 'Dues' },
  { value: 'NO_MEMBERSHIP', label: 'No plan' },
  { value: 'ARCHIVED', label: 'Archived' },
]

/** The summary with its status worked out for [today], so every screen agrees with Home. */
export interface MemberView extends MemberSummary {
  status: MemberStatus
}

export function withStatus(rows: MemberSummary[], today: IsoDate): MemberView[] {
  return rows.map((r) => ({ ...r, status: memberStatus(r.latest_end_date, today) }))
}

export function matchesFilter(m: MemberView, filter: MemberFilter): boolean {
  if (filter === 'ARCHIVED') return m.is_archived
  if (m.is_archived) return false
  switch (filter) {
    case 'ALL':
      return true
    case 'ACTIVE':
      return m.status === 'ACTIVE'
    case 'EXPIRING_SOON':
      return m.status === 'EXPIRING_SOON'
    case 'EXPIRED':
      return m.status === 'EXPIRED'
    case 'WITH_DUES':
      return m.amount_due > 0
    case 'NO_MEMBERSHIP':
      return m.status === 'NONE'
  }
}

export function matchesQuery(m: { name: string; phone: string | null }, query: string): boolean {
  const q = query.trim()
  if (!q) return true
  return m.name.toLowerCase().includes(q.toLowerCase()) || (m.phone ?? '').includes(q)
}

export const byName = <T extends { name: string }>(a: T, b: T) => a.name.toLowerCase().localeCompare(b.name.toLowerCase())

// Dashboard and insights --------------------------------------------------------------------------

export interface Dashboard {
  active: number
  expiringSoon: number
  expired: number
  withDues: number
  totalDue: number
}

export function dashboard(members: MemberView[]): Dashboard {
  const live = members.filter((m) => !m.is_archived)
  const owing = live.filter((m) => m.amount_due > 0)
  return {
    active: live.filter((m) => m.status === 'ACTIVE' || m.status === 'EXPIRING_SOON').length,
    expiringSoon: live.filter((m) => m.status === 'EXPIRING_SOON').length,
    expired: live.filter((m) => m.status === 'EXPIRED').length,
    withDues: owing.length,
    totalDue: owing.reduce((s, m) => s + m.amount_due, 0),
  }
}

export interface Pace {
  thisMonth: number[]
  lastMonth: number[]
  thisMonthToDate: number
  lastMonthToDate: number
  lastMonthTotal: number
  /** Whole percent ahead (+) or behind (−) last month by the same day; null with nothing to compare. */
  changePercent: number | null
}

/** Running totals day by day for this month (to today) and all of last month. */
export function pace(daily: Map<IsoDate, number>, today: IsoDate): Pace {
  const thisStart = startOfMonth(today)
  const lastStart = addMonths(thisStart, -1)
  const cumulative = (from: IsoDate, days: number) => {
    const out: number[] = []
    let sum = 0
    for (let i = 0; i < days; i++) {
      sum += daily.get(addDays(from, i)) ?? 0
      out.push(sum)
    }
    return out
  }
  const day = Number(today.slice(8, 10))
  const lastDays = daysBetween(lastStart, thisStart)
  const thisMonth = cumulative(thisStart, day)
  const lastMonth = cumulative(lastStart, lastDays)
  const thisMonthToDate = thisMonth[thisMonth.length - 1] ?? 0
  const lastMonthToDate = lastMonth[Math.min(day, lastDays) - 1] ?? 0
  return {
    thisMonth,
    lastMonth,
    thisMonthToDate,
    lastMonthToDate,
    lastMonthTotal: lastMonth[lastMonth.length - 1] ?? 0,
    changePercent: lastMonthToDate === 0 ? null : Math.trunc(((thisMonthToDate - lastMonthToDate) * 100) / lastMonthToDate),
  }
}

export interface MembershipWithMember extends MembershipRecord {
  member: { id: string; name: string }
}

export interface Insights {
  pace: Pace
  forecast: { collected: number; outstandingDues: number; renewalsValue: number; renewalsCount: number; total: number }
  endingSoon: { memberId: string; name: string; endDate: IsoDate; value: number }[]
  atStake: number
  renewal: { ended: number; renewed: number; percent: number | null }
  oldestDues: { memberId: string; name: string; amount: number; owedSince: IsoDate; days: number }[]
  overdueCount: number
  winBack: { memberId: string; name: string; endedOn: IsoDate; daysAgo: number; planName: string; value: number }[]
  winBackCount: number
}

export function insights(today: IsoDate, members: MemberView[], memberships: MembershipWithMember[], daily: Map<IsoDate, number>): Insights {
  const live = members.filter((m) => !m.is_archived)
  const byMember = new Map<string, MembershipWithMember[]>()
  for (const ms of memberships) byMember.set(ms.member_id, [...(byMember.get(ms.member_id) ?? []), ms])
  const latest = new Map<string, MembershipWithMember>()
  for (const [id, list] of byMember) latest.set(id, list.reduce((a, b) => (b.end_date > a.end_date ? b : a)))

  const p = pace(daily, today)
  const monthEnd = endOfMonth(today)
  const endingThisMonth = [...latest.values()].filter((m) => m.end_date >= today && m.end_date <= monthEnd)
  const outstandingDues = memberships.reduce((s, m) => s + due(m), 0)
  const renewalsValue = endingThisMonth.reduce((s, m) => s + charged(m), 0)

  const endingSoon = live
    .filter((m) => m.status === 'EXPIRING_SOON' && latest.has(m.member_id))
    .map((m) => {
      const l = latest.get(m.member_id)!
      return { memberId: m.member_id, name: m.name, endDate: l.end_date, value: charged(l) }
    })
    .sort((a, b) => a.endDate.localeCompare(b.endDate))

  const windowStart = addDays(today, -WINDOW_DAYS)
  let ended = 0
  let renewed = 0
  for (const list of byMember.values()) {
    const inWindow = list.filter((m) => m.end_date >= windowStart && m.end_date < today)
    if (inWindow.length === 0) continue
    const last = inWindow.reduce((a, b) => (b.start_date > a.start_date ? b : a))
    ended++
    if (list.some((m) => m.start_date > last.start_date)) renewed++
  }

  const names = new Map(live.map((m) => [m.member_id, m.name]))
  const dues: Insights['oldestDues'] = []
  for (const [id, list] of byMember) {
    const owing = list.filter((m) => due(m) > 0)
    if (owing.length === 0 || !names.has(id)) continue
    const owedSince = owing.reduce((a, b) => (b.start_date < a.start_date ? b : a)).start_date
    const days = Math.max(0, daysBetween(owedSince, today))
    if (days >= ATTENTION_DUE_DAYS) dues.push({ memberId: id, name: names.get(id)!, amount: owing.reduce((s, m) => s + due(m), 0), owedSince, days })
  }
  dues.sort((a, b) => b.days - a.days || b.amount - a.amount)

  const lapsed = [...latest.values()]
    .filter((m) => m.end_date >= windowStart && m.end_date < today && names.has(m.member_id))
    .sort((a, b) => b.end_date.localeCompare(a.end_date))
    .map((m) => ({ memberId: m.member_id, name: names.get(m.member_id)!, endedOn: m.end_date, daysAgo: daysBetween(m.end_date, today), planName: m.plan_name, value: charged(m) }))

  return {
    pace: p,
    forecast: {
      collected: p.thisMonthToDate,
      outstandingDues,
      renewalsValue,
      renewalsCount: endingThisMonth.length,
      total: p.thisMonthToDate + outstandingDues + renewalsValue,
    },
    endingSoon,
    atStake: endingSoon.reduce((s, e) => s + e.value, 0),
    renewal: { ended, renewed, percent: ended === 0 ? null : Math.trunc((renewed * 100) / ended) },
    oldestDues: dues.slice(0, 3),
    overdueCount: dues.length,
    winBack: lapsed.slice(0, 3),
    winBackCount: lapsed.length,
  }
}

/** Today's brief, as the phone writes it without its on-device model. */
export function brief(i: Insights, today: IsoDate, monthName: (d: IsoDate) => string): string {
  const lastMonth = monthName(addMonths(startOfMonth(today), -1))
  const collected = formatRupees(i.pace.thisMonthToDate)
  const change = i.pace.changePercent
  const parts = [
    change === null
      ? `${collected} collected so far this month.`
      : `${collected} collected so far, ${Math.abs(change)}% ${change >= 0 ? 'ahead of' : 'behind'} ${lastMonth} by this date.`,
  ]
  if (i.endingSoon.length === 1) parts.push(`${i.endingSoon[0].name}’s membership (${formatRupees(i.atStake)}) ends this week.`)
  else if (i.endingSoon.length > 1)
    parts.push(`${i.endingSoon.length} memberships worth ${formatRupees(i.atStake)} end this week, starting with ${i.endingSoon[0].name}.`)
  else if (i.oldestDues[0])
    parts.push(`${i.oldestDues[0].name} has owed ${formatRupees(i.oldestDues[0].amount)} since ${shortDate(i.oldestDues[0].owedSince, today)}, worth a reminder today.`)
  else if (i.winBack[0]) parts.push(`${i.winBack[0].name} hasn’t renewed since ${shortDate(i.winBack[0].endedOn, today)}; a call could bring them back.`)
  else parts.push('Nothing urgent today.')
  if (i.forecast.total > i.forecast.collected) parts.push(`On track for ${formatRupees(i.forecast.total)} if dues are paid and renewals come in.`)
  return parts.join(' ')
}

// Suggestion chips --------------------------------------------------------------------------------

export interface UsageStats {
  plan_uses: Record<string, number>
  payment_amounts: number[]
  discounts: number[]
}

export const EMPTY_USAGE: UsageStats = { plan_uses: {}, payment_amounts: [], discounts: [] }

export interface AmountChip {
  label: string
  /** Null clears the field ("Pay later"). */
  amount: number | null
}

export function amountSuggestions(dueAmount: number, stats: UsageStats, payLater = false): AmountChip[] {
  if (dueAmount <= 0) return []
  const chips: AmountChip[] = [{ label: `Full ${formatRupees(dueAmount)}`, amount: dueAmount }]
  if (dueAmount >= 2) chips.push({ label: `Half ${formatRupees(Math.floor(dueAmount / 2))}`, amount: Math.floor(dueAmount / 2) })
  for (const a of [...stats.payment_amounts, 500, 1000, 2000, 5000]) {
    if (a >= 1 && a < dueAmount) chips.push({ label: formatRupees(a), amount: a })
  }
  const seen = new Set<number>()
  const unique = chips.filter((c) => (seen.has(c.amount!) ? false : (seen.add(c.amount!), true))).slice(0, payLater ? 3 : 4)
  return payLater ? [...unique, { label: 'Pay later', amount: null }] : unique
}

export function discountSuggestions(price: number, stats: UsageStats): number[] {
  return [...new Set([...stats.discounts, 100, 200, 500])].filter((d) => d >= 1 && d < price).slice(0, 4)
}

export const LENGTH_CHIPS: { value: number; unit: 'DAYS' | 'MONTHS' }[] = [
  { value: 1, unit: 'DAYS' },
  { value: 7, unit: 'DAYS' },
  { value: 1, unit: 'MONTHS' },
  { value: 3, unit: 'MONTHS' },
  { value: 6, unit: 'MONTHS' },
  { value: 12, unit: 'MONTHS' },
]

/** Most used first; ties keep name order. */
export function byUse(plans: Plan[], stats: UsageStats): Plan[] {
  return [...plans].sort((a, b) => (stats.plan_uses[b.id] ?? 0) - (stats.plan_uses[a.id] ?? 0) || byName(a, b))
}

// Reminders ---------------------------------------------------------------------------------------

/** Everyone worth a reminder: ending this week, ended in the last month, or owing money. */
export function reminderCandidates(members: MemberView[], today: IsoDate): MemberView[] {
  const group = (m: MemberView) => (m.status === 'EXPIRING_SOON' ? 0 : m.status === 'EXPIRED' ? 1 : 2)
  return members
    .filter(
      (m) =>
        !m.is_archived &&
        (m.status === 'EXPIRING_SOON' ||
          (m.status === 'EXPIRED' && m.latest_end_date && daysBetween(m.latest_end_date, today) <= REMIND_EXPIRED_FOR_DAYS) ||
          m.amount_due > 0),
    )
    .sort((a, b) => {
      const g = group(a) - group(b)
      if (g) return g
      const ea = a.latest_end_date ?? '0000-00-00'
      const eb = b.latest_end_date ?? '0000-00-00'
      const byEnd = group(a) === 1 ? eb.localeCompare(ea) : ea.localeCompare(eb)
      return byEnd || byName(a, b)
    })
}

// Attendance --------------------------------------------------------------------------------------

export interface QuietMember {
  member: MemberView
  lastVisit: IsoDate
  daysAway: number
}

/**
 * Members with a running plan whose latest visit was GONE_QUIET_DAYS or more ago, most recently gone
 * first. Members who never checked in are left out, so starting to take attendance doesn't flag everyone.
 */
export function goneQuiet(members: MemberView[], lastVisits: Map<string, IsoDate>, today: IsoDate): QuietMember[] {
  return members
    .filter((m) => !m.is_archived && (m.status === 'ACTIVE' || m.status === 'EXPIRING_SOON'))
    .flatMap((member) => {
      const lastVisit = lastVisits.get(member.member_id)
      if (!lastVisit) return []
      const daysAway = daysBetween(lastVisit, today)
      return daysAway >= GONE_QUIET_DAYS ? [{ member, lastVisit, daysAway }] : []
    })
    .sort((a, b) => a.daysAway - b.daysAway || byName(a.member, b.member))
}

// Inbox alerts ------------------------------------------------------------------------------------

export type AlertKind = 'ENDS_TODAY' | 'LAPSED' | 'DUE_OVERDUE'

export interface Alert {
  eventKey: string
  kind: AlertKind
  memberId: string
  memberName: string
  amount: number
  date: IsoDate
}

export function inboxAlerts(today: IsoDate, members: MemberView[], memberships: MembershipRecord[]): Alert[] {
  const names = new Map(members.filter((m) => !m.is_archived).map((m) => [m.member_id, m.name]))
  const byMember = new Map<string, MembershipRecord[]>()
  for (const ms of memberships) if (names.has(ms.member_id)) byMember.set(ms.member_id, [...(byMember.get(ms.member_id) ?? []), ms])
  const alerts: Alert[] = []
  for (const [id, list] of byMember) {
    const name = names.get(id)!
    const latest = list.reduce((a, b) => (b.end_date > a.end_date ? b : a))
    if (latest.end_date === today) alerts.push({ eventKey: `ends:${id}:${latest.end_date}`, kind: 'ENDS_TODAY', memberId: id, memberName: name, amount: charged(latest), date: latest.end_date })
    else if (latest.end_date < today && latest.end_date >= addDays(today, -LAPSED_ALERT_DAYS))
      alerts.push({ eventKey: `lapsed:${id}:${latest.end_date}`, kind: 'LAPSED', memberId: id, memberName: name, amount: charged(latest), date: latest.end_date })
    const owing = list.filter((m) => due(m) > 0)
    if (owing.length) {
      const owedSince = owing.reduce((a, b) => (b.start_date < a.start_date ? b : a)).start_date
      if (daysBetween(owedSince, today) >= OVERDUE_DAYS)
        alerts.push({ eventKey: `due:${id}:${owedSince}`, kind: 'DUE_OVERDUE', memberId: id, memberName: name, amount: owing.reduce((s, m) => s + due(m), 0), date: owedSince })
    }
  }
  return alerts
}
