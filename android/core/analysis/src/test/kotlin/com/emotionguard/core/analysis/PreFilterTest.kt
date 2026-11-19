package com.emotionguard.core.analysis

import com.emotionguard.core.model.AnalysisSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreFilterTest {

    private fun isSafe(text: String, hasContext: Boolean = false) =
        PreFilter.check(text, hasContext) is PreFilter.Verdict.Safe

    @Test
    fun `웃음과 맞장구는 기기 안에서 안전 처리`() {
        listOf("ㅋㅋㅋㅋ", "ㅎㅎ", "ㅇㅋ", "응응", "고마워!!", "알겠어~", "넵 ㅎㅎ", "👍", "감사합니다", "ㅠㅠ").forEach {
            assertTrue("'$it'는 안전이어야 함", isSafe(it))
        }
    }

    @Test
    fun `욕설과 변형 표기는 반드시 서버로`() {
        listOf("ㅅㅂ", "시1발", "ㅅ ㅂ", "씨.발", "병신", "ㅂㅅ아", "개새끼", "꺼져", "죽여버린다", "ㅗ").forEach {
            assertTrue("'$it'는 서버로 가야 함", !isSafe(it))
        }
    }

    @Test
    fun `짧아도 짜증·비꼼 표현은 서버로`() {
        listOf("됐거든", "어쩌라고", "뭐래", "ㅉㅉ", "너 때문이야", "한심해", "짜증나").forEach {
            assertTrue("'$it'는 서버로 가야 함", !isSafe(it))
        }
    }

    @Test
    fun `긴 일반 문장은 서버로`() {
        assertTrue(!isSafe("오늘 회의 끝나고 같이 저녁 먹을래?"))
    }

    @Test
    fun `맥락이 있으면 짧은 문장 기준이 더 엄격하다`() {
        assertTrue(isSafe("그래서요"))            // 맥락 없음: 4글자 → 안전
        assertTrue(!isSafe("그래서요", hasContext = true)) // 맥락 있음: 3글자 초과 → 서버
    }

    @Test
    fun `안전 판정 결과는 LOCAL 출처`() {
        val verdict = PreFilter.check("ㅇㅇ", false) as PreFilter.Verdict.Safe
        assertEquals(AnalysisSource.LOCAL, verdict.result.source)
        assertTrue(verdict.result.score <= 30)
    }

    @Test
    fun `정규화는 공백·숫자·문장부호를 지운다`() {
        assertEquals("시발", PreFilter.normalize("시 1 발!!"))
        assertEquals("ㅅㅂ", PreFilter.normalize("ㅅ.ㅂ"))
    }
}
