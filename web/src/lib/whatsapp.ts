/** WhatsApp messages, word for word as the phone app sends them, opened as wa.me links. */
import { fullDate, type IsoDate } from './dates'
import { formatRupees } from './format'
import { charged, firstName, normalizeIndianPhone, paid, MODE_LABEL } from './domain'
import type { MemberStatus, MembershipRecord, Payment } from './types'

export type MessageKind = 'WELCOME' | 'RECEIPT' | 'REMINDER' | 'MISS_YOU'

export interface Message {
  kind: MessageKind
  memberId: string
  memberName: string
  /** "919876543210". */
  number: string
  text: string
}

export const KIND_LABEL: Record<MessageKind, string> = { WELCOME: 'Welcome', RECEIPT: 'Receipt', REMINDER: 'Reminder', MISS_YOU: 'We miss you' }
export const KIND_TITLE: Record<MessageKind, string> = {
  WELCOME: 'Send a welcome on WhatsApp?',
  RECEIPT: 'Send the receipt on WhatsApp?',
  REMINDER: 'Send a reminder on WhatsApp?',
  MISS_YOU: 'Send a “we miss you” on WhatsApp?',
}

export function whatsAppNumber(phone: string | null | undefined): string | null {
  const local = normalizeIndianPhone(phone)
  return local ? '91' + local : null
}

interface Who {
  id: string
  name: string
  phone: string | null
}

export function welcome(member: Who, gymName: string): Message | null {
  const number = whatsAppNumber(member.phone)
  if (!number) return null
  return {
    kind: 'WELCOME',
    memberId: member.id,
    memberName: member.name,
    number,
    text: `Hi ${firstName(member.name)}, welcome to ${gymName}! 💪\nWe’re glad to have you. Save this number to get your receipts and renewal reminders here.`,
  }
}

/** For a sale, or for one later [payment] on it. Paid and due are as of that payment. */
export function receipt(member: Who, gymName: string, membership: MembershipRecord, payment?: Payment): Message | null {
  const number = whatsAppNumber(member.phone)
  if (!number) return null
  const paidSoFar = payment
    ? membership.payments.filter((p) => p.paid_on < payment.paid_on || (p.paid_on === payment.paid_on && p.created_at <= payment.created_at)).reduce((s, p) => s + p.amount, 0)
    : paid(membership)
  const balance = charged(membership) - paidSoFar
  const lines = [
    `Plan: ${membership.plan_name}`,
    `Valid: ${fullDate(membership.start_date)} to ${fullDate(membership.end_date)}`,
    `Price: ${formatRupees(membership.price)}`,
    ...(membership.discount > 0 ? [`Discount: ${formatRupees(membership.discount)}`] : []),
    ...(payment
      ? [`Received: ${formatRupees(payment.amount)} by ${MODE_LABEL[payment.mode]} on ${fullDate(payment.paid_on)}`, `Paid so far: ${formatRupees(paidSoFar)}`]
      : [`Paid: ${formatRupees(paidSoFar)}`]),
    balance > 0 ? `Balance due: ${formatRupees(balance)}` : 'Fully paid ✅',
  ]
  const number_ = payment ? `${membership.id}-${payment.id}` : membership.id
  return {
    kind: 'RECEIPT',
    memberId: member.id,
    memberName: member.name,
    number,
    text: [`*${gymName} · Receipt*\nReceipt no. ${number_}`, `Hi ${firstName(member.name)}, thank you for your payment.`, lines.join('\n')].join('\n\n'),
  }
}

export function reminder(
  member: Who & { status: MemberStatus; latest_end_date: IsoDate | null; amount_due: number },
  gymName: string,
  today: IsoDate,
): Message | null {
  const number = whatsAppNumber(member.phone)
  if (!number) return null
  const first = firstName(member.name)
  const end = member.latest_end_date
  let renewal: string | null = null
  if (end && member.status === 'EXPIRING_SOON')
    renewal =
      end === today
        ? `Hi ${first}, your membership at ${gymName} ends today. Renew to keep training without a break 💪`
        : `Hi ${first}, your membership at ${gymName} ends on ${fullDate(end)}. Renew to keep training without a break 💪`
  else if (end && member.status === 'EXPIRED')
    renewal = `Hi ${first}, your membership at ${gymName} ended on ${fullDate(end)}. We miss you! Renew any time to get back to training 💪`
  const text = !renewal
    ? `Hi ${first}, ${formatRupees(member.amount_due)} is pending for your membership at ${gymName}. Please clear it on your next visit. Thank you!`
    : member.amount_due > 0
      ? `${renewal}\n\nPending balance: ${formatRupees(member.amount_due)}`
      : renewal
  return { kind: 'REMINDER', memberId: member.id, memberName: member.name, number, text }
}

/** For a member who has stopped coming. */
export function missYou(member: Who, gymName: string): Message | null {
  const number = whatsAppNumber(member.phone)
  if (!number) return null
  return {
    kind: 'MISS_YOU',
    memberId: member.id,
    memberName: member.name,
    number,
    text: `Hi ${firstName(member.name)}, we haven’t seen you at ${gymName} for a while! 💪\nHope all is well. Drop in soon and keep your progress going.`,
  }
}

export function waLink(message: Message): string {
  return `https://wa.me/${message.number}?text=${encodeURIComponent(message.text)}`
}
