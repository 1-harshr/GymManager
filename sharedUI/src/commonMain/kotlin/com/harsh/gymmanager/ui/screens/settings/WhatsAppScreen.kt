package com.harsh.gymmanager.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harsh.gymmanager.LocalAppDependencies
import com.harsh.gymmanager.domain.messaging.MessageKind
import com.harsh.gymmanager.domain.whatsapp.AutoSend
import com.harsh.gymmanager.domain.whatsapp.OutboxMessage
import com.harsh.gymmanager.domain.whatsapp.OutboxStatus
import com.harsh.gymmanager.domain.whatsapp.WhatsAppSettings
import com.harsh.gymmanager.presentation.settings.WhatsAppEvent
import com.harsh.gymmanager.presentation.settings.WhatsAppViewModel
import com.harsh.gymmanager.templates.gymTemplates
import com.harsh.gymmanager.ui.LocalGym
import com.harsh.gymmanager.ui.LocalSnackbar
import com.harsh.gymmanager.ui.components.AkhadaButton
import com.harsh.gymmanager.ui.components.AkhadaCard
import com.harsh.gymmanager.ui.components.AkhadaTextField
import com.harsh.gymmanager.ui.components.ButtonKind
import com.harsh.gymmanager.ui.components.ConfirmSheet
import com.harsh.gymmanager.ui.components.PushedHeader
import com.harsh.gymmanager.ui.components.SectionHeader
import com.harsh.gymmanager.ui.components.StatusDot
import com.harsh.gymmanager.ui.icons.AkhadaIcons
import com.harsh.gymmanager.ui.messaging.label
import com.harsh.gymmanager.ui.platform.sendsRemindersInBackground
import com.harsh.gymmanager.ui.theme.AkhadaRadius
import com.harsh.gymmanager.ui.theme.AkhadaSpace
import com.harsh.gymmanager.ui.theme.AkhadaTheme
import com.harsh.gymmanager.ui.theme.screenGutter
import gymmanager.sharedui.generated.resources.*
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * Settings → WhatsApp: hook up the owner's Make.com scenario, choose what goes out on its own,
 * and see how recent messages got on. Make does the actual sending with the gym's WhatsApp
 * Business account; the app is the queue and the clock.
 */
@Composable
fun WhatsAppScreen(onBack: () -> Unit) {
    val deps = LocalAppDependencies.current
    val gymName = LocalGym.current.name
    val vm = viewModel { WhatsAppViewModel(deps.whatsApp) { gymName } }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    var editing by remember { mutableStateOf(false) }
    var confirmingStop by remember { mutableStateOf(false) }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                WhatsAppEvent.Saved -> {
                    editing = false
                    snackbar.showSnackbar(getString(Res.string.wa_saved_toast))
                }
                WhatsAppEvent.InvalidWebhook -> snackbar.showSnackbar(getString(Res.string.wa_webhook_invalid))
                WhatsAppEvent.InvalidNumber -> snackbar.showSnackbar(getString(Res.string.wa_invalid_number))
                WhatsAppEvent.Removed -> snackbar.showSnackbar(getString(Res.string.wa_stopped_toast))
                WhatsAppEvent.TestSent -> snackbar.showSnackbar(getString(Res.string.wa_test_sent))
                is WhatsAppEvent.TestFailed -> snackbar.showSnackbar(
                    if (event.tryLater) getString(Res.string.wa_test_offline) else getString(Res.string.wa_test_refused, event.reason)
                )
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PushedHeader(stringResource(Res.string.section_whatsapp), onBack, subtitle = stringResource(Res.string.wa_screen_subtitle))
        Column(
            Modifier.screenGutter().navigationBarsPadding().padding(bottom = AkhadaSpace.xxl),
            verticalArrangement = Arrangement.spacedBy(AkhadaSpace.md),
        ) {
            when {
                state.isLoading -> Unit
                !state.available -> UnavailableCard()
                state.webhookUrl == null || editing -> {
                    SetupCard(state.webhookUrl.orEmpty(), onSave = vm::saveWebhook, onCancel = if (editing) ({ editing = false }) else null)
                    TemplatesSection()
                }
                else -> {
                    OnCard(onChange = { editing = true }, onStop = { confirmingStop = true })
                    TestCard(state.testing, vm::sendTest)
                    SectionHeader(stringResource(Res.string.section_wa_auto), Modifier.padding(top = AkhadaSpace.md))
                    AutoSendCard(state.autoSend, vm::setAutoSend)
                    SectionHeader(stringResource(Res.string.section_wa_recent), Modifier.padding(top = AkhadaSpace.md))
                    RecentCard(state.recent)
                    TemplatesSection()
                }
            }
        }
    }

    if (confirmingStop) {
        ConfirmSheet(
            title = stringResource(Res.string.wa_stop_title),
            body = stringResource(Res.string.wa_stop_body),
            confirmLabel = stringResource(Res.string.wa_stop),
            onConfirm = {
                confirmingStop = false
                vm.removeWebhook()
            },
            onDismiss = { confirmingStop = false },
        )
    }
}

