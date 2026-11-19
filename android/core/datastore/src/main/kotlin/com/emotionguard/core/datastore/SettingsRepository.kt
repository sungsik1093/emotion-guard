/**
 * 사용자 설정 저장소 (Preferences DataStore).
 * 접근성 서비스와 화면이 같은 Flow를 구독하므로, 홈에서 토글을 끄면 서비스가 바로 멈춘다.
 */
package com.emotionguard.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.emotionguard.core.model.Relationship
import com.emotionguard.core.model.UserSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "emotion_guard")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val store = context.appDataStore

    private object Keys {
        val GUARD_ENABLED = booleanPreferencesKey("guard_enabled")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val CONSENT = booleanPreferencesKey("consent_accepted")
        val THRESHOLD = intPreferencesKey("warn_threshold")
        val AUTO_EXPAND = booleanPreferencesKey("auto_expand_on_danger")
        val ALWAYS_SHOW = booleanPreferencesKey("always_show_bubble")
        val SAVE_TEXT = booleanPreferencesKey("save_original_text")
        val COUNT_LIKELY = booleanPreferencesKey("count_likely_sends")
        val DISABLED_PACKAGES = stringSetPreferencesKey("disabled_packages")
        val INSTALL_ID = stringPreferencesKey("install_id")
        val DEFAULT_RELATIONSHIP = stringPreferencesKey("default_relationship")
        val WEEKLY_REPORT = booleanPreferencesKey("weekly_report_enabled")
    }

    val settings: Flow<UserSettings> = store.data.map { p ->
        UserSettings(
            guardEnabled = p[Keys.GUARD_ENABLED] ?: false,
            onboardingDone = p[Keys.ONBOARDING_DONE] ?: false,
            consentAccepted = p[Keys.CONSENT] ?: false,
            warnThreshold = (p[Keys.THRESHOLD] ?: UserSettings.DEFAULT_THRESHOLD)
                .coerceIn(UserSettings.THRESHOLD_RANGE),
            autoExpandOnDanger = p[Keys.AUTO_EXPAND] ?: false,
            alwaysShowBubble = p[Keys.ALWAYS_SHOW] ?: false,
            saveOriginalText = p[Keys.SAVE_TEXT] ?: false,
            countLikelySends = p[Keys.COUNT_LIKELY] ?: true,
            disabledPackages = p[Keys.DISABLED_PACKAGES] ?: emptySet(),
            defaultRelationship = Relationship.fromName(p[Keys.DEFAULT_RELATIONSHIP]),
            weeklyReportEnabled = p[Keys.WEEKLY_REPORT] ?: true,
        )
    }.distinctUntilChanged()

    suspend fun current(): UserSettings = settings.first()

    suspend fun setGuardEnabled(enabled: Boolean) {
        store.edit { it[Keys.GUARD_ENABLED] = enabled }
    }

    suspend fun toggleGuard(): Boolean {
        var next = false
        store.edit {
            next = !(it[Keys.GUARD_ENABLED] ?: false)
            it[Keys.GUARD_ENABLED] = next
        }
        return next
    }

    suspend fun completeOnboarding() {
        store.edit {
            it[Keys.ONBOARDING_DONE] = true
            it[Keys.CONSENT] = true
        }
    }

    suspend fun setWarnThreshold(value: Int) {
        store.edit { it[Keys.THRESHOLD] = value.coerceIn(UserSettings.THRESHOLD_RANGE) }
    }

    suspend fun setAutoExpandOnDanger(value: Boolean) {
        store.edit { it[Keys.AUTO_EXPAND] = value }
    }

    suspend fun setAlwaysShowBubble(value: Boolean) {
        store.edit { it[Keys.ALWAYS_SHOW] = value }
    }

    suspend fun setSaveOriginalText(value: Boolean) {
        store.edit { it[Keys.SAVE_TEXT] = value }
    }

    suspend fun setCountLikelySends(value: Boolean) {
        store.edit { it[Keys.COUNT_LIKELY] = value }
    }

    suspend fun setMessengerEnabled(packageName: String, enabled: Boolean) {
        store.edit {
            val current = it[Keys.DISABLED_PACKAGES] ?: emptySet()
            it[Keys.DISABLED_PACKAGES] = if (enabled) current - packageName else current + packageName
        }
    }

    suspend fun setDefaultRelationship(value: Relationship) {
        store.edit { it[Keys.DEFAULT_RELATIONSHIP] = value.name }
    }

    suspend fun setWeeklyReportEnabled(value: Boolean) {
        store.edit { it[Keys.WEEKLY_REPORT] = value }
    }

    /** 앱 설치마다 한 번 만드는 ID. dev 인증에서 사용자 구분용으로 쓴다. */
    suspend fun installId(): String {
        store.data.first()[Keys.INSTALL_ID]?.let { return it }
        var id = ""
        store.edit { p ->
            id = p[Keys.INSTALL_ID] ?: UUID.randomUUID().toString().also { p[Keys.INSTALL_ID] = it }
        }
        return id
    }
}
