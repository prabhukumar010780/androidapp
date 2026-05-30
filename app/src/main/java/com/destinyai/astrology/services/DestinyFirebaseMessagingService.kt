package com.destinyai.astrology.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.destinyai.astrology.BuildConfig
import com.destinyai.astrology.MainActivity
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.DeviceTokenRequest
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
            val email = prefs.getUserEmail() ?: return@launch
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
            } catch (_: Exception) {}
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val type = message.data["type"] ?: "general"
        val title = message.notification?.title ?: message.data["title"] ?: "Destiny AI"
        val body = message.notification?.body ?: message.data["body"] ?: return
        val channelId = channelForType(type)

        ensureChannel(channelId)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("notification_type", type)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
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

    private fun channelForType(type: String): String = when (type) {
        "daily_prediction" -> CHANNEL_DAILY
        "transit_alert", "life_alert" -> CHANNEL_TRANSIT
        else -> CHANNEL_GENERAL
    }

    private fun ensureChannel(channelId: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(channelId) != null) return
        val name = when (channelId) {
            CHANNEL_DAILY -> "Daily Insights"
            CHANNEL_TRANSIT -> "Transit Alerts"
            else -> "General"
        }
        manager.createNotificationChannel(
            NotificationChannel(channelId, name, NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    companion object {
        const val CHANNEL_DAILY = "daily_prediction"
        const val CHANNEL_TRANSIT = "transit_alert"
        const val CHANNEL_GENERAL = "general"
    }
}
