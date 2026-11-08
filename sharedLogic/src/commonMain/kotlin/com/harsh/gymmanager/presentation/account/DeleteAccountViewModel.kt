package com.harsh.gymmanager.presentation.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsh.gymmanager.domain.repository.AccountDeletion
import com.harsh.gymmanager.domain.repository.GymRepository
import com.harsh.gymmanager.domain.repository.MembershipState
import com.harsh.gymmanager.domain.usecase.resultOf
import com.harsh.gymmanager.presentation.UiMessage
import com.harsh.gymmanager.presentation.toUiMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DeleteAccountUiState(
    /** Null until it's known what deleting would take with it. */
    val deletion: AccountDeletion? = null,
    /** The gym that goes too when [deletion] is [AccountDeletion.ACCOUNT_AND_GYM]; null without a gym. */
    val gymName: String? = null,
    /** What the person has typed to confirm deleting the gym. */
    val typedName: String = "",
    val isDeleting: Boolean = false,
    /** Couldn't find out what deleting would do. */
    val loadError: UiMessage? = null,
) {
    /** Deleting the gym asks for its name first, so it can't happen by a slip. */
    val canDelete: Boolean
        get() = !isDeleting && when (deletion) {
            AccountDeletion.ACCOUNT -> true
            AccountDeletion.ACCOUNT_AND_GYM -> typedName.trim().equals(gymName?.trim(), ignoreCase = true)
            AccountDeletion.HAND_OVER_FIRST, null -> false
        }
}

/**
 * Deleting your own account. Asks first what it would take with it, so the screen can warn about the
 * gym going too, or say someone else must be made owner first. Success signs out, which leaves the screen.
 */
class DeleteAccountViewModel(private val gyms: GymRepository) : ViewModel() {
    private val _state = MutableStateFlow(
        DeleteAccountUiState(gymName = (gyms.membership.value as? MembershipState.Member)?.membership?.gymName),
    )
    val state: StateFlow<DeleteAccountUiState> = _state.asStateFlow()

    /** Failures, said once. */
    private val messageChannel = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = messageChannel.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(deletion = null, loadError = null) }
        viewModelScope.launch {
            resultOf { gyms.accountDeletion() }
                .onSuccess { deletion -> _state.update { it.copy(deletion = deletion) } }
                .onFailure { e -> _state.update { it.copy(loadError = e.toUiMessage()) } }
        }
    }

    fun onTypedNameChange(name: String) = _state.update { it.copy(typedName = name) }

    fun delete() {
        if (!state.value.canDelete) return
        _state.update { it.copy(isDeleting = true) }
        viewModelScope.launch {
            resultOf { gyms.deleteAccount() }.onFailure { e ->
                _state.update { it.copy(isDeleting = false) }
                messageChannel.send(e.toUiMessage())
                // Someone may have joined or left meanwhile, changing what deleting would do.
                load()
            }
        }
    }
}
