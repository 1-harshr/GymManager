package com.harsh.gymmanager.presentation.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsh.gymmanager.domain.model.MemberSummary
import com.harsh.gymmanager.domain.usecase.AttendanceUseCases
import com.harsh.gymmanager.domain.usecase.MemberUseCases
import com.harsh.gymmanager.domain.usecase.Visit
import com.harsh.gymmanager.presentation.UiMessage
import com.harsh.gymmanager.presentation.toUiMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How many search results the check-in search shows. */
private const val MAX_MATCHES = 20

data class AttendanceUiState(
    val isLoading: Boolean = true,
    /** Who's in today, latest first. */
    val visits: List<Visit> = emptyList(),
    val query: String = "",
    /** Members matching [query]; empty while it's blank. */
    val matches: List<MemberSummary> = emptyList(),
    /** Ids of everyone already in today, so a match can show it. */
    val checkedIn: Set<String> = emptySet(),
    /** Members with a running membership who've stopped coming. */
    val goneQuiet: Int = 0,
)

/** Today's attendance: who's in, and a search to check someone in. */
@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceViewModel(private val attendance: AttendanceUseCases, members: MemberUseCases) : ViewModel() {
    private val query = MutableStateFlow("")
    private val refreshes = MutableStateFlow(0)
    private val messageChannel = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = messageChannel.receiveAsFlow()

    private val matches: Flow<List<MemberSummary>> = query.flatMapLatest { q ->
        if (q.isBlank()) flowOf(emptyList()) else members.observeMembers(q).map { it.take(MAX_MATCHES) }
    }

    val state: StateFlow<AttendanceUiState> = refreshes
        .flatMapLatest {
            combine(attendance.observeToday(), query, matches, attendance.observeGoneQuiet()) { visits, query, matches, quiet ->
                AttendanceUiState(
                    isLoading = false,
                    visits = visits,
                    query = query,
                    matches = matches,
                    checkedIn = visits.mapTo(mutableSetOf()) { it.member.memberId },
                    goneQuiet = quiet.size,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AttendanceUiState())

    fun onQueryChange(value: String) = query.update { value }

    /** Checks [member] in and clears the search; the message warns if their membership needs attention. */
    fun checkIn(member: MemberSummary) {
        val already = member.memberId in state.value.checkedIn
        viewModelScope.launch {
            attendance.checkIn(member.memberId)
                .onSuccess {
                    query.value = ""
                    messageChannel.send(if (already) UiMessage.AlreadyCheckedIn(member.name) else UiMessage.CheckedIn(member.name, member.status, member.amountDue))
                }
                .onFailure { messageChannel.send(it.toUiMessage()) }
        }
    }

    fun undo(visit: Visit) {
        viewModelScope.launch {
            val message = attendance.undoCheckIn(visit.checkIn.id).fold({ UiMessage.CheckInUndone(visit.member.name) }, { it.toUiMessage() })
            messageChannel.send(message)
        }
    }

    /** Call when the screen resumes so "today" is re-read after midnight. */
    fun refresh() = refreshes.update { it + 1 }
}
