package com.harsh.gymmanager.presentation.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsh.gymmanager.domain.model.MemberSummary
import com.harsh.gymmanager.domain.usecase.MemberFilter
import com.harsh.gymmanager.domain.usecase.MemberUseCases
import com.harsh.gymmanager.presentation.toUserMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MembersUiState(
    val query: String = "",
    val filter: MemberFilter = MemberFilter.ALL,
    val members: List<MemberSummary> = emptyList(),
    val isLoading: Boolean = true,
    /** Members ticked in select mode; empty means not selecting. */
    val selectedIds: Set<Long> = emptySet(),
) {
    val isSelecting: Boolean get() = selectedIds.isNotEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class MembersViewModel(private val memberUseCases: MemberUseCases) : ViewModel() {
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(MemberFilter.ALL)
    private val refreshes = MutableStateFlow(0)
    private val selected = MutableStateFlow<Set<Long>>(emptySet())

    private val messageChannel = Channel<String>(Channel.BUFFERED)
    /** Messages to show in a snackbar. */
    val messages: Flow<String> = messageChannel.receiveAsFlow()

    private val results = combine(
        query.debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MS },
        filter,
        refreshes,
    ) { query, filter, _ -> query to filter }
        .flatMapLatest { (query, filter) -> memberUseCases.observeMembers(query, filter) }

    val state: StateFlow<MembersUiState> = combine(query, filter, results, selected) { query, filter, members, selected ->
        // Keep only ticks on members still in the list, e.g. after a bulk archive moves them out.
        val visible = members.mapTo(HashSet()) { it.memberId }
        MembersUiState(query, filter, members, isLoading = false, selectedIds = selected intersect visible)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MembersUiState())

    fun onQueryChange(value: String) = query.update { value }

    /** Changing the filter ends select mode, so nothing hidden stays ticked. */
    fun onFilterChange(value: MemberFilter) {
        filter.update { value }
        clearSelection()
    }

    /** Call when the screen resumes so statuses are re-read after midnight. */
    fun refresh() = refreshes.update { it + 1 }

    fun toggleSelection(memberId: Long) = selected.update { if (memberId in it) it - memberId else it + memberId }

    fun selectAll() = selected.update { state.value.members.mapTo(HashSet()) { it.memberId } }

    fun clearSelection() = selected.update { emptySet() }

    fun archiveSelected() = runBulk({ "Archived ${members(it)}" }) { memberUseCases.bulk.archive(it) }

    fun restoreSelected() = runBulk({ "Restored ${members(it)}" }) { memberUseCases.bulk.restore(it) }

    fun deleteSelected() = runBulk({ "Deleted ${members(it)}" }) { memberUseCases.bulk.deletePermanently(it) }

    private fun runBulk(success: (Int) -> String, action: suspend (Set<Long>) -> Result<Int>) {
        val ids = state.value.selectedIds
        if (ids.isEmpty()) return
        viewModelScope.launch {
            action(ids)
                .onSuccess { clearSelection(); messageChannel.send(success(it)) }
                .onFailure { messageChannel.send(it.toUserMessage()) }
        }
    }

    private fun members(count: Int) = if (count == 1) "1 member" else "$count members"

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}
