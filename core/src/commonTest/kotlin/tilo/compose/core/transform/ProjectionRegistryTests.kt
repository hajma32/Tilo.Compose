package tilo.compose.core.transform

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import tilo.compose.core.geometry.Point
import tilo.compose.core.projection.Epsg3857Projection
import tilo.compose.core.projection.Epsg4326Projection
import tilo.compose.core.projection.Epsg5514Projection

private fun approx(a: Double, b: Double, eps: Double = 1e-6) = abs(a - b) <= eps

class ProjectionRegistryTests {

    @Test
    fun explicitProjectionsExposeStableIds() {
        assertEquals("EPSG:4326", Epsg4326Projection.id)
        assertEquals("EPSG:3857", Epsg3857Projection.id)
        assertEquals("EPSG:5514", Epsg5514Projection.id)
    }

    @Test
    fun registryResolvesIdentityTransformForSameProjection() {
        val point = Point(14.42, 50.08)
        val transformed = TransformationRegistry.Default
            .resolve(Epsg4326Projection, Epsg4326Projection)
            .sourceToTarget(point)

        assertEquals(point, transformed)
    }

    @Test
    fun registryResolves4326To3857Transform() {
        val transformation = TransformationRegistry.Default.find(Epsg4326Projection, Epsg3857Projection)
        assertNotNull(transformation)
    }

    @Test
    fun webMercatorRoundTripReturnsOriginalLonLat() {
        val source = Point(16.6068, 49.1951)
        val mercator = Wgs84ToWebMercatorTransformation.sourceToTarget(source)
        val roundTrip = WebMercatorToWgs84Transformation.sourceToTarget(mercator)

        assertTrue(approx(source.x, roundTrip.x), "lon differs: $source vs $roundTrip")
        assertTrue(approx(source.y, roundTrip.y), "lat differs: $source vs $roundTrip")
    }
}
