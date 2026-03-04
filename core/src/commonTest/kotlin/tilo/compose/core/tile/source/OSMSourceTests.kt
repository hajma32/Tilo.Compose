package tilo.compose.core.tile.source

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import tilo.compose.core.map.Viewport

class OSMSourceTests {

    @Test
    fun returnsRequestedTileCount() {
        val source = OSMSource(centerLat = 50.087, centerLon = 14.421)
        val viewport = Viewport(width = 1080, height = 1920)

        val tiles = source.getTiles(zoomLevel = 5, viewport = viewport, tileCount = 9)

        assertEquals(9, tiles.size)
    }

    @Test
    fun buildsWmsGetMapUrl() {
        val source = OSMSource(centerLat = 48.8566, centerLon = 2.3522)
        val viewport = Viewport(width = 800, height = 600)

        val tile = source.getTiles(zoomLevel = 4, viewport = viewport, tileCount = 1).first()

        assertNotNull(tile.url)
        assertTrue(tile.url.contains("SERVICE=WMS"))
        assertTrue(tile.url.contains("REQUEST=GetMap"))
        assertTrue(tile.url.contains("BBOX="))
    }
}
