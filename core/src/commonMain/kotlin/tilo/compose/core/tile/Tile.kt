package tilo.compose.core.tile

/** Tile index in XYZ scheme. */
data class TileCoordinate(
    val z: Int,
    val x: Int,
    val y: Int
)

/**
 * Tile payload returned by a Source.
 * `url` is always populated; `bytes` can be null when source is configured for URL-only mode.
 */
data class Tile(
    val coordinate: TileCoordinate,
    val url: String,
    val bytes: ByteArray? = null
)

