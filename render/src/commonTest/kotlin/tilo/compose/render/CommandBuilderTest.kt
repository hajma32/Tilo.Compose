@file:OptIn(ExperimentalTiloRenderingApi::class)

package tilo.compose.render

import tilo.compose.core.feature.ColorValue
import tilo.compose.core.feature.Feature
import tilo.compose.core.feature.FeatureLayerStyle
import tilo.compose.core.feature.FeatureLayerStyleZoomRule
import tilo.compose.core.feature.FillStyle
import tilo.compose.core.feature.PointIconStyle
import tilo.compose.core.feature.PointStyle
import tilo.compose.core.geometry.LineString
import tilo.compose.core.geometry.MultiLineString
import tilo.compose.core.geometry.MultiPoint
import tilo.compose.core.geometry.MultiPolygon
import tilo.compose.core.geometry.Point
import tilo.compose.core.geometry.Polygon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CommandBuilderTest {
    @Test
    fun iconOnlyPointProducesRenderCommand() {
        val icon = PointIconStyle(id = "stop", size = 24.0)
        val feature =
            Feature(
                key = "icon",
                geometry = Point(0.0, 0.0),
                style = PointStyle(fill = null, stroke = null, icon = icon),
            )

        val command =
            assertIs<RenderPoint>(
                CommandBuilder.build(testMap(width = 100, height = 100), listOf(feature)).single(),
            )

        assertEquals(icon, command.style.icon)
    }

    /**
     * Verifies command generation for every supported geometry family.
     *
     * Input: point, multi-point, line, multi-line, polygon, and multi-polygon features.
     * Expected: stable command IDs, command types, ordering, and preserved polygon rings.
     */
    @Test
    fun allGeometryTypesProduceStableExpectedCommands() {
        val polygon = square(-4.0, -4.0, -2.0, -2.0)
        val features =
            listOf(
                Feature(key = "point", geometry = Point(0.0, 0.0)),
                Feature(key = "multipoint", geometry = MultiPoint(listOf(Point(1.0, 1.0), Point(2.0, 2.0)))),
                Feature(key = "line", geometry = LineString(listOf(Point(-2.0, 0.0), Point(2.0, 0.0)))),
                Feature(
                    key = "multiline",
                    geometry =
                        MultiLineString(
                            listOf(
                                LineString(listOf(Point(-2.0, 1.0), Point(2.0, 1.0))),
                                LineString(listOf(Point(-2.0, 2.0), Point(2.0, 2.0))),
                            ),
                        ),
                ),
                Feature(key = "polygon", geometry = polygon),
                Feature(key = "multipolygon", geometry = MultiPolygon(listOf(polygon, square(3.0, 3.0, 5.0, 5.0)))),
            )

        val commands = CommandBuilder.build(testMap(width = 100, height = 100), features)

        assertEquals(
            listOf(
                "point:point",
                "multipoint:point:0",
                "multipoint:point:1",
                "line:line",
                "multiline:line:0",
                "multiline:line:1",
                "polygon:polygon",
                "multipolygon:polygon:0",
                "multipolygon:polygon:1",
            ),
            commands.map(RenderCommand::id),
        )
        assertIs<RenderPoint>(commands[0])
        assertIs<RenderLineString>(commands[3])
        assertIs<RenderPolygon>(commands[6])
        assertEquals(polygon.rings, (commands[6] as RenderPolygon).rings)
    }

    /**
     * Verifies viewport culling together with the command builder's ten-percent padding.
     *
     * Input: features at the center, inside the padded edge, and just beyond the padded edge.
     * Expected: center and padded-edge commands remain; the outside feature is omitted.
     */
    @Test
    fun offscreenFeaturesAreCulledButPaddedEdgeFeaturesRemain() {
        val commands =
            CommandBuilder.build(
                map = testMap(width = 100, height = 100),
                features =
                    listOf(
                        Feature(key = "center", geometry = Point(0.0, 0.0)),
                        Feature(key = "padded-edge", geometry = Point(59.0, 0.0)),
                        Feature(key = "outside", geometry = Point(61.0, 0.0)),
                    ),
            )

        assertEquals(listOf("center:point", "padded-edge:point"), commands.map(RenderCommand::id))
    }

    /**
     * Verifies resolution of selected geometry and label presentation.
     *
     * Input: one selected point with a layer-level selected style and a label.
     * Expected: the point uses the selected style and its label is marked selected.
     */
    @Test
    fun selectedFeatureUsesLayerSelectionStyleAndMarksLabel() {
        val selectedStyle =
            PointStyle(
                size = 30.0,
                fill = FillStyle(ColorValue(0xFFFF0000u)),
            )
        val commands =
            CommandBuilder.build(
                map = testMap(),
                features = listOf(Feature(key = "selected", geometry = Point(0.0, 0.0), label = "Selected")),
                layerId = "places",
                selectedFeatureKeys = setOf("selected"),
                layerStyle = FeatureLayerStyle(selectedPoint = selectedStyle),
            )

        assertEquals(selectedStyle, assertIs<RenderPoint>(commands[0]).style)
        assertEquals(true, assertIs<RenderLabel>(commands[1]).selected)
    }

    /**
     * Verifies line-label placement and readable screen-space rotation.
     *
     * Input: a descending diagonal line from `(10, 10)` to `(-10, -10)` with a label.
     * Expected: the label is anchored at the midpoint, follows the line, and rotates `-45°`.
     */
    @Test
    fun lineLabelUsesReadableMidpointRotation() {
        val commands =
            CommandBuilder.build(
                map = testMap(),
                features =
                    listOf(
                        Feature(
                            key = "road",
                            geometry = LineString(listOf(Point(10.0, 10.0), Point(-10.0, -10.0))),
                            label = "Road",
                        ),
                    ),
            )

        val label = assertIs<RenderLabel>(commands.last())
        assertEquals(Point(0.0, 0.0), label.anchor)
        assertEquals(true, label.followsLine)
        assertEquals(-45.0, label.rotationDegrees, absoluteTolerance = 0.0001)
    }

    @Test
    fun zoomRuleCanChangeGeometryAndSuppressLabels() {
        val compact = PointStyle(size = 12.0)
        val detailed = PointStyle(size = 24.0)
        val layerStyle =
            FeatureLayerStyle(
                point = compact,
                zoomRules =
                    listOf(
                        FeatureLayerStyleZoomRule(
                            minZoom = 14.0,
                            point = detailed,
                            labelsVisible = false,
                        ),
                    ),
            )
        val feature = Feature(key = "place", geometry = Point(0.0, 0.0), label = "Place")

        val lowZoom = CommandBuilder.build(testMap(zoom = 13.99), listOf(feature), layerStyle = layerStyle)
        val highZoom = CommandBuilder.build(testMap(zoom = 14.0), listOf(feature), layerStyle = layerStyle)

        assertEquals(compact, assertIs<RenderPoint>(lowZoom.first()).style)
        assertIs<RenderLabel>(lowZoom.last())
        assertEquals(listOf("place:point"), highZoom.map(RenderCommand::id))
        assertEquals(detailed, assertIs<RenderPoint>(highZoom.single()).style)
    }

    private fun square(
        minX: Double,
        minY: Double,
        maxX: Double,
        maxY: Double,
    ): Polygon =
        Polygon(
            listOf(
                listOf(
                    Point(minX, minY),
                    Point(maxX, minY),
                    Point(maxX, maxY),
                    Point(minX, maxY),
                    Point(minX, minY),
                ),
            ),
        )
}
