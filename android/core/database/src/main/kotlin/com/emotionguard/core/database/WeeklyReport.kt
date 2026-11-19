/**
 * 주간 리포트 문장 만들기 (디자인 문서 §15.4). 순수 함수라 단위 테스트한다.
 * 메시지 원문은 쓰지 않는다. 숫자와 유형만 쓴다.
 */
package com.emotionguard.core.database

import com.emotionguard.core.model.Category
import com.emotionguard.core.model.EmotionRecord
import com.emotionguard.core.model.Growth
import com.emotionguard.core.model.Level
import kotlin.math.abs
import kotlin.math.roundToInt

data class WeeklyReportText(val title: String, val body: String)

object WeeklyReport {

    fun build(thisWeek: List<EmotionRecord>, lastWeek: List<EmotionRecord>, growth: Growth): WeeklyReportText {
        if (thisWeek.isEmpty()) {
            return WeeklyReportText(
                title = "이번 주 감정 가드 리포트",
                body = "이번 주엔 기록이 없어요. 보호를 켜두면 다음 주엔 리포트를 보여드릴게요. (캐릭터 Lv ${growth.level})",
            )
        }

        val summary = HistoryStats.summarize(thisWeek)
        val avg = summary.averageScore.roundToInt()
        val title = when (Level.of(avg)) {
            Level.SAFE -> "이번 주는 따뜻한 대화가 많았어요"
            Level.CAUTION -> "이번 주엔 말이 조금 날카로웠어요"
            Level.DANGER -> "이번 주는 힘든 대화가 많았나 봐요"
        }

        val parts = mutableListOf<String>()
        parts += "보낸 메시지 ${summary.total}개, 평균 감정 온도 ${avg}°${trend(avg, lastWeek)}."
        if (summary.replacedCount > 0) {
            parts += "대안으로 바꿔 보낸 게 ${summary.replacedCount}번이에요. 잘하고 있어요!"
        } else if (summary.warnedCount > 0) {
            parts += "경고가 ${summary.warnedCount}번 있었어요. 다음엔 대안 문장도 한번 써보세요."
        }
        topCategory(summary.byCategory)?.let { parts += "가장 많았던 유형은 '${it.label}'이에요." }
        parts += "캐릭터 Lv ${growth.level}" + if (growth.streakDays > 0) ", ${growth.streakDays}일 연속 기록 중." else "."

        return WeeklyReportText(title, parts.joinToString(" "))
    }

    private fun trend(avg: Int, lastWeek: List<EmotionRecord>): String {
        if (lastWeek.isEmpty()) return ""
        val last = HistoryStats.summarize(lastWeek).averageScore.roundToInt()
        val diff = avg - last
        return when {
            diff == 0 -> "(지난주와 같아요)"
            diff < 0 -> "(지난주보다 ${abs(diff)}° 낮아졌어요)"
            else -> "(지난주보다 ${diff}° 높아졌어요)"
        }
    }

    private fun topCategory(counts: Map<Category, Int>): Category? =
        counts.entries.maxWithOrNull(compareBy<Map.Entry<Category, Int>> { it.value }.thenBy { -it.key.ordinal })?.key
}
