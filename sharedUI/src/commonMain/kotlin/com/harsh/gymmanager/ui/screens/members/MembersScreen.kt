package com.harsh.gymmanager.ui.screens.members

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

fun MemberFilter.label(): String = when (this) {
    MemberFilter.ALL -> "All"
    MemberFilter.ACTIVE -> "Active"
    MemberFilter.EXPIRING_SOON -> "Expiring"
    MemberFilter.EXPIRED -> "Expired"
    MemberFilter.WITH_DUES -> "Dues"
    MemberFilter.NO_MEMBERSHIP -> "No plan"
    MemberFilter.ARCHIVED -> "Archived"
}

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
    val subtitle = when {
        state.isLoading -> null
        state.members.size == 1 -> "1 member"
        else -> "${state.members.size} members"
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = AkhadaSize.contentBottomClearance),
        verticalArrangement = Arrangement.spacedBy(AkhadaSpace.md),
    ) {
        item {
            if (state.isSelecting) {
                SelectionHeader(state.selectedIds.size, onCancel = onClearSelection, onSelectAll = onSelectAll)
            } else {
                TabHeader("Members", subtitle) {
                    MoreMenu(
                        actions = listOf(
                            MenuAction("Select members") { state.members.firstOrNull()?.let(onToggleSelection) },
                            MenuAction("Import from file", onClick = onImport),
                        ),
                        contentDescription = "More member actions",
                    )
                    AkhadaIconButton(AkhadaIcons.Settings, "Settings", onOpenSettings)
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
                            AkhadaIcons.Users, "No members yet", "Add your first member, or bring them all in from a spreadsheet.",
                            actionLabel = "Add member", onAction = onAddMember,
                        )
                        GhostButton("Import from file", onImport)
                    }
                    state.query.isNotBlank() -> EmptyState(AkhadaIcons.Search, "No one matches “${state.query.trim()}”", "Check the spelling, or search by phone number.")
                    else -> EmptyState(AkhadaIcons.Users, "No ${state.filter.label().lowercase()} members", "Members show up here when they match this filter.")
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
