package tilo.compose.core.layers.raster

import io.ktor.http.URLBuilder
import tilo.compose.core.projection.Epsg4326Projection
import tilo.compose.core.projection.Projection
import tilo.compose.core.tile.TileGrid
import tilo.compose.core.tile.TileRequest

/** Axis order used when serializing WMS 1.3.0 BBOX coordinates. */
enum class WmsAxisOrder {
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
         * pass an explicit value to [WmsTileSource].
         */
        fun forCrs(crs: String): WmsAxisOrder = if (crs.equals("EPSG:4326", ignoreCase = true)) YX else XY
    }
}

/**
 * OGC WMS GetMap raster source.
 *
 * The requested `crs` must match the supplied projection. Raster reprojection is
 * intentionally left to the service or to a custom source implementation.
 */
class WmsTileSource internal constructor(
    override val projection: Projection = Epsg4326Projection,
    override val grid: TileGrid = TileGrid.defaultFor(projection),
    private val baseUrl: String,
    layerNames: List<String>,
    styles: List<String> = emptyList(),
    private val format: WmsImageFormat = WmsImageFormat.Png,
    private val version: WmsVersion = WmsVersion.V1_1_1,
    private val axisOrder: WmsAxisOrder = WmsAxisOrder.forCrs(projection.id),
    private val transport: TileHttpTransport,
) : DiagnosticRasterTileSource {
    constructor(
        projection: Projection = Epsg4326Projection,
        grid: TileGrid = TileGrid.defaultFor(projection),
        baseUrl: String,
        layerNames: List<String>,
        styles: List<String> = emptyList(),
        format: WmsImageFormat = WmsImageFormat.Png,
        version: WmsVersion = WmsVersion.V1_1_1,
        axisOrder: WmsAxisOrder = WmsAxisOrder.forCrs(projection.id),
    ) : this(projection, grid, baseUrl, layerNames, styles, format, version, axisOrder, KtorTileHttpTransport())

    private val layers: String
    private val styles: String

    init {
        val names = layerNames.toList()
        val resolvedStyles = styles.toList()
        validateWmsLayerSelection(names, resolvedStyles)
        layers = names.joinToString(",")
        this.styles = resolvedStyles.joinToString(",")
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
        val isWms13 = version == WmsVersion.V1_3_0
        val bbox =
            if (isWms13 && axisOrder == WmsAxisOrder.YX) {
                "$south,$west,$north,$east"
            } else {
                "$west,$south,$east,$north"
            }
        val crsParamName = if (isWms13) "CRS" else "SRS"
        return wmsRequestUrl(
            baseUrl = baseUrl,
            additionalReplacedNames = setOf("SRS", "CRS"),
            parameters =
                listOf(
                    "SERVICE" to "WMS",
                    "REQUEST" to "GetMap",
                    "VERSION" to version.value,
                    "LAYERS" to layers,
                    "STYLES" to styles,
                    "FORMAT" to format.mimeType,
                    "TRANSPARENT" to "FALSE",
                    crsParamName to projection.id,
                    "WIDTH" to grid.tileSize.toString(),
                    "HEIGHT" to grid.tileSize.toString(),
                    "BBOX" to bbox,
                ),
        )
    }
}

/** Replaces WMS-controlled KVP names while retaining unrelated endpoint query parameters. */
internal fun wmsRequestUrl(
    baseUrl: String,
    parameters: List<Pair<String, String>>,
    additionalReplacedNames: Set<String> = emptySet(),
): String {
    val builder = URLBuilder(baseUrl)
    val replacedNames = additionalReplacedNames.mapTo(mutableSetOf(), String::uppercase)
    parameters.mapTo(replacedNames) { (name, _) -> name.uppercase() }
    builder.parameters
        .names()
        .filter { name -> name.uppercase() in replacedNames }
        .forEach(builder.parameters::remove)
    parameters.forEach { (name, value) -> builder.parameters.append(name, value) }
    return builder.buildString()
}
