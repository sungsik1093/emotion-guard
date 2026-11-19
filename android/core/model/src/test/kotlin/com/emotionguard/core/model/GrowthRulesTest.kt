package com.emotionguard.core.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GrowthRulesTest {
    private val day1 = LocalDate.of(2026, 11, 17)

    private fun send(g: Growth, level: Level, replaced: Boolean = false, day: LocalDate = day1) =
        GrowthRules.onSent(g, level, replaced, day)

    @Test
    fun `레벨 경계`() {
        assertEquals(1, GrowthRules.levelOf(0))
        assertEquals(1, GrowthRules.levelOf(29))
        assertEquals(2, GrowthRules.levelOf(30))
        assertEquals(10, GrowthRules.levelOf(1500))
        assertEquals(10, GrowthRules.levelOf(99999))
    }

    @Test
    fun `행동별 경험치`() {
        assertEquals(2, send(Growth(), Level.SAFE).gainedXp)
        assertEquals(1, send(Growth(), Level.CAUTION).gainedXp)
        assertEquals(15, send(Growth(), Level.SAFE, replaced = true).gainedXp)
        assertEquals(0, send(Growth(), Level.DANGER).gainedXp)
    }

    @Test
    fun `위험한 말을 그대로 보내면 연속 기록이 끊긴다`() {
        val g = send(Growth(streakDays = 4), Level.DANGER).growth
        assertEquals(0, g.streakDays)
        assertEquals("2026-11-17", g.lastDangerDay)
    }

    @Test
    fun `위험 없이 하루를 넘기면 다음 날 첫 전송에 연속 보너스`() {
        var g = send(Growth(), Level.SAFE).growth                       // 11/17
        val next = send(g, Level.SAFE, day = day1.plusDays(1))          // 11/18 첫 전송
        assertEquals(1, next.growth.streakDays)
        assertEquals(2 + 5, next.gainedXp)
        g = next.growth
        assertEquals(2, send(g, Level.SAFE, day = day1.plusDays(1)).gainedXp) // 같은 날 두 번째는 보너스 없음
    }

    @Test
    fun `어제 위험한 말을 보냈거나 하루를 건너뛰면 연속 기록이 0부터`() {
        val dangerYesterday = send(Growth(), Level.DANGER).growth
        assertEquals(0, send(dangerYesterday, Level.SAFE, day = day1.plusDays(1)).growth.streakDays)

        val active = send(Growth(streakDays = 3), Level.SAFE).growth
        assertEquals(0, send(active, Level.SAFE, day = day1.plusDays(2)).growth.streakDays)
    }

    @Test
    fun `연속 보너스는 최대 35`() {
        val g = Growth(streakDays = 20, lastActiveDay = "2026-11-16")
        assertEquals(2 + 35, send(g, Level.SAFE).gainedXp)
    }

    @Test
    fun `하루 최대 60`() {
        var g = Growth()
        var total = 0
        repeat(10) {
            val u = send(g, Level.SAFE, replaced = true)
            total += u.gainedXp
            g = u.growth
        }
        assertEquals(60, total)
        // 다음 날은 다시 얻을 수 있다
        assertTrue(send(g, Level.SAFE, day = day1.plusDays(1)).gainedXp > 0)
    }

    @Test
    fun `레벨업하면 새 장식이 열린다`() {
        val u = send(Growth(xp = 25), Level.SAFE, replaced = true) // 25 → 40, Lv1 → Lv2
        assertTrue(u.leveledUp)
        assertEquals(listOf(Accessory.FLOWER), u.unlocked)
        assertTrue(u.growth.isUnlocked(Accessory.FLOWER))
        assertFalse(u.growth.isUnlocked(Accessory.GLASSES))
    }

    @Test
    fun `진행률과 남은 경험치`() {
        val g = Growth(xp = 55) // Lv2 (30~80)
        assertEquals(2, g.level)
        assertEquals(0.5f, g.progress, 0.001f)
        assertEquals(25, g.xpToNext)
        assertEquals(1f, Growth(xp = 2000).progress, 0.001f)
    }
}
