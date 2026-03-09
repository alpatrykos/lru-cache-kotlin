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
