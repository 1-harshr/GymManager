package com.harsh.gymmanager

import gymmanager.sharedui.generated.resources.demo_exit
import gymmanager.sharedui.generated.resources.demo_banner
import org.jetbrains.compose.resources.stringResource
import com.harsh.gymmanager.ui.theme.MyGymSpace
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.ViewModelStore
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.toRoute
import com.harsh.gymmanager.domain.model.AppSettings
import com.harsh.gymmanager.domain.repository.AuthRepository
import com.harsh.gymmanager.domain.repository.AuthState
import com.harsh.gymmanager.ui.screens.signin.SignInScreen
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
import com.harsh.gymmanager.navigation.RemindersRoute
import com.harsh.gymmanager.navigation.InboxRoute
import com.harsh.gymmanager.ui.screens.inbox.InboxScreen
import com.harsh.gymmanager.ui.screens.reminders.RemindersScreen
import com.harsh.gymmanager.ui.messaging.WhatsAppSheet
import com.harsh.gymmanager.ui.LocalWhatsApp
import com.harsh.gymmanager.presentation.messaging.WhatsAppPromptViewModel
import com.harsh.gymmanager.presentation.messaging.MessageRequest
import com.harsh.gymmanager.ui.LocalGym
import com.harsh.gymmanager.ui.LocalLanguage
import com.harsh.gymmanager.ui.LocalSnackbar
import com.harsh.gymmanager.ui.components.MyGymSnackbarHost
import com.harsh.gymmanager.ui.motion.MyGymMotion
import com.harsh.gymmanager.ui.motion.Destination
import com.harsh.gymmanager.ui.motion.LocalSharedTransitionScope
import com.harsh.gymmanager.ui.platform.LocalAppLocale
import com.harsh.gymmanager.ui.platform.SystemBarsFor
import com.harsh.gymmanager.ui.platform.rebuildUiOnLanguageChange
import com.harsh.gymmanager.ui.screens.bulkrenew.BulkRenewScreen
import com.harsh.gymmanager.ui.screens.importmembers.ImportMembersScreen
import com.harsh.gymmanager.ui.screens.main.MainScreen
import com.harsh.gymmanager.ui.screens.memberdetail.MemberDetailScreen
import com.harsh.gymmanager.ui.screens.memberedit.MemberEditScreen
import com.harsh.gymmanager.ui.screens.planedit.PlanEditScreen
import com.harsh.gymmanager.ui.screens.sellmembership.SellMembershipScreen
import com.harsh.gymmanager.ui.screens.settings.GymProfileScreen
import com.harsh.gymmanager.ui.screens.settings.SettingsScreen
import com.harsh.gymmanager.ui.screens.settings.WhatsAppScreen
import com.harsh.gymmanager.ui.screens.settings.autoSentText
import com.harsh.gymmanager.navigation.WhatsAppRoute
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.harsh.gymmanager.ui.theme.MyGymTheme
import gymmanager.sharedui.generated.resources.Res
import gymmanager.sharedui.generated.resources.changes_saved
import gymmanager.sharedui.generated.resources.gym_saved
import gymmanager.sharedui.generated.resources.member_added
import gymmanager.sharedui.generated.resources.membership_saved
import gymmanager.sharedui.generated.resources.msg_added
import gymmanager.sharedui.generated.resources.msg_renewed
import gymmanager.sharedui.generated.resources.plan_saved
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

/**
 * The app on the gym's real data, or on sample data while [demoMode] runs a demo. Switching
 * rebuilds every screen with fresh view models, so nothing keeps hold of the other data; a demo is
 * closed and deleted once its screens are gone. The real data needs someone signed in with [auth];
 * a demo doesn't.
 */
@Composable
fun GymManagerApp(real: AppDependencies, demoMode: DemoMode, auth: AuthRepository, onReady: () -> Unit = {}) {
    val demo = demoMode.active
    key(demo) {
        val owner = remember { object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() } }
        DisposableEffect(Unit) {
            onDispose {
                owner.viewModelStore.clear()
                demo?.let(demoMode::release)
            }
        }
        CompositionLocalProvider(LocalViewModelStoreOwner provides owner, LocalDemoMode provides demoMode) {
            App(demo ?: real, auth.takeIf { demo == null }, onReady)
        }
    }
}

