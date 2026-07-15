package tilo.compose.core.layers.raster

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import tilo.compose.core.geometry.BoundingBox
import tilo.compose.core.layers.Attribution
import tilo.compose.core.net.sharedHttpClient
import tilo.compose.core.projection.Projection
import tilo.compose.core.tile.TileGrid

data class WMSLayerCapabilities(
    val name: String,
    val title: String? = null,
    val crs: Set<String> = emptySet(),
    val boundingBoxes: Map<String, BoundingBox> = emptyMap(),
)

data class WMSCapabilities(
    val version: String,
    val getMapUrl: String?,
    val formats: List<String>,
    val layers: List<WMSLayerCapabilities>,
) {
    fun layer(name: String): WMSLayerCapabilities? =
        layers.firstOrNull { it.name == name }

    fun tileGridFor(
        layerName: String,
        projection: Projection,
        tileSize: Int = 256,
    ): TileGrid = tileGridFor(layerName.toWMSLayerNames(), projection, tileSize)

    fun tileGridFor(
        layerNames: List<String>,
        projection: Projection,
        tileSize: Int = 256,
    ): TileGrid {
        require(layerNames.isNotEmpty()) { "At least one WMS layer name is required." }
        val bounds = layerNames.map { layerName ->
            val layer = requireNotNull(layer(layerName)) {
                "WMS layer '$layerName' was not found in GetCapabilities."
            }
            requireNotNull(layer.boundingBoxes[projection.id]) {
                "WMS layer '$layerName' does not expose a ${projection.id} BoundingBox."
            }
        }.reduce { combined, next ->
            BoundingBox.fromExtents(
                minX = minOf(combined.minX, next.minX),
                maxX = maxOf(combined.maxX, next.maxX),
                minY = minOf(combined.minY, next.minY),
                maxY = maxOf(combined.maxY, next.maxY),
            )
        }

        return TileGrid(
            originX = bounds.minX,
            originY = bounds.maxY,
            worldWidth = bounds.maxX - bounds.minX,
            nTilesX0 = 1,
            nTilesY0 = 1,
            tileSize = tileSize,
        )
    }

    fun createTileLayer(
        id: String,
        layerName: String,
        projection: Projection,
        baseUrl: String? = getMapUrl,
        styles: String = "",
        format: String = formats.firstOrNull() ?: "image/png",
        getMapVersion: String = "1.1.1",
        axisOrder: WMSAxisOrder = WMSAxisOrder.forCrs(projection.id),
        zIndex: Int = 0,
        visible: Boolean = true,
        minZoom: Double? = null,
        maxZoom: Double? = null,
        tileSize: Int = 256,
        maxVisibleTiles: Int = 9,
        prefetchMargin: Int = 1,
        overviewZoomOffset: Int = 2,
        maxOverviewTiles: Int = 4,
        overviewPrefetchMargin: Int = 1,
        attributions: List<Attribution> = emptyList(),
        fetchConfig: TileFetchConfig = TileFetchConfig(),
    ): WMSTileLayer = createTileLayer(
        id = id,
        layerNames = layerName.toWMSLayerNames(),
        projection = projection,
        baseUrl = baseUrl,
        styles = styles,
        format = format,
        getMapVersion = getMapVersion,
        axisOrder = axisOrder,
        zIndex = zIndex,
        visible = visible,
        minZoom = minZoom,
        maxZoom = maxZoom,
        tileSize = tileSize,
        maxVisibleTiles = maxVisibleTiles,
        prefetchMargin = prefetchMargin,
        overviewZoomOffset = overviewZoomOffset,
        maxOverviewTiles = maxOverviewTiles,
        overviewPrefetchMargin = overviewPrefetchMargin,
        attributions = attributions,
        fetchConfig = fetchConfig,
    )

    fun createTileLayer(
        id: String,
        layerNames: List<String>,
        projection: Projection,
        baseUrl: String? = getMapUrl,
        styles: String = "",
        format: String = formats.firstOrNull() ?: "image/png",
        getMapVersion: String = "1.1.1",
        axisOrder: WMSAxisOrder = WMSAxisOrder.forCrs(projection.id),
        zIndex: Int = 0,
        visible: Boolean = true,
        minZoom: Double? = null,
        maxZoom: Double? = null,
        tileSize: Int = 256,
        maxVisibleTiles: Int = 9,
        prefetchMargin: Int = 1,
        overviewZoomOffset: Int = 2,
        maxOverviewTiles: Int = 4,
        overviewPrefetchMargin: Int = 1,
        attributions: List<Attribution> = emptyList(),
        fetchConfig: TileFetchConfig = TileFetchConfig(),
    ): WMSTileLayer {
        val resolvedBaseUrl = requireNotNull(baseUrl) {
            "WMS GetMap URL was not found in GetCapabilities. Pass baseUrl explicitly."
        }
        return WMSTileLayer(
            id = id,
            projection = projection,
            grid = tileGridFor(layerNames, projection, tileSize),
            baseUrl = resolvedBaseUrl,
            layers = layerNames.joinToString(","),
            crs = projection.id,
            styles = styles,
            format = format,
            version = getMapVersion,
            axisOrder = axisOrder,
            zIndex = zIndex,
            visible = visible,
            minZoom = minZoom,
            maxZoom = maxZoom,
            maxVisibleTiles = maxVisibleTiles,
            prefetchMargin = prefetchMargin,
            overviewZoomOffset = overviewZoomOffset,
            maxOverviewTiles = maxOverviewTiles,
            overviewPrefetchMargin = overviewPrefetchMargin,
            attributions = attributions,
            fetchConfig = fetchConfig,
        )
    }
}

