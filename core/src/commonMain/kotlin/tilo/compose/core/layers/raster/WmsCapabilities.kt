package tilo.compose.core.layers.raster

import io.ktor.http.ContentType
import io.ktor.http.Url
import io.ktor.utils.io.charsets.Charset
import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.charsets.forName
import io.ktor.utils.io.charsets.isSupported
import io.ktor.utils.io.core.buildPacket
import io.ktor.utils.io.core.readText
import io.ktor.utils.io.core.writeFully
import tilo.compose.core.geometry.BoundingBox
import tilo.compose.core.projection.Projection
import tilo.compose.core.tile.TileGrid

/** Metadata advertised by a named WMS layer in a GetCapabilities response. */
class WmsLayerCapabilities(
    val name: String,
    val title: String? = null,
    crs: Set<String> = emptySet(),
    boundingBoxes: Map<String, BoundingBox> = emptyMap(),
) {
    val crs: Set<String> = crs.toSet()
    val boundingBoxes: Map<String, BoundingBox> = boundingBoxes.toMap()

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is WmsLayerCapabilities &&
            name == other.name &&
            title == other.title &&
            crs == other.crs &&
            boundingBoxes == other.boundingBoxes

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + (title?.hashCode() ?: 0)
        result = 31 * result + crs.hashCode()
        result = 31 * result + boundingBoxes.hashCode()
        return result
    }

    override fun toString(): String =
        "WmsLayerCapabilities(name=$name, title=$title, crsCount=${crs.size}, " +
            "boundingBoxCount=${boundingBoxes.size})"
}

/**
 * Parsed subset of a WMS GetCapabilities document used to configure tile layers.
 *
 * Bounding boxes are retained per advertised CRS so [tileGridFor] can derive a
 * tile matrix for one layer or the combined extent of multiple layers.
 */
