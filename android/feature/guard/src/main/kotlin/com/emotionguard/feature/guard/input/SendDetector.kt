/**
 * "메시지를 보냈는지" 판단 (디자인 문서 §5.4). 안드로이드에 의존하지 않는 순수 로직.
 *
 * 세 가지 신호를 [windowMillis] 안에서 모은다.
 *   A. 전송 버튼 클릭
 *   B. 입력창이 2글자 이상에서 한 번에 비워짐
 *   C. 화면에 같은 내용의 내 말풍선이 새로 나타남
 *
 *   A 또는 C가 있으면 CONFIRMED, B만 있으면 LIKELY.
 *
 * 사용법: 신호가 올 때마다 on*()를 부르고, 창이 끝날 즈음 poll(now)로 결과를 받는다.
 */
package com.emotionguard.feature.guard.input

import com.emotionguard.core.model.SendConfidence

class SendDetector(private val windowMillis: Long = 1_500) {

    data class Decision(val text: String, val confidence: SendConfidence)

    private data class Pending(
        val text: String,
        val startedAt: Long,
        var clicked: Boolean = false,
        var cleared: Boolean = false,
        var bubbleSeen: Boolean = false,
    )

    private var lastText: String = ""
    private var pending: Pending? = null

    val hasPending: Boolean get() = pending != null
    val pendingText: String? get() = pending?.text

    /** 입력창 글이 바뀔 때 */
    fun onText(text: String, now: Long) {
        val previous = lastText
        lastText = text
        if (text.isEmpty() && previous.trim().length >= MIN_SENT_LENGTH) {
            begin(previous, now).cleared = true
        } else if (text.isNotEmpty() && pending?.cleared == true && !pending!!.clicked) {
            // 비운 뒤 바로 다시 쓰기 시작했고 전송 버튼도 안 눌렸다 → 지운 것으로 본다
            pending = null
        }
    }

    /** 전송 버튼이 눌렸을 때 (글이 비워지기 전에 온다) */
    fun onSendClick(now: Long) {
        val text = lastText.trim()
        if (text.isEmpty()) return
        begin(text, now).clicked = true
    }

    /** 화면의 내 말풍선 목록을 받았을 때 */
    fun onMyBubbles(texts: List<String>, now: Long) {
        val p = pending ?: return
        if (now - p.startedAt > windowMillis) return
        if (texts.any { it.trim() == p.text.trim() }) p.bubbleSeen = true
    }

    /** 창이 끝났으면 판단 결과를 돌려주고 초기화한다. */
    fun poll(now: Long): Decision? {
        val p = pending ?: return null
        if (now - p.startedAt < windowMillis) return null
        pending = null
        val textGone = p.cleared || lastText.isEmpty()
        return when {
            p.bubbleSeen -> Decision(p.text, SendConfidence.CONFIRMED)
            p.clicked && textGone -> Decision(p.text, SendConfidence.CONFIRMED)
            p.cleared -> Decision(p.text, SendConfidence.LIKELY)
            else -> null // 클릭만 있고 글이 그대로 → 전송 실패 등
        }
    }

    private fun begin(text: String, now: Long): Pending {
        val current = pending
        if (current != null && current.text.trim() == text.trim() && now - current.startedAt <= windowMillis) {
            return current
        }
        return Pending(text.trim(), now).also { pending = it }
    }

    companion object {
        const val MIN_SENT_LENGTH = 2
    }
}
