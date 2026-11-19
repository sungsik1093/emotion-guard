package com.emotionguard.core.analysis

import org.junit.Assert.assertEquals
import org.junit.Test

/** 서버(server/test/pii.test.ts)와 같은 사례로 검사한다. 두 구현이 어긋나지 않게. */
class PiiMaskerTest {

    @Test
    fun `개인정보 형식을 가린다`() {
        val cases = mapOf(
            "내 번호 010-1234-5678로 연락해" to "내 번호 [전화번호]로 연락해",
            "01012345678" to "[전화번호]",
            "+82 10 1234 5678 이야" to "[전화번호] 이야",
            "회사 02-123-4567" to "회사 [전화번호]",
            "메일은 me.test@example.co.kr" to "메일은 [이메일]",
            "주민번호 990101-1234567" to "주민번호 [주민번호]",
            "카드 1234 5678 9012 3456" to "카드 [카드번호]",
            "계좌 110-123-456789 로 보내" to "계좌 [계좌번호] 로 보내",
            "국민 123456789012" to "국민 [계좌번호]",
        )
        cases.forEach { (input, expected) -> assertEquals(input, expected, PiiMasker.mask(input)) }
    }

    @Test
    fun `평범한 숫자와 날짜는 그대로`() {
        listOf("3시에 보자", "2026년 11월 17일", "1000원만", "ㅋㅋㅋ 100점").forEach {
            assertEquals(it, PiiMasker.mask(it))
        }
    }
}
