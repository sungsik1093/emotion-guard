package com.emotionguard.core.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LruCacheTest {
    @Test
    fun `가장 오래 안 쓴 항목부터 버린다`() {
        val cache = LruCache<String, Int>(2)
        cache["a"] = 1
        cache["b"] = 2
        cache["a"] // a를 최근에 씀
        cache["c"] = 3
        assertNull(cache["b"])
        assertEquals(1, cache["a"])
        assertEquals(3, cache["c"])
        assertEquals(2, cache.size())
    }
}
