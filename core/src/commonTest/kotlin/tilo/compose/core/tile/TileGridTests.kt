package tilo.compose.core.tile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import tilo.compose.core.map.Viewport
import tilo.compose.core.projection.Epsg3857Projection

class TileGridTests {

    @Test
    fun webMercatorPresetIsUsedFor3857() {
        assertEquals(TileGrid.WebMercator, TileGrid.defaultFor(Epsg3857Projection))
    }

    @Test
    fun webMercatorZoomForViewportTracksMapZoomInProjectedCrs() {
        val viewport = Viewport(width = 1200, height = 800, pixelRatio = 2.0)
        val zoom = TileGrid.WebMercator.zoomForViewport(
            mapZoom = 11.5,
            viewport = viewport,
            projection = Epsg3857Projection
        )

        assertTrue(zoom in 11..12, "unexpected zoom: $zoom")
    }
}

