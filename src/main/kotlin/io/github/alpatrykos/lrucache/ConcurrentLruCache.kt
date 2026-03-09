package io.github.alpatrykos.lrucache

import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.withLock
import kotlin.concurrent.write

/**
 * A sharded concurrent LRU cache.
 *
 * Entries are distributed across shards by key hash. Each shard maintains its own LRU order and
 * evicts locally, which improves concurrency by reducing lock contention. Because eviction is
 * shard-local, recency is approximate across the cache as a whole rather than exact globally.
 */
public class ConcurrentLruCache<K, V>(
    public val capacity: Int,
    public val shardCount: Int = defaultShardCount(capacity),
) {
    private val lifecycleLock = ReentrantReadWriteLock()
    private val totalSize = AtomicInteger(0)
    private val shards: Array<Shard<K, V>>

    init {
        require(capacity > 0) { "capacity must be greater than 0" }
        require(shardCount > 0) { "shardCount must be greater than 0" }
        require(shardCount <= capacity) { "shardCount must be less than or equal to capacity" }

        val baseShardCapacity = capacity / shardCount
        val shardCapacityRemainder = capacity % shardCount
        shards = Array(shardCount) { index ->
            val shardCapacity = baseShardCapacity + if (index < shardCapacityRemainder) 1 else 0
            Shard(shardCapacity)
        }
    }

    /**
     * The number of entries currently stored in the cache.
     */
    public val size: Int
        get() = totalSize.get()

    /**
     * Returns the cached value for [key] and marks it as recently used within its shard.
     */
    public fun get(key: K): V? = lifecycleLock.read {
        val shard = shardFor(key)
        shard.lock.withLock {
            shard.entries[key]
        }
    }

    /**
     * Stores [value] for [key].
     *
     * Updating an existing key refreshes its recency and returns the previous value.
     */
    public fun put(key: K, value: V): V? = lifecycleLock.read {
        val shard = shardFor(key)
        shard.lock.withLock {
            if (shard.entries.containsKey(key)) {
                val previous = shard.entries.put(key, value)
                return previous
            }

            shard.entries[key] = value

            if (shard.entries.size > shard.capacity) {
                evictLeastRecentlyUsed(shard)
            } else {
                totalSize.incrementAndGet()
            }

            null
        }
    }

    /**
     * Removes the value stored for [key], if present.
     */
    public fun remove(key: K): V? = lifecycleLock.read {
        val shard = shardFor(key)
        shard.lock.withLock {
            if (!shard.entries.containsKey(key)) {
                return null
            }

            val removed = shard.entries.remove(key)
            totalSize.decrementAndGet()
            removed
        }
    }

    /**
     * Returns `true` when [key] exists in the cache.
     */
    public fun containsKey(key: K): Boolean = lifecycleLock.read {
        val shard = shardFor(key)
        shard.lock.withLock {
            shard.entries.containsKey(key)
        }
    }

    /**
     * Removes all cached entries.
     */
    public fun clear() {
        lifecycleLock.write {
            shards.forEach { shard -> shard.entries.clear() }
            totalSize.set(0)
        }
    }

    private fun shardFor(key: K): Shard<K, V> = shards[shardIndex(key)]

    private fun shardIndex(key: K): Int {
        val hash = key?.hashCode() ?: 0
        val spread = hash xor (hash ushr 16)
        return (spread and Int.MAX_VALUE) % shardCount
    }

    private fun evictLeastRecentlyUsed(shard: Shard<K, V>) {
        val iterator = shard.entries.entries.iterator()
        if (iterator.hasNext()) {
            iterator.next()
            iterator.remove()
        }
    }

    private class Shard<K, V>(val capacity: Int) {
        val lock: ReentrantLock = ReentrantLock()
        val entries: LinkedHashMap<K, V> = LinkedHashMap(capacity, 0.75f, true)
    }

    private companion object {
        fun defaultShardCount(capacity: Int): Int {
            return Runtime.getRuntime().availableProcessors().coerceIn(1, capacity.coerceAtLeast(1))
        }
    }
}
