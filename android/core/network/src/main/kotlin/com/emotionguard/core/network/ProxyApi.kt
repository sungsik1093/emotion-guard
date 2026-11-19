/**
 * 감정 가드 프록시 서버 API (server/ 폴더의 Cloudflare Worker).
 * 서버 API 명세는 server/README.md 참고.
 */
package com.emotionguard.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.HeaderMap
import retrofit2.http.POST

interface ProxyApi {
    @POST("v1/analyze")
    suspend fun analyze(
        @HeaderMap auth: Map<String, String>,
        @Body body: AnalyzeRequestDto,
    ): Response<AnalyzeResponseDto>
}

@Serializable
data class ChatLineDto(val speaker: String, val text: String)

@Serializable
data class AnalyzeRequestDto(
    val text: String,
    val context: List<ChatLineDto> = emptyList(),
    val contextConfidence: Float = 1f,
    val relationship: String = "UNKNOWN",
)

@Serializable
data class AlternativeDto(val tone: String, val text: String)

@Serializable
data class QuotaDto(val limit: Int, val remaining: Int, val resetAt: String)

@Serializable
data class AnalyzeResponseDto(
    val score: Int,
    val level: String,
    val category: String,
    val reason: String,
    /** 첫 번째 대안 (구버전 서버는 이것만 보낸다) */
    val alternative: String = "",
    val alternatives: List<AlternativeDto> = emptyList(),
    val quota: QuotaDto? = null,
)

@Serializable
data class ErrorBodyDto(val code: String, val message: String = "")

@Serializable
data class ErrorResponseDto(@SerialName("error") val error: ErrorBodyDto)
