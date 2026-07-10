package tilo.compose.dsl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import tilo.compose.core.layers.raster.TileFetchConfig
import tilo.compose.core.layers.raster.WMSTileLayer
import tilo.compose.core.layers.raster.createWMSTileLayerFromCapabilities
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
    tileSize: Int = 256,
    maxVisibleTiles: Int = 9,
    prefetchMargin: Int = 1,
    fetchConfig: TileFetchConfig = TileFetchConfig(),
): WMSLayerState {
    val state = remember { WMSLayerState() }

    LaunchedEffect(
        id,
        capabilitiesUrl,
        layerName,
        projection,
        styles,
        format,
        getMapVersion,
        tileSize,
        maxVisibleTiles,
        prefetchMargin,
        fetchConfig,
    ) {
        state.isLoading = true
        state.error = null
        try {
            state.layer = createWMSTileLayerFromCapabilities(
                id = id,
                capabilitiesUrl = capabilitiesUrl,
                layerName = layerName,
                projection = projection,
                styles = styles,
                format = format,
                getMapVersion = getMapVersion,
                tileSize = tileSize,
                maxVisibleTiles = maxVisibleTiles,
                prefetchMargin = prefetchMargin,
                fetchConfig = fetchConfig,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            state.error = error
            state.layer = null
        } finally {
            state.isLoading = false
        }
    }

    return state
}
