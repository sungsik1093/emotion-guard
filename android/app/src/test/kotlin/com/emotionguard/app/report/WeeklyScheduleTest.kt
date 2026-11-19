package com.emotionguard.app.report

import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class WeeklyScheduleTest {
    private val seoul = ZoneId.of("Asia/Seoul")
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0) = ZonedDateTime.of(y, m, d, h, min, 0, 0, seoul)

    @Test
    fun `평일이면 이번 주 일요일 20시`() {
        // 2026-11-17은 화요일 → 11/22(일) 20시
        assertEquals(Duration.ofHours(5 * 24 + 10), WeeklyReportWorker.delayUntilNextSunday(at(2026, 11, 17, 10)))
    }

    @Test
    fun `일요일 20시 전이면 오늘, 지났으면 다음 주`() {
        assertEquals(Duration.ofHours(2), WeeklyReportWorker.delayUntilNextSunday(at(2026, 11, 22, 18)))
        assertEquals(Duration.ofDays(7), WeeklyReportWorker.delayUntilNextSunday(at(2026, 11, 22, 20)))
        assertEquals(Duration.ofDays(7).minusHours(1), WeeklyReportWorker.delayUntilNextSunday(at(2026, 11, 22, 21)))
    }
}
