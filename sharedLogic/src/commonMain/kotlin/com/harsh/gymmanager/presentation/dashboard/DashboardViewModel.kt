package com.harsh.gymmanager.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsh.gymmanager.domain.usecase.Dashboard
import com.harsh.gymmanager.domain.usecase.ObserveDashboardUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class DashboardUiState(
    val isLoading: Boolean = true,
    val dashboard: Dashboard? = null,
    /** Members due a WhatsApp reminder who haven't had one in the last few days. */
    val remindersToSend: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    observeDashboard: ObserveDashboardUseCase,
    remindersToSend: () -> Flow<Int> = { flowOf(0) },
) : ViewModel() {
    private val refreshes = MutableStateFlow(0)

    val state: StateFlow<DashboardUiState> = refreshes
        .flatMapLatest { combine(observeDashboard(), remindersToSend(), ::Pair) }
        .map { (dashboard, reminders) -> DashboardUiState(isLoading = false, dashboard = dashboard, remindersToSend = reminders) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    /** Call when the screen resumes so "today" is re-read after midnight. */
    fun refresh() = refreshes.update { it + 1 }
}
