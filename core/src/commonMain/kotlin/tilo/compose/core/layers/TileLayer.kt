package tilo.compose.core.layers

import tilo.compose.core.map.Viewport
import tilo.compose.core.tile.source.WMSSource
import tilo.compose.core.tile.source.WmsTileRequest

/**
 * A layer that computes WMS tile requests and delegates fetching to [source].
 */
interface TileLayer : Layer {
    /**
     * The WMS source that fetches tiles for this layer.
     */
    val source: WMSSource

    /**
     * Compute requests for the current map view.
     */
    fun buildRequests(
        zoomLevel: Int,
        centerLon: Double,
        centerLat: Double,
        viewport: Viewport,
        tileCount: Int
    ): List<WmsTileRequest>
}
