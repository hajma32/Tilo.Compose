package tilo.compose.dsl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import tilo.compose.core.layers.Attribution
import tilo.compose.core.layers.raster.WMSCapabilities
import tilo.compose.core.layers.raster.WMSCapabilitiesLoader
import tilo.compose.core.layers.raster.WMSTileLayer
import tilo.compose.core.projection.Projection

/**
 * State holder for a WMS tile layer loaded from GetCapabilities.
 */
class WMSLayerState internal constructor() {
    internal var layer: WMSTileLayer? by mutableStateOf(null)
        internal set

    var isLoading: Boolean by mutableStateOf(false)
        internal set

    var error: Throwable? by mutableStateOf(null)
        internal set
}

/**
 * Loads WMS GetCapabilities and remembers the resulting tile layer state.
 *
 * Pass the returned state to `wmsTileLayer(state)` inside [TiloMap].
 */
@Composable
fun rememberWMSLayer(
    id: String,
    capabilitiesUrl: String,
    layerName: String,
    projection: Projection,
    styles: String = "",
    format: String? = null,
    getMapVersion: String = "1.1.1",
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
    attribution: Attribution? = null,
    attributions: List<Attribution> = emptyList(),
): WMSLayerState {
    val state = remember { WMSLayerState() }
    var capabilities by remember(capabilitiesUrl) { mutableStateOf<WMSCapabilities?>(null) }

    LaunchedEffect(capabilitiesUrl) {
        state.isLoading = true
        state.error = null
        state.layer = null
        try {
            capabilities = WMSCapabilitiesLoader().load(capabilitiesUrl)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            state.error = error
            capabilities = null
        } finally {
            state.isLoading = false
        }
    }

    LaunchedEffect(
        capabilities,
        id,
        layerName,
        projection,
        styles,
        format,
        getMapVersion,
        zIndex,
        visible,
        minZoom,
        maxZoom,
        tileSize,
        maxVisibleTiles,
        prefetchMargin,
        overviewZoomOffset,
        maxOverviewTiles,
        overviewPrefetchMargin,
        attribution,
        attributions,
    ) {
        val loadedCapabilities = capabilities
        if (loadedCapabilities == null) {
            state.layer = null
            return@LaunchedEffect
        }

        state.error = null
        try {
            state.layer = loadedCapabilities.createTileLayer(
                id = id,
                layerName = layerName,
                projection = projection,
                styles = styles,
                format = format ?: loadedCapabilities.formats.firstOrNull() ?: "image/png",
                getMapVersion = getMapVersion,
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
                attributions = attributions.withSingle(attribution),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            state.error = error
            state.layer = null
        }
    }

    return state
}
