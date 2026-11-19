/**
 * 감지 계층 인터페이스 (디자인 문서 §5.3).
 *
 * "사용자가 입력 중인 글을 어디서 얻는지"를 이 인터페이스 뒤로 숨긴다.
 * - 지금: AccessibilityInputSource (접근성 서비스)
 * - 출시 대안: ImeInputSource (커스텀 키보드) — Play 심사에서 접근성 사용이 반려되면 이걸로 바꾼다.
 *   나머지 코드(분석, 캐릭터, 기록)는 그대로 쓸 수 있다.
 */
package com.emotionguard.feature.guard.input

import com.emotionguard.core.model.ChatContext
import com.emotionguard.core.model.ChatRoom
import com.emotionguard.core.model.SendConfidence
import kotlinx.coroutines.flow.Flow

sealed interface InputEvent {
    /** 지원 메신저의 입력창에 포커스가 감 */
    data class Focused(val packageName: String) : InputEvent

    /** 입력창 글이 바뀜 (빈 문자열이면 비워진 것) */
    data class TextChanged(val packageName: String, val text: String) : InputEvent

    /** 메시지를 보낸 것으로 판단됨 */
    data class Sent(val packageName: String, val text: String, val confidence: SendConfidence) : InputEvent

    /** 메신저를 벗어남 */
    data object Blurred : InputEvent
}

interface InputSource {
    val events: Flow<InputEvent>

    /** 지금 화면의 대화 맥락. 맥락을 읽을 수 없는 출처(IME)는 ChatContext.EMPTY를 돌려준다. */
    fun readContext(): ChatContext

    /** 입력창 글을 [newText]로 바꾼다. 성공하면 true. */
    fun replaceText(newText: String): Boolean

    /** 지금 채팅방. 알 수 없으면(IME 등) null */
    fun currentRoom(): ChatRoom?
}
