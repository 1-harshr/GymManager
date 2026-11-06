package com.harsh.gymmanager.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsh.gymmanager.domain.ValidationError
import com.harsh.gymmanager.domain.repository.GymRepository
import com.harsh.gymmanager.domain.usecase.resultOf
import com.harsh.gymmanager.presentation.UiMessage
import com.harsh.gymmanager.presentation.toUiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Invite codes are 8 letters and digits; see create_invite in the database. */
const val INVITE_CODE_LENGTH = 8

enum class OnboardingStep {
    /** Owner or staff? */
    CHOOSE,
    CREATE,
    JOIN,
}

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.CHOOSE,
    val yourName: String = "",
    val gymName: String = "",
    val gymAddress: String = "",
    /** Upper-case letters and digits only, without the dash it's shown with. */
    val code: String = "",
    val isBusy: Boolean = false,
    val yourNameError: UiMessage? = null,
    val gymNameError: UiMessage? = null,
    val codeError: UiMessage? = null,
    val errorMessage: UiMessage? = null,
) {
    val isCodeComplete: Boolean get() = code.length == INVITE_CODE_LENGTH
}

/**
 * After signing in to no gym: set one up as its owner, or join one with the code its owner shared.
 * Nothing to do on success; the app notices the new membership and opens the gym.
 */
class OnboardingViewModel(private val gyms: GymRepository) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun choose(step: OnboardingStep) = _state.update { it.copy(step = step, errorMessage = null) }

    /** Back to choosing; what was typed is kept in case they come back. */
    fun back() = _state.update {
        it.copy(step = OnboardingStep.CHOOSE, yourNameError = null, gymNameError = null, codeError = null, errorMessage = null)
    }

    fun onYourNameChange(value: String) = _state.update { it.copy(yourName = value, yourNameError = null, errorMessage = null) }

    fun onGymNameChange(value: String) = _state.update { it.copy(gymName = value, gymNameError = null, errorMessage = null) }

    fun onGymAddressChange(value: String) = _state.update { it.copy(gymAddress = value) }

    /** Takes a pasted code with its dash or spaces, in any case. */
    fun onCodeChange(value: String) = _state.update {
        val code = value.uppercase().filter { c -> c.isLetterOrDigit() }.take(INVITE_CODE_LENGTH)
        it.copy(code = code, codeError = null, errorMessage = null)
    }

    fun create() {
        val current = _state.value
        if (current.isBusy) return
        val yourNameError = UiMessage.EnterYourName.takeIf { current.yourName.isBlank() }
        val gymNameError = UiMessage.Invalid(ValidationError.GYM_NAME_REQUIRED).takeIf { current.gymName.isBlank() }
        if (yourNameError != null || gymNameError != null) {
            _state.update { it.copy(yourNameError = yourNameError, gymNameError = gymNameError) }
            return
        }
        submit { gyms.createGym(current.gymName, current.gymAddress, current.yourName) }
    }

    fun join() {
        val current = _state.value
        if (current.isBusy) return
        val codeError = UiMessage.EnterInviteCode.takeIf { !current.isCodeComplete }
        val yourNameError = UiMessage.EnterYourName.takeIf { current.yourName.isBlank() }
        if (codeError != null || yourNameError != null) {
            _state.update { it.copy(codeError = codeError, yourNameError = yourNameError) }
            return
        }
        submit { gyms.joinGym(current.code, current.yourName) }
    }

    private fun submit(action: suspend () -> Unit) {
        _state.update { it.copy(isBusy = true, errorMessage = null) }
        viewModelScope.launch {
            resultOf { action() }
                // Stays busy on success: this screen is about to be replaced by the gym.
                .onFailure { e -> _state.update { it.copy(isBusy = false, errorMessage = e.toUiMessage()) } }
        }
    }
}
