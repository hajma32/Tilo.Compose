@file:OptIn(ExperimentalTiloApi::class)

package tilo.compose.dsl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import tilo.compose.core.layers.Attribution
import tilo.compose.core.layers.raster.RasterTileLayer
import tilo.compose.core.layers.raster.TileLayer
import tilo.compose.core.layers.raster.WMSAxisOrder
import tilo.compose.core.layers.raster.WMSCapabilities
import tilo.compose.core.layers.raster.WMSCapabilitiesLoader
import tilo.compose.core.projection.Projection

/**
 * State holder for a WMS tile layer loaded from GetCapabilities.
 */
@ExperimentalTiloApi
class WMSLayerState internal constructor() {
    private var runtime: RasterTileLayer? = null
    private var presentation: WMSLayerPresentation? = null

    internal var layer: TileLayer? by mutableStateOf(null)
        private set

    var isLoading: Boolean by mutableStateOf(false)
        internal set

    var error: Throwable? by mutableStateOf(null)
        internal set

    internal fun updatePresentation(
        id: String,
        zIndex: Int,
        visible: Boolean,
        minZoom: Double?,
        maxZoom: Double?,
        attributions: List<Attribution>,
    ) {
        val next = WMSLayerPresentation(id, zIndex, visible, minZoom, maxZoom, attributions)
        if (presentation == next) return
        presentation = next
        publishLayer()
    }

    internal fun replaceRuntime(next: RasterTileLayer?) {
        if (runtime === next) return
        val previous = runtime
        runtime = next
        publishLayer()
        previous?.close()
    }

    internal fun close() {
        replaceRuntime(null)
    }

    private fun publishLayer() {
        val activeRuntime = runtime
        val activePresentation = presentation
        layer =
            if (activeRuntime == null || activePresentation == null) {
                null
            } else {
                PresentedTileLayer(
                    runtime = activeRuntime,
                    id = activePresentation.id,
                    zIndex = activePresentation.zIndex,
                    visible = activePresentation.visible,
                    minZoom = activePresentation.minZoom,
                    maxZoom = activePresentation.maxZoom,
                    attributions = activePresentation.attributions,
                )
            }
    }
}

private data class WMSLayerPresentation(
    val id: String,
    val zIndex: Int,
    val visible: Boolean,
    val minZoom: Double?,
    val maxZoom: Double?,
    val attributions: List<Attribution>,
)

@Composable
internal fun rememberWMSLayerRuntimeState(): WMSLayerState {
    val state = remember { WMSLayerState() }
    DisposableEffect(state) {
        onDispose(state::close)
    }
    return state
}

/**
 * Loads WMS GetCapabilities and remembers the resulting tile layer state.
 *
 * Pass the returned state to `wmsTileLayer(state)` inside [TiloMap].
 * The state owns its raster runtime and closes it when it leaves composition.
 * [onError] receives both GetCapabilities and tile request failures.
 */
@Composable
@ExperimentalTiloApi
@Suppress("TooGenericExceptionCaught") // WMS failures are exposed as state; cancellation is still rethrown.
fun rememberWMSLayer(
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
    attribution: Attribution? = null,
    attributions: List<Attribution> = emptyList(),
    onError: ((Throwable) -> Unit)? = null,
): WMSLayerState {
    val state = rememberWMSLayerRuntimeState()
    val errorHandler = remember { MutableRasterErrorHandler(onError) }
    var capabilities by remember(capabilitiesUrl) { mutableStateOf<WMSCapabilities?>(null) }

    SideEffect {
        errorHandler.delegate = onError
        state.updatePresentation(
            id = id,
            zIndex = zIndex,
            visible = visible,
            minZoom = minZoom,
            maxZoom = maxZoom,
            attributions = attributions.withSingle(attribution),
        )
    }

    LaunchedEffect(capabilitiesUrl) {
        state.isLoading = true
        state.error = null
        state.replaceRuntime(null)
        try {
            capabilities = WMSCapabilitiesLoader().load(capabilitiesUrl)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            state.error = error
            capabilities = null
            errorHandler.report(error)
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
        axisOrder,
        tileSize,
        maxVisibleTiles,
        prefetchMargin,
        overviewZoomOffset,
        maxOverviewTiles,
        overviewPrefetchMargin,
    ) {
        val loadedCapabilities = capabilities
        if (loadedCapabilities == null) {
            state.replaceRuntime(null)
            return@LaunchedEffect
        }

        state.error = null
        try {
            state.replaceRuntime(
                loadedCapabilities.createTileLayer(
                    id = id,
                    layerName = layerName,
                    projection = projection,
                    styles = styles,
                    format = format ?: loadedCapabilities.formats.firstOrNull() ?: "image/png",
                    getMapVersion = getMapVersion,
                    axisOrder = axisOrder,
                    zIndex = 0,
                    visible = true,
                    minZoom = null,
                    maxZoom = null,
                    tileSize = tileSize,
                    maxVisibleTiles = maxVisibleTiles,
                    prefetchMargin = prefetchMargin,
                    overviewZoomOffset = overviewZoomOffset,
                    maxOverviewTiles = maxOverviewTiles,
                    overviewPrefetchMargin = overviewPrefetchMargin,
                    attributions = emptyList(),
                    onError = errorHandler::report,
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            state.error = error
            state.replaceRuntime(null)
            errorHandler.report(error)
        }
    }

    return state
}

/**
 * Loads several WMS sublayers as one composited GetMap tile layer.
 * Layer order is preserved in the comma-separated WMS `LAYERS` parameter.
 * The returned state owns its raster runtime and closes it when it leaves composition.
 * [onError] receives both GetCapabilities and tile request failures.
 */
@Composable
@ExperimentalTiloApi
fun rememberWMSLayer(
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
    attribution: Attribution? = null,
    attributions: List<Attribution> = emptyList(),
    onError: ((Throwable) -> Unit)? = null,
): WMSLayerState {
    require(layerNames.isNotEmpty()) { "At least one WMS layer name is required." }
    return rememberWMSLayer(
        id = id,
        capabilitiesUrl = capabilitiesUrl,
        layerName = layerNames.joinToString(","),
        projection = projection,
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
        attribution = attribution,
        attributions = attributions,
        onError = onError,
    )
}
