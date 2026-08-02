package tilo.compose.core.map

/**
 * A point in physical screen pixels from the top-left corner of the map viewport.
 *
 * This is intentionally distinct from `geometry.Point`, whose coordinates belong to a world CRS.
 */
data class ScreenPoint(
    val x: Double,
    val y: Double,
) {
    init {
        require(x.isFinite() && y.isFinite()) { "screen coordinates must be finite" }
    }
}
