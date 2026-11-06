import { createClient } from '@supabase/supabase-js'

/** Local Supabase (`supabase start`) unless .env.local names a hosted project. */
const url = import.meta.env.VITE_SUPABASE_URL || 'http://127.0.0.1:54321'
const key = import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY || 'sb_publishable_ACJWlzQHlZjBrEguHvfOxg_3BJgxAaH'

export const supabase = createClient(url, key, {
  auth: { persistSession: true, autoRefreshToken: true, detectSessionInUrl: true, flowType: 'pkce' },
})

export const googleSignInEnabled = import.meta.env.VITE_GOOGLE_SIGN_IN === 'true'

export const MEMBER_PHOTOS = 'member-photos'
export const GYM_LOGOS = 'gym-logos'
