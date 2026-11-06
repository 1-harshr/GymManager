import { describe, expect, it } from 'vitest'
import { membershipEndDate } from './dates'
import { amountSuggestions, EMPTY_USAGE, memberStatus, normalizeIndianPhone, pace, renewalStartDate, statusShortLabel } from './domain'
import { parseDate, parseMembersCsv } from './csv'
import { formatRupees } from './format'

describe('member status', () => {
  const today = '2026-11-07'
  it('runs from no plan through expiring to expired', () => {
    expect(memberStatus(null, today)).toBe('NONE')
    expect(memberStatus('2026-11-06', today)).toBe('EXPIRED')
    expect(memberStatus('2026-11-07', today)).toBe('EXPIRING_SOON')
    expect(memberStatus('2026-11-14', today)).toBe('EXPIRING_SOON')
    expect(memberStatus('2026-11-15', today)).toBe('ACTIVE')
  })
  it('says how many days are left', () => {
    expect(statusShortLabel('EXPIRING_SOON', '2026-11-07', today)).toBe('Ends today')
    expect(statusShortLabel('EXPIRING_SOON', '2026-11-08', today)).toBe('Ends tomorrow')
    expect(statusShortLabel('EXPIRING_SOON', '2026-11-12', today)).toBe('5 days left')
  })
})

describe('membership dates', () => {
  it('ends the day before the same date next month', () => {
    expect(membershipEndDate('2026-01-15', 1, 'MONTHS')).toBe('2026-02-14')
    expect(membershipEndDate('2026-11-01', 30, 'DAYS')).toBe('2026-11-30')
  })
  it('clamps to the end of a short month', () => {
    expect(membershipEndDate('2026-01-31', 1, 'MONTHS')).toBe('2026-02-27')
    expect(membershipEndDate('2028-01-31', 1, 'MONTHS')).toBe('2028-02-28')
  })
  it('renews from the day after a running membership, else today', () => {
    expect(renewalStartDate('2026-11-07', '2026-11-07')).toBe('2026-11-08')
    expect(renewalStartDate('2026-11-01', '2026-11-07')).toBe('2026-11-07')
    expect(renewalStartDate(null, '2026-11-07')).toBe('2026-11-07')
  })
})

describe('money', () => {
  it('groups as India does', () => {
    expect(formatRupees(123456)).toBe('₹1,23,456')
    expect(formatRupees(800)).toBe('₹800')
  })
  it('suggests full, half, usual and round amounts below the due', () => {
    expect(amountSuggestions(1500, { ...EMPTY_USAGE, payment_amounts: [800] }).map((c) => c.label)).toEqual(['Full ₹1,500', 'Half ₹750', '₹800', '₹500'])
    expect(amountSuggestions(1500, EMPTY_USAGE, true).at(-1)?.label).toBe('Pay later')
    expect(amountSuggestions(0, EMPTY_USAGE)).toEqual([])
  })
})

describe('collections pace', () => {
  it('compares with last month by the same day', () => {
    const daily = new Map([
      ['2026-10-02', 1000],
      ['2026-10-20', 5000],
      ['2026-11-01', 1500],
    ])
    const p = pace(daily, '2026-11-07')
    expect(p.thisMonthToDate).toBe(1500)
    expect(p.lastMonthToDate).toBe(1000)
    expect(p.changePercent).toBe(50)
    expect(p.lastMonthTotal).toBe(6000)
  })
})

describe('import', () => {
  it('reads phones and dates the Indian way', () => {
    expect(normalizeIndianPhone('+91 98765 43210')).toBe('9876543210')
    expect(normalizeIndianPhone('09876543210')).toBe('9876543210')
    expect(normalizeIndianPhone('9876543210.0')).toBe('9876543210')
    expect(normalizeIndianPhone('12345')).toBeNull()
    expect(parseDate('05/11/2026', '2026-11-07')).toBe('2026-11-05')
    expect(parseDate('5 Nov 26', '2026-11-07')).toBe('2026-11-05')
    expect(parseDate('31/02/2000', '2026-11-07')).toBeNull()
    expect(parseDate('15/08/98', '2026-11-07')).toBe('1998-08-15')
  })
  it('skips rows without a name or with a phone already on file', () => {
    const csv = 'Mobile No;Naam;Sex\n98765 43210;Ravi;M\n98765 43210;Ravi Again;M\n;;\n9000000001;Asha;girl\n;;\n'
    const r = parseMembersCsv(csv, '2026-11-07', ['919000000001'])
    expect(r.members.map((m) => m.name)).toEqual(['Ravi'])
    expect(r.issues.map((i) => i.kind)).toEqual(['DUPLICATE_PHONE', 'DUPLICATE_PHONE'])
    expect(r.members[0]).toMatchObject({ phone: '9876543210', gender: 'MALE', joined_on: '2026-11-07' })
  })
})
