package tilo.compose.core.util

import platform.Foundation.NSLock

private class IosConcurrentCache<K : Any, V : Any> : ConcurrentCache<K, V> {
    private val lock = NSLock()
    private val values = mutableMapOf<K, V>()

    override fun getOrPut(
        key: K,
        create: () -> V,
    ): V {
        lock.lock()
        return try {
            values.getOrPut(key, create)
        } finally {
            lock.unlock()
        }
    }
}

internal actual fun <K : Any, V : Any> concurrentCache(): ConcurrentCache<K, V> = IosConcurrentCache()