/** [auth] null shows the app without anyone signed in, as in demo mode. */
@Composable
fun App(dependencies: AppDependencies, auth: AuthRepository? = null, onReady: () -> Unit = {}) {
    val settingsVm = viewModel { SettingsViewModel(dependencies.settingsUseCases) }
    val settings = settingsVm.state.collectAsStateWithLifecycle().value
    val authState = auth?.authState?.collectAsStateWithLifecycle()?.value
    val scope = rememberCoroutineScope()
    val dark = when (settings?.theme ?: ThemeMode.SYSTEM) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val inDemo = LocalDemoMode.current?.active != null
    // The demo banner is clay, so the status bar icons go light on it.
    SystemBarsFor(dark || inDemo)
    // Hold the launch screen until both the settings and any saved sign-in are read, so neither screen flashes.
    if (settings != null && authState !is AuthState.Loading) LaunchedEffect(Unit) { onReady() }
    // String resources follow the overridden configuration, so switching language keeps the current screen.
    val tag = settings?.language?.tag
    val language = tag ?: Locale.current.language
    CompositionLocalProvider(LocalAppLocale provides tag, LocalLanguage provides language) {
        val content: @Composable () -> Unit = {
            MyGymTheme(darkTheme = dark) {
                Column(Modifier.fillMaxSize().background(MyGymTheme.colors.bg)) {
                    if (inDemo) DemoBanner()
                    // Below the banner, screens don't need their own room for the status bar.
                    Box(Modifier.weight(1f).then(if (inDemo) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier)) {
                        // Wait for stored settings so first-run setup never flashes on later launches.
                        if (settings != null) {
                            if (auth == null) AppContent(dependencies, settings, settingsVm) else when (authState) {
                                null, AuthState.Loading -> Unit
                                AuthState.SignedOut -> SignInScreen(auth)
                                // A different person signing in starts again from the first screen.
                                is AuthState.SignedIn -> key(authState.userId) {
                                    AppContent(dependencies, settings, settingsVm, authState, onSignOut = { scope.launch { auth.signOut() } })
                                }
                            }
                        }
                    }
                }
            }
        }
        if (rebuildUiOnLanguageChange) key(language) { content() } else content()
    }
}

/** Lets a burst of edits settle before the notifications are rescheduled. */
private val MORNING_UPDATE_SYNC_DELAY = 500.milliseconds

