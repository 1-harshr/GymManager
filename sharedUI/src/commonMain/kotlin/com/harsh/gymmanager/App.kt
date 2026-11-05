package com.harsh.gymmanager

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.harsh.gymmanager.domain.model.AppSettings
import com.harsh.gymmanager.domain.model.ThemeMode
import com.harsh.gymmanager.navigation.BulkRenewRoute
import com.harsh.gymmanager.navigation.GymProfileRoute
import com.harsh.gymmanager.navigation.ImportMembersRoute
import com.harsh.gymmanager.navigation.MainRoute
import com.harsh.gymmanager.navigation.MemberDetailRoute
import com.harsh.gymmanager.navigation.MemberEditRoute
import com.harsh.gymmanager.navigation.PlanEditRoute
import com.harsh.gymmanager.navigation.SellMembershipRoute
import com.harsh.gymmanager.navigation.SettingsRoute
import com.harsh.gymmanager.presentation.settings.SettingsViewModel
import com.harsh.gymmanager.ui.LocalGym
import com.harsh.gymmanager.ui.LocalLanguage
import com.harsh.gymmanager.ui.LocalSnackbar
import com.harsh.gymmanager.ui.components.AkhadaSnackbarHost
import com.harsh.gymmanager.ui.platform.LocalAppLocale
import com.harsh.gymmanager.ui.platform.SystemBarsFor
import com.harsh.gymmanager.ui.screens.bulkrenew.BulkRenewScreen
import com.harsh.gymmanager.ui.screens.importmembers.ImportMembersScreen
import com.harsh.gymmanager.ui.screens.main.MainScreen
import com.harsh.gymmanager.ui.screens.memberdetail.MemberDetailScreen
import com.harsh.gymmanager.ui.screens.memberedit.MemberEditScreen
import com.harsh.gymmanager.ui.screens.planedit.PlanEditScreen
import com.harsh.gymmanager.ui.screens.sellmembership.SellMembershipScreen
import com.harsh.gymmanager.ui.screens.settings.GymProfileScreen
import com.harsh.gymmanager.ui.screens.settings.SettingsScreen
import com.harsh.gymmanager.ui.theme.AkhadaTheme
import gymmanager.sharedui.generated.resources.Res
import gymmanager.sharedui.generated.resources.changes_saved
import gymmanager.sharedui.generated.resources.gym_saved
import gymmanager.sharedui.generated.resources.member_added
import gymmanager.sharedui.generated.resources.membership_saved
import gymmanager.sharedui.generated.resources.msg_added
import gymmanager.sharedui.generated.resources.msg_renewed
import gymmanager.sharedui.generated.resources.plan_saved
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

private const val PUSH_MS = 250

@Composable
fun App(dependencies: AppDependencies) {
    val settingsVm = viewModel { SettingsViewModel(dependencies.settingsUseCases) }
    val settings = settingsVm.state.collectAsStateWithLifecycle().value
    val dark = when (settings?.theme ?: ThemeMode.SYSTEM) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    SystemBarsFor(dark)
    // String resources follow the overridden configuration, so switching language keeps the current screen.
    val tag = settings?.language?.tag
    val language = tag ?: Locale.current.language
    CompositionLocalProvider(LocalAppLocale provides tag, LocalLanguage provides language) {
        AkhadaTheme(darkTheme = dark) {
            Box(Modifier.fillMaxSize().background(AkhadaTheme.colors.bg)) {
                // Wait for stored settings so first-run setup never flashes on later launches.
                if (settings != null) AppContent(dependencies, settings, settingsVm)
            }
        }
    }
}

@Composable
private fun AppContent(dependencies: AppDependencies, settings: AppSettings, settingsVm: SettingsViewModel) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val nav = rememberNavController()
    fun toast(res: StringResource) = scope.launch { snackbar.showSnackbar(getString(res)) }
    fun toast(res: PluralStringResource, count: Int) = scope.launch { snackbar.showSnackbar(getPluralString(res, count, count)) }
    val startDestination: Any = remember { if (settings.gym == null) GymProfileRoute else MainRoute }

    CompositionLocalProvider(
        LocalAppDependencies provides dependencies,
        LocalSnackbar provides snackbar,
        LocalGym provides (settings.gym ?: LocalGym.current),
    ) {
        Box(Modifier.fillMaxSize()) {
            NavHost(
                navController = nav,
                startDestination = startDestination,
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
                composable<GymProfileRoute> {
                    GymProfileScreen(
                        onBack = { nav.popBackStack() },
                        onSaved = { firstRun ->
                            if (firstRun) {
                                nav.navigate(MainRoute) { popUpTo<GymProfileRoute> { inclusive = true } }
                            } else {
                                nav.popBackStack()
                                toast(Res.string.gym_saved)
                            }
                        },
                    )
                }
                composable<BulkRenewRoute> { entry ->
                    BulkRenewScreen(
                        memberIds = entry.toRoute<BulkRenewRoute>().ids,
                        onBack = { nav.popBackStack() },
                        onDone = { count -> nav.popBackStack(); toast(Res.plurals.msg_renewed, count) },
                        onAddPlan = { nav.navigate(PlanEditRoute()) },
                    )
                }
                composable<ImportMembersRoute> {
                    ImportMembersScreen(
                        onBack = { nav.popBackStack() },
                        onImported = { count -> nav.popBackStack(); toast(Res.plurals.msg_added, count) },
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
                                toast(Res.string.member_added)
                            } else {
                                nav.popBackStack()
                                toast(Res.string.changes_saved)
                            }
                        },
                    )
                }
                composable<SellMembershipRoute> { entry ->
                    SellMembershipScreen(
                        memberId = entry.toRoute<SellMembershipRoute>().memberId,
                        onBack = { nav.popBackStack() },
                        onSold = { nav.popBackStack(); toast(Res.string.membership_saved) },
                        onAddPlan = { nav.navigate(PlanEditRoute()) },
                    )
                }
                composable<PlanEditRoute> { entry ->
                    PlanEditScreen(
                        planId = entry.toRoute<PlanEditRoute>().planId,
                        onBack = { nav.popBackStack() },
                        onSaved = { nav.popBackStack(); toast(Res.string.plan_saved) },
                    )
                }
                composable<SettingsRoute> {
                    SettingsScreen(
                        settings = settings,
                        onBack = { nav.popBackStack() },
                        onEditGym = { nav.navigate(GymProfileRoute) },
                        onThemeChange = settingsVm::onThemeChange,
                        onLanguageChange = settingsVm::onLanguageChange,
                    )
                }
            }
            // Sits above the floating tab bar.
            AkhadaSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 88.dp))
        }
    }
}
