package tilo.compose.core.util

/** Small platform-backed cache for immutable runtime resolutions. */
internal interface ConcurrentCache<K : Any, V : Any> {
    fun getOrPut(
        key: K,
        create: () -> V,
    ): V
}

internal expect fun <K : Any, V : Any> concurrentCache(): ConcurrentCache<K, V>
