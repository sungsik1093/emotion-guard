package com.emotionguard.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelTest {

    @Test
    fun `점수 구간 경계값`() {
        assertEquals(Level.SAFE, Level.of(0))
        assertEquals(Level.SAFE, Level.of(30))
        assertEquals(Level.CAUTION, Level.of(31))
        assertEquals(Level.CAUTION, Level.of(60))
        assertEquals(Level.DANGER, Level.of(61))
        assertEquals(Level.DANGER, Level.of(100))
    }

    @Test
    fun `모르는 카테고리 코드는 NONE`() {
        assertEquals(Category.SARCASM, Category.fromCode("SARCASM"))
        assertEquals(Category.NONE, Category.fromCode("SOMETHING_NEW"))
        assertEquals(Category.NONE, Category.fromCode(null))
    }

    @Test
    fun `기본 모드는 항상 기본 캐릭터`() {
        val skin = CharacterSkin(mode = SkinMode.DEFAULT, singleImage = "a.webp")
        assertNull(skin.imageFor(MoodState.SAFE))
    }

    @Test
    fun `사진 한 장 모드는 모든 상태에 같은 사진`() {
        val skin = CharacterSkin(mode = SkinMode.SINGLE_IMAGE, singleImage = "a.webp", stateImages = mapOf(MoodState.DANGER to "d.webp"))
        MoodState.entries.forEach { assertEquals("a.webp", skin.imageFor(it)) }
    }

    @Test
    fun `상태별 모드는 상태 사진 - 단일 사진 - 기본 캐릭터 순으로 대체`() {
        val withSingle = CharacterSkin(SkinMode.PER_STATE, "a.webp", mapOf(MoodState.DANGER to "d.webp"))
        assertEquals("d.webp", withSingle.imageFor(MoodState.DANGER))
        assertEquals("a.webp", withSingle.imageFor(MoodState.SAFE))

        val withoutSingle = withSingle.copy(singleImage = null)
        assertNull(withoutSingle.imageFor(MoodState.SAFE))
        assertEquals(setOf("d.webp"), withoutSingle.referencedFiles())
    }

    @Test
    fun `맥락 신뢰도가 낮으면 쓰지 않는다`() {
        val lines = listOf(ChatLine(Speaker.OTHER, "안녕"))
        assertEquals(false, ChatContext(lines, 0.4f).usable)
        assertEquals(true, ChatContext(lines, 0.5f).usable)
        assertEquals(false, ChatContext(emptyList(), 1f).usable)
    }
}
