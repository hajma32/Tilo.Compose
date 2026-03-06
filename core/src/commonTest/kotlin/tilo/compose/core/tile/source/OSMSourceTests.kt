package tilo.compose.core.tile.source

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import tilo.compose.core.geometry.BoundingBox
import tilo.compose.core.geometry.Point
import tilo.compose.core.tile.TileCoordinate

class OSMSourceTests {

    @Test
    fun returnsRequestedTileCount() = runBlocking {
        val source = OSMSource()

        val tiles = source.getTiles(
            listOf(
                WmsTileRequest(
                    coordinate = TileCoordinate(5, 17, 10),
                    bbox = bbox(-10.0, -10.0, 10.0, 10.0)
                ),
                WmsTileRequest(
                    coordinate = TileCoordinate(5, 18, 10),
                    bbox = bbox(10.0, -10.0, 30.0, 10.0)
                )
            )
        )

        assertEquals(2, tiles.size)
    }

    @Test
    fun buildsWmsGetMapUrl() = runBlocking {
        val source = OSMSource()

        val tile = source.getTiles(
            listOf(
                WmsTileRequest(
                    coordinate = TileCoordinate(4, 8, 5),
                    bbox = bbox(-1000.0, -1000.0, 1000.0, 1000.0)
                )
            )
        ).first()

        assertNotNull(tile.url)
        assertTrue(tile.url.contains("SERVICE=WMS"))
        assertTrue(tile.url.contains("REQUEST=GetMap"))
        assertTrue(tile.url.contains("BBOX="))
    }

    private fun bbox(minX: Double, minY: Double, maxX: Double, maxY: Double): BoundingBox {
        return BoundingBox(
            topLeft = Point(minX, maxY),
            topRight = Point(maxX, maxY),
            bottomLeft = Point(minX, minY),
            bottomRight = Point(maxX, minY)
        )
    }
}
