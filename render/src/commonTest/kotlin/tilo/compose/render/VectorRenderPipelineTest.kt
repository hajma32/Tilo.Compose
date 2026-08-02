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
import tilo.compose.core.feature.source.FeatureListSource
import tilo.compose.core.feature.source.FeatureSource
import tilo.compose.core.geometry.LineString
import tilo.compose.core.geometry.Point
import tilo.compose.core.layers.vector.VectorLayer
import tilo.compose.core.layers.vector.VectorRenderStrategy
import tilo.compose.core.map.MapState
import tilo.compose.core.map.Viewport
import tilo.compose.core.projection.Projection
import tilo.compose.core.selection.FeatureSelectionRef
import tilo.compose.core.transform.Transformation
import tilo.compose.core.transform.TransformationProvider
import tilo.compose.core.transform.TransformationRegistry
import tilo.compose.render.backend.VectorBitmapRenderSceneLayer
import tilo.compose.render.backend.VectorBitmapSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

class VectorRenderPipelineTest {
    /**
     * Verifies that an immediate vector layer produces commands without bitmap work.
     *
     * Input: one point feature using `VectorRenderStrategy.Immediate`.
     * Expected: one point command and zero calls to the bitmap render target.
     */
    @Test
    fun immediateLayerBuildsCommandsWithoutBitmapRendering() =
        runTest {
            var bitmapRenders = 0
            val pipeline =
                pipeline {
                    bitmapRenders += 1
                    null
                }
            val layer = TestVectorLayer(id = "places", source = MutableSource())

            val frame = pipeline.buildFrame(listOf(layer), testMap(), Density(1f), LayoutDirection.Ltr)

            assertEquals(listOf("feature:point"), frame.commandsByLayer.getValue("places").map(RenderCommand::id))
            assertEquals(0, bitmapRenders)
            assertEquals(1, frame.metrics.returnedFeatures)
            assertEquals(1, frame.metrics.visibleFeatures)
            assertEquals(1, frame.metrics.geometryCommands)
            assertEquals(0, frame.metrics.bitmapLayersReused)
            assertEquals(0, frame.metrics.bitmapLayersRebuilt)
        }

    /**
     * Verifies the complete reuse and invalidation contract for cached vector bitmaps.
     *
     * Input: equivalent recomposition, padded pan, zoom threshold, source, style, and selection changes.
     * Expected: reuse for equivalent/padded views and exactly one rebuild per real invalidation.
     */
    @Test
    fun cachedBitmapIsRebuiltOnlyForRealInvalidations() =
        runTest {
            var renderCount = 0
            val target =
                VectorBitmapRenderTarget { layer, _, map, strategy, _, _ ->
                    renderCount += 1
                    bitmapLayer(layer, map, strategy)
                }
            val pipeline = VectorRenderPipeline(StandardTestDispatcher(testScheduler), target)
            val source = MutableSource()
            val layer =
                TestVectorLayer(
                    id = "places",
                    source = source,
                    renderStrategy = VectorRenderStrategy.CachedBitmap(paddingPx = 100, invalidateOnZoomDelta = 0.3),
                )
            val map = testMap(width = 100, height = 100, zoom = 5.0)

            var frame = pipeline.buildFrame(listOf(layer), map, Density(1f), LayoutDirection.Ltr)
            assertEquals(1, renderCount)
            assertEquals(1, frame.metrics.bitmapLayersRebuilt)
            assertEquals(0, frame.metrics.bitmapLayersReused)

            // Equivalent recomposition and a pan inside bitmap padding reuse the same bitmap.
            val previousKeys = frame.cacheKeysByLayer
            map.panBy(40.0, 0.0)
            val sameKeys =
                mapOf(
                    layer.id to
                        layer.cacheKey(
                            map = map,
                            density = Density(1f),
                            layoutDirection = LayoutDirection.Ltr,
                        ),
                )
            frame =
                pipeline.buildFrame(
                    listOf(layer),
                    map,
                    Density(1f),
                    LayoutDirection.Ltr,
                    reusableBitmapsByLayer = frame.bitmapLayersByLayer.validFor(sameKeys, previousKeys),
                )
            assertEquals(1, renderCount)
            assertEquals(1, frame.metrics.bitmapLayersReused)
            assertEquals(0, frame.metrics.bitmapLayersRebuilt)

            // Crossing the zoom threshold is a viewport invalidation.
            map.zoom = 5.31
            frame =
                pipeline.buildFrame(
                    listOf(layer),
                    map,
                    Density(1f),
                    LayoutDirection.Ltr,
                    reusableBitmapsByLayer = frame.bitmapLayersByLayer,
                )
            assertEquals(2, renderCount)

            // Source version, style and selection each invalidate the layer cache key.
            frame = renderAfterKeyChange(pipeline, layer, frame, map) { source.versionValue += 1 }
            assertEquals(3, renderCount)
            layer.styleValue = FeatureLayerStyle(point = PointStyle(size = 31.0))
            frame = buildAfterFilteringInvalidCache(pipeline, layer, frame, map)
            assertEquals(4, renderCount)
            val selected = setOf(FeatureSelectionRef(layer.id, "feature"))
            buildAfterFilteringInvalidCache(pipeline, layer, frame, map, selected)
            assertEquals(5, renderCount)
        }

