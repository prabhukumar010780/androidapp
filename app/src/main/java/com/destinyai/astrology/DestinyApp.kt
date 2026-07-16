package com.destinyai.astrology

import android.app.Application
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.destinyai.astrology.data.billing.BillingManager
import com.destinyai.astrology.services.AppStartupService
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class DestinyApp : Application() {
    @Inject lateinit var billingManager: BillingManager
    @Inject lateinit var appStartupService: AppStartupService

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
        // iOS parity (AppStartupService.swift:90-96, C-1 fix): refresh app config on
        // every foreground so a gate-mode / streaming kill-switch flip propagates to an
        // already-running app within one foreground cycle instead of on relaunch only.
        runCatching {
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onResume(owner: LifecycleOwner) {
                    appScope.launch { runCatching { appStartupService.refreshConfig() } }
                }
            })
        }
    }
}
