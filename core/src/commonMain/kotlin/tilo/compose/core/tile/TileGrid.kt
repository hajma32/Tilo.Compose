package tilo.compose.core.tile

import kotlin.math.floor
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
     * Returns the tile zoom level that places approximately [tilesAcross] tiles
     * across the shorter viewport dimension (width or height in DIP).
     */
    fun zoomForViewport(
        mapZoom: Double,
        viewport: Viewport,
        projection: Projection,
        tilesAcross: Int = 2
    ): Int {
        val shortSide = minOf(viewport.dipWidth, viewport.dipHeight)
        val normalizedWorldWidth = worldWidth / projection.worldUnitsPerMapUnit
        return (mapZoom + log2(tilesAcross.toDouble() * normalizedWorldWidth / (nTilesX0 * shortSide)))
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
     * All tiles visible within the world-coordinate rectangle minX..maxX × minY..maxY.
     * Pass values directly from Map.screenToWorld — no projection needed.
     */
    fun visibleTiles(
        minX: Double, maxX: Double,
        minY: Double, maxY: Double,
        zoom: Int
    ): List<TileRequest> {
        val nx = nTilesX(zoom)
        val ny = nTilesY(zoom)
        val span = tileSpan(zoom)

        val x0 = floor((minX - originX) / span).toInt().coerceIn(0, nx - 1)
        val x1 = floor((maxX - originX) / span).toInt().coerceIn(0, nx - 1)
        val y0 = floor((originY - maxY) / span).toInt().coerceIn(0, ny - 1)
        val y1 = floor((originY - minY) / span).toInt().coerceIn(0, ny - 1)

        return buildList {
            for (y in y0..y1) {
                for (x in x0..x1) {
                    add(TileRequest(TileCoordinate(zoom, x, y), tileBounds(x, y, zoom)))
                }
            }
        }
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
