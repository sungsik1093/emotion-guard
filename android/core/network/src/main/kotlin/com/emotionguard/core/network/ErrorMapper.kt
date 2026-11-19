/**
 * 서버 오류 응답(HTTP 상태 + { error: { code } })을 앱의 AnalysisError로 바꾼다.
 * 순수 함수라 단위 테스트한다.
 */
package com.emotionguard.core.network

import com.emotionguard.core.model.AnalysisError
import kotlinx.serialization.json.Json

object ErrorMapper {
    private val json = Json { ignoreUnknownKeys = true }

    fun map(status: Int, body: String?, retryAfter: String?): AnalysisError {
        val code = body?.let { runCatching { json.decodeFromString<ErrorResponseDto>(it).error.code }.getOrNull() }
        return when {
            status == 401 -> AnalysisError.Unauthorized
            status == 429 -> AnalysisError.QuotaExceeded(
                daily = code == "DAILY_LIMIT",
                retryAfterSeconds = retryAfter?.toLongOrNull(),
            )
            status == 422 || code == "ANALYSIS_REFUSED" -> AnalysisError.Refused
            else -> AnalysisError.Server(code ?: "HTTP_$status")
        }
    }
}
