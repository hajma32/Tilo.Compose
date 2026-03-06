package tilo.compose.core.transform

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import tilo.compose.core.geometry.Point
import tilo.compose.core.map.Viewport
import tilo.compose.core.projection.IdentityProjection

private fun approxEqual(a: Double, b: Double, eps: Double = 1e-9) = abs(a - b) <= eps

class IdentityProjectionTests {

    @Test
    fun exposesStableId() {
        assertEquals("IDENTITY", IdentityProjection.id)
    }

    @Test
    fun viewportCartesianWorldToScreenAndBackAreInverses() {
        val viewport = Viewport(800, 600)
        val center = Point(100.5, -42.25)
        val world = Point(-123.45, 67.89)
        val zoom = 3.5

        val screen = viewport.worldToScreen(world, center, zoom)
        val world2 = viewport.screenToWorld(screen, center, zoom)

        assertTrue(approxEqual(world.x, world2.x), "x differs: $world vs $world2")
        assertTrue(approxEqual(world.y, world2.y), "y differs: $world vs $world2")
    }
}