    @Test
    fun reusableBitmapSkipsFeatureAndGeometryWorkWhenLabelsAreDisabled() =
        runTest {
            val source = MutableSource()
            val layer =
                TestVectorLayer(
                    id = "places",
                    source = source,
                    renderStrategy = VectorRenderStrategy.CachedBitmap(paddingPx = 100),
                ).apply {
                    styleValue = FeatureLayerStyle(labelsVisible = false)
                }
            val pipeline =
                VectorRenderPipeline(
                    StandardTestDispatcher(testScheduler),
                    VectorBitmapRenderTarget { renderedLayer, _, map, strategy, _, _ ->
                        bitmapLayer(renderedLayer, map, strategy)
                    },
                )
            val map = testMap(width = 100, height = 100, zoom = 5.0)

            val initial = pipeline.buildFrame(listOf(layer), map, Density(1f), LayoutDirection.Ltr)
            map.panBy(20.0, 0.0)
            val reused =
                pipeline.buildFrame(
                    listOf(layer),
                    map,
                    Density(1f),
                    LayoutDirection.Ltr,
                    reusableBitmapsByLayer = initial.bitmapLayersByLayer,
                )

            assertEquals(1, source.queryCount)
            assertEquals(0, reused.metrics.geometryCommands)
            assertEquals(1, reused.metrics.bitmapLayersReused)
        }

    @Test
    fun cachedBitmapBuildIncludesItsBufferedViewport() =
        runTest {
            val source =
                FeatureListSource(
                    listOf(
                        Feature(key = "center", geometry = Point(0.0, 0.0), label = "Center"),
                        Feature(key = "buffer", geometry = Point(120.0, 0.0), label = "Buffer"),
                        Feature(key = "outside", geometry = Point(200.0, 0.0), label = "Outside"),
                    ),
                )
            val layer =
                TestVectorLayer(
                    id = "places",
                    source = source,
                    renderStrategy = VectorRenderStrategy.CachedBitmap(paddingPx = 100),
                )
            var renderedCommandIds = emptyList<String>()
            val pipeline =
                VectorRenderPipeline(
                    StandardTestDispatcher(testScheduler),
                    VectorBitmapRenderTarget { renderedLayer, commands, map, strategy, _, _ ->
                        renderedCommandIds = commands.map(RenderCommand::id)
                        bitmapLayer(renderedLayer, map, strategy)
                    },
                )

            val frame =
                pipeline.buildFrame(
                    listOf(layer),
                    testMap(width = 100, height = 100),
                    Density(1f),
                    LayoutDirection.Ltr,
                )

            assertEquals(listOf("center:point", "buffer:point"), renderedCommandIds)
            assertEquals(
                listOf("center:label"),
                frame.commandsByLayer.getValue("places").map(RenderCommand::id),
            )
        }

