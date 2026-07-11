package tilo.compose.core.layers.raster

import io.ktor.client.request.get
import kotlinx.coroutines.CancellationException
import tilo.compose.core.net.sharedHttpClient
import tilo.compose.core.projection.Epsg4326Projection
import tilo.compose.core.projection.Projection
import tilo.compose.core.tile.TileGrid
import tilo.compose.core.tile.TileRequest

/**
 * OGC WMS GetMap raster source.
 *
 * The requested [crs] must match [projection]. Raster reprojection is
 * intentionally left to the service or to a custom source implementation.
 */
class WMSTileSource(
    override val projection: Projection = Epsg4326Projection,
    override val grid: TileGrid = TileGrid.defaultFor(projection),
    private val baseUrl: String,
    private val layers: String,
    private val crs: String = projection.id,
    private val styles: String = "",
    private val format: String = "image/png",
    private val version: String = "1.1.1",
    private val crsParamName: String = "SRS",
) : RasterTileSource {
    init {
        require(crs == projection.id) {
            "WMS CRS parameter '$crs' must match layer projection ${projection.id}."
        }
    }

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

    /**
     * Builds the WMS GetMap URL. Bounds are already in the source CRS.
     */
    private fun buildUrl(request: TileRequest): String {
        val b = request.bounds
        val west = minOf(b.topLeft.x, b.bottomRight.x)
        val east = maxOf(b.topLeft.x, b.bottomRight.x)
        val south = minOf(b.topLeft.y, b.bottomRight.y)
        val north = maxOf(b.topLeft.y, b.bottomRight.y)
        val bbox = "$west,$south,$east,$north"
        val sep = if ('?' in baseUrl) "&" else "?"
        return "$baseUrl$sep" +
            "SERVICE=WMS&REQUEST=GetMap&VERSION=$version" +
            "&LAYERS=$layers&STYLES=$styles" +
            "&FORMAT=$format&TRANSPARENT=FALSE" +
            "&$crsParamName=$crs" +
            "&WIDTH=${grid.tileSize}&HEIGHT=${grid.tileSize}" +
            "&BBOX=$bbox"
    }
}
