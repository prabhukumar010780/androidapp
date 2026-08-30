package com.destinyai.astrology

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.graphics.Color
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.lifecycleScope
import com.destinyai.astrology.services.FcmTokenManager
import com.destinyai.astrology.services.NotificationRouter
import com.destinyai.astrology.ui.auth.E2EBirthDataOverrides
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
        //
        // R7 fix: force light (white) icons on BOTH bars unconditionally. The app uses
        // a dark theme exclusively (DestinyTheme never switches to a light palette).
        // The no-arg overload uses SystemBarStyle.auto() keyed to the device night-mode
        // setting; on a Light-mode device it requests dark icons over our dark navy
        // backdrop → invisible clock/battery/nav pill. Explicit SystemBarStyle.dark()
        // ensures white icons regardless of the device's system theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // Debug-only: capture E2E partner pre-fill extras before any Compose
        // graph runs so CompatibilityViewModel.loadUserData() can consume them
        // on its first invocation. No-op in release builds (BuildConfig.DEBUG
        // gate inside E2EPartnerOverrides).
        E2EPartnerOverrides.captureFromIntent(intent)
        E2EBirthDataOverrides.captureFromIntent(intent)
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
            // Haptics/vibration globally disabled: provide a no-op HapticFeedback so
            // every Compose `performHapticFeedback` call across the app is a no-op.
            CompositionLocalProvider(LocalHapticFeedback provides NoOpHapticFeedback) {
                DestinyTheme {
                    AppNav()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        E2EPartnerOverrides.captureFromIntent(intent)
        E2EBirthDataOverrides.captureFromIntent(intent)
        handleNotificationIntent(intent)
    }

    /**
     * Mirrors iOS AppDelegate notification routing (ios_appApp.swift:227-229).
     *
     * Two code paths arrive here:
     *  1. FOREGROUND tap: [DestinyFirebaseMessagingService.onMessageReceived] built the
     *     notification tray entry and put `notification_type` / `notification_prefill`
     *     into the pending intent's extras.
     *  2. BACKGROUND / KILLED tap: FCM delivered a notification+data message while the
     *     app was not running. Android restores the app and puts the FCM `data` dict keys
     *     directly as intent extras — so the type extra is `"type"` (not `"notification_type"`)
     *     and the prefill extra is `"chat_prompt"` (not `"notification_prefill"`).
     *
     * We prefer the explicit foreground-path keys (set by DFMS) and fall back to the raw
     * backend keys so both paths work. This matches iOS which reads userInfo["type"] and
     * userInfo["chat_prompt"] in both states.
     */
    private fun handleNotificationIntent(intent: Intent?) {
        if (intent == null) return
        // Prefer DFMS-built extra; fall back to raw backend key sent by FCM on background tap.
        val type = intent.getStringExtra("notification_type")
            ?: intent.getStringExtra("type")
            ?: return
        val prefill = intent.getStringExtra("notification_prefill")
            ?: intent.getStringExtra("chat_prompt")
            ?: ""
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

/** No-op HapticFeedback — haptics/vibration are globally disabled app-wide. */
private val NoOpHapticFeedback = object : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        // intentionally does nothing
    }
}
