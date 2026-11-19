/**
 * 떠 있는 캐릭터의 상태와 꾸미기(스킨) 설정.
 */
package com.emotionguard.core.model

import kotlinx.serialization.Serializable

/** 캐릭터 표정 상태. 디자인 문서 §4.4 */
@Serializable
enum class MoodState(val label: String) {
    IDLE("대기"),
    THINKING("생각 중"),
    SAFE("안전"),
    CAUTION("주의"),
    DANGER("위험"),
    PRAISE("칭찬"),
    OFFLINE("오프라인");

    companion object {
        fun of(level: Level): MoodState = when (level) {
            Level.SAFE -> SAFE
            Level.CAUTION -> CAUTION
            Level.DANGER -> DANGER
        }
    }
}

@Serializable
enum class SkinMode {
    /** 앱에 내장된 기본 캐릭터 */
    DEFAULT,

    /** 사진 한 장을 모든 상태에 사용 */
    SINGLE_IMAGE,

    /** 상태마다 다른 사진 (없는 상태는 단일 사진 → 기본 캐릭터 순으로 대체) */
    PER_STATE,
}

/**
 * 캐릭터 꾸미기 설정. 이미지는 앱 내부 저장소(filesDir/skins)의 파일 이름만 저장한다.
 */
@Serializable
data class CharacterSkin(
    val mode: SkinMode = SkinMode.DEFAULT,
    val singleImage: String? = null,
    val stateImages: Map<MoodState, String> = emptyMap(),
    val showColorRing: Boolean = true,
    val animate: Boolean = true,
) {
    /**
     * 상태 [state]를 그릴 때 쓸 이미지 파일 이름. null이면 기본 캐릭터를 그린다.
     * 우선순위: 상태별 사진 → 단일 사진 → 기본 캐릭터 (디자인 문서 §6.3)
     */
    fun imageFor(state: MoodState): String? = when (mode) {
        SkinMode.DEFAULT -> null
        SkinMode.SINGLE_IMAGE -> singleImage
        SkinMode.PER_STATE -> stateImages[state] ?: singleImage
    }

    /** 더 이상 쓰지 않게 된 이미지 파일을 정리할 때 쓴다. */
    fun referencedFiles(): Set<String> = buildSet {
        singleImage?.let(::add)
        addAll(stateImages.values)
    }
}