    @Test
    fun cameraDependentSourceDoesNotReuseBitmapAcrossCameraChanges() =
        runTest {
            var renderCount = 0
            val pipeline =
                VectorRenderPipeline(
                    StandardTestDispatcher(testScheduler),
                    VectorBitmapRenderTarget { renderedLayer, _, map, strategy, _, _ ->
                        renderCount += 1
                        bitmapLayer(renderedLayer, map, strategy)
                    },
                )
            val source = CameraDependentSource()
            val layer =
                TestVectorLayer(
                    id = "places",
                    source = source,
                    renderStrategy = VectorRenderStrategy.CachedBitmap(paddingPx = 100),
                )
            val map = testMap(width = 100, height = 100)

            val first = pipeline.buildFrame(listOf(layer), map, Density(1f), LayoutDirection.Ltr)
            map.panBy(20.0, 0.0)
            pipeline.buildFrame(
                listOf(layer),
                map,
                Density(1f),
                LayoutDirection.Ltr,
                reusableBitmapsByLayer = first.bitmapLayersByLayer,
            )

            assertEquals(2, source.queryCount)
            assertEquals(2, renderCount)
        }

    /**
     * Verifies layer-local cache invalidation when feature selection changes.
     *
     * Input: cached bitmaps for two layers and a new selection only in the first layer.
     * Expected: the first bitmap is removed while the second bitmap is retained by identity.
     */
    @Test
    fun selectionInvalidatesOnlyAffectedVectorLayer() {
        val first = TestVectorLayer("first", MutableSource())
        val second = TestVectorLayer("second", MutableSource())
        val previousKeys = mapOf(first.id to first.cacheKey(), second.id to second.cacheKey())
        val currentKeys =
            mapOf(
                first.id to first.cacheKey(setOf("feature")),
                second.id to second.cacheKey(),
            )
        val firstBitmap = bitmapLayer(first, testMap(), VectorRenderStrategy.CachedBitmap())
        val secondBitmap = bitmapLayer(second, testMap(), VectorRenderStrategy.CachedBitmap())

        val retained = mapOf(first.id to firstBitmap, second.id to secondBitmap).validFor(currentKeys, previousKeys)

        assertEquals(setOf("second"), retained.keys)
        assertSame(secondBitmap, retained.getValue("second"))
    }

    /**
     * Verifies every content-affecting field represented by a vector cache key.
     *
     * Input: sequential source-version, style, strategy, and selection changes on one layer.
     * Expected: each change produces a cache key different from the preceding key.
     */
    @Test
    fun cacheKeyTracksSourceStyleStrategyAndSelection() {
        val source = MutableSource()
        val layer = TestVectorLayer("layer", source)
        val base = layer.cacheKey()

        source.versionValue += 1
        val sourceChanged = layer.cacheKey()
        layer.styleValue = FeatureLayerStyle(point = PointStyle(size = 20.0))
        val styleChanged = layer.cacheKey()
        layer.strategyValue = VectorRenderStrategy.CachedBitmap()
        val strategyChanged = layer.cacheKey()
        val selectionChanged = layer.cacheKey(setOf("feature"))

        assertNotEquals(base, sourceChanged)
        assertNotEquals(sourceChanged, styleChanged)
        assertNotEquals(styleChanged, strategyChanged)
        assertNotEquals(strategyChanged, selectionChanged)
    }

    @Test
    fun cacheKeyChangesOnlyWhenZoomCrossesAStyleRuleBoundary() {
        val layer = TestVectorLayer("layer", MutableSource())
        layer.styleValue =
            FeatureLayerStyle(
                point = PointStyle(size = 10.0),
                zoomRules =
                    listOf(
                        FeatureLayerStyleZoomRule(minZoom = 14.0, point = PointStyle(size = 20.0)),
                    ),
            )

        val belowFirst = layer.cacheKey(zoom = 12.0)
        val belowSecond = layer.cacheKey(zoom = 13.99)
        val above = layer.cacheKey(zoom = 14.0)

        assertEquals(belowFirst, belowSecond)
        assertNotEquals(belowSecond, above)
    }

