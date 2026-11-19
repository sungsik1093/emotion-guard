/**
 * 캐릭터 성장 저장소. 규칙은 core:model의 GrowthRules에 있고, 여기는 저장만 맡는다.
 */
package com.emotionguard.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.emotionguard.core.model.Accessory
import com.emotionguard.core.model.Growth
import com.emotionguard.core.model.GrowthRules
import com.emotionguard.core.model.GrowthUpdate
import com.emotionguard.core.model.Level
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

@Singleton
class GrowthRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val store = context.appDataStore
    private val key = stringPreferencesKey("growth")
    private val json = Json { ignoreUnknownKeys = true }

    private fun decode(raw: String?): Growth =
        raw?.let { runCatching { json.decodeFromString<Growth>(it) }.getOrNull() } ?: Growth()

    val growth: Flow<Growth> = store.data.map { decode(it[key]) }.distinctUntilChanged()

    suspend fun current(): Growth = growth.first()

    /** 메시지를 보냈을 때 경험치를 더한다. DataStore.edit 안에서 계산해서 동시에 불려도 안전하다. */
    suspend fun onSent(level: Level, replaced: Boolean, today: LocalDate = LocalDate.now()): GrowthUpdate {
        var update: GrowthUpdate? = null
        store.edit { p ->
            val result = GrowthRules.onSent(decode(p[key]), level, replaced, today)
            p[key] = json.encodeToString(Growth.serializer(), result.growth)
            update = result
        }
        return update!!
    }

    /** 열린 장식만 고를 수 있다 */
    suspend fun selectAccessory(accessory: Accessory) {
        store.edit { p ->
            val g = decode(p[key])
            if (g.isUnlocked(accessory)) p[key] = json.encodeToString(Growth.serializer(), g.copy(selectedAccessory = accessory))
        }
    }
}
