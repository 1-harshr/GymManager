package com.harsh.gymmanager.ui.screens.settings

import gymmanager.sharedui.generated.resources.credits
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harsh.gymmanager.LocalAppDependencies
import com.harsh.gymmanager.presentation.backup.BackupEvent
import com.harsh.gymmanager.presentation.backup.BackupViewModel
import com.harsh.gymmanager.ui.LocalSnackbar
import com.harsh.gymmanager.ui.components.ConfirmSheet
import com.harsh.gymmanager.ui.format.formatFull
import com.harsh.gymmanager.ui.format.resolve
import com.harsh.gymmanager.ui.platform.rememberBackupPicker
import com.harsh.gymmanager.ui.platform.rememberBackupSaver
import com.harsh.gymmanager.ui.platform.rememberNotificationPermission
import com.harsh.gymmanager.ui.components.AkhadaBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import org.jetbrains.compose.resources.pluralStringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.harsh.gymmanager.domain.model.AppLanguage
import com.harsh.gymmanager.domain.model.AppSettings
import com.harsh.gymmanager.domain.model.MorningUpdate
import com.harsh.gymmanager.domain.model.ThemeMode
import com.harsh.gymmanager.ui.components.AkhadaButton
import com.harsh.gymmanager.ui.components.AkhadaCard
import com.harsh.gymmanager.ui.components.ButtonKind
import com.harsh.gymmanager.ui.components.GymLogo
import com.harsh.gymmanager.ui.components.PushedHeader
import com.harsh.gymmanager.ui.components.SectionHeader
import com.harsh.gymmanager.ui.components.SegmentedControl
import com.harsh.gymmanager.ui.theme.AkhadaSize
import com.harsh.gymmanager.ui.theme.AkhadaSpace
import com.harsh.gymmanager.ui.theme.AkhadaTheme
import com.harsh.gymmanager.ui.theme.screenGutter
import gymmanager.sharedui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onEditGym: () -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onMorningUpdateChange: (MorningUpdate) -> Unit,
) {
    val colors = AkhadaTheme.colors
    val typography = AkhadaTheme.typography
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PushedHeader(stringResource(Res.string.settings), onBack)
        Column(Modifier.screenGutter().navigationBarsPadding().padding(bottom = AkhadaSpace.xxl), verticalArrangement = Arrangement.spacedBy(AkhadaSpace.md)) {
            SectionHeader(stringResource(Res.string.section_gym))
            AkhadaCard(onClick = onEditGym) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AkhadaSpace.md)) {
                    settings.gym?.let { GymLogo(profile = it, size = AkhadaSize.avatar + AkhadaSpace.lg) }
                    Column(Modifier.weight(1f)) {
                        Text(settings.gym?.name.orEmpty(), style = typography.title)
                        settings.gym?.address?.let { Text(it, style = typography.caption, color = colors.inkMuted) }
                    }
                }
                AkhadaButton(stringResource(Res.string.edit_gym), onEditGym, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
            }

            SectionHeader(stringResource(Res.string.section_appearance), Modifier.padding(top = AkhadaSpace.md))
            AkhadaCard {
                val themeLabels = mapOf(
                    ThemeMode.SYSTEM to stringResource(Res.string.theme_system),
                    ThemeMode.LIGHT to stringResource(Res.string.theme_light),
                    ThemeMode.DARK to stringResource(Res.string.theme_dark),
                )
                SegmentedControl(ThemeMode.entries, settings.theme, { it?.let(onThemeChange) }, { themeLabels.getValue(it) })
                Text(stringResource(Res.string.theme_hint), style = typography.caption, color = colors.inkMuted)
            }

            SectionHeader(stringResource(Res.string.section_language), Modifier.padding(top = AkhadaSpace.md))
            AkhadaCard {
                val languageLabels = mapOf(
                    AppLanguage.SYSTEM to stringResource(Res.string.language_system),
                    AppLanguage.ENGLISH to stringResource(Res.string.language_english),
                    AppLanguage.HINDI to stringResource(Res.string.language_hindi),
                )
                SegmentedControl(AppLanguage.entries, settings.language, { it?.let(onLanguageChange) }, { languageLabels.getValue(it) })
            }

            SectionHeader(stringResource(Res.string.section_notifications), Modifier.padding(top = AkhadaSpace.md))
            MorningUpdateCard(settings.morningUpdate, onMorningUpdateChange)

            SectionHeader(stringResource(Res.string.section_data), Modifier.padding(top = AkhadaSpace.md))
            BackupCard(settings)

            SectionHeader(stringResource(Res.string.section_about), Modifier.padding(top = AkhadaSpace.md))
            AkhadaCard {
                Text(stringResource(Res.string.version, "1.0"), style = typography.body)
            }
            Text(
                stringResource(Res.string.credits),
                style = typography.caption,
                color = colors.inkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = AkhadaSpace.sm),
            )
        }
    }
}

