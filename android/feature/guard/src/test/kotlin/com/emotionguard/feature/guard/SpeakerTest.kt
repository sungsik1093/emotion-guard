package com.emotionguard.feature.guard

import com.emotionguard.core.model.Speaker
import com.emotionguard.feature.guard.input.ChatContextReader.Companion.speakerOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeakerTest {
    private val w = 1080

    @Test
    fun `짧은 말풍선은 붙은 쪽으로`() {
        assertEquals(Speaker.ME, speakerOf(600, 1040, w, strict = true))
        assertEquals(Speaker.OTHER, speakerOf(40, 500, w, strict = true))
    }

    @Test
    fun `화면을 거의 다 채운 긴 말풍선은 여백이 좁은 쪽으로`() {
        // Google 메시지에서 실제로 본 내 긴 말풍선: [110, 1047]
        assertEquals(Speaker.ME, speakerOf(110, 1047, w, strict = true))
        assertEquals(Speaker.OTHER, speakerOf(40, 970, w, strict = true))
    }

    @Test
    fun `가운데 정렬은 엄격 모드에서 버린다`() {
        assertNull(speakerOf(400, 680, w, strict = true))
        assertEquals(Speaker.OTHER, speakerOf(400, 680, w, strict = false))
    }
}
