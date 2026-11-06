import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import type { Session } from '@supabase/supabase-js'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { supabase } from '@/lib/supabase'
import type { Membership, StaffRole } from '@/lib/types'

/** Undefined while the saved session is still being read. */
export function useSession(): Session | null | undefined {
  const [session, setSession] = useState<Session | null | undefined>(undefined)
  useEffect(() => {
    supabase.auth.getSession().then(({ data }) => setSession(data.session))
    const { data } = supabase.auth.onAuthStateChange((_event, next) => setSession(next))
    return () => data.subscription.unsubscribe()
  }, [])
  return session
}

interface MembershipRow {
  role: StaffRole
  name: string | null
  gyms: { id: string; name: string; address: string | null; logo_path: string | null; timezone: string }
}

/** The gym the signed-in person works at, or null if they're not part of one yet. */
export function useMembership(userId: string | undefined) {
  return useQuery({
    queryKey: ['membership', userId],
    enabled: !!userId,
    queryFn: async (): Promise<Membership | null> => {
      const { data, error } = await supabase
        .from('gym_staff')
        .select('role, name, gyms(id, name, address, logo_path, timezone)')
        .eq('user_id', userId!)
        .maybeSingle<MembershipRow>()
      if (error) throw error
      return data ? { gym: data.gyms, role: data.role, name: data.name } : null
    },
  })
}

interface GymContextValue {
  membership: Membership
  userId: string
  phone: string | null
  email: string | null
  isOwner: boolean
}

const GymContext = createContext<GymContextValue | null>(null)

export function GymProvider({ value, children }: { value: GymContextValue; children: ReactNode }) {
  return <GymContext.Provider value={value}>{children}</GymContext.Provider>
}

export function useGym(): GymContextValue {
  const value = useContext(GymContext)
  if (!value) throw new Error('useGym outside a gym')
  return value
}

/** Re-reads everything that depends on the gym: after sign-in changes, joins, and the like. */
export function useRefreshAll() {
  const client = useQueryClient()
  return () => client.invalidateQueries()
}
