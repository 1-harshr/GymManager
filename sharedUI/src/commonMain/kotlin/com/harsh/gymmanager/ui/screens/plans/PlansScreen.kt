package com.harsh.gymmanager.ui.screens.plans

import com.harsh.gymmanager.ui.components.LoadingMark
import com.harsh.gymmanager.ui.components.MyGymSwitch
import gymmanager.sharedui.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.pluralStringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.harsh.gymmanager.domain.model.Plan
import com.harsh.gymmanager.presentation.plans.PlansUiState
import com.harsh.gymmanager.ui.components.MyGymButton
import com.harsh.gymmanager.ui.components.MyGymIconButton
import com.harsh.gymmanager.ui.components.ButtonKind
import com.harsh.gymmanager.ui.components.EmptyState
import com.harsh.gymmanager.ui.components.MenuAction
import com.harsh.gymmanager.ui.components.MoreMenu
import com.harsh.gymmanager.ui.components.PlanCard
import com.harsh.gymmanager.ui.components.SelectionHeader
import com.harsh.gymmanager.ui.components.TabHeader
import com.harsh.gymmanager.ui.icons.MyGymIcons
import com.harsh.gymmanager.ui.theme.MyGymSize
import com.harsh.gymmanager.ui.theme.MyGymSpace
import com.harsh.gymmanager.ui.theme.MyGymTheme
import com.harsh.gymmanager.ui.theme.screenGutter

@Composable
fun PlansScreen(
    state: PlansUiState,
    onShowArchivedChange: (Boolean) -> Unit,
    /** False for staff, who see the plans but can't change them. */
    canEdit: Boolean,
    onOpenPlan: (Plan) -> Unit,
    onAddPlan: () -> Unit,
    onArchive: (Plan) -> Unit,
    onRestore: (Plan) -> Unit,
    onOpenSettings: () -> Unit,
    onToggleSelection: (Plan) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
) {
    val colors = MyGymTheme.colors
    val activeCount = state.plans.count { !it.isArchived }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = MyGymSize.contentBottomClearance),
        verticalArrangement = Arrangement.spacedBy(MyGymSpace.md),
    ) {
        item {
            if (state.isSelecting) {
                SelectionHeader(state.selectedIds.size, onCancel = onClearSelection, onSelectAll = onSelectAll)
            } else {
                TabHeader(stringResource(Res.string.tab_plans), if (state.isLoading) null else pluralStringResource(Res.plurals.plans_count, activeCount, activeCount)) {
                    MyGymIconButton(MyGymIcons.Settings, stringResource(Res.string.settings), onOpenSettings)
                }
            }
        }
        if (state.isLoading) {
            item { LoadingMark(Modifier.fillParentMaxHeight(0.5f)) }
        } else if (state.plans.isEmpty() && !state.showArchived) {
            item {
                EmptyState(
                    MyGymIcons.Tag, stringResource(Res.string.no_plans_title), stringResource(Res.string.no_plans_body),
                    actionLabel = if (canEdit) stringResource(Res.string.add_plan) else null, onAction = onAddPlan,
                )
            }
        } else {
            item {
                Row(Modifier.fillMaxWidth().screenGutter(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.show_archived_plans), Modifier.weight(1f), style = MyGymTheme.typography.body, color = colors.inkMuted)
                    MyGymSwitch(
                        checked = state.showArchived,
                        onCheckedChange = onShowArchivedChange,
                    )
                }
            }
            items(state.plans, key = { it.id }) { plan ->
                PlanCard(
                    plan,
                    onClick = {
                        when {
                            state.isSelecting -> onToggleSelection(plan)
                            canEdit -> onOpenPlan(plan)
                        }
                    },
                    modifier = Modifier.screenGutter(),
                    selected = plan.id in state.selectedIds,
                    onLongClick = if (canEdit) ({ onToggleSelection(plan) }) else null,
                    trailing = if (state.isSelecting || !canEdit) null else ({
                        MoreMenu(
                            actions = listOf(
                                MenuAction(stringResource(Res.string.edit)) { onOpenPlan(plan) },
                                if (plan.isArchived) MenuAction(stringResource(Res.string.restore)) { onRestore(plan) } else MenuAction(stringResource(Res.string.archive)) { onArchive(plan) },
                            ),
                            contentDescription = stringResource(Res.string.more_for, plan.name),
                            container = colors.surface,
                        )
                    }),
                )
            }
            if (!state.isSelecting && canEdit) item {
                MyGymButton(
                    stringResource(Res.string.add_plan), onAddPlan,
                    Modifier.fillMaxWidth().screenGutter().padding(top = MyGymSpace.sm),
                    kind = ButtonKind.Secondary, icon = MyGymIcons.Plus,
                )
            }
        }
    }
}
