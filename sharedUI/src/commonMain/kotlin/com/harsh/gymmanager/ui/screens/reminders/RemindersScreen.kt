package com.harsh.gymmanager.ui.screens.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harsh.gymmanager.LocalAppDependencies
import com.harsh.gymmanager.domain.messaging.ReminderItem
import com.harsh.gymmanager.presentation.formatRupees
import com.harsh.gymmanager.presentation.messaging.RemindersViewModel
import com.harsh.gymmanager.ui.components.LoadingMark
import com.harsh.gymmanager.ui.components.MyGymButton
import com.harsh.gymmanager.ui.components.MyGymIconButton
import com.harsh.gymmanager.ui.components.Avatar
import com.harsh.gymmanager.ui.components.Badge
import com.harsh.gymmanager.ui.components.MemberStatusBadge
import com.harsh.gymmanager.ui.components.BadgeTone
import com.harsh.gymmanager.ui.components.ButtonKind
import com.harsh.gymmanager.ui.components.EmptyState
import com.harsh.gymmanager.ui.components.PushedHeader
import com.harsh.gymmanager.ui.components.tone
import com.harsh.gymmanager.ui.format.statusShortLabel
import com.harsh.gymmanager.ui.format.rememberToday
import com.harsh.gymmanager.ui.icons.MyGymIcons
import com.harsh.gymmanager.ui.messaging.rememberWhatsAppOpener
import com.harsh.gymmanager.ui.messaging.text
import com.harsh.gymmanager.ui.theme.MyGymRadius
import com.harsh.gymmanager.ui.theme.MyGymSpace
import com.harsh.gymmanager.ui.theme.MyGymTheme
import com.harsh.gymmanager.ui.theme.screenGutter
import gymmanager.sharedui.generated.resources.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Everyone worth a WhatsApp reminder. "Send to …" at the bottom walks through those not reminded
 * in the last few days, one tap each; each row can also be sent on its own.
 */
@Composable
fun RemindersScreen(onBack: () -> Unit, onOpenMember: (String) -> Unit) {
    val deps = LocalAppDependencies.current
    val vm = viewModel { RemindersViewModel(deps.messaging) }
    val state by vm.state.collectAsStateWithLifecycle()
    val open = rememberWhatsAppOpener()

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = MyGymSpace.xl),
            verticalArrangement = Arrangement.spacedBy(MyGymSpace.md),
        ) {
            item { PushedHeader(stringResource(Res.string.reminders), onBack, subtitle = stringResource(Res.string.reminders_subtitle)) }
            if (state.isLoading) item { LoadingMark(Modifier.fillParentMaxHeight(0.5f)) }
            if (!state.isLoading && state.items.isEmpty()) {
                item {
                    EmptyState(MyGymIcons.MessageCircle, stringResource(Res.string.reminders_empty_title), stringResource(Res.string.reminders_empty_body))
                }
            }
            items(state.items, key = { it.member.memberId }) { item ->
                val message = item.message
                val text = message?.text()
                ReminderRow(
                    item,
                    onClick = { onOpenMember(item.member.memberId) },
                    onSend = if (message != null && text != null) ({ open(message, text) { vm.sent(message) } }) else null,
                    modifier = Modifier.screenGutter(),
                )
            }
        }

        if (state.items.isNotEmpty()) {
            val next = state.next
            Column(
                Modifier.screenGutter().navigationBarsPadding().padding(vertical = MyGymSpace.md),
                verticalArrangement = Arrangement.spacedBy(MyGymSpace.sm),
            ) {
                val message = next?.message
                if (next == null || message == null) {
                    Text(
                        stringResource(Res.string.reminders_all_sent),
                        style = MyGymTheme.typography.bodyStrong,
                        color = MyGymTheme.colors.statusActive,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = MyGymSpace.md),
                    )
                } else {
                    val text = message.text()
                    MyGymButton(
                        stringResource(Res.string.send_next, message.memberName.substringBefore(' '), state.position, state.total),
                        onClick = { open(message, text) { vm.sent(message) } },
                        modifier = Modifier.fillMaxWidth(),
                        icon = MyGymIcons.MessageCircle,
                    )
                    MyGymButton(stringResource(Res.string.skip), { vm.skip(next.member.memberId) }, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
                }
            }
        }
    }
}

@Composable
private fun ReminderRow(item: ReminderItem, onClick: () -> Unit, onSend: (() -> Unit)?, modifier: Modifier = Modifier) {
    val colors = MyGymTheme.colors
    val member = item.member
    val today = rememberToday()
    val sentOn = item.lastSentAt?.toLocalDateTime(TimeZone.currentSystemDefault())?.date
    val note = when {
        item.message == null -> stringResource(Res.string.no_whatsapp_number)
        sentOn == null -> null
        sentOn.daysUntil(today) <= 0 -> stringResource(Res.string.sent_today)
        sentOn.daysUntil(today) == 1 -> stringResource(Res.string.sent_yesterday)
        else -> sentOn.daysUntil(today).let { pluralStringResource(Res.plurals.sent_days_ago, it, it) }
    }
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = MyGymRadius.cardShape, color = colors.surface) {
        Row(
            Modifier.padding(horizontal = MyGymSpace.lg, vertical = MyGymSpace.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MyGymSpace.md),
        ) {
            Avatar(member.name, member.photoPath)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MyGymSpace.xs)) {
                Text(member.name, style = MyGymTheme.typography.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(MyGymSpace.xs)) {
                    MemberStatusBadge(member, today)
                    if (member.amountDue > 0) Badge(formatRupees(member.amountDue), BadgeTone.Due)
                }
                note?.let {
                    Text(it, style = MyGymTheme.typography.caption, color = if (item.sentRecently) colors.statusActive else colors.inkMuted)
                }
            }
            if (onSend != null) {
                MyGymIconButton(
                    MyGymIcons.MessageCircle,
                    stringResource(Res.string.wa_message_member, member.name),
                    onClick = onSend,
                    iconSize = 20.dp,
                    container = if (item.sentRecently) colors.surfaceSunken else colors.accentSoft,
                    content = if (item.sentRecently) colors.inkMuted else colors.onAccentSoft,
                )
            }
        }
    }
}
