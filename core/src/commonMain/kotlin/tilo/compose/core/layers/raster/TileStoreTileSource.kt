package tilo.compose.core.layers.raster

import tilo.compose.core.projection.Projection
import tilo.compose.core.tile.TileCoordinate
import tilo.compose.core.tile.TileGrid
import tilo.compose.core.tile.TileRequest

/**
 * Tile row addressing used by a raster tile store.
 */
enum class TileRowScheme {
    /**
     * Top-left origin: y grows from north to south.
     */
    XYZ,

    /**
     * Bottom-left origin: y grows from south to north. This is common in
     * MBTiles databases.
     */
    TMS,
}

/**
 * Raster source backed by an app-owned z/x/y tile store.
 *
 * This class deliberately does not open files or SQLite databases itself. Apps
 * or platform adapters provide the `readTile` callback, which makes S-JTSK/Krovak, Web
 * Mercator and project-specific tile stores equally valid as long as
 * the supplied projection and grid describe the stored tiles.
 */
class TileStoreTileSource(
    override val projection: Projection,
    override val grid: TileGrid,
    private val scheme: TileRowScheme = TileRowScheme.TMS,
    private val sourceId: String = "tile-store",
    private val readTile: suspend (TileCoordinate) -> ByteArray?,
) : RasterTileSource {
    override fun cacheKey(request: TileRequest): String {
        val coordinate = sourceCoordinate(request)
        return "${sourceId.segment()}:${projection.id.segment()}:${projection.definition.segment()}:" +
            "${coordinate.z}:${coordinate.x}:${coordinate.y}"
    }

    override suspend fun readTile(request: TileRequest): ByteArray? = readTile(sourceCoordinate(request))

    private fun sourceCoordinate(request: TileRequest): TileCoordinate {
        val (z, x, y) = request.coordinate
        val sourceY =
            when (scheme) {
                TileRowScheme.XYZ -> y
                TileRowScheme.TMS -> grid.nTilesY(z) - 1 - y
            }
        return TileCoordinate(z = z, x = x, y = sourceY)
    }
}

private fun String.segment(): String = "$length:$this"
