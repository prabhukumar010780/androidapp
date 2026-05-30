package com.destinyai.astrology.services

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
 */
@Singleton
class LocaleManager @Inject constructor() {

    private val _languageChanges = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** Observe language-change events (BCP-47 code e.g. "en", "hi", "zh-Hans"). */
    val languageChanges: SharedFlow<String> = _languageChanges.asSharedFlow()

    /**
     * Apply [languageCode] as the active app locale and emit to [languageChanges].
     *
     * Must be called from the main thread because AppCompatDelegate accesses
     * the Android UI toolkit.
     */
    suspend fun applyLocale(languageCode: String) {
        val localeList = LocaleListCompat.forLanguageTags(languageCode)
        AppCompatDelegate.setApplicationLocales(localeList)
        _languageChanges.emit(languageCode)
    }
}
