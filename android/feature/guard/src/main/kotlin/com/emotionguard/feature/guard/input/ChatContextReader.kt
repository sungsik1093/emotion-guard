/**
 * 채팅방 화면에서 최근 대화를 읽는다 (디자인 문서 §5.3).
 *
 * 1순위: 메신저 프로필의 말풍선 뷰 ID로 찾기 → 신뢰도 높음
 * 2순위: 화면의 글자 뷰를 모두 훑고 위치로 추정 → 신뢰도 낮음
 *        말풍선이 화면 오른쪽에 붙어 있으면 "나", 왼쪽이면 "상대".
 *
 * 비밀번호 칸과 입력창 자체는 읽지 않는다.
 */
package com.emotionguard.feature.guard.input

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.emotionguard.core.model.ChatContext
import com.emotionguard.core.model.ChatLine
import com.emotionguard.core.model.Speaker

class ChatContextReader(private val maxLines: Int = MAX_LINES) {

    private data class Bubble(val top: Int, val line: ChatLine)

    private var topCutoff = 0

    fun read(
        root: AccessibilityNodeInfo?,
        profile: MessengerProfile,
        screenWidth: Int,
        currentInput: String,
        screenHeight: Int = 0,
    ): ChatContext {
        topCutoff = (screenHeight * TITLE_BAND).toInt()
        if (root == null || screenWidth <= 0) return ChatContext.EMPTY

        val byId = profile.fullBubbleIds
            .flatMap { id -> root.findAccessibilityNodeInfosByViewId(id).orEmpty() }
            .mapNotNull { toBubble(it, profile, screenWidth, strict = false) }
        if (byId.size >= 2) return finish(byId, currentInput, confidence = 0.9f)

        val guessed = mutableListOf<Bubble>()
        collectTextNodes(root, depth = 0) { node ->
            toBubble(node, profile, screenWidth, strict = true)?.let(guessed::add)
        }
        val confidence = if (guessed.size >= 3) 0.6f else 0.4f
        return finish(guessed, currentInput, confidence)
    }

    /**
     * 채팅방 제목(보통 상대 이름)을 읽는다. 화면 맨 위 띠(상단 14%)에서 처음 나오는 글자를 쓴다.
     * 못 찾으면 null → 관계는 기본값을 쓴다.
     */
    fun readRoomTitle(root: AccessibilityNodeInfo?, profile: MessengerProfile, screenHeight: Int): String? {
        if (root == null || screenHeight <= 0) return null
        val bandBottom = screenHeight * TITLE_BAND
        var title: String? = null
        collectTextNodes(root, depth = 0) { node ->
            if (title != null || node.isEditable) return@collectTextNodes
            val text = node.text?.toString()?.trim().orEmpty()
            if (text.isEmpty() || text.length > MAX_TITLE_LENGTH || profile.isNoise(text)) return@collectTextNodes
            val bounds = Rect().also(node::getBoundsInScreen)
            if (bounds.top in 1 until bandBottom.toInt()) title = text
        }
        return title
    }

    private fun finish(bubbles: List<Bubble>, currentInput: String, confidence: Float): ChatContext {
        val input = currentInput.trim()
        val lines = bubbles
            .sortedBy { it.top }
            .map { it.line }
            .filterNot { it.text == input }
            .fold(mutableListOf<ChatLine>()) { acc, line ->
                if (acc.lastOrNull() != line) acc.add(line) // 같은 노드를 두 번 읽은 경우 제거
                acc
            }
            .takeLast(maxLines)
        return if (lines.isEmpty()) ChatContext.EMPTY else ChatContext(lines, confidence)
    }

    private fun toBubble(
        node: AccessibilityNodeInfo,
        profile: MessengerProfile,
        screenWidth: Int,
        strict: Boolean,
    ): Bubble? {
        if (node.isEditable || node.isPassword) return null
        val text = node.text?.toString()?.trim().orEmpty()
        if (text.length > MAX_LINE_LENGTH || profile.isNoise(text)) return null

        val bounds = Rect().also(node::getBoundsInScreen)
        if (bounds.width() <= 0) return null
        if (bounds.top < topCutoff) return null // 화면 맨 위(방 제목·툴바)는 대화가 아니다
        val speaker = speakerOf(bounds.left, bounds.right, screenWidth, strict) ?: return null
        return Bubble(bounds.top, ChatLine(speaker, text))
    }

    private fun collectTextNodes(node: AccessibilityNodeInfo, depth: Int, visit: (AccessibilityNodeInfo) -> Unit) {
        if (depth > MAX_DEPTH) return
        if (node.childCount == 0 && !node.text.isNullOrBlank()) visit(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectTextNodes(child, depth + 1, visit)
        }
    }

    companion object {
        /**
         * 말풍선 위치로 나/상대를 정한다. 오른쪽에 붙어 있으면 나, 왼쪽에 붙어 있으면 상대.
         * 긴 말풍선은 양쪽 끝에 다 닿을 수 있어서, 그때는 여백이 더 좁은 쪽에 붙은 것으로 본다.
         * [strict]이면 가운데 정렬처럼 애매한 것(날짜 구분선 등)은 버린다.
         */
        fun speakerOf(left: Int, right: Int, screenWidth: Int, strict: Boolean): Speaker? {
            val rightAligned = right >= screenWidth * 0.85f
            val leftAligned = left <= screenWidth * 0.2f
            return when {
                rightAligned && !leftAligned -> Speaker.ME
                leftAligned && !rightAligned -> Speaker.OTHER
                rightAligned && leftAligned -> if (screenWidth - right < left) Speaker.ME else Speaker.OTHER
                !strict -> if ((left + right) / 2 > screenWidth / 2) Speaker.ME else Speaker.OTHER
                else -> null
            }
        }

        const val MAX_LINES = 8
        private const val TITLE_BAND = 0.14f
        private const val MAX_TITLE_LENGTH = 40
        const val MAX_LINE_LENGTH = 300
        private const val MAX_DEPTH = 30
    }
}
