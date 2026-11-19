package com.emotionguard.feature.guard

import com.emotionguard.core.model.Alternative
import com.emotionguard.core.model.AnalysisError
import com.emotionguard.core.model.Tone
import com.emotionguard.core.model.AnalysisResult
import com.emotionguard.core.model.AnalysisSource
import com.emotionguard.core.model.Category
import com.emotionguard.core.model.MoodState
import com.emotionguard.feature.guard.mood.MoodEvent
import com.emotionguard.feature.guard.mood.MoodStateMachine.reduce
import com.emotionguard.feature.guard.mood.MoodUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoodStateMachineTest {
    private fun result(score: Int) = AnalysisResult(
        "t", score, Category.NONE, "r",
        if (score > 30) listOf(Alternative(Tone.SOFT, "alt")) else emptyList(),
        AnalysisSource.REMOTE,
    )

    @Test
    fun `점수에 따라 안전 주의 위험`() {
        assertEquals(MoodState.SAFE, reduce(MoodUi(), MoodEvent.Analyzed(result(10))).state)
        assertEquals(MoodState.CAUTION, reduce(MoodUi(), MoodEvent.Analyzed(result(45))).state)
        assertEquals(MoodState.DANGER, reduce(MoodUi(), MoodEvent.Analyzed(result(80))).state)
    }

    @Test
    fun `위험한 말을 고쳐 써서 안전해지면 칭찬`() {
        val danger = reduce(MoodUi(), MoodEvent.Analyzed(result(80)))
        val fixed = reduce(danger, MoodEvent.Analyzed(result(10)))
        assertEquals(MoodState.PRAISE, fixed.state)
        assertEquals(MoodState.SAFE, reduce(fixed, MoodEvent.PraiseExpired).state)
    }

    @Test
    fun `처음부터 안전하면 칭찬하지 않는다`() {
        val safe = reduce(MoodUi(), MoodEvent.Analyzed(result(10)))
        assertEquals(MoodState.SAFE, reduce(safe, MoodEvent.Analyzed(result(5))).state)
    }

    @Test
    fun `대안으로 바꾸면 칭찬, 끝나도 옛날 위험 표정으로 돌아가지 않는다`() {
        val danger = reduce(MoodUi(), MoodEvent.Analyzed(result(80)))
        val praise = reduce(danger, MoodEvent.Replaced)
        assertEquals(MoodState.PRAISE, praise.state)
        assertNull(praise.result)
        assertEquals(MoodState.IDLE, reduce(praise, MoodEvent.PraiseExpired).state)
        // 칭찬 중에 바뀐 문장이 안전하다고 나오면 칭찬을 유지하고, 끝나면 안전
        val analyzed = reduce(praise, MoodEvent.Analyzed(result(10)))
        assertEquals(MoodState.PRAISE, analyzed.state)
        assertEquals(MoodState.SAFE, reduce(analyzed, MoodEvent.PraiseExpired).state)
        // 칭찬 중이라도 위험 결과가 오면 바로 위험
        assertEquals(MoodState.DANGER, reduce(praise, MoodEvent.Analyzed(result(80))).state)
    }

    @Test
    fun `생각 중에도 이전 결과는 카드에 남아 있다`() {
        val danger = reduce(MoodUi(), MoodEvent.Analyzed(result(80)))
        val thinking = reduce(danger, MoodEvent.Thinking)
        assertEquals(MoodState.THINKING, thinking.state)
        assertEquals(80, thinking.result?.score)
    }

    @Test
    fun `칭찬 중에는 생각 중으로 덮지 않는다`() {
        val praise = MoodUi(state = MoodState.PRAISE)
        assertEquals(MoodState.PRAISE, reduce(praise, MoodEvent.Thinking).state)
    }

    @Test
    fun `오류는 오프라인, 입력창을 비우면 대기`() {
        val offline = reduce(MoodUi(), MoodEvent.Failed(AnalysisError.Network))
        assertEquals(MoodState.OFFLINE, offline.state)
        val cleared = reduce(offline, MoodEvent.InputCleared)
        assertEquals(MoodState.IDLE, cleared.state)
        assertNull(cleared.result)
        assertNull(cleared.error)
    }
}
