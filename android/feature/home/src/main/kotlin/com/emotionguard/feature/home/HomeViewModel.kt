package com.emotionguard.feature.home

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emotionguard.core.analysis.AnalysisRepository
import com.emotionguard.core.common.AccessibilityStatus
import com.emotionguard.core.database.HistoryRepository
import com.emotionguard.core.database.HistoryStats
import com.emotionguard.core.datastore.SettingsRepository
import com.emotionguard.core.datastore.GrowthRepository
import com.emotionguard.core.datastore.SkinRepository
import com.emotionguard.core.model.Growth
import com.emotionguard.core.model.CharacterSkin
import com.emotionguard.core.model.HistorySummary
import com.emotionguard.core.model.Quota
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object HomeDestination

data class HomeUiState(
    val guardEnabled: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val today: HistorySummary = HistorySummary.EMPTY,
    val quota: Quota? = null,
    val skin: CharacterSkin = CharacterSkin(),
    val growth: Growth = Growth(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val accessibility: AccessibilityStatus,
    history: HistoryRepository,
    analysis: AnalysisRepository,
    private val skins: SkinRepository,
    growthRepository: GrowthRepository,
) : ViewModel() {

    private val accessibilityEnabled = MutableStateFlow(accessibility.isServiceEnabled())

    val uiState: StateFlow<HomeUiState> = combine(
        settings.settings,
        accessibilityEnabled,
        history.observeLastDays(1),
        analysis.quota,
        combine(skins.skin, growthRepository.growth, ::Pair),
    ) { s, a11y, today, quota, (skin, growth) ->
        HomeUiState(
            guardEnabled = s.guardEnabled,
            accessibilityEnabled = a11y,
            today = HistoryStats.summarize(HistoryStats.filterByConfidence(today, s.countLikelySends)),
            quota = quota,
            skin = skin,
            growth = growth,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun refresh() {
        accessibilityEnabled.value = accessibility.isServiceEnabled()
    }

    fun resolveImage(name: String): File = skins.fileOf(name)

    fun accessibilitySettingsIntent(): Intent = accessibility.settingsIntent()

    /** 접근성 권한이 없으면 false를 돌려주고, 화면이 설정 화면을 연다. */
    fun toggle(): Boolean {
        if (!accessibility.isServiceEnabled()) {
            accessibilityEnabled.value = false
            return false
        }
        viewModelScope.launch { settings.toggleGuard() }
        return true
    }
}
