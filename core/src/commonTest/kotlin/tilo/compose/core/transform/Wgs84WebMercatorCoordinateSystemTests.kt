package tilo.compose.core.transform

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import tilo.compose.core.geometry.Point
import tilo.compose.core.map.Viewport

class Wgs84WebMercatorCoordinateSystemTests {

    @Test
    fun worldToScreenAndBackRoundtrip() {
        val viewport = Viewport(width = 1200, height = 800)
        val center = Point(14.421, 50.087) // Prague lon/lat
        val world = Point(14.5, 50.1)
        val zoom = 8.0

        val screen = Wgs84WebMercatorCoordinateSystem.worldToScreen(world, center, zoom, viewport)
        val back = Wgs84WebMercatorCoordinateSystem.screenToWorld(screen, center, zoom, viewport)

        assertTrue(abs(world.x - back.x) < 1e-6)
        assertTrue(abs(world.y - back.y) < 1e-6)
    }
}