    @Test
    fun cacheKeyTracksSourceTargetCrsAndTransformationRuntime() {
        val sourceProjection = TestProjection("TEST:SOURCE")
        val firstTarget = TestProjection("TEST:TARGET-1")
        val secondTarget = TestProjection("TEST:TARGET-2")
        val layer = TestVectorLayer("layer", MutableSource(), projection = sourceProjection)
        val firstProvider = TransformationProvider { _, _ -> null }
        val secondProvider = TransformationProvider { _, _ -> null }
        val firstMap =
            MapState(
                projection = firstTarget,
                transformationRegistry = TransformationRegistry(listOf(firstProvider)),
            )
        val secondMap =
            MapState(
                projection = secondTarget,
                transformationRegistry = firstMap.transformationRegistry,
            )
        val replacementRuntime =
            MapState(
                projection = firstTarget,
                transformationRegistry = TransformationRegistry(listOf(secondProvider)),
            )

        assertNotEquals(layer.cacheKey(map = firstMap), layer.cacheKey(map = secondMap))
        assertNotEquals(layer.cacheKey(map = firstMap), layer.cacheKey(map = replacementRuntime))
        assertNotEquals(
            layer.cacheKey(map = firstMap),
            layer.cacheKey(
                map =
                    MapState(
                        projection = TestProjection(firstTarget.id, worldUnitsPerMapUnit = 2.0),
                        transformationRegistry = firstMap.transformationRegistry,
                    ),
            ),
        )
        assertNotEquals(
            layer.cacheKey(map = firstMap, density = Density(1f)),
            layer.cacheKey(map = firstMap, density = Density(2f)),
        )
    }

    @Test
    fun stableFeatureGeometryIsProjectedOnlyOnceAcrossCameraFrames() =
        runTest {
            val sourceProjection = TestProjection("TEST:SOURCE")
            val targetProjection = TestProjection("TEST:TARGET")
            var providerCalls = 0
            var transformedPoints = 0
            val transformation =
                object : Transformation<Projection, Projection> {
                    override val source = sourceProjection
                    override val target = targetProjection

                    override fun sourceToTarget(point: Point): Point {
                        transformedPoints += 1
                        return Point(point.x + 1.0, point.y + 1.0)
                    }

                    override fun targetToSource(point: Point): Point = Point(point.x - 1.0, point.y - 1.0)
                }
            val registry =
                TransformationRegistry(
                    listOf(
                        TransformationProvider { _, _ ->
                            providerCalls += 1
                            transformation
                        },
                    ),
                )
            val feature =
                Feature(
                    key = "road",
                    geometry = LineString(listOf(Point(0.0, 0.0), Point(1.0, 1.0), Point(2.0, 2.0))),
                )
            val source =
                object : FeatureSource {
                    override val version = 1L

                    override fun getFeatures(map: MapState): List<Feature> = listOf(feature)
                }
            val layer = TestVectorLayer("roads", source, projection = sourceProjection)
            val map =
                MapState(
                    projection = targetProjection,
                    transformationRegistry = registry,
                    viewport = Viewport(256, 256),
                )
            val pipeline = VectorRenderPipeline(StandardTestDispatcher(testScheduler))

            pipeline.buildFrame(listOf(layer), map, Density(1f), LayoutDirection.Ltr)
            map.panBy(20.0, 0.0)
            pipeline.buildFrame(listOf(layer), map, Density(1f), LayoutDirection.Ltr)

            assertEquals(1, providerCalls)
            assertEquals(3, transformedPoints)
        }

