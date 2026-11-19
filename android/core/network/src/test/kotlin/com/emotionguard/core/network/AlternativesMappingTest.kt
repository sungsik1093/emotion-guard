package com.emotionguard.core.network

import com.emotionguard.core.model.Alternative
import com.emotionguard.core.model.Tone
import org.junit.Assert.assertEquals
import org.junit.Test

class AlternativesMappingTest {
    private fun dto(alternative: String = "", alternatives: List<AlternativeDto> = emptyList()) =
        AnalyzeResponseDto(70, "DANGER", "BLAME", "r", alternative, alternatives)

    @Test
    fun `말투별 대안을 그대로 옮기고 모르는 말투와 빈 문장은 버린다`() {
        val out = dto(
            alternatives = listOf(
                AlternativeDto("SOFT", " 부드럽게 "),
                AlternativeDto("SARCASTIC", "모르는 말투"),
                AlternativeDto("FIRM", "  "),
                AlternativeDto("PLAYFUL", "가볍게"),
            ),
        ).toAlternatives()
        assertEquals(listOf(Alternative(Tone.SOFT, "부드럽게"), Alternative(Tone.PLAYFUL, "가볍게")), out)
    }

    @Test
    fun `구버전 서버의 대안 하나도 받아준다`() {
        assertEquals(listOf(Alternative(Tone.SOFT, "옛 대안")), dto(alternative = "옛 대안").toAlternatives())
        assertEquals(emptyList<Alternative>(), dto().toAlternatives())
    }
}
