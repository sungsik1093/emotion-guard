package com.emotionguard.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emotionguard.core.common.AccessibilityStatus
import com.emotionguard.core.common.DiagnosticsBus
import com.emotionguard.core.database.HistoryRepository
import com.emotionguard.core.datastore.RelationshipRepository
import com.emotionguard.core.datastore.SettingsRepository
import com.emotionguard.core.model.Relationship
import com.emotionguard.core.model.DiagnosticsSnapshot
import com.emotionguard.core.model.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object SettingsDestination

@Serializable
data object DiagnosticsDestination

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val history: HistoryRepository,
    private val relationships: RelationshipRepository,
    diagnostics: DiagnosticsBus,
    private val accessibility: AccessibilityStatus,
) : ViewModel() {

    val state: StateFlow<UserSettings> =
        settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    val diagnostics: StateFlow<DiagnosticsSnapshot> = diagnostics.snapshot

    val roomCount: StateFlow<Int> =
        relationships.roomCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun isAccessibilityEnabled() = accessibility.isServiceEnabled()

    fun setThreshold(value: Int) = viewModelScope.launch { settings.setWarnThreshold(value) }
    fun setAutoExpand(value: Boolean) = viewModelScope.launch { settings.setAutoExpandOnDanger(value) }
    fun setAlwaysShow(value: Boolean) = viewModelScope.launch { settings.setAlwaysShowBubble(value) }
    fun setSaveText(value: Boolean) = viewModelScope.launch { settings.setSaveOriginalText(value) }
    fun setCountLikely(value: Boolean) = viewModelScope.launch { settings.setCountLikelySends(value) }
    fun setMessenger(packageName: String, enabled: Boolean) =
        viewModelScope.launch { settings.setMessengerEnabled(packageName, enabled) }

    fun clearHistory() = viewModelScope.launch { history.clear() }
    fun setDefaultRelationship(value: Relationship) = viewModelScope.launch { settings.setDefaultRelationship(value) }
    fun clearRooms() = viewModelScope.launch { relationships.clearRooms() }
    fun setWeeklyReport(value: Boolean) = viewModelScope.launch { settings.setWeeklyReportEnabled(value) }
}
