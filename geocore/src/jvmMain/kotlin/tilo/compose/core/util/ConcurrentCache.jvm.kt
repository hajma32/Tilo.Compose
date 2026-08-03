package tilo.compose.core.util

import java.util.concurrent.ConcurrentHashMap

private class JvmConcurrentCache<K : Any, V : Any> : ConcurrentCache<K, V> {
    private val values = ConcurrentHashMap<K, V>()

    override fun getOrPut(
        key: K,
        create: () -> V,
    ): V = values.computeIfAbsent(key) { create() }
}

internal actual fun <K : Any, V : Any> concurrentCache(): ConcurrentCache<K, V> = JvmConcurrentCache()
