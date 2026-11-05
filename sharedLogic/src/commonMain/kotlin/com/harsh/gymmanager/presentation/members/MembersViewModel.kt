package com.harsh.gymmanager.presentation.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsh.gymmanager.domain.model.MemberSummary
import com.harsh.gymmanager.domain.usecase.MemberFilter
import com.harsh.gymmanager.domain.usecase.MemberUseCases
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class MembersUiState(
    val query: String = "",
    val filter: MemberFilter = MemberFilter.ALL,
    val members: List<MemberSummary> = emptyList(),
    val isLoading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class MembersViewModel(private val memberUseCases: MemberUseCases) : ViewModel() {
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(MemberFilter.ALL)
    private val refreshes = MutableStateFlow(0)

    private val results = combine(
        query.debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MS },
        filter,
        refreshes,
    ) { query, filter, _ -> query to filter }
        .flatMapLatest { (query, filter) -> memberUseCases.observeMembers(query, filter) }

    val state: StateFlow<MembersUiState> = combine(query, filter, results) { query, filter, members ->
        MembersUiState(query = query, filter = filter, members = members, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MembersUiState())

    fun onQueryChange(value: String) = query.update { value }

    fun onFilterChange(value: MemberFilter) = filter.update { value }

    /** Call when the screen resumes so statuses are re-read after midnight. */
    fun refresh() = refreshes.update { it + 1 }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}
