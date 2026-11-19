/**
 * 프록시 서버에 분석을 요청한다.
 * - 401이면 토큰을 새로 받아 한 번만 다시 시도한다.
 * - 응답에 실린 남은 할당량을 [quota]로 내보낸다 (홈 화면 표시용).
 */
package com.emotionguard.core.network

import com.emotionguard.core.model.AnalysisError
import com.emotionguard.core.model.AnalysisOutcome
import com.emotionguard.core.model.AnalysisResult
import com.emotionguard.core.model.AnalysisSource
import com.emotionguard.core.model.Category
import com.emotionguard.core.model.ChatContext
import com.emotionguard.core.model.Alternative
import com.emotionguard.core.model.Quota
import com.emotionguard.core.model.Relationship
import com.emotionguard.core.model.Speaker
import com.emotionguard.core.model.Tone
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class RemoteAnalyzer @Inject constructor(
    private val api: ProxyApi,
    private val auth: AuthHeadersProvider,
) {
    private val _quota = MutableStateFlow<Quota?>(null)
    val quota: StateFlow<Quota?> = _quota.asStateFlow()

    suspend fun analyze(text: String, context: ChatContext, relationship: Relationship): AnalysisOutcome {
        val body = AnalyzeRequestDto(
            text = text,
            context = if (context.usable) {
                context.lines.map { ChatLineDto(if (it.speaker == Speaker.ME) "me" else "other", it.text) }
            } else {
                emptyList()
            },
            contextConfidence = context.confidence,
            relationship = relationship.name,
        )
        return try {
            var response = api.analyze(auth.headers(), body)
            if (response.code() == 401) response = api.analyze(auth.headers(forceRefresh = true), body)

            val dto = response.body()
            if (response.isSuccessful && dto != null) {
                dto.quota?.let { _quota.value = Quota(it.limit, it.remaining, it.resetAt) }
                AnalysisOutcome.Success(
                    AnalysisResult(
                        text = text,
                        score = dto.score.coerceIn(0, 100),
                        category = Category.fromCode(dto.category),
                        reason = dto.reason,
                        alternatives = dto.toAlternatives(),
                        source = AnalysisSource.REMOTE,
                    ),
                )
            } else {
                val error = ErrorMapper.map(
                    status = response.code(),
                    body = response.errorBody()?.string(),
                    retryAfter = response.headers()["Retry-After"],
                )
                if (error is AnalysisError.QuotaExceeded && error.daily) {
                    _quota.value = _quota.value?.copy(remaining = 0)
                }
                AnalysisOutcome.Failure(error)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            AnalysisOutcome.Failure(AnalysisError.Network)
        } catch (e: Exception) {
            AnalysisOutcome.Failure(AnalysisError.Server(e.javaClass.simpleName))
        }
    }
}

/** 서버 대안 목록을 앱 모델로. 모르는 말투는 버리고, 구버전 서버(alternative 하나)도 받아준다. */
internal fun AnalyzeResponseDto.toAlternatives(): List<Alternative> {
    val list = alternatives.mapNotNull { dto ->
        val tone = Tone.fromCode(dto.tone) ?: return@mapNotNull null
        dto.text.trim().takeIf { it.isNotEmpty() }?.let { Alternative(tone, it) }
    }
    if (list.isNotEmpty()) return list
    return alternative.trim().takeIf { it.isNotEmpty() }?.let { listOf(Alternative(Tone.SOFT, it)) }.orEmpty()
}