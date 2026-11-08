package com.harsh.gymmanager.ui.screens.attendance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harsh.gymmanager.LocalAppDependencies
import com.harsh.gymmanager.domain.usecase.QuietMember
import com.harsh.gymmanager.presentation.attendance.GoneQuietViewModel
import com.harsh.gymmanager.presentation.formatRupees
import com.harsh.gymmanager.ui.components.Avatar
import com.harsh.gymmanager.ui.components.Badge
import com.harsh.gymmanager.ui.components.BadgeTone
import com.harsh.gymmanager.ui.components.EmptyState
import com.harsh.gymmanager.ui.components.MyGymIconButton
import com.harsh.gymmanager.ui.components.PushedHeader
import com.harsh.gymmanager.ui.components.tone
import com.harsh.gymmanager.ui.format.rememberToday
import com.harsh.gymmanager.ui.format.statusShortLabel
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

/** Members with a running plan who've stopped coming, most recently gone first, each with a "we miss you". */
@Composable
fun GoneQuietScreen(onBack: () -> Unit, onOpenMember: (String) -> Unit) {
    val deps = LocalAppDependencies.current
    val vm = viewModel { GoneQuietViewModel(deps.attendance) }
    val state by vm.state.collectAsStateWithLifecycle()
    val open = rememberWhatsAppOpener()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = MyGymSpace.xl),
        verticalArrangement = Arrangement.spacedBy(MyGymSpace.md),
    ) {
        item { PushedHeader(stringResource(Res.string.gone_quiet), onBack, subtitle = stringResource(Res.string.gone_quiet_subtitle)) }
        if (!state.isLoading && state.items.isEmpty()) {
            item { EmptyState(MyGymIcons.UserCheck, stringResource(Res.string.gone_quiet_empty_title), stringResource(Res.string.gone_quiet_empty_body)) }
        }
        items(state.items, key = { it.member.memberId }) { item ->
            val message = item.message
            val text = message?.text()
            QuietRow(
                item,
                onClick = { onOpenMember(item.member.memberId) },
                onSend = if (message != null && text != null) ({ open(message, text) { vm.sent(message) } }) else null,
                modifier = Modifier.screenGutter(),
            )
        }
    }
}

@Composable
private fun QuietRow(item: QuietMember, onClick: () -> Unit, onSend: (() -> Unit)?, modifier: Modifier = Modifier) {
    val colors = MyGymTheme.colors
    val member = item.member
    val today = rememberToday()
    val sentOn = item.lastSentAt?.toLocalDateTime(TimeZone.currentSystemDefault())?.date
    val sent = when {
        item.message == null -> stringResource(Res.string.no_whatsapp_number)
        sentOn == null -> null
        sentOn.daysUntil(today) <= 0 -> stringResource(Res.string.nudged_today)
        sentOn.daysUntil(today) == 1 -> stringResource(Res.string.nudged_yesterday)
        else -> sentOn.daysUntil(today).let { pluralStringResource(Res.plurals.nudged_days_ago, it, it) }
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
                Text(pluralStringResource(Res.plurals.last_came_days_ago, item.daysAway, item.daysAway), style = MyGymTheme.typography.body)
                Row(horizontalArrangement = Arrangement.spacedBy(MyGymSpace.xs)) {
                    Badge(statusShortLabel(member.status, member.latestEndDate, today), member.status.tone())
                    if (member.amountDue > 0) Badge(formatRupees(member.amountDue), BadgeTone.Due)
                }
                sent?.let { Text(it, style = MyGymTheme.typography.caption, color = if (item.sentRecently) colors.statusActive else colors.inkMuted) }
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
