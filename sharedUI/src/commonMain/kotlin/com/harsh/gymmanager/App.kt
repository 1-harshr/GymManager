package com.harsh.gymmanager

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.harsh.gymmanager.navigation.BulkRenewRoute
import com.harsh.gymmanager.navigation.ImportMembersRoute
import com.harsh.gymmanager.navigation.MainRoute
import com.harsh.gymmanager.navigation.MemberDetailRoute
import com.harsh.gymmanager.navigation.MemberEditRoute
import com.harsh.gymmanager.navigation.PlanEditRoute
import com.harsh.gymmanager.navigation.SellMembershipRoute
import com.harsh.gymmanager.navigation.SettingsRoute
import com.harsh.gymmanager.ui.LocalSnackbar
import com.harsh.gymmanager.ui.components.AkhadaSnackbarHost
import com.harsh.gymmanager.ui.screens.bulkrenew.BulkRenewScreen
import com.harsh.gymmanager.ui.screens.importmembers.ImportMembersScreen
import com.harsh.gymmanager.ui.screens.main.MainScreen
import com.harsh.gymmanager.ui.screens.memberdetail.MemberDetailScreen
import com.harsh.gymmanager.ui.screens.memberedit.MemberEditScreen
import com.harsh.gymmanager.ui.screens.planedit.PlanEditScreen
import com.harsh.gymmanager.ui.screens.sellmembership.SellMembershipScreen
import com.harsh.gymmanager.ui.screens.settings.SettingsScreen
import com.harsh.gymmanager.ui.theme.AkhadaTheme
import kotlinx.coroutines.launch

private const val PUSH_MS = 250

@Composable
fun App(dependencies: AppDependencies) {
    AkhadaTheme {
        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val nav = rememberNavController()
        fun toast(message: String) = scope.launch { snackbar.showSnackbar(message) }

        CompositionLocalProvider(LocalAppDependencies provides dependencies, LocalSnackbar provides snackbar) {
            Box(Modifier.fillMaxSize().background(AkhadaTheme.colors.bg)) {
                NavHost(
                    navController = nav,
                    startDestination = MainRoute,
                    enterTransition = { slideIntoContainer(SlideDirection.Start, tween(PUSH_MS)) },
                    exitTransition = { fadeOut(tween(PUSH_MS)) },
                    popEnterTransition = { fadeIn(tween(PUSH_MS)) },
                    popExitTransition = { slideOutOfContainer(SlideDirection.End, tween(PUSH_MS)) },
                ) {
                    composable<MainRoute> {
                        MainScreen(
                            onOpenMember = { nav.navigate(MemberDetailRoute(it)) },
                            onAddMember = { nav.navigate(MemberEditRoute()) },
                            onOpenPlan = { nav.navigate(PlanEditRoute(it)) },
                            onOpenSettings = { nav.navigate(SettingsRoute) },
                            onBulkRenew = { nav.navigate(BulkRenewRoute(it)) },
                            onImportMembers = { nav.navigate(ImportMembersRoute) },
                        )
                    }
                    composable<BulkRenewRoute> { entry ->
                        BulkRenewScreen(
                            memberIds = entry.toRoute<BulkRenewRoute>().ids,
                            onBack = { nav.popBackStack() },
                            onDone = { count -> nav.popBackStack(); toast(if (count == 1) "Renewed 1 member" else "Renewed $count members") },
                            onAddPlan = { nav.navigate(PlanEditRoute()) },
                        )
                    }
                    composable<ImportMembersRoute> {
                        ImportMembersScreen(
                            onBack = { nav.popBackStack() },
                            onImported = { count -> nav.popBackStack(); toast(if (count == 1) "Added 1 member" else "Added $count members") },
                        )
                    }
                    composable<MemberDetailRoute> { entry ->
                        val id = entry.toRoute<MemberDetailRoute>().memberId
                        MemberDetailScreen(
                            memberId = id,
                            onBack = { nav.popBackStack() },
                            onEdit = { nav.navigate(MemberEditRoute(id)) },
                            onSellMembership = { nav.navigate(SellMembershipRoute(id)) },
                        )
                    }
                    composable<MemberEditRoute> { entry ->
                        MemberEditScreen(
                            memberId = entry.toRoute<MemberEditRoute>().memberId,
                            onBack = { nav.popBackStack() },
                            onSaved = { id, isNew ->
                                if (isNew) {
                                    // Go straight to the new member so a membership can be sold next.
                                    nav.navigate(MemberDetailRoute(id)) { popUpTo<MemberEditRoute> { inclusive = true } }
                                    toast("Member added")
                                } else {
                                    nav.popBackStack()
                                    toast("Changes saved")
                                }
                            },
                        )
                    }
                    composable<SellMembershipRoute> { entry ->
                        SellMembershipScreen(
                            memberId = entry.toRoute<SellMembershipRoute>().memberId,
                            onBack = { nav.popBackStack() },
                            onSold = { nav.popBackStack(); toast("Membership saved") },
                            onAddPlan = { nav.navigate(PlanEditRoute()) },
                        )
                    }
                    composable<PlanEditRoute> { entry ->
                        PlanEditScreen(
                            planId = entry.toRoute<PlanEditRoute>().planId,
                            onBack = { nav.popBackStack() },
                            onSaved = { nav.popBackStack(); toast("Plan saved") },
                        )
                    }
                    composable<SettingsRoute> { SettingsScreen(onBack = { nav.popBackStack() }) }
                }
                // Sits above the floating tab bar.
                AkhadaSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 88.dp))
            }
        }
    }
}
