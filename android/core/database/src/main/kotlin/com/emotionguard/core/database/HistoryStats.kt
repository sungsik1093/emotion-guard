/**
 * 기록 통계 계산 (순수 함수). DB와 분리해서 단위 테스트한다.
 */
package com.emotionguard.core.database

import com.emotionguard.core.model.Category
import com.emotionguard.core.model.DailyAverage
import com.emotionguard.core.model.EmotionRecord
import com.emotionguard.core.model.HistorySummary
import com.emotionguard.core.model.SendConfidence
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object HistoryStats {

    /** 오늘을 포함한 최근 [days]일의 시작 시각 */
    fun periodStart(days: Int, now: Instant, zone: ZoneId): Long =
        now.atZone(zone).toLocalDate().minusDays((days - 1).toLong())
            .atStartOfDay(zone).toInstant().toEpochMilli()

    /** 최근 [days]일 각각의 하루 평균. 기록이 없는 날도 count = 0으로 채운다. */
    fun dailyAverages(
        records: List<EmotionRecord>,
        days: Int,
        now: Instant,
        zone: ZoneId,
    ): List<DailyAverage> {
        val today = now.atZone(zone).toLocalDate()
        val byDay = records.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
        return (days - 1 downTo 0).map { offset ->
            val day = today.minusDays(offset.toLong())
            val dayRecords = byDay[day].orEmpty()
            DailyAverage(
                dayStartMillis = day.atStartOfDay(zone).toInstant().toEpochMilli(),
                average = if (dayRecords.isEmpty()) 0f else dayRecords.map { it.score }.average().toFloat(),
                count = dayRecords.size,
            )
        }
    }

    fun summarize(records: List<EmotionRecord>): HistorySummary {
        if (records.isEmpty()) return HistorySummary.EMPTY
        val byCategory = records
            .filter { it.category != Category.NONE }
            .groupingBy { it.category }
            .eachCount()
        return HistorySummary(
            total = records.size,
            averageScore = records.map { it.score }.average().toFloat(),
            warnedCount = records.count { it.warned },
            replacedCount = records.count { it.warned && it.replaced },
            byCategory = byCategory,
        )
    }

    /** "추정" 전송을 통계에서 뺄지 */
    fun filterByConfidence(records: List<EmotionRecord>, includeLikely: Boolean): List<EmotionRecord> =
        if (includeLikely) records else records.filter { it.sendConfidence == SendConfidence.CONFIRMED }
}
