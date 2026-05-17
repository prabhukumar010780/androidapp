package com.destinyai.astrology.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: HomeRepository,
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
        }
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
