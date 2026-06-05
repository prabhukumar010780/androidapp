package com.destinyai.astrology

import android.app.Application
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.destinyai.astrology.data.billing.BillingManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class DestinyApp : Application() {
    @Inject lateinit var billingManager: BillingManager

    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            Log.e("DestinyApp", "Uncaught exception on thread ${t.name}: ${e.message}", e)
            // don't crash — let coroutine exception handler take over
        }
        // iOS parity (SubscriptionManager.swift:79-125): wire ProcessLifecycle
        // so reconcile fires on every foreground and the 60s sync timer runs
        // while app is in foreground. Surfaces backend webhook-driven
        // cancellations without requiring an app restart.
        runCatching { billingManager.observeAppLifecycle() }
    }
}
