package com.harsh.gymmanager.ui.screens.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harsh.gymmanager.LocalAppDependencies
import com.harsh.gymmanager.domain.model.Plan
import com.harsh.gymmanager.presentation.dashboard.DashboardViewModel
import com.harsh.gymmanager.presentation.members.MembersViewModel
import com.harsh.gymmanager.presentation.plans.PlansViewModel
import com.harsh.gymmanager.ui.LocalSnackbar
import com.harsh.gymmanager.ui.components.AddButton
import com.harsh.gymmanager.ui.components.FloatingTabBar
import com.harsh.gymmanager.ui.components.TabItem
import com.harsh.gymmanager.ui.icons.AkhadaIcons
import com.harsh.gymmanager.ui.screens.home.HomeScreen
import com.harsh.gymmanager.ui.screens.members.MembersScreen
import com.harsh.gymmanager.ui.screens.plans.PlansScreen
import com.harsh.gymmanager.ui.theme.AkhadaSpace

private val MainTabs = listOf(
    TabItem("Home", AkhadaIcons.Home),
    TabItem("Members", AkhadaIcons.Users),
    TabItem("Plans", AkhadaIcons.Tag),
)
private const val HOME = 0
private const val MEMBERS = 1
private const val PLANS = 2

/** The three tabs under the floating tab bar. */
@Composable
fun MainScreen(
    onOpenMember: (Long) -> Unit,
    onAddMember: () -> Unit,
    onOpenPlan: (Long?) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val deps = LocalAppDependencies.current
    val snackbar = LocalSnackbar.current
    val dashboardVm = viewModel { DashboardViewModel(deps.observeDashboard) }
    val membersVm = viewModel { MembersViewModel(deps.memberUseCases) }
    val plansVm = viewModel { PlansViewModel(deps.planUseCases) }
    var tab by rememberSaveable { mutableIntStateOf(HOME) }

    // "Today" is read when lists load, so re-read it whenever the app comes back.
    LifecycleResumeEffect(Unit) {
        dashboardVm.refresh()
        membersVm.refresh()
        onPauseOrDispose { }
    }
    LaunchedEffect(plansVm) { plansVm.messages.collect { snackbar.showSnackbar(it) } }

    Box(Modifier.fillMaxSize()) {
        Crossfade(tab, label = "tab") { current ->
            when (current) {
                HOME -> HomeScreen(
                    state = dashboardVm.state.collectAsStateWithLifecycle().value,
                    onOpenMembers = { filter ->
                        membersVm.onQueryChange("")
                        membersVm.onFilterChange(filter)
                        tab = MEMBERS
                    },
                    onOpenMember = { onOpenMember(it.memberId) },
                    onOpenSettings = onOpenSettings,
                )
                MEMBERS -> MembersScreen(
                    state = membersVm.state.collectAsStateWithLifecycle().value,
                    onQueryChange = membersVm::onQueryChange,
                    onFilterChange = membersVm::onFilterChange,
                    onOpenMember = { onOpenMember(it.memberId) },
                    onAddMember = onAddMember,
                    onOpenSettings = onOpenSettings,
                )
                else -> PlansScreen(
                    state = plansVm.state.collectAsStateWithLifecycle().value,
                    onShowArchivedChange = plansVm::onShowArchivedChange,
                    onOpenPlan = { plan: Plan -> onOpenPlan(plan.id) },
                    onAddPlan = { onOpenPlan(null) },
                    onArchive = { plansVm.archive(it.id) },
                    onRestore = { plansVm.restore(it.id) },
                    onOpenSettings = onOpenSettings,
                )
            }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = AkhadaSpace.md),
            horizontalArrangement = Arrangement.spacedBy(AkhadaSpace.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FloatingTabBar(tabs = MainTabs, selected = tab, onSelect = { tab = it })
            AnimatedVisibility(tab == MEMBERS, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                AddButton(onClick = onAddMember, contentDescription = "Add member")
            }
        }
    }
}
