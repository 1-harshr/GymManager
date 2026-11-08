package com.harsh.gymmanager.ui.screens.settings

import androidx.compose.ui.platform.LocalUriHandler
import com.harsh.gymmanager.ui.WEB_APP_URL
import org.jetbrains.compose.resources.StringResource
import com.harsh.gymmanager.ui.components.MyGymTextField
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.harsh.gymmanager.LocalDemoMode
import com.harsh.gymmanager.DemoMode
import com.harsh.gymmanager.domain.repository.AuthState
import com.harsh.gymmanager.ui.LocalGymRepository
import com.harsh.gymmanager.ui.isOwnerOrNoAccount
import com.harsh.gymmanager.ui.format.formatSignInPhone
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
import androidx.compose.runtime.getValue
import com.harsh.gymmanager.ui.LocalSnackbar
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
import com.harsh.gymmanager.ui.components.GhostButton
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
    /** Who's signed in; null in demo mode, where there's no account. */
    account: AuthState.SignedIn? = null,
    onSignOut: () -> Unit = {},
    onOpenStaff: () -> Unit = {},
    onDeleteAccount: () -> Unit = {},
) {
    val colors = MyGymTheme.colors
    val typography = MyGymTheme.typography
    // Staff get their own preferences; the gym's setup and messaging are the owner's.
    val isOwner = isOwnerOrNoAccount
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PushedHeader(stringResource(Res.string.settings), onBack)
        Column(Modifier.screenGutter().navigationBarsPadding().padding(bottom = MyGymSpace.xxl), verticalArrangement = Arrangement.spacedBy(MyGymSpace.md)) {
            SectionHeader(stringResource(Res.string.section_gym))
            MyGymCard(onClick = if (isOwner) onEditGym else null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MyGymSpace.md)) {
                    settings.gym?.let { GymLogo(profile = it, size = MyGymSize.avatar + MyGymSpace.lg) }
                    Column(Modifier.weight(1f)) {
                        Text(settings.gym?.name.orEmpty(), style = typography.title)
                        settings.gym?.address?.let { Text(it, style = typography.caption, color = colors.inkMuted) }
                    }
                }
                if (isOwner) MyGymButton(stringResource(Res.string.edit_gym), onEditGym, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
            }

            // Only with a gym online; the phone-only app and the demo have no staff.
            if (LocalGymRepository.current != null) {
                SectionHeader(stringResource(Res.string.section_staff), Modifier.padding(top = MyGymSpace.md))
                MyGymCard(onClick = onOpenStaff) {
                    Text(
                        stringResource(if (isOwnerOrNoAccount) Res.string.staff_card_owner else Res.string.staff_card_staff),
                        style = typography.body,
                        color = colors.inkMuted,
                    )
                    MyGymButton(stringResource(Res.string.staff_manage), onOpenStaff, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
                }
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

            if (isOwner) {
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
            }

            // The morning update includes the month's collections, so it's the owner's too.
            if (isOwner) {
                SectionHeader(stringResource(Res.string.section_notifications), Modifier.padding(top = MyGymSpace.md))
                MorningUpdateCard(settings.morningUpdate, onMorningUpdateChange)
            }

            LocalDemoMode.current?.let { demoMode ->
                SectionHeader(stringResource(Res.string.section_demo), Modifier.padding(top = MyGymSpace.md))
                DemoCard(demoMode)
            }

            if (account != null) {
                SectionHeader(stringResource(Res.string.section_account), Modifier.padding(top = MyGymSpace.md))
                MyGymCard {
                    Text(
                        stringResource(Res.string.account_signed_in_as, formatSignInPhone(account.phone ?: account.email.orEmpty())),
                        style = typography.body,
                    )
                    MyGymButton(stringResource(Res.string.sign_out), onSignOut, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
                    GhostButton(stringResource(Res.string.delete_account), onDeleteAccount, Modifier.align(Alignment.CenterHorizontally), color = colors.statusDue)
                }
            }

            SectionHeader(stringResource(Res.string.section_about), Modifier.padding(top = MyGymSpace.md))
            MyGymCard {
                Text(stringResource(Res.string.version, "1.0"), style = typography.body)
            }
            val uriHandler = LocalUriHandler.current
            MyGymCard(onClick = { uriHandler.openUri(WEB_APP_URL) }) {
                Column(verticalArrangement = Arrangement.spacedBy(MyGymSpace.xs)) {
                    Text(stringResource(Res.string.web_app_title), style = typography.bodyStrong)
                    Text(stringResource(Res.string.web_app_body), style = typography.body, color = colors.inkMuted)
                }
                MyGymButton(stringResource(Res.string.open_web_app), { uriHandler.openUri(WEB_APP_URL) }, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
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

/** Asks for the demo password, then builds the demo; the app switches to it when ready. Also on the sign-in screen. */
@Composable
internal fun DemoPasswordSheet(demoMode: DemoMode, onDismiss: () -> Unit) {
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
