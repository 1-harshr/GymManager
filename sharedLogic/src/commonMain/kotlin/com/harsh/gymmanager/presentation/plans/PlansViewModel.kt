package com.harsh.gymmanager.presentation.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsh.gymmanager.domain.model.Plan
import com.harsh.gymmanager.domain.usecase.PlanUseCases
import com.harsh.gymmanager.presentation.toUserMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlansUiState(
    val isLoading: Boolean = true,
    val plans: List<Plan> = emptyList(),
    val showArchived: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class PlansViewModel(private val planUseCases: PlanUseCases) : ViewModel() {
    private val showArchived = MutableStateFlow(false)

    private val eventChannel = Channel<String>(Channel.BUFFERED)
    /** Messages to show in a snackbar. */
    val messages: Flow<String> = eventChannel.receiveAsFlow()

    val state: StateFlow<PlansUiState> = showArchived
        .flatMapLatest { include ->
            planUseCases.observePlans(includeArchived = include).map { PlansUiState(false, it, include) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlansUiState())

    fun onShowArchivedChange(value: Boolean) = showArchived.update { value }

    fun archive(id: Long) = run("Plan archived") { planUseCases.archivePlan(id) }

    fun restore(id: Long) = run("Plan restored") { planUseCases.restorePlan(id) }

    private fun run(successMessage: String, action: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            eventChannel.send(action().fold({ successMessage }, { it.toUserMessage() }))
        }
    }
}
