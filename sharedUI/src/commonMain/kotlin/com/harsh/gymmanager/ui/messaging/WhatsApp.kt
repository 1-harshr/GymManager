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
import com.harsh.gymmanager.domain.messaging.MemberMessage
import com.harsh.gymmanager.domain.messaging.MessageKind
import com.harsh.gymmanager.domain.messaging.whatsAppLink
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
 * Opens WhatsApp on the member's chat with [text] typed in; the owner taps send there.
 * Returns false, after saying so in a snackbar, if nothing could open the link.
 */
@Composable
fun rememberWhatsAppOpener(): (message: MemberMessage, text: String) -> Boolean {
    val uriHandler = LocalUriHandler.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val failed = stringResource(Res.string.wa_unavailable)
    return remember(uriHandler, snackbar, failed) {
        { message, text ->
            try {
                uriHandler.openUri(whatsAppLink(message.whatsAppNumber, text))
                true
            } catch (e: IllegalArgumentException) {
                // Android's handler throws this when no app can open the link.
                scope.launch { snackbar.showSnackbar(failed) }
                false
            }
        }
    }
}

/** Shows the message before WhatsApp opens; with several options the owner picks one. */
@Composable
fun WhatsAppSheet(prompt: WhatsAppPrompt, onSelect: (Int) -> Unit, onSent: () -> Unit, onDismiss: () -> Unit) {
    val colors = AkhadaTheme.colors
    val message = prompt.message
    val text = message.text()
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
        Text(stringResource(Res.string.wa_hint), style = AkhadaTheme.typography.caption, color = colors.inkMuted)
        AkhadaButton(
            stringResource(Res.string.wa_open),
            onClick = { if (open(message, text)) onSent() },
            modifier = Modifier.fillMaxWidth(),
            icon = AkhadaIcons.MessageCircle,
        )
        AkhadaButton(stringResource(Res.string.not_now), onClick = onDismiss, modifier = Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
    }
}
