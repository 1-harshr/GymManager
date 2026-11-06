package com.harsh.gymmanager.data.remote

import com.harsh.gymmanager.domain.backup.StoredFiles
import com.harsh.gymmanager.domain.repository.AuthRepository
import com.harsh.gymmanager.domain.repository.SettingsRepository
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
import io.github.jan.supabase.storage.Storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Everything the app gets from Supabase, on one connection made once per app launch. The session is
 * saved on the device and refreshed automatically. [scope] should live as long as the app.
 */
class SupabaseBackend(scope: CoroutineScope, files: StoredFiles) {
    private val client = createGymSupabaseClient()

    val auth: AuthRepository = SupabaseAuthRepository(client, scope)

    private val refresher = Refresher(ready = { gate.settledGymId() })

    private val photos = PhotoSync(client, files, scope, onArrived = refresher::changed)

    val gyms: GymRepository = SupabaseGymRepository(client, auth, photos, scope)

    private val gate = GymGate(auth, gyms)

    /** The gym's records online, for the real app once someone is signed in to a gym. */
    val data = RemoteGymData(
        members = SupabaseMemberRepository(client, refresher, gate, photos),
        plans = SupabasePlanRepository(client, refresher, gate),
        memberships = SupabaseMembershipRepository(client, refresher),
        payments = SupabasePaymentRepository(client, refresher, gate),
        usage = SupabaseUsageRepository(client),
    )

    init {
        LiveChanges(client, gyms, refresher, scope)
    }

    /** True while the gym's records can't be read, usually for want of a connection; screens show what was last read. */
    val unreachable: StateFlow<Boolean> = refresher.problem
        .map { it != null }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /**
     * For work started with the app off screen: waits up to [timeout] for the saved sign-in and the
     * gym, and says whether there's a gym to work on. False when signed out, not in a gym, or it
     * can't be reached; the work should then wait for another day.
     */
    suspend fun awaitGym(timeout: Duration = 20.seconds): Boolean =
        withTimeoutOrNull(timeout) { gate.settledGymId() } != null

    /** The phone's [local] settings with the gym's name, address and logo taken from the gym online. */
    fun gymSettings(local: SettingsRepository): SettingsRepository = CloudGymSettingsRepository(local, gyms, photos)

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
        install(Storage)
    }

/** Local Supabase runs on the Mac; some devices reach it by another address. */
internal expect fun reachableFromDevice(url: String): String
