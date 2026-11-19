package com.emotionguard.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emotionguard.core.database.HistoryRepository
import com.emotionguard.core.database.HistoryStats
import com.emotionguard.core.datastore.SettingsRepository
import com.emotionguard.core.model.DailyAverage
import com.emotionguard.core.model.EmotionRecord
import com.emotionguard.core.model.HistorySummary
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable

@Serializable
data object HistoryDestination

enum class Period(val days: Int, val label: String) { WEEK(7, "주간"), MONTH(30, "월간") }

data class HistoryUiState(
    val period: Period = Period.WEEK,
    val daily: List<DailyAverage> = emptyList(),
    val summary: HistorySummary = HistorySummary.EMPTY,
    val recent: List<EmotionRecord> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    history: HistoryRepository,
    settings: SettingsRepository,
) : ViewModel() {

    private val period = MutableStateFlow(Period.WEEK)

    val uiState: StateFlow<HistoryUiState> = combine(
        period,
        period.flatMapLatest { history.observeLastDays(it.days) },
        settings.settings,
    ) { p, records, s ->
        val filtered = HistoryStats.filterByConfidence(records, s.countLikelySends)
        HistoryUiState(
            period = p,
            daily = HistoryStats.dailyAverages(filtered, p.days, Instant.now(), ZoneId.systemDefault()),
            summary = HistoryStats.summarize(filtered),
            recent = filtered.take(30),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun selectPeriod(value: Period) {
        period.value = value
    }
}
