package com.harsh.gymmanager.ui.screens.home

import gymmanager.sharedui.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.harsh.gymmanager.domain.model.MemberSummary
import com.harsh.gymmanager.domain.usecase.MemberFilter
import com.harsh.gymmanager.presentation.dashboard.DashboardUiState
import com.harsh.gymmanager.presentation.formatRupees
import com.harsh.gymmanager.ui.LocalGym
import com.harsh.gymmanager.ui.components.AkhadaIconButton
import com.harsh.gymmanager.ui.components.BadgeTone
import com.harsh.gymmanager.ui.components.EmptyState
import com.harsh.gymmanager.ui.components.HeroTile
import com.harsh.gymmanager.ui.components.MemberRow
import com.harsh.gymmanager.ui.components.SectionHeader
import com.harsh.gymmanager.ui.components.StatTile
import com.harsh.gymmanager.ui.components.TabHeader
import com.harsh.gymmanager.ui.format.formatWeekday
import com.harsh.gymmanager.ui.format.monthName
import com.harsh.gymmanager.ui.format.rememberToday
import com.harsh.gymmanager.ui.icons.AkhadaIcons
import com.harsh.gymmanager.ui.theme.AkhadaSize
import com.harsh.gymmanager.ui.theme.AkhadaSpace
import com.harsh.gymmanager.ui.theme.AkhadaTheme
import com.harsh.gymmanager.ui.theme.screenGutter

@Composable
fun HomeScreen(
    state: DashboardUiState,
    onOpenMembers: (MemberFilter) -> Unit,
    onOpenMember: (MemberSummary) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val today = rememberToday()
    val board = state.dashboard
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = AkhadaSize.contentBottomClearance),
        verticalArrangement = Arrangement.spacedBy(AkhadaSpace.md),
    ) {
        item {
            TabHeader(
                title = LocalGym.current.name,
                subtitle = today.formatWeekday(),
                titleStyle = AkhadaTheme.typography.title,
            ) { AkhadaIconButton(AkhadaIcons.Settings, stringResource(Res.string.settings), onOpenSettings) }
        }
        if (board == null) return@LazyColumn
        item {
            Column(Modifier.screenGutter(), verticalArrangement = Arrangement.spacedBy(AkhadaSpace.md)) {
                HeroTile(
                    label = stringResource(Res.string.collected_in, today.monthName()),
                    value = formatRupees(board.revenueThisMonth),
                    caption = if (board.totalDue > 0) stringResource(Res.string.still_due, formatRupees(board.totalDue)) else stringResource(Res.string.nothing_due),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(AkhadaSpace.md)) {
                    StatTile(stringResource(Res.string.stat_active), board.activeCount.toString(), BadgeTone.Active, { onOpenMembers(MemberFilter.ACTIVE) }, Modifier.weight(1f))
                    StatTile(stringResource(Res.string.stat_expiring_week), board.expiringSoon.size.toString(), BadgeTone.Expiring, { onOpenMembers(MemberFilter.EXPIRING_SOON) }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(AkhadaSpace.md)) {
                    StatTile(stringResource(Res.string.stat_expired), board.expiredCount.toString(), BadgeTone.Expired, { onOpenMembers(MemberFilter.EXPIRED) }, Modifier.weight(1f))
                    StatTile(stringResource(Res.string.stat_dues, board.membersWithDuesCount), formatRupees(board.totalDue), BadgeTone.Due, { onOpenMembers(MemberFilter.WITH_DUES) }, Modifier.weight(1f))
                }
            }
        }
        item {
            SectionHeader(
                stringResource(Res.string.section_expiring_week),
                Modifier.screenGutter().padding(top = AkhadaSpace.md),
                action = if (board.expiringSoon.isNotEmpty()) stringResource(Res.string.see_all) else null,
                onAction = { onOpenMembers(MemberFilter.EXPIRING_SOON) },
            )
        }
        if (board.expiringSoon.isEmpty()) {
            item {
                EmptyState(AkhadaIcons.Calendar, stringResource(Res.string.no_one_expires_title), stringResource(Res.string.no_one_expires_body))
            }
        } else {
            items(board.expiringSoon, key = { it.memberId }) { member ->
                MemberRow(member, today, onClick = { onOpenMember(member) }, modifier = Modifier.screenGutter())
            }
        }
    }
}
