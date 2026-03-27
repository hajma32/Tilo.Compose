package tilo.compose.data.mbtiles

import kotlin.time.TimeSource
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
        val parseMark = TimeSource.Monotonic.markNow()
        val parsed = parser.parseTile(bytes)
        MbtilesDiagnostics.log(
            "parseTile z=${tile.z} x=${tile.x} y=${tile.y} bytes=${bytes.size} parse=${parseMark.elapsedNow().inWholeMilliseconds}ms"
        )
        return parsed
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
