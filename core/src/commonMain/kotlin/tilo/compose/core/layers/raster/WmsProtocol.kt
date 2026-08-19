package tilo.compose.core.layers.raster

import io.ktor.http.ContentType
import tilo.compose.core.layers.Attribution
import kotlin.jvm.JvmInline

/** WMS protocol version used for GetMap requests. */
enum class WmsVersion(
    val value: String,
) {
    V1_1_0("1.1.0"),
    V1_1_1("1.1.1"),
    V1_3_0("1.3.0"),

    ;

    internal companion object {
        fun parse(value: String): WmsVersion? = entries.firstOrNull { it.value == value }
    }
}

/** MIME type requested from a WMS GetMap endpoint. */
@JvmInline
value class WmsImageFormat(
    val mimeType: String,
) {
    init {
        require(mimeType.isNotBlank()) { "WMS image format must not be blank." }
        val contentType = runCatching { ContentType.parse(mimeType) }.getOrNull()
        require(
            contentType != null &&
                contentType.contentType != "*" &&
                contentType.contentSubtype != "*" &&
                contentType.withoutParameters().toString().lowercase() in supportedBaseMimeTypes,
        ) { "WMS image format must be PNG, JPEG, GIF, or WebP." }
    }

    companion object {
        private val supportedBaseMimeTypes =
            setOf("image/png", "image/jpeg", "image/jpg", "image/gif", "image/webp")

        val Png = WmsImageFormat("image/png")
        val Jpeg = WmsImageFormat("image/jpeg")
        val Gif = WmsImageFormat("image/gif")
        val Webp = WmsImageFormat("image/webp")

        internal fun advertisedOrNull(value: String): WmsImageFormat? =
            runCatching { WmsImageFormat(value) }.getOrNull()
    }
}

/**
 * Optional source, presentation, loading, and diagnostics settings for one WMS layer.
 *
 * `styles` is either empty to select every layer's default style or contains one
 * entry per requested layer. Blank entries select the corresponding default style.
 * The layer and style lists are serialized only inside the WMS source. A null
 * `version` uses the capabilities version, or WMS 1.1.1 for a direct layer.
 */
class WmsLayerOptions(
    styles: List<String> = emptyList(),
    val format: WmsImageFormat? = null,
    val version: WmsVersion? = null,
    val axisOrder: WmsAxisOrder? = null,
    val zIndex: Int = 0,
    val visible: Boolean = true,
    val minZoom: Double? = null,
    val maxZoom: Double? = null,
    val maxVisibleTiles: Int = 9,
    val prefetchMargin: Int = 0,
    val overviewZoomOffset: Int = 0,
    val maxOverviewTiles: Int = 4,
    val overviewPrefetchMargin: Int = 0,
    attributions: List<Attribution> = emptyList(),
    val http: RasterHttpConfig = RasterHttpConfig(),
    val fetchConfig: TileFetchConfig = TileFetchConfig(),
    val onError: ((Throwable) -> Unit)? = null,
    val opacity: Double = 1.0,
    val onDiagnostic: (suspend (RasterTileDiagnosticEvent) -> Unit)? = null,
) {
    val styles: List<String> = styles.toList()
    val attributions: List<Attribution> = attributions.toList()

    init {
        require(this.styles.none { ',' in it }) { "WMS style names must not contain commas." }
        require(opacity in 0.0..1.0) { "opacity must be between 0.0 and 1.0" }
        require(minZoom == null || maxZoom == null || minZoom <= maxZoom) {
            "minZoom must not be greater than maxZoom"
        }
        require(maxVisibleTiles > 0) { "maxVisibleTiles must be positive." }
        require(prefetchMargin >= 0) { "prefetchMargin must not be negative." }
        require(overviewZoomOffset >= 0) { "overviewZoomOffset must not be negative." }
        require(maxOverviewTiles >= 0) { "maxOverviewTiles must not be negative." }
        require(overviewPrefetchMargin >= 0) { "overviewPrefetchMargin must not be negative." }
    }

    internal fun withProtocolDefaults(
        resolvedFormat: WmsImageFormat,
        resolvedVersion: WmsVersion,
    ): WmsLayerOptions =
        WmsLayerOptions(
            styles = styles,
            format = resolvedFormat,
            version = version ?: resolvedVersion,
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
            http = http,
            fetchConfig = fetchConfig,
            onError = onError,
            opacity = opacity,
            onDiagnostic = onDiagnostic,
        )
}

internal fun validateWmsLayerSelection(
    layerNames: List<String>,
    styles: List<String>,
) {
    require(layerNames.isNotEmpty()) { "At least one WMS layer name is required." }
    require(layerNames.none(String::isBlank)) { "WMS layer names must not be blank." }
    require(layerNames.none { ',' in it }) { "WMS layer names must not contain commas." }
    require(styles.none { ',' in it }) { "WMS style names must not contain commas." }
    require(styles.isEmpty() || styles.size == layerNames.size) {
        "WMS styles must be empty or contain exactly one entry per layer name."
    }
}
