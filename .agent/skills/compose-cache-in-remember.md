# SKILL: Mutable caches in Compose belong in `remember`

Never create a mutable cache as a `private val` on an `object` or at top-level — they are shared across all instances and have no synchronization.

```kotlin
// ❌ WRONG — global singleton cache
object Foo {
    private val cache = mutableMapOf<String, Bar>()
}

// ✅ CORRECT — cache passed from remember{} in the composable
val cache = remember { mutableMapOf<String, Bar>() }
Foo.build(map, features, cache)
```

