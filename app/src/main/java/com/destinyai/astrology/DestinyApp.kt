package com.destinyai.astrology

import android.app.Application
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.destinyai.astrology.data.billing.BillingManager
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.repository.AuthRepository
import com.destinyai.astrology.services.AppStartupService
import com.destinyai.astrology.services.QuotaManager
import com.destinyai.astrology.ui.auth.AccountDeletedError
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
    @Inject lateinit var quotaManager: QuotaManager
    @Inject lateinit var userPreferences: UserPreferences
    // iOS parity (AppRootView.swift:204-210): on account_deleted detection at foreground,
    // run the full sign-out teardown so the next Splash routes to Auth.
    @Inject lateinit var authRepository: AuthRepository

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
                    // iOS parity (ios_appApp.swift:148 — QuotaManager.syncStatus on foreground):
                    // refresh the authoritative entitlement store on every foreground so an
                    // external cancel/expiry (Play Store) or webhook downgrade that happened
                    // while backgrounded downgrades the gates without waiting for re-login.
                    // Force-sync (bypasses cooldown) since foreground is an explicit user signal.
                    //
                    // Fix 6 (HIGH): stop swallowing AccountDeletedError from syncStatus — if the
                    // server returns account_deleted/403 for an already-authenticated user, run
                    // the full sign-out teardown so the next Splash routes to Auth, matching
                    // iOS AppRootView.swift:204-210 and ios_appApp.swift foreground behavior.
                    // Transient network errors (non-AccountDeletedError) are still swallowed.
                    appScope.launch {
                        val email = runCatching { userPreferences.getUserEmail() }.getOrNull()
                        if (!email.isNullOrBlank()) {
                            try {
                                quotaManager.syncStatus(email, force = true)
                            } catch (e: AccountDeletedError) {
                                Log.w("DestinyApp", "foreground syncStatus: account_deleted — forcing sign-out")
                                runCatching { authRepository.clearSession() }
                            } catch (_: Exception) {
                                // Transient / network errors — swallow as before.
                            }
                        }
                    }
                }
            })
        }
    }
}
