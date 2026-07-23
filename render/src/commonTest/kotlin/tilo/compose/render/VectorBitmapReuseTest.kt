@file:OptIn(ExperimentalTiloRenderingApi::class)

package tilo.compose.render

import tilo.compose.core.feature.Feature
import tilo.compose.core.geometry.Point
import tilo.compose.core.layers.vector.FeatureLayer
import tilo.compose.core.layers.vector.VectorRenderStrategy
import tilo.compose.core.map.MapState
import tilo.compose.core.map.Viewport
import tilo.compose.render.backend.VectorBitmapSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VectorBitmapReuseTest {
    private val strategy =
        VectorRenderStrategy.CachedBitmap(
            paddingPx = 200,
            invalidateOnZoomDelta = 0.3,
        )

    /**
     * Verifies spatial reuse while a padded vector snapshot still covers the viewport.
     *
     * Input: a 1,400 × 1,200 snapshot, then pans of 150 and another 100 pixels.
     * Expected: the first pan remains covered; the cumulative second pan invalidates reuse.
     */
    @Test
    fun bitmapIsReusedWhilePaddedSnapshotCoversViewport() {
        val map = testMap()
        val bitmap = testBitmapLayer()

        assertTrue(bitmap.canCover(map, strategy))

        map.panBy(150.0, 0.0)
        assertTrue(bitmap.canCover(map, strategy))

        map.panBy(100.0, 0.0)
        assertFalse(bitmap.canCover(map, strategy))
    }

    /**
     * Verifies the inclusive zoom threshold used by cached vector snapshots.
     *
     * Input: a snapshot at zoom `10.0`, tested at zoom `10.3` and `10.31` with threshold `0.3`.
     * Expected: reuse at the threshold and invalidation immediately beyond it.
     */
    @Test
    fun bitmapIsInvalidatedOnlyAfterConfiguredZoomDelta() {
        val map = testMap()
        val bitmap = testBitmapLayer()

        map.zoom = 10.3
        assertTrue(bitmap.canCover(map, strategy))

        map.zoom = 10.31
        assertFalse(bitmap.canCover(map, strategy))
    }

    @Test
    fun bitmapIsInvalidatedWhenBearingChanges() {
        val map = testMap()
        val bitmap = testBitmapLayer()

        assertTrue(bitmap.canCover(map, strategy))
        map.rotateBy(0.01)
        assertFalse(bitmap.canCover(map, strategy))
    }

    /**
     * Verifies stable cache identity for equivalent DSL-style layer reconstruction.
     *
     * Input: two feature layers built from equal feature lists and the same render strategy.
     * Expected: both layers produce identical vector cache keys.
     */
    @Test
    fun equivalentFeatureListsKeepCacheIdentityAcrossRecomposition() {
        val features = listOf(Feature(key = "same", geometry = Point(1.0, 2.0)))
        val first = FeatureLayer(id = "layer", features = features, renderStrategy = strategy)
        val rebuilt = FeatureLayer(id = "layer", features = features, renderStrategy = strategy)

        assertEquals(first.cacheKey(), rebuilt.cacheKey())
    }

    private fun testMap() =
        MapState(
            center = Point(0.0, 0.0),
            zoom = 10.0,
            viewport = Viewport(width = 1_000, height = 800),
        )

    private fun testBitmapLayer() =
        VectorBitmapSnapshot(
            center = Point(0.0, 0.0),
            zoom = 10.0,
            bitmapWidth = 1_400,
            bitmapHeight = 1_200,
            displayWidth = 1_400,
            displayHeight = 1_200,
        )
}
