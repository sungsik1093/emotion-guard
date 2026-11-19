package com.emotionguard.feature.guard

import com.emotionguard.core.model.SendConfidence
import com.emotionguard.feature.guard.input.SendDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SendDetectorTest {
    private val window = 1_500L

    private fun typed(d: SendDetector, text: String, t: Long = 0) {
        // 한 글자씩 입력
        text.indices.forEach { d.onText(text.substring(0, it + 1), t) }
    }

    @Test
    fun `전송 버튼 클릭 후 입력창이 비면 CONFIRMED`() {
        val d = SendDetector(window)
        typed(d, "안녕하세요")
        d.onSendClick(1_000)
        d.onText("", 1_050)
        assertNull("창이 끝나기 전에는 판단하지 않음", d.poll(1_200))
        assertEquals(SendDetector.Decision("안녕하세요", SendConfidence.CONFIRMED), d.poll(2_600))
    }

    @Test
    fun `입력창이 한 번에 비워지기만 하면 LIKELY`() {
        val d = SendDetector(window)
        typed(d, "보낼까 말까")
        d.onText("", 1_000)
        assertEquals(SendConfidence.LIKELY, d.poll(2_600)?.confidence)
    }

    @Test
    fun `비워진 뒤 내 말풍선이 보이면 CONFIRMED`() {
        val d = SendDetector(window)
        typed(d, "곧 갈게")
        d.onText("", 1_000)
        d.onMyBubbles(listOf("아까 말", "곧 갈게"), 1_300)
        assertEquals(SendConfidence.CONFIRMED, d.poll(2_600)?.confidence)
    }

    @Test
    fun `비운 직후 다시 쓰기 시작하면 지운 것으로 본다`() {
        val d = SendDetector(window)
        typed(d, "이건 지울래")
        d.onText("", 1_000)
        d.onText("다", 1_200)
        assertNull(d.poll(2_600))
    }

    @Test
    fun `한 글자를 지운 건 전송이 아니다`() {
        val d = SendDetector(window)
        d.onText("ㅋ", 0)
        d.onText("", 100)
        assertNull(d.poll(2_000))
    }

    @Test
    fun `클릭했지만 글이 그대로면 전송 실패로 본다`() {
        val d = SendDetector(window)
        typed(d, "네트워크 오류")
        d.onSendClick(1_000)
        assertNull(d.poll(2_600))
    }
}
