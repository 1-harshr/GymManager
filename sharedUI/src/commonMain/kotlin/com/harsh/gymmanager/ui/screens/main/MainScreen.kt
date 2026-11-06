package com.harsh.gymmanager.ui.screens.main

import com.harsh.gymmanager.ui.isOwnerOrNoAccount
import gymmanager.sharedui.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.pluralStringResource
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
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
import com.harsh.gymmanager.ui.components.SystemTabBar
import com.harsh.gymmanager.ui.components.usesLiquidGlass
import com.harsh.gymmanager.ui.components.GlassCapsuleHeight
import com.harsh.gymmanager.ui.components.GlassCapsuleLift
import com.harsh.gymmanager.ui.components.glassCapsuleBottom
import com.harsh.gymmanager.ui.components.TabItem
import com.harsh.gymmanager.ui.format.resolve
import com.harsh.gymmanager.ui.icons.MyGymIcons
import com.harsh.gymmanager.ui.motion.MyGymMotion
import com.harsh.gymmanager.ui.platform.SystemBackHandler
import com.harsh.gymmanager.domain.model.HomeFeatures
import com.harsh.gymmanager.ui.screens.home.HomeScreen
import com.harsh.gymmanager.ui.screens.members.MembersScreen
import com.harsh.gymmanager.ui.screens.plans.PlansScreen
import com.harsh.gymmanager.ui.theme.MyGymSpace
import kotlin.math.roundToInt


/** Matches the tab pill's spring so the bar and pill move together. */
private val BarSpring = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntSize(1, 1))

private const val HOME = 0
private const val MEMBERS = 1
private const val PLANS = 2

/** How many payments Home lists. */
private const val RECENT_PAYMENTS = 5

