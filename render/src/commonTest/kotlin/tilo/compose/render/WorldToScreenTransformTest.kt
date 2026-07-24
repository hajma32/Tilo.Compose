package tilo.compose.render

import tilo.compose.core.geometry.Point
import kotlin.test.Test
import kotlin.test.assertEquals

class WorldToScreenTransformTest {
    @Test
    fun matchesMapTransformAcrossCameraConfigurations() {
        val maps =
            listOf(
                testMap(center = Point(0.0, 0.0), zoom = 0.0, width = 320, height = 240),
                testMap(center = Point(14.42, 50.08), zoom = 13.75, width = 1080, height = 1920),
                testMap(
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

        maps.forEach { map ->
            val transform = WorldToScreenTransform.from(map)
            points.forEach { point ->
                val expected = map.worldToScreen(point)
                assertEquals(
                    expected.x.toFloat(),
                    transform.screenX(point.x, point.y).toFloat(),
                )
                assertEquals(
                    expected.y.toFloat(),
                    transform.screenY(point.x, point.y).toFloat(),
                )
            }
        }
    }
}
