package com.emotionguard.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EmotionDao {
    @Insert
    suspend fun insert(log: EmotionLogEntity): Long

    @Query("SELECT * FROM emotion_log WHERE timestamp >= :fromMillis ORDER BY timestamp DESC")
    fun observeSince(fromMillis: Long): Flow<List<EmotionLogEntity>>

    @Query("SELECT * FROM emotion_log ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<EmotionLogEntity>>

    @Query("SELECT * FROM emotion_log WHERE timestamp >= :fromMillis AND timestamp < :toMillis")
    suspend fun getBetween(fromMillis: Long, toMillis: Long): List<EmotionLogEntity>

    @Query("DELETE FROM emotion_log")
    suspend fun deleteAll()
}
