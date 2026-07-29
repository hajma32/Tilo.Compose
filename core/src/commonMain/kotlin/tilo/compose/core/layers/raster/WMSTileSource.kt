package tilo.compose.core.layers.raster

import tilo.compose.core.projection.Epsg4326Projection
import tilo.compose.core.projection.Projection
import tilo.compose.core.tile.TileGrid
import tilo.compose.core.tile.TileRequest

/** Axis order used when serializing WMS 1.3.0 BBOX coordinates. */
enum class WMSAxisOrder {
    /** Internal x coordinate followed by y coordinate. */
    XY,

    /** Internal y coordinate followed by x coordinate. */
    YX,
    ;

    companion object {
        /**
         * Returns the standard default for CRSs known to Tilo.
         *
         * WMS 1.3.0 follows authoritative CRS axis order. `EPSG:4326` is the
         * common latitude/longitude exception; custom axis-sensitive CRSs can
         * pass an explicit value to [WMSTileSource].
         */
        fun forCrs(crs: String): WMSAxisOrder = if (crs.equals("EPSG:4326", ignoreCase = true)) YX else XY
    }
}

/**
 * OGC WMS GetMap raster source.
 *
 * The requested [crs] must match [projection]. Raster reprojection is
 * intentionally left to the service or to a custom source implementation.
 */
class WMSTileSource internal constructor(
    override val projection: Projection = Epsg4326Projection,
    override val grid: TileGrid = TileGrid.defaultFor(projection),
    private val baseUrl: String,
    private val layers: String,
    private val crs: String = projection.id,
    private val styles: String = "",
    private val format: String = "image/png",
    private val version: String = "1.1.1",
    private val axisOrder: WMSAxisOrder = WMSAxisOrder.forCrs(crs),
    private val transport: TileHttpTransport,
) : DiagnosticRasterTileSource {
    constructor(
        projection: Projection = Epsg4326Projection,
        grid: TileGrid = TileGrid.defaultFor(projection),
        baseUrl: String,
        layers: String,
        crs: String = projection.id,
        styles: String = "",
        format: String = "image/png",
        version: String = "1.1.1",
        axisOrder: WMSAxisOrder = WMSAxisOrder.forCrs(crs),
    ) : this(projection, grid, baseUrl, layers, crs, styles, format, version, axisOrder, KtorTileHttpTransport())

    init {
        require(crs == projection.id) {
            "WMS CRS parameter '$crs' must match layer projection ${projection.id}."
        }
    }

    override fun cacheKey(request: TileRequest): String = buildUrl(request)

    override suspend fun readTile(request: TileRequest): ByteArray? = transport.readImage(buildUrl(request))

    override suspend fun readTileResult(request: TileRequest): TileReadResult =
        (transport as? DiagnosticTileHttpTransport)?.readImageResult(buildUrl(request))
            ?: transport.readImage(buildUrl(request))?.let(TileReadResult::Success)
            ?: TileReadResult.Missing

    /**
     * Builds the WMS GetMap URL. Bounds are already in the source CRS.
     */
    private fun buildUrl(request: TileRequest): String {
        val b = request.bounds
        val west = minOf(b.topLeft.x, b.bottomRight.x)
        val east = maxOf(b.topLeft.x, b.bottomRight.x)
        val south = minOf(b.topLeft.y, b.bottomRight.y)
        val north = maxOf(b.topLeft.y, b.bottomRight.y)
        val isWms13 = version.startsWith("1.3")
        val bbox =
            if (isWms13 && axisOrder == WMSAxisOrder.YX) {
                "$south,$west,$north,$east"
            } else {
                "$west,$south,$east,$north"
            }
        val crsParamName = if (isWms13) "CRS" else "SRS"
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
