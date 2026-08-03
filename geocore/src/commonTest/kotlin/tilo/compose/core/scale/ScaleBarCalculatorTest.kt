package tilo.compose.core.scale

import tilo.compose.core.geometry.Point
import tilo.compose.core.map.MapState
import tilo.compose.core.map.Viewport
import tilo.compose.core.projection.Epsg3857Projection
import tilo.compose.core.projection.Epsg4326Projection
import tilo.compose.core.projection.Epsg5514Projection
import tilo.compose.core.projection.Projection
import tilo.compose.core.projection.ReferencedProjection
import tilo.compose.core.transform.TransformationRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScaleBarCalculatorTest {
    @Test
    fun choosesNiceDistance() {
        assertEquals(500.0, ScaleBarCalculator.niceDistance(740.0))
        assertEquals(2000.0, ScaleBarCalculator.niceDistance(3100.0))
        assertEquals(5000.0, ScaleBarCalculator.niceDistance(9900.0))
    }

    @Test
    fun formatsMetersAndKilometers() {
        assertEquals("500 m", ScaleBarCalculator.formatDistance(500.0))
        assertEquals("1 km", ScaleBarCalculator.formatDistance(1000.0))
        assertEquals("5 km", ScaleBarCalculator.formatDistance(5000.0))
    }

    @Test
    fun calculatesScaleBarForWgs84() {
        val map =
            MapState(
                center = Point(14.0, 50.0),
                zoom = 10.0,
                projection = Epsg4326Projection,
                viewport = Viewport(width = 1000, height = 800, pixelRatio = 2.0),
            )

        val scaleBar = assertNotNull(ScaleBarCalculator.calculate(map, maxWidthPx = 200.0))

        assertTrue(scaleBar.distanceMeters > 0.0)
        assertTrue(scaleBar.widthPx in 0.0..200.0)
    }

    @Test
    fun calculatesScaleBarForWebMercatorWithoutRegisteredTransformation() {
        val map =
            MapState(
                center = Point(1_606_000.0, 6_453_000.0),
                zoom = 10.0,
                projection = Epsg3857Projection,
                viewport = Viewport(width = 1000, height = 800, pixelRatio = 2.0),
            )

        val scaleBar = assertNotNull(ScaleBarCalculator.calculate(map, maxWidthPx = 200.0))

        assertTrue(scaleBar.distanceMeters > 0.0)
        assertTrue(scaleBar.widthPx in 0.0..200.0)
    }

    @Test
    fun autoReturnsNullWhenProjectionCannotBeMeasured() {
        val distance =
            DistanceCalculators.Auto.distanceMeters(
                from = Point(-600_000.0, -1_100_000.0),
                to = Point(-599_000.0, -1_100_000.0),
                projection = Epsg5514Projection,
                transformationRegistry = TransformationRegistry.Default,
            )

        assertNull(distance)
    }

    @Test
    fun autoDoesNotInferKnownCrsFromIdAlone() {
        val disguisedProjection =
            object : Projection {
                override val id = Epsg4326Projection.id
                override val definition = "TEST:NOT-WGS84"
            }

        val distance =
            DistanceCalculators.Auto.distanceMeters(
                from = Point(0.0, 0.0),
                to = Point(1.0, 0.0),
                projection = disguisedProjection,
                transformationRegistry = TransformationRegistry.Default,
            )

        assertNull(distance)
    }

    @Test
    fun planarMetersCanBeSelectedExplicitly() {
        val distance =
            DistanceCalculators.PlanarMeters.distanceMeters(
                from = Point(-600_000.0, -1_100_000.0),
                to = Point(-599_000.0, -1_100_000.0),
                projection = Epsg5514Projection,
                transformationRegistry = TransformationRegistry.Default,
            )

        assertEquals(1000.0, distance)
    }

    @Test
    fun discoversTransformationAttachedToProjection() {
        val localProjection =
            ReferencedProjection(
                id = "TEST:LOCAL",
                reference = Epsg4326Projection,
                toReference = { point -> Point(point.x / 1_000.0, point.y) },
                fromReference = { point -> Point(point.x * 1_000.0, point.y) },
            )

        val distance =
            DistanceCalculators.Auto.distanceMeters(
                from = Point(0.0, 0.0),
                to = Point(1_000.0, 0.0),
                projection = localProjection,
                transformationRegistry = TransformationRegistry.Default,
            )

        assertTrue(requireNotNull(distance) > 100_000.0)
    }
}
