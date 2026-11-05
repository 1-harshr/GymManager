package com.harsh.gymmanager.presentation.importmembers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsh.gymmanager.domain.importing.ImportPreview
import com.harsh.gymmanager.domain.usecase.ImportMembersUseCase
import com.harsh.gymmanager.presentation.toUserMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ImportMembersUiState(
    val fileName: String? = null,
    /** What the chosen file holds, before anything is saved. */
    val preview: ImportPreview? = null,
    val isReading: Boolean = false,
    val isImporting: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface ImportMembersEvent {
    data class Imported(val count: Int) : ImportMembersEvent
}

class ImportMembersViewModel(private val importMembers: ImportMembersUseCase) : ViewModel() {
    private val _state = MutableStateFlow(ImportMembersUiState())
    val state: StateFlow<ImportMembersUiState> = _state.asStateFlow()

    private val eventChannel = Channel<ImportMembersEvent>(Channel.BUFFERED)
    val events: Flow<ImportMembersEvent> = eventChannel.receiveAsFlow()

    /** Called with the picked file's name and text, or null text if it couldn't be read. */
    fun onFilePicked(name: String, text: String?) {
        if (text == null) {
            _state.update { ImportMembersUiState(fileName = name, errorMessage = "Couldn't read $name. Save it as CSV and try again.") }
            return
        }
        _state.update { ImportMembersUiState(fileName = name, isReading = true) }
        viewModelScope.launch {
            importMembers.preview(text)
                .onSuccess { preview -> _state.update { it.copy(isReading = false, preview = preview) } }
                .onFailure { e -> _state.update { it.copy(isReading = false, errorMessage = e.toUserMessage()) } }
        }
    }

    fun import() {
        val current = _state.value
        val members = current.preview?.members.orEmpty()
        if (current.isImporting || members.isEmpty()) return
        _state.update { it.copy(isImporting = true, errorMessage = null) }
        viewModelScope.launch {
            importMembers.import(members)
                .onSuccess { eventChannel.send(ImportMembersEvent.Imported(it)) }
                .onFailure { e -> _state.update { it.copy(isImporting = false, errorMessage = e.toUserMessage()) } }
        }
    }
}
