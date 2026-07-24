@file:OptIn(ExperimentalTiloRenderingApi::class)

package tilo.compose.render

import tilo.compose.core.feature.PointStyle
import tilo.compose.core.geometry.Point
import kotlin.test.Test
import kotlin.test.assertEquals

class CachedGeometryTest {
    @Test
    fun keepsEqualPointsInOneCachedPath() {
        val commands =
            List(1_025) { index ->
                RenderPoint(
                    id = "point-$index",
                    point = Point(index.toDouble(), 0.0),
                    style = PointStyle(stroke = null),
                )
            }

        val cached = CachedGeometry.build(commands, pointWorldUnitsPerPixel = 1.0)

        assertEquals(1, cached.points.size)
    }
}
