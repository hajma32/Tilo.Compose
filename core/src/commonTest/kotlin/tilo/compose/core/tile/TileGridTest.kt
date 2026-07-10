package tilo.compose.core.tile

import tilo.compose.core.map.Viewport
import tilo.compose.core.projection.Epsg4326Projection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TileGridTest {
    @Test
    fun zoomForViewportUsesDipSizeInsteadOfPhysicalPixels() {
        val grid = TileGrid()
        val lowDensity =
            grid.zoomForViewport(
                mapZoom = 10.0,
                viewport = Viewport(width = 360, height = 800, pixelRatio = 1.0),
                projection = Epsg4326Projection,
            )
        val highDensity =
            grid.zoomForViewport(
                mapZoom = 10.0,
                viewport = Viewport(width = 1080, height = 2400, pixelRatio = 3.0),
                projection = Epsg4326Projection,
            )

        assertEquals(lowDensity, highDensity)
    }

    @Test
    fun requestPlanLimitsVisibleTilesAndKeepsPrefetchSeparate() {
        val plan =
            TileGrid().requestPlan(
                minX = -90.0,
                maxX = 90.0,
                minY = -60.0,
                maxY = 60.0,
                preferredZoom = 8,
                maxVisibleTiles = 9,
                prefetchMargin = 1,
            )
        val visibleCoordinates = plan.visible.mapTo(mutableSetOf()) { it.coordinate }

        assertTrue(plan.visible.size <= 9)
        assertTrue(plan.prefetch.isNotEmpty())
        assertTrue(plan.prefetch.none { it.coordinate in visibleCoordinates })
    }

    @Test
    fun visibleTilesUseHalfOpenViewportBounds() {
        val grid = TileGrid()
        val plan =
            grid.requestPlan(
                minX = -180.0,
                maxX = 0.0,
                minY = -90.0,
                maxY = 90.0,
                preferredZoom = 0,
                maxVisibleTiles = 9,
                prefetchMargin = 0,
            )

        assertEquals(listOf(TileCoordinate(z = 0, x = 0, y = 0)), plan.visible.map { it.coordinate })
    }
}
