package tilo.compose.core.layers.raster

import tilo.compose.core.projection.Projection
import tilo.compose.core.tile.TileGrid
import tilo.compose.core.tile.TileRequest

/**
 * Byte source for a raster tile layer.
 *
 * A source owns the addressing details for one raster backend: WMS, XYZ, MBTiles
 * or a custom project-specific provider. It does not decide which tiles are
 * visible; that stays in [RasterTileLayer] through [grid].
 */
interface RasterTileSource {
    val projection: Projection
    val grid: TileGrid

    /**
     * Stable key used by the in-memory tile cache and in-flight request
     * coalescing. Include every source setting that can change returned bytes.
     */
    fun cacheKey(request: TileRequest): String

    /**
     * Returns encoded image bytes for [request], or null when the tile is
     * unavailable. Implementations should let coroutine cancellation propagate.
     */
    suspend fun readTile(request: TileRequest): ByteArray?
}
