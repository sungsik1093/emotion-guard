/**
 * 감정 기록 저장소. 화면과 서비스는 DAO 대신 이 클래스를 쓴다.
 */
package com.emotionguard.core.database

import com.emotionguard.core.model.EmotionRecord
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class HistoryRepository @Inject constructor(
    private val dao: EmotionDao,
) {
    suspend fun add(record: EmotionRecord) {
        dao.insert(record.toEntity())
    }

    /** 오늘을 포함한 최근 [days]일 기록 */
    fun observeLastDays(days: Int, zone: ZoneId = ZoneId.systemDefault()): Flow<List<EmotionRecord>> =
        dao.observeSince(HistoryStats.periodStart(days, Instant.now(), zone))
            .map { list -> list.map { it.toModel() } }

    fun observeRecent(limit: Int): Flow<List<EmotionRecord>> =
        dao.observeRecent(limit).map { list -> list.map { it.toModel() } }

    suspend fun between(fromMillis: Long, toMillis: Long): List<EmotionRecord> =
        dao.getBetween(fromMillis, toMillis).map { it.toModel() }

    suspend fun clear() = dao.deleteAll()
}
