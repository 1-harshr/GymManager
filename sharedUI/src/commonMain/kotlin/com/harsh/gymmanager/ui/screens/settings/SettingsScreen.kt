package com.harsh.gymmanager.ui.screens.settings

import org.jetbrains.compose.resources.StringResource
import com.harsh.gymmanager.ui.components.MyGymTextField
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.harsh.gymmanager.LocalDemoMode
import com.harsh.gymmanager.DemoMode
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
import com.harsh.gymmanager.ui.platform.rememberFileSaver
import com.harsh.gymmanager.ui.platform.rememberNotificationPermission
import com.harsh.gymmanager.ui.components.MyGymBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.harsh.gymmanager.domain.model.HomeFeatures
import com.harsh.gymmanager.domain.model.MorningUpdate
import com.harsh.gymmanager.domain.model.ThemeMode
import com.harsh.gymmanager.ui.components.MyGymButton
import com.harsh.gymmanager.ui.components.MyGymCard
import com.harsh.gymmanager.ui.components.MyGymSwitch
import com.harsh.gymmanager.ui.components.ButtonKind
import com.harsh.gymmanager.ui.components.GymLogo
import com.harsh.gymmanager.ui.components.PushedHeader
import com.harsh.gymmanager.ui.components.SectionHeader
import com.harsh.gymmanager.ui.components.SegmentedControl
import com.harsh.gymmanager.ui.theme.MyGymSize
import com.harsh.gymmanager.ui.theme.MyGymSpace
import com.harsh.gymmanager.ui.theme.MyGymTheme
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
    onHomeFeaturesChange: (HomeFeatures) -> Unit,
    onOpenWhatsApp: () -> Unit,
) {
    val colors = MyGymTheme.colors
    val typography = MyGymTheme.typography
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PushedHeader(stringResource(Res.string.settings), onBack)
        Column(Modifier.screenGutter().navigationBarsPadding().padding(bottom = MyGymSpace.xxl), verticalArrangement = Arrangement.spacedBy(MyGymSpace.md)) {
            SectionHeader(stringResource(Res.string.section_gym))
            MyGymCard(onClick = onEditGym) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MyGymSpace.md)) {
                    settings.gym?.let { GymLogo(profile = it, size = MyGymSize.avatar + MyGymSpace.lg) }
                    Column(Modifier.weight(1f)) {
                        Text(settings.gym?.name.orEmpty(), style = typography.title)
                        settings.gym?.address?.let { Text(it, style = typography.caption, color = colors.inkMuted) }
                    }
                }
                MyGymButton(stringResource(Res.string.edit_gym), onEditGym, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
            }

            SectionHeader(stringResource(Res.string.section_appearance), Modifier.padding(top = MyGymSpace.md))
            MyGymCard {
                val themeLabels = mapOf(
                    ThemeMode.SYSTEM to stringResource(Res.string.theme_system),
                    ThemeMode.LIGHT to stringResource(Res.string.theme_light),
                    ThemeMode.DARK to stringResource(Res.string.theme_dark),
                )
                SegmentedControl(ThemeMode.entries, settings.theme, { it?.let(onThemeChange) }, { themeLabels.getValue(it) })
                Text(stringResource(Res.string.theme_hint), style = typography.caption, color = colors.inkMuted)
            }

            SectionHeader(stringResource(Res.string.section_language), Modifier.padding(top = MyGymSpace.md))
            MyGymCard {
                val languageLabels = mapOf(
                    AppLanguage.SYSTEM to stringResource(Res.string.language_system),
                    AppLanguage.ENGLISH to stringResource(Res.string.language_english),
                    AppLanguage.HINDI to stringResource(Res.string.language_hindi),
                )
                SegmentedControl(AppLanguage.entries, settings.language, { it?.let(onLanguageChange) }, { languageLabels.getValue(it) })
            }

            SectionHeader(stringResource(Res.string.section_home_screen), Modifier.padding(top = MyGymSpace.md))
            MyGymCard {
                val features = settings.homeFeatures
                SwitchRow(
                    stringResource(Res.string.ai_insights),
                    stringResource(Res.string.ai_insights_body),
                    features.aiInsights,
                ) { onHomeFeaturesChange(features.copy(aiInsights = it)) }
                SwitchRow(
                    stringResource(Res.string.collections_chart),
                    stringResource(Res.string.collections_chart_body),
                    features.collectionsChart,
                ) { onHomeFeaturesChange(features.copy(collectionsChart = it)) }
            }

            SectionHeader(stringResource(Res.string.section_whatsapp), Modifier.padding(top = MyGymSpace.md))
            WhatsAppSettingsCard(onOpenWhatsApp)

            SectionHeader(stringResource(Res.string.section_notifications), Modifier.padding(top = MyGymSpace.md))
            MorningUpdateCard(settings.morningUpdate, onMorningUpdateChange)

            SectionHeader(stringResource(Res.string.section_data), Modifier.padding(top = MyGymSpace.md))
            BackupCard(settings)

            LocalDemoMode.current?.let { demoMode ->
                SectionHeader(stringResource(Res.string.section_demo), Modifier.padding(top = MyGymSpace.md))
                DemoCard(demoMode)
            }

            SectionHeader(stringResource(Res.string.section_about), Modifier.padding(top = MyGymSpace.md))
            MyGymCard {
                Text(stringResource(Res.string.version, "1.0"), style = typography.body)
            }
            Text(
                stringResource(Res.string.credits),
                style = typography.caption,
                color = colors.inkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = MyGymSpace.sm),
            )
        }
    }
}

