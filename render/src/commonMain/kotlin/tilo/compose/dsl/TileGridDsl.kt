package tilo.compose.dsl

import tilo.compose.core.tile.TileGrid

/**
 * Standard Web Mercator tile grid used by most public XYZ tile services.
 */
fun webMercatorTileGrid(): TileGrid = TileGrid.WebMercator

/**
 * Creates a regular square tile grid in an arbitrary CRS.
 *
 * Use this for custom raster sources such as S-JTSK/Krovak MBTiles when the
 * database uses stable z/x/y addressing but does not follow Web Mercator
 * extents.
 */
fun tileGrid(
    originX: Double,
    originY: Double,
    worldWidth: Double,
    nTilesX0: Int = 1,
    nTilesY0: Int = 1,
    tileSize: Int = 256,
): TileGrid =
    TileGrid(
        originX = originX,
        originY = originY,
        worldWidth = worldWidth,
        nTilesX0 = nTilesX0,
        nTilesY0 = nTilesY0,
        tileSize = tileSize,
    )
