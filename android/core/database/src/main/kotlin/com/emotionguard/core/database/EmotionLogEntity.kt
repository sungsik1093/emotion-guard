/**
 * 보낸 메시지 한 건의 감정 기록 (Room 엔티티).
 * 원문(originalText)은 사용자가 설정에서 켰을 때만 저장한다.
 */
package com.emotionguard.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.emotionguard.core.model.Category
import com.emotionguard.core.model.EmotionRecord
import com.emotionguard.core.model.SendConfidence

@Entity(tableName = "emotion_log", indices = [Index("timestamp")])
data class EmotionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    @ColumnInfo(name = "package_name") val packageName: String,
    val score: Int,
    val category: Category,
    val warned: Boolean,
    val replaced: Boolean,
    @ColumnInfo(name = "send_confidence") val sendConfidence: SendConfidence,
    @ColumnInfo(name = "analyzed_locally") val analyzedLocally: Boolean,
    @ColumnInfo(name = "original_text") val originalText: String?,
)

fun EmotionLogEntity.toModel() = EmotionRecord(
    id = id,
    timestamp = timestamp,
    packageName = packageName,
    score = score,
    category = category,
    warned = warned,
    replaced = replaced,
    sendConfidence = sendConfidence,
    analyzedLocally = analyzedLocally,
    originalText = originalText,
)

fun EmotionRecord.toEntity() = EmotionLogEntity(
    id = id,
    timestamp = timestamp,
    packageName = packageName,
    score = score,
    category = category,
    warned = warned,
    replaced = replaced,
    sendConfidence = sendConfidence,
    analyzedLocally = analyzedLocally,
    originalText = originalText,
)
