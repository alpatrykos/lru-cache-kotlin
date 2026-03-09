# lru-cache-kotlin

Minimal Kotlin/JVM LRU cache library.

## Usage

```kotlin
import io.github.alpatrykos.lrucache.LruCache
import io.github.alpatrykos.lrucache.SynchronizedLruCache

val cache = LruCache<String, Int>(capacity = 2)
cache.put("a", 1)
cache.put("b", 2)
cache.get("a")
cache.put("c", 3) // evicts "b"

val threadSafeCache = SynchronizedLruCache<String, Int>(capacity = 2)
threadSafeCache.put("x", 10)
```

`SynchronizedLruCache` makes each individual call thread-safe. Multi-step sequences across
multiple calls are not atomic.