    @Test
    fun targetSpaceIndexKeepsFeaturesMissedByTransformedViewportCorners() =
        runTest {
            val sourceProjection = TestProjection("source")
            val targetProjection = TestProjection("target")
            var transformedPoints = 0
            val transformation =
                object : Transformation<Projection, Projection> {
                    override val source = sourceProjection
                    override val target = targetProjection

                    override fun sourceToTarget(point: Point): Point {
                        transformedPoints += 1
                        return Point(point.x, point.y - point.x.centerBump())
                    }

                    override fun targetToSource(point: Point): Point = Point(point.x, point.y + point.x.centerBump())
                }
            val registry =
                TransformationRegistry(listOf(TransformationProvider { _, _ -> transformation }))
            val visibleAfterProjection = Feature(key = "curved", geometry = Point(0.0, 100.0))
            val outside = Feature(key = "outside", geometry = Point(1_000.0, 1_000.0))
            val source =
                FeatureListSource(
                    features = listOf(visibleAfterProjection, outside),
                    projection = sourceProjection,
                )
            val layer = TestVectorLayer("points", source, projection = sourceProjection)
            val map =
                MapState(
                    projection = targetProjection,
                    transformationRegistry = registry,
                    viewport = Viewport(width = 100, height = 100),
                )
            val pipeline = VectorRenderPipeline(StandardTestDispatcher(testScheduler))

            val first = pipeline.buildFrame(listOf(layer), map, Density(1f), LayoutDirection.Ltr)
            val transformationsAfterFirstFrame = transformedPoints
            val second = pipeline.buildFrame(listOf(layer), map, Density(1f), LayoutDirection.Ltr)

            assertEquals(
                listOf("curved:point"),
                first.commandsByLayer.getValue("points").map(RenderCommand::id),
            )
            assertEquals(first.commandsByLayer, second.commandsByLayer)
            assertEquals(2, transformationsAfterFirstFrame)
            assertEquals(transformationsAfterFirstFrame, transformedPoints)
        }

    @Test
    fun sourceUpdateProjectsOnlyChangedGeometry() =
        runTest {
            val sourceProjection = TestProjection("source")
            val targetProjection = TestProjection("target")
            var transformedPoints = 0
            val transformation =
                object : Transformation<Projection, Projection> {
                    override val source = sourceProjection
                    override val target = targetProjection

                    override fun sourceToTarget(point: Point): Point {
                        transformedPoints += 1
                        return point
                    }

                    override fun targetToSource(point: Point): Point = point
                }
            val source = MutableFeatureSnapshotSource()
            val layer = TestVectorLayer("points", source, projection = sourceProjection)
            val map =
                MapState(
                    projection = targetProjection,
                    transformationRegistry =
                        TransformationRegistry(listOf(TransformationProvider { _, _ -> transformation })),
                    viewport = Viewport(width = 100, height = 100),
                )
            val pipeline = VectorRenderPipeline(StandardTestDispatcher(testScheduler))

            pipeline.buildFrame(listOf(layer), map, Density(1f), LayoutDirection.Ltr)
            assertEquals(2, transformedPoints)

            source.update(source.features.first().copy(label = "updated"), source.features.last())
            pipeline.buildFrame(listOf(layer), map, Density(1f), LayoutDirection.Ltr)
            assertEquals(2, transformedPoints)

            source.update(source.features.first(), source.features.last().copy(geometry = Point(2.0, 2.0)))
            pipeline.buildFrame(listOf(layer), map, Density(1f), LayoutDirection.Ltr)
            assertEquals(3, transformedPoints)
        }

    private suspend fun renderAfterKeyChange(
        pipeline: VectorRenderPipeline,
        layer: TestVectorLayer,
        previousFrame: VectorFrame,
        map: MapState,
        change: () -> Unit,
    ): VectorFrame {
        val previousKeys = previousFrame.cacheKeysByLayer
        change()
        val currentKeys =
            mapOf(
                layer.id to
                    layer.cacheKey(
                        map = map,
                        density = Density(1f),
                        layoutDirection = LayoutDirection.Ltr,
                    ),
            )
        return pipeline.buildFrame(
            listOf(layer),
            map,
            Density(1f),
            LayoutDirection.Ltr,
            reusableBitmapsByLayer = previousFrame.bitmapLayersByLayer.validFor(currentKeys, previousKeys),
        )
    }

