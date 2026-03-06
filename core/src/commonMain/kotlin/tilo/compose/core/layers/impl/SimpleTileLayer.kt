package tilo.compose.core.layers.impl

import tilo.compose.core.layers.TileLayer
import tilo.compose.core.map.Viewport
import tilo.compose.core.tile.source.WMSSource
import tilo.compose.core.tile.source.WmsTileRequest
import tilo.compose.core.tile.utils.AddressingStrategy
import tilo.compose.core.tile.utils.TilePlanner
import tilo.compose.core.tile.utils.TileRequestFactory

/**
 * Simple concrete TileLayer used as an example and for tests.
 */
class SimpleTileLayer(
    override val id: String,
    override val source: WMSSource,
    override val addressingStrategy: AddressingStrategy = AddressingStrategy.WMS,
    private val tileSizePx: Int = 256
) : TileLayer {

    override fun buildRequests(
        zoomLevel: Int,
        centerLon: Double,
        centerLat: Double,
        viewport: Viewport,
        tileCount: Int
    ): List<WmsTileRequest> {
        val coordinates = TilePlanner.computeTileCoordinates(
            zoomLevel = zoomLevel,
            centerLon = centerLon,
            centerLat = centerLat,
            tileCount = tileCount
        )

        return TileRequestFactory.buildWmsRequests(
            coordinates = coordinates,
            addressingStrategy = addressingStrategy,
            tileSizePx = tileSizePx
        )
    }
}
