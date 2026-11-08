import { useEffect } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { supabase } from '@/lib/supabase'

const RECORD_TABLES = ['plans', 'members', 'memberships', 'payments', 'check_ins']
const GYM_TABLES = ['gyms', 'gym_staff']

/**
 * Changes from other phones and tabs, as they happen: a change to the gym's records re-reads them, a
 * change to the gym or its staff re-reads who's in it. Bursts are folded into one re-read.
 */
export function useLiveChanges(gymId: string) {
  const client = useQueryClient()
  useEffect(() => {
    let timer: ReturnType<typeof setTimeout> | undefined
    let gymChanged = false
    const settle = () => {
      clearTimeout(timer)
      timer = setTimeout(() => {
        client.invalidateQueries({ predicate: (q) => q.queryKey[0] !== 'membership' })
        if (gymChanged) client.invalidateQueries({ queryKey: ['membership'] })
        gymChanged = false
      }, 300)
    }
    let channel = supabase.channel(`gym-${gymId}`)
    for (const table of RECORD_TABLES) {
      // Deletes carry no gym id to filter on, so any delete is worth a cheap re-read.
      channel = channel
        .on('postgres_changes', { event: 'INSERT', schema: 'public', table, filter: `gym_id=eq.${gymId}` }, settle)
        .on('postgres_changes', { event: 'UPDATE', schema: 'public', table, filter: `gym_id=eq.${gymId}` }, settle)
        .on('postgres_changes', { event: 'DELETE', schema: 'public', table }, settle)
    }
    for (const table of GYM_TABLES) {
      const filter = table === 'gyms' ? `id=eq.${gymId}` : `gym_id=eq.${gymId}`
      const onGym = () => {
        gymChanged = true
        settle()
      }
      channel = channel
        .on('postgres_changes', { event: '*', schema: 'public', table, filter }, onGym)
        .on('postgres_changes', { event: 'DELETE', schema: 'public', table }, onGym)
    }
    channel.subscribe()
    return () => {
      clearTimeout(timer)
      supabase.removeChannel(channel)
    }
  }, [gymId, client])
}
