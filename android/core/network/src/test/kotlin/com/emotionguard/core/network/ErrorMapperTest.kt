package com.emotionguard.core.network

import com.emotionguard.core.model.AnalysisError
import org.junit.Assert.assertEquals
import org.junit.Test

class ErrorMapperTest {
    private fun body(code: String) = """{"error":{"code":"$code","message":"m"}}"""

    @Test
    fun `서버 오류 코드를 앱 오류로 바꾼다`() {
        assertEquals(AnalysisError.Unauthorized, ErrorMapper.map(401, body("UNAUTHORIZED"), null))
        assertEquals(AnalysisError.QuotaExceeded(daily = true, retryAfterSeconds = 3600), ErrorMapper.map(429, body("DAILY_LIMIT"), "3600"))
        assertEquals(AnalysisError.QuotaExceeded(daily = false, retryAfterSeconds = 60), ErrorMapper.map(429, body("MINUTE_LIMIT"), "60"))
        assertEquals(AnalysisError.Refused, ErrorMapper.map(422, body("ANALYSIS_REFUSED"), null))
        assertEquals(AnalysisError.Server("UPSTREAM_TIMEOUT"), ErrorMapper.map(504, body("UPSTREAM_TIMEOUT"), null))
    }

    @Test
    fun `본문이 깨져 있어도 상태 코드로 판단`() {
        assertEquals(AnalysisError.Server("HTTP_502"), ErrorMapper.map(502, "<html>bad gateway</html>", null))
        assertEquals(AnalysisError.QuotaExceeded(daily = false, retryAfterSeconds = null), ErrorMapper.map(429, null, null))
    }
}
