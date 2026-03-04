package tilo.compose.core.transform

import kotlin.test.Test
import kotlin.test.assertTrue
import tilo.compose.core.geometry.Point
import tilo.compose.core.map.Viewport
import kotlin.math.abs

private fun approxEqual(a: Double, b: Double, eps: Double = 1e-9) = abs(a - b) <= eps

class IdentityCoordinateSystemTests {

    @Test
    fun worldToScreenAndBackAreInverses() {
        val viewport = Viewport(800, 600)
        val centers = listOf(Point(0.0, 0.0), Point(100.5, -42.25))
        val zooms = listOf(0.0, 1.0, 3.5)
        val worlds = listOf(Point(0.0, 0.0), Point(10.0, 5.0), Point(-123.45, 67.89))

        for (center in centers) {
            for (zoom in zooms) {
                for (world in worlds) {
                    val screen = IdentityCoordinateSystem.worldToScreen(world, center, zoom, viewport)
                    val world2 = IdentityCoordinateSystem.screenToWorld(screen, center, zoom, viewport)
                    assertTrue(approxEqual(world.x, world2.x), "x differs: $world vs $world2 at zoom $zoom center $center")
                    assertTrue(approxEqual(world.y, world2.y), "y differs: $world vs $world2 at zoom $zoom center $center")
                }
            }
        }
    }

    @Test
    fun scalingIsAppliedByZoom() {
        val viewport = Viewport(200, 200)
        val center = Point(0.0, 0.0)
        val worldA = Point(1.0, 0.0)

        val s0 = IdentityCoordinateSystem.worldToScreen(worldA, center, 0.0, viewport)
        val s1 = IdentityCoordinateSystem.worldToScreen(worldA, center, 1.0, viewport)
        // scale at zoom 1 should be 2x distance from center compared to zoom 0
        val dx0 = s0.x - viewport.width / 2.0
        val dx1 = s1.x - viewport.width / 2.0
        assertTrue(approxEqual(dx1, dx0 * 2.0), "Expected scale factor ~2.0, got ${dx1/dx0}")
    }

    @Test
    fun centerTranslationAffectsScreenCoordinates() {
        val viewport = Viewport(100, 100)
        val world = Point(10.0, 20.0)
        val centerA = Point(0.0, 0.0)
        val centerB = Point(5.0, 0.0)
        val sA = IdentityCoordinateSystem.worldToScreen(world, centerA, 0.0, viewport)
        val sB = IdentityCoordinateSystem.worldToScreen(world, centerB, 0.0, viewport)
        // moving the center to the right should move the screen position to the left by same amount
        val dx = sB.x - sA.x
        assertTrue(approxEqual(dx, -5.0), "Expected dx -5.0, got $dx")
    }
}

