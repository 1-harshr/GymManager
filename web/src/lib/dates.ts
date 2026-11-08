/**
 * Calendar dates as the database keeps them, "YYYY-MM-DD", with no time of day or time zone. Arithmetic
 * happens in UTC so a day is always a day.
 */
export type IsoDate = string

const DAY_MS = 86_400_000

export function parseIso(date: IsoDate): Date {
  const [y, m, d] = date.split('-').map(Number)
  return new Date(Date.UTC(y, m - 1, d))
}

export function toIso(date: Date): IsoDate {
  return date.toISOString().slice(0, 10)
}

/** Today where the gym is, which is what "expires today" means; India unless the gym says otherwise. */
export function todayIn(timezone = 'Asia/Kolkata'): IsoDate {
  return new Intl.DateTimeFormat('en-CA', { timeZone: timezone, year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())
}

export function addDays(date: IsoDate, days: number): IsoDate {
  return toIso(new Date(parseIso(date).getTime() + days * DAY_MS))
}

/** Calendar months, clamped to the month's last day as Postgres does (31 Jan + 1 month = 28/29 Feb). */
export function addMonths(date: IsoDate, months: number): IsoDate {
  const d = parseIso(date)
  const target = new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth() + months, 1))
  const lastDay = new Date(Date.UTC(target.getUTCFullYear(), target.getUTCMonth() + 1, 0)).getUTCDate()
  target.setUTCDate(Math.min(d.getUTCDate(), lastDay))
  return toIso(target)
}

/** Whole days from [from] to [to]; negative when [to] is earlier. */
export function daysBetween(from: IsoDate, to: IsoDate): number {
  return Math.round((parseIso(to).getTime() - parseIso(from).getTime()) / DAY_MS)
}

/** The Monday on or before [date]. */
export function startOfWeek(date: IsoDate): IsoDate {
  return addDays(date, -((parseIso(date).getUTCDay() + 6) % 7))
}

export function startOfMonth(date: IsoDate): IsoDate {
  return date.slice(0, 8) + '01'
}

export function endOfMonth(date: IsoDate): IsoDate {
  return addDays(addMonths(startOfMonth(date), 1), -1)
}

/** The inclusive last day, as the database works it out: a 1-month plan from 15 Jan ends on 14 Feb. */
export function membershipEndDate(start: IsoDate, value: number, unit: 'DAYS' | 'MONTHS'): IsoDate {
  return addDays(unit === 'DAYS' ? addDays(start, value) : addMonths(start, value), -1)
}

const fmt = (options: Intl.DateTimeFormatOptions) => new Intl.DateTimeFormat('en-IN', { timeZone: 'UTC', ...options })
const dayMonth = fmt({ day: 'numeric', month: 'short' })
const dayMonthYear = fmt({ day: 'numeric', month: 'short', year: 'numeric' })
const longDay = fmt({ weekday: 'long', day: 'numeric', month: 'long' })
const monthName = fmt({ month: 'long' })
const shortMonthName = fmt({ month: 'short' })

/** "16 Nov", with the year only when it isn't this year's. */
export function shortDate(date: IsoDate, today: IsoDate): string {
  return (date.slice(0, 4) === today.slice(0, 4) ? dayMonth : dayMonthYear).format(parseIso(date))
}

export function fullDate(date: IsoDate): string {
  return dayMonthYear.format(parseIso(date))
}

/** "Friday, 6 November". */
export function longDate(date: IsoDate): string {
  return longDay.format(parseIso(date))
}

export function monthOf(date: IsoDate): string {
  return monthName.format(parseIso(date))
}

/** "Nov". */
export function shortMonthOf(date: IsoDate): string {
  return shortMonthName.format(parseIso(date))
}

/** "Today", "Yesterday", or "4 Nov". */
export function relativeDay(date: IsoDate, today: IsoDate): string {
  const days = daysBetween(date, today)
  if (days === 0) return 'Today'
  if (days === 1) return 'Yesterday'
  return shortDate(date, today)
}

/** Whole years old on [today]. */
export function age(dateOfBirth: IsoDate, today: IsoDate): number {
  const [by, bm, bd] = dateOfBirth.split('-').map(Number)
  const [ty, tm, td] = today.split('-').map(Number)
  return ty - by - (tm < bm || (tm === bm && td < bd) ? 1 : 0)
}
