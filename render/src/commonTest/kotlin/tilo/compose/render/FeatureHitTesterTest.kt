@file:OptIn(ExperimentalTiloRenderingApi::class)

package tilo.compose.render

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import tilo.compose.core.feature.Feature
import tilo.compose.core.feature.FeatureLayerStyle
import tilo.compose.core.feature.FeatureLayerStyleZoomRule
import tilo.compose.core.feature.PointStyle
import tilo.compose.core.geometry.Point
import tilo.compose.core.geometry.Polygon
import tilo.compose.core.layers.vector.FeatureLayer
import tilo.compose.core.map.MapConfig
import tilo.compose.core.map.MapState
import tilo.compose.core.map.ScreenPoint
import tilo.compose.core.map.Viewport
import tilo.compose.core.projection.Epsg3857Projection
import tilo.compose.core.projection.Epsg4326Projection
import tilo.compose.core.projection.Projection
import tilo.compose.core.transform.Transformation
import tilo.compose.core.transform.TransformationProvider
import tilo.compose.core.transform.TransformationRegistry
import tilo.compose.core.transform.WebMercatorToWgs84Transformation
import tilo.compose.core.transform.Wgs84ToWebMercatorTransformation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeatureHitTesterTest {
    @Test
    fun selectsPointFromLayerProjection() {
        val pragueWgs84 = Point(14.4378, 50.0755)
        val pragueMercator = Wgs84ToWebMercatorTransformation.sourceToTarget(pragueWgs84)
        val map = mercatorMap(center = pragueMercator)
        val layer =
            FeatureLayer(
                id = "places",
                projection = Epsg4326Projection,
                features =
                    listOf(
                        Feature(
                            key = "prague",
                            geometry = pragueWgs84,
                        ),
                    ),
            )

        val selections =
            FeatureHitTester().hitTest(
                map = map,
                layers = listOf(layer),
                screenPoint = map.worldToScreen(pragueMercator),
            )

        assertEquals("places", selections.firstOrNull()?.layerId)
        assertEquals("prague", selections.firstOrNull()?.feature?.key)
    }

    @Test
    fun returnsAllFeaturesHitByTapInTopMostOrder() {
        val pragueWgs84 = Point(14.4378, 50.0755)
        val pragueMercator = Wgs84ToWebMercatorTransformation.sourceToTarget(pragueWgs84)
        val map = mercatorMap(center = pragueMercator)
        val lowerLayer =
            FeatureLayer(
                id = "lower",
                zIndex = 1,
                projection = Epsg4326Projection,
                features =
                    listOf(
                        Feature(
                            key = "lower-prague",
                            geometry = pragueWgs84,
                        ),
                    ),
            )
        val upperLayer =
            FeatureLayer(
                id = "upper",
                zIndex = 2,
                projection = Epsg4326Projection,
                features =
                    listOf(
                        Feature(
                            key = "upper-prague",
                            geometry = pragueWgs84,
                        ),
                    ),
            )

        val selections =
            FeatureHitTester().hitTest(
                map = map,
                layers = listOf(lowerLayer, upperLayer),
                screenPoint = map.worldToScreen(pragueMercator),
            )

        assertEquals(listOf("upper-prague", "lower-prague"), selections.map { it.feature.key })
    }

    @Test
    fun returnsEmptyListWhenTapMissesFeatures() {
        val pragueWgs84 = Point(14.4378, 50.0755)
        val pragueMercator = Wgs84ToWebMercatorTransformation.sourceToTarget(pragueWgs84)
        val map = mercatorMap(center = pragueMercator)
        val layer =
            FeatureLayer(
                id = "places",
                projection = Epsg4326Projection,
                features =
                    listOf(
                        Feature(
                            key = "prague",
                            geometry = pragueWgs84,
                        ),
                    ),
            )

        val selections =
            FeatureHitTester().hitTest(
                map = map,
                layers = listOf(layer),
                screenPoint = ScreenPoint(20.0, 20.0),
            )

        assertTrue(selections.isEmpty())
    }

    @Test
    fun hitTestingUsesStyleResolvedForCurrentZoom() {
        val map = testMap(center = Point(0.0, 0.0), zoom = 13.0, width = 200, height = 200)
        val layer =
            FeatureLayer(
                id = "places",
                features = listOf(Feature(key = "place", geometry = Point(0.0, 0.0))),
                style =
                    FeatureLayerStyle(
                        point = PointStyle(size = 10.0, stroke = null),
                        zoomRules =
                            listOf(
                                FeatureLayerStyleZoomRule(
                                    minZoom = 14.0,
                                    point = PointStyle(size = 200.0, stroke = null),
                                ),
                            ),
                    ),
            )
        val tap = ScreenPoint(180.0, 100.0)

        assertTrue(FeatureHitTester().hitTest(map, listOf(layer), tap).isEmpty())

        map.zoom = 14.0
        val selectedKey =
            FeatureHitTester()
                .hitTest(map, listOf(layer), tap)
                .single()
                .feature.key
        assertEquals("place", selectedKey)
    }

    @Test
    fun hitTestingUsesRotatedWorldToScreenTransform() {
        val map = MapState(bearing = 90.0, viewport = Viewport(width = 100, height = 100))
        val layer =
            FeatureLayer(
                id = "points",
                features = listOf(Feature(key = "east", geometry = Point(20.0, 0.0))),
            )

        val selections = FeatureHitTester().hitTest(map, listOf(layer), ScreenPoint(50.0, 30.0))

        assertEquals(listOf("east"), selections.map { it.feature.key })
    }

    @Test
    fun repeatedHitTestingReusesProjectedGeometry() {
        val sourceProjection = TestProjection("source")
        val mapProjection = TestProjection("map")
        var transformedPoints = 0
        val transformation =
            object : Transformation<Projection, Projection> {
                override val source = sourceProjection
                override val target = mapProjection

                override fun sourceToTarget(point: Point): Point {
                    transformedPoints += 1
                    return Point(point.x + 10.0, point.y)
                }

                override fun targetToSource(point: Point): Point = Point(point.x - 10.0, point.y)
            }
        val map =
            MapState(
                center = Point(10.0, 0.0),
                projection = mapProjection,
                transformationRegistry =
                    TransformationRegistry(listOf(TransformationProvider { _, _ -> transformation })),
                viewport = Viewport(width = 100, height = 100),
            )
        val layer =
            FeatureLayer(
                id = "points",
                projection = sourceProjection,
                features = listOf(Feature(key = "point", geometry = Point(0.0, 0.0))),
            )
        val tester = FeatureHitTester()
        val tap = map.worldToScreen(Point(10.0, 0.0))

        val first = tester.hitTest(map, listOf(layer), tap)
        val transformationsAfterFirstHit = transformedPoints
        val second = tester.hitTest(map, listOf(layer), tap)

        assertEquals(listOf("point"), first.map { it.feature.key })
        assertEquals(first, second)
        assertTrue(transformationsAfterFirstHit > 0)
        assertEquals(transformationsAfterFirstHit, transformedPoints)
    }

    @Test
    fun hitTestingReusesTheProjectionSnapshotBuiltByRenderer() =
        runTest {
            val sourceProjection = TestProjection("source")
            val mapProjection = TestProjection("map")
            var transformedPoints = 0
            val transformation =
                object : Transformation<Projection, Projection> {
                    override val source = sourceProjection
                    override val target = mapProjection

                    override fun sourceToTarget(point: Point): Point {
                        transformedPoints += 1
                        return Point(point.x + 10.0, point.y)
                    }

                    override fun targetToSource(point: Point): Point = Point(point.x - 10.0, point.y)
                }
            val map =
                MapState(
                    center = Point(10.0, 0.0),
                    projection = mapProjection,
                    transformationRegistry =
                        TransformationRegistry(listOf(TransformationProvider { _, _ -> transformation })),
                    viewport = Viewport(width = 100, height = 100),
                )
            val layer =
                FeatureLayer(
                    id = "points",
                    projection = sourceProjection,
                    features = listOf(Feature(key = "point", geometry = Point(0.0, 0.0))),
                )
            val frame =
                VectorRenderPipeline(StandardTestDispatcher(testScheduler)).buildFrame(
                    vectorLayers = listOf(layer),
                    map = map,
                    density = Density(1f),
                    layoutDirection = LayoutDirection.Ltr,
                )
            val transformationsAfterRender = transformedPoints
            val snapshots = FeatureProjectionSnapshotStore().also { it.publish(frame.projectionSnapshotsByLayer) }

            val selections =
                FeatureHitTester(snapshots).hitTest(
                    map = map,
                    layers = listOf(layer),
                    screenPoint = map.worldToScreen(Point(10.0, 0.0)),
                )

            assertTrue(transformationsAfterRender > 0)
            assertEquals(transformationsAfterRender, transformedPoints)
            assertEquals(listOf("point"), selections.map { it.feature.key })
        }

    @Test
    fun reprojectedHitTestingReversesTheTargetIndexRenderOrder() {
        val sourceProjection = TestProjection("source")
        val mapProjection = TestProjection("map")
        val identity =
            object : Transformation<Projection, Projection> {
                override val source = sourceProjection
                override val target = mapProjection

                override fun sourceToTarget(point: Point): Point = point

                override fun targetToSource(point: Point): Point = point
            }
        val map =
            MapState(
                projection = mapProjection,
                transformationRegistry =
                    TransformationRegistry(listOf(TransformationProvider { _, _ -> identity })),
                viewport = Viewport(width = 100, height = 100),
            )
        val right = Feature(key = "right", geometry = rectangle(minX = -10.0, maxX = 20.0))
        val left = Feature(key = "left", geometry = rectangle(minX = -20.0, maxX = 10.0))
        val layer =
            FeatureLayer(
                id = "polygons",
                projection = sourceProjection,
                // Target-space RBush orders these left, right; rendering therefore puts right on top.
                features = listOf(right, left),
            )

        val selections =
            FeatureHitTester().hitTest(
                map = map,
                layers = listOf(layer),
                screenPoint = map.worldToScreen(Point(0.0, 0.0)),
            )

        assertEquals(listOf("right", "left"), selections.map { it.feature.key })
    }

    private fun rectangle(
        minX: Double,
        maxX: Double,
    ): Polygon =
        Polygon(
            rings =
                listOf(
                    listOf(
                        Point(minX, -10.0),
                        Point(maxX, -10.0),
                        Point(maxX, 10.0),
                        Point(minX, 10.0),
                        Point(minX, -10.0),
                    ),
                ),
        )

    private fun mercatorMap(center: Point): MapState =
        MapState(
            center = center,
            zoom = 11.5,
            projection = Epsg3857Projection,
            config = MapConfig(minZoom = 0.0, maxZoom = 20.0),
            transformationRegistry =
                TransformationRegistry(
                    providers =
                        listOf(
                            TransformationProvider { source, target ->
                                when (source.id to target.id) {
                                    Epsg4326Projection.id to Epsg3857Projection.id ->
                                        Wgs84ToWebMercatorTransformation
                                    Epsg3857Projection.id to Epsg4326Projection.id ->
                                        WebMercatorToWgs84Transformation
                                    else -> null
                                }
                            },
                        ),
                ),
            viewport = Viewport(width = 1080, height = 2100, pixelRatio = 2.625),
        )

    private data class TestProjection(
        override val id: String,
    ) : Projection
}
