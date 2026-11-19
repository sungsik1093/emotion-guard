package com.emotionguard.core.datastore

import com.emotionguard.core.model.ChatRoom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RoomKeyTest {
    @Test
    fun `같은 방은 같은 키, 다른 앱이나 다른 이름은 다른 키`() {
        val a = RelationshipRepository.keyOf(ChatRoom("com.kakao.talk", "김민수"))
        assertEquals(a, RelationshipRepository.keyOf(ChatRoom("com.kakao.talk", " 김민수 ")))
        assertNotEquals(a, RelationshipRepository.keyOf(ChatRoom("org.telegram.messenger", "김민수")))
        assertNotEquals(a, RelationshipRepository.keyOf(ChatRoom("com.kakao.talk", "김민지")))
    }

    @Test
    fun `키에는 이름이 드러나지 않는다`() {
        val k = RelationshipRepository.keyOf(ChatRoom("com.kakao.talk", "김민수"))
        assertEquals(32, k.length)
        assertFalse(k.contains("김민수"))
    }
}
