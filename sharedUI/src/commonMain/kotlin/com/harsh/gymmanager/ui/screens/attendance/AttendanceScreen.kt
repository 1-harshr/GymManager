package com.harsh.gymmanager.ui.screens.attendance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harsh.gymmanager.LocalAppDependencies
import com.harsh.gymmanager.domain.model.MemberSummary
import com.harsh.gymmanager.domain.usecase.Visit
import com.harsh.gymmanager.presentation.attendance.AttendanceViewModel
import com.harsh.gymmanager.presentation.formatRupees
import com.harsh.gymmanager.ui.LocalSnackbar
import com.harsh.gymmanager.ui.components.LoadingMark
import com.harsh.gymmanager.ui.components.Avatar
import com.harsh.gymmanager.ui.components.Badge
import com.harsh.gymmanager.ui.components.BadgeTone
import com.harsh.gymmanager.ui.components.EmptyState
import com.harsh.gymmanager.ui.components.MyGymCard
import com.harsh.gymmanager.ui.components.MyGymIconButton
import com.harsh.gymmanager.ui.components.PushedHeader
import com.harsh.gymmanager.ui.components.SearchBar
import com.harsh.gymmanager.ui.components.SectionHeader
import com.harsh.gymmanager.ui.components.tone
import com.harsh.gymmanager.ui.format.formatTime
import com.harsh.gymmanager.ui.format.formatWeekday
import com.harsh.gymmanager.ui.format.rememberToday
import com.harsh.gymmanager.ui.format.resolve
import com.harsh.gymmanager.ui.format.statusShortLabel
import com.harsh.gymmanager.ui.icons.MyGymIcons
import com.harsh.gymmanager.ui.theme.MyGymRadius
import com.harsh.gymmanager.ui.theme.MyGymSpace
import com.harsh.gymmanager.ui.theme.MyGymTheme
import com.harsh.gymmanager.ui.theme.screenGutter
import gymmanager.sharedui.generated.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Who's in today. The search at the top finds a member and checks them in with one tap; each row
 * below can be undone. Members who've stopped coming are a tap away at the top.
 */
@Composable
fun AttendanceScreen(onBack: () -> Unit, onOpenMember: (String) -> Unit, onOpenGoneQuiet: () -> Unit) {
    val deps = LocalAppDependencies.current
    val snackbar = LocalSnackbar.current
    val vm = viewModel { AttendanceViewModel(deps.attendance, deps.memberUseCases) }
    val state by vm.state.collectAsStateWithLifecycle()
    val today = rememberToday()

    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it.resolve()) } }
    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = MyGymSpace.xxl),
        verticalArrangement = Arrangement.spacedBy(MyGymSpace.md),
    ) {
        item { PushedHeader(stringResource(Res.string.here_today), onBack, subtitle = today.formatWeekday()) }
        item { SearchBar(state.query, vm::onQueryChange, Modifier.screenGutter(), placeholder = stringResource(Res.string.check_in_search)) }
        if (state.query.isNotBlank()) {
            if (state.matches.isEmpty()) {
                item {
                    Text(
                        stringResource(Res.string.no_match_title, state.query.trim()),
                        Modifier.screenGutter(),
                        style = MyGymTheme.typography.body,
                        color = MyGymTheme.colors.inkMuted,
                    )
                }
            }
            items(state.matches, key = { "match-${it.memberId}" }) { member ->
                MatchRow(member, isIn = member.memberId in state.checkedIn, onCheckIn = { vm.checkIn(member) }, modifier = Modifier.screenGutter())
            }
            return@LazyColumn
        }
        if (state.goneQuiet > 0) item { GoneQuietTile(state.goneQuiet, onOpenGoneQuiet, Modifier.screenGutter()) }
        if (state.isLoading) item { LoadingMark(Modifier.fillParentMaxHeight(0.5f)) }
        if (!state.isLoading && state.visits.isEmpty()) {
            item { EmptyState(MyGymIcons.UserCheck, stringResource(Res.string.attendance_empty_title), stringResource(Res.string.attendance_empty_body)) }
        }
        if (state.visits.isNotEmpty()) {
            item {
                SectionHeader(
                    pluralStringResource(Res.plurals.checked_in_count, state.visits.size, state.visits.size),
                    Modifier.screenGutter().padding(top = MyGymSpace.sm),
                )
            }
        }
        items(state.visits, key = { it.checkIn.id }) { visit ->
            VisitRow(visit, onClick = { onOpenMember(visit.member.memberId) }, onUndo = { vm.undo(visit) }, modifier = Modifier.screenGutter())
        }
    }
}

/** A search result: tap the button to check them in; someone already in says so instead. */
@Composable
private fun MatchRow(member: MemberSummary, isIn: Boolean, onCheckIn: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MyGymTheme.colors
    MemberRow(member, onClick = onCheckIn, modifier = modifier) {
        if (isIn) {
            Badge(stringResource(Res.string.in_today), BadgeTone.Active)
        } else {
            MyGymIconButton(
                MyGymIcons.UserCheck,
                stringResource(Res.string.check_in_member, member.name),
                onClick = onCheckIn,
                iconSize = 20.dp,
                container = colors.accentSoft,
                content = colors.onAccentSoft,
            )
        }
    }
}

@Composable
private fun VisitRow(visit: Visit, onClick: () -> Unit, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    MemberRow(visit.member, onClick = onClick, modifier = modifier, note = stringResource(Res.string.checked_in_at, visit.checkIn.at.formatTime())) {
        MyGymIconButton(MyGymIcons.X, stringResource(Res.string.undo_check_in_for, visit.member.name), onClick = onUndo, iconSize = 18.dp)
    }
}

/** Name, photo and what staff should notice: an ended plan or money owed. */
@Composable
private fun MemberRow(
    member: MemberSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null,
    trailing: @Composable () -> Unit,
) {
    val colors = MyGymTheme.colors
    val today = rememberToday()
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
                    Badge(statusShortLabel(member.status, member.latestEndDate, today), member.status.tone())
                    if (member.amountDue > 0) Badge(formatRupees(member.amountDue), BadgeTone.Due)
                }
                note?.let { Text(it, style = MyGymTheme.typography.caption, color = colors.inkMuted) }
            }
            trailing()
        }
    }
}

@Composable
private fun GoneQuietTile(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MyGymTheme.colors
    MyGymCard(modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MyGymSpace.md)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.gone_quiet), style = MyGymTheme.typography.bodyStrong)
                Text(pluralStringResource(Res.plurals.gone_quiet_count, count, count), style = MyGymTheme.typography.caption, color = colors.inkMuted)
            }
            Icon(MyGymIcons.ChevronRight, contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(20.dp))
        }
    }
}
