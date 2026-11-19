/**
 * 캐릭터 성장 (디자인 문서 §15.3). 경험치 규칙은 순수 함수라 단위 테스트한다.
 *
 * | 행동                              | XP                        |
 * | 안전한 메시지 전송                | +2  (주의 +1)              |
 * | 대안으로 바꿔 보냄                | +15                       |
 * | 위험 메시지를 그대로 보냄         | 0, 연속 기록 끊김          |
 * | 위험 없이 하루를 넘김 (연속 N일)  | 그날 첫 전송 때 +5×N (최대 35) |
 * | 하루 최대                         | 60                        |
 */
package com.emotionguard.core.model

import java.time.LocalDate
import kotlinx.serialization.Serializable

/** 레벨에 따라 열리는 캐릭터 장식 */
@Serializable
enum class Accessory(val label: String, val unlockLevel: Int) {
    NONE("없음", 1),
    FLOWER("꽃", 2),
    GLASSES("동그란 안경", 3),
    HEADPHONES("헤드폰", 5),
    PARTY_HAT("파티 모자", 7),
    CROWN("왕관", 10),
}

@Serializable
data class Growth(
    val xp: Int = 0,
    /** 위험 메시지 없이 이어온 날 수 */
    val streakDays: Int = 0,
    /** 마지막으로 메시지를 보낸 날 (yyyy-MM-dd) */
    val lastActiveDay: String? = null,
    /** 마지막으로 위험 메시지를 그대로 보낸 날 */
    val lastDangerDay: String? = null,
    /** 오늘 얻은 XP (하루 최대치 계산용) */
    val todayXp: Int = 0,
    val todayDay: String? = null,
    val selectedAccessory: Accessory = Accessory.NONE,
) {
    val level: Int get() = GrowthRules.levelOf(xp)

    /** 다음 레벨까지 진행률 0~1. 최고 레벨이면 1 */
    val progress: Float
        get() {
            val lv = level
            if (lv >= GrowthRules.MAX_LEVEL) return 1f
            val from = GrowthRules.LEVEL_XP[lv - 1]
            val to = GrowthRules.LEVEL_XP[lv]
            return (xp - from).toFloat() / (to - from)
        }

    /** 다음 레벨까지 남은 XP. 최고 레벨이면 0 */
    val xpToNext: Int
        get() = if (level >= GrowthRules.MAX_LEVEL) 0 else GrowthRules.LEVEL_XP[level] - xp

    fun isUnlocked(accessory: Accessory): Boolean = level >= accessory.unlockLevel
}

data class GrowthUpdate(
    val growth: Growth,
    val gainedXp: Int,
    val leveledUp: Boolean,
    /** 이번에 새로 열린 장식 */
    val unlocked: List<Accessory>,
)

object GrowthRules {
    /** 레벨 n이 되는 데 필요한 누적 XP (index = n - 1) */
    val LEVEL_XP = listOf(0, 30, 80, 150, 250, 400, 600, 850, 1150, 1500)
    val MAX_LEVEL = LEVEL_XP.size

    const val SAFE_XP = 2
    const val CAUTION_XP = 1
    const val REPLACED_XP = 15
    const val STREAK_XP_PER_DAY = 5
    const val MAX_STREAK_BONUS = 35
    const val DAILY_CAP = 60

    fun levelOf(xp: Int): Int = LEVEL_XP.indexOfLast { xp >= it } + 1

    /** 메시지를 하나 보냈을 때 */
    fun onSent(current: Growth, level: Level, replaced: Boolean, today: LocalDate): GrowthUpdate {
        val day = today.toString()
        val yesterday = today.minusDays(1).toString()
        var g = if (current.todayDay == day) current else current.copy(todayXp = 0, todayDay = day)

        // 오늘 첫 전송이면 연속 기록 계산: 어제 보냈고 어제 위험한 말이 없었으면 +1
        var bonus = 0
        if (g.lastActiveDay != day) {
            val continued = g.lastActiveDay == yesterday && g.lastDangerDay != yesterday
            val streak = if (continued) g.streakDays + 1 else 0
            bonus = minOf(streak * STREAK_XP_PER_DAY, MAX_STREAK_BONUS)
            g = g.copy(streakDays = streak, lastActiveDay = day)
        }

        val dangerSent = level == Level.DANGER && !replaced
        val base = when {
            replaced -> REPLACED_XP
            dangerSent -> 0
            level == Level.CAUTION -> CAUTION_XP
            else -> SAFE_XP
        }
        if (dangerSent) g = g.copy(streakDays = 0, lastDangerDay = day)

        val gained = (base + bonus).coerceAtMost(DAILY_CAP - g.todayXp).coerceAtLeast(0)
        val before = current.level
        g = g.copy(xp = g.xp + gained, todayXp = g.todayXp + gained)
        val after = g.level
        val unlocked = Accessory.entries.filter { it.unlockLevel in (before + 1)..after }
        return GrowthUpdate(g, gained, after > before, unlocked)
    }
}