/** The row on the main Settings screen: whether Make is set up, tapping through to [WhatsAppScreen]. */
@Composable
internal fun WhatsAppSettingsCard(onOpen: () -> Unit) {
    val whatsApp = LocalAppDependencies.current.whatsApp
    val settings by whatsApp.observeSettings().collectAsStateWithLifecycle(WhatsAppSettings())
    AkhadaCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AkhadaSpace.md)) {
            Icon(AkhadaIcons.MessageCircle, contentDescription = null, tint = AkhadaTheme.colors.ink)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AkhadaSpace.xs)) {
                Text(stringResource(Res.string.wa_settings_title), style = AkhadaTheme.typography.bodyStrong)
                Text(
                    stringResource(if (whatsApp.isAvailable && settings.isSetUp) Res.string.wa_settings_on else Res.string.wa_settings_off),
                    style = AkhadaTheme.typography.body,
                    color = AkhadaTheme.colors.inkMuted,
                )
            }
            Icon(AkhadaIcons.ChevronRight, contentDescription = null, tint = AkhadaTheme.colors.inkMuted)
        }
    }
}

@Composable
private fun UnavailableCard() {
    AkhadaCard {
        Text(stringResource(Res.string.wa_unavailable_title), style = AkhadaTheme.typography.title)
        Text(stringResource(Res.string.wa_unavailable_body), style = AkhadaTheme.typography.body, color = AkhadaTheme.colors.inkMuted)
    }
}

@Composable
private fun SetupCard(current: String, onSave: (String) -> Unit, onCancel: (() -> Unit)?) {
    val colors = AkhadaTheme.colors
    var url by remember { mutableStateOf(current) }
    AkhadaCard {
        Text(stringResource(Res.string.wa_setup_title), style = AkhadaTheme.typography.title)
        Text(stringResource(Res.string.wa_setup_body), style = AkhadaTheme.typography.body)
        Column(verticalArrangement = Arrangement.spacedBy(AkhadaSpace.xs)) {
            listOf(Res.string.wa_setup_step1, Res.string.wa_setup_step2, Res.string.wa_setup_step3).forEach {
                Text(stringResource(it), style = AkhadaTheme.typography.body, color = colors.inkMuted)
            }
        }
        AkhadaTextField(
            value = url,
            onValueChange = { url = it },
            label = stringResource(Res.string.wa_webhook_label),
            placeholder = stringResource(Res.string.wa_webhook_placeholder),
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Done,
        )
        AkhadaButton(stringResource(Res.string.wa_webhook_save), { onSave(url) }, Modifier.fillMaxWidth(), enabled = url.isNotBlank(), icon = AkhadaIcons.MessageCircle)
        if (onCancel != null) {
            AkhadaButton(stringResource(Res.string.cancel), onCancel, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
        }
    }
}

@Composable
private fun OnCard(onChange: () -> Unit, onStop: () -> Unit) {
    val colors = AkhadaTheme.colors
    AkhadaCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AkhadaSpace.sm)) {
            StatusDot(colors.statusActive)
            Text(stringResource(Res.string.wa_on_title), style = AkhadaTheme.typography.bodyStrong)
        }
        Text(stringResource(Res.string.wa_on_body), style = AkhadaTheme.typography.body, color = colors.inkMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(AkhadaSpace.sm)) {
            AkhadaButton(stringResource(Res.string.wa_change_webhook), onChange, Modifier.weight(1f), kind = ButtonKind.Secondary)
            AkhadaButton(stringResource(Res.string.wa_stop), onStop, Modifier.weight(1f), kind = ButtonKind.Danger)
        }
    }
}

@Composable
private fun TestCard(testing: Boolean, onSend: (String) -> Unit) {
    var phone by remember { mutableStateOf("") }
    AkhadaCard {
        Text(stringResource(Res.string.wa_test_title), style = AkhadaTheme.typography.bodyStrong)
        Text(stringResource(Res.string.wa_test_body), style = AkhadaTheme.typography.body, color = AkhadaTheme.colors.inkMuted)
        AkhadaTextField(
            value = phone,
            onValueChange = { phone = it },
            label = stringResource(Res.string.wa_test_number),
            keyboardType = KeyboardType.Phone,
            imeAction = ImeAction.Done,
        )
        AkhadaButton(stringResource(Res.string.wa_test_send), { onSend(phone) }, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary, enabled = !testing && phone.isNotBlank())
    }
}

