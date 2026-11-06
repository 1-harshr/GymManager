import { useMemo } from 'react'
import { reminderCandidates, REMINDER_COOLDOWN_MS, type MemberView } from '@/lib/domain'
import { reminder, type Message } from '@/lib/whatsapp'
import { usePref, reminderLogKey } from '@/lib/prefs'
import { useGym } from './session'
import { useMemberSummaries, useToday } from './queries'

export interface ReminderItem {
  member: MemberView
  /** Null without a WhatsApp number. */
  message: Message | null
  lastSentAt: number | null
  sentRecently: boolean
}

/** Everyone worth reminding, and how many of them haven't heard from the gym in 3 days. */
export function useReminders() {
  const { membership } = useGym()
  const today = useToday()
  const members = useMemberSummaries().data
  const [log] = usePref<Record<string, number>>(reminderLogKey(membership.gym.id), {})
  return useMemo(() => {
    const now = Date.now()
    const items: ReminderItem[] = reminderCandidates(members ?? [], today).map((m) => {
      const lastSentAt = log[m.member_id] ?? null
      return {
        member: m,
        message: reminder({ ...m, id: m.member_id }, membership.gym.name, today),
        lastSentAt,
        sentRecently: lastSentAt !== null && now - lastSentAt < REMINDER_COOLDOWN_MS,
      }
    })
    return { items, loaded: !!members, toSend: items.filter((i) => i.message && !i.sentRecently).length }
  }, [members, today, log, membership.gym.name])
}