/** The three tabs under the tab bar (the system one on iOS); select mode swaps the bar for bulk actions. */
@Composable
fun MainScreen(
    onOpenMember: (String) -> Unit,
    onAddMember: () -> Unit,
    onOpenPlan: (String?) -> Unit,
    onOpenSettings: () -> Unit,
    onBulkRenew: (Set<String>) -> Unit,
    onImportMembers: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenInbox: () -> Unit,
    homeFeatures: HomeFeatures,
    membersAsGrid: Boolean,
    onMembersAsGridChange: (Boolean) -> Unit,
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
    val isOwner = isOwnerOrNoAccount
    val mainTabs = listOf(
        TabItem(stringResource(Res.string.tab_home), MyGymIcons.Home, symbol = "house.fill"),
        TabItem(stringResource(Res.string.tab_members), MyGymIcons.Users, symbol = "person.2.fill"),
        TabItem(stringResource(Res.string.tab_plans), MyGymIcons.Tag, symbol = "tag.fill"),
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
    // Back from another tab returns Home before it leaves the app.
    SystemBackHandler(enabled = !selecting && tab != HOME) { tab = HOME }

    // The tab bar slides off the bottom while the list scrolls down and comes back on any scroll up.
    // Select mode keeps its bar in place.
    val density = LocalDensity.current
    val barScroll = remember(density) { HideOnScroll(with(density) { 24.dp.toPx() }) }
    LaunchedEffect(tab, selecting) { barScroll.show() }
    val barShift by animateFloatAsState(
        if (barScroll.hidden && !selecting) 1f else 0f,
        spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
        label = "tab bar",
    )
    var barHeight by remember { mutableIntStateOf(0) }
    val barGap = WindowInsets.navigationBars.getBottom(density) + with(density) { MyGymSpace.md.roundToPx() }
    val slidesAway = Modifier
        .onSizeChanged { barHeight = it.height }
        .offset { IntOffset(0, (barShift * (barHeight + barGap)).roundToInt()) }

    Box(Modifier.fillMaxSize().nestedScroll(barScroll)) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                // Tabs sit side by side, so the new one comes from the side it lives on.
                val forward = targetState > initialState
                val tabSpec = tween<IntOffset>(MyGymMotion.TAB_MS, easing = MyGymMotion.Settle)
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
                    asGrid = membersAsGrid,
                    onAsGridChange = onMembersAsGridChange,
                )
                else -> PlansScreen(
                    state = plansState,
                    onShowArchivedChange = plansVm::onShowArchivedChange,
                    canEdit = isOwner,
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
        if (usesLiquidGlass && !selecting) {
            // iOS 26 centres the glass capsule in the bar's frame, so the add button takes the trailing end
            // and the capsule slides aside as it appears, like a tab bar with a trailing button.
            Row(Modifier.align(Alignment.BottomCenter).then(slidesAway), verticalAlignment = Alignment.CenterVertically) {
                SystemTabBar(tabs = mainTabs, selected = tab, onSelect = { tab = it }, modifier = Modifier.weight(1f))
                AnimatedVisibility(
                    tab == MEMBERS,
                    enter = expandHorizontally(BarSpring, expandFrom = Alignment.Start),
                    exit = shrinkHorizontally(BarSpring, shrinkTowards = Alignment.Start),
                ) {
                    AddButton(
                        onClick = onAddMember,
                        contentDescription = stringResource(Res.string.add_member),
                        // Centred on the capsule rather than on the bar's frame.
                        modifier = Modifier.padding(end = MyGymSpace.gutter, bottom = GlassCapsuleLift * 2),
                        size = GlassCapsuleHeight,
                    )
                }
            }
        }
        AnimatedContent(
            targetState = selecting,
            // On iOS 26 the select-mode bar takes the tab bar capsule's exact place.
            modifier = Modifier.align(Alignment.BottomCenter).then(
                if (usesLiquidGlass) Modifier.padding(bottom = glassCapsuleBottom) else Modifier.navigationBarsPadding().padding(bottom = MyGymSpace.md),
            ),
            transitionSpec = {
                (fadeIn() + slideInVertically { it / 2 }) togetherWith (fadeOut() + slideOutVertically { it / 2 })
            },
            label = "bottom bar",
        ) { isSelecting ->
            if (isSelecting) {
                BulkActionBar(
                    if (tab == MEMBERS) memberActions(
                        archivedList = membersState.filter == MemberFilter.ARCHIVED,
                        isOwner = isOwner,
                        onRenew = { onBulkRenew(membersState.selectedIds); membersVm.clearSelection() },
                        onArchive = membersVm::archiveSelected,
                        onRestore = membersVm::restoreSelected,
                        onDelete = { confirmDelete = true },
                    ) else if (!isOwner) emptyList() else listOfNotNull(
                        if (plansState.selectedHasActive) BulkAction(stringResource(Res.string.archive), MyGymIcons.Archive, onClick = plansVm::archiveSelected) else null,
                        if (plansState.selectedHasArchived) BulkAction(stringResource(Res.string.restore), MyGymIcons.Restore, onClick = plansVm::restoreSelected) else null,
                    ),
                )
            } else if (!usesLiquidGlass) {
                Row(slidesAway, verticalAlignment = Alignment.CenterVertically) {
                    FloatingTabBar(tabs = mainTabs, selected = tab, onSelect = { tab = it })
                    // The button's width (gap included) grows on the same spring as the tab pill, so the
                    // centred bar glides aside instead of jumping when the button appears.
                    AnimatedVisibility(
                        tab == MEMBERS,
                        enter = expandHorizontally(BarSpring, expandFrom = Alignment.Start) + fadeIn() + scaleIn(initialScale = 0.6f),
                        exit = shrinkHorizontally(BarSpring, shrinkTowards = Alignment.Start) + fadeOut() + scaleOut(targetScale = 0.6f),
                    ) {
                        AddButton(onClick = onAddMember, contentDescription = stringResource(Res.string.add_member), modifier = Modifier.padding(start = MyGymSpace.md))
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

/**
 * Says whether the tab bar should be out of the way: after scrolling down a little way, and not again
 * until a scroll back up, or a pull at the top of a list that can't scroll.
 */
private class HideOnScroll(private val threshold: Float) : NestedScrollConnection {
    var hidden by mutableStateOf(false)
        private set

    /** How far the list has moved in its current direction: below zero going down. */
    private var travel = 0f

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        val dy = consumed.y
        if (dy == 0f) {
            if (available.y > 0f) show()
            return Offset.Zero
        }
        travel = if ((dy < 0f) == (travel < 0f)) travel + dy else dy
        if (travel < -threshold) hidden = true else if (travel > threshold / 3) hidden = false
        return Offset.Zero
    }

    fun show() {
        hidden = false
        travel = 0f
    }
}

@Composable
private fun memberActions(
    archivedList: Boolean,
    isOwner: Boolean,
    onRenew: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
): List<BulkAction> = if (!isOwner) {
    // Staff renew; archiving and deleting are the owner's.
    if (archivedList) emptyList() else listOf(BulkAction(stringResource(Res.string.renew), MyGymIcons.Renew, onClick = onRenew))
} else if (archivedList) {
    listOf(
        BulkAction(stringResource(Res.string.restore), MyGymIcons.Restore, onClick = onRestore),
        BulkAction(stringResource(Res.string.delete), MyGymIcons.Trash, destructive = true, onClick = onDelete),
    )
} else {
    listOf(
        BulkAction(stringResource(Res.string.renew), MyGymIcons.Renew, onClick = onRenew),
        BulkAction(stringResource(Res.string.archive), MyGymIcons.Archive, onClick = onArchive),
        BulkAction(stringResource(Res.string.delete), MyGymIcons.Trash, destructive = true, onClick = onDelete),
    )
}
