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
    /** Google or Apple vouched for the person, but the sign-in didn't go through. */
    IDENTITY_REJECTED,
}

enum class IdentityProvider { GOOGLE, APPLE }

/**
 * Google's or Apple's word, given on the phone, for who is signing in. [nonce] is the random value the
 * token was asked for with (its hash is inside the token), so a token taken from elsewhere can't be replayed.
 */
class IdentityToken(val provider: IdentityProvider, val idToken: String, val nonce: String)

class SignInException(val problem: SignInProblem, cause: Throwable? = null) : Exception(problem.name, cause)

/** Signing in and out. Failures are [SignInException]s where the reason is known. */
interface AuthRepository {
    val authState: StateFlow<AuthState>

    /** Texts a one-time code to [phone], given with its country code, e.g. "+919876543210". */
    suspend fun sendPhoneCode(phone: String)

    /** Signs in with the code from [sendPhoneCode], creating the account the first time. */
    suspend fun verifyPhoneCode(phone: String, code: String)

    /** Signs in as the person Google or Apple vouched for, creating the account the first time. */
    suspend fun signInWithIdentity(token: IdentityToken)

    suspend fun signOut()
}