class WmsCapabilities internal constructor(
    val version: WmsVersion,
    val getMapUrl: String?,
    formats: List<String>,
    layers: List<WmsLayerCapabilities>,
    internal val sourceOrigin: WmsHttpOrigin?,
) {
    constructor(
        version: WmsVersion,
        getMapUrl: String?,
        formats: List<String>,
        layers: List<WmsLayerCapabilities>,
    ) : this(version, getMapUrl, formats, layers, sourceOrigin = null)

    val formats: List<String> = formats.toList()
    val layers: List<WmsLayerCapabilities> = layers.toList()

    fun layer(name: String): WmsLayerCapabilities? = layers.firstOrNull { it.name == name }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is WmsCapabilities &&
            version == other.version &&
            getMapUrl == other.getMapUrl &&
            formats == other.formats &&
            layers == other.layers

    override fun hashCode(): Int {
        var result = version.hashCode()
        result = 31 * result + (getMapUrl?.hashCode() ?: 0)
        result = 31 * result + formats.hashCode()
        result = 31 * result + layers.hashCode()
        return result
    }

    override fun toString(): String =
        "WmsCapabilities(version=$version, hasGetMapUrl=${getMapUrl != null}, " +
            "formatCount=${formats.size}, layerCount=${layers.size})"

    fun tileGridFor(
        layerNames: List<String>,
        projection: Projection,
        tileSize: Int = 256,
    ): TileGrid {
        require(layerNames.isNotEmpty()) { "At least one WMS layer name is required." }
        require(tileSize > 0) { "WMS tileSize must be positive." }
        val bounds =
            layerNames
                .map { layerName ->
                    val layer =
                        requireNotNull(layer(layerName)) {
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

    /** Creates a caller-owned layer that must be closed after its last use. */
    fun createTileLayer(
        id: String,
        layerNames: List<String>,
        projection: Projection,
        baseUrl: String? = getMapUrl,
        tileSize: Int = 256,
        options: WmsLayerOptions = WmsLayerOptions(),
    ): WmsTileLayer {
        validateWmsLayerSelection(layerNames, options.styles)
        val resolvedBaseUrl =
            requireNotNull(baseUrl) {
                "WMS GetMap URL was not found in GetCapabilities. Pass baseUrl explicitly."
            }
        validateWmsHeaderOrigin(sourceOrigin, resolvedBaseUrl, options.http)
        val resolvedFormat = resolveWmsImageFormat(options.format, formats)
        return WmsTileLayer(
            id = id,
            baseUrl = resolvedBaseUrl,
            layerNames = layerNames,
            projection = projection,
            grid = tileGridFor(layerNames, projection, tileSize),
            options = options.withProtocolDefaults(resolvedFormat, version),
        )
    }
}

internal fun resolveWmsImageFormat(
    requested: WmsImageFormat?,
    advertised: List<String>,
): WmsImageFormat =
    requested
        ?: advertised.firstNotNullOfOrNull { value -> WmsImageFormat.advertisedOrNull(value) }
        ?: WmsImageFormat.Png.takeIf { advertised.isEmpty() }
        ?: throw IllegalArgumentException(
            "WMS does not advertise a supported raster image format; pass options.format explicitly " +
                "only when its payload decodes as PNG, JPEG, GIF, or WebP.",
        )

/** Downloads and parses the WMS capabilities metadata needed by Tilo's raster pipeline. */
class WmsCapabilitiesLoader {
    suspend fun load(
        url: String,
        http: RasterHttpConfig = RasterHttpConfig(),
    ): WmsCapabilities {
        val requestUrl = capabilitiesUrl(url)
        return parse(fetchWmsCapabilitiesXml(requestUrl, http), wmsHttpOrigin(requestUrl))
    }

    fun parse(xml: String): WmsCapabilities = parse(xml, sourceOrigin = null)

    private fun parse(
        xml: String,
        sourceOrigin: WmsHttpOrigin?,
    ): WmsCapabilities {
        val versionValue = requireNotNull(capabilitiesVersion(xml)) { "WMS capabilities version is missing." }
        val version =
            requireNotNull(WmsVersion.parse(versionValue)) {
                "Unsupported WMS capabilities version."
            }
        val getMapBlock = tagBlock(xml, "GetMap")
        return WmsCapabilities(
            version = version,
            getMapUrl = getMapBlock?.let(::onlineResourceHref),
            formats = getMapBlock?.let { tagTexts(it, "Format") }.orEmpty(),
            layers = parseLayerBlocks(xml).mapNotNull { parseLayer(it, version) },
            sourceOrigin = sourceOrigin,
        )
    }

    private fun parseLayer(
        block: String,
        version: WmsVersion,
    ): WmsLayerCapabilities? {
        val name = directTagText(block, "Name") ?: return null
        val boundingBoxes = boundingBoxes(block, version)
        return WmsLayerCapabilities(
            name = name,
            title = directTagText(block, "Title"),
            crs = crsValues(block) + boundingBoxes.keys,
            boundingBoxes = boundingBoxes,
        )
    }
}

/** Loads capabilities and creates a caller-owned layer that must be closed after use. */
suspend fun createWmsTileLayerFromCapabilities(
    id: String,
    capabilitiesUrl: String,
    layerNames: List<String>,
    projection: Projection,
    tileSize: Int = 256,
    options: WmsLayerOptions = WmsLayerOptions(),
): WmsTileLayer {
    val capabilities = WmsCapabilitiesLoader().load(capabilitiesUrl, options.http)
    return capabilities.createTileLayer(
        id = id,
        layerNames = layerNames,
        projection = projection,
        tileSize = tileSize,
        options = options,
    )
}

private suspend fun fetchWmsCapabilitiesXml(
    url: String,
    http: RasterHttpConfig,
): String {
    val response = http.readResponse(url)
    return response.requireWmsCapabilitiesText()
}

internal fun RasterHttpResponse.requireWmsCapabilitiesText(): String {
    if (!isSuccess) {
        throw RasterHttpStatusException(
            statusCode = statusCode,
            message = "WMS GetCapabilities returned HTTP $statusCode.",
            headers = headers,
        )
    }
    check(bodyBytes.isNotEmpty()) { "WMS GetCapabilities returned an empty response body." }
    return bodyAsCapabilitiesText()
}

private fun RasterHttpResponse.bodyAsCapabilitiesText(): String {
    val headerCharset =
        header("Content-Type")
            ?.let { value ->
                runCatching {
                    ContentType.parse(value).parameter("charset")?.let { name ->
                        require(Charsets.isSupported(name)) { "Unsupported WMS XML charset." }
                        Charsets.forName(name)
                    }
                }.getOrElse { cause ->
                    throw IllegalArgumentException("Invalid WMS Content-Type header.", cause)
                }
            }
    val xmlCharsetName = xmlEncodingName(bodyBytes)
    val charset =
        headerCharset
            ?: xmlByteOrderCharset(bodyBytes)
            ?: xmlCharsetName?.let { name ->
                require(Charsets.isSupported(name)) { "Unsupported WMS XML charset." }
                Charsets.forName(name)
            }
            ?: Charsets.UTF_8
    return bodyBytes.decode(charset)
}

private fun ByteArray.decode(charset: Charset): String = buildPacket { writeFully(this@decode) }.readText(charset)

private fun xmlByteOrderCharset(bytes: ByteArray): Charset? {
    val name =
        when {
            bytes.hasPrefix(0xEF, 0xBB, 0xBF) -> "UTF-8"
            bytes.hasPrefix(0xFE, 0xFF) || bytes.hasPrefix(0xFF, 0xFE) -> "UTF-16"
            else -> return null
        }
    require(Charsets.isSupported(name)) { "Unsupported WMS XML charset." }
    return Charsets.forName(name)
}

private fun ByteArray.hasPrefix(vararg prefix: Int): Boolean =
    size >= prefix.size && prefix.indices.all { index -> this[index] == prefix[index].toByte() }

private fun xmlEncodingName(bytes: ByteArray): String? {
    val prefix =
        buildString(minOf(bytes.size, 256)) {
            bytes.take(256).forEach { byte ->
                val value = byte.toInt() and 0xFF
                append(
                    when (value) {
                        in 0x20..0x7E, 0x09, 0x0A, 0x0D -> value.toChar()
                        else -> ' '
                    },
                )
            }
        }
    return Regex("""<\?xml\s+[^>]*encoding\s*=\s*[\"']([^\"']+)[\"']""", RegexOption.IGNORE_CASE)
        .find(prefix)
        ?.groupValues
        ?.get(1)
}

internal fun capabilitiesUrl(url: String): String =
    wmsRequestUrl(
        baseUrl = url,
        parameters = listOf("SERVICE" to "WMS", "REQUEST" to "GetCapabilities"),
    )

internal data class WmsHttpOrigin(
    val scheme: String,
    val host: String,
    val port: Int,
)

internal fun wmsHttpOrigin(url: String): WmsHttpOrigin? =
    runCatching { Url(url) }
        .getOrNull()
        ?.takeIf { parsed -> parsed.host.isNotBlank() }
        ?.let { parsed ->
            WmsHttpOrigin(
                scheme = parsed.protocol.name.lowercase(),
                host = parsed.host.lowercase(),
                port = parsed.port,
            )
        }

private fun validateWmsHeaderOrigin(
    sourceOrigin: WmsHttpOrigin?,
    getMapUrl: String,
    http: RasterHttpConfig,
) {
    if (http.headers.isEmpty() || http.allowCrossOriginHeaders || sourceOrigin == null) return
    require(sourceOrigin == wmsHttpOrigin(getMapUrl)) {
        "WMS GetMap endpoint has a different origin than GetCapabilities; request headers were not forwarded. " +
            "Set allowCrossOriginHeaders only when the advertised endpoint is trusted."
    }
}

private fun capabilitiesVersion(xml: String): String? {
    val rootTag =
        Regex("""<(WMT_MS_Capabilities|WMS_Capabilities)\b[^>]*>""")
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
    version: WmsVersion,
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
                version == WmsVersion.V1_3_0 && WmsAxisOrder.forCrs(crs) == WmsAxisOrder.YX
            val bounds =
                if (usesYxOrder) {
                    BoundingBox.fromExtents(minX = minY, minY = minX, maxX = maxY, maxY = maxX)
                } else {
                    BoundingBox.fromExtents(minX = minX, minY = minY, maxX = maxX, maxY = maxY)
                }
            crs to bounds
        }.toMap()

private fun onlineResourceHref(block: String): String? {
    val onlineResource = Regex("""<OnlineResource\b[^>]*>""").find(block)?.value ?: return null
    val attributes = attrs(onlineResource)
    return attributes["xlink:href"] ?: attributes["href"]
}

private fun tagBlock(
    xml: String,
    tag: String,
): String? = Regex("""<$tag\b[^>]*>[\s\S]*?</$tag>""").find(xml)?.value

private fun tagTexts(
    xml: String,
    tag: String,
): List<String> =
    Regex("""<$tag\b[^>]*>([\s\S]*?)</$tag>""")
        .findAll(xml)
        .map { it.groupValues[1].trim().xmlUnescaped() }
        .filter { it.isNotBlank() }
        .toList()

private fun directTagText(
    block: String,
    tag: String,
): String? = directTagTexts(block, tag).firstOrNull()

private fun directTagTexts(
    block: String,
    tag: String,
): List<String> {
    val content =
        block
            .substringAfter('>', missingDelimiterValue = block)
            .substringBeforeLast("</Layer>", missingDelimiterValue = block)
    val nestedRanges =
        parseLayerBlocks(content).map { nested ->
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

private fun attr(
    xml: String,
    name: String,
): String? = attrs(xml.substringBefore('>', missingDelimiterValue = xml))[name]

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