@Composable
private fun AutoSendCard(auto: AutoSend, onChange: (AutoSend) -> Unit) {
    val colors = AkhadaTheme.colors
    var pickingTime by remember { mutableStateOf(false) }
    AkhadaCard {
        SwitchRow(stringResource(Res.string.wa_auto_welcome), stringResource(Res.string.wa_auto_welcome_body), auto.welcome) { onChange(auto.copy(welcome = it)) }
        SwitchRow(stringResource(Res.string.wa_auto_receipts), stringResource(Res.string.wa_auto_receipts_body), auto.receipts) { onChange(auto.copy(receipts = it)) }
        SwitchRow(stringResource(Res.string.wa_auto_reminders), stringResource(Res.string.wa_auto_reminders_body), auto.reminders) { onChange(auto.copy(reminders = it)) }
        if (auto.reminders) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.wa_reminder_time), Modifier.weight(1f), style = AkhadaTheme.typography.body, color = colors.inkMuted)
                AkhadaButton(timeLabel(auto.reminderMinuteOfDay), { pickingTime = true }, kind = ButtonKind.Secondary)
            }
            if (!sendsRemindersInBackground) {
                Text(stringResource(Res.string.wa_auto_reminders_ios), style = AkhadaTheme.typography.caption, color = colors.inkMuted)
            }
        }
        Text(stringResource(Res.string.wa_auto_offline), style = AkhadaTheme.typography.caption, color = colors.inkMuted)
    }
    if (pickingTime) {
        TimeSheet(
            stringResource(Res.string.wa_reminder_set_time),
            auto.reminderMinuteOfDay,
            onPick = { minute ->
                onChange(auto.copy(reminderMinuteOfDay = minute))
                pickingTime = false
            },
            onDismiss = { pickingTime = false },
        )
    }
}

@Composable
private fun RecentCard(recent: List<OutboxMessage>) {
    AkhadaCard {
        if (recent.isEmpty()) {
            Text(stringResource(Res.string.wa_recent_empty), style = AkhadaTheme.typography.body, color = AkhadaTheme.colors.inkMuted)
        }
        recent.forEach { RecentRow(it) }
    }
}

@Composable
private fun RecentRow(message: OutboxMessage) {
    val colors = AkhadaTheme.colors
    val failed = message.status == OutboxStatus.FAILED
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(AkhadaSpace.md)) {
        StatusDot(message.status.color(), Modifier.padding(top = 7.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(Res.string.wa_recent_row, message.memberName, message.kind.label()), style = AkhadaTheme.typography.bodyStrong)
            Text(
                when (message.status) {
                    OutboxStatus.PENDING -> stringResource(Res.string.wa_status_pending)
                    OutboxStatus.SENT -> stringResource(Res.string.wa_status_sent)
                    OutboxStatus.FAILED -> stringResource(Res.string.wa_status_failed, message.errorMessage.orEmpty())
                },
                style = AkhadaTheme.typography.caption,
                color = if (failed) colors.statusDue else colors.inkMuted,
            )
        }
    }
}

@Composable
private fun OutboxStatus.color(): Color = when (this) {
    OutboxStatus.PENDING -> AkhadaTheme.colors.statusExpiring
    OutboxStatus.SENT -> AkhadaTheme.colors.statusActive
    OutboxStatus.FAILED -> AkhadaTheme.colors.statusDue
}

/** The template names and wording to create in WhatsApp Manager, copyable. */
@Composable
private fun TemplatesSection() {
    val colors = AkhadaTheme.colors
    SectionHeader(stringResource(Res.string.section_wa_templates), Modifier.padding(top = AkhadaSpace.md))
    AkhadaCard {
        Text(stringResource(Res.string.wa_templates_body), style = AkhadaTheme.typography.body, color = colors.inkMuted)
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(AkhadaSpace.md)) {
                gymTemplates.forEach { template ->
                    Column(
                        Modifier.fillMaxWidth().padding(top = AkhadaSpace.xs),
                        verticalArrangement = Arrangement.spacedBy(AkhadaSpace.xs),
                    ) {
                        Text(template.name, style = AkhadaTheme.typography.bodyStrong)
                        Surface(color = colors.surfaceSunken, shape = AkhadaRadius.cardShape, modifier = Modifier.fillMaxWidth()) {
                            Text(template.body, style = AkhadaTheme.typography.caption, modifier = Modifier.padding(AkhadaSpace.md))
                        }
                    }
                }
            }
        }
    }
}

/** "Welcome sent to Ravi on WhatsApp" and the like, after an automatic send. */
internal suspend fun autoSentText(kind: MessageKind, memberName: String): String {
    val first = memberName.trim().substringBefore(' ')
    return getString(
        when (kind) {
            MessageKind.WELCOME -> Res.string.wa_auto_sent_welcome
            MessageKind.RECEIPT -> Res.string.wa_auto_sent_receipt
            MessageKind.REMINDER -> Res.string.wa_auto_sent_reminder
        },
        first,
    )
}
