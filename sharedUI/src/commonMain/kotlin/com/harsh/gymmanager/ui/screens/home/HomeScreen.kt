package com.harsh.gymmanager.ui.screens.home

import com.harsh.gymmanager.ui.isOwnerOrNoAccount
import gymmanager.sharedui.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.harsh.gymmanager.LocalAppDependencies
import com.harsh.gymmanager.ui.LocalLanguage
import com.harsh.gymmanager.ui.components.MyGymCard
import com.harsh.gymmanager.ui.theme.MyGymRadius
import org.jetbrains.compose.resources.pluralStringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.harsh.gymmanager.domain.model.HomeFeatures
import com.harsh.gymmanager.presentation.dashboard.DashboardUiState
import com.harsh.gymmanager.presentation.formatRupees
import com.harsh.gymmanager.ui.LocalGym
import com.harsh.gymmanager.ui.components.MyGymIconButton
import com.harsh.gymmanager.ui.components.BadgeTone
import com.harsh.gymmanager.ui.components.StatTile
import com.harsh.gymmanager.ui.components.TabHeader
import com.harsh.gymmanager.ui.format.formatWeekday
import com.harsh.gymmanager.ui.format.rememberToday
import com.harsh.gymmanager.ui.icons.MyGymIcons
import com.harsh.gymmanager.ui.theme.MyGymSize
import com.harsh.gymmanager.ui.theme.MyGymSpace
import com.harsh.gymmanager.ui.theme.MyGymTheme
import com.harsh.gymmanager.ui.theme.screenGutter

/**
 * The owner's morning view: how collections are going, the counts, and recent payments. With
 * [HomeFeatures.aiInsights] on it adds a short brief (written by the phone's own model where it
 * has one), the month's outlook, renewals and who needs attention; the chart has its own switch.
 */
@Composable
fun HomeScreen(
    state: DashboardUiState,
    onOpenMember: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenInbox: () -> Unit,
    features: HomeFeatures,
) {
    val today = rememberToday()
    val board = state.dashboard
    val insights = state.insights
    val deps = LocalAppDependencies.current
    val gymName = LocalGym.current.name
    val language = LocalLanguage.current
    var aiBrief by remember { mutableStateOf<String?>(null) }
    // The phone's model only writes English, and only once the owner has turned insights on.
    LaunchedEffect(insights, language, gymName, features.aiInsights) {
        aiBrief = if (features.aiInsights && insights != null && language == "en") deps.brief.write(insights, gymName) else null
    }

    // Staff don't see what the gym takes in: the brief, collections and the forecast are the owner's.
    val seesRevenue = isOwnerOrNoAccount
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = MyGymSize.contentBottomClearance),
        verticalArrangement = Arrangement.spacedBy(MyGymSpace.md),
    ) {
        item {
            TabHeader(
                title = gymName,
                subtitle = today.formatWeekday(),
                titleStyle = MyGymTheme.typography.title,
            ) {
                BellButton(state.unreadAlerts, onOpenInbox)
                MyGymIconButton(MyGymIcons.Settings, stringResource(Res.string.settings), onOpenSettings)
            }
        }
        if (board == null || insights == null) return@LazyColumn
        if (features.aiInsights && seesRevenue) item { Box(Modifier.screenGutter()) { BriefCard(insights, aiBrief) } }
        if (seesRevenue) item { Box(Modifier.screenGutter()) { CollectionsCard(insights.pace, insights.today, showChart = features.collectionsChart) } }
        item {
            Column(Modifier.screenGutter(), verticalArrangement = Arrangement.spacedBy(MyGymSpace.md)) {
                Row(horizontalArrangement = Arrangement.spacedBy(MyGymSpace.md)) {
                    StatTile(stringResource(Res.string.stat_active), board.activeCount.toString(), BadgeTone.Active, Modifier.weight(1f))
                    StatTile(stringResource(Res.string.stat_expiring_week), board.expiringSoon.size.toString(), BadgeTone.Expiring, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(MyGymSpace.md)) {
                    StatTile(stringResource(Res.string.stat_expired), board.expiredCount.toString(), BadgeTone.Expired, Modifier.weight(1f))
                    StatTile(stringResource(Res.string.stat_dues, board.membersWithDuesCount), formatRupees(board.totalDue), BadgeTone.Due, Modifier.weight(1f))
                }
                if (state.remindersToSend > 0) RemindersTile(state.remindersToSend, onOpenReminders)
            }
        }
        if (features.aiInsights) {
            if (seesRevenue) item { Box(Modifier.screenGutter()) { ForecastCard(insights.forecast) } }
            item { Box(Modifier.screenGutter()) { RenewalsCard(insights.renewals, insights.today, onOpenMember) } }
            if (insights.oldestDues.isNotEmpty() || insights.winBack.isNotEmpty()) {
                item { Box(Modifier.screenGutter()) { AttentionCard(insights, onOpenMember) } }
            }
        }
        item { Box(Modifier.screenGutter()) { RecentPaymentsCard(state.recentPayments, insights.today, onOpenMember) } }
    }
}

/** Opens the inbox; a badge counts unread alerts. */
@Composable
private fun BellButton(unread: Int, onClick: () -> Unit) {
    MyGymIconButton(
        MyGymIcons.Bell,
        if (unread > 0) stringResource(Res.string.open_alerts_unread, unread) else stringResource(Res.string.open_alerts),
        onClick,
        badge = when {
            unread > 9 -> "9+"
            unread > 0 -> unread.toString()
            else -> null
        },
    )
}

/** Leads to the WhatsApp reminder run; shown only while someone is due one. */
@Composable
private fun RemindersTile(count: Int, onClick: () -> Unit) {
    val colors = MyGymTheme.colors
    MyGymCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MyGymSpace.md)) {
            Box(Modifier.size(MyGymSize.iconButton).background(colors.accentSoft, MyGymRadius.pillShape), contentAlignment = Alignment.Center) {
                Icon(MyGymIcons.MessageCircle, contentDescription = null, tint = colors.onAccentSoft, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.reminders_tile), style = MyGymTheme.typography.bodyStrong)
                Text(pluralStringResource(Res.plurals.reminders_to_send, count, count), style = MyGymTheme.typography.caption, color = colors.inkMuted)
            }
            Icon(MyGymIcons.ChevronRight, contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(20.dp))
        }
    }
}
