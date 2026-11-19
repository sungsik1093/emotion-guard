/**
 * 사용자 설정과 기록 통계 모델.
 */
package com.emotionguard.core.model

data class UserSettings(
    /** 보호 켜기/끄기 (VPN식 토글) */
    val guardEnabled: Boolean = false,
    val onboardingDone: Boolean = false,
    val consentAccepted: Boolean = false,
    /** 이 점수 이상이면 경고 (31~90) */
    val warnThreshold: Int = DEFAULT_THRESHOLD,
    /** 위험할 때 경고 카드를 자동으로 펼칠지 */
    val autoExpandOnDanger: Boolean = false,
    /** 메신저 밖에서도 캐릭터를 보일지 */
    val alwaysShowBubble: Boolean = false,
    /** 기록에 원문을 저장할지 (기본 꺼짐) */
    val saveOriginalText: Boolean = false,
    /** "추정" 전송도 통계에 넣을지 */
    val countLikelySends: Boolean = true,
    /** 감지를 끈 메신저 패키지 */
    val disabledPackages: Set<String> = emptySet(),
    /** 관계를 정하지 않은 대화방에 쓸 관계 */
    val defaultRelationship: Relationship = Relationship.UNKNOWN,
    /** 주간 리포트 알림 */
    val weeklyReportEnabled: Boolean = true,
) {
    fun isMessengerEnabled(packageName: String): Boolean = packageName !in disabledPackages

    companion object {
        const val DEFAULT_THRESHOLD = 61
        val THRESHOLD_RANGE = 31..90
    }
}

/** 기록 한 건 */
data class EmotionRecord(
    val id: Long,
    val timestamp: Long,
    val packageName: String,
    val score: Int,
    val category: Category,
    val warned: Boolean,
    val replaced: Boolean,
    val sendConfidence: SendConfidence,
    val analyzedLocally: Boolean,
    val originalText: String?,
)

/** 하루 평균 (차트 막대 하나) */
data class DailyAverage(val dayStartMillis: Long, val average: Float, val count: Int)

data class HistorySummary(
    val total: Int,
    val averageScore: Float,
    val warnedCount: Int,
    val replacedCount: Int,
    val byCategory: Map<Category, Int>,
) {
    /** 경고받은 메시지 중 대안으로 바꿔 보낸 비율 */
    val replaceRate: Float get() = if (warnedCount == 0) 0f else replacedCount.toFloat() / warnedCount

    companion object {
        val EMPTY = HistorySummary(0, 0f, 0, 0, emptyMap())
    }
}

/** 설정 → 진단 화면에 보여줄 감지 상태 */
data class DiagnosticsSnapshot(
    val packageName: String? = null,
    val inputFieldFound: Boolean = false,
    val inputFieldId: String? = null,
    val contextLines: List<ChatLine> = emptyList(),
    val contextConfidence: Float = 0f,
    val sendButtonFound: Boolean = false,
    val lastLatencyMillis: Long? = null,
    val lastSource: AnalysisSource? = null,
    val lastError: String? = null,
    val roomTitle: String? = null,
    val relationship: Relationship? = null,
    val updatedAt: Long = 0,
)
