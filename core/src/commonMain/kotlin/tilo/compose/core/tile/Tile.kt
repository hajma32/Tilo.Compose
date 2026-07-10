package tilo.compose.core.tile

import tilo.compose.core.geometry.Point

/** Slippy-map tile address. */
data class TileCoordinate(val z: Int, val x: Int, val y: Int)

/**
 * Axis-aligned bounding box of a tile in the layer's own coordinate space.
 * topLeft = NW corner (minX, maxY), bottomRight = SE corner (maxX, minY).
 */
data class TileBounds(val topLeft: Point, val bottomRight: Point)

/** A tile request — everything needed to fetch and position one tile. */
data class TileRequest(
    val coordinate: TileCoordinate,
    val bounds: TileBounds
)

/**
 * Tile requests split by rendering priority.
 *
 * [visible] must be loaded before rendering the current viewport. [prefetch]
 * covers the surrounding area and can be loaded asynchronously into cache.
 */
data class TileRequestPlan(
    val zoom: Int,
    val visible: List<TileRequest>,
    val prefetch: List<TileRequest>,
)

/** Fetched tile result. bytes = null when download failed. */
data class Tile(
    val coordinate: TileCoordinate,
    val bounds: TileBounds,
    val bytes: ByteArray?
)