/** A setting that's on or off: its name, what it does, and the switch. */
@Composable
internal fun SwitchRow(title: String, body: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MyGymSpace.md)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MyGymSpace.xs)) {
            Text(title, style = MyGymTheme.typography.bodyStrong)
            Text(body, style = MyGymTheme.typography.body, color = MyGymTheme.colors.inkMuted)
        }
        MyGymSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** The daily notification: on or off, and when. Turning it on asks for notification permission first. */
@Composable
private fun MorningUpdateCard(update: MorningUpdate, onChange: (MorningUpdate) -> Unit) {
    val colors = MyGymTheme.colors
    val typography = MyGymTheme.typography
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var pickingTime by remember { mutableStateOf(false) }
    val denied = stringResource(Res.string.morning_update_denied)
    val askPermission = rememberNotificationPermission { granted ->
        if (granted) onChange(update.copy(enabled = true)) else scope.launch { snackbar.showSnackbar(denied) }
    }

    MyGymCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MyGymSpace.md)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MyGymSpace.xs)) {
                Text(stringResource(Res.string.morning_update), style = typography.bodyStrong)
                Text(stringResource(Res.string.morning_update_body), style = typography.body, color = colors.inkMuted)
            }
            MyGymSwitch(
                checked = update.enabled,
                onCheckedChange = { on -> if (on) askPermission() else onChange(update.copy(enabled = false)) },
            )
        }
        if (update.enabled) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.morning_update_time), Modifier.weight(1f), style = typography.body, color = colors.inkMuted)
                MyGymButton(timeLabel(update.minuteOfDay), { pickingTime = true }, kind = ButtonKind.Secondary)
            }
        }
    }

    if (pickingTime) {
        TimeSheet(
            stringResource(Res.string.morning_update_set_time),
            update.minuteOfDay,
            onPick = { minuteOfDay ->
                onChange(update.copy(minuteOfDay = minuteOfDay))
                pickingTime = false
            },
            onDismiss = { pickingTime = false },
        )
    }
}

/** Picks a time of day; [minuteOfDay] is where it starts, e.g. 480 for 8:00 AM. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeSheet(title: String, minuteOfDay: Int, onPick: (minuteOfDay: Int) -> Unit, onDismiss: () -> Unit) {
    val colors = MyGymTheme.colors
    val state = rememberTimePickerState(initialHour = minuteOfDay / 60, initialMinute = minuteOfDay % 60, is24Hour = false)
    MyGymBottomSheet(onDismiss) {
        Text(title, style = MyGymTheme.typography.title)
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
        MyGymButton(stringResource(Res.string.done), { onPick(state.hour * 60 + state.minute) }, Modifier.fillMaxWidth())
        MyGymButton(stringResource(Res.string.cancel), onDismiss, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
    }
}

/** "8:00 AM" for 480. */
@Composable
internal fun timeLabel(minuteOfDay: Int): String {
    val hour = minuteOfDay / 60
    val hour12 = if (hour % 12 == 0) 12 else hour % 12
    val minutes = (minuteOfDay % 60).toString().padStart(2, '0')
    return stringResource(if (hour < 12) Res.string.time_am else Res.string.time_pm, hour12, minutes)
}

/**
 * Sample data for showing the app, opened in its own space after the demo password. During a demo
 * the card offers the way back instead.
 */
