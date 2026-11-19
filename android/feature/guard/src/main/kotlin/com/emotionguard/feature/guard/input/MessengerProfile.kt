/**
 * 메신저별 화면 구조 정보 (디자인 문서 §5.3).
 *
 * 뷰 ID가 맞으면 말풍선·입력창·전송 버튼을 정확히 찾고, 틀리거나 비어 있으면
 * 화면 위치로 추정하는 대비책으로 넘어간다. 그래서 ID가 틀려도 앱이 멈추지는 않는다.
 *
 * ⚠️ 아래 뷰 ID는 메신저 업데이트로 바뀔 수 있다. 실기기에서 설정 → 진단 화면을 열고
 *    해당 메신저에 입력해 보면 실제 입력창 ID가 표시되니, 그 값으로 확인·수정한다.
 */
package com.emotionguard.feature.guard.input

data class MessengerProfile(
    val packageName: String,
    val inputFieldIds: List<String> = emptyList(),
    val sendButtonIds: List<String> = emptyList(),
    val bubbleTextIds: List<String> = emptyList(),
    val sendButtonLabels: List<String> = DEFAULT_SEND_LABELS,
    val noisePatterns: List<Regex> = DEFAULT_NOISE,
) {
    private fun full(id: String) = if (':' in id) id else "$packageName:id/$id"

    val fullInputIds: List<String> get() = inputFieldIds.map(::full)
    val fullSendIds: List<String> get() = sendButtonIds.map(::full)
    val fullBubbleIds: List<String> get() = bubbleTextIds.map(::full)

    fun isNoise(text: String): Boolean = text.isBlank() || noisePatterns.any { it.matches(text.trim()) }

    companion object {
        val DEFAULT_SEND_LABELS = listOf("전송", "보내기", "send", "보냄")

        /** 시간, 날짜 구분선, 안 읽음 숫자, 시스템 안내 */
        val DEFAULT_NOISE = listOf(
            Regex("""(오전|오후)\s?\d{1,2}:\d{2}"""),
            Regex("""\d{1,2}:\d{2}(\s?(AM|PM|am|pm))?"""),
            Regex("""\d{4}년\s?\d{1,2}월\s?\d{1,2}일.*"""),
            Regex("""\d{1,2}월\s?\d{1,2}일.*"""),
            Regex("""(어제|오늘|Yesterday|Today)"""),
            Regex("""\d{1,3}"""),
            Regex("""(읽음|안 읽음|전송됨|전송 중|Read|Sent|Delivered)"""),
            Regex(""".*님이 (들어왔습니다|나갔습니다|초대했습니다).*"""),
            Regex(""".*님과 .*대화 중.*"""),
        )
    }
}

object MessengerProfiles {
    private val profiles = listOf(
        MessengerProfile(
            packageName = "com.kakao.talk",
            inputFieldIds = listOf("message_edit_text"),
            sendButtonIds = listOf("send"),
            bubbleTextIds = listOf("message"),
        ),
        MessengerProfile(
            packageName = "com.google.android.apps.messaging",
            inputFieldIds = listOf("compose_message_text"),
            sendButtonIds = listOf("send_message_button_icon", "send_message_button_container"),
            bubbleTextIds = listOf("message_text"),
        ),
        MessengerProfile(packageName = "com.samsung.android.messaging"),
        MessengerProfile(packageName = "com.instagram.android"),
        MessengerProfile(packageName = "org.telegram.messenger"),
    )

    fun of(packageName: String?): MessengerProfile? = profiles.firstOrNull { it.packageName == packageName }
}
