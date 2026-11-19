/**
 * 채팅방 화면에서 읽은 대화 맥락과 전송 판단 결과.
 */
package com.emotionguard.core.model

enum class Speaker { ME, OTHER }

data class ChatLine(val speaker: Speaker, val text: String)

/**
 * @param confidence 맥락을 얼마나 믿을 수 있는지 (0.0~1.0).
 *   뷰 ID로 읽었으면 높고, 화면 위치로 추정했으면 낮다. 0.5 미만이면 분석에 쓰지 않는다.
 */
data class ChatContext(val lines: List<ChatLine>, val confidence: Float) {
    val usable: Boolean get() = lines.isNotEmpty() && confidence >= MIN_CONFIDENCE

    companion object {
        const val MIN_CONFIDENCE = 0.5f
        val EMPTY = ChatContext(emptyList(), 0f)
    }
}

/**
 * 대화 상대와의 관계 (서버 relationship 코드와 이름이 같다).
 * 관계에 따라 같은 말도 다르게 판단한다. 예: 직장이면 반말·비꼼을 더 엄격하게.
 */
enum class Relationship(val label: String) {
    UNKNOWN("기본"),
    FRIEND("친구"),
    WORK("직장"),
    PARTNER("연인"),
    FAMILY("가족");

    companion object {
        fun fromName(name: String?): Relationship = entries.firstOrNull { it.name == name } ?: UNKNOWN
    }
}

/**
 * 지금 보고 있는 채팅방. [title]은 화면 상단의 방 제목(상대 이름)이다.
 * 제목은 화면 표시용으로만 잠깐 쓰고, 저장할 때는 해시([key])만 쓴다.
 */
data class ChatRoom(val packageName: String, val title: String)

/** 메시지를 실제로 보냈다고 얼마나 확신하는지 */
enum class SendConfidence {
    /** 전송 버튼 클릭 또는 새 내 말풍선 확인 */
    CONFIRMED,

    /** 입력창이 한 번에 비워진 것만 확인 (전체 삭제일 수도 있음) */
    LIKELY,
}

/** 감정 가드가 지원하는 메신저 */
data class Messenger(val packageName: String, val displayName: String)

object SupportedMessengers {
    val all: List<Messenger> = listOf(
        Messenger("com.kakao.talk", "카카오톡"),
        Messenger("com.google.android.apps.messaging", "Google 메시지"),
        Messenger("com.samsung.android.messaging", "삼성 메시지"),
        Messenger("com.instagram.android", "인스타그램"),
        Messenger("org.telegram.messenger", "텔레그램"),
    )

    fun find(packageName: String?): Messenger? = all.firstOrNull { it.packageName == packageName }
}
