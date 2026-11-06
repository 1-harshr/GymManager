package com.harsh.gymmanager.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/** The app's one connection to Supabase; the session is saved on the device and refreshed automatically. */
fun createGymSupabaseClient(): SupabaseClient =
    createSupabaseClient(
        supabaseUrl = reachableFromDevice(SupabaseBuildConfig.URL),
        supabaseKey = SupabaseBuildConfig.PUBLISHABLE_KEY,
    ) {
        install(Auth)
        install(Postgrest)
    }

/** Local Supabase runs on the Mac; some devices reach it by another address. */
internal expect fun reachableFromDevice(url: String): String