    private suspend fun buildAfterFilteringInvalidCache(
        pipeline: VectorRenderPipeline,
        layer: TestVectorLayer,
        previousFrame: VectorFrame,
        map: MapState,
        selection: Set<FeatureSelectionRef> = emptySet(),
    ): VectorFrame {
        val selectedKeys = if (selection.isEmpty()) emptySet() else setOf("feature")
        val currentKeys =
            mapOf(
                layer.id to
                    layer.cacheKey(
                        selectedKeys,
                        map = map,
                        density = Density(1f),
                        layoutDirection = LayoutDirection.Ltr,
                    ),
            )
        return pipeline.buildFrame(
            listOf(layer),
            map,
            Density(1f),
            LayoutDirection.Ltr,
            selectedFeatures = selection,
            reusableBitmapsByLayer =
                previousFrame.bitmapLayersByLayer.validFor(
                    currentKeys,
                    previousFrame.cacheKeysByLayer,
                ),
        )
    }

    private fun pipeline(render: suspend () -> VectorBitmapRenderSceneLayer?): VectorRenderPipeline =
        VectorRenderPipeline(
            bitmapRenderer = VectorBitmapRenderTarget { _, _, _, _, _, _ -> render() },
        )

    private fun bitmapLayer(
        layer: VectorLayer,
        map: MapState,
        strategy: VectorRenderStrategy.CachedBitmap,
    ): VectorBitmapRenderSceneLayer =
        VectorBitmapRenderSceneLayer(
            id = layer.id,
            zIndex = layer.zIndex,
            bitmap = TestImageBitmap(),
            snapshot =
                VectorBitmapSnapshot(
                    center = map.center,
                    zoom = map.zoom,
                    bitmapWidth = map.viewport.width + strategy.paddingPx * 2,
                    bitmapHeight = map.viewport.height + strategy.paddingPx * 2,
                    displayWidth = map.viewport.width + strategy.paddingPx * 2,
                    displayHeight = map.viewport.height + strategy.paddingPx * 2,
                ),
        )

    private class MutableSource : FeatureSource {
        var versionValue = 0L
        var queryCount = 0
        override val supportsBufferedQueries: Boolean = true
        override val version: Long get() = versionValue

        override fun getFeatures(map: MapState): List<Feature> {
            queryCount += 1
            return listOf(Feature(key = "feature", geometry = Point(0.0, 0.0)))
        }
    }

    private class CameraDependentSource : FeatureSource {
        var queryCount = 0

        override fun getFeatures(map: MapState): List<Feature> {
            queryCount += 1
            return listOf(Feature(key = "feature", geometry = map.center))
        }
    }

    private class MutableFeatureSnapshotSource : FeatureSource {
        var features =
            listOf(
                Feature(key = "first", geometry = Point(0.0, 0.0)),
                Feature(key = "second", geometry = Point(1.0, 1.0)),
            )
            private set
        private var versionValue = 0L
        override val version: Long get() = versionValue

        override fun getFeatures(map: MapState): List<Feature> = features

        fun update(vararg updated: Feature) {
            features = updated.toList()
            versionValue += 1
        }
    }

    private class TestVectorLayer(
        override val id: String,
        override val source: FeatureSource,
        renderStrategy: VectorRenderStrategy = VectorRenderStrategy.Immediate,
        override val projection: Projection? = null,
    ) : VectorLayer {
        var strategyValue = renderStrategy
        var styleValue = FeatureLayerStyle()
        override val renderStrategy: VectorRenderStrategy get() = strategyValue
        override val style: FeatureLayerStyle get() = styleValue
    }

    private data class TestProjection(
        override val id: String,
        override val worldUnitsPerMapUnit: Double = 1.0,
    ) : Projection

    private fun Double.centerBump(): Double = if (this == 0.0) 100.0 else 0.0
}
