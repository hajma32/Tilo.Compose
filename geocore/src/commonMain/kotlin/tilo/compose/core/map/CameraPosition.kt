package tilo.compose.core.map

import tilo.compose.core.geometry.Point

/**
 * Immutable snapshot of a map camera.
 *
 * `center` is expressed in the active map projection, `zoom` is the map zoom level, and
 * `bearing` is clockwise rotation in degrees. A [MapState] clamps zoom to its configuration and
 * normalizes bearing to the `[0, 360)` range when applying a position.
 */
data class CameraPosition(
    val center: Point,
    val zoom: Double,
    val bearing: Double = 0.0,
) {
    init {
        require(center.x.isFinite() && center.y.isFinite()) { "center coordinates must be finite" }
        require(zoom.isFinite()) { "zoom must be finite" }
        require(bearing.isFinite()) { "bearing must be finite" }
    }
}