private fun String.toWMSLayerNames(): List<String> =
    split(',').map(String::trim).filter(String::isNotEmpty)

class WMSCapabilitiesLoader(
    private val fetchCapabilities: suspend (String) -> String = ::fetchWMSCapabilitiesXml,
) {
    suspend fun load(url: String): WMSCapabilities =
        parse(fetchCapabilities(capabilitiesUrl(url)))

    fun parse(xml: String): WMSCapabilities {
        val version = capabilitiesVersion(xml) ?: "1.1.1"
        val getMapBlock = tagBlock(xml, "GetMap")
        return WMSCapabilities(
            version = version,
            getMapUrl = getMapBlock?.let(::onlineResourceHref),
            formats = getMapBlock?.let { tagTexts(it, "Format") }.orEmpty(),
            layers = parseLayerBlocks(xml).mapNotNull { parseLayer(it, version) },
        )
    }

    private fun parseLayer(
        block: String,
        version: String,
    ): WMSLayerCapabilities? {
        val name = directTagText(block, "Name") ?: return null
        val boundingBoxes = boundingBoxes(block, version)
        return WMSLayerCapabilities(
            name = name,
            title = directTagText(block, "Title"),
            crs = crsValues(block) + boundingBoxes.keys,
            boundingBoxes = boundingBoxes,
        )
    }
}

