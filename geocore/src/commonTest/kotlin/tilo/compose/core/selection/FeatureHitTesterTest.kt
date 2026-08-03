package tilo.compose.core.selection

import tilo.compose.core.feature.Feature
import tilo.compose.core.feature.PointIconStyle
import tilo.compose.core.feature.PointStyle
import tilo.compose.core.geometry.LineString
import tilo.compose.core.geometry.Point
import tilo.compose.core.geometry.Polygon
import tilo.compose.core.map.ScreenPoint
import kotlin.test.Test
import kotlin.test.assertEquals

class FeatureHitTesterTest {
    @Test
    fun screenPointMathPreservesLineAndPolygonHitTesting() {
        val line = Feature(key = "line", geometry = LineString(listOf(Point(0.0, 0.0), Point(100.0, 0.0))))
        val polygon =
            Feature(
                key = "polygon",
                geometry =
                    Polygon(
                        rings =
                            listOf(
                                closedRing(0.0, 0.0, 100.0, 100.0),
                                closedRing(30.0, 30.0, 70.0, 70.0),
                            ),
                    ),
            )
        val lineLayer = FeatureHitTestLayer("line", listOf(FeatureHitTestFeature(line)))
        val polygonLayer = FeatureHitTestLayer("polygon", listOf(FeatureHitTestFeature(polygon)))
        val tester = FeatureHitTester(toleranceDip = 1.0)
        val transform: (Point) -> ScreenPoint = { ScreenPoint(it.x, it.y) }

        val lineHits = tester.hitTest(listOf(lineLayer), ScreenPoint(50.0, 0.5), Point(50.0, 0.5), transform)
        val polygonHits = tester.hitTest(listOf(polygonLayer), ScreenPoint(15.0, 50.0), Point(15.0, 50.0), transform)
        val holeHits = tester.hitTest(listOf(polygonLayer), ScreenPoint(50.0, 50.0), Point(50.0, 50.0), transform)

        assertEquals(listOf("line"), lineHits.map { it.feature.key })
        assertEquals(listOf("polygon"), polygonHits.map { it.feature.key })
        assertEquals(emptyList(), holeHits.map { it.feature.key })
    }

    @Test
    fun pointIconSizeExpandsHitArea() {
        val feature =
            Feature(
                key = "large-icon",
                geometry = Point(0.0, 0.0),
                style = PointStyle(size = 1.0, stroke = null, icon = PointIconStyle("stop", size = 100.0)),
            )

        val selections =
            FeatureHitTester().hitTest(
                layers =
                    listOf(
                        FeatureHitTestLayer(
                            id = "stops",
                            features = listOf(FeatureHitTestFeature(feature)),
                        ),
                    ),
                screenPoint = ScreenPoint(60.0, 0.0),
                worldPoint = Point(60.0, 0.0),
                worldToScreen = { ScreenPoint(it.x, it.y) },
            )

        assertEquals(listOf("large-icon"), selections.map { it.feature.key })
    }

    @Test
    fun returnsAllMatchingFeaturesInInputOrder() {
        val layers =
            listOf(
                FeatureHitTestLayer(
                    id = "upper",
                    features =
                        listOf(
                            FeatureHitTestFeature(
                                feature =
                                    Feature(
                                        key = "upper-point",
                                        geometry = Point(10.0, 10.0),
                                    ),
                            ),
                        ),
                ),
                FeatureHitTestLayer(
                    id = "lower",
                    features =
                        listOf(
                            FeatureHitTestFeature(
                                feature =
                                    Feature(
                                        key = "lower-point",
                                        geometry = Point(10.0, 10.0),
                                    ),
                            ),
                        ),
                ),
            )

        val selections =
            FeatureHitTester().hitTest(
                layers = layers,
                screenPoint = ScreenPoint(10.0, 10.0),
                worldPoint = Point(10.0, 10.0),
                worldToScreen = { ScreenPoint(it.x, it.y) },
            )

        assertEquals(listOf("upper-point", "lower-point"), selections.map { it.feature.key })
    }

    private fun closedRing(
        minX: Double,
        minY: Double,
        maxX: Double,
        maxY: Double,
    ): List<Point> =
        listOf(
            Point(minX, minY),
            Point(maxX, minY),
            Point(maxX, maxY),
            Point(minX, maxY),
            Point(minX, minY),
        )
}