@OptIn(ExperimentalSharedTransitionApi::class, FlowPreview::class)
@Composable
private fun AppContent(
    dependencies: AppDependencies,
    settings: AppSettings,
    settingsVm: SettingsViewModel,
    account: AuthState.SignedIn? = null,
    onSignOut: () -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val nav = rememberNavController()
    fun toast(res: StringResource) = scope.launch { snackbar.showSnackbar(getString(res)) }
    fun toast(res: PluralStringResource, count: Int) = scope.launch { snackbar.showSnackbar(getPluralString(res, count, count)) }
    val startDestination: Any = remember { if (settings.gym == null) GymProfileRoute else MainRoute }
    val whatsApp = viewModel { WhatsAppPromptViewModel(dependencies.messaging, dependencies.whatsApp) }
    val whatsAppPrompt by whatsApp.prompt.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(whatsApp) {
        whatsApp.autoSent.collect { snackbar.showSnackbar(autoSentText(it.kind, it.memberName)) }
    }
    // Each time the app comes to the front: send anything queued, and send today's reminders if
    // they're due (the only way they go out on iOS).
    LaunchedEffect(dependencies, lifecycle) {
        if (dependencies.whatsApp.isAvailable) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { dependencies.whatsApp.catchUp() }
        }
    }
    LaunchedEffect(dependencies) {
        dependencies.whatsApp.observeSettings().collectLatest { dependencies.whatsAppScheduler.sync(it) }
    }
    // Reschedule the morning update whenever settings or gym data change, so iOS's pre-written text stays true.
    LaunchedEffect(dependencies) {
        combine(dependencies.settingsUseCases.observe(), dependencies.observeDashboard()) { latest, _ -> latest }
            .debounce(MORNING_UPDATE_SYNC_DELAY)
            .collectLatest { dependencies.morningUpdates.sync(it) }
    }

    CompositionLocalProvider(
        LocalAppDependencies provides dependencies,
        LocalSnackbar provides snackbar,
        LocalGym provides (settings.gym ?: LocalGym.current),
        LocalWhatsApp provides whatsApp::offer,
    ) {
        Box(Modifier.fillMaxSize()) {
            SharedTransitionLayout {
                CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                    NavHost(
                        navController = nav,
                        startDestination = startDestination,
                        enterTransition = MyGymMotion.pushEnter,
                        exitTransition = MyGymMotion.pushExit,
                        popEnterTransition = MyGymMotion.popEnter,
                        popExitTransition = MyGymMotion.popExit,
                    ) {
                        composable<MainRoute>(
                            // Opening a member grows their page out of the list instead of sliding it in.
                            exitTransition = { if (targetState.destination.hasRoute<MemberDetailRoute>()) MyGymMotion.openExit else MyGymMotion.pushExit(this) },
                            popEnterTransition = { if (initialState.destination.hasRoute<MemberDetailRoute>()) MyGymMotion.closeEnter else MyGymMotion.popEnter(this) },
                        ) {
                            Destination {
                                MainScreen(
                                    onOpenMember = { nav.navigate(MemberDetailRoute(it)) },
                                    onAddMember = { nav.navigate(MemberEditRoute()) },
                                    onOpenPlan = { nav.navigate(PlanEditRoute(it)) },
                                    onOpenSettings = { nav.navigate(SettingsRoute) },
                                    onBulkRenew = { nav.navigate(BulkRenewRoute(it)) },
                                    onImportMembers = { nav.navigate(ImportMembersRoute) },
                                    onOpenReminders = { nav.navigate(RemindersRoute) },
                                    onOpenInbox = { nav.navigate(InboxRoute) },
                                    homeFeatures = settings.homeFeatures,
                                    membersAsGrid = settings.membersAsGrid,
                                    onMembersAsGridChange = settingsVm::onMembersAsGridChange,
                                )
                            }
                        }
                        composable<GymProfileRoute> {
                            Destination {
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
                        }
                        composable<BulkRenewRoute> { entry ->
                            Destination {
                                BulkRenewScreen(
                                    memberIds = entry.toRoute<BulkRenewRoute>().ids,
                                    onBack = { nav.popBackStack() },
                                    onDone = { count -> nav.popBackStack(); toast(Res.plurals.msg_renewed, count) },
                                    onAddPlan = { nav.navigate(PlanEditRoute()) },
                                )
                            }
                        }
                        composable<ImportMembersRoute> {
                            Destination {
                                ImportMembersScreen(
                                    onBack = { nav.popBackStack() },
                                    onImported = { count -> nav.popBackStack(); toast(Res.plurals.msg_added, count) },
                                )
                            }
                        }
                        composable<MemberDetailRoute>(
                            enterTransition = { if (initialState.destination.hasRoute<MainRoute>()) MyGymMotion.openEnter else MyGymMotion.pushEnter(this) },
                            popExitTransition = { if (targetState.destination.hasRoute<MainRoute>()) MyGymMotion.closeExit else MyGymMotion.popExit(this) },
                        ) { entry ->
                            Destination {
                                val id = entry.toRoute<MemberDetailRoute>().memberId
                                MemberDetailScreen(
                                    memberId = id,
                                    onBack = { nav.popBackStack() },
                                    onEdit = { nav.navigate(MemberEditRoute(id)) },
                                    onSellMembership = { nav.navigate(SellMembershipRoute(id)) },
                                )
                            }
                        }
                        composable<MemberEditRoute> { entry ->
                            Destination {
                                MemberEditScreen(
                                    memberId = entry.toRoute<MemberEditRoute>().memberId,
                                    onBack = { nav.popBackStack() },
                                    onSaved = { id, isNew ->
                                        if (isNew) {
                                            // Go straight to the new member so a membership can be sold next.
                                            nav.navigate(MemberDetailRoute(id)) { popUpTo<MemberEditRoute> { inclusive = true } }
                                            toast(Res.string.member_added)
                                            whatsApp.offer(MessageRequest.Welcome(id))
                                        } else {
                                            nav.popBackStack()
                                            toast(Res.string.changes_saved)
                                        }
                                    },
                                )
                            }
                        }
                        composable<SellMembershipRoute> { entry ->
                            Destination {
                                val memberId = entry.toRoute<SellMembershipRoute>().memberId
                                SellMembershipScreen(
                                    memberId = memberId,
                                    onBack = { nav.popBackStack() },
                                    onSold = { membershipId ->
                                        nav.popBackStack()
                                        toast(Res.string.membership_saved)
                                        whatsApp.offer(MessageRequest.Receipt(memberId, membershipId))
                                    },
                                    onAddPlan = { nav.navigate(PlanEditRoute()) },
                                )
                            }
                        }
                        composable<PlanEditRoute> { entry ->
                            Destination {
                                PlanEditScreen(
                                    planId = entry.toRoute<PlanEditRoute>().planId,
                                    onBack = { nav.popBackStack() },
                                    onSaved = { nav.popBackStack(); toast(Res.string.plan_saved) },
                                )
                            }
                        }
                        composable<RemindersRoute> {
                            Destination {
                                RemindersScreen(onBack = { nav.popBackStack() }, onOpenMember = { nav.navigate(MemberDetailRoute(it)) })
                            }
                        }
                        composable<InboxRoute> {
                            Destination {
                                InboxScreen(onBack = { nav.popBackStack() }, onOpenMember = { nav.navigate(MemberDetailRoute(it)) })
                            }
                        }
                        composable<SettingsRoute> {
                            Destination {
                                SettingsScreen(
                                    settings = settings,
                                    onBack = { nav.popBackStack() },
                                    onEditGym = { nav.navigate(GymProfileRoute) },
                                    onThemeChange = settingsVm::onThemeChange,
                                    onLanguageChange = settingsVm::onLanguageChange,
                                    onMorningUpdateChange = settingsVm::onMorningUpdateChange,
                                    onHomeFeaturesChange = settingsVm::onHomeFeaturesChange,
                                    onOpenWhatsApp = { nav.navigate(WhatsAppRoute) },
                                    account = account,
                                    onSignOut = onSignOut,
                                )
                            }
                        }
                        composable<WhatsAppRoute> {
                            Destination {
                                WhatsAppScreen(onBack = { nav.popBackStack() })
                            }
                        }
                    }
                }
            }
            whatsAppPrompt?.let { prompt ->
                WhatsAppSheet(prompt, onSelect = whatsApp::select, onSent = whatsApp::sent, onDismiss = whatsApp::dismiss)
            }
            // Sits above the floating tab bar.
            MyGymSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 88.dp))
        }
    }
}

/** Always on screen during a demo, so sample data is never mistaken for the gym's records. */
@Composable
private fun DemoBanner() {
    val colors = MyGymTheme.colors
    val demoMode = LocalDemoMode.current ?: return
    Row(
        Modifier.fillMaxWidth().background(colors.accent).statusBarsPadding().padding(start = MyGymSpace.gutter, end = MyGymSpace.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(Res.string.demo_banner), Modifier.weight(1f), style = MyGymTheme.typography.bodyStrong, color = colors.onAccent)
        TextButton(onClick = demoMode::exit) {
            Text(stringResource(Res.string.demo_exit), style = MyGymTheme.typography.bodyStrong, color = colors.onAccent)
        }
    }
}
