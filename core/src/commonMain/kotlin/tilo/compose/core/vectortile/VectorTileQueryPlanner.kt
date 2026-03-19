package tilo.compose.core.vectortile

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.tan
import tilo.compose.core.geometry.Point

data class VectorTileLoadPlan(
    val requestedZoom: Int,
    val sourceZoom: Int,
    val tiles: List<TileCoordinate>
)

class VectorTileQueryPlanner {

    fun plan(
        center: Point,
        zoom: Double,
        tileCount: Int,
        metadata: VectorTileDatasetMetadata
    ): VectorTileLoadPlan? {
        val requestedZoom = zoom.roundToInt().coerceAtLeast(0)
        val sourceZoom = resolveSourceZoom(requestedZoom, metadata) ?: return null

        val centerTileX = lonToTileX(center.x, requestedZoom)
        val centerTileY = latToTileY(center.y, requestedZoom)
        val gridSide = maxOf(1, ceil(sqrt(tileCount.coerceAtLeast(1).toDouble())).toInt())
        val radius = gridSide / 2
        val maxIndex = tileGridSize(requestedZoom) - 1

        val sourceTiles = LinkedHashSet<TileCoordinate>()
        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                val requestedX = wrapX(centerTileX + dx, requestedZoom)
                val requestedY = (centerTileY + dy).coerceIn(0, maxIndex)
                sourceTiles += mapRequestedTileToSourceTile(
                    requestedX = requestedX,
                    requestedY = requestedY,
                    requestedZoom = requestedZoom,
                    sourceZoom = sourceZoom
                )
            }
        }

        if (sourceTiles.isEmpty()) return null
        return VectorTileLoadPlan(
            requestedZoom = requestedZoom,
            sourceZoom = sourceZoom,
            tiles = sourceTiles.toList()
        )
    }

    fun resolveSourceZoom(requestedZoom: Int, metadata: VectorTileDatasetMetadata): Int? {
        return metadata.availableZoomLevels
            .asSequence()
            .filter { zoom -> metadata.minZoom == null || zoom >= metadata.minZoom }
            .filter { zoom -> metadata.maxZoom == null || zoom <= metadata.maxZoom }
            .filter { zoom -> zoom <= requestedZoom }
            .maxOrNull()
            ?: metadata.availableZoomLevels.minOrNull()
    }

    private fun mapRequestedTileToSourceTile(
        requestedX: Int,
        requestedY: Int,
        requestedZoom: Int,
        sourceZoom: Int
    ): TileCoordinate {
        if (sourceZoom == requestedZoom) {
            return TileCoordinate(z = sourceZoom, x = requestedX, y = requestedY)
        }

        return if (sourceZoom < requestedZoom) {
            val delta = requestedZoom - sourceZoom
            TileCoordinate(z = sourceZoom, x = requestedX shr delta, y = requestedY shr delta)
        } else {
            val delta = sourceZoom - requestedZoom
            TileCoordinate(z = sourceZoom, x = requestedX shl delta, y = requestedY shl delta)
        }
    }

    private fun lonToTileX(lon: Double, z: Int): Int {
        val gridSize = tileGridSize(z).toDouble()
        return floor((lon + 180.0) / 360.0 * gridSize).toInt()
    }

    private fun latToTileY(lat: Double, z: Int): Int {
        val latRad = lat.coerceIn(-85.05112878, 85.05112878) * PI / 180.0
        val gridSize = tileGridSize(z).toDouble()
        return floor((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * gridSize).toInt()
    }

    private fun wrapX(x: Int, z: Int): Int {
        val gridSize = tileGridSize(z)
        val mod = x % gridSize
        return if (mod < 0) mod + gridSize else mod
    }

    private fun tileGridSize(z: Int): Int {
        return 2.0.pow(z.toDouble()).toInt().coerceAtLeast(1)
    }
}

