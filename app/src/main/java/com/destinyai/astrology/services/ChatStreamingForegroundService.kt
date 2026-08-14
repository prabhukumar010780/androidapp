package com.destinyai.astrology.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Keeps the process alive while a chat stream is in flight so the user can
 * background the app without losing the response — iOS parity (URLSession
 * tasks continue in the background by default on iOS; Android needs an
 * explicit foreground service to prevent the OS from suspending the process).
 *
 * Lifecycle: ChatViewModel calls start() before launching the stream coroutine
 * and stop() in the stream's finally block. The service shows a low-priority
 * silent notification ("Destiny is thinking…") that disappears automatically
 * when stop() is called.
 */
class ChatStreamingForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        ensureChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Destiny is thinking…")
            .setContentText("Your response will be ready when you return.")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setOngoing(true)
            .setSilent(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
        return START_NOT_STICKY
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Background streaming",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) },
        )
    }

    companion object {
        const val CHANNEL_ID = "chat_streaming"
        private const val NOTIFICATION_ID = 9001
        const val ACTION_STOP = "com.destinyai.astrology.STOP_STREAMING"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ChatStreamingForegroundService::class.java))
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, ChatStreamingForegroundService::class.java).apply {
                    action = ACTION_STOP
                },
            )
        }
    }
}
