package com.destinyai.astrology.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.destinyai.astrology.BuildConfig
import com.destinyai.astrology.MainActivity
import com.destinyai.astrology.R
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.DeviceTokenRequest
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DestinyFirebaseMessagingService : FirebaseMessagingService() {

    @Inject lateinit var prefs: UserPreferences
    @Inject lateinit var api: AstroApiService

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch {
            prefs.setFcmToken(token)
            prefs.setFcmTokenRegistered(false)
            val email = prefs.getUserEmail() ?: run {
                Log.w(TAG, "Cannot register FCM token: no userEmail in prefs")
                return@launch
            }
            // In-process retry with exponential backoff so a transient network
            // hiccup does not silently drop the FCM token registration. iOS does
            // a single attempt today; Android adds bounded retry to mitigate.
            var attempt = 0
            var delayMs = REGISTER_RETRY_INITIAL_DELAY_MS
            while (attempt < REGISTER_RETRY_MAX_ATTEMPTS) {
                attempt++
                try {
                    api.registerDeviceToken(
                        DeviceTokenRequest(
                            userEmail = email,
                            token = token,
                            platform = "android",
                            appVersion = BuildConfig.VERSION_NAME,
                        )
                    )
                    prefs.setFcmTokenRegistered(true)
                    return@launch
                } catch (e: Exception) {
                    Log.w(
                        TAG,
                        "FCM token registration attempt $attempt failed: ${e.message}",
                        e,
                    )
                    if (attempt < REGISTER_RETRY_MAX_ATTEMPTS) {
                        delay(delayMs)
                        delayMs = (delayMs * 2).coerceAtMost(REGISTER_RETRY_MAX_DELAY_MS)
                    }
                }
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val type = message.data["type"] ?: "general"
        val title = message.notification?.title
            ?: message.data["title"]
            ?: getString(R.string.notification_default_title)
        val body = message.notification?.body ?: message.data["body"] ?: return
        val channelId = channelForType(type)

        ensureChannel(channelId)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("notification_type", type)
            // Mirrors iOS NotificationRouter chat-prefill flow — forwarded to
            // MainActivity which dispatches to NotificationRouter.route(...).
            message.data["prefill"]?.let { putExtra("notification_prefill", it) }
            message.data["auto_submit"]?.let {
                putExtra("notification_auto_submit", it.equals("true", ignoreCase = true))
            }
            message.data["new_thread"]?.let {
                putExtra("notification_new_thread", it.equals("true", ignoreCase = true))
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(type.hashCode(), notification)
    }

    // Mirrors iOS NotificationRouter type-string set — matches both UPPER_SNAKE_CASE
    // (e.g. DAILY_PREDICTION_READY) emitted by backend and any legacy lowercase variants
    // so daily/transit/life messages land on the right channel instead of collapsing onto general.
    private fun channelForType(type: String): String = when (type.uppercase()) {
        "DAILY_PREDICTION", "DAILY_PREDICTION_READY", "WELCOME" -> CHANNEL_DAILY
        "TRANSIT_ALERT", "LIFE_ALERT", "CUSTOM_ALERT" -> CHANNEL_TRANSIT
        else -> CHANNEL_GENERAL
    }

    private fun ensureChannel(channelId: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(channelId) != null) return
        val name = when (channelId) {
            CHANNEL_DAILY -> getString(R.string.notification_channel_daily)
            CHANNEL_TRANSIT -> getString(R.string.notification_channel_transit)
            else -> getString(R.string.notification_channel_general)
        }
        // Transit/life alerts are time-sensitive — use HIGH importance so they
        // surface as heads-up notifications, matching iOS APNs interruption-level.
        val importance = if (channelId == CHANNEL_TRANSIT) {
            NotificationManager.IMPORTANCE_HIGH
        } else {
            NotificationManager.IMPORTANCE_DEFAULT
        }
        manager.createNotificationChannel(
            NotificationChannel(channelId, name, importance)
        )
    }

    companion object {
        private const val TAG = "DestinyFcmService"
        const val CHANNEL_DAILY = "daily_prediction"
        const val CHANNEL_TRANSIT = "transit_alert"
        const val CHANNEL_GENERAL = "general"
        private const val REGISTER_RETRY_MAX_ATTEMPTS = 3
        private const val REGISTER_RETRY_INITIAL_DELAY_MS = 2_000L
        private const val REGISTER_RETRY_MAX_DELAY_MS = 30_000L
    }
}
