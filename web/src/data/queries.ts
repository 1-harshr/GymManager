import { useEffect, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { supabase } from '@/lib/supabase'
import { addMonths, startOfMonth, endOfMonth, todayIn, type IsoDate } from '@/lib/dates'
import { EMPTY_USAGE, withStatus, type MembershipWithMember, type UsageStats } from '@/lib/domain'
import type { Member, MemberSummary, MembershipRecord, Payment, PaymentMode, Plan, StaffMember, Invite } from '@/lib/types'
import { useGym } from './session'

/** The rows a query returned, typed as the caller knows them to be; its error is thrown. */
async function rows<T>(query: PromiseLike<{ data: unknown; error: unknown }>): Promise<T> {
  const { data, error } = await query
  if (error) throw error
  return data as T
}

/** Today where the gym is; rolls over at midnight and when the tab comes back. */
export function useToday(): IsoDate {
  const { membership } = useGym()
  const tz = membership.gym.timezone
  const [today, setToday] = useState(() => todayIn(tz))
  useEffect(() => {
    const check = () => setToday(todayIn(tz))
    const timer = setInterval(check, 60_000)
    document.addEventListener('visibilitychange', check)
    return () => {
      clearInterval(timer)
      document.removeEventListener('visibilitychange', check)
    }
  }, [tz])
  return today
}

const SUMMARY = 'member_id, name, phone, photo_path, is_archived, latest_end_date, amount_due, paused_until'

/** Every member with their status for today; archived ones too, flagged. */
export function useMemberSummaries() {
  const today = useToday()
  const query = useQuery({
    queryKey: ['members'],
    queryFn: () => rows<MemberSummary[]>(supabase.from('member_summary').select(SUMMARY).order('name').limit(10000)),
  })
  const data = useMemo(() => (query.data ? withStatus(query.data, today) : undefined), [query.data, today])
  return { ...query, data }
}

export function useMember(id: string | undefined) {
  return useQuery({
    queryKey: ['member', id],
    enabled: !!id,
    queryFn: () => rows<Member | null>(supabase.from('members').select('*').eq('id', id!).maybeSingle()),
  })
}

const MEMBERSHIP = 'id, member_id, plan_id, plan_name, start_date, end_date, price, discount, notes, created_at, payments(id, membership_id, amount, mode, paid_on, note, created_at), membership_pauses(paused_from, resumes_on)'

function sortPayments<T extends MembershipRecord>(list: T[]): T[] {
  return list.map((m) => ({ ...m, payments: [...m.payments].sort((a, b) => a.paid_on.localeCompare(b.paid_on) || a.created_at.localeCompare(b.created_at)) }))
}

/** A member's memberships, newest first, each with its payments oldest first. */
export function useMemberships(memberId: string | undefined) {
  return useQuery({
    queryKey: ['memberships', memberId],
    enabled: !!memberId,
    queryFn: async () =>
      sortPayments(
        await rows<MembershipRecord[]>(supabase.from('memberships').select(MEMBERSHIP).eq('member_id', memberId!).order('start_date', { ascending: false })),
      ),
  })
}

/** Every membership of members still on the books, for Home's insights and the alerts inbox. */
export function useAllMemberships(enabled = true) {
  return useQuery({
    queryKey: ['memberships', 'all'],
    enabled,
    queryFn: async () => {
      const list = await rows<(MembershipRecord & { members: { id: string; name: string } })[]>(
        supabase.from('memberships').select(`${MEMBERSHIP}, members!inner(id, name, is_archived)`).eq('members.is_archived', false).limit(20000),
      )
      return sortPayments(list).map(({ members, ...m }) => ({ ...m, member: members })) as MembershipWithMember[]
    },
  })
}

export function usePlans() {
  return useQuery({
    queryKey: ['plans'],
    queryFn: () => rows<Plan[]>(supabase.from('plans').select('id, name, duration_value, duration_unit, default_price, is_archived').order('name')),
  })
}

export function useUsageStats() {
  return useQuery({
    queryKey: ['usage'],
    queryFn: async () => ({ ...EMPTY_USAGE, ...(await rows<UsageStats>(supabase.rpc('usage_stats', { top: 6 }))) }),
  }).data ?? EMPTY_USAGE
}

export interface PaymentRecord {
  id: string
  amount: number
  mode: PaymentMode
  paid_on: IsoDate
  plan_name: string
  member_id: string
  member_name: string
}

export function useRecentPayments(limit = 5) {
  return useQuery({
    queryKey: ['payments', 'recent', limit],
    queryFn: async () => {
      const list = await rows<
        { id: string; amount: number; mode: PaymentMode; paid_on: string; memberships: { plan_name: string; member_id: string; members: { name: string } } }[]
      >(
        supabase
          .from('payments')
          .select('id, amount, mode, paid_on, memberships(plan_name, member_id, members(name))')
          .order('paid_on', { ascending: false })
          .order('created_at', { ascending: false })
          .limit(limit),
      )
      return list.map(
        (p): PaymentRecord => ({
          id: p.id,
          amount: p.amount,
          mode: p.mode,
          paid_on: p.paid_on,
          plan_name: p.memberships.plan_name,
          member_id: p.memberships.member_id,
          member_name: p.memberships.members.name,
        }),
      )
    },
  })
}

/** Money taken each day from the 1st of last month to the end of this one. */
export function useDailyRevenue(today: IsoDate, enabled = true) {
  const from = addMonths(startOfMonth(today), -1)
  const to = endOfMonth(today)
  return useQuery({
    queryKey: ['payments', 'daily', from, to],
    enabled,
    queryFn: async () => {
      const list = await rows<{ amount: number; paid_on: string }[]>(
        supabase.from('payments').select('amount, paid_on').gte('paid_on', from).lte('paid_on', to).limit(50000),
      )
      const daily = new Map<IsoDate, number>()
      for (const p of list) daily.set(p.paid_on, (daily.get(p.paid_on) ?? 0) + p.amount)
      return daily
    },
  })
}

export function useStaff() {
  return useQuery({
    queryKey: ['staff'],
    queryFn: async () => {
      const list = await rows<StaffMember[]>(supabase.from('gym_staff').select('user_id, role, name, joined_at').order('joined_at'))
      return [...list].sort((a, b) => (a.role === b.role ? 0 : a.role === 'owner' ? -1 : 1))
    },
  })
}

export function useInvites(enabled: boolean) {
  return useQuery({
    queryKey: ['invites'],
    enabled,
    queryFn: () =>
      rows<Invite[]>(
        supabase.from('gym_invites').select('code, role, expires_at').is('used_at', null).gt('expires_at', new Date().toISOString()).order('created_at', { ascending: false }),
      ),
  })
}

/** Runs a write, then re-reads everything (other screens show what it changed). */
export function useWrite<A, R>(write: (args: A) => Promise<R>) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: write,
    onSuccess: () => client.invalidateQueries({ predicate: (q) => q.queryKey[0] !== 'membership' && q.queryKey[0] !== 'signed-url' }),
  })
}

export { rows }

export type NewPayment = { membershipId: string; amount: number; mode: PaymentMode; paidOn: IsoDate; note: string | null }

export async function recordPayment(gymId: string, p: NewPayment): Promise<Payment> {
  return rows<Payment>(
    supabase
      .from('payments')
      .insert({ gym_id: gymId, membership_id: p.membershipId, amount: p.amount, mode: p.mode, paid_on: p.paidOn, note: p.note })
      .select('id, membership_id, amount, mode, paid_on, note, created_at')
      .single(),
  )
}
