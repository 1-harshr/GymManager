package com.harsh.gymmanager.ui.screens.main

import gymmanager.sharedui.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.pluralStringResource
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harsh.gymmanager.LocalAppDependencies
import com.harsh.gymmanager.domain.model.Plan
import com.harsh.gymmanager.domain.usecase.MemberFilter
import com.harsh.gymmanager.presentation.dashboard.DashboardViewModel
import com.harsh.gymmanager.presentation.members.MembersViewModel
import com.harsh.gymmanager.presentation.plans.PlansViewModel
import com.harsh.gymmanager.ui.LocalSnackbar
import com.harsh.gymmanager.ui.components.AddButton
import com.harsh.gymmanager.ui.components.BulkAction
import com.harsh.gymmanager.ui.components.BulkActionBar
import com.harsh.gymmanager.ui.components.ConfirmSheet
import com.harsh.gymmanager.ui.components.FloatingTabBar
import com.harsh.gymmanager.ui.components.TabItem
import com.harsh.gymmanager.ui.format.resolve
import com.harsh.gymmanager.ui.icons.AkhadaIcons
import com.harsh.gymmanager.ui.motion.AkhadaMotion
import com.harsh.gymmanager.ui.platform.SystemBackHandler
import com.harsh.gymmanager.domain.model.HomeFeatures
import com.harsh.gymmanager.ui.screens.home.HomeScreen
import com.harsh.gymmanager.ui.screens.members.MembersScreen
import com.harsh.gymmanager.ui.screens.plans.PlansScreen
import com.harsh.gymmanager.ui.theme.AkhadaSpace


/** Matches the tab pill's spring so the bar and pill move together. */
private val BarSpring = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntSize(1, 1))

private const val HOME = 0
private const val MEMBERS = 1
private const val PLANS = 2

/** How many payments Home lists. */
private const val RECENT_PAYMENTS = 5

