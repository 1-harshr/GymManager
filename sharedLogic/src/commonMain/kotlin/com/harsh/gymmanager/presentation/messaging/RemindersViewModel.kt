package com.harsh.gymmanager.presentation.messaging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsh.gymmanager.domain.messaging.MemberMessage
import com.harsh.gymmanager.domain.messaging.MessagingUseCases
import com.harsh.gymmanager.domain.messaging.ReminderItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RemindersUiState(
    val isLoading: Boolean = true,
    val items: List<ReminderItem> = emptyList(),
    /** Who "send next" goes to; null once everyone is done or skipped. */
    val next: ReminderItem? = null,
    /** 1-based place of [next] in this session's run. */
    val position: Int = 0,
    val total: Int = 0,
)

/**
 * The reminder list and its "send next" run: one tap opens WhatsApp for the next member who
 * hasn't been reminded in the last few days, then moves on.
 */
class RemindersViewModel(private val messaging: MessagingUseCases) : ViewModel() {
    private val handled = MutableStateFlow<Set<String>>(emptySet())

    val state: StateFlow<RemindersUiState> =
        combine(messaging.observeReminders(), handled) { items, handled ->
            val remaining = items.filter { it.message != null && !it.sentRecently && it.member.memberId !in handled }
            val done = handled.size
            RemindersUiState(
                isLoading = false,
                items = items,
                next = remaining.firstOrNull(),
                position = done + 1,
                total = done + remaining.size,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RemindersUiState())

    /** Call after WhatsApp opened with [message]. */
    fun sent(message: MemberMessage) {
        handled.update { it + message.memberId }
        viewModelScope.launch { messaging.markSent(message) }
    }

    fun skip(memberId: String) = handled.update { it + memberId }
}
