package com.destinyai.astrology.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.repository.HomeRepository
import com.destinyai.astrology.domain.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class HomeUiState(
    val currentUser: User? = null,
    val displayName: String = "Guest",
    val dailyQuota: Int = 0,
    val dailyUsed: Int = 0,
    val remaining: Int = 0,
    val quotaProgress: Float = 0f,
    val isUnlimited: Boolean = false,
    val isLoading: Boolean = false,
    val suggestedQuestions: List<String> = emptyList(),
    val dailyInsight: String? = null,
    val renewalDateString: String? = null,
    // Rich astrology data
    val transits: List<HomeTransit> = emptyList(),
    val dashaInfo: HomeDashaInfo? = null,
    val yogas: List<HomeYoga> = emptyList(),
    val doshas: HomeDoshaStatus = HomeDoshaStatus(),
    val lifeAreas: List<HomeLifeArea> = defaultLifeAreas(),
    val isRichDataLoading: Boolean = false,
    val selectedLifeArea: HomeLifeArea? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: HomeRepository,
    private val prefs: UserPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        viewModelScope.launch {
            val user = repository.getCurrentUser()
            val quota = repository.getDailyQuota()
            val used = repository.getDailyUsed()
            updateQuotaState(user, quota, used)
        }
    }

    private fun updateQuotaState(user: User?, quota: Int, used: Int) {
        val unlimited = quota < 0
        val remaining = if (unlimited) Int.MAX_VALUE else maxOf(0, quota - used)
        val progress = if (unlimited || quota == 0) 0f else used.toFloat() / quota.toFloat()
        val name = when {
            user == null || user.isGuestEmail -> "Guest"
            user.name != null -> user.name.split(" ").first()
            else -> user.email.substringBefore("@")
        }
        _uiState.update {
            it.copy(
                currentUser = user,
                displayName = name,
                dailyQuota = quota,
                dailyUsed = used,
                remaining = remaining,
                quotaProgress = progress,
                isUnlimited = unlimited,
                isLoading = false,
            )
        }
    }

    fun loadHomeData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val questions = repository.getSuggestedQuestions()
            val insight = repository.getDailyInsight()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    suggestedQuestions = questions,
                    dailyInsight = insight,
                )
            }
            loadRichHomeData()
        }
    }

    private fun loadRichHomeData() {
        viewModelScope.launch {
            val email = prefs.getUserEmail() ?: return@launch
            val birth = prefs.getBirthProfile() ?: return@launch
            _uiState.update { it.copy(isRichDataLoading = true) }
            val richData = repository.getRichHomeData(email, birth)
            if (richData != null) {
                _uiState.update {
                    it.copy(
                        isRichDataLoading = false,
                        transits = richData.transits,
                        dashaInfo = richData.dashaInfo,
                        yogas = richData.yogas,
                        doshas = richData.doshas,
                        lifeAreas = richData.lifeAreas,
                    )
                }
            } else {
                _uiState.update { it.copy(isRichDataLoading = false) }
            }
        }
    }

    fun selectLifeArea(area: HomeLifeArea) {
        _uiState.update { it.copy(selectedLifeArea = area) }
    }

    fun dismissLifeArea() {
        _uiState.update { it.copy(selectedLifeArea = null) }
    }

    fun decrementQuota() {
        viewModelScope.launch {
            val quota = repository.getDailyQuota()
            val used = repository.getDailyUsed()
            val newUsed = used + 1
            val newRemaining = maxOf(0, quota - newUsed)
            val newProgress = if (quota <= 0) 0f else newUsed.toFloat() / quota
            _uiState.update {
                it.copy(
                    dailyUsed = newUsed,
                    remaining = newRemaining,
                    quotaProgress = newProgress,
                )
            }
        }
    }

    companion object {
        fun greetingFor(time: LocalTime): String = when {
            time.hour < 12 -> "Good Morning"
            time.hour < 17 -> "Good Afternoon"
            else -> "Good Evening"
        }

        fun formatRenewalDate(isoDate: String): String {
            val date = LocalDate.parse(isoDate)
            val formatter = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
            return date.format(formatter)
        }
    }
}
