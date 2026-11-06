package com.harsh.gymmanager.ui.screens.memberedit

import androidx.compose.ui.text.input.ImeAction
import com.harsh.gymmanager.ui.components.rememberDiscardGuard
import kotlinx.datetime.minus
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DatePeriod
import gymmanager.sharedui.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harsh.gymmanager.LocalAppDependencies
import com.harsh.gymmanager.domain.model.Gender
import com.harsh.gymmanager.presentation.memberedit.MemberEditEvent
import com.harsh.gymmanager.presentation.memberedit.MemberEditViewModel
import com.harsh.gymmanager.ui.components.MyGymButton
import com.harsh.gymmanager.ui.components.MyGymTextField
import com.harsh.gymmanager.ui.components.Avatar
import com.harsh.gymmanager.ui.components.ButtonKind
import com.harsh.gymmanager.ui.components.DateField
import com.harsh.gymmanager.ui.components.PushedHeader
import com.harsh.gymmanager.ui.components.SegmentedControl
import com.harsh.gymmanager.ui.format.asString
import com.harsh.gymmanager.ui.format.label
import com.harsh.gymmanager.ui.format.rememberToday
import com.harsh.gymmanager.ui.icons.MyGymIcons
import com.harsh.gymmanager.ui.photo.CameraCapture
import com.harsh.gymmanager.ui.theme.MyGymSpace
import com.harsh.gymmanager.ui.theme.MyGymTheme
import com.harsh.gymmanager.ui.theme.screenGutter

/** Adds a member when [memberId] is null; [onSaved] gets the member's id. */
@Composable
fun MemberEditScreen(memberId: Long?, onBack: () -> Unit, onSaved: (Long, Boolean) -> Unit) {
    val deps = LocalAppDependencies.current
    val vm = viewModel(key = "member-edit-$memberId") { MemberEditViewModel(memberId, deps.memberUseCases) }
    val state by vm.state.collectAsStateWithLifecycle()
    val back = rememberDiscardGuard(state.hasChanges, onBack)
    var showCamera by rememberSaveable { mutableStateOf(false) }
    val today = rememberToday()

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is MemberEditEvent.Saved -> onSaved(event.memberId, !state.isEditing)
                MemberEditEvent.NotFound -> onBack()
            }
        }
    }

    val form = state.form
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().imePadding()) {
            PushedHeader(if (state.isEditing) stringResource(Res.string.edit_member) else stringResource(Res.string.new_member), back)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).screenGutter().padding(bottom = MyGymSpace.xl),
                verticalArrangement = Arrangement.spacedBy(MyGymSpace.lg),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MyGymSpace.lg)) {
                    Avatar(form.name.ifBlank { "?" }, form.photoPath, size = 88.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MyGymSpace.sm)) {
                        MyGymButton(
                            if (form.photoPath == null) stringResource(Res.string.take_photo) else stringResource(Res.string.retake_photo),
                            { showCamera = true },
                            Modifier.fillMaxWidth(),
                            kind = ButtonKind.Secondary,
                            icon = MyGymIcons.Camera,
                        )
                        if (form.photoPath != null) {
                            com.harsh.gymmanager.ui.components.GhostButton(stringResource(Res.string.remove_photo), { vm.onPhotoChange(null) })
                        }
                    }
                }
                MyGymTextField(
                    form.name, vm::onNameChange, stringResource(Res.string.name),
                    error = state.nameError?.asString(), capitalization = KeyboardCapitalization.Words, placeholder = stringResource(Res.string.full_name_hint),
                )
                MyGymTextField(
                    form.phone, vm::onPhoneChange, stringResource(Res.string.phone),
                    optional = true, keyboardType = KeyboardType.Phone, placeholder = stringResource(Res.string.phone_hint),
                    // The next few answers are taps, not typing.
                    imeAction = ImeAction.Done,
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(MyGymSpace.xs)) {
                        Text(stringResource(Res.string.gender), style = MyGymTheme.typography.caption.copy(fontWeight = MyGymTheme.typography.bodyStrong.fontWeight))
                        Text(stringResource(Res.string.optional), style = MyGymTheme.typography.caption, color = MyGymTheme.colors.inkMuted)
                    }
                    SegmentedControl(Gender.entries, form.gender, vm::onGenderChange, { it.label() }, allowNone = true)
                }
                // Birthdays are in the past; the picker opens on a typical member's birth year.
                DateField(
                    form.dateOfBirth, vm::onDateOfBirthChange, stringResource(Res.string.date_of_birth), optional = true,
                    latest = today.minus(1, DateTimeUnit.DAY), openAt = today.minus(DatePeriod(years = TYPICAL_AGE_YEARS)),
                )
                DateField(
                    form.joinedOn ?: today,
                    { it?.let(vm::onJoinedOnChange) },
                    stringResource(Res.string.joined_on_label),
                    latest = today,
                )
                MyGymTextField(
                    form.notes, vm::onNotesChange, stringResource(Res.string.notes),
                    optional = true, singleLine = false, capitalization = KeyboardCapitalization.Sentences,
                    placeholder = stringResource(Res.string.notes_hint),
                )
                state.errorMessage?.let { Text(it.asString(), style = MyGymTheme.typography.caption, color = MyGymTheme.colors.statusDue) }
            }
            MyGymButton(
                if (state.isSaving) stringResource(Res.string.saving) else if (state.isEditing) stringResource(Res.string.save_changes) else stringResource(Res.string.add_member),
                vm::save,
                Modifier.fillMaxWidth().screenGutter().navigationBarsPadding().padding(vertical = MyGymSpace.md),
                enabled = !state.isSaving && !state.isLoading,
            )
        }
        if (showCamera) {
            CameraCapture(
                onPhotoTaken = { vm.onPhotoChange(it); showCamera = false },
                onClose = { showCamera = false },
            )
        }
    }
}

/** Where the birthday picker opens when no date is set. */
private const val TYPICAL_AGE_YEARS = 20
