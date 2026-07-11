package tilo.compose.core.layers.raster

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import tilo.compose.core.geometry.Point
import tilo.compose.core.map.Map
import tilo.compose.core.map.Viewport
import tilo.compose.core.projection.Epsg3857Projection
import tilo.compose.core.projection.Epsg5514Projection
import tilo.compose.core.tile.TileCoordinate
import tilo.compose.core.tile.TileGrid
import tilo.compose.core.tile.TileRequest

class RasterTileLayerTest {
    @Test
    fun xyzSourceDefaultsToWebMercator() {
        val source = XYZTileSource(urlTemplate = "https://example.com/{z}/{x}/{y}.png")

        assertEquals(Epsg3857Projection.id, source.projection.id)
        assertEquals(TileGrid.WebMercator, source.grid)
    }

    @Test
    fun tileStoreTmsSchemeFlipsRowsUsingGridHeight() {
        val grid = TileGrid(originX = 0.0, originY = 1024.0, worldWidth = 1024.0, nTilesX0 = 1, nTilesY0 = 2)
        val source =
            TileStoreTileSource(
                projection = Epsg5514Projection,
                grid = grid,
                scheme = TileRowScheme.TMS,
                readTile = { byteArrayOf(1) },
            )

        val key = source.cacheKey(TileRequest(TileCoordinate(z = 2, x = 3, y = 1), grid.tileBounds(x = 3, y = 1, zoom = 2)))

        assertEquals("tile-store:EPSG:5514:2:3:6", key)
    }

    @Test
    fun rasterLayerRejectsTilesInDifferentProjection() {
        val source =
            TileStoreTileSource(
                projection = Epsg5514Projection,
                grid = TileGrid(),
                scheme = TileRowScheme.XYZ,
                readTile = { byteArrayOf(1) },
            )
        val layer = RasterTileLayer(id = "offline", source = source)
        val map =
            Map(
                center = Point(0.0, 0.0),
                zoom = 0.0,
                viewport = Viewport(width = 256, height = 256),
                projection = Epsg3857Projection,
            )

        assertFailsWith<IllegalArgumentException> {
            layer.planTiles(map)
        }
    }
}
