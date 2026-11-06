import { useEffect, useMemo } from 'react'
import { addDays, type IsoDate } from '@/lib/dates'
import { inboxAlerts, type Alert } from '@/lib/domain'
import { usePref } from '@/lib/prefs'
import { useGym } from './session'
import { useAllMemberships, useMemberSummaries, useToday } from './queries'

const KEEP_DAYS = 60

export interface InboxItem extends Alert {
  createdOn: IsoDate
  read: boolean
}

/**
 * The bell's alerts, kept in this browser as the phone keeps them on the phone: each event is added
 * once, on the day it's first noticed, and dropped after 60 days. Alerts about members who've since
 * been deleted aren't shown.
 */
export function useInbox() {
  const { membership } = useGym()
  const today = useToday()
  const [items, setItems] = usePref<InboxItem[]>(`inbox:${membership.gym.id}`, [])
  const members = useMemberSummaries().data
  const memberships = useAllMemberships().data

  useEffect(() => {
    if (!members || !memberships) return
    const keep = items.filter((i) => i.createdOn >= addDays(today, -KEEP_DAYS))
    const known = new Set(keep.map((i) => i.eventKey))
    const fresh = inboxAlerts(today, members, memberships)
      .filter((a) => !known.has(a.eventKey))
      .map((a): InboxItem => ({ ...a, createdOn: today, read: false }))
    if (fresh.length || keep.length !== items.length) setItems([...fresh, ...keep])
    // Only when the data or the day changes; the write itself mustn't re-run it.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [members, memberships, today])

  const visible = useMemo(() => {
    if (!members) return items
    const exists = new Set(members.map((m) => m.member_id))
    return items.filter((i) => exists.has(i.memberId)).sort((a, b) => b.createdOn.localeCompare(a.createdOn))
  }, [items, members])

  return {
    items: visible,
    loaded: !!members && !!memberships,
    markAllRead: () => setItems(items.map((i) => (i.read ? i : { ...i, read: true }))),
  }
}

export function useUnreadCount(): number {
  return useInbox().items.filter((i) => !i.read).length
}
