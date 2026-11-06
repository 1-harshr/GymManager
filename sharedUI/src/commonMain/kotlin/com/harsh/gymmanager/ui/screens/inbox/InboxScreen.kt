package com.harsh.gymmanager.ui.screens.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harsh.gymmanager.LocalAppDependencies
import com.harsh.gymmanager.domain.model.InboxItem
import com.harsh.gymmanager.domain.model.InboxKind
import com.harsh.gymmanager.presentation.formatRupees
import com.harsh.gymmanager.presentation.inbox.InboxViewModel
import com.harsh.gymmanager.ui.components.EmptyState
import com.harsh.gymmanager.ui.components.PushedHeader
import com.harsh.gymmanager.ui.components.SectionHeader
import com.harsh.gymmanager.ui.format.formatDayMonth
import com.harsh.gymmanager.ui.format.rememberToday
import com.harsh.gymmanager.ui.icons.MyGymIcons
import com.harsh.gymmanager.ui.theme.MyGymRadius
import com.harsh.gymmanager.ui.theme.MyGymSize
import com.harsh.gymmanager.ui.theme.MyGymSpace
import com.harsh.gymmanager.ui.theme.MyGymTheme
import com.harsh.gymmanager.ui.theme.screenGutter
import gymmanager.sharedui.generated.resources.*
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import org.jetbrains.compose.resources.stringResource

/** The bell's alerts, newest first, grouped by the day they came in; each opens its member. */
@Composable
fun InboxScreen(onBack: () -> Unit, onOpenMember: (Long) -> Unit) {
    val deps = LocalAppDependencies.current
    val vm = viewModel { InboxViewModel(deps.inbox) }
    val state by vm.state.collectAsStateWithLifecycle()
    val today = rememberToday()
    val yesterday = today.minus(1, DateTimeUnit.DAY)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = MyGymSpace.xl),
        verticalArrangement = Arrangement.spacedBy(MyGymSpace.md),
    ) {
        item { PushedHeader(stringResource(Res.string.inbox_title), onBack) }
        if (!state.isLoading && state.items.isEmpty()) {
            item { EmptyState(MyGymIcons.Bell, stringResource(Res.string.inbox_empty_title), stringResource(Res.string.inbox_empty_body)) }
        }
        state.items.groupBy { it.createdOn }.forEach { (day, items) ->
            item(key = "day-$day") {
                SectionHeader(
                    when (day) {
                        today -> stringResource(Res.string.day_today)
                        yesterday -> stringResource(Res.string.day_yesterday)
                        else -> day.formatDayMonth()
                    },
                    Modifier.screenGutter().padding(top = MyGymSpace.sm),
                )
            }
            items(items, key = { it.id }) { item ->
                AlertRow(item, isNew = item.id in state.newIds, onClick = { onOpenMember(item.memberId) }, modifier = Modifier.screenGutter())
            }
        }
        item { Box(Modifier.navigationBarsPadding()) }
    }
}

@Composable
private fun AlertRow(item: InboxItem, isNew: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MyGymTheme.colors
    val (icon, title, detail) = when (item.kind) {
        InboxKind.ENDS_TODAY -> Triple(
            MyGymIcons.Calendar,
            stringResource(Res.string.alert_ends_today, item.memberName),
            stringResource(Res.string.alert_ends_today_detail, formatRupees(item.amount)),
        )
        InboxKind.LAPSED -> Triple(
            MyGymIcons.Renew,
            stringResource(Res.string.alert_lapsed, item.memberName, item.date.formatDayMonth()),
            stringResource(Res.string.alert_lapsed_detail),
        )
        InboxKind.DUE_OVERDUE -> Triple(
            MyGymIcons.Clock,
            stringResource(Res.string.alert_due, item.memberName, formatRupees(item.amount), item.date.formatDayMonth()),
            stringResource(Res.string.alert_due_detail),
        )
    }
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = MyGymRadius.cardShape, color = if (isNew) colors.accentSoft else colors.surface) {
        Row(
            Modifier.padding(horizontal = MyGymSpace.lg, vertical = MyGymSpace.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MyGymSpace.md),
        ) {
            Box(Modifier.size(MyGymSize.iconButton).background(if (isNew) colors.surface else colors.surfaceSunken, MyGymRadius.pillShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = if (item.kind == InboxKind.DUE_OVERDUE) colors.statusDue else colors.ink, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MyGymTheme.typography.bodyStrong, color = if (isNew) colors.onAccentSoft else colors.ink)
                Text(detail, style = MyGymTheme.typography.caption, color = if (isNew) colors.onAccentSoft else colors.inkMuted)
            }
        }
    }
}
