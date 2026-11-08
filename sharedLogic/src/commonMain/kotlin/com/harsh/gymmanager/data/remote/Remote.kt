package com.harsh.gymmanager.data.remote

import com.harsh.gymmanager.domain.NoConnectionException
import com.harsh.gymmanager.domain.OverpaymentException
import com.harsh.gymmanager.domain.repository.AuthRepository
import com.harsh.gymmanager.domain.repository.AuthState
import com.harsh.gymmanager.domain.repository.GymRepository
import com.harsh.gymmanager.domain.repository.MembershipState
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.coroutines.cancellation.CancellationException

/**
 * Keeps the screens' reads fresh. Every write calls [changed], and each [observe]d read runs again;
 * so do they all when the app comes back to the front. A read that fails keeps showing its last good
 * value, and [problem] says why for as long as any read being watched is failing.
 */
internal class Refresher(
    /** Waits until reads can be made, i.e. it's known whether someone is signed in to a gym. */
    private val ready: suspend () -> Unit = {},
) {
    private val ticks = MutableStateFlow(0)

    /**
     * Each watched read's latest failure. Kept per read so one that keeps failing isn't hidden by
     * others that succeed, and dropped once nobody is watching it.
     */
    private val failing = MutableStateFlow<Map<Any, Throwable>>(emptyMap())
    val problem: Flow<Throwable?> = failing.map { it.values.firstOrNull() }

    fun changed() = ticks.update { it + 1 }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun <T> observe(read: suspend () -> T): Flow<T> = flow {
        val key = Any()
        try {
            emitAll(
                ticks.flatMapLatest {
                    flow {
                        val value = try {
                            ready()
                            remote { read() }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            failing.update { it + (key to e) }
                            return@flow
                        }
                        failing.update { it - key }
                        emit(value)
                    }
                },
            )
        } finally {
            failing.update { it - key }
        }
    }
}

/**
 * Whether someone is signed in to a gym, once that's known. Work started with the app off screen, such
 * as the morning update or a Siri request, may begin before the saved sign-in has been read.
 */
internal class GymGate(private val auth: AuthRepository, private val gyms: GymRepository) {
    /** The signed-in person's gym, or null if they're signed out or not in one (or it can't be reached). */
    suspend fun settledGymId(): String? =
        combine(auth.authState, gyms.membership) { auth, membership -> auth to membership }
            .first { (auth, membership) ->
                auth is AuthState.SignedOut || (auth is AuthState.SignedIn && membership !is MembershipState.Loading)
            }
            .second
            .let { (it as? MembershipState.Member)?.membership?.gymId }

    /** The gym every row written belongs to. */
    suspend fun gymId(): String = settledGymId() ?: throw IllegalStateException("Not part of a gym")
}

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
