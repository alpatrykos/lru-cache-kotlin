package io.github.alpatrykos.lrucache

/**
 * A fixed-capacity least recently used cache.
 *
 * This implementation is not thread-safe.
 */
public class LruCache<K, V>(public val capacity: Int) {
    private val entries: LinkedHashMap<K, V>

    init {
        require(capacity > 0) { "capacity must be greater than 0" }

        entries = object : LinkedHashMap<K, V>(capacity, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
                return size > this@LruCache.capacity
            }
        }
    }

    /**
     * The number of entries currently stored in the cache.
     */
    public val size: Int
        get() = entries.size

    /**
     * Returns the cached value for [key] and marks it as recently used when present.
     */
    public fun get(key: K): V? = entries[key]

    /**
     * Stores [value] for [key].
     *
     * Updating an existing key refreshes its recency and returns the previous value.
     */
    public fun put(key: K, value: V): V? = entries.put(key, value)

    /**
     * Removes the value stored for [key], if present.
     */
    public fun remove(key: K): V? = entries.remove(key)

    /**
     * Returns `true` when [key] exists in the cache.
     */
    public fun containsKey(key: K): Boolean = entries.containsKey(key)

    /**
     * Removes all cached entries.
     */
    public fun clear() {
        entries.clear()
    }
}
