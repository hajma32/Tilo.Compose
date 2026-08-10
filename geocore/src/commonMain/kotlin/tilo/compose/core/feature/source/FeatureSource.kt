package tilo.compose.core.feature.source

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
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
     * Signals that this source's returned features changed while its identity stayed the same.
     *
     * Mutable sources should increment [version] before emitting. Renderers collect this flow only
     * while a layer backed by this source is active. Static sources can keep the empty default.
     */
    val invalidations: Flow<Unit>
        get() = emptyFlow()

    /**
     * Return features that are relevant for the provided [map] state.
     */
    fun getFeatures(map: MapState): List<Feature>
}
