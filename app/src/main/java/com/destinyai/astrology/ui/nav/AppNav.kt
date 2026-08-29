package com.destinyai.astrology.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import com.destinyai.astrology.ui.theme.CosmicBackground
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.destinyai.astrology.ui.theme.LocalReduceMotion
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.destinyai.astrology.services.LocaleManager
import com.destinyai.astrology.services.NotificationDeepLink
import com.destinyai.astrology.services.NotificationRouter
import com.destinyai.astrology.ui.auth.AuthScreen
import com.destinyai.astrology.ui.auth.AuthViewModel
import com.destinyai.astrology.ui.auth.BirthDataScreen
import com.destinyai.astrology.ui.auth.WaitlistPendingScreen
import com.destinyai.astrology.ui.charts.ChartsScreen
import com.destinyai.astrology.ui.compatibility.CompatibilityScreen
import com.destinyai.astrology.ui.history.HistoryScreen
import com.destinyai.astrology.ui.main.MainScreen
import com.destinyai.astrology.ui.notifications.NotificationPreferencesScreen
import com.destinyai.astrology.ui.notifications.NotificationsScreen
import com.destinyai.astrology.ui.onboarding.LanguageSelectionScreen
import com.destinyai.astrology.ui.onboarding.OnboardingScreen
import com.destinyai.astrology.ui.onboarding.ProfileSetupLoadingScreen
import com.destinyai.astrology.ui.onboarding.ResponseStyleOnboardingScreen
import com.destinyai.astrology.ui.partners.PartnersScreen
import com.destinyai.astrology.ui.profile.BirthDetailsScreen
import com.destinyai.astrology.ui.profile.FaqHelpScreen
import com.destinyai.astrology.ui.profile.ProfileScreen
import com.destinyai.astrology.ui.settings.AstrologySettingsScreen
import com.destinyai.astrology.ui.settings.SettingsScreen
import com.destinyai.astrology.ui.splash.SplashDestination
import com.destinyai.astrology.ui.splash.SplashScreen
import com.destinyai.astrology.ui.splash.SplashViewModel
import com.destinyai.astrology.ui.subscription.SubscriptionScreen
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AppNav() {
    val navController = rememberNavController()
    val pendingDeepLink by NotificationRouter.pendingDeepLink.collectAsStateWithLifecycle()

    // Mirrors iOS AppRootView.swift:127-133. iOS bumps `languageRefreshID = UUID()`
    // on .appLanguageChanged and applies `.id(languageRefreshID)` to force a full
    // SwiftUI rebuild. On Android we read LocaleManager.localeVersion (a counter
    // bumped inside applyLocale) and wrap NavHost in `key(localeVersion)` so a
    // mid-session language change recomposes every screen with the new resources.
    val context = LocalContext.current
    val localeManager = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AppNavLocaleEntryPoint::class.java,
        ).localeManager()
    }
    val splashSoundManager = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AppNavLocaleEntryPoint::class.java,
        ).soundManager()
    }
    // iOS parity (ChatView.swift signOutAndReauth): paywall Sign In CTAs reach this
    // repository to clear the guest session before navigating to AuthScreen, so the
    // login UI sticks instead of bouncing back to Main via auto-redirect.
    val paywallAuthRepository = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AppNavLocaleEntryPoint::class.java,
        ).authRepository()
    }
    // Coroutine scope tied to NavHost root, NOT a composable's viewModelScope, so
    // signOutPreserveBirthData can complete even after popUpTo(0) destroys the calling
    // composable. Mirrors the BIRTH_DATA logout pattern at line 228-235.
    val paywallScope = rememberCoroutineScope()
    /**
     * Centralized paywall → AuthScreen navigator. Used by every quota-exhausted Sign In CTA
     * and every guest gate that needs to land at AuthScreen. Mirrors iOS signOutAndReauth +
     * QuotaExhaustedView.onSignIn: clears the guest session (preserving birth data) FIRST
     * so AuthScreen.loadSession() returns a null user, then navigates with popUpTo(0) so the
     * user can't back-button their way around the auth wall.
     */
    val navigateToAuthFromPaywall: () -> Unit = remember(navController, paywallAuthRepository, paywallScope) {
        {
            paywallScope.launch {
                runCatching { paywallAuthRepository.signOutPreserveBirthData() }
                navController.navigate(Routes.AUTH) { popUpTo(0) { inclusive = true } }
            }
            Unit
        }
    }
    val localeVersion by localeManager.localeVersion.collectAsStateWithLifecycle()

    // iOS parity (AppRootView.swift:108-122): the splash is a ZStack overlay over the
    // already-routed underlying screen. Android mirrors this by rendering a NavHost
    // (with a resolved start destination) and laying the SplashScreen on top via
    // AnimatedVisibility, so the next screen is pre-warmed behind the splash.
    val splashViewModel: SplashViewModel = hiltViewModel()
    val splashDestination by splashViewModel.uiState.collectAsStateWithLifecycle()
    var showSplash by rememberSaveable { mutableStateOf(true) }
    var hasNavigatedFromSplash by rememberSaveable { mutableStateOf(false) }
    // R10: read the system "Remove animations" setting once per composition.
    // animatorDurationScale == 0f means the user has disabled animations.
    val reduceMotion = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }

    LaunchedEffect(Unit) { splashViewModel.navigate() }

    LaunchedEffect(splashDestination) {
        if (splashDestination == SplashDestination.Splash || hasNavigatedFromSplash) return@LaunchedEffect
        val route = when (splashDestination) {
            SplashDestination.LanguageSelection -> Routes.LANGUAGE_SELECTION
            SplashDestination.Onboarding -> Routes.ONBOARDING
            SplashDestination.Auth -> Routes.AUTH
            SplashDestination.WaitlistPending -> Routes.WAITLIST
            SplashDestination.BirthData -> Routes.BIRTH_DATA
            SplashDestination.Main -> Routes.MAIN
            SplashDestination.Splash -> return@LaunchedEffect
        }
        // Replace the placeholder start so the underlying screen is already loaded
        // when the splash overlay fades out — matches iOS pre-warm behavior.
        navController.navigate(route) {
            popUpTo(Routes.SPLASH) { inclusive = true }
            launchSingleTop = true
        }
        hasNavigatedFromSplash = true
        showSplash = false
    }

    // Mirrors iOS AppDelegate handler — once nav is past splash/auth, consume
    // the pending deep link emitted by a tapped push notification.
    //
    // Consumer split (single-consumer rule, parity with iOS):
    //   • AppNav consumes ONLY links that change the navigation surface
    //     (currently just Settings → push NotificationPrefs route).
    //   • MainScreen consumes intra-MAIN tab links (Home / Chat / Match).
    //   • Without this split, AppNav and MainScreen both consumed the link and
    //     raced — Settings used to fire BOTH a NotificationPrefs push AND a
    //     selectedTab=0 reset.
    LaunchedEffect(pendingDeepLink, hasNavigatedFromSplash) {
        // A8: deep-link nav must wait until the splash → destination transition is
        // complete; firing before that would race with popUpTo(SPLASH) and either
        // get lost or land on a stale back stack.
        if (!hasNavigatedFromSplash) return@LaunchedEffect
        val link = pendingDeepLink ?: return@LaunchedEffect
        when (link) {
            NotificationDeepLink.Settings -> {
                // DES-161 D5: push SETTINGS under NOTIFICATION_PREFS so Back from the
                // alerts screen returns to Settings (matching the in-app Settings →
                // Alerts path), not Home. Previously NOTIFICATION_PREFS was pushed
                // directly onto MAIN, so popBackStack() landed on Home.
                navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
                navController.navigate(Routes.NOTIFICATION_PREFS) { launchSingleTop = true }
                NotificationRouter.consume()
            }
            // R8/B7 fix: subscription-expiring push deep-links to the paywall so the
            // user has a direct path to renew instead of landing on Alerts.
            NotificationDeepLink.Subscription -> {
                navController.navigate(Routes.SUBSCRIPTION) { launchSingleTop = true }
                NotificationRouter.consume()
            }
            else -> Unit  // Home / Chat / Match handled by MainScreen.kt
        }
    }

    // Global tap-to-dismiss keyboard (iOS parity — tapping outside a text field
    // resigns the responder). Several screens (birth details, partner picker,
    // history/search sheets, alert editor) have text fields but no per-field
    // dismiss, so the IME could stay up with no way down. A root-level tap
    // handler in the Initial pass clears focus + hides the keyboard on any tap
    // that children don't consume, without stealing taps from buttons/fields.
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    // Single root cosmic background — applied here so every route and every
    // transition inherits the deep-navy + golden-nebula + star-field look
    // without each screen having to redraw the base layer (iOS parity:
    // CosmicBackgroundView rendered once in AppRootView's ZStack root).
    // The pointerInput tap-to-dismiss keyboard lives here so it still fires
    // on any tap that child screens don't consume.
    // R10: provide the reduce-motion flag to every composable in the tree so
    // CosmicStarField, ShimmerButton, CelestialOrb, and SplashScreen can
    // freeze their infinite animations without threading the value manually.
    CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
    CosmicBackground(
        modifier = Modifier
            .fillMaxSize()
            // Expose Compose testTags as Android resource-ids globally so the
            // Appium E2E suite can locate elements with
            // AppiumBy.ID("com.destinyai.astrology:id/<tag>").
            // Mirrors NotificationsScreen.kt's per-screen usage — applied here
            // once so every route inherits it automatically.
            .semantics { testTagsAsResourceId = true }
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                })
            },
    ) {
        // key(localeVersion) — when LocaleManager.applyLocale() bumps the counter,
        // the entire NavHost is recomposed so every screen picks up the new
        // string resources. Mirrors iOS AppRootView's .id(languageRefreshID).
        key(localeVersion) {
            // Issues 1 + 2: iOS-parity asymmetric move+opacity gate transitions.
            // Replaces the default Material crossfade with slideInHorizontally + fadeIn
            // (forward) and slideOutHorizontally + fadeOut (back), so each route push
            // feels like the iOS NavigationStack's lateral slide rather than a crossfade.
            val navEnterDurationMs = 320
            val navExitDurationMs = 280
            NavHost(
                navController = navController,
                startDestination = Routes.SPLASH,
                enterTransition = {
                    slideInHorizontally(
                        animationSpec = tween(navEnterDurationMs, easing = FastOutSlowInEasing),
                        initialOffsetX = { fullWidth -> fullWidth / 4 },
                    ) + fadeIn(animationSpec = tween(navEnterDurationMs))
                },
                exitTransition = {
                    slideOutHorizontally(
                        animationSpec = tween(navExitDurationMs, easing = FastOutSlowInEasing),
                        targetOffsetX = { fullWidth -> -fullWidth / 6 },
                    ) + fadeOut(animationSpec = tween(navExitDurationMs))
                },
                popEnterTransition = {
                    slideInHorizontally(
                        animationSpec = tween(navEnterDurationMs, easing = FastOutSlowInEasing),
                        initialOffsetX = { fullWidth -> -fullWidth / 6 },
                    ) + fadeIn(animationSpec = tween(navEnterDurationMs))
                },
                popExitTransition = {
                    slideOutHorizontally(
                        animationSpec = tween(navExitDurationMs, easing = FastOutSlowInEasing),
                        targetOffsetX = { fullWidth -> fullWidth / 4 },
                    ) + fadeOut(animationSpec = tween(navExitDurationMs))
                },
            ) {

            // Placeholder: empty composable — splash is rendered as overlay above.
            // Kept as a real route so initial navigation has a valid back-stack root
            // that we then replace via popUpTo(SPLASH, inclusive=true).
            composable(Routes.SPLASH) { /* overlay handled outside NavHost */ }

            composable(Routes.LANGUAGE_SELECTION) {
                LanguageSelectionScreen(
                    onNavigateNext = { navController.navigate(Routes.ONBOARDING) { popUpTo(Routes.LANGUAGE_SELECTION) { inclusive = true } } },
                )
            }

            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onNavigateToAuth = { navController.navigate(Routes.AUTH) { popUpTo(Routes.ONBOARDING) { inclusive = true } } },
                )
            }

            composable(Routes.AUTH) {
                AuthScreen(
                    // iOS parity (AuthView.swift loadingOverlay + AuthViewModel.performSignIn):
                    // post-sign-in users go directly to MAIN — the LoadingOverlay covers the
                    // entire sync window because AuthViewModel keeps isLoading=true until
                    // fetchAndRestoreProfile + LoginSyncCoordinator + QuotaManager.syncStatus
                    // all complete. Matches iOS isAuthenticated flip happening AFTER syncs.
                    onNavigateToMain = {
                        navController.navigate(Routes.MAIN) {
                            popUpTo(Routes.AUTH) { inclusive = true }
                        }
                    },
                    // iOS parity: once authenticated, AUTH is removed from the
                    // back stack so a back press from BirthDataScreen does not
                    // return the user to a sign-in surface they already passed.
                    onNavigateToBirthData = {
                        navController.navigate(Routes.BIRTH_DATA) {
                            popUpTo(Routes.AUTH) { inclusive = true }
                        }
                    },
                    onNavigateToWaitlist = { navController.navigate(Routes.WAITLIST) { popUpTo(Routes.AUTH) { inclusive = true } } },
                )
            }

            composable(Routes.WAITLIST) {
                WaitlistPendingScreen(
                    onSignedOut = { navController.navigate(Routes.AUTH) { popUpTo(0) { inclusive = true } } },
                    // iOS parity (AppRootView.swift:77-92 + .task{recheckWaitlistStatus}):
                    // when the polled access state changes off "waitlist_pending",
                    // fall through to BIRTH_DATA (no profile yet) or MAIN (warm cache).
                    onAccessGranted = { hasBirthData ->
                        val target = if (hasBirthData) Routes.MAIN else Routes.BIRTH_DATA
                        navController.navigate(target) {
                            popUpTo(Routes.WAITLIST) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(Routes.BIRTH_DATA) {
                val authViewModel: AuthViewModel = hiltViewModel()
                // Use a NavHost-rooted scope (not viewModelScope) so the
                // suspend logout completes even after popUpTo(0) destroys
                // this composable's NavBackStackEntry. Without this, the
                // viewModelScope launched by AuthViewModel.logout() is
                // cancelled mid-clear and prefs.isAuthenticated stays true.
                val logoutScope = rememberCoroutineScope()
                BirthDataScreen(
                    // Mirrors iOS: after birth data save, route through ProfileSetupLoading
                    // to prefetch chart + today's prediction before landing on MAIN with a warm cache.
                    onSaved = { navController.navigate(Routes.PROFILE_SETUP) { popUpTo(Routes.BIRTH_DATA) { inclusive = true } } },
                    onBack = {
                        logoutScope.launch {
                            authViewModel.logoutAndAwait()
                            navController.navigate(Routes.AUTH) {
                                popUpTo(0) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    },
                )
            }

            composable(Routes.PROFILE_SETUP) {
                ProfileSetupLoadingScreen(
                    onComplete = {
                        navController.navigate(Routes.MAIN) { popUpTo(Routes.PROFILE_SETUP) { inclusive = true } }
                    },
                )
            }

            composable(Routes.MAIN) {
                MainScreen(
                    onNavigateToCharts = { navController.navigate(Routes.CHARTS) },
                    onNavigateToNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                    onNavigateToPartners = { navController.navigate(Routes.PARTNERS) },
                    onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                    onNavigateToSubscription = { navController.navigate(Routes.SUBSCRIPTION) },
                    onDeletedAccount = { navController.navigate(Routes.AUTH) { popUpTo(0) { inclusive = true } } },
                    onNavigateToLanguage = { navController.navigate(Routes.LANGUAGE_SELECTION) },
                    onNavigateToResponseStyle = { navController.navigate(Routes.RESPONSE_STYLE) },
                    onNavigateToNotificationPrefs = { navController.navigate(Routes.NOTIFICATION_PREFS) },
                    onNavigateToFaq = { navController.navigate(Routes.FAQ_HELP) },
                    onNavigateToAstrologySettings = { navController.navigate(Routes.ASTROLOGY_SETTINGS) },
                    onNavigateToBirthDetails = { navController.navigate(Routes.BIRTH_DETAILS) },
                    onNavigateToAuth = navigateToAuthFromPaywall,
                )
            }

            composable(Routes.HISTORY) {
                HistoryScreen(
                    onBack = { navController.popBackStack() },
                    // Mirrors iOS HistoryView.swift:89-93 — `.openProfileSettings`
                    // NotificationCenter post. On Android we route through the
                    // SETTINGS destination so the "Open Settings" CTA always
                    // deep-links instead of falling back to onBack.
                    onOpenProfileSettings = {
                        navController.popBackStack()
                        navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
                    },
                )
            }

            composable(Routes.CHARTS) {
                ChartsScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.NOTIFICATIONS) {
                NotificationsScreen(
                    onBack = { navController.popBackStack() },
                    onNotifPrefs = { navController.navigate(Routes.NOTIFICATION_PREFS) },
                    onGuestSignInRequest = navigateToAuthFromPaywall,
                    onUpgradeRequest = { navController.navigate(Routes.SUBSCRIPTION) },
                    // iOS parity (NotificationInboxView.swift:382-394) — Ask More from a daily/transit/life alert
                    // dismisses the inbox; NotificationRouter.pendingDeepLink (set inside the screen) is then
                    // consumed by MainScreen's LaunchedEffect, which routes to chat with prefill+autoSubmit.
                    onAskMore = { _, _ ->
                        navController.popBackStack()
                        navController.navigate(Routes.MAIN) { launchSingleTop = true }
                    },
                    onOpenCompatibility = {
                        navController.popBackStack()
                        navController.navigate(Routes.COMPATIBILITY) { launchSingleTop = true }
                    },
                    onOpenSubscription = {
                        navController.popBackStack()
                        navController.navigate(Routes.SUBSCRIPTION) { launchSingleTop = true }
                    },
                )
            }

            composable(Routes.NOTIFICATION_PREFS) {
                NotificationPreferencesScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.PARTNERS) {
                PartnersScreen(
                    onBack = { navController.popBackStack() },
                    onUpgrade = { navController.navigate(Routes.SUBSCRIPTION) },
                    // iOS parity (ProfileView.swift:339-347 + QuotaExhaustedView.onSignIn):
                    // defense-in-depth — if a guest somehow reaches this destination,
                    // clear the guest session and route to AuthScreen.
                    onNavigateToAuth = navigateToAuthFromPaywall,
                )
            }

            composable(Routes.COMPATIBILITY) {
                CompatibilityScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToPartners = { navController.navigate(Routes.PARTNERS) },
                    onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                    onNavigateToAuth = navigateToAuthFromPaywall,
                )
            }

            composable(Routes.PROFILE) {
                ProfileScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                    onNavigateToSubscription = { navController.navigate(Routes.SUBSCRIPTION) },
                    onDeletedAccount = { navController.navigate(Routes.AUTH) { popUpTo(0) { inclusive = true } } },
                    onNavigateToLanguage = { navController.navigate(Routes.LANGUAGE_SELECTION) },
                    onNavigateToResponseStyle = { navController.navigate(Routes.RESPONSE_STYLE) },
                    onNavigateToNotificationPrefs = { navController.navigate(Routes.NOTIFICATION_PREFS) },
                    onNavigateToCharts = { navController.navigate(Routes.CHARTS) },
                    onNavigateToPartners = { navController.navigate(Routes.PARTNERS) },
                    onNavigateToFaq = { navController.navigate(Routes.FAQ_HELP) },
                    onNavigateToBirthDetails = { navController.navigate(Routes.BIRTH_DETAILS) },
                    onNavigateToAstrologySettings = { navController.navigate(Routes.ASTROLOGY_SETTINGS) },
                    // iOS parity (ProfileView.swift:157-183): GuestSignInPromptView
                    // is presented as a sheet with the existing authViewModel — the
                    // user signs in inside the prompt without bouncing through
                    // signOut. On Android we route to the dedicated AUTH screen
                    // (the same Google flow) without first clearing the guest
                    // session.
                    onLaunchEmbeddedAuth = navigateToAuthFromPaywall,
                )
            }

            composable(Routes.BIRTH_DETAILS) {
                BirthDetailsScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }

            composable(Routes.FAQ_HELP) {
                FaqHelpScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToAstrologySettings = { navController.navigate(Routes.ASTROLOGY_SETTINGS) },
                    onNavigateToNotificationPrefs = { navController.navigate(Routes.NOTIFICATION_PREFS) },
                )
            }

            composable(Routes.ASTROLOGY_SETTINGS) {
                AstrologySettingsScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.SUBSCRIPTION) {
                SubscriptionScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.RESPONSE_STYLE) {
                ResponseStyleOnboardingScreen(
                    isSettingsMode = true,
                    onContinue = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                )
            }
            }  // NavHost
        }      // key(localeVersion)

        // Splash overlay — sits above NavHost (zIndex 1) and fades out when the
        // resolved destination has been navigated. Mirrors iOS AppRootView ZStack.
        AnimatedVisibility(
            visible = showSplash,
            enter = fadeIn(animationSpec = tween(0)),
            exit = fadeOut(animationSpec = tween(durationMillis = 400, easing = FastOutLinearInEasing)),
            modifier = Modifier.zIndex(1f),
        ) {
            SplashScreen(soundManager = splashSoundManager)
        }
    }
    }  // CompositionLocalProvider(LocalReduceMotion)
}

/**
 * Hilt EntryPoint that exposes the application-scoped LocaleManager to the
 * non-Hilt AppNav composable. Used so the NavHost can observe localeVersion
 * and force a recomposition on every language change (iOS parity).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppNavLocaleEntryPoint {
    fun localeManager(): LocaleManager
    fun soundManager(): com.destinyai.astrology.services.SoundManager
    // iOS parity (ChatView.swift signOutAndReauth): exposes AuthRepository so paywall
    // sign-in CTAs can clear the guest session before navigating to AuthScreen — without
    // this, AuthScreen.LaunchedEffect(state.isAuthenticated) immediately routes back to Main.
    fun authRepository(): com.destinyai.astrology.data.repository.AuthRepository
}