suspend fun createWMSTileLayerFromCapabilities(
    id: String,
    capabilitiesUrl: String,
    layerName: String,
    projection: Projection,
    styles: String = "",
    format: String? = null,
    getMapVersion: String = "1.1.1",
    axisOrder: WMSAxisOrder = WMSAxisOrder.forCrs(projection.id),
    zIndex: Int = 0,
    visible: Boolean = true,
    minZoom: Double? = null,
    maxZoom: Double? = null,
    tileSize: Int = 256,
    maxVisibleTiles: Int = 9,
    prefetchMargin: Int = 1,
    overviewZoomOffset: Int = 2,
    maxOverviewTiles: Int = 4,
    overviewPrefetchMargin: Int = 1,
    attributions: List<Attribution> = emptyList(),
    fetchConfig: TileFetchConfig = TileFetchConfig(),
): WMSTileLayer {
    val capabilities = WMSCapabilitiesLoader().load(capabilitiesUrl)
    return capabilities.createTileLayer(
        id = id,
        layerName = layerName,
        projection = projection,
        styles = styles,
        format = format ?: capabilities.formats.firstOrNull() ?: "image/png",
        getMapVersion = getMapVersion,
        axisOrder = axisOrder,
        zIndex = zIndex,
        visible = visible,
        minZoom = minZoom,
        maxZoom = maxZoom,
        tileSize = tileSize,
        maxVisibleTiles = maxVisibleTiles,
        prefetchMargin = prefetchMargin,
        overviewZoomOffset = overviewZoomOffset,
        maxOverviewTiles = maxOverviewTiles,
        overviewPrefetchMargin = overviewPrefetchMargin,
        attributions = attributions,
        fetchConfig = fetchConfig,
    )
}

suspend fun createWMSTileLayerFromCapabilities(
    id: String,
    capabilitiesUrl: String,
    layerNames: List<String>,
    projection: Projection,
    styles: String = "",
    format: String? = null,
    getMapVersion: String = "1.1.1",
    axisOrder: WMSAxisOrder = WMSAxisOrder.forCrs(projection.id),
    zIndex: Int = 0,
    visible: Boolean = true,
    minZoom: Double? = null,
    maxZoom: Double? = null,
    tileSize: Int = 256,
    maxVisibleTiles: Int = 9,
    prefetchMargin: Int = 1,
    overviewZoomOffset: Int = 2,
    maxOverviewTiles: Int = 4,
    overviewPrefetchMargin: Int = 1,
    attributions: List<Attribution> = emptyList(),
    fetchConfig: TileFetchConfig = TileFetchConfig(),
): WMSTileLayer {
    val capabilities = WMSCapabilitiesLoader().load(capabilitiesUrl)
    return capabilities.createTileLayer(
        id = id,
        layerNames = layerNames,
        projection = projection,
        styles = styles,
        format = format ?: capabilities.formats.firstOrNull() ?: "image/png",
        getMapVersion = getMapVersion,
        axisOrder = axisOrder,
        zIndex = zIndex,
        visible = visible,
        minZoom = minZoom,
        maxZoom = maxZoom,
        tileSize = tileSize,
        maxVisibleTiles = maxVisibleTiles,
        prefetchMargin = prefetchMargin,
        overviewZoomOffset = overviewZoomOffset,
        maxOverviewTiles = maxOverviewTiles,
        overviewPrefetchMargin = overviewPrefetchMargin,
        attributions = attributions,
        fetchConfig = fetchConfig,
    )
}

private suspend fun fetchWMSCapabilitiesXml(url: String): String {
    val http = sharedHttpClient()
    return try {
        http.get(url).bodyAsText()
    } catch (error: CancellationException) {
        throw error
    }
}

private fun capabilitiesUrl(url: String): String {
    val lower = url.lowercase()
    if ("request=getcapabilities" in lower) return url
    val sep = if ('?' in url) "&" else "?"
    return url + sep + "SERVICE=WMS&REQUEST=GetCapabilities"
}

private fun capabilitiesVersion(xml: String): String? {
    val rootTag = Regex("""<(WMT_MS_Capabilities|WMS_Capabilities)\b[^>]*>""")
        .find(xml)
        ?.value
        ?: return null
    return attr(rootTag, "version")
}

private fun parseLayerBlocks(xml: String): List<String> {
    val tags = Regex("""</?Layer\b[^>]*>""").findAll(xml)
    val stack = mutableListOf<Int>()
    val blocks = mutableListOf<String>()
    for (tag in tags) {
        if (tag.value.startsWith("</")) {
            val start = stack.removeLastOrNull() ?: continue
            blocks += xml.substring(start, tag.range.last + 1)
        } else if (!tag.value.endsWith("/>")) {
            stack += tag.range.first
        }
    }
    return blocks
}

