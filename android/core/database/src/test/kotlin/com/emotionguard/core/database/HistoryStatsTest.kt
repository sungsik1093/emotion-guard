package com.emotionguard.core.database

import com.emotionguard.core.model.Category
import com.emotionguard.core.model.EmotionRecord
import com.emotionguard.core.model.SendConfidence
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryStatsTest {
    private val zone = ZoneId.of("Asia/Seoul")
    // 2026-11-17 15:00 KST
    private val now = Instant.parse("2026-11-17T06:00:00Z")

    private fun record(
        hoursAgo: Long,
        score: Int,
        category: Category = Category.NONE,
        warned: Boolean = score >= 61,
        replaced: Boolean = false,
        confidence: SendConfidence = SendConfidence.CONFIRMED,
    ) = EmotionRecord(
        id = hoursAgo,
        timestamp = now.minusSeconds(hoursAgo * 3600).toEpochMilli(),
        packageName = "com.kakao.talk",
        score = score,
        category = category,
        warned = warned,
        replaced = replaced,
        sendConfidence = confidence,
        analyzedLocally = false,
        originalText = null,
    )

    @Test
    fun `최근 N일 하루 평균, 기록 없는 날은 0건`() {
        val records = listOf(
            record(1, 20),  // 오늘
            record(2, 40),  // 오늘
            record(24, 80), // 어제
        )
        val days = HistoryStats.dailyAverages(records, 7, now, zone)
        assertEquals(7, days.size)
        assertEquals(30f, days.last().average, 0.01f)
        assertEquals(2, days.last().count)
        assertEquals(80f, days[5].average, 0.01f)
        assertEquals(0, days[0].count)
    }

    @Test
    fun `요약 통계와 바꿔 보낸 비율`() {
        val records = listOf(
            record(1, 70, Category.BLAME, replaced = true),
            record(2, 80, Category.PROFANITY),
            record(3, 10),
            record(4, 65, Category.BLAME),
        )
        val s = HistoryStats.summarize(records)
        assertEquals(4, s.total)
        assertEquals(56.25f, s.averageScore, 0.01f)
        assertEquals(3, s.warnedCount)
        assertEquals(1, s.replacedCount)
        assertEquals(1f / 3, s.replaceRate, 0.001f)
        assertEquals(mapOf(Category.BLAME to 2, Category.PROFANITY to 1), s.byCategory)
    }

    @Test
    fun `추정 전송 제외 옵션`() {
        val records = listOf(record(1, 10), record(2, 10, confidence = SendConfidence.LIKELY))
        assertEquals(2, HistoryStats.filterByConfidence(records, includeLikely = true).size)
        assertEquals(1, HistoryStats.filterByConfidence(records, includeLikely = false).size)
    }

    @Test
    fun `기간 시작은 N-1일 전 자정`() {
        val start = HistoryStats.periodStart(7, now, zone)
        assertEquals(Instant.parse("2026-11-10T15:00:00Z").toEpochMilli(), start) // 11/11 00:00 KST
    }
}
