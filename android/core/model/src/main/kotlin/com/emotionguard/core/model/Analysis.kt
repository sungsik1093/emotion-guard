/**
 * 감정 분석 결과와 관련된 순수 모델.
 * 안드로이드에 의존하지 않으므로 어느 모듈에서나 쓰고, JVM 단위 테스트도 빠르다.
 */
package com.emotionguard.core.model

/** 공격성 유형. 서버 응답의 category 코드와 이름이 같다. */
enum class Category(val label: String) {
    NONE("해당 없음"),
    PROFANITY("욕설"),
    BELITTLING("비하·무시"),
    SARCASM("비꼼·짜증"),
    BLAME("비난·탓하기"),
    SEXUAL("성적 표현"),
    HATE("혐오·차별"),
    THREAT("위협");

    companion object {
        fun fromCode(code: String?): Category = entries.firstOrNull { it.name == code } ?: NONE
    }
}

/** 점수 구간: 0~30 안전, 31~60 주의, 61~100 위험 */
enum class Level(val label: String) {
    SAFE("안전"),
    CAUTION("주의"),
    DANGER("위험");

    companion object {
        fun of(score: Int): Level = when {
            score <= 30 -> SAFE
            score <= 60 -> CAUTION
            else -> DANGER
        }
    }
}

/** 결과를 어디서 얻었는지. 기기 안 판정은 비용이 0이다. */
enum class AnalysisSource { REMOTE, LOCAL, CACHE }

/** 대안 문장의 말투 (서버 tone 코드와 이름이 같다) */
enum class Tone(val label: String) {
    SOFT("부드럽게"),
    FIRM("단호하지만 정중하게"),
    PLAYFUL("가볍게");

    companion object {
        fun fromCode(code: String?): Tone? = entries.firstOrNull { it.name == code }
    }
}

data class Alternative(val tone: Tone, val text: String)

data class AnalysisResult(
    val text: String,
    val score: Int,
    val category: Category,
    val reason: String,
    /** 말투별 대안 (SOFT → FIRM → PLAYFUL 순, 최대 3개). 안전하면 빈 목록 */
    val alternatives: List<Alternative>,
    val source: AnalysisSource,
) {
    val level: Level get() = Level.of(score)
    val hasAlternative: Boolean get() = alternatives.isNotEmpty()

    /** 첫 번째 대안 (없으면 빈 문자열) */
    val alternative: String get() = alternatives.firstOrNull()?.text.orEmpty()
}

/** 서버 할당량 */
data class Quota(val limit: Int, val remaining: Int, val resetAtIso: String)

/** 분석 실패 이유. 캐릭터는 모두 OFFLINE 표정으로 보여주지만 안내 문구가 다르다. */
sealed interface AnalysisError {
    data object Network : AnalysisError
    data object Unauthorized : AnalysisError
    data class QuotaExceeded(val daily: Boolean, val retryAfterSeconds: Long?) : AnalysisError
    data object Refused : AnalysisError
    data class Server(val code: String) : AnalysisError

    val message: String
        get() = when (this) {
            Network -> "인터넷 연결을 확인해 주세요."
            Unauthorized -> "앱 인증에 실패했어요. 잠시 후 다시 시도해 주세요."
            is QuotaExceeded -> if (daily) "오늘 무료 분석을 모두 썼어요." else "잠시 후 다시 분석할게요."
            Refused -> "이 문장은 분석할 수 없어요."
            is Server -> "분석 서버에 문제가 생겼어요."
        }
}

sealed interface AnalysisOutcome {
    data class Success(val result: AnalysisResult) : AnalysisOutcome
    data class Failure(val error: AnalysisError) : AnalysisOutcome
}
