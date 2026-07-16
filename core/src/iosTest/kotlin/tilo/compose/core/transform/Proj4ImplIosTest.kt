package tilo.compose.core.transform

import tilo.compose.core.geometry.Point
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Proj4ImplIosTest {
    @Test
    fun transformsWgs84ToWebMercatorWithVisualAxisOrder() {
        val input = Point(x = 16.6068, y = 49.1951)

        val transformed = proj4Transform(input, "EPSG:4326", "EPSG:3857")

        val expectedX = input.x * WEB_MERCATOR_RADIUS * PI / 180.0
        val expectedY = WEB_MERCATOR_RADIUS * ln(tan(PI / 4.0 + input.y * PI / 360.0))
        assertEquals(expectedX, transformed.x, absoluteTolerance = 1e-6)
        assertEquals(expectedY, transformed.y, absoluteTolerance = 1e-6)
    }

    @Test
    fun transformsEpsg5514AndRoundTripsWithoutExternalGridFiles() {
        val input = Point(x = 16.6068, y = 49.1951)

        val projected = proj4Transform(input, "EPSG:4326", "EPSG:5514")
        val roundTripped = proj4Transform(projected, "EPSG:5514", "EPSG:4326")

        assertTrue(projected.x in -1_000_000.0..-300_000.0, "Unexpected S-JTSK X: ${projected.x}")
        assertTrue(projected.y in -1_300_000.0..-800_000.0, "Unexpected S-JTSK Y: ${projected.y}")
        // The database-only WGS 84 ↔ S-JTSK operation is a datum transformation;
        // its inverse is expected to round-trip to approximately one metre.
        assertEquals(input.x, roundTripped.x, absoluteTolerance = 1e-5)
        assertEquals(input.y, roundTripped.y, absoluteTolerance = 1e-5)
    }

    @Test
    fun leavesCoordinatesUntouchedForIdenticalCrs() {
        val input = Point(x = 1.0, y = 2.0)

        assertEquals(input, proj4Transform(input, "EPSG:4326", "EPSG:4326"))
    }

    private companion object {
        const val WEB_MERCATOR_RADIUS = 6_378_137.0
    }
}
