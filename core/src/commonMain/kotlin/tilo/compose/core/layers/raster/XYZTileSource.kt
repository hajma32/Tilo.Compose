package tilo.compose.core.layers.raster

import tilo.compose.core.projection.Epsg3857Projection
import tilo.compose.core.projection.Projection
import tilo.compose.core.tile.TileGrid
import tilo.compose.core.tile.TileRequest

/**
 * URL-template raster source using `{z}`, `{x}`, `{y}` placeholders.
 *
 * XYZ slippy-map sources are Web Mercator by default. Pass [projection] and
 * [grid] explicitly for custom grids that use the same address shape.
 */
class XYZTileSource internal constructor(
    private val urlTemplate: String,
    override val projection: Projection = Epsg3857Projection,
    override val grid: TileGrid = TileGrid.defaultFor(projection),
    private val tms: Boolean = false,
    private val transport: TileHttpTransport,
) : DiagnosticRasterTileSource {
    constructor(
        urlTemplate: String,
        projection: Projection = Epsg3857Projection,
        grid: TileGrid = TileGrid.defaultFor(projection),
        tms: Boolean = false,
    ) : this(urlTemplate, projection, grid, tms, KtorTileHttpTransport())

    override fun cacheKey(request: TileRequest): String = buildUrl(request)

    override suspend fun readTile(request: TileRequest): ByteArray? = transport.readImage(buildUrl(request))

    override suspend fun readTileResult(request: TileRequest): TileReadResult =
        (transport as? DiagnosticTileHttpTransport)?.readImageResult(buildUrl(request))
            ?: transport.readImage(buildUrl(request))?.let(TileReadResult::Success)
            ?: TileReadResult.Missing

    private fun buildUrl(request: TileRequest): String {
        val (z, x, y) = request.coordinate
        val sourceY = if (tms) grid.nTilesY(z) - 1 - y else y
        return urlTemplate
            .replace("{z}", z.toString())
            .replace("{x}", x.toString())
            .replace("{y}", sourceY.toString())
    }
}
