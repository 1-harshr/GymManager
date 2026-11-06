package com.harsh.gymmanager.ui.messaging

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.harsh.gymmanager.domain.messaging.LinkSender
import com.harsh.gymmanager.domain.messaging.gymTemplate
import com.harsh.gymmanager.domain.whatsapp.WhatsAppSettings
import com.harsh.gymmanager.templates.gymTemplateDefinition
import com.harsh.gymmanager.templates.render
import com.harsh.gymmanager.LocalAppDependencies
import com.harsh.gymmanager.ui.screens.settings.autoSentText
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harsh.gymmanager.domain.messaging.MemberMessage
import com.harsh.gymmanager.domain.messaging.MessageKind
import com.harsh.gymmanager.presentation.messaging.WhatsAppPrompt
import com.harsh.gymmanager.ui.LocalSnackbar
import com.harsh.gymmanager.ui.components.AkhadaBottomSheet
import com.harsh.gymmanager.ui.components.AkhadaButton
import com.harsh.gymmanager.ui.components.ButtonKind
import com.harsh.gymmanager.ui.components.SegmentedControl
import com.harsh.gymmanager.ui.format.formatPhone
import com.harsh.gymmanager.ui.icons.AkhadaIcons
import com.harsh.gymmanager.ui.theme.AkhadaRadius
import com.harsh.gymmanager.ui.theme.AkhadaSpace
import com.harsh.gymmanager.ui.theme.AkhadaTheme
import gymmanager.sharedui.generated.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Sends a message the owner chose. Once Make.com is set up it goes through the owner's scenario
 * with no tap; otherwise [LinkSender] opens WhatsApp with the text typed in for the
 * owner to send. Calls `onSent` once that worked; otherwise says why in a snackbar.
 */
@Composable
fun rememberWhatsAppOpener(): (message: MemberMessage, text: String, onSent: () -> Unit) -> Unit {
    val uriHandler = LocalUriHandler.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val whatsApp = LocalAppDependencies.current.whatsApp
    val viaServer = rememberSendsThroughServer()
    val failed = stringResource(Res.string.wa_unavailable)
    val sender = remember(uriHandler) { LinkSender(uriHandler::openUri) }
    return remember(sender, snackbar, failed, viaServer, whatsApp) {
        { message, text, onSent ->
            scope.launch {
                if (viaServer && whatsApp.sendNow(message)) {
                    onSent()
                    snackbar.showSnackbar(autoSentText(message.kind, message.memberName))
                } else if (sender.send(message, text).isSent) {
                    onSent()
                } else {
                    // A link can only fail for want of a WhatsApp app.
                    snackbar.showSnackbar(failed)
                }
            }
        }
    }
}

/** True when messages go out through the owner's Make scenario rather than by opening WhatsApp. */
@Composable
fun rememberSendsThroughServer(): Boolean {
    val whatsApp = LocalAppDependencies.current.whatsApp
    val settings by whatsApp.observeSettings().collectAsStateWithLifecycle(WhatsAppSettings())
    return whatsApp.isAvailable && settings.isSetUp
}

/** What the member will read: the approved template when sent through Make, else the app's own text. */
@Composable
fun MemberMessage.previewText(viaServer: Boolean): String {
    if (!viaServer) return text()
    val template = gymTemplate(this)
    return gymTemplateDefinition(template.name, template.languageCode)?.render(template.bodyParameters) ?: text()
}

/** Shows the message before WhatsApp opens; with several options the owner picks one. */
@Composable
fun WhatsAppSheet(prompt: WhatsAppPrompt, onSelect: (Int) -> Unit, onSent: () -> Unit, onDismiss: () -> Unit) {
    val colors = AkhadaTheme.colors
    val message = prompt.message
    val viaServer = rememberSendsThroughServer()
    val text = message.previewText(viaServer)
    val open = rememberWhatsAppOpener()
    AkhadaBottomSheet(onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(AkhadaSpace.xs)) {
            Text(
                stringResource(
                    when (message.kind) {
                        MessageKind.WELCOME -> Res.string.wa_title_welcome
                        MessageKind.RECEIPT -> Res.string.wa_title_receipt
                        MessageKind.REMINDER -> Res.string.wa_title_reminder
                    }
                ),
                style = AkhadaTheme.typography.title,
            )
            Text(
                stringResource(Res.string.wa_to, message.memberName, formatPhone(message.whatsAppNumber.removePrefix("91"))),
                style = AkhadaTheme.typography.caption,
                color = colors.inkMuted,
            )
        }
        if (prompt.options.size > 1) {
            SegmentedControl(prompt.options.indices.toList(), prompt.selected, { it?.let(onSelect) }, { prompt.options[it].kind.label() })
        }
        Surface(color = colors.surfaceSunken, shape = AkhadaRadius.cardShape, modifier = Modifier.fillMaxWidth()) {
            Text(
                text,
                style = AkhadaTheme.typography.body,
                modifier = Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()).padding(AkhadaSpace.lg),
            )
        }
        Text(stringResource(if (viaServer) Res.string.wa_send_hint else Res.string.wa_hint), style = AkhadaTheme.typography.caption, color = colors.inkMuted)
        AkhadaButton(
            stringResource(if (viaServer) Res.string.wa_send else Res.string.wa_open),
            onClick = { open(message, text, onSent) },
            modifier = Modifier.fillMaxWidth(),
            icon = AkhadaIcons.MessageCircle,
        )
        AkhadaButton(stringResource(Res.string.not_now), onClick = onDismiss, modifier = Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
    }
}
