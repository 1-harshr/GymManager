package com.harsh.gymmanager.ui.screens.members

import com.harsh.gymmanager.ui.components.MemberTile
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.BoxWithConstraints
import gymmanager.sharedui.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.pluralStringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.harsh.gymmanager.domain.model.MemberSummary
import com.harsh.gymmanager.domain.usecase.MemberFilter
import com.harsh.gymmanager.presentation.members.MembersUiState
import com.harsh.gymmanager.ui.components.MyGymIconButton
import com.harsh.gymmanager.ui.components.EmptyState
import com.harsh.gymmanager.ui.components.FilterChips
import com.harsh.gymmanager.ui.components.GhostButton
import com.harsh.gymmanager.ui.components.MenuAction
import com.harsh.gymmanager.ui.components.MoreMenu
import com.harsh.gymmanager.ui.components.SelectionHeader
import com.harsh.gymmanager.ui.components.MemberRow
import com.harsh.gymmanager.ui.components.SearchBar
import com.harsh.gymmanager.ui.components.TabHeader
import com.harsh.gymmanager.ui.format.rememberToday
import com.harsh.gymmanager.ui.icons.MyGymIcons
import com.harsh.gymmanager.ui.theme.MyGymSize
import com.harsh.gymmanager.ui.theme.MyGymSpace
import com.harsh.gymmanager.ui.theme.screenGutter

@Composable
fun MemberFilter.label(): String = stringResource(
    when (this) {
        MemberFilter.ALL -> Res.string.filter_all
        MemberFilter.ACTIVE -> Res.string.filter_active
        MemberFilter.EXPIRING_SOON -> Res.string.filter_expiring
        MemberFilter.EXPIRED -> Res.string.filter_expired
        MemberFilter.WITH_DUES -> Res.string.filter_dues
        MemberFilter.NO_MEMBERSHIP -> Res.string.filter_no_plan
        MemberFilter.ARCHIVED -> Res.string.filter_archived
    },
)

private val FilterOrder = listOf(
    MemberFilter.ALL, MemberFilter.ACTIVE, MemberFilter.EXPIRING_SOON, MemberFilter.EXPIRED,
    MemberFilter.WITH_DUES, MemberFilter.NO_MEMBERSHIP, MemberFilter.ARCHIVED,
)

@Composable
fun MembersScreen(
    state: MembersUiState,
    onQueryChange: (String) -> Unit,
    onFilterChange: (MemberFilter) -> Unit,
    onOpenMember: (MemberSummary) -> Unit,
    onAddMember: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleSelection: (MemberSummary) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onImport: () -> Unit,
    asGrid: Boolean,
    onAsGridChange: (Boolean) -> Unit,
) = BoxWithConstraints(Modifier.fillMaxSize()) {
    val today = rememberToday()
    val subtitle = if (state.isLoading) null else pluralStringResource(Res.plurals.members_count, state.members.size, state.members.size)
    // As many tiles across as fit at their smallest; never fewer than two.
    val columns = ((maxWidth - MyGymSpace.gutter * 2 + MyGymSpace.md) / (TILE_MIN_WIDTH + MyGymSpace.md)).toInt().coerceAtLeast(2)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = MyGymSize.contentBottomClearance),
        verticalArrangement = Arrangement.spacedBy(MyGymSpace.md),
    ) {
        item {
            if (state.isSelecting) {
                SelectionHeader(state.selectedIds.size, onCancel = onClearSelection, onSelectAll = onSelectAll)
            } else {
                TabHeader(stringResource(Res.string.tab_members), subtitle) {
                    MoreMenu(
                        actions = listOf(
                            MenuAction(stringResource(Res.string.select_members)) { state.members.firstOrNull()?.let(onToggleSelection) },
                            MenuAction(stringResource(Res.string.import_from_file), onClick = onImport),
                        ),
                        contentDescription = stringResource(Res.string.more_member_actions),
                    )
                    // Shows the view it switches to.
                    MyGymIconButton(
                        if (asGrid) MyGymIcons.List else MyGymIcons.LayoutGrid,
                        stringResource(if (asGrid) Res.string.show_as_list else Res.string.show_as_grid),
                        { onAsGridChange(!asGrid) },
                    )
                    MyGymIconButton(MyGymIcons.Settings, stringResource(Res.string.settings), onOpenSettings)
                }
            }
        }
        item { SearchBar(state.query, onQueryChange, Modifier.screenGutter()) }
        item { FilterChips(FilterOrder, state.filter, onFilterChange, { it.label() }) }
        if (!state.isLoading && state.members.isEmpty()) {
            item {
                val nothingYet = state.query.isBlank() && state.filter == MemberFilter.ALL
                when {
                    nothingYet -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        EmptyState(
                            MyGymIcons.Users, stringResource(Res.string.no_members_title), stringResource(Res.string.no_members_body),
                            actionLabel = stringResource(Res.string.add_member), onAction = onAddMember,
                        )
                        GhostButton(stringResource(Res.string.import_from_file), onImport)
                    }
                    state.query.isNotBlank() -> EmptyState(MyGymIcons.Search, stringResource(Res.string.no_match_title, state.query.trim()), stringResource(Res.string.no_match_body))
                    else -> EmptyState(MyGymIcons.Users, stringResource(Res.string.no_filter_match_title), stringResource(Res.string.no_filter_match_body))
                }
            }
        }
        if (asGrid) {
            items(state.members.chunked(columns), key = { row -> "row-${row.first().memberId}" }) { row ->
                Row(Modifier.screenGutter().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(MyGymSpace.md)) {
                    row.forEach { member ->
                        MemberTile(
                            member,
                            today,
                            onClick = { if (state.isSelecting) onToggleSelection(member) else onOpenMember(member) },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            selected = member.memberId in state.selectedIds,
                            onLongClick = { onToggleSelection(member) },
                        )
                    }
                    // Keep a short last row's tiles the same width as the rest.
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        } else {
            items(state.members, key = { it.memberId }) { member ->
                MemberRow(
                    member,
                    today,
                    onClick = { if (state.isSelecting) onToggleSelection(member) else onOpenMember(member) },
                    modifier = Modifier.screenGutter(),
                    selected = member.memberId in state.selectedIds,
                    onLongClick = { onToggleSelection(member) },
                )
            }
        }
    }
}

/** A grid tile is never narrower than this; wider screens fit more across. */
private val TILE_MIN_WIDTH = 150.dp
