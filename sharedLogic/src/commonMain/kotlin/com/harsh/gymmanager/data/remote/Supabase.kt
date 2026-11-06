package com.harsh.gymmanager.data.remote

import com.harsh.gymmanager.domain.repository.AuthRepository
import com.harsh.gymmanager.domain.repository.GymRepository
import com.harsh.gymmanager.domain.repository.MemberRepository
import com.harsh.gymmanager.domain.repository.MembershipRepository
import com.harsh.gymmanager.domain.repository.PaymentRepository
import com.harsh.gymmanager.domain.repository.PlanRepository
import com.harsh.gymmanager.domain.suggestions.UsageRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import kotlinx.coroutines.CoroutineScope

/**
 * Everything the app gets from Supabase, on one connection made once per app launch. The session is
 * saved on the device and refreshed automatically. [scope] should live as long as the app.
 */
class SupabaseBackend(scope: CoroutineScope) {
    private val client = createGymSupabaseClient()

    val auth: AuthRepository = SupabaseAuthRepository(client, scope)

    val gyms: GymRepository = SupabaseGymRepository(client, auth, scope)

    private val refresher = Refresher()

    /** The gym's records online, for the real app once someone is signed in to a gym. */
    val data = RemoteGymData(
        members = SupabaseMemberRepository(client, refresher, gyms),
        plans = SupabasePlanRepository(client, refresher, gyms),
        memberships = SupabaseMembershipRepository(client, refresher),
        payments = SupabasePaymentRepository(client, refresher, gyms),
        usage = SupabaseUsageRepository(client),
    )

    init {
        LiveChanges(client, gyms, refresher, scope)
    }

    /**
     * Reads everything again, including the person's place in their gym: for when the app comes back
     * to the front, since changes made while it was away weren't heard.
     */
    suspend fun refresh() {
        gyms.refresh()
        refresher.changed()
    }
}

/** The repositories that live online; everything else (alerts, the WhatsApp outbox) stays on the phone. */
class RemoteGymData(
    val members: MemberRepository,
    val plans: PlanRepository,
    val memberships: MembershipRepository,
    val payments: PaymentRepository,
    val usage: UsageRepository,
)

private fun createGymSupabaseClient(): SupabaseClient =
    createSupabaseClient(
        supabaseUrl = reachableFromDevice(SupabaseBuildConfig.URL),
        supabaseKey = SupabaseBuildConfig.PUBLISHABLE_KEY,
    ) {
        install(Auth)
        install(Postgrest)
        install(Realtime)
    }

/** Local Supabase runs on the Mac; some devices reach it by another address. */
internal expect fun reachableFromDevice(url: String): String
