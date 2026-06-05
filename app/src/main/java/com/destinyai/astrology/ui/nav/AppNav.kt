package com.destinyai.astrology.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    val localeVersion by localeManager.localeVersion.collectAsStateWithLifecycle()

    // iOS parity (AppRootView.swift:108-122): the splash is a ZStack overlay over the
    // already-routed underlying screen. Android mirrors this by rendering a NavHost
    // (with a resolved start destination) and laying the SplashScreen on top via
    // AnimatedVisibility, so the next screen is pre-warmed behind the splash.
    val splashViewModel: SplashViewModel = hiltViewModel()
    val splashDestination by splashViewModel.uiState.collectAsStateWithLifecycle()
    var showSplash by remember { mutableStateOf(true) }
    var hasNavigatedFromSplash by remember { mutableStateOf(false) }

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
    LaunchedEffect(pendingDeepLink) {
        val link = pendingDeepLink ?: return@LaunchedEffect
        when (link) {
            NotificationDeepLink.Settings -> {
                navController.navigate(Routes.NOTIFICATION_PREFS) { launchSingleTop = true }
                NotificationRouter.consume()
            }
            else -> Unit  // Home / Chat / Match handled by MainScreen.kt
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // key(localeVersion) — when LocaleManager.applyLocale() bumps the counter,
        // the entire NavHost is recomposed so every screen picks up the new
        // string resources. Mirrors iOS AppRootView's .id(languageRefreshID).
        key(localeVersion) {
            NavHost(navController = navController, startDestination = Routes.SPLASH) {

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
                    onNavigateToMain = { navController.navigate(Routes.MAIN) { popUpTo(Routes.AUTH) { inclusive = true } } },
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
                    onNavigateToAuth = {
                        // Mirrors iOS QuotaExhaustedView.onSignIn — navigate to AuthScreen and clear stack so
                        // the user can re-auth (or upgrade after re-auth). Birth data is preserved in prefs.
                        navController.navigate(Routes.AUTH) { popUpTo(0) { inclusive = true } }
                    },
                )
            }

            composable(Routes.HISTORY) {
                HistoryScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.CHARTS) {
                ChartsScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.NOTIFICATIONS) {
                NotificationsScreen(
                    onBack = { navController.popBackStack() },
                    onNotifPrefs = { navController.navigate(Routes.NOTIFICATION_PREFS) },
                    onGuestSignInRequest = { navController.navigate(Routes.AUTH) },
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
                )
            }

            composable(Routes.COMPATIBILITY) {
                CompatibilityScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToPartners = { navController.navigate(Routes.PARTNERS) },
                    onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
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
            exit = fadeOut(animationSpec = tween(durationMillis = 600)),
            modifier = Modifier.zIndex(1f),
        ) {
            SplashScreen(soundManager = splashSoundManager)
        }
    }
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
}
