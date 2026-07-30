package tilo.compose.core.map

import tilo.compose.core.geometry.BoundingBox
import tilo.compose.core.geometry.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MapRotationTest {
    @Test
    fun bearingIsNormalizedAndRejectsNonFiniteValues() {
        val map = MapState(bearing = -30.0)

        assertEquals(330.0, map.bearing)
        map.bearing = 750.0
        assertEquals(30.0, map.bearing)
        assertFailsWith<IllegalArgumentException> { map.bearing = Double.NaN }
        assertFailsWith<IllegalArgumentException> { map.rotateBy(Double.POSITIVE_INFINITY) }
    }

    @Test
    fun cardinalWorldDirectionsRotateCounterToClockwiseBearing() {
        val map =
            MapState(
                center = Point(10.0, 20.0),
                bearing = 90.0,
                viewport = Viewport(width = 200, height = 100),
            )

        assertPointEquals(Point(100.0, 40.0), map.worldToScreen(Point(20.0, 20.0)))
        assertPointEquals(Point(90.0, 50.0), map.worldToScreen(Point(10.0, 30.0)))
    }

    @Test
    fun worldAndScreenTransformsRemainInverseAcrossBearingsZoomAndDensity() {
        val points = listOf(Point(-17.25, 48.5), Point(12.0, -3.0), Point(101.75, 220.125))

        listOf(0.0, 30.0, 90.0, 179.5, 270.0, 359.9).forEach { bearing ->
            val map =
                MapState(
                    center = Point(12.5, 48.0),
                    zoom = 3.25,
                    bearing = bearing,
                    viewport = Viewport(width = 731, height = 419, pixelRatio = 2.5),
                )

            points.forEach { point ->
                assertPointEquals(point, map.screenToWorld(map.worldToScreen(point)), tolerance = 1e-10)
            }
        }
    }

    @Test
    fun focusedRotationKeepsWorldCoordinateUnderFocus() {
        val map =
            MapState(
                center = Point(20.0, -10.0),
                zoom = 1.5,
                bearing = 350.0,
                viewport = Viewport(width = 400, height = 300, pixelRatio = 1.5),
            )
        val focus = Point(315.0, 82.0)
        val worldBefore = map.screenToWorld(focus)

        map.rotateBy(delta = 35.0, focus = focus)

        assertEquals(25.0, map.bearing)
        assertPointEquals(worldBefore, map.screenToWorld(focus), tolerance = 1e-10)
    }

    @Test
    fun panUsesRotatedScreenAxes() {
        val map = MapState(bearing = 90.0, viewport = Viewport(width = 100, height = 100))

        map.panBy(dx = 10.0, dy = 0.0)

        assertPointEquals(Point(0.0, -10.0), map.center)
    }

    @Test
    fun viewportBoundsEncloseAllFourRotatedCornersAndResolutionIsInvariant() {
        val unrotated = MapState(viewport = Viewport(width = 100, height = 100, pixelRatio = 2.0))
        val rotated = MapState(bearing = 45.0, viewport = Viewport(width = 100, height = 100, pixelRatio = 2.0))

        val bounds = rotated.viewportBounds()

        assertEquals(-35.3553390593, bounds.minX, absoluteTolerance = 1e-9)
        assertEquals(35.3553390593, bounds.maxX, absoluteTolerance = 1e-9)
        assertEquals(-35.3553390593, bounds.minY, absoluteTolerance = 1e-9)
        assertEquals(35.3553390593, bounds.maxY, absoluteTolerance = 1e-9)
        assertEquals(unrotated.resolution(), rotated.resolution(), absoluteTolerance = 1e-12)
    }

    @Test
    fun cameraBoundsConstrainRotatedViewportEnvelope() {
        val bounds = BoundingBox.fromExtents(-100.0, 100.0, -100.0, 100.0)
        val map =
            MapState(
                center = Point(100.0, 100.0),
                bearing = 45.0,
                config = MapConfig(cameraBounds = bounds),
                viewport = Viewport(width = 100, height = 100),
            )

        assertPointEquals(Point(29.2893218813, 29.2893218813), map.center, tolerance = 1e-9)
        listOf(
            Point(0.0, 0.0),
            Point(100.0, 0.0),
            Point(0.0, 100.0),
            Point(100.0, 100.0),
        ).forEach { corner ->
            val world = map.screenToWorld(corner)
            assertTrue(world.x >= bounds.minX - 1e-9 && world.x <= bounds.maxX + 1e-9)
            assertTrue(world.y >= bounds.minY - 1e-9 && world.y <= bounds.maxY + 1e-9)
        }
    }

    private fun assertPointEquals(
        expected: Point,
        actual: Point,
        tolerance: Double = 1e-12,
    ) {
        assertEquals(expected.x, actual.x, absoluteTolerance = tolerance)
        assertEquals(expected.y, actual.y, absoluteTolerance = tolerance)
    }
}
