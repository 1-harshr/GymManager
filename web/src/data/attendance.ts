import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { supabase } from '@/lib/supabase'
import { goneQuiet, MISS_YOU_COOLDOWN_MS, type QuietMember } from '@/lib/domain'
import { missYou, type Message } from '@/lib/whatsapp'
import { missYouLogKey, usePref } from '@/lib/prefs'
import type { IsoDate } from '@/lib/dates'
import { useGym } from './session'
import { rows, useMemberSummaries, useToday } from './queries'

export interface CheckIn {
  id: string
  member_id: string
  checked_in_on: IsoDate
  checked_in_at: string
}

const CHECK_IN = 'id, member_id, checked_in_on, checked_in_at'

/** Everyone checked in on [date], latest first. */
export function useCheckIns(date: IsoDate) {
  return useQuery({
    queryKey: ['check-ins', 'day', date],
    queryFn: () => rows<CheckIn[]>(supabase.from('check_ins').select(CHECK_IN).eq('checked_in_on', date).order('checked_in_at', { ascending: false })),
  })
}

/** Weeks of visits a member's page shows as a grid. */
export const MEMBER_HEATMAP_WEEKS = 12

/**
 * A member's latest visits, latest first. A member checks in at most once a day, so this many always
 * covers every day of their visit grid.
 */
export function useMemberCheckIns(memberId: string | undefined) {
  return useQuery({
    queryKey: ['check-ins', 'member', memberId],
    enabled: !!memberId,
    queryFn: () =>
      rows<CheckIn[]>(
        supabase.from('check_ins').select(CHECK_IN).eq('member_id', memberId!).order('checked_in_on', { ascending: false }).limit(MEMBER_HEATMAP_WEEKS * 7),
      ),
  })
}

export function useLastVisits() {
  return useQuery({
    queryKey: ['check-ins', 'last'],
    queryFn: async () => {
      const list = await rows<{ member_id: string; last_check_in_on: IsoDate }[]>(supabase.from('member_last_check_in').select('member_id, last_check_in_on').limit(10000))
      return new Map(list.map((r) => [r.member_id, r.last_check_in_on]))
    },
  })
}

/**
 * Checks the member in for today, at the gym's date. Checking in someone already in leaves their
 * first check-in as it was.
 */
export async function checkIn(gymId: string, memberId: string): Promise<void> {
  const { error } = await supabase
    .from('check_ins')
    .upsert({ gym_id: gymId, member_id: memberId }, { onConflict: 'member_id,checked_in_on', ignoreDuplicates: true })
  if (error) throw error
}

export async function undoCheckIn(id: string): Promise<void> {
  const { error } = await supabase.from('check_ins').delete().eq('id', id)
  if (error) throw error
}

export interface QuietItem extends QuietMember {
  /** Null without a WhatsApp number. */
  message: Message | null
  lastSentAt: number | null
  sentRecently: boolean
}

/** Members with a running plan who've stopped coming, each with a "we miss you". */
export function useGoneQuiet() {
  const { membership } = useGym()
  const today = useToday()
  const members = useMemberSummaries().data
  const lastVisits = useLastVisits().data
  const [log] = usePref<Record<string, number>>(missYouLogKey(membership.gym.id), {})
  return useMemo(() => {
    const now = Date.now()
    const items: QuietItem[] = goneQuiet(members ?? [], lastVisits ?? new Map(), today).map((q) => {
      const lastSentAt = log[q.member.member_id] ?? null
      return {
        ...q,
        message: missYou({ id: q.member.member_id, name: q.member.name, phone: q.member.phone }, membership.gym.name),
        lastSentAt,
        sentRecently: lastSentAt !== null && now - lastSentAt < MISS_YOU_COOLDOWN_MS,
      }
    })
    return { items, loaded: !!members && !!lastVisits }
  }, [members, lastVisits, today, log, membership.gym.name])
}

/** "7:05 AM" on this computer's clock. */
export function checkInTime(checkIn: CheckIn): string {
  return new Date(checkIn.checked_in_at).toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit', hour12: true }).toUpperCase()
}
