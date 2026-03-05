package tilo.compose.core.tile.source

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import tilo.compose.core.tile.TileCoordinate
import tilo.compose.core.transform.WmsBbox

class OSMSourceTests {

    @Test
    fun returnsRequestedTileCount() = runBlocking {
        val source = OSMSource()

        val tiles = source.getTiles(
            listOf(
                WmsTileRequest(
                    coordinate = TileCoordinate(5, 17, 10),
                    bbox = WmsBbox(-10.0, -10.0, 10.0, 10.0)
                ),
                WmsTileRequest(
                    coordinate = TileCoordinate(5, 18, 10),
                    bbox = WmsBbox(10.0, -10.0, 30.0, 10.0)
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
                    bbox = WmsBbox(-1000.0, -1000.0, 1000.0, 1000.0)
                )
            )
        ).first()

        assertNotNull(tile.url)
        assertTrue(tile.url.contains("SERVICE=WMS"))
        assertTrue(tile.url.contains("REQUEST=GetMap"))
        assertTrue(tile.url.contains("BBOX="))
    }
}
