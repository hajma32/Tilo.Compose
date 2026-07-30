package tilo.compose.core.layers

import tilo.compose.core.projection.Projection

/** Human-readable provider credit and an optional destination URL. */
data class Attribution(
    val label: String,
    val url: String? = null,
)

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

    /**
     * Whether this layer participates in rendering and interaction.
     *
     * A hidden layer is not fetched, rendered, hit-tested, or included in the
     * active attribution list.
     */
    val visible: Boolean
        get() = true

    /** Opacity applied when compositing this layer, from fully transparent `0.0` to opaque `1.0`. */
    val opacity: Double
        get() = 1.0

    /** Inclusive lower zoom limit for this layer, or `null` for no limit. */
    val minZoom: Double?
        get() = null

    /** Inclusive upper zoom limit for this layer, or `null` for no limit. */
    val maxZoom: Double?
        get() = null

    /** Source CRS of data provided by this layer (for example `EPSG:4326`, `EPSG:5514`).
     *  If null, the layer is expected to provide data in the same coordinate system as the viewport (for example `EPSG:3857` for WebMercator).
     * */
    val projection: Projection?
        get() = null

    /**
     * Attribution required by the layer's data source or provider.
     */
    val attributions: List<Attribution>
        get() = emptyList()

    /** Returns whether this layer is active at [zoom]. */
    fun isVisibleAt(zoom: Double): Boolean {
        val minimum = minZoom
        val maximum = maxZoom
        return visible &&
            (minimum == null || zoom >= minimum) &&
            (maximum == null || zoom <= maximum)
    }
}