/** The three tabs under the floating tab bar; select mode swaps the bar for bulk actions. */
@Composable
fun MainScreen(
    onOpenMember: (Long) -> Unit,
    onAddMember: () -> Unit,
    onOpenPlan: (Long?) -> Unit,
    onOpenSettings: () -> Unit,
    onBulkRenew: (Set<Long>) -> Unit,
    onImportMembers: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenInbox: () -> Unit,
    homeFeatures: HomeFeatures,
) {
    val deps = LocalAppDependencies.current
    val snackbar = LocalSnackbar.current
    val dashboardVm = viewModel {
        DashboardViewModel(
            deps.observeDashboard,
            remindersToSend = deps.messaging::observeRemindersToSend,
            recentPayments = { deps.paymentUseCases.observePayments.recent(RECENT_PAYMENTS) },
            insights = { deps.insights() },
            unreadAlerts = deps.inbox::observeUnreadCount,
        )
    }
    val membersVm = viewModel { MembersViewModel(deps.memberUseCases) }
    val plansVm = viewModel { PlansViewModel(deps.planUseCases) }
    var tab by rememberSaveable { mutableIntStateOf(HOME) }
    var confirmDelete by remember { mutableStateOf(false) }
    val mainTabs = listOf(
        TabItem(stringResource(Res.string.tab_home), AkhadaIcons.Home),
        TabItem(stringResource(Res.string.tab_members), AkhadaIcons.Users),
        TabItem(stringResource(Res.string.tab_plans), AkhadaIcons.Tag),
    )

    val membersState by membersVm.state.collectAsStateWithLifecycle()
    val plansState by plansVm.state.collectAsStateWithLifecycle()
    val selecting = (tab == MEMBERS && membersState.isSelecting) || (tab == PLANS && plansState.isSelecting)

    // "Today" is read when lists load, so re-read it whenever the app comes back.
    // Alerts come from the data, so look for new ones whenever the app comes back too.
    val scope = rememberCoroutineScope()
    LifecycleResumeEffect(Unit) {
        dashboardVm.refresh()
        membersVm.refresh()
        scope.launch { deps.inbox.refresh() }
        onPauseOrDispose { }
    }
    LaunchedEffect(plansVm) { plansVm.messages.collect { snackbar.showSnackbar(it.resolve()) } }
    LaunchedEffect(membersVm) { membersVm.messages.collect { snackbar.showSnackbar(it.resolve()) } }
    SystemBackHandler(enabled = selecting) {
        membersVm.clearSelection()
        plansVm.clearSelection()
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                // Tabs sit side by side, so the new one comes from the side it lives on.
                val forward = targetState > initialState
                val tabSpec = tween<IntOffset>(AkhadaMotion.TAB_MS, easing = AkhadaMotion.Settle)
                (slideInHorizontally(tabSpec) { if (forward) it / 8 else -it / 8 } + fadeIn(tween(220, delayMillis = 60))) togetherWith
                    (slideOutHorizontally(tabSpec) { if (forward) -it / 8 else it / 8 } + fadeOut(tween(140)))
            },
            label = "tab",
        ) { current ->
            when (current) {
                HOME -> HomeScreen(
                    state = dashboardVm.state.collectAsStateWithLifecycle().value,
                    onOpenMember = onOpenMember,
                    onOpenSettings = onOpenSettings,
                    onOpenReminders = onOpenReminders,
                    onOpenInbox = onOpenInbox,
                    features = homeFeatures,
                )
                MEMBERS -> MembersScreen(
                    state = membersState,
                    onQueryChange = membersVm::onQueryChange,
                    onFilterChange = membersVm::onFilterChange,
                    onOpenMember = { onOpenMember(it.memberId) },
                    onAddMember = onAddMember,
                    onOpenSettings = onOpenSettings,
                    onToggleSelection = { membersVm.toggleSelection(it.memberId) },
                    onSelectAll = membersVm::selectAll,
                    onClearSelection = membersVm::clearSelection,
                    onImport = onImportMembers,
                )
                else -> PlansScreen(
                    state = plansState,
                    onShowArchivedChange = plansVm::onShowArchivedChange,
                    onOpenPlan = { plan: Plan -> onOpenPlan(plan.id) },
                    onAddPlan = { onOpenPlan(null) },
                    onArchive = { plansVm.archive(it.id) },
                    onRestore = { plansVm.restore(it.id) },
                    onOpenSettings = onOpenSettings,
                    onToggleSelection = { plansVm.toggleSelection(it.id) },
                    onSelectAll = plansVm::selectAll,
                    onClearSelection = plansVm::clearSelection,
                )
            }
        }
        AnimatedContent(
            targetState = selecting,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = AkhadaSpace.md),
            transitionSpec = {
                (fadeIn() + slideInVertically { it / 2 }) togetherWith (fadeOut() + slideOutVertically { it / 2 })
            },
            label = "bottom bar",
        ) { isSelecting ->
            if (isSelecting) {
                BulkActionBar(
                    if (tab == MEMBERS) memberActions(
                        archivedList = membersState.filter == MemberFilter.ARCHIVED,
                        onRenew = { onBulkRenew(membersState.selectedIds); membersVm.clearSelection() },
                        onArchive = membersVm::archiveSelected,
                        onRestore = membersVm::restoreSelected,
                        onDelete = { confirmDelete = true },
                    ) else listOfNotNull(
                        if (plansState.selectedHasActive) BulkAction(stringResource(Res.string.archive), AkhadaIcons.Archive, onClick = plansVm::archiveSelected) else null,
                        if (plansState.selectedHasArchived) BulkAction(stringResource(Res.string.restore), AkhadaIcons.Restore, onClick = plansVm::restoreSelected) else null,
                    ),
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FloatingTabBar(tabs = mainTabs, selected = tab, onSelect = { tab = it })
                    // The button's width (gap included) grows on the same spring as the tab pill, so the
                    // centred bar glides aside instead of jumping when the button appears.
                    AnimatedVisibility(
                        tab == MEMBERS,
                        enter = expandHorizontally(BarSpring, expandFrom = Alignment.Start) + fadeIn() + scaleIn(initialScale = 0.6f),
                        exit = shrinkHorizontally(BarSpring, shrinkTowards = Alignment.Start) + fadeOut() + scaleOut(targetScale = 0.6f),
                    ) {
                        AddButton(onClick = onAddMember, contentDescription = stringResource(Res.string.add_member), modifier = Modifier.padding(start = AkhadaSpace.md))
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        val count = membersState.selectedIds.size
        ConfirmSheet(
            title = pluralStringResource(Res.plurals.delete_members_title, count, count),
            body = stringResource(Res.string.delete_members_body),
            confirmLabel = pluralStringResource(Res.plurals.delete_members_button, count, count),
            onConfirm = { confirmDelete = false; membersVm.deleteSelected() },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun memberActions(
    archivedList: Boolean,
    onRenew: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
): List<BulkAction> = if (archivedList) {
    listOf(
        BulkAction(stringResource(Res.string.restore), AkhadaIcons.Restore, onClick = onRestore),
        BulkAction(stringResource(Res.string.delete), AkhadaIcons.Trash, destructive = true, onClick = onDelete),
    )
} else {
    listOf(
        BulkAction(stringResource(Res.string.renew), AkhadaIcons.Renew, onClick = onRenew),
        BulkAction(stringResource(Res.string.archive), AkhadaIcons.Archive, onClick = onArchive),
        BulkAction(stringResource(Res.string.delete), AkhadaIcons.Trash, destructive = true, onClick = onDelete),
    )
}
