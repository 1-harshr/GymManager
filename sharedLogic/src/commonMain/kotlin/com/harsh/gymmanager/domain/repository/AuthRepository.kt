package com.harsh.gymmanager.domain.repository

import kotlinx.coroutines.flow.StateFlow

sealed interface AuthState {
    /** The saved session is still being restored at launch. */
    data object Loading : AuthState

    data object SignedOut : AuthState

    data class SignedIn(val userId: String, val phone: String?, val email: String?) : AuthState
}

enum class SignInProblem {
    /** The code is wrong, or too old. */
    WRONG_CODE,
    /** Too many codes or attempts in a short time. */
    TOO_MANY_TRIES,
    /** The text message couldn't be sent to that number. */
    CODE_NOT_SENT,
    NO_CONNECTION,
}

class SignInException(val problem: SignInProblem, cause: Throwable? = null) : Exception(problem.name, cause)

/** Signing in and out. Failures are [SignInException]s where the reason is known. */
interface AuthRepository {
    val authState: StateFlow<AuthState>

    /** Texts a one-time code to [phone], given with its country code, e.g. "+919876543210". */
    suspend fun sendPhoneCode(phone: String)

    /** Signs in with the code from [sendPhoneCode], creating the account the first time. */
    suspend fun verifyPhoneCode(phone: String, code: String)

    suspend fun signOut()
}
