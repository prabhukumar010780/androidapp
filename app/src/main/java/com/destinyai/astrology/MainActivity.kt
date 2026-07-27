package com.destinyai.astrology

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.destinyai.astrology.services.FcmTokenManager
import com.destinyai.astrology.services.NotificationRouter
import com.destinyai.astrology.ui.compatibility.E2EPartnerOverrides
import com.destinyai.astrology.ui.nav.AppNav
import com.destinyai.astrology.ui.theme.DestinyTheme
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var fcmTokenManager: FcmTokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        // Opt into edge-to-edge BEFORE super/setContent. On targetSdk 35+ Android
        // forces edge-to-edge regardless, but calling enableEdgeToEdge() installs
        // the proper WindowInsets dispatch so Compose's WindowInsets.navigationBars /
        // statusBars / ime resolve correctly. Without it, on a real device with
        // gesture navigation the bottom tab bar's windowInsetsPadding(navigationBars)
        // could resolve to 0 and the bar rendered behind/under the gesture pill →
        // "tab bar missing on device" (emulator dispatched insets differently, so it
        // showed there). Also required for IME (keyboard) insets to be reported so
        // adjustResize + imePadding behave.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Debug-only: capture E2E partner pre-fill extras before any Compose
        // graph runs so CompatibilityViewModel.loadUserData() can consume them
        // on its first invocation. No-op in release builds (BuildConfig.DEBUG
        // gate inside E2EPartnerOverrides).
        E2EPartnerOverrides.captureFromIntent(intent)
        // Notification permission is requested in-context from MainScreen, after the user
        // has completed onboarding and auth. Removed from here (cold onCreate) because
        // firing the system dialog immediately on every launch — before the user has seen
        // the app — gave zero context and produced poor opt-in rates. The new flow
        // (MainScreen.kt) shows a rationale dialog first, then the OS prompt, exactly
        // once per install, after the user reaches the Home tab.
        handleNotificationIntent(intent)
        try {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                lifecycleScope.launch {
                    fcmTokenManager.registerToken(token, BuildConfig.VERSION_NAME)
                }
            }
        } catch (e: Exception) {
            Log.w("MainActivity", "FCM unavailable", e)
        }
        setContent {
            DestinyTheme {
                AppNav()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        E2EPartnerOverrides.captureFromIntent(intent)
        handleNotificationIntent(intent)
    }

    /**
     * Mirrors iOS AppDelegate notification routing. Reads `notification_type`
     * extra emitted by [DestinyFirebaseMessagingService] and publishes a deep
     * link to [NotificationRouter] so AppNav can route the user to the right
     * destination (chat / match / settings / home).
     */
    private fun handleNotificationIntent(intent: Intent?) {
        val type = intent?.getStringExtra("notification_type") ?: return
        val prefill = intent.getStringExtra("notification_prefill").orEmpty()
        val autoSubmit = intent.getBooleanExtra("notification_auto_submit", false)
        val newThread = intent.getBooleanExtra("notification_new_thread", false)
        NotificationRouter.route(
            type = type,
            prefill = prefill,
            autoSubmit = autoSubmit,
            newThread = newThread,
        )
    }
}
