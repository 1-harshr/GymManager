package com.harsh.gymmanager.presentation.signin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsh.gymmanager.domain.repository.AuthRepository
import com.harsh.gymmanager.domain.repository.IdentityProvider
import com.harsh.gymmanager.domain.repository.IdentityToken
import com.harsh.gymmanager.domain.usecase.resultOf
import com.harsh.gymmanager.presentation.UiMessage
import com.harsh.gymmanager.presentation.toUiMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/** Indian mobile numbers only for now: the +91 is shown, and the person types the 10 digits. */
const val PHONE_COUNTRY_CODE = "+91"
const val PHONE_DIGITS = 10
const val CODE_DIGITS = 6

/** How long before another code can be asked for. */
const val RESEND_WAIT_SECONDS = 30

data class SignInUiState(
    /** False while typing the number; true once a code has been sent to it. */
    val awaitingCode: Boolean = false,
    /** The 10 digits after +91. */
    val phone: String = "",
    val code: String = "",
    val isBusy: Boolean = false,
    val phoneError: UiMessage? = null,
    val codeError: UiMessage? = null,
    /** Seconds until "Send again" works; 0 when it does. */
    val resendInSeconds: Int = 0,
    /** Goes up with each rejected code, so the screen can react even when the message repeats. */
    val failedAttempts: Int = 0,
    /** Google or Apple while signing in with it. */
    val signingInWith: IdentityProvider? = null,
    val identityError: UiMessage? = null,
) {
    val isPhoneComplete: Boolean get() = phone.length == PHONE_DIGITS
    val isCodeComplete: Boolean get() = code.length == CODE_DIGITS
}

/**
 * Signs in by phone (send a code, then type it in) or with Google or Apple. There's nothing to do on
 * success; the app notices the new session and moves on by itself.
 */
class SignInViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(SignInUiState())
    val state: StateFlow<SignInUiState> = _state.asStateFlow()

    private var countdown: Job? = null

    private val fullPhone get() = PHONE_COUNTRY_CODE + _state.value.phone

    /** A pasted number may come with its 91 or a leading 0; both are dropped. */
    fun onPhoneChange(value: String) {
        val digits = value.filter(Char::isDigit)
        val local = when {
            digits.length > PHONE_DIGITS && digits.startsWith("91") -> digits.drop(2)
            digits.length > PHONE_DIGITS && digits.startsWith("0") -> digits.drop(1)
            else -> digits
        }
        _state.update { it.copy(phone = local.take(PHONE_DIGITS), phoneError = null, identityError = null) }
    }

    /**
     * Signs in with Google or Apple. [askProvider] shows their sheet and returns what they vouched for,
     * or null if the person backed out of it, which isn't an error.
     */
    fun signInWith(provider: IdentityProvider, askProvider: suspend () -> IdentityToken?) {
        if (_state.value.isBusy) return
        _state.update { it.copy(isBusy = true, signingInWith = provider, identityError = null, phoneError = null) }
        viewModelScope.launch {
            resultOf { askProvider()?.also { auth.signInWithIdentity(it) } }
                // Stays busy once signed in: this screen is about to be replaced.
                .onSuccess { token -> if (token == null) _state.update { it.copy(isBusy = false, signingInWith = null) } }
                .onFailure { e -> _state.update { it.copy(isBusy = false, signingInWith = null, identityError = e.toUiMessage()) } }
        }
    }

    /** Fills in the last digit of the code verifies it straight away. */
    fun onCodeChange(value: String) {
        // The field stays focused while a code is checked, so typing then is ignored rather than lost mid-check.
        if (_state.value.isBusy) return
        val code = value.filter(Char::isDigit).take(CODE_DIGITS)
        _state.update { it.copy(code = code, codeError = null) }
        if (code.length == CODE_DIGITS) verifyCode()
    }

    fun sendCode() {
        val current = _state.value
        if (current.isBusy) return
        if (current.phone.length != PHONE_DIGITS) {
            _state.update { it.copy(phoneError = UiMessage.EnterPhone) }
            return
        }
        _state.update { it.copy(isBusy = true, phoneError = null, identityError = null) }
        viewModelScope.launch {
            resultOf { auth.sendPhoneCode(fullPhone) }
                .onSuccess {
                    _state.update { it.copy(isBusy = false, awaitingCode = true, code = "", codeError = null) }
                    startResendCountdown()
                }
                .onFailure { e -> _state.update { it.copy(isBusy = false, phoneError = e.toUiMessage()) } }
        }
    }

    fun resendCode() {
        val current = _state.value
        if (current.isBusy || current.resendInSeconds > 0) return
        _state.update { it.copy(isBusy = true, code = "", codeError = null) }
        viewModelScope.launch {
            resultOf { auth.sendPhoneCode(fullPhone) }
                .onSuccess {
                    _state.update { it.copy(isBusy = false) }
                    startResendCountdown()
                }
                .onFailure { e -> _state.update { it.copy(isBusy = false, codeError = e.toUiMessage()) } }
        }
    }

    fun verifyCode() {
        val current = _state.value
        if (current.isBusy) return
        if (current.code.length != CODE_DIGITS) {
            _state.update { it.copy(codeError = UiMessage.EnterCode) }
            return
        }
        _state.update { it.copy(isBusy = true, codeError = null) }
        viewModelScope.launch {
            resultOf { auth.verifyPhoneCode(fullPhone, current.code) }
                // Stays busy on success: this screen is about to be replaced.
                .onFailure { e ->
                    _state.update {
                        it.copy(isBusy = false, code = "", codeError = e.toUiMessage(), failedAttempts = it.failedAttempts + 1)
                    }
                }
        }
    }

    /** Back to the number, to fix a typo. */
    fun changeNumber() {
        countdown?.cancel()
        _state.update { it.copy(awaitingCode = false, code = "", codeError = null, resendInSeconds = 0) }
    }

    private fun startResendCountdown() {
        countdown?.cancel()
        countdown = viewModelScope.launch {
            for (left in RESEND_WAIT_SECONDS downTo 0) {
                _state.update { it.copy(resendInSeconds = left) }
                if (left > 0) delay(1.seconds)
            }
        }
    }
}