/** The daily notification: on or off, and when. Turning it on asks for notification permission first. */
@Composable
private fun MorningUpdateCard(update: MorningUpdate, onChange: (MorningUpdate) -> Unit) {
    val colors = AkhadaTheme.colors
    val typography = AkhadaTheme.typography
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var pickingTime by remember { mutableStateOf(false) }
    val denied = stringResource(Res.string.morning_update_denied)
    val askPermission = rememberNotificationPermission { granted ->
        if (granted) onChange(update.copy(enabled = true)) else scope.launch { snackbar.showSnackbar(denied) }
    }

    AkhadaCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AkhadaSpace.md)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AkhadaSpace.xs)) {
                Text(stringResource(Res.string.morning_update), style = typography.bodyStrong)
                Text(stringResource(Res.string.morning_update_body), style = typography.body, color = colors.inkMuted)
            }
            Switch(
                checked = update.enabled,
                onCheckedChange = { on -> if (on) askPermission() else onChange(update.copy(enabled = false)) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.onInk, checkedTrackColor = colors.ink,
                    uncheckedThumbColor = colors.inkMuted, uncheckedTrackColor = colors.surfaceSunken, uncheckedBorderColor = colors.surfaceSunken,
                ),
            )
        }
        if (update.enabled) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.morning_update_time), Modifier.weight(1f), style = typography.body, color = colors.inkMuted)
                AkhadaButton(update.timeLabel(), { pickingTime = true }, kind = ButtonKind.Secondary)
            }
        }
    }

    if (pickingTime) {
        MorningTimeSheet(
            update,
            onPick = { minuteOfDay ->
                onChange(update.copy(minuteOfDay = minuteOfDay))
                pickingTime = false
            },
            onDismiss = { pickingTime = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MorningTimeSheet(update: MorningUpdate, onPick: (minuteOfDay: Int) -> Unit, onDismiss: () -> Unit) {
    val colors = AkhadaTheme.colors
    val state = rememberTimePickerState(initialHour = update.hour, initialMinute = update.minute, is24Hour = false)
    AkhadaBottomSheet(onDismiss) {
        Text(stringResource(Res.string.morning_update_set_time), style = AkhadaTheme.typography.title)
        TimePicker(
            state,
            Modifier.align(Alignment.CenterHorizontally),
            colors = TimePickerDefaults.colors(
                clockDialColor = colors.surfaceSunken,
                selectorColor = colors.ink,
                clockDialSelectedContentColor = colors.onInk,
                clockDialUnselectedContentColor = colors.ink,
                periodSelectorBorderColor = colors.line,
                periodSelectorSelectedContainerColor = colors.ink,
                periodSelectorSelectedContentColor = colors.onInk,
                periodSelectorUnselectedContentColor = colors.inkMuted,
                timeSelectorSelectedContainerColor = colors.ink,
                timeSelectorSelectedContentColor = colors.onInk,
                timeSelectorUnselectedContainerColor = colors.surfaceSunken,
                timeSelectorUnselectedContentColor = colors.ink,
            ),
        )
        AkhadaButton(stringResource(Res.string.done), { onPick(state.hour * 60 + state.minute) }, Modifier.fillMaxWidth())
        AkhadaButton(stringResource(Res.string.cancel), onDismiss, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
    }
}

/** "8:00 AM". */
@Composable
private fun MorningUpdate.timeLabel(): String {
    val hour12 = if (hour % 12 == 0) 12 else hour % 12
    val minutes = minute.toString().padStart(2, '0')
    return stringResource(if (hour < 12) Res.string.time_am else Res.string.time_pm, hour12, minutes)
}

/** Saving a backup file and restoring one; restoring asks first, since it replaces everything. */
@Composable
private fun BackupCard(settings: AppSettings) {
    val colors = AkhadaTheme.colors
    val typography = AkhadaTheme.typography
    val deps = LocalAppDependencies.current
    val snackbar = LocalSnackbar.current
    val vm = viewModel { BackupViewModel(deps.backup) }
    val state by vm.state.collectAsStateWithLifecycle()
    val save = rememberBackupSaver(vm::onSaved)
    val pick = rememberBackupPicker(vm::onPicked)

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is BackupEvent.SaveFile -> save(event.fileName, event.bytes)
                is BackupEvent.ShowMessage -> snackbar.showSnackbar(event.message.resolve())
            }
        }
    }

    AkhadaCard {
        Column(verticalArrangement = Arrangement.spacedBy(AkhadaSpace.xs)) {
            Text(stringResource(Res.string.data_title), style = typography.bodyStrong)
            Text(stringResource(Res.string.data_body), style = typography.body, color = colors.inkMuted)
        }
        val last = settings.lastBackupAt?.toLocalDateTime(TimeZone.currentSystemDefault())?.date
        Text(
            if (last != null) stringResource(Res.string.last_backup, last.formatFull()) else stringResource(Res.string.never_backed_up),
            style = typography.caption,
            color = if (last != null) colors.statusActive else colors.statusExpiring,
        )
        AkhadaButton(
            if (state.isWorking && state.pendingRestore == null) stringResource(Res.string.backing_up) else stringResource(Res.string.back_up_now),
            vm::backUp,
            Modifier.fillMaxWidth(),
            enabled = !state.isWorking,
        )
        AkhadaButton(stringResource(Res.string.restore_backup), pick, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary, enabled = !state.isWorking)
    }

    state.pendingRestore?.let { backup ->
        val savedOn = Instant.fromEpochMilliseconds(backup.createdAt).toLocalDateTime(TimeZone.currentSystemDefault()).date
        ConfirmSheet(
            title = stringResource(Res.string.restore_title),
            body = stringResource(
                Res.string.restore_body,
                savedOn.formatFull(),
                backup.gym?.name.orEmpty(),
                pluralStringResource(Res.plurals.members_count, backup.members.size, backup.members.size),
                pluralStringResource(Res.plurals.plans_count, backup.plans.size, backup.plans.size),
                pluralStringResource(Res.plurals.payments_count, backup.payments.size, backup.payments.size),
            ),
            confirmLabel = if (state.isWorking) stringResource(Res.string.restoring) else stringResource(Res.string.restore_confirm),
            onConfirm = vm::confirmRestore,
            onDismiss = { if (!state.isWorking) vm.cancelRestore() },
        )
    }
}
