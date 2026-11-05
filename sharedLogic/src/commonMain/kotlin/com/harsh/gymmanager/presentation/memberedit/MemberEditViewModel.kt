package com.harsh.gymmanager.presentation.memberedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsh.gymmanager.domain.ValidationError
import com.harsh.gymmanager.domain.model.Gender
import com.harsh.gymmanager.domain.model.Member
import com.harsh.gymmanager.domain.usecase.MemberUseCases
import com.harsh.gymmanager.presentation.UiMessage
import com.harsh.gymmanager.presentation.toUiMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

data class MemberForm(
    val name: String = "",
    val phone: String = "",
    val gender: Gender? = null,
    val dateOfBirth: LocalDate? = null,
    val photoPath: String? = null,
    val notes: String = "",
    /** Null means today for a new member. */
    val joinedOn: LocalDate? = null,
)

data class MemberEditUiState(
    val isEditing: Boolean,
    val isLoading: Boolean = isEditing,
    val form: MemberForm = MemberForm(),
    val nameError: UiMessage? = null,
    val errorMessage: UiMessage? = null,
    val isSaving: Boolean = false,
    /** True once the owner has edited anything, so leaving asks before throwing it away. */
    val hasChanges: Boolean = false,
)

sealed interface MemberEditEvent {
    data class Saved(val memberId: Long) : MemberEditEvent
    data object NotFound : MemberEditEvent
}

/** Adds a member when [memberId] is null, otherwise edits that member. */
class MemberEditViewModel(
    private val memberId: Long?,
    private val memberUseCases: MemberUseCases,
) : ViewModel() {
    private val _state = MutableStateFlow(MemberEditUiState(isEditing = memberId != null))
    val state: StateFlow<MemberEditUiState> = _state.asStateFlow()

    private val eventChannel = Channel<MemberEditEvent>(Channel.BUFFERED)
    val events: Flow<MemberEditEvent> = eventChannel.receiveAsFlow()

    private var existing: Member? = null

    init {
        if (memberId != null) {
            viewModelScope.launch {
                val member = memberUseCases.observeMember(memberId).first()
                if (member == null) {
                    eventChannel.send(MemberEditEvent.NotFound)
                    return@launch
                }
                existing = member
                _state.update {
                    it.copy(
                        isLoading = false,
                        form = MemberForm(
                            name = member.name,
                            phone = member.phone.orEmpty(),
                            gender = member.gender,
                            dateOfBirth = member.dateOfBirth,
                            photoPath = member.photoPath,
                            notes = member.notes.orEmpty(),
                            joinedOn = member.joinedOn,
                        ),
                    )
                }
            }
        }
    }

    fun onNameChange(value: String) = updateForm(clearNameError = true) { copy(name = value) }
    fun onPhoneChange(value: String) = updateForm { copy(phone = value) }
    fun onGenderChange(value: Gender?) = updateForm { copy(gender = value) }
    fun onDateOfBirthChange(value: LocalDate?) = updateForm { copy(dateOfBirth = value) }
    fun onPhotoChange(path: String?) = updateForm { copy(photoPath = path) }
    fun onNotesChange(value: String) = updateForm { copy(notes = value) }
    fun onJoinedOnChange(value: LocalDate) = updateForm { copy(joinedOn = value) }

    fun save() {
        val current = _state.value
        if (current.isSaving || current.isLoading) return
        val form = current.form
        if (form.name.isBlank()) {
            _state.update { it.copy(nameError = UiMessage.Invalid(ValidationError.NAME_REQUIRED)) }
            return
        }
        _state.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            val before = existing
            val result = if (before == null) {
                memberUseCases.addMember(
                    name = form.name,
                    phone = form.phone,
                    gender = form.gender,
                    dateOfBirth = form.dateOfBirth,
                    photoPath = form.photoPath,
                    notes = form.notes,
                    joinedOn = form.joinedOn,
                )
            } else {
                memberUseCases.updateMember(
                    before.copy(
                        name = form.name,
                        phone = form.phone,
                        gender = form.gender,
                        dateOfBirth = form.dateOfBirth,
                        photoPath = form.photoPath,
                        notes = form.notes,
                        joinedOn = form.joinedOn ?: before.joinedOn,
                    )
                ).map { before.id }
            }
            result
                .onSuccess { eventChannel.send(MemberEditEvent.Saved(it)) }
                .onFailure { e -> _state.update { it.copy(isSaving = false, errorMessage = e.toUiMessage()) } }
        }
    }

    private fun updateForm(clearNameError: Boolean = false, change: MemberForm.() -> MemberForm) =
        _state.update {
            it.copy(
                form = it.form.change(),
                nameError = if (clearNameError) null else it.nameError,
                errorMessage = null,
                hasChanges = true,
            )
        }
}
