package com.harsh.gymmanager.presentation.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsh.gymmanager.domain.backup.BackupFile
import com.harsh.gymmanager.domain.backup.BackupUseCases
import com.harsh.gymmanager.presentation.UiMessage
import com.harsh.gymmanager.presentation.toUiMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BackupUiState(
    val isWorking: Boolean = false,
    /** A picked backup waiting for the owner to confirm the restore. */
    val pendingRestore: BackupFile? = null,
)

sealed interface BackupEvent {
    /** The screen asks the system where to save [bytes], then reports back with [BackupViewModel.onSaved]. */
    class SaveFile(val fileName: String, val bytes: ByteArray) : BackupEvent
    data class ShowMessage(val message: UiMessage) : BackupEvent
}

class BackupViewModel(private val backup: BackupUseCases) : ViewModel() {
    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    private val eventChannel = Channel<BackupEvent>(Channel.BUFFERED)
    val events: Flow<BackupEvent> = eventChannel.receiveAsFlow()

    fun backUp() {
        if (_state.value.isWorking) return
        _state.update { it.copy(isWorking = true) }
        viewModelScope.launch {
            backup.create()
                .onSuccess { eventChannel.send(BackupEvent.SaveFile(backup.fileName(), it)) }
                .onFailure { fail(it) }
        }
    }

    /** [saved] is false when the owner backed out of the save dialog or writing failed. */
    fun onSaved(saved: Boolean) {
        viewModelScope.launch {
            if (saved) {
                backup.markSaved()
                eventChannel.send(BackupEvent.ShowMessage(UiMessage.BackupSaved))
            }
            _state.update { it.copy(isWorking = false) }
        }
    }

    /** [bytes] is null when the picked file couldn't be read; [fileName] is for the message. */
    fun onPicked(fileName: String, bytes: ByteArray?) {
        if (bytes == null) {
            viewModelScope.launch { eventChannel.send(BackupEvent.ShowMessage(UiMessage.FileUnreadable(fileName))) }
            return
        }
        backup.read(bytes)
            .onSuccess { file -> _state.update { it.copy(pendingRestore = file) } }
            .onFailure { e -> viewModelScope.launch { eventChannel.send(BackupEvent.ShowMessage(e.toUiMessage())) } }
    }

    fun confirmRestore() {
        val file = _state.value.pendingRestore ?: return
        if (_state.value.isWorking) return
        _state.update { it.copy(isWorking = true) }
        viewModelScope.launch {
            backup.restore(file)
                .onSuccess {
                    _state.update { it.copy(isWorking = false, pendingRestore = null) }
                    eventChannel.send(BackupEvent.ShowMessage(UiMessage.BackupRestored))
                }
                .onFailure { fail(it) }
        }
    }

    fun cancelRestore() = _state.update { it.copy(pendingRestore = null) }

    private suspend fun fail(e: Throwable) {
        _state.update { it.copy(isWorking = false, pendingRestore = null) }
        eventChannel.send(BackupEvent.ShowMessage(e.toUiMessage()))
    }
}
