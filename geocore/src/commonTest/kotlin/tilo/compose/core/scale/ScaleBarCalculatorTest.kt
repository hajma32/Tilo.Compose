package tilo.compose.core.scale

import tilo.compose.core.geometry.Point
import tilo.compose.core.map.MapConfig
import tilo.compose.core.map.MapState
import tilo.compose.core.map.Viewport
import tilo.compose.core.projection.Epsg3857Projection
import tilo.compose.core.projection.Epsg4326Projection
import tilo.compose.core.projection.Epsg5514Projection
import tilo.compose.core.projection.Projection
import tilo.compose.core.transform.Transformation
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
                transformationRegistry = MapConfig.Default.transformationRegistry,
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
                transformationRegistry = MapConfig.Default.transformationRegistry,
            )

        assertEquals(1000.0, distance)
    }

    @Test
    fun prefersRegisteredTransformationOverPlanarFallback() {
        val config = MapConfig.Default.withTransformation(FakeSjtskToWgs84Transformation)

        val distance =
            DistanceCalculators.Auto.distanceMeters(
                from = Point(0.0, 0.0),
                to = Point(1_000.0, 0.0),
                projection = Epsg5514Projection,
                transformationRegistry = config.transformationRegistry,
            )

        assertTrue(requireNotNull(distance) > 100_000.0)
    }

    private object FakeSjtskToWgs84Transformation : Transformation<Projection, Projection> {
        override val source: Projection = Epsg5514Projection
        override val target: Projection = Epsg4326Projection

        override fun sourceToTarget(point: Point): Point = Point(point.x / 1_000.0, point.y)

        override fun targetToSource(point: Point): Point = Point(point.x * 1_000.0, point.y)
    }
}
