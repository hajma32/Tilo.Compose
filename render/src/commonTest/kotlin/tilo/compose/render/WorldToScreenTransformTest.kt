package tilo.compose.render

import tilo.compose.core.geometry.Point
import tilo.compose.core.map.MapState
import tilo.compose.core.map.Viewport
import kotlin.test.Test
import kotlin.test.assertEquals

class WorldToScreenTransformTest {
    @Test
    fun matchesMapTransformAcrossCameraConfigurations() {
        val maps =
            listOf(
                map(center = Point(0.0, 0.0), zoom = 0.0, width = 320, height = 240),
                map(center = Point(14.42, 50.08), zoom = 13.75, width = 1080, height = 1920),
                map(
                    center = Point(-742_000.0, -1_043_000.0),
                    zoom = 3.25,
                    width = 1440,
                    height = 900,
                    pixelRatio = 2.625,
                    bearing = 37.0,
                ),
            )
        val points =
            listOf(
                Point(0.0, 0.0),
                Point(14.421, 50.087),
                Point(-741_234.567, -1_042_345.678),
                Point(20_037_508.3427892, -20_037_508.3427892),
            )

        for (map in maps) {
            val transform = WorldToScreenTransform.from(map)
            for (point in points) {
                val expected = map.worldToScreen(point)
                assertEquals(expected.x.toFloat(), transform.screenX(point.x, point.y).toFloat())
                assertEquals(expected.y.toFloat(), transform.screenY(point.x, point.y).toFloat())
            }
        }
    }

    private fun map(
        center: Point,
        zoom: Double,
        width: Int,
        height: Int,
        pixelRatio: Double = 1.0,
        bearing: Double = 0.0,
    ): MapState =
        MapState(
            center = center,
            zoom = zoom,
            viewport = Viewport(width = width, height = height, pixelRatio = pixelRatio),
            bearing = bearing,
        )
}
