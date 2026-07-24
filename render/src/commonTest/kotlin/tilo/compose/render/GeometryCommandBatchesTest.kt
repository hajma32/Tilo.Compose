@file:OptIn(ExperimentalTiloRenderingApi::class)

package tilo.compose.render

import tilo.compose.core.feature.LineStyle
import tilo.compose.core.feature.PointStyle
import tilo.compose.core.feature.PolygonStyle
import tilo.compose.core.geometry.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class GeometryCommandBatchesTest {
    @Test
    fun groupsEqualEffectiveStylesAndKeepsOverridesSeparate() {
        val layerStyle = PointStyle(size = 8.0)
        val equivalentFeatureStyle = layerStyle.copy()
        val featureOverride = PointStyle(size = 14.0)
        val commands =
            listOf(
                RenderPoint("default-1", Point(0.0, 0.0), layerStyle),
                RenderPoint("override", Point(1.0, 1.0), featureOverride),
                RenderPoint("default-2", Point(2.0, 2.0), equivalentFeatureStyle),
            )

        val batches = GeometryCommandBatches.build(commands)

        assertEquals(2, batches.points.size)
        assertEquals(listOf("default-1", "default-2"), batches.points.getValue(layerStyle).map(RenderCommand::id))
        assertEquals(listOf("override"), batches.points.getValue(featureOverride).map(RenderCommand::id))
        assertSame(layerStyle, batches.points.keys.first())
    }

    @Test
    fun neverCombinesDifferentGeometryTypes() {
        val point = Point(0.0, 0.0)
        val batches =
            GeometryCommandBatches.build(
                listOf(
                    RenderPoint("point", point, PointStyle()),
                    RenderLineString("line", listOf(point, Point(1.0, 1.0)), LineStyle()),
                    RenderPolygon(
                        "polygon",
                        listOf(listOf(point, Point(1.0, 0.0), Point(1.0, 1.0), point)),
                        PolygonStyle(),
                    ),
                ),
            )

        assertEquals(1, batches.points.size)
        assertEquals(1, batches.lines.size)
        assertEquals(1, batches.polygons.size)
    }
}
