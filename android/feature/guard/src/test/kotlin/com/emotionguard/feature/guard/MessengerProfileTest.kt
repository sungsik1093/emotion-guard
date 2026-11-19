package com.emotionguard.feature.guard

import com.emotionguard.feature.guard.input.MessengerProfiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessengerProfileTest {
    private val kakao = MessengerProfiles.of("com.kakao.talk")!!

    @Test
    fun `시간 날짜 숫자 시스템 문구는 대화가 아니다`() {
        listOf("오후 3:12", "오전 10:05", "13:45", "2026년 11월 17일 화요일", "11월 17일", "어제", "1", "읽음", "민수님이 들어왔습니다.", "555-1234님과 문자 메시지로 대화 중")
            .forEach { assertTrue("'$it'는 노이즈", kakao.isNoise(it)) }
    }

    @Test
    fun `일반 대화는 노이즈가 아니다`() {
        listOf("오늘 몇 시에 만나?", "3시에 보자", "ㅋㅋㅋ").forEach { assertFalse("'$it'는 대화", kakao.isNoise(it)) }
    }

    @Test
    fun `짧은 뷰 ID에는 패키지 이름을 붙인다`() {
        assertEquals(listOf("com.kakao.talk:id/message_edit_text"), kakao.fullInputIds)
    }
}
