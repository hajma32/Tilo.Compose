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
        val resolvedFormat =
            resolveWmsImageFormat(
                requested = options.format,
                advertised = formats,
                requireTransparency = options.transparent,
            )
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
    requireTransparency: Boolean,
): WmsImageFormat {
    requested?.let { return it }
    if (advertised.isEmpty()) return WmsImageFormat.Png

    val supported = advertised.mapNotNull(WmsImageFormat::advertisedOrNull)
    if (requireTransparency) {
        supported.firstOrNull(WmsImageFormat::supportsTransparency)?.let { return it }
        throw IllegalArgumentException(
            "WMS does not advertise a supported transparent raster image format; " +
                "set options.format explicitly to override automatic format selection.",
        )
    }
    return supported.firstOrNull()
        ?: throw IllegalArgumentException(
            "WMS does not advertise a supported raster image format; pass options.format explicitly " +
                "only when its payload decodes as PNG, JPEG, GIF, or WebP.",
        )
}

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
            layers = parseLayers(xml, version),
            sourceOrigin = sourceOrigin,
        )
    }

    private fun parseLayers(
        xml: String,
        version: WmsVersion,
    ): List<WmsLayerCapabilities> =
        buildList {
            parseLayerNodes(xml).forEach { root ->
                collectLayers(
                    node = root,
                    version = version,
                    destination = this,
                )
            }
        }

    private fun collectLayers(
        node: WmsLayerNode,
        version: WmsVersion,
        destination: MutableList<WmsLayerCapabilities>,
    ) {
        val effectiveCrs = mutableSetOf<String>()
        val effectiveBoundingBoxes = mutableMapOf<String, BoundingBox>()
        val frames = ArrayDeque<WmsLayerTraversalFrame>()
        frames.addLast(WmsLayerTraversalFrame.Visit(node))
        while (frames.isNotEmpty()) {
            when (val frame = frames.removeLast()) {
                is WmsLayerTraversalFrame.Visit -> {
                    val localBoundingBoxes = boundingBoxes(frame.node.directContent, version)
                    val previousBoundingBoxes =
                        localBoundingBoxes.map { (crs, bounds) ->
                            WmsBoundingBoxOverride(crs, effectiveBoundingBoxes.put(crs, bounds))
                        }
                    val addedCrs =
                        buildList {
                            (crsValues(frame.node.directContent) + localBoundingBoxes.keys).forEach { crs ->
                                if (effectiveCrs.add(crs)) add(crs)
                            }
                        }
                    val layer =
                        tagTexts(frame.node.directContent, "Name").firstOrNull()?.let { name ->
                            WmsLayerCapabilities(
                                name = name,
                                title = tagTexts(frame.node.directContent, "Title").firstOrNull(),
                                crs = effectiveCrs,
                                boundingBoxes = effectiveBoundingBoxes,
                            )
                        }
                    frames.addLast(
                        WmsLayerTraversalFrame.Leave(
                            addedCrs = addedCrs,
                            previousBoundingBoxes = previousBoundingBoxes,
                            layer = layer,
                        ),
                    )
                    frame.node.children.asReversed().forEach { child ->
                        frames.addLast(WmsLayerTraversalFrame.Visit(child))
                    }
                }

                is WmsLayerTraversalFrame.Leave -> {
                    frame.layer?.let(destination::add)
                    frame.addedCrs.forEach(effectiveCrs::remove)
                    frame.previousBoundingBoxes.asReversed().forEach { overridden ->
                        overridden.previous?.let { previous ->
                            effectiveBoundingBoxes[overridden.crs] = previous
                        } ?: effectiveBoundingBoxes.remove(overridden.crs)
                    }
                }
            }
        }
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

private data class WmsLayerNode(
    val directContent: String,
    val children: List<WmsLayerNode>,
)

private sealed interface WmsLayerTraversalFrame {
    data class Visit(
        val node: WmsLayerNode,
    ) : WmsLayerTraversalFrame

    data class Leave(
        val addedCrs: List<String>,
        val previousBoundingBoxes: List<WmsBoundingBoxOverride>,
        val layer: WmsLayerCapabilities?,
    ) : WmsLayerTraversalFrame
}

private data class WmsBoundingBoxOverride(
    val crs: String,
    val previous: BoundingBox?,
)

private data class ParsedWmsLayerNode(
    val node: WmsLayerNode,
    val startIndex: Int,
    val endIndexExclusive: Int,
)

private data class OpenWmsLayer(
    val startIndex: Int,
    val contentStartIndex: Int,
    val children: MutableList<ParsedWmsLayerNode> = mutableListOf(),
)

private fun parseLayerNodes(xml: String): List<WmsLayerNode> {
    val tags = Regex("""</?Layer\b[^>]*>""").findAll(xml)
    val stack = mutableListOf<OpenWmsLayer>()
    val roots = mutableListOf<ParsedWmsLayerNode>()
    for (tag in tags) {
        if (tag.value.startsWith("</")) {
            val open = stack.removeLastOrNull() ?: continue
            val parsed =
                ParsedWmsLayerNode(
                    node =
                        WmsLayerNode(
                            directContent =
                                directLayerContent(
                                    xml = xml,
                                    contentStartIndex = open.contentStartIndex,
                                    contentEndIndex = tag.range.first,
                                    children = open.children,
                                ),
                            children = open.children.map(ParsedWmsLayerNode::node),
                        ),
                    startIndex = open.startIndex,
                    endIndexExclusive = tag.range.last + 1,
                )
            stack.lastOrNull()?.children?.add(parsed) ?: roots.add(parsed)
        } else if (!tag.value.endsWith("/>")) {
            stack +=
                OpenWmsLayer(
                    startIndex = tag.range.first,
                    contentStartIndex = tag.range.last + 1,
                )
        }
    }
    return roots.map(ParsedWmsLayerNode::node)
}

private fun directLayerContent(
    xml: String,
    contentStartIndex: Int,
    contentEndIndex: Int,
    children: List<ParsedWmsLayerNode>,
): String =
    buildString {
        var nextIndex = contentStartIndex
        children.forEach { child ->
            append(xml, nextIndex, child.startIndex)
            nextIndex = child.endIndexExclusive
        }
        append(xml, nextIndex, contentEndIndex)
    }

private fun crsValues(content: String): Set<String> =
    (tagTexts(content, "SRS") + tagTexts(content, "CRS"))
        .flatMap { it.split(Regex("""\s+""")) }
        .filter { it.isNotBlank() }
        .toSet()

private fun boundingBoxes(
    content: String,
    version: WmsVersion,
): Map<String, BoundingBox> =
    Regex("""<BoundingBox\b[^>]*>""")
        .findAll(content)
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
