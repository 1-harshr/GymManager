package com.harsh.gymmanager.presentation.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsh.gymmanager.domain.model.InboxItem
import com.harsh.gymmanager.domain.usecase.InboxUseCases
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class InboxUiState(
    val isLoading: Boolean = true,
    /** Newest first. */
    val items: List<InboxItem> = emptyList(),
    /** Alerts that were unread when the inbox opened; they stay highlighted while it's open. */
    val newIds: Set<String> = emptySet(),
)

/** Opening the inbox marks everything read, but what was new keeps its highlight until the owner leaves. */
class InboxViewModel(private val inbox: InboxUseCases) : ViewModel() {
    private var newIds: Set<String> = emptySet()

    val state: StateFlow<InboxUiState> = inbox.observeItems()
        .map { items -> InboxUiState(isLoading = false, items = items, newIds = newIds) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InboxUiState())

    init {
        viewModelScope.launch {
            newIds = inbox.observeItems().first().filterNot { it.isRead }.map { it.id }.toSet()
            inbox.markAllRead()
        }
    }
}
