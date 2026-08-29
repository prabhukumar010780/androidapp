package com.destinyai.astrology.ui.charts

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.destinyai.astrology.BuildConfig
import com.destinyai.astrology.R
import com.destinyai.astrology.data.local.db.AstroDataCacheDao
import com.destinyai.astrology.data.local.db.AstroDataCacheEntity
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.BirthProfileDto
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.time.LocalDate
import javax.inject.Inject

data class ChartsUiState(
    val hasData: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val dateOfBirth: String = "",
    val timeOfBirth: String = "",
    val cityOfBirth: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val chartStyle: String = "north",
    val timeUnknown: Boolean = false,
    val chartApiData: ChartApiResponse? = null,
    val ascendantSign: String? = null,
    val dashaResponse: DashaResponse? = null,
    val transitResponse: TransitResponse? = null,
)

@HiltViewModel
class ChartsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val prefs: UserPreferences,
    private val api: AstroApiService,
    // iOS parity (UserChartService reads AstroDataCache before the network): the cache
    // infra + kinds (chart/dasha/transits) exist and are already used by Home; the
    // Charts flow bypassed them, forcing 1-3 live round trips on every open.
    private val astroDataCacheDao: AstroDataCacheDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChartsUiState())
    val uiState: StateFlow<ChartsUiState> = _uiState

    private val gson = Gson()

    /** iOS parity (HomeRepositoryImpl.computeBirthHash / AstroDataCache key). */
    private fun computeBirthHash(p: BirthProfileDto): String {
        val raw = "${p.dateOfBirth}|${p.timeOfBirth}|${p.latitude}|" +
            "${p.longitude}|${p.cityOfBirth}|${p.birthTimeUnknown}"
        return MessageDigest.getInstance("SHA-1").digest(raw.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    /** iOS parity (UserAstroDataModels.swift roundCoordinate): round lat/lon to 6 dp
     *  so wire payloads + cache keys are byte-identical to iOS and satisfy backend
     *  decimal-place validation. */
    private fun round6(v: Double): Double = Math.round(v * 1_000_000.0) / 1_000_000.0

    fun loadChartData() {
        viewModelScope.launch {
            val profile = prefs.getBirthProfile()
            val chartStyle = prefs.getChartStyle()
            if (profile == null) {
                _uiState.update { it.copy(hasData = false, isLoading = false) }
                return@launch
            }
            // Parity with ChatRepositoryImpl — pass user-selected ayanamsa / house_system so
            // backend calculations reflect the active profile's preferences instead of
            // silently defaulting to lahiri / whole_sign.
            val ayanamsa = runCatching { prefs.getAyanamsa() }.getOrDefault("lahiri")
            val houseSystem = runCatching { prefs.getHouseSystem() }.getOrDefault("whole_sign")
            // iOS parity (PlanetaryPositionsSheet.swift:326): log chart load for active profile.
            Log.d("PlanetaryPositionsSheet", "Loading chart for: ${profile.cityOfBirth}")
            _uiState.update {
                it.copy(
                    hasData = true,
                    isLoading = true,
                    errorMessage = null,
                    dateOfBirth = profile.dateOfBirth,
                    timeOfBirth = profile.timeOfBirth,
                    cityOfBirth = profile.cityOfBirth,
                    latitude = round6(profile.latitude),
                    longitude = round6(profile.longitude),
                    timeUnknown = profile.birthTimeUnknown,
                    chartStyle = chartStyle,
                )
            }
            try {
                val activeProfileId = prefs.getActiveProfileId()?.takeIf { it.isNotBlank() }
                    ?: prefs.getUserEmail().orEmpty()
                val birthHash = computeBirthHash(profile)
                // iOS parity (UserChartService.fetchFullChartData): read the forever-cached
                // chart (year=0,month=0) first; render instantly + skip the network on hit.
                val cached = runCatching {
                    astroDataCacheDao.get("chart", activeProfileId, birthHash, 0, 0)
                        ?.let { gson.fromJson(it.payloadJson, ChartApiResponse::class.java) }
                }.getOrNull()
                val response = cached ?: api.getChartData(
                    ChartDataRequest(
                        birthData = BirthData(
                            dob = profile.dateOfBirth,
                            time = profile.timeOfBirth,
                            latitude = round6(profile.latitude),
                            longitude = round6(profile.longitude),
                            ayanamsa = ayanamsa,
                            houseSystem = houseSystem,
                            cityOfBirth = profile.cityOfBirth,
                            birthTimeUnknown = profile.birthTimeUnknown,
                        ),
                    )
                )
                if (cached == null) {
                    // Persist forever (per iOS setFullChart) so future opens are instant.
                    runCatching {
                        astroDataCacheDao.upsert(
                            AstroDataCacheEntity(
                                kind = "chart",
                                profileId = activeProfileId,
                                birthHash = birthHash,
                                year = 0,
                                month = 0,
                                ownerEmail = prefs.getUserEmail().orEmpty(),
                                payloadJson = gson.toJson(response),
                                savedAtMs = System.currentTimeMillis(),
                            ),
                        )
                    }
                }
                val signNum = response.houses["1"]?.signNum ?: 1
                val ascIndex = (signNum - 1).coerceIn(0, 11)
                val ascSign = ChartConstants.orderedSigns[ascIndex]
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        chartApiData = response,
                        ascendantSign = ascSign,
                    )
                }
                // Mirrors iOS UserChartService.fetchDashaPeriods / fetchTransits — fire after main chart loads
                loadDashaAndTransits(profile, ayanamsa, houseSystem)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = appContext.getString(R.string.error_chart_load_failed))
                }
            }
        }
    }

    private fun loadDashaAndTransits(
        profile: BirthProfileDto,
        ayanamsa: String,
        houseSystem: String,
    ) {
        viewModelScope.launch {
            val year = LocalDate.now().year
            val month = LocalDate.now().monthValue
            val activeProfileId = prefs.getActiveProfileId()?.takeIf { it.isNotBlank() }
                ?: prefs.getUserEmail().orEmpty()
            val birthHash = computeBirthHash(profile)
            val authHeader = "Bearer ${BuildConfig.API_KEY}"
            val request = DashaTransitRequest(
                birthData = BirthData(
                    dob = profile.dateOfBirth,
                    time = profile.timeOfBirth,
                    latitude = round6(profile.latitude),
                    longitude = round6(profile.longitude),
                    ayanamsa = ayanamsa,
                    houseSystem = houseSystem,
                    cityOfBirth = profile.cityOfBirth,
                    birthTimeUnknown = profile.birthTimeUnknown,
                ),
                year = year,
            )
            try {
                // iOS parity (UserChartService.fetchDashaPeriods): per-year cache (month=0).
                val cachedDasha = runCatching {
                    astroDataCacheDao.get("dasha", activeProfileId, birthHash, year, 0)
                        ?.let { gson.fromJson(it.payloadJson, DashaResponse::class.java) }
                }.getOrNull()
                val dasha = cachedDasha ?: api.getDashaPeriods(authHeader, request)
                if (cachedDasha == null) {
                    runCatching {
                        astroDataCacheDao.upsert(
                            AstroDataCacheEntity(
                                "dasha", activeProfileId, birthHash, year, 0,
                                prefs.getUserEmail().orEmpty(), gson.toJson(dasha), System.currentTimeMillis(),
                            ),
                        )
                    }
                }
                _uiState.update { it.copy(dashaResponse = dasha) }
            } catch (_: Exception) {
                // Non-fatal — chart still renders without dasha
            }
            try {
                // iOS parity (UserChartService.fetchTransits): per-year+month cache.
                val cachedTransits = runCatching {
                    astroDataCacheDao.get("transits", activeProfileId, birthHash, year, month)
                        ?.let { gson.fromJson(it.payloadJson, TransitResponse::class.java) }
                }.getOrNull()
                val transits = cachedTransits ?: api.getTransits(authHeader, request)
                if (cachedTransits == null) {
                    runCatching {
                        astroDataCacheDao.upsert(
                            AstroDataCacheEntity(
                                "transits", activeProfileId, birthHash, year, month,
                                prefs.getUserEmail().orEmpty(), gson.toJson(transits), System.currentTimeMillis(),
                            ),
                        )
                    }
                }
                _uiState.update { it.copy(transitResponse = transits) }
            } catch (_: Exception) {
                // Non-fatal — chart still renders without transits
            }
        }
    }

    fun setChartStyle(style: String) {
        viewModelScope.launch {
            prefs.setChartStyle(style)
            _uiState.update { it.copy(chartStyle = style) }
        }
    }

    fun retry() {
        _uiState.update { it.copy(errorMessage = null) }
        loadChartData()
    }
}
