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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlansUiState(
    val isLoading: Boolean = true,
    val plans: List<Plan> = emptyList(),
    val showArchived: Boolean = false,
    /** Plans ticked in select mode; empty means not selecting. */
    val selectedIds: Set<Long> = emptySet(),
) {
    val isSelecting: Boolean get() = selectedIds.isNotEmpty()
    val selectedHasActive: Boolean get() = plans.any { it.id in selectedIds && !it.isArchived }
    val selectedHasArchived: Boolean get() = plans.any { it.id in selectedIds && it.isArchived }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlansViewModel(private val planUseCases: PlanUseCases) : ViewModel() {
    private val showArchived = MutableStateFlow(false)
    private val selected = MutableStateFlow<Set<Long>>(emptySet())

    private val eventChannel = Channel<String>(Channel.BUFFERED)
    /** Messages to show in a snackbar. */
    val messages: Flow<String> = eventChannel.receiveAsFlow()

    private val plans = showArchived.flatMapLatest { planUseCases.observePlans(includeArchived = it) }

    val state: StateFlow<PlansUiState> = combine(plans, showArchived, selected) { plans, include, selected ->
        val visible = plans.mapTo(HashSet()) { it.id }
        PlansUiState(isLoading = false, plans = plans, showArchived = include, selectedIds = selected intersect visible)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlansUiState())

    fun onShowArchivedChange(value: Boolean) = showArchived.update { value }

    fun archive(id: Long) = run("Plan archived") { planUseCases.archivePlan(id) }

    fun restore(id: Long) = run("Plan restored") { planUseCases.restorePlan(id) }

    fun toggleSelection(planId: Long) = selected.update { if (planId in it) it - planId else it + planId }

    fun selectAll() = selected.update { state.value.plans.mapTo(HashSet()) { it.id } }

    fun clearSelection() = selected.update { emptySet() }

    /** Archives the ticked plans that are active; archived ones are left as they are. */
    fun archiveSelected() = runBulk("Archived") { ids ->
        planUseCases.bulk.archive(state.value.plans.filter { it.id in ids && !it.isArchived }.map { it.id })
    }

    fun restoreSelected() = runBulk("Restored") { ids ->
        planUseCases.bulk.restore(state.value.plans.filter { it.id in ids && it.isArchived }.map { it.id })
    }

    private fun run(successMessage: String, action: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            eventChannel.send(action().fold({ successMessage }, { it.toUserMessage() }))
        }
    }

    private fun runBulk(verb: String, action: suspend (Set<Long>) -> Result<Int>) {
        val ids = state.value.selectedIds
        if (ids.isEmpty()) return
        viewModelScope.launch {
            action(ids)
                .onSuccess { count ->
                    clearSelection()
                    eventChannel.send("$verb ${if (count == 1) "1 plan" else "$count plans"}")
                }
                .onFailure { eventChannel.send(it.toUserMessage()) }
        }
    }
}
