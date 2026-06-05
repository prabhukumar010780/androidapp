package com.destinyai.astrology

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.destinyai.astrology.services.FcmTokenManager
import com.destinyai.astrology.services.NotificationRouter
import com.destinyai.astrology.ui.nav.AppNav
import com.destinyai.astrology.ui.theme.DestinyTheme
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var fcmTokenManager: FcmTokenManager

    // Mirrors iOS PushNotificationService.requestPermission() — requests POST_NOTIFICATIONS
    // on Android 13+ (API 33+) so notifications are not silently suppressed by the OS.
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Log.d("MainActivity", "POST_NOTIFICATIONS granted")
        } else {
            Log.w("MainActivity", "POST_NOTIFICATIONS denied — pushes will be suppressed")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
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

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val alreadyGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!alreadyGranted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