@Composable
private fun DemoCard(demoMode: DemoMode) {
    val colors = MyGymTheme.colors
    val typography = MyGymTheme.typography
    val inDemo = demoMode.active != null
    var asking by remember { mutableStateOf(false) }
    MyGymCard {
        Column(verticalArrangement = Arrangement.spacedBy(MyGymSpace.xs)) {
            Text(stringResource(Res.string.demo_title), style = typography.bodyStrong)
            Text(stringResource(if (inDemo) Res.string.demo_active_body else Res.string.demo_body), style = typography.body, color = colors.inkMuted)
        }
        if (inDemo) {
            MyGymButton(stringResource(Res.string.demo_exit), demoMode::exit, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
        } else {
            MyGymButton(stringResource(Res.string.demo_start), { asking = true }, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
        }
    }
    if (asking) DemoPasswordSheet(demoMode, onDismiss = { asking = false })
}

/** Asks for the demo password, then builds the demo; the app switches to it when ready. */
@Composable
private fun DemoPasswordSheet(demoMode: DemoMode, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<StringResource?>(null) }
    var starting by remember { mutableStateOf(false) }
    fun start() {
        if (starting) return
        if (!demoMode.isPassword(password)) {
            error = Res.string.demo_wrong_password
            return
        }
        starting = true
        scope.launch {
            demoMode.start().onFailure {
                starting = false
                error = Res.string.demo_failed
            }
        }
    }
    MyGymBottomSheet(onDismiss = { if (!starting) onDismiss() }) {
        Column(verticalArrangement = Arrangement.spacedBy(MyGymSpace.xs)) {
            Text(stringResource(Res.string.demo_start), style = MyGymTheme.typography.title)
            Text(stringResource(Res.string.demo_sheet_body), style = MyGymTheme.typography.body, color = MyGymTheme.colors.inkMuted)
        }
        MyGymTextField(
            password,
            { password = it.filter(Char::isDigit).take(8); error = null },
            stringResource(Res.string.demo_password),
            keyboardType = KeyboardType.NumberPassword,
            visualTransformation = PasswordVisualTransformation(),
            error = error?.let { stringResource(it) },
            imeAction = ImeAction.Done,
            onDone = ::start,
        )
        MyGymButton(
            stringResource(if (starting) Res.string.demo_starting else Res.string.demo_start),
            ::start,
            Modifier.fillMaxWidth(),
            enabled = !starting && password.isNotEmpty(),
        )
    }
}

/** Saving a backup file and restoring one; restoring asks first, since it replaces everything. */
@Composable
private fun BackupCard(settings: AppSettings) {
    val colors = MyGymTheme.colors
    val typography = MyGymTheme.typography
    val deps = LocalAppDependencies.current
    val snackbar = LocalSnackbar.current
    val vm = viewModel { BackupViewModel(deps.backup) }
    val state by vm.state.collectAsStateWithLifecycle()
    val save = rememberFileSaver("application/json", vm::onSaved)
    val pick = rememberBackupPicker(vm::onPicked)

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is BackupEvent.SaveFile -> save(event.fileName, event.bytes)
                is BackupEvent.ShowMessage -> snackbar.showSnackbar(event.message.resolve())
            }
        }
    }

    // Sample data must never become a backup, or be restored over.
    if (LocalDemoMode.current?.active != null) {
        MyGymCard {
            Text(stringResource(Res.string.data_title), style = typography.bodyStrong)
            Text(stringResource(Res.string.backups_off_in_demo), style = typography.body, color = colors.inkMuted)
        }
        return
    }
    MyGymCard {
        Column(verticalArrangement = Arrangement.spacedBy(MyGymSpace.xs)) {
            Text(stringResource(Res.string.data_title), style = typography.bodyStrong)
            Text(stringResource(Res.string.data_body), style = typography.body, color = colors.inkMuted)
        }
        val last = settings.lastBackupAt?.toLocalDateTime(TimeZone.currentSystemDefault())?.date
        Text(
            if (last != null) stringResource(Res.string.last_backup, last.formatFull()) else stringResource(Res.string.never_backed_up),
            style = typography.caption,
            color = if (last != null) colors.statusActive else colors.statusExpiring,
        )
        MyGymButton(
            if (state.isWorking && state.pendingRestore == null) stringResource(Res.string.backing_up) else stringResource(Res.string.back_up_now),
            vm::backUp,
            Modifier.fillMaxWidth(),
            enabled = !state.isWorking,
        )
        MyGymButton(stringResource(Res.string.restore_backup), pick, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary, enabled = !state.isWorking)
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
