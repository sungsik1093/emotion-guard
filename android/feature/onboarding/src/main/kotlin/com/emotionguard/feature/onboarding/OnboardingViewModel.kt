package com.emotionguard.feature.onboarding

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emotionguard.core.common.AccessibilityStatus
import com.emotionguard.core.datastore.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object OnboardingDestination

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val accessibility: AccessibilityStatus,
) : ViewModel() {

    private val _accessibilityEnabled = MutableStateFlow(accessibility.isServiceEnabled())
    val accessibilityEnabled: StateFlow<Boolean> = _accessibilityEnabled.asStateFlow()

    /** 시스템 설정에서 돌아올 때마다 다시 확인 */
    fun refresh() {
        _accessibilityEnabled.value = accessibility.isServiceEnabled()
    }

    fun accessibilitySettingsIntent(): Intent = accessibility.settingsIntent()

    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            settings.completeOnboarding()
            if (accessibility.isServiceEnabled()) settings.setGuardEnabled(true)
            onDone()
        }
    }
}
