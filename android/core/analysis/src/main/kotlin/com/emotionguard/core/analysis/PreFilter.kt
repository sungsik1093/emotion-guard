/**
 * 기기 안 1차 필터 (디자인 문서 §5.5).
 *
 * 확실히 무난한 문장은 서버를 부르지 않고 바로 "안전"으로 처리한다.
 * 개인정보를 덜 보내고, 비용과 지연도 줄어든다.
 *
 * 원칙: 애매하면 무조건 서버로 보낸다. 이 필터가 위험한 말을 놓치면 안 된다.
 * - 위험·짜증 단어가 하나라도 있으면 → 서버 (변형 표기 "시1발", "ㅅ ㅂ"도 정규화해서 잡는다)
 * - 웃음·이모지·짧은 맞장구만 있으면 → 안전
 * - 아주 짧은 문장(맥락이 있으면 더 짧게)이면 → 안전
 * - 그 외 → 서버
 */
package com.emotionguard.core.analysis

import com.emotionguard.core.model.AnalysisResult
import com.emotionguard.core.model.AnalysisSource
import com.emotionguard.core.model.Category

object PreFilter {

    sealed interface Verdict {
        data class Safe(val result: AnalysisResult) : Verdict
        data object NeedsRemote : Verdict
    }

    /** 정규화한 문장에 포함되면 반드시 서버로 보내는 말 */
    private val riskyStems = listOf(
        // 욕설·비속어
        "시발", "씨발", "십발", "ㅅㅂ", "ㅆㅂ", "ㅅㅃ", "병신", "븅신", "ㅂㅅ", "개새", "새끼", "ㅅㄲ",
        "좆", "존나", "졸라", "ㅈㄴ", "지랄", "ㅈㄹ", "닥쳐", "꺼져", "엿먹", "염병", "미친", "ㅁㅊ",
        "또라이", "돌았", "등신", "애미", "에미", "느금", "니미", "ㄴㄱㅁ", "썅", "ㅗ", "ㅄ",
        "개같", "개소리", "개빡", "빡치", "빡쳐", "꺼지", "ㄲㅈ", "ㄷㅊ", "뒤져", "뒤질", "쓰레기",
        // 위협
        "죽어", "죽여", "죽일", "패죽", "가만안둬", "가만안둔",
        // 비하·무시
        "한심", "멍청", "찐따", "바보", "수준하고는", "주제에", "꼴값", "어이없", "역겹", "토나와",
        "못생", "찌질", "재수없", "싸가지", "꼰대", "돼지", "노답", "극혐",
        // 짜증·비꼼·탓하기
        "짜증", "됐거든", "됐어", "알아서해", "어련하시", "너때문", "니탓", "네탓", "너나잘해",
        "왜그래", "뭐래", "그래서뭐", "어쩌라고", "ㅉㅉ", "쯧쯧", "답답", "실망",
        // 성적 표현·혐오 (대표 표현만, 나머지는 서버가 판단)
        "섹스", "야동", "한남", "김치녀", "틀딱", "급식충", "맘충",
    )

    /** 이 말들로만 이루어진 문장은 안전 */
    private val safePhrases = setOf(
        "응", "웅", "어", "엉", "ㅇㅇ", "ㅇㅋ", "ㅇㅋㅇㅋ", "오키", "오케이", "ok", "okay",
        "네", "넵", "넹", "예", "알겠어", "알겠습니다", "알았어", "그래", "좋아", "좋아요", "굿", "good",
        "고마워", "고마워요", "감사합니다", "감사해요", "땡큐", "thanks", "thx", "ㄱㅅ", "ㄳ",
        "안녕", "안녕하세요", "잘자", "잘자요", "굿밤", "하이", "hi", "hello", "ㅎㅇ",
        "미안", "미안해", "죄송합니다", "괜찮아", "괜찮아요", "축하해", "사랑해", "보고싶어",
        "수고했어", "수고하셨습니다", "화이팅", "파이팅", "대박", "헐", "와", "우와",
    )

    private const val MAX_SAFE_LENGTH = 5
    private const val MAX_SAFE_LENGTH_WITH_CONTEXT = 3

    fun check(text: String, hasContext: Boolean): Verdict {
        val normalized = normalize(text)
        if (riskyStems.any { normalized.contains(it) }) return Verdict.NeedsRemote

        val core = stripReactions(normalized)
        val safe = when {
            core.isEmpty() -> true // 웃음·이모지·문장부호만
            core in safePhrases -> true
            core.length <= (if (hasContext) MAX_SAFE_LENGTH_WITH_CONTEXT else MAX_SAFE_LENGTH) -> true
            else -> false
        }
        if (!safe) return Verdict.NeedsRemote

        return Verdict.Safe(
            AnalysisResult(
                text = text,
                score = 5,
                category = Category.NONE,
                reason = "짧고 무난한 말이에요.",
                alternatives = emptyList(),
                source = AnalysisSource.LOCAL,
            ),
        )
    }

    /**
     * 변형 표기를 잡기 위한 정규화: 소문자, 공백·숫자·문장부호 제거.
     * "시 1 발" → "시발", "ㅅ.ㅂ" → "ㅅㅂ"
     */
    internal fun normalize(text: String): String = buildString {
        for (ch in text.lowercase()) {
            if (ch.isLetter() || isJamo(ch)) append(ch)
        }
    }

    /** 웃음(ㅋ, ㅎ), 울음(ㅠ, ㅜ) 같은 반응 글자를 뺀 나머지 */
    internal fun stripReactions(normalized: String): String =
        normalized.filterNot { it in REACTION_CHARS }

    private fun isJamo(ch: Char): Boolean = ch in 'ㄱ'..'ㆎ'

    private val REACTION_CHARS = setOf('ㅋ', 'ㅎ', 'ㅠ', 'ㅜ', 'ㅡ')
}
