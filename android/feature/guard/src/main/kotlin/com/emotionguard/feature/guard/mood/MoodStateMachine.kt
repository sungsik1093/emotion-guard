/**
 * 캐릭터 상태 전이 (디자인 문서 §4.4). 순수 함수라 단위 테스트한다.
 *
 *  IDLE ──입력──▶ THINKING ──결과──▶ SAFE / CAUTION / DANGER
 *    ▲                │                    │
 *    │             오류│                    │대안으로 바꾸기, 위험한 말을 고쳐 씀
 *    │                ▼                    ▼
 *    └──입력창 비움── OFFLINE             PRAISE ──2초──▶ (결과 상태)
 */
package com.emotionguard.feature.guard.mood

import com.emotionguard.core.model.AnalysisError
import com.emotionguard.core.model.AnalysisResult
import com.emotionguard.core.model.Level
import com.emotionguard.core.model.MoodState

data class MoodUi(
    val state: MoodState = MoodState.IDLE,
    /** 경고 카드에 보여줄 마지막 분석 결과 */
    val result: AnalysisResult? = null,
    val error: AnalysisError? = null,
)

sealed interface MoodEvent {
    data object InputCleared : MoodEvent
    data object Thinking : MoodEvent
    data class Analyzed(val result: AnalysisResult) : MoodEvent
    data class Failed(val error: AnalysisError) : MoodEvent
    data object Replaced : MoodEvent
    data object PraiseExpired : MoodEvent
}

object MoodStateMachine {
    const val PRAISE_DURATION_MS = 2_000L

    fun reduce(current: MoodUi, event: MoodEvent): MoodUi = when (event) {
        MoodEvent.InputCleared -> MoodUi()

        MoodEvent.Thinking ->
            if (current.state == MoodState.PRAISE) current else current.copy(state = MoodState.THINKING, error = null)

        is MoodEvent.Analyzed -> {
            val newLevel = event.result.level
            val previousLevel = current.result?.level
            val improved = previousLevel != null && previousLevel != Level.SAFE && newLevel == Level.SAFE
            // 칭찬 중에 안전 결과가 오면 칭찬을 끝까지 보여주고, 끝나면 안전으로 간다
            val keepPraise = current.state == MoodState.PRAISE && newLevel == Level.SAFE
            MoodUi(
                state = if (improved || keepPraise) MoodState.PRAISE else MoodState.of(newLevel),
                result = event.result,
            )
        }

        is MoodEvent.Failed -> current.copy(state = MoodState.OFFLINE, error = event.error)

        // 바꾸기 전 결과는 이제 입력창 글과 맞지 않으므로 버린다.
        // (그대로 두면 칭찬이 끝난 뒤 옛날 "위험" 표정으로 돌아간다) 새 문장은 곧 다시 분석된다.
        MoodEvent.Replaced -> MoodUi(state = MoodState.PRAISE)

        MoodEvent.PraiseExpired ->
            if (current.state != MoodState.PRAISE) {
                current
            } else {
                current.copy(state = current.result?.let { MoodState.of(it.level) } ?: MoodState.IDLE)
            }
    }
}
