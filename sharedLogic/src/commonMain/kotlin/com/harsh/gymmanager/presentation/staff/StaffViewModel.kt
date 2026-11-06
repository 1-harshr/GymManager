package com.harsh.gymmanager.presentation.staff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsh.gymmanager.domain.model.StaffInvite
import com.harsh.gymmanager.domain.model.StaffMember
import com.harsh.gymmanager.domain.model.StaffRole
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

data class StaffUiState(
    val isLoading: Boolean = true,
    val isOwner: Boolean = false,
    val staff: List<StaffMember> = emptyList(),
    /** Only the owner sees these. */
    val invites: List<StaffInvite> = emptyList(),
    /** Something is being saved; actions wait for it. */
    val isBusy: Boolean = false,
    /** The invite just made, shown so the owner can share it; null once they're done. */
    val newInvite: StaffInvite? = null,
    /** Couldn't load the list. */
    val loadError: UiMessage? = null,
)

/** Who works at the gym. The owner invites people, changes roles and removes people; staff can leave. */
class StaffViewModel(private val gyms: GymRepository) : ViewModel() {
    private val _state = MutableStateFlow(StaffUiState())
    val state: StateFlow<StaffUiState> = _state.asStateFlow()

    /** Confirmations and failures, said once. */
    private val messageChannel = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = messageChannel.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        val isOwner = (gyms.membership.value as? MembershipState.Member)?.membership?.isOwner == true
        _state.update { it.copy(isLoading = true, isOwner = isOwner, loadError = null) }
        viewModelScope.launch {
            resultOf {
                val staff = gyms.staff()
                val invites = if (isOwner) gyms.pendingInvites() else emptyList()
                staff to invites
            }
                .onSuccess { (staff, invites) -> _state.update { it.copy(isLoading = false, staff = staff, invites = invites) } }
                .onFailure { e -> _state.update { it.copy(isLoading = false, loadError = e.toUiMessage()) } }
        }
    }

    fun invite(role: StaffRole = StaffRole.STAFF) = act {
        val invite = gyms.createInvite(role)
        _state.update { it.copy(newInvite = invite, invites = listOf(invite) + it.invites) }
        null
    }

    fun dismissNewInvite() = _state.update { it.copy(newInvite = null) }

    fun cancelInvite(code: String) = act {
        gyms.cancelInvite(code)
        _state.update { it.copy(invites = it.invites.filterNot { invite -> invite.code == code }) }
        UiMessage.InviteCancelled
    }

    fun setRole(userId: String, role: StaffRole) = act {
        gyms.setRole(userId, role)
        reloadStaff()
        UiMessage.RoleChanged
    }

    fun remove(userId: String) = act {
        gyms.removeStaff(userId)
        reloadStaff()
        UiMessage.StaffRemoved
    }

    /** The app closes the gym by itself once they've left. */
    fun leave() = act {
        gyms.leaveGym()
        null
    }

    private suspend fun reloadStaff() {
        val staff = gyms.staff()
        _state.update { it.copy(staff = staff) }
    }

    /** Runs one change at a time; [action] returns the confirmation to show, if any. */
    private fun act(action: suspend () -> UiMessage?) {
        if (_state.value.isBusy) return
        _state.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            resultOf { action() }
                .onSuccess { message -> message?.let { messageChannel.send(it) } }
                .onFailure { e -> messageChannel.send(e.toUiMessage()) }
            _state.update { it.copy(isBusy = false) }
        }
    }
}
