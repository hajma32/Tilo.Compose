package tilo.compose.core.tile.utils

import kotlin.math.pow
import tilo.compose.core.tile.TileCoordinate
import tilo.compose.core.tile.source.WmsTileRequest

/**
 * Builds source requests from logical tile coordinates.
 */
object TileRequestFactory {

    fun buildWmsRequests(
        coordinates: List<TileCoordinate>,
        addressingStrategy: AddressingStrategy,
        tileSizePx: Int = 256
    ): List<WmsTileRequest> {
        return coordinates.map { coordinate ->
            val sourceY = mapYToSourceY(
                y = coordinate.y,
                zoomLevel = coordinate.z,
                strategy = addressingStrategy
            )
            val bbox = WebMercatorTileMath.tileBbox(coordinate.x, sourceY, coordinate.z)
            WmsTileRequest(
                coordinate = coordinate,
                bbox = bbox,
                width = tileSizePx,
                height = tileSizePx
            )
        }
    }

    private fun mapYToSourceY(y: Int, zoomLevel: Int, strategy: AddressingStrategy): Int {
        if (strategy != AddressingStrategy.TMS) return y
        val maxIndex = (2.0.pow(zoomLevel.toDouble()).toInt() - 1).coerceAtLeast(0)
        return (maxIndex - y).coerceIn(0, maxIndex)
    }
}
