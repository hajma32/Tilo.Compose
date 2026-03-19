package tilo.compose.core.tile

/** Inclusive bounds of available tile indexes within one tile matrix (zoom level). */
data class TileMatrixBounds(
    val minX: Int,
    val maxX: Int,
    val minY: Int,
    val maxY: Int
) {
    fun contains(coordinate: TileCoordinate): Boolean {
        return coordinate.x in minX..maxX && coordinate.y in minY..maxY
    }
}

