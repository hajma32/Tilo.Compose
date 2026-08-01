package tilo.compose.core.map

import tilo.compose.core.geometry.BoundingBox
import tilo.compose.core.scale.DistanceCalculator
import tilo.compose.core.scale.DistanceCalculators

/**
 * Map runtime configuration.
 *
 * `cameraBounds` uses the camera projection coordinates. While the visible
 * viewport fits on a constrained axis, its full extent stays inside the bounds.
 * A viewport that is larger than an axis is centered on that axis.
 */
data class MapConfig(
    val minZoom: Double = 0.0,
    val maxZoom: Double = 22.0,
    val wrapHorizontal: Boolean = true,
    val distanceCalculator: DistanceCalculator = DistanceCalculators.Auto,
    val cameraBounds: BoundingBox? = null,
) {
    init {
        require(minZoom.isFinite()) { "minZoom must be finite" }
        require(maxZoom.isFinite()) { "maxZoom must be finite" }
        require(minZoom <= maxZoom) { "minZoom must not be greater than maxZoom" }
    }

    companion object {
        val Default = MapConfig()
    }
}