private fun crsValues(block: String): Set<String> =
    (directTagTexts(block, "SRS") + directTagTexts(block, "CRS"))
        .flatMap { it.split(Regex("""\s+""")) }
        .filter { it.isNotBlank() }
        .toSet()

private fun boundingBoxes(
    block: String,
    version: String,
): Map<String, BoundingBox> =
    Regex("""<BoundingBox\b[^>]*>""")
        .findAll(block)
        .mapNotNull { match ->
            val attributes = attrs(match.value)
            val crs = attributes["CRS"] ?: attributes["SRS"] ?: return@mapNotNull null
            val minX = attributes["minx"]?.toDoubleOrNull() ?: return@mapNotNull null
            val minY = attributes["miny"]?.toDoubleOrNull() ?: return@mapNotNull null
            val maxX = attributes["maxx"]?.toDoubleOrNull() ?: return@mapNotNull null
            val maxY = attributes["maxy"]?.toDoubleOrNull() ?: return@mapNotNull null
            val usesYxOrder =
                version.startsWith("1.3") && WMSAxisOrder.forCrs(crs) == WMSAxisOrder.YX
            val bounds =
                if (usesYxOrder) {
                    BoundingBox.fromExtents(minX = minY, minY = minX, maxX = maxY, maxY = maxX)
                } else {
                    BoundingBox.fromExtents(minX = minX, minY = minY, maxX = maxX, maxY = maxY)
                }
            crs to bounds
        }
        .toMap()

private fun onlineResourceHref(block: String): String? {
    val onlineResource = Regex("""<OnlineResource\b[^>]*>""").find(block)?.value ?: return null
    val attributes = attrs(onlineResource)
    return attributes["xlink:href"] ?: attributes["href"]
}

private fun tagBlock(xml: String, tag: String): String? =
    Regex("""<$tag\b[^>]*>[\s\S]*?</$tag>""").find(xml)?.value

private fun tagTexts(xml: String, tag: String): List<String> =
    Regex("""<$tag\b[^>]*>([\s\S]*?)</$tag>""")
        .findAll(xml)
        .map { it.groupValues[1].trim().xmlUnescaped() }
        .filter { it.isNotBlank() }
        .toList()

private fun directTagText(block: String, tag: String): String? =
    directTagTexts(block, tag).firstOrNull()

private fun directTagTexts(block: String, tag: String): List<String> {
    val content = block.substringAfter('>', missingDelimiterValue = block)
        .substringBeforeLast("</Layer>", missingDelimiterValue = block)
    val nestedRanges = parseLayerBlocks(content).map { nested ->
        val start = content.indexOf(nested)
        start until (start + nested.length)
    }
    return Regex("""<$tag\b[^>]*>([\s\S]*?)</$tag>""")
        .findAll(content)
        .filter { match -> nestedRanges.none { range -> match.range.first in range } }
        .map { it.groupValues[1].trim().xmlUnescaped() }
        .filter { it.isNotBlank() }
        .toList()
}

private fun attr(xml: String, name: String): String? =
    attrs(xml.substringBefore('>', missingDelimiterValue = xml))[name]

private fun attrs(tag: String): Map<String, String> {
    val doubleQuoted = Regex("([A-Za-z_:][A-Za-z0-9_:.-]*)\\s*=\\s*\"([^\"]*)\"")
    val singleQuoted = Regex("""([A-Za-z_:][A-Za-z0-9_:.-]*)\s*=\s*'([^']*)'""")
    return (doubleQuoted.findAll(tag) + singleQuoted.findAll(tag))
        .associate { it.groupValues[1] to it.groupValues[2].xmlUnescaped() }
}

private fun String.xmlUnescaped(): String =
    replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
