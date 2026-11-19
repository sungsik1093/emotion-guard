/**
 * 분석 파이프라인 (디자인 문서 §5.5)
 *
 *   PreFilter(기기 안) → PiiMasker(기기 안) → 캐시 → 프록시 서버
 *
 * 진행 중 요청 취소는 호출하는 쪽(collectLatest)이 맡는다. 이 함수는 취소에 협조적이다.
 */
package com.emotionguard.core.analysis

import com.emotionguard.core.model.AnalysisOutcome
import com.emotionguard.core.model.AnalysisResult
import com.emotionguard.core.model.AnalysisSource
import com.emotionguard.core.model.ChatContext
import com.emotionguard.core.model.Quota
import com.emotionguard.core.model.Relationship
import com.emotionguard.core.network.RemoteAnalyzer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

@Singleton
class AnalysisRepository @Inject constructor(
    private val remote: RemoteAnalyzer,
) {
    private val cache = LruCache<String, AnalysisResult>(CACHE_SIZE)

    val quota: StateFlow<Quota?> get() = remote.quota

    /**
     * @param relationship 대화 상대와의 관계. 직장이면 1차 필터도 더 엄격하게 본다.
     */
    suspend fun analyze(
        text: String,
        context: ChatContext,
        relationship: Relationship = Relationship.UNKNOWN,
    ): AnalysisOutcome {
        val trimmed = text.trim()

        val strict = context.usable || relationship == Relationship.WORK
        when (val verdict = PreFilter.check(trimmed, hasContext = strict)) {
            is PreFilter.Verdict.Safe -> return AnalysisOutcome.Success(verdict.result)
            PreFilter.Verdict.NeedsRemote -> Unit
        }

        val maskedText = PiiMasker.mask(trimmed)
        val maskedContext = if (context.usable) {
            context.copy(lines = context.lines.map { it.copy(text = PiiMasker.mask(it.text)) })
        } else {
            ChatContext.EMPTY
        }

        val key = cacheKey(maskedText, maskedContext, relationship)
        cache[key]?.let { return AnalysisOutcome.Success(it.copy(text = trimmed, source = AnalysisSource.CACHE)) }

        return when (val outcome = remote.analyze(maskedText, maskedContext, relationship)) {
            is AnalysisOutcome.Success -> {
                val result = outcome.result.copy(text = trimmed)
                cache[key] = result
                AnalysisOutcome.Success(result)
            }
            is AnalysisOutcome.Failure -> outcome
        }
    }

    private fun cacheKey(text: String, context: ChatContext, relationship: Relationship): String =
        buildString {
            append(relationship.name).append('\u0002').append(text)
            context.lines.forEach { append('\u0001').append(it.speaker.name).append(':').append(it.text) }
        }

    companion object {
        const val CACHE_SIZE = 50
    }
}

/** 최근에 쓴 항목을 남기는 작은 캐시 */
internal class LruCache<K, V>(private val maxSize: Int) {
    private val map = object : LinkedHashMap<K, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > maxSize
    }

    @Synchronized operator fun get(key: K): V? = map[key]

    @Synchronized operator fun set(key: K, value: V) {
        map[key] = value
    }

    @Synchronized fun size(): Int = map.size
}
