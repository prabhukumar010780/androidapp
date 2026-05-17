package com.destinyai.astrology.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.destinyai.astrology.ui.auth.AuthScreen
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
import com.destinyai.astrology.ui.partners.PartnersScreen
import com.destinyai.astrology.ui.profile.ProfileScreen
import com.destinyai.astrology.ui.settings.SettingsScreen
import com.destinyai.astrology.ui.splash.SplashScreen
import com.destinyai.astrology.ui.subscription.SubscriptionScreen

@Composable
fun AppNav() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.SPLASH) {

        composable(Routes.SPLASH) {
            SplashScreen(
                onNavigateToLanguage = { navController.navigate(Routes.LANGUAGE_SELECTION) { popUpTo(Routes.SPLASH) { inclusive = true } } },
                onNavigateToOnboarding = { navController.navigate(Routes.ONBOARDING) { popUpTo(Routes.SPLASH) { inclusive = true } } },
                onNavigateToAuth = { navController.navigate(Routes.AUTH) { popUpTo(Routes.SPLASH) { inclusive = true } } },
                onNavigateToWaitlist = { navController.navigate(Routes.WAITLIST) { popUpTo(Routes.SPLASH) { inclusive = true } } },
                onNavigateToBirthData = { navController.navigate(Routes.BIRTH_DATA) { popUpTo(Routes.SPLASH) { inclusive = true } } },
                onNavigateToMain = { navController.navigate(Routes.MAIN) { popUpTo(Routes.SPLASH) { inclusive = true } } },
            )
        }

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
                onNavigateToBirthData = { navController.navigate(Routes.BIRTH_DATA) },
                onNavigateToWaitlist = { navController.navigate(Routes.WAITLIST) { popUpTo(Routes.AUTH) { inclusive = true } } },
            )
        }

        composable(Routes.WAITLIST) {
            WaitlistPendingScreen(
                onSignedOut = { navController.navigate(Routes.AUTH) { popUpTo(0) { inclusive = true } } },
            )
        }

        composable(Routes.BIRTH_DATA) {
            BirthDataScreen(
                onSaved = { navController.navigate(Routes.MAIN) { popUpTo(Routes.BIRTH_DATA) { inclusive = true } } },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.MAIN) {
            MainScreen(
                onNavigateToHistory = { navController.navigate(Routes.HISTORY) },
                onNavigateToCharts = { navController.navigate(Routes.CHARTS) },
                onNavigateToNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                onNavigateToProfile = { navController.navigate(Routes.PROFILE) },
                onNavigateToPartners = { navController.navigate(Routes.PARTNERS) },
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
            )
        }

        composable(Routes.NOTIFICATION_PREFS) {
            NotificationPreferencesScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.PARTNERS) {
            PartnersScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.COMPATIBILITY) {
            CompatibilityScreen(
                onBack = { navController.popBackStack() },
                onNavigateToPartners = { navController.navigate(Routes.PARTNERS) },
            )
        }

        composable(Routes.PROFILE) {
            ProfileScreen(
                onBack = { navController.popBackStack() },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToSubscription = { navController.navigate(Routes.SUBSCRIPTION) },
                onDeletedAccount = { navController.navigate(Routes.AUTH) { popUpTo(0) { inclusive = true } } },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SUBSCRIPTION) {
            SubscriptionScreen(onBack = { navController.popBackStack() })
        }
    }
}
