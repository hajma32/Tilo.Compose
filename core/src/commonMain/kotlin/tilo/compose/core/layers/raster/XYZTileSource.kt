package tilo.compose.core.layers.raster

import io.ktor.client.request.get
import kotlinx.coroutines.CancellationException
import tilo.compose.core.net.sharedHttpClient
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
class XYZTileSource(
    private val urlTemplate: String,
    override val projection: Projection = Epsg3857Projection,
    override val grid: TileGrid = TileGrid.defaultFor(projection),
    private val tms: Boolean = false,
) : RasterTileSource {
    private val http = sharedHttpClient()

    override fun cacheKey(request: TileRequest): String = buildUrl(request)

    override suspend fun readTile(request: TileRequest): ByteArray? =
        try {
            val response = http.get(buildUrl(request))
            response.readTileImageBytesOrNull()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            null
        }

    private fun buildUrl(request: TileRequest): String {
        val (z, x, y) = request.coordinate
        val sourceY = if (tms) grid.nTilesY(z) - 1 - y else y
        return urlTemplate
            .replace("{z}", z.toString())
            .replace("{x}", x.toString())
            .replace("{y}", sourceY.toString())
    }
}
