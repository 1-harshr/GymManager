/** Rupees as India writes them: ₹1,23,450. Amounts are whole rupees. */
const rupees = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 })
const grouped = new Intl.NumberFormat('en-IN', { maximumFractionDigits: 0 })

export function formatRupees(amount: number): string {
  return rupees.format(amount)
}

export function formatNumber(value: number): string {
  return grouped.format(value)
}

/** "+91 90000 00001" for a number kept as "919000000001", "+919000000001" or "9000000001". */
export function formatPhone(phone: string | null | undefined): string {
  if (!phone) return ''
  const digits = phone.replace(/\D/g, '')
  const local = digits.length === 12 && digits.startsWith('91') ? digits.slice(2) : digits
  if (local.length === 10) return `+91 ${local.slice(0, 5)} ${local.slice(5)}`
  return phone
}

/** The 10 digits after +91, from whatever was pasted: a leading 0 or 91 is dropped. */
export function localDigits(value: string): string {
  const digits = value.replace(/\D/g, '')
  if (digits.length > 10 && digits.startsWith('91')) return digits.slice(2, 12)
  if (digits.length > 10 && digits.startsWith('0')) return digits.slice(1, 11)
  return digits.slice(0, 10)
}

/** "RV" for "Rohan Verma"; one letter for one name. */
export function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean)
  if (parts.length === 0) return '?'
  return (parts[0][0] + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase()
}

export function plural(count: number, one: string, many: string): string {
  return `${formatNumber(count)} ${count === 1 ? one : many}`
}

/** Whole rupees from what was typed; null for nothing or nonsense. */
export function parseRupees(value: string): number | null {
  const digits = value.replace(/[^\d]/g, '')
  if (digits === '') return null
  const n = Number(digits)
  return Number.isSafeInteger(n) ? n : null
}
