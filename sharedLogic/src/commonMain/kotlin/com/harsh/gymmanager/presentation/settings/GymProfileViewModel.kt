package com.harsh.gymmanager.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsh.gymmanager.domain.ValidationError
import com.harsh.gymmanager.domain.usecase.SettingsUseCases
import com.harsh.gymmanager.presentation.UiMessage
import com.harsh.gymmanager.presentation.toUiMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GymProfileUiState(
    val isLoading: Boolean = true,
    /** True on first launch, before any gym is saved. */
    val isFirstRun: Boolean = false,
    val name: String = "",
    val address: String = "",
    val logoPath: String? = null,
    val nameError: UiMessage? = null,
    val errorMessage: UiMessage? = null,
    val isSaving: Boolean = false,
    /** True once the owner has edited anything, so leaving asks before throwing it away. */
    val hasChanges: Boolean = false,
)

sealed interface GymProfileEvent {
    data object Saved : GymProfileEvent
}

/** Sets up the gym on first launch, and edits its name, address and logo later. */
class GymProfileViewModel(private val settings: SettingsUseCases) : ViewModel() {
    private val _state = MutableStateFlow(GymProfileUiState())
    val state: StateFlow<GymProfileUiState> = _state.asStateFlow()

    private val eventChannel = Channel<GymProfileEvent>(Channel.BUFFERED)
    val events: Flow<GymProfileEvent> = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            val gym = settings.observe().first().gym
            _state.update {
                it.copy(
                    isLoading = false,
                    isFirstRun = gym == null,
                    name = gym?.name.orEmpty(),
                    address = gym?.address.orEmpty(),
                    logoPath = gym?.logoPath,
                )
            }
        }
    }

    fun onNameChange(value: String) = _state.update { it.copy(name = value, nameError = null, errorMessage = null, hasChanges = true) }

    fun onAddressChange(value: String) = _state.update { it.copy(address = value, hasChanges = true) }

    fun onLogoChange(path: String?) = _state.update { it.copy(logoPath = path, hasChanges = true) }

    fun save() {
        val current = _state.value
        if (current.isSaving || current.isLoading) return
        if (current.name.isBlank()) {
            _state.update { it.copy(nameError = UiMessage.Invalid(ValidationError.GYM_NAME_REQUIRED)) }
            return
        }
        _state.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            settings.saveGym(current.name, current.address, current.logoPath)
                .onSuccess { eventChannel.send(GymProfileEvent.Saved) }
                .onFailure { e -> _state.update { it.copy(isSaving = false, errorMessage = e.toUiMessage()) } }
        }
    }
}
