package com.harsh.gymmanager.data.remote

import com.harsh.gymmanager.domain.repository.GymRepository
import com.harsh.gymmanager.domain.repository.MembershipState
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * While someone is in a gym, listens for changes anyone at the gym makes and reloads what the screens
 * show; a change to the gym or its staff rechecks the person's own place in it, so a renamed gym shows
 * its new name and someone removed is shown out at once. A burst of changes, such as a bulk renewal, reloads once.
 */
internal class LiveChanges(
    private val supabase: SupabaseClient,
    private val gyms: GymRepository,
    private val refresher: Refresher,
    scope: CoroutineScope,
) {
    init {
        scope.launch {
            gyms.membership
                .map { (it as? MembershipState.Member)?.membership?.gymId }
                .distinctUntilChanged()
                .collectLatest { gymId -> if (gymId != null) listen(gymId) }
        }
    }

    @OptIn(FlowPreview::class)
    private suspend fun listen(gymId: String) {
        val channel = supabase.channel("gym-$gymId")
        val records = merge(*RECORD_TABLES.map { table -> channel.postgresChangeFlow<PostgresAction>("public") { this.table = table } }.toTypedArray())
        val gym = merge(*GYM_TABLES.map { table -> channel.postgresChangeFlow<PostgresAction>("public") { this.table = table } }.toTypedArray())
        try {
            channel.subscribe()
            coroutineScope {
                launch { records.debounce(SETTLE).collect { refresher.changed() } }
                launch { gym.debounce(SETTLE).collect { gyms.refresh() } }
            }
        } finally {
            // Leaving a gym, or signing out, stops listening to it.
            withContext(NonCancellable) { runCatching { supabase.realtime.removeChannel(channel) } }
        }
    }

    private companion object {
        val RECORD_TABLES = listOf("plans", "members", "memberships", "payments", "check_ins", "membership_pauses")

        /** The gym's own details and its staff, which decide what the person sees and may do. */
        val GYM_TABLES = listOf("gyms", "gym_staff")
        val SETTLE = 300.milliseconds
    }
}
