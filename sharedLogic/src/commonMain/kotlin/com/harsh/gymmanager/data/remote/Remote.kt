package com.harsh.gymmanager.data.remote

import com.harsh.gymmanager.domain.NoConnectionException
import com.harsh.gymmanager.domain.OverpaymentException
import com.harsh.gymmanager.domain.repository.GymRepository
import com.harsh.gymmanager.domain.repository.MembershipState
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.coroutines.cancellation.CancellationException

/**
 * Keeps the screens' reads fresh. Every write calls [changed], and each [observe]d read runs again;
 * so do they all when the app comes back to the front. A read that fails keeps showing its last good
 * value, and [problem] says why until a read succeeds again.
 */
internal class Refresher {
    private val ticks = MutableStateFlow(0)
    private val _problem = MutableStateFlow<Throwable?>(null)
    val problem: StateFlow<Throwable?> = _problem.asStateFlow()

    fun changed() = ticks.update { it + 1 }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun <T> observe(read: suspend () -> T): Flow<T> = ticks.flatMapLatest {
        flow {
            try {
                val value = remote { read() }
                _problem.value = null
                emit(value)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _problem.value = e
            }
        }
    }
}

/** The signed-in person's gym; every row written belongs to it. */
internal fun GymRepository.currentGymId(): String =
    (membership.value as? MembershipState.Member)?.membership?.gymId ?: throw IllegalStateException("Not part of a gym")

/**
 * Runs a request and turns the failures the app knows how to explain into its own exceptions: a lost
 * connection, and a payment above what's due (the database says how much is due).
 */
internal suspend fun <T> remote(request: suspend () -> T): T =
    try {
        request()
    } catch (e: HttpRequestException) {
        throw NoConnectionException(e)
    } catch (e: HttpRequestTimeoutException) {
        throw NoConnectionException(e)
    } catch (e: PostgrestRestException) {
        if (e.error == "overpayment") {
            val due = (e.details as? JsonPrimitive)?.let { it.longOrNull ?: it.content.toLongOrNull() } ?: 0
            throw OverpaymentException(due)
        }
        throw e
    }
