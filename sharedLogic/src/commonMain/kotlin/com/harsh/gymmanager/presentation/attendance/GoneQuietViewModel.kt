package com.harsh.gymmanager.presentation.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsh.gymmanager.domain.messaging.MemberMessage
import com.harsh.gymmanager.domain.usecase.AttendanceUseCases
import com.harsh.gymmanager.domain.usecase.QuietMember
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GoneQuietUiState(
    val isLoading: Boolean = true,
    val items: List<QuietMember> = emptyList(),
)

/** Members with a running membership who've stopped coming, each a tap from a "we miss you". */
class GoneQuietViewModel(private val attendance: AttendanceUseCases) : ViewModel() {
    val state: StateFlow<GoneQuietUiState> = attendance.observeGoneQuiet()
        .map { GoneQuietUiState(isLoading = false, items = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoneQuietUiState())

    /** Call after WhatsApp opened with [message]. */
    fun sent(message: MemberMessage) {
        viewModelScope.launch { attendance.markSent(message) }
    }
}
