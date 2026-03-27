package tilo.compose.core.layers

import tilo.compose.core.projection.Projection

/**
 * Represents a map layer responsible for rendering and updating map elements.
 *
 * Implementations should be platform-agnostic and lightweight. Platform-specific
 * behavior should be placed behind `expect/actual` or small adapters when needed.
 */
interface Layer {
    /**
     * Unique identifier for the layer.
     */
    val id: String

    /**
     * Draw order relative to other layers. Lower values render first (below higher values).
     * Layers with equal zIndex preserve their order in the input list.
     */
    val zIndex: Int
        get() = 0

    /** Source CRS of data provided by this layer (for example `EPSG:4326`, `EPSG:5514`).
     *  If null, the layer is expected to provide data in the same coordinate system as the viewport (for example `EPSG:3857` for WebMercator).
     * */
    val projection: Projection?
        get() = null
}
