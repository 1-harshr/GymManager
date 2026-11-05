package com.harsh.gymmanager.ui.screens.members

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
import com.harsh.gymmanager.ui.components.AkhadaIconButton
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
import com.harsh.gymmanager.ui.icons.AkhadaIcons
import com.harsh.gymmanager.ui.theme.AkhadaSize
import com.harsh.gymmanager.ui.theme.AkhadaSpace
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
) {
    val today = rememberToday()
    val subtitle = if (state.isLoading) null else pluralStringResource(Res.plurals.members_count, state.members.size, state.members.size)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = AkhadaSize.contentBottomClearance),
        verticalArrangement = Arrangement.spacedBy(AkhadaSpace.md),
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
                    AkhadaIconButton(AkhadaIcons.Settings, stringResource(Res.string.settings), onOpenSettings)
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
                            AkhadaIcons.Users, stringResource(Res.string.no_members_title), stringResource(Res.string.no_members_body),
                            actionLabel = stringResource(Res.string.add_member), onAction = onAddMember,
                        )
                        GhostButton(stringResource(Res.string.import_from_file), onImport)
                    }
                    state.query.isNotBlank() -> EmptyState(AkhadaIcons.Search, stringResource(Res.string.no_match_title, state.query.trim()), stringResource(Res.string.no_match_body))
                    else -> EmptyState(AkhadaIcons.Users, stringResource(Res.string.no_filter_match_title), stringResource(Res.string.no_filter_match_body))
                }
            }
        }
        items(state.members, key = { it.memberId }) { member ->
            val selected = member.memberId in state.selectedIds
            MemberRow(
                member,
                today,
                onClick = { if (state.isSelecting) onToggleSelection(member) else onOpenMember(member) },
                modifier = Modifier.screenGutter(),
                selected = selected,
                onLongClick = { onToggleSelection(member) },
            )
        }
    }
}
