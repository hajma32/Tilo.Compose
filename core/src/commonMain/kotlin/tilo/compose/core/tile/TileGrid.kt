package tilo.compose.core.tile

import kotlin.math.log2
import kotlin.math.roundToInt
import tilo.compose.core.geometry.Point
import tilo.compose.core.map.Viewport
import tilo.compose.core.projection.Epsg3857Projection
import tilo.compose.core.projection.Projection

/**
 * CRS-agnostic tile grid defined in world coordinates.
 *
 * Tiles are always square in world-unit space — [tileSpan] = worldWidth / 2^zoom.
 * At zoom=0 there are [nTilesX0] tiles horizontally and [nTilesY0] tiles vertically.
 */
data class TileGrid(
    val originX: Double = -180.0,
    val originY: Double = 90.0,
    val worldWidth: Double = 360.0,
    val nTilesX0: Int = 2,
    val nTilesY0: Int = 1,
    val tileSize: Int = 256
) {
    val worldHeight: Double = worldWidth / nTilesX0 * nTilesY0

    fun nTilesX(zoom: Int): Int = nTilesX0 shl zoom
    fun nTilesY(zoom: Int): Int = nTilesY0 shl zoom

    /** Square tile span in world units at [zoom]: worldWidth / nTilesX(zoom) */
    fun tileSpan(zoom: Int): Double = worldWidth / nTilesX(zoom)

    /**
     * Returns a density-aware tile zoom where a tile is rendered near its
     * logical size in DIP, not near its physical-pixel size.
     */
    fun zoomForViewport(
        mapZoom: Double,
        viewport: Viewport,
        projection: Projection,
        targetTileSizeDip: Double = tileSize.toDouble(),
    ): Int {
        val normalizedWorldWidth = worldWidth / (nTilesX0 * projection.worldUnitsPerMapUnit)
        return (mapZoom + log2(normalizedWorldWidth / targetTileSizeDip))
            .roundToInt()
            .coerceIn(0, 22)
    }

    /**
     * World-coordinate bounding box of tile (x, y) at [zoom].
     * topLeft = NW corner, bottomRight = SE corner.
     */
    fun tileBounds(x: Int, y: Int, zoom: Int): TileBounds {
        val span = tileSpan(zoom)
        return TileBounds(
            topLeft = Point(originX + x * span, originY - y * span),
            bottomRight = Point(originX + (x + 1) * span, originY - (y + 1) * span)
        )
    }

    /**
     * All tiles visible within the world-coordinate rectangle minX..maxX  minY..maxY.
     * Pass values directly from Map.screenToWorld — no projection needed.
     */
    fun visibleTiles(
        minX: Double, maxX: Double,
        minY: Double, maxY: Double,
        zoom: Int,
    ): List<TileRequest> = tileRequests(tileRange(minX, maxX, minY, maxY, zoom), zoom)

    /**
     * Builds a prioritized request plan for the current viewport.
     *
     * If [preferredZoom] would produce too many visible tiles, the zoom is
     * lowered until the visible request count is at or below [maxVisibleTiles].
     * Prefetch requests are computed at the same zoom and exclude visible tiles.
     */
    fun requestPlan(
        minX: Double, maxX: Double,
        minY: Double, maxY: Double,
        preferredZoom: Int,
        maxVisibleTiles: Int = 9,
        prefetchMargin: Int = 1,
    ): TileRequestPlan {
        var zoom = preferredZoom
        var visibleRange = tileRange(minX, maxX, minY, maxY, zoom)
        while (visibleRange.count > maxVisibleTiles && zoom > 0) {
            zoom -= 1
            visibleRange = tileRange(minX, maxX, minY, maxY, zoom)
        }

        val visible = tileRequests(visibleRange, zoom)
        val visibleCoordinates = visible.mapTo(mutableSetOf()) { it.coordinate }
        val prefetch = if (prefetchMargin > 0) {
            tileRequests(visibleRange.expanded(prefetchMargin, nTilesX(zoom), nTilesY(zoom)), zoom)
                .filterNot { it.coordinate in visibleCoordinates }
        } else {
            emptyList()
        }

        return TileRequestPlan(zoom = zoom, visible = visible, prefetch = prefetch)
    }

    private fun tileRange(
        minX: Double, maxX: Double,
        minY: Double, maxY: Double,
        zoom: Int,
    ): TileRange {
        val nx = nTilesX(zoom)
        val ny = nTilesY(zoom)
        val span = tileSpan(zoom)
        val epsilon = span * 1e-9

        val x0 = kotlin.math.floor((minX - originX) / span).toInt().coerceIn(0, nx - 1)
        val x1 = kotlin.math.floor((maxX - originX - epsilon) / span).toInt().coerceIn(0, nx - 1)
        val y0 = kotlin.math.floor((originY - maxY) / span).toInt().coerceIn(0, ny - 1)
        val y1 = kotlin.math.floor((originY - minY - epsilon) / span).toInt().coerceIn(0, ny - 1)

        return TileRange(
            x0 = minOf(x0, x1),
            x1 = maxOf(x0, x1),
            y0 = minOf(y0, y1),
            y1 = maxOf(y0, y1),
        )
    }

    private fun tileRequests(range: TileRange, zoom: Int): List<TileRequest> =
        buildList {
            for (y in range.y0..range.y1) {
                for (x in range.x0..range.x1) {
                    add(TileRequest(TileCoordinate(zoom, x, y), tileBounds(x, y, zoom)))
                }
            }
        }

    private data class TileRange(val x0: Int, val x1: Int, val y0: Int, val y1: Int) {
        val count: Int = (x1 - x0 + 1) * (y1 - y0 + 1)

        fun expanded(margin: Int, nx: Int, ny: Int): TileRange =
            TileRange(
                x0 = (x0 - margin).coerceAtLeast(0),
                x1 = (x1 + margin).coerceAtMost(nx - 1),
                y0 = (y0 - margin).coerceAtLeast(0),
                y1 = (y1 + margin).coerceAtMost(ny - 1),
            )
    }

    companion object {
        val WebMercator = TileGrid(
            originX = -20_037_508.342789244,
            originY = 20_037_508.342789244,
            worldWidth = 40_075_016.68557849,
            nTilesX0 = 1,
            nTilesY0 = 1,
            tileSize = 256
        )

        fun defaultFor(projection: Projection): TileGrid =
            if (projection.id == Epsg3857Projection.id) WebMercator else TileGrid()
    }
}
