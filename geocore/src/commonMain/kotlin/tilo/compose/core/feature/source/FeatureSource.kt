package tilo.compose.core.feature.source

import tilo.compose.core.feature.Feature
import tilo.compose.core.map.MapState

/**
 * Source of vector features for a `VectorLayer`.
 */
interface FeatureSource {
    /**
     * Whether results are determined by viewport coverage and may be reused while a smaller
     * viewport remains inside an earlier, buffered query. Custom camera-dependent sources keep
     * the safe default and are queried for every camera state.
     */
    val supportsBufferedQueries: Boolean
        get() = false

    /**
     * Monotonic or content-derived version used by renderers to invalidate layer caches.
     *
     * Custom mutable sources should update this value whenever returned features can change.
     */
    val version: Long
        get() = 0L

    /**
     * Return features that are relevant for the provided [map] state.
     */
    fun getFeatures(map: MapState): List<Feature>
}
