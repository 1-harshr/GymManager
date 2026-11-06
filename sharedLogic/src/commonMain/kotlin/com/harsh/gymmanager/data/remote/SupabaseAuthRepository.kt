package com.harsh.gymmanager.data.remote

import com.harsh.gymmanager.domain.repository.AuthRepository
import com.harsh.gymmanager.domain.repository.AuthState
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class SupabaseAuthRepository(private val supabase: SupabaseClient, scope: CoroutineScope) : AuthRepository {
    override val authState: StateFlow<AuthState> =
        supabase.auth.sessionStatus
            .map { status ->
                when (status) {
                    is SessionStatus.Initializing -> AuthState.Loading
                    is SessionStatus.Authenticated -> status.session.user?.toAuthState() ?: AuthState.Loading
                    // A failed refresh is usually just no network; the saved session is kept and retried.
                    is SessionStatus.RefreshFailure -> supabase.auth.currentUserOrNull()?.toAuthState() ?: AuthState.SignedOut
                    is SessionStatus.NotAuthenticated -> AuthState.SignedOut
                }
            }
            .stateIn(scope, SharingStarted.Eagerly, AuthState.Loading)

    private fun UserInfo.toAuthState() = AuthState.SignedIn(userId = id, phone = phone, email = email)

    override suspend fun sendPhoneCode(phone: String) {
        supabase.auth.signInWith(OTP) { this.phone = phone }
    }

    override suspend fun verifyPhoneCode(phone: String, code: String) {
        supabase.auth.verifyPhoneOtp(type = OtpType.Phone.SMS, phone = phone, token = code)
    }

    override suspend fun signOut() {
        supabase.auth.signOut()
    }
}
