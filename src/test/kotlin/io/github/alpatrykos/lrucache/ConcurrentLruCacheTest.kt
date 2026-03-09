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

class ConcurrentLruCacheTest {
    @Test
    fun `constructor rejects zero capacity`() {
        val exception = kotlin.test.assertFailsWith<IllegalArgumentException> {
            ConcurrentLruCache<String, Int>(0)
        }

        assertEquals("capacity must be greater than 0", exception.message)
    }

    @Test
    fun `constructor rejects invalid shard count`() {
        val zeroShardException = kotlin.test.assertFailsWith<IllegalArgumentException> {
            ConcurrentLruCache<String, Int>(capacity = 2, shardCount = 0)
        }
        val oversizedShardException = kotlin.test.assertFailsWith<IllegalArgumentException> {
            ConcurrentLruCache<String, Int>(capacity = 2, shardCount = 3)
        }

        assertEquals("shardCount must be greater than 0", zeroShardException.message)
        assertEquals("shardCount must be less than or equal to capacity", oversizedShardException.message)
    }

    @Test
    fun `single shard concurrent cache preserves exact lru order`() {
        val cache = ConcurrentLruCache<String, Int>(capacity = 2, shardCount = 1)

        cache.put("a", 1)
        cache.put("b", 2)
        assertEquals(1, cache.get("a"))
        cache.put("c", 3)

        assertTrue(cache.containsKey("a"))
        assertFalse(cache.containsKey("b"))
        assertTrue(cache.containsKey("c"))
    }

    @Test
    fun `concurrent cache evicts within a shard rather than globally`() {
        val cache = ConcurrentLruCache<TestKey, Int>(capacity = 4, shardCount = 2)
        val a = TestKey("a", forcedHash = 0)
        val b = TestKey("b", forcedHash = 0)
        val c = TestKey("c", forcedHash = 1)
        val d = TestKey("d", forcedHash = 1)
        val e = TestKey("e", forcedHash = 0)

        cache.put(a, 1)
        cache.put(c, 3)
        cache.put(d, 4)
        cache.put(b, 2)
        assertEquals(1, cache.get(a))
        assertEquals(3, cache.get(c))
        cache.put(e, 5)

        assertTrue(cache.containsKey(a))
        assertFalse(cache.containsKey(b))
        assertTrue(cache.containsKey(c))
        assertTrue(cache.containsKey(d))
        assertTrue(cache.containsKey(e))
    }

    @Test
    fun `concurrent cache updates existing keys without growing`() {
        val cache = ConcurrentLruCache<String, Int>(capacity = 2, shardCount = 1)

        assertNull(cache.put("a", 1))
        assertNull(cache.put("b", 2))
        assertEquals(1, cache.put("a", 10))
        cache.put("c", 3)

        assertEquals(2, cache.size)
        assertEquals(10, cache.get("a"))
        assertFalse(cache.containsKey("b"))
        assertTrue(cache.containsKey("c"))
    }

    @Test
    fun `concurrent cache handles nullable values without corrupting size`() {
        val cache = ConcurrentLruCache<String, Int?>(capacity = 2, shardCount = 1)

        assertNull(cache.put("a", null))
        assertEquals(1, cache.size)
        assertNull(cache.put("a", 1))
        assertEquals(1, cache.size)
        assertEquals(1, cache.remove("a"))
        assertEquals(0, cache.size)
    }

    @Test
    fun `concurrent cache supports mixed concurrent access without exceeding capacity`() {
        val cache = ConcurrentLruCache<Int, Int>(capacity = 32, shardCount = 8)
        val threadCount = 12
        val iterations = 2_000
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
                        val key = (worker * iterations + index) % 64

                        try {
                            when (index % 5) {
                                0 -> cache.put(key, index)
                                1 -> cache.get(key)
                                2 -> cache.containsKey(key)
                                3 -> cache.remove(key)
                                else -> if (index % 17 == 0) cache.clear() else cache.get(key)
                            }
                        } catch (throwable: Throwable) {
                            failures.add(throwable)
                        }
                    }
                })
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS), "workers did not initialize in time")
            start.countDown()

            futures.forEach { it.get(15, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }

        if (failures.isNotEmpty()) {
            fail("concurrent access produced failures: ${failures.joinToString(limit = 3)}")
        }

        assertTrue(cache.size <= cache.capacity)
    }

    private data class TestKey(
        val value: String,
        val forcedHash: Int,
    ) {
        override fun hashCode(): Int = forcedHash
    }
}
