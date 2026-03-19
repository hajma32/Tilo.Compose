package tilo.compose.data.mbtiles

import tilo.compose.core.tile.TileCoordinate
import tilo.compose.core.tile.vector.VectorTile
import tilo.compose.core.tile.vector.VectorTileDatasetMetadata
import tilo.compose.core.tile.vector.VectorTileSource
import tilo.compose.data.vectortile.MvtParser

class SqlDelightMbtilesVectorTileSource(
    private val repository: MbtilesRepository,
    private val parser: MvtParser = MvtParser()
) : VectorTileSource {
    override val metadata: VectorTileDatasetMetadata by lazy {
        repository.metadata.toVectorTileDatasetMetadata()
    }

    override fun loadTile(tile: TileCoordinate): VectorTile? {
        if (repository.metadata.contentType == MbtilesContentType.RASTER) return null
        val bytes = repository.readTileBytes(tile) ?: return null
        return parser.parseTile(bytes)
    }

    private fun MbtilesMetadata.toVectorTileDatasetMetadata(): VectorTileDatasetMetadata {
        return VectorTileDatasetMetadata(
            availableZoomLevels = availableZoomLevels,
            minZoom = minZoom,
            maxZoom = maxZoom,
            bounds = bounds
        )
    }
}
