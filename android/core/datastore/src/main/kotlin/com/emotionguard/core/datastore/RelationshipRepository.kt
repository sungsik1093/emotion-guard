/**
 * 대화방별 관계 저장소 (디자인 문서 §15.2).
 *
 * 대화방은 "패키지 + 방 제목(상대 이름)"의 SHA-256 해시로 구분한다.
 * 상대 이름 자체는 저장하지 않는다. 같은 이름의 방은 같은 관계를 쓴다.
 */
package com.emotionguard.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.emotionguard.core.model.ChatRoom
import com.emotionguard.core.model.Relationship
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

@Singleton
class RelationshipRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val settings: SettingsRepository,
) {
    private val store = context.appDataStore
    private val key = stringPreferencesKey("room_relationships")
    private val json = Json { ignoreUnknownKeys = true }

    /** 방 키 → 관계 이름 */
    private val rooms: Flow<Map<String, String>> = store.data.map { p ->
        p[key]?.let { runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrNull() } ?: emptyMap()
    }.distinctUntilChanged()

    /** 관계를 정해둔 대화방 수 (설정 화면 표시용) */
    val roomCount: Flow<Int> = rooms.map { it.size }

    /** 이 방의 관계. 정해둔 게 없으면 설정의 기본 관계 */
    suspend fun relationshipFor(room: ChatRoom?): Relationship {
        val saved = room?.let { rooms.first()[keyOf(it)] }
        return saved?.let(Relationship::fromName) ?: settings.current().defaultRelationship
    }

    /** 이 방에 관계를 따로 정해뒀는지 */
    suspend fun hasOwnRelationship(room: ChatRoom?): Boolean =
        room != null && rooms.first().containsKey(keyOf(room))

    /** 방 제목을 못 읽었으면 기본 관계를 바꾼다 */
    suspend fun set(room: ChatRoom?, relationship: Relationship) {
        if (room == null) {
            settings.setDefaultRelationship(relationship)
            return
        }
        store.edit { p ->
            val current = p[key]?.let { runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrNull() }
                ?: emptyMap()
            p[key] = json.encodeToString(current + (keyOf(room) to relationship.name))
        }
    }

    suspend fun clearRooms() {
        store.edit { it.remove(key) }
    }

    companion object {
        fun keyOf(room: ChatRoom): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest("${room.packageName}\u0000${room.title.trim()}".toByteArray())
            return digest.take(16).joinToString("") { "%02x".format(it) }
        }
    }
}
