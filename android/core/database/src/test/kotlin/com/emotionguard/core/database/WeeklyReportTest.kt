package com.emotionguard.core.database

import com.emotionguard.core.model.Category
import com.emotionguard.core.model.EmotionRecord
import com.emotionguard.core.model.Growth
import com.emotionguard.core.model.SendConfidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyReportTest {
    private fun r(score: Int, category: Category = Category.NONE, replaced: Boolean = false) = EmotionRecord(
        id = 0, timestamp = 0, packageName = "p", score = score, category = category,
        warned = replaced || score >= 61, replaced = replaced, sendConfidence = SendConfidence.CONFIRMED,
        analyzedLocally = false, originalText = "비밀 원문",
    )

    @Test
    fun `기록이 없으면 안내 문구`() {
        val text = WeeklyReport.build(emptyList(), emptyList(), Growth(xp = 40))
        assertTrue(text.body.contains("기록이 없어요"))
        assertTrue(text.body.contains("Lv 2"))
    }

    @Test
    fun `요약 숫자와 지난주 대비`() {
        val thisWeek = listOf(r(10), r(20, replaced = true), r(70, Category.BLAME), r(65, Category.BLAME), r(80, Category.PROFANITY))
        val lastWeek = listOf(r(60), r(60))
        val text = WeeklyReport.build(thisWeek, lastWeek, Growth(xp = 160, streakDays = 3))
        assertEquals("이번 주엔 말이 조금 날카로웠어요", text.title) // 평균 49
        assertTrue(text.body, text.body.contains("보낸 메시지 5개, 평균 감정 온도 49°(지난주보다 11° 낮아졌어요)"))
        assertTrue(text.body.contains("대안으로 바꿔 보낸 게 1번"))
        assertTrue(text.body.contains("'비난·탓하기'"))
        assertTrue(text.body.contains("Lv 4, 3일 연속"))
    }

    @Test
    fun `원문은 절대 넣지 않는다`() {
        val text = WeeklyReport.build(listOf(r(80, Category.PROFANITY)), emptyList(), Growth())
        assertFalse(text.body.contains("비밀 원문"))
        assertFalse(text.title.contains("비밀 원문"))
    }
}
