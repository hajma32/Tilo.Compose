package tilo.compose.data.mbtiles

import tilo.compose.core.geometry.BoundingBox
import tilo.compose.core.tile.TileCoordinate

enum class MbtilesContentType {
    VECTOR,
    RASTER,
    UNKNOWN
}

data class MbtilesMetadata(
    val availableZoomLevels: Set<Int>,
    val minZoom: Int? = null,
    val maxZoom: Int? = null,
    val bounds: BoundingBox? = null,
    val format: String? = null,
    val contentType: MbtilesContentType = MbtilesContentType.UNKNOWN
)

interface MbtilesRepository {
    val metadata: MbtilesMetadata

    fun readTileBytes(tile: TileCoordinate): ByteArray?
}

