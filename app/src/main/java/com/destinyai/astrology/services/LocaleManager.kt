package com.destinyai.astrology.services

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * R2-S6: Singleton locale manager.
 *
 * Holds a SharedFlow that broadcasts BCP-47 language codes whenever the user
 * changes their language preference. Callers (e.g. LanguageSettingsSheet) emit
 * here after persisting to UserPreferences.
 *
 * [applyLocale] calls AppCompatDelegate.setApplicationLocales which triggers
 * per-app language override (Android 13+ natively; AppCompat back-fills to API 21).
 *
 * [localeVersion] is a monotonically increasing counter bumped on every
 * applyLocale() call. UI roots (AppNav) wrap their NavHost in `key(localeVersion)`
 * so a mid-session language change forces a full recomposition — parity with
 * iOS AppRootView.swift:127-133 where languageRefreshID = UUID() forces a
 * .id() rebuild on .appLanguageChanged.
 */
@Singleton
class LocaleManager @Inject constructor() {

    private val _languageChanges = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** Observe language-change events (BCP-47 code e.g. "en", "hi", "zh-Hans"). */
    val languageChanges: SharedFlow<String> = _languageChanges.asSharedFlow()

    private val _localeVersion = MutableStateFlow(0)

    /**
     * Monotonic counter incremented on every applyLocale() — wrap NavHost in
     * key(localeVersion) to force full UI recomposition on language change.
     */
    val localeVersion: StateFlow<Int> = _localeVersion.asStateFlow()

    /**
     * Apply [languageCode] as the active app locale and emit to [languageChanges].
     *
     * Must be called from the main thread because AppCompatDelegate accesses
     * the Android UI toolkit.
     */
    suspend fun applyLocale(languageCode: String) {
        val localeList = LocaleListCompat.forLanguageTags(languageCode)
        AppCompatDelegate.setApplicationLocales(localeList)
        _localeVersion.value = _localeVersion.value + 1
        _languageChanges.emit(languageCode)
    }
}

