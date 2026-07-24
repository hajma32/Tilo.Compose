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
import tilo.compose.core.feature.source.FeatureSource
import tilo.compose.core.geometry.Point
import tilo.compose.core.layers.vector.VectorLayer
import tilo.compose.core.layers.vector.VectorRenderStrategy
import tilo.compose.core.map.MapState
import tilo.compose.core.selection.FeatureSelectionRef
import tilo.compose.render.backend.VectorBitmapRenderSceneLayer
import tilo.compose.render.backend.VectorBitmapSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class VectorRenderPipelineTest {
    @Test
    fun performanceLoggerReceivesStructuredLayerTiming() =
        runTest {
            val events = mutableListOf<RenderPerformanceEvent>()
            val layer = TestVectorLayer(id = "places", source = MutableSource())

            VectorRenderPipeline(StandardTestDispatcher(testScheduler))
                .buildFrame(
                    vectorLayers = listOf(layer),
                    map = testMap(),
                    density = Density(1f),
                    layoutDirection = LayoutDirection.Ltr,
                    performanceLogger = RenderPerformanceLogger(events::add),
                )

            val event = assertIs<VectorLayerPerformanceEvent>(events.single())
            assertEquals("places", event.layerId)
            assertEquals(1, event.featureCount)
            assertEquals(1, event.commandCount)
            assertEquals(1, event.vertexCount)
            assertTrue(event.totalMillis >= 0.0)
        }

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

            // Equivalent recomposition and a pan inside bitmap padding reuse the same bitmap.
            val sameKeys = mapOf(layer.id to layer.cacheKey())
            map.panBy(40.0, 0.0)
            frame =
                pipeline.buildFrame(
                    listOf(layer),
                    map,
                    Density(1f),
                    LayoutDirection.Ltr,
                    reusableBitmapsByLayer = frame.bitmapLayersByLayer.validFor(sameKeys, sameKeys),
                )
            assertEquals(1, renderCount)

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

    private suspend fun renderAfterKeyChange(
        pipeline: VectorRenderPipeline,
        layer: TestVectorLayer,
        previousFrame: VectorFrame,
        map: MapState,
        change: () -> Unit,
    ): VectorFrame {
        val previousKeys = previousFrame.cacheKeysByLayer
        change()
        val currentKeys = mapOf(layer.id to layer.cacheKey())
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
        val currentKeys = mapOf(layer.id to layer.cacheKey(selectedKeys))
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
        override val version: Long get() = versionValue

        override fun getFeatures(map: MapState): List<Feature> =
            listOf(Feature(key = "feature", geometry = Point(map.center.x, map.center.y)))
    }

    private class TestVectorLayer(
        override val id: String,
        override val source: FeatureSource,
        renderStrategy: VectorRenderStrategy = VectorRenderStrategy.Immediate,
    ) : VectorLayer {
        var strategyValue = renderStrategy
        var styleValue = FeatureLayerStyle()
        override val renderStrategy: VectorRenderStrategy get() = strategyValue
        override val style: FeatureLayerStyle get() = styleValue
    }
}
