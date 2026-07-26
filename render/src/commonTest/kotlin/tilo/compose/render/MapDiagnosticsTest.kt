@file:OptIn(ExperimentalTiloRenderingApi::class)

package tilo.compose.render

import tilo.compose.dsl.MapDiagnosticsState
import tilo.compose.dsl.MapFeatureMetrics
import tilo.compose.dsl.MapTileMetrics
import kotlin.test.Test
import kotlin.test.assertEquals

class MapDiagnosticsTest {
    @Test
    fun categoryUpdatesPreserveOtherPublishedMetrics() {
        val state = MapDiagnosticsState()

        state.publishTiles(MapTileMetrics(planned = 9, loaded = 8, missing = 1))
        state.publishFeatures(MapFeatureMetrics(returned = 100, visible = 12, geometryCommands = 14))
        state.publishDisplayedTiles(11)

        assertEquals(9, state.metrics.tiles.planned)
        assertEquals(11, state.metrics.tiles.displayed)
        assertEquals(12, state.metrics.features.visible)
        assertEquals(14, state.metrics.features.geometryCommands)
    }

    @Test
    fun labelCachePublishesPlacementAndCapacitySnapshot() {
        val state = MapDiagnosticsState()
        val cache = LabelBitmapCache(maxEntries = 32)
        cache.diagnosticsState = state

        cache.publishDiagnostics(candidates = 7, placed = 3)

        assertEquals(7, state.metrics.labels.candidates)
        assertEquals(3, state.metrics.labels.placed)
        assertEquals(4, state.metrics.labels.rejected)
        assertEquals(32, state.metrics.labels.maxCacheEntries)
    }
}
