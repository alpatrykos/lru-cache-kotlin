package io.github.alpatrykos.lrucache

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * A thread-safe wrapper around [LruCache].
 *
 * Each individual call is synchronized. Sequences of multiple calls are not atomic.
 */
public class SynchronizedLruCache<K, V>(capacity: Int) {
    private val delegate = LruCache<K, V>(capacity)
    private val lock = ReentrantLock()

    /**
     * The maximum number of entries the cache can hold.
     */
    public val capacity: Int = delegate.capacity

    /**
     * The number of entries currently stored in the cache.
     */
    public val size: Int
        get() = lock.withLock { delegate.size }

    /**
     * Returns the cached value for [key] and marks it as recently used when present.
     */
    public fun get(key: K): V? = lock.withLock { delegate.get(key) }

    /**
     * Stores [value] for [key].
     *
     * Updating an existing key refreshes its recency and returns the previous value.
     */
    public fun put(key: K, value: V): V? = lock.withLock { delegate.put(key, value) }

    /**
     * Removes the value stored for [key], if present.
     */
    public fun remove(key: K): V? = lock.withLock { delegate.remove(key) }

    /**
     * Returns `true` when [key] exists in the cache.
     */
    public fun containsKey(key: K): Boolean = lock.withLock { delegate.containsKey(key) }

    /**
     * Removes all cached entries.
     */
    public fun clear() {
        lock.withLock { delegate.clear() }
    }
}
