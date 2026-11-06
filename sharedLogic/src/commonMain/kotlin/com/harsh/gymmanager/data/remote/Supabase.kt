package com.harsh.gymmanager.data.remote

import com.harsh.gymmanager.domain.repository.AuthRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.CoroutineScope

/**
 * Everything the app gets from Supabase, on one connection made once per app launch. The session is
 * saved on the device and refreshed automatically. [scope] should live as long as the app.
 */
class SupabaseBackend(scope: CoroutineScope) {
    private val client = createGymSupabaseClient()

    val auth: AuthRepository = SupabaseAuthRepository(client, scope)
}

private fun createGymSupabaseClient(): SupabaseClient =
    createSupabaseClient(
        supabaseUrl = reachableFromDevice(SupabaseBuildConfig.URL),
        supabaseKey = SupabaseBuildConfig.PUBLISHABLE_KEY,
    ) {
        install(Auth)
        install(Postgrest)
    }

/** Local Supabase runs on the Mac; some devices reach it by another address. */
internal expect fun reachableFromDevice(url: String): String
