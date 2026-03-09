package io.github.alpatrykos.lrucache

import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class LruCacheTest {
    @Test
    fun `constructor rejects zero capacity`() {
        val exception = kotlin.test.assertFailsWith<IllegalArgumentException> {
            LruCache<String, Int>(0)
        }

        assertEquals("capacity must be greater than 0", exception.message)
    }

    @Test
    fun `constructor rejects negative capacity`() {
        val exception = kotlin.test.assertFailsWith<IllegalArgumentException> {
            LruCache<String, Int>(-1)
        }

        assertEquals("capacity must be greater than 0", exception.message)
    }

    @Test
    fun `empty cache reports no entries`() {
        val cache = LruCache<String, Int>(capacity = 2)

        assertEquals(0, cache.size)
        assertNull(cache.get("missing"))
        assertFalse(cache.containsKey("missing"))
        assertNull(cache.remove("missing"))
    }

    @Test
    fun `put get remove clear and contains key behave as expected`() {
        val cache = LruCache<String, Int>(capacity = 2)

        assertNull(cache.put("a", 1))
        assertEquals(1, cache.size)
        assertTrue(cache.containsKey("a"))
        assertEquals(1, cache.get("a"))

        assertEquals(1, cache.put("a", 2))
        assertEquals(1, cache.size)
        assertEquals(2, cache.get("a"))

        assertEquals(2, cache.remove("a"))
        assertEquals(0, cache.size)
        assertFalse(cache.containsKey("a"))

        cache.put("b", 3)
        cache.put("c", 4)
        cache.clear()

        assertEquals(0, cache.size)
        assertFalse(cache.containsKey("b"))
        assertFalse(cache.containsKey("c"))
    }

    @Test
    fun `inserting past capacity evicts least recently used entry`() {
        val cache = LruCache<String, Int>(capacity = 2)

        cache.put("a", 1)
        cache.put("b", 2)
        cache.put("c", 3)

        assertEquals(2, cache.size)
        assertFalse(cache.containsKey("a"))
        assertTrue(cache.containsKey("b"))
        assertTrue(cache.containsKey("c"))
    }

    @Test
    fun `get refreshes recency before eviction`() {
        val cache = LruCache<String, Int>(capacity = 2)

        cache.put("a", 1)
        cache.put("b", 2)
        assertEquals(1, cache.get("a"))
        cache.put("c", 3)

        assertTrue(cache.containsKey("a"))
        assertFalse(cache.containsKey("b"))
        assertTrue(cache.containsKey("c"))
    }

    @Test
    fun `overwriting existing key refreshes recency without changing size`() {
        val cache = LruCache<String, Int>(capacity = 2)

        cache.put("a", 1)
        cache.put("b", 2)
        assertEquals(1, cache.put("a", 10))
        cache.put("c", 3)

        assertEquals(2, cache.size)
        assertEquals(10, cache.get("a"))
        assertFalse(cache.containsKey("b"))
        assertTrue(cache.containsKey("c"))
    }

    @Test
    fun `synchronized cache matches single threaded behavior`() {
        val cache = SynchronizedLruCache<String, Int>(capacity = 2)

        assertNull(cache.put("a", 1))
        assertNull(cache.put("b", 2))
        assertEquals(1, cache.get("a"))
        assertEquals(2, cache.size)

        cache.put("c", 3)

        assertTrue(cache.containsKey("a"))
        assertFalse(cache.containsKey("b"))
        assertTrue(cache.containsKey("c"))
        assertEquals(3, cache.remove("c"))
        assertEquals(1, cache.size)

        cache.clear()
        assertEquals(0, cache.size)
    }

    @Test
    fun `synchronized cache handles concurrent access without exceeding capacity`() {
        val cache = SynchronizedLruCache<Int, Int>(capacity = 16)
        val threadCount = 8
        val iterations = 1_000
        val executor = Executors.newFixedThreadPool(threadCount)
        val ready = CountDownLatch(threadCount)
        val start = CountDownLatch(1)
        val failures = ConcurrentLinkedQueue<Throwable>()

        try {
            val futures = (0 until threadCount).map { worker ->
                executor.submit(Callable {
                    ready.countDown()
                    start.await()

                    repeat(iterations) { index ->
                        val key = (worker * iterations + index) % 32

                        try {
                            when (index % 4) {
                                0 -> cache.put(key, index)
                                1 -> cache.get(key)
                                2 -> cache.containsKey(key)
                                else -> cache.remove(key)
                            }
                        } catch (throwable: Throwable) {
                            failures.add(throwable)
                        }
                    }
                })
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS), "workers did not initialize in time")
            start.countDown()

            futures.forEach { it.get(10, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }

        if (failures.isNotEmpty()) {
            fail("concurrent access produced failures: ${failures.joinToString(limit = 3)}")
        }

        assertTrue(cache.size <= cache.capacity)
    }
}
