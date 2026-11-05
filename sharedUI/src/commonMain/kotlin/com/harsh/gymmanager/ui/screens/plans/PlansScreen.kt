package com.harsh.gymmanager.ui.screens.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.harsh.gymmanager.domain.model.Plan
import com.harsh.gymmanager.presentation.plans.PlansUiState
import com.harsh.gymmanager.ui.components.AkhadaButton
import com.harsh.gymmanager.ui.components.AkhadaIconButton
import com.harsh.gymmanager.ui.components.ButtonKind
import com.harsh.gymmanager.ui.components.EmptyState
import com.harsh.gymmanager.ui.components.MenuAction
import com.harsh.gymmanager.ui.components.MoreMenu
import com.harsh.gymmanager.ui.components.PlanCard
import com.harsh.gymmanager.ui.components.SelectionHeader
import com.harsh.gymmanager.ui.components.TabHeader
import com.harsh.gymmanager.ui.icons.AkhadaIcons
import com.harsh.gymmanager.ui.theme.AkhadaSize
import com.harsh.gymmanager.ui.theme.AkhadaSpace
import com.harsh.gymmanager.ui.theme.AkhadaTheme
import com.harsh.gymmanager.ui.theme.screenGutter

@Composable
fun PlansScreen(
    state: PlansUiState,
    onShowArchivedChange: (Boolean) -> Unit,
    onOpenPlan: (Plan) -> Unit,
    onAddPlan: () -> Unit,
    onArchive: (Plan) -> Unit,
    onRestore: (Plan) -> Unit,
    onOpenSettings: () -> Unit,
    onToggleSelection: (Plan) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
) {
    val colors = AkhadaTheme.colors
    val activeCount = state.plans.count { !it.isArchived }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = AkhadaSize.contentBottomClearance),
        verticalArrangement = Arrangement.spacedBy(AkhadaSpace.md),
    ) {
        item {
            if (state.isSelecting) {
                SelectionHeader(state.selectedIds.size, onCancel = onClearSelection, onSelectAll = onSelectAll)
            } else {
                TabHeader("Plans", if (state.isLoading) null else if (activeCount == 1) "1 plan" else "$activeCount plans") {
                    AkhadaIconButton(AkhadaIcons.Settings, "Settings", onOpenSettings)
                }
            }
        }
        if (!state.isLoading && state.plans.isEmpty() && !state.showArchived) {
            item {
                EmptyState(
                    AkhadaIcons.Tag, "No plans yet", "Add the plans you sell, like Gym · Monthly for ₹800.",
                    actionLabel = "Add plan", onAction = onAddPlan,
                )
            }
        } else {
            item {
                Row(Modifier.fillMaxWidth().screenGutter(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Show archived plans", Modifier.weight(1f), style = AkhadaTheme.typography.body, color = colors.inkMuted)
                    Switch(
                        checked = state.showArchived,
                        onCheckedChange = onShowArchivedChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.onInk, checkedTrackColor = colors.ink,
                            uncheckedThumbColor = colors.inkMuted, uncheckedTrackColor = colors.surfaceSunken, uncheckedBorderColor = colors.surfaceSunken,
                        ),
                    )
                }
            }
            items(state.plans, key = { it.id }) { plan ->
                PlanCard(
                    plan,
                    onClick = { if (state.isSelecting) onToggleSelection(plan) else onOpenPlan(plan) },
                    modifier = Modifier.screenGutter(),
                    selected = plan.id in state.selectedIds,
                    onLongClick = { onToggleSelection(plan) },
                    trailing = if (state.isSelecting) null else ({
                        MoreMenu(
                            actions = listOf(
                                MenuAction("Edit") { onOpenPlan(plan) },
                                if (plan.isArchived) MenuAction("Restore") { onRestore(plan) } else MenuAction("Archive") { onArchive(plan) },
                            ),
                            contentDescription = "More for ${plan.name}",
                            container = colors.surface,
                        )
                    }),
                )
            }
            if (!state.isSelecting) item {
                AkhadaButton(
                    "Add plan", onAddPlan,
                    Modifier.fillMaxWidth().screenGutter().padding(top = AkhadaSpace.sm),
                    kind = ButtonKind.Secondary, icon = AkhadaIcons.Plus,
                )
            }
        }
    }
}
