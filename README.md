# lru-cache-kotlin

Minimal Kotlin/JVM LRU cache library.

## Usage

```kotlin
import io.github.alpatrykos.lrucache.ConcurrentLruCache
import io.github.alpatrykos.lrucache.LruCache
import io.github.alpatrykos.lrucache.SynchronizedLruCache

val cache = LruCache<String, Int>(capacity = 2)
cache.put("a", 1)
cache.put("b", 2)
cache.get("a")
cache.put("c", 3) // evicts "b"

val threadSafeCache = SynchronizedLruCache<String, Int>(capacity = 2)
threadSafeCache.put("x", 10)

val concurrentCache = ConcurrentLruCache<String, Int>(capacity = 64, shardCount = 8)
concurrentCache.put("x", 10)
concurrentCache.get("x")
```

`SynchronizedLruCache` makes each individual call thread-safe. Multi-step sequences across
multiple calls are not atomic, while preserving exact global LRU ordering.

`ConcurrentLruCache` shards entries by key hash and maintains LRU ordering per shard. That
reduces lock contention under multi-threaded access, but eviction is approximate globally
rather than exact across the whole cache.

## Scalability follow-ups

If higher throughput matters more than the current simple design, the next steps would be:

- `ConcurrentHashMap` for the primary key/value store, with recency tracking separated from the
  value table so shard locks do not guard every access path.
- Lock-free read buffers to record cache hits and replay recency updates in batches instead of
  mutating LRU state on every `get`.
- Background eviction so capacity enforcement can run asynchronously and reduce latency spikes on
  hot write paths.
- TinyLFU admission to avoid admitting one-off entries that would evict more valuable hot keys,
  which usually improves hit rate under skewed traffic.
