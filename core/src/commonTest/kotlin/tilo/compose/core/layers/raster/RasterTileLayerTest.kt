package tilo.compose.core.layers.raster

import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import tilo.compose.core.geometry.Point
import tilo.compose.core.map.MapState
import tilo.compose.core.map.Viewport
import tilo.compose.core.projection.Epsg3857Projection
import tilo.compose.core.projection.Epsg5514Projection
import tilo.compose.core.projection.IdentityProjection
import tilo.compose.core.tile.TileCoordinate
import tilo.compose.core.tile.TileGrid
import tilo.compose.core.tile.TileRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RasterTileLayerTest {
    /**
     * Verifies that speculative network work requires an explicit layer configuration.
     *
     * Input: a default raster layer and calls to all overview and prefetch entry points.
     * Expected: no source request is made and no overview tile is returned.
     */
    @Test
    fun prefetchAndOverviewLoadingAreDisabledByDefault() =
        runTest {
            val requests = mutableListOf<TileCoordinate>()
            val layer = RasterTileLayer(id = "network", source = trackingSource(requests))
            try {
                val map = webMercatorMap()

                val overview = layer.loadOverviewTiles(map)
                layer.prefetchTiles(map)
                layer.prefetchOverviewTiles(map)

                assertTrue(overview.isEmpty())
                assertTrue(requests.isEmpty())
            } finally {
                layer.close()
            }
        }

    /**
     * Verifies that applications can still opt in to each speculative loading feature.
     *
     * Input: a raster layer with non-zero prefetch margins and overview zoom offset.
     * Expected: overview loading and both prefetch entry points request source tiles.
     */
    @Test
    fun prefetchAndOverviewLoadingCanBeEnabledExplicitly() =
        runTest {
            val requests = mutableListOf<TileCoordinate>()
            val layer =
                RasterTileLayer(
                    id = "network",
                    source = trackingSource(requests),
                    prefetchMargin = 1,
                    overviewZoomOffset = 1,
                    overviewPrefetchMargin = 1,
                )
            try {
                val map = webMercatorMap()

                assertTrue(layer.loadOverviewTiles(map).isNotEmpty())
                assertTrue(requests.isNotEmpty())

                requests.clear()
                layer.prefetchTiles(map)
                assertTrue(requests.isNotEmpty())

                requests.clear()
                layer.prefetchOverviewTiles(map)
                assertTrue(requests.isNotEmpty())
            } finally {
                layer.close()
            }
        }

    /**
     * Verifies the full plan-to-fetch contract for two consumers of one viewport.
     *
     * Input: two concurrent `loadTiles` calls for the same Web Mercator map state.
     * Expected: the source receives exactly the planned coordinates, each only once.
     */
    @Test
    fun sameViewportLoadsExactlyItsPlannedRequestsOnce() =
        runTest {
            val requests = mutableListOf<TileCoordinate>()
            val requestsMutex = Mutex()
            val source =
                object : RasterTileSource {
                    override val projection = Epsg3857Projection
                    override val grid = TileGrid.WebMercator

                    override fun cacheKey(request: TileRequest): String = request.coordinate.toString()

                    override suspend fun readTile(request: TileRequest): ByteArray {
                        requestsMutex.withLock {
                            requests += request.coordinate
                        }
                        return byteArrayOf(1)
                    }
                }
            val layer =
                RasterTileLayer(
                    id = "network",
                    source = source,
                    prefetchMargin = 0,
                )
            val map =
                MapState(
                    center = Point(0.0, 0.0),
                    zoom = 1.0,
                    viewport = Viewport(width = 256, height = 256),
                    projection = Epsg3857Projection,
                )
            val planned = layer.planTiles(map).map { it.coordinate }

            val first = async { layer.loadTiles(map) }
            val second = async { layer.loadTiles(map) }
            first.await()
            second.await()

            assertEquals(planned.toSet(), requests.toSet())
            assertEquals(planned.size, requests.size)
        }

    /**
     * Verifies the default projection and grid selected by an XYZ source.
     *
     * Input: an XYZ source created with only a URL template.
     * Expected: the source uses EPSG:3857 and the standard Web Mercator tile grid.
     */
    @Test
    fun xyzSourceDefaultsToWebMercator() {
        val source = XYZTileSource(urlTemplate = "https://example.com/{z}/{x}/{y}.png")

        assertEquals(Epsg3857Projection.id, source.projection.id)
        assertEquals(TileGrid.WebMercator, source.grid)
    }

    /**
     * Verifies conversion of XYZ row coordinates to TMS row coordinates.
     *
     * Input: coordinate `(z=2, x=3, y=1)` on a grid with two rows at zoom zero.
     * Expected: the cache key contains the vertically flipped source row `6`.
     */
    @Test
    fun tileStoreTmsSchemeFlipsRowsUsingGridHeight() {
        val grid = TileGrid(originX = 0.0, originY = 1024.0, worldWidth = 1024.0, nTilesX0 = 1, nTilesY0 = 2)
        val source =
            TileStoreTileSource(
                projection = Epsg5514Projection,
                grid = grid,
                scheme = TileRowScheme.TMS,
                readTile = { byteArrayOf(1) },
            )

        val key =
            source.cacheKey(
                TileRequest(TileCoordinate(z = 2, x = 3, y = 1), grid.tileBounds(x = 3, y = 1, zoom = 2)),
            )

        assertEquals("tile-store:EPSG:5514:2:3:6", key)
    }

    /**
     * Verifies that raster tiles are never silently rendered in another projection.
     *
     * Input: an EPSG:5514 tile layer planned against an EPSG:3857 map.
     * Expected: planning fails with `IllegalArgumentException` before any tile is requested.
     */
    @Test
    fun rasterLayerRejectsTilesInDifferentProjection() {
        val source =
            TileStoreTileSource(
                projection = Epsg5514Projection,
                grid = TileGrid(),
                scheme = TileRowScheme.XYZ,
                readTile = { byteArrayOf(1) },
            )
        val layer = RasterTileLayer(id = "offline", source = source)
        val map =
            MapState(
                center = Point(0.0, 0.0),
                zoom = 0.0,
                viewport = Viewport(width = 256, height = 256),
                projection = Epsg3857Projection,
            )

        assertFailsWith<IllegalArgumentException> {
            layer.planTiles(map)
        }
    }

    @Test
    fun rotatedViewportPlansTilesCoveringAllFourCorners() {
        val grid = TileGrid(originX = -128.0, originY = 128.0, worldWidth = 256.0, nTilesX0 = 1, nTilesY0 = 1)
        val source =
            TileStoreTileSource(
                projection = IdentityProjection,
                grid = grid,
                readTile = { byteArrayOf(1) },
            )
        val layer = RasterTileLayer(id = "rotated", source = source, maxVisibleTiles = 16)
        try {
            val unrotated =
                layer.planTiles(
                    MapState(
                        center = Point(32.0, 32.0),
                        zoom = 2.0,
                        viewport = Viewport(width = 400, height = 400, pixelRatio = 2.0),
                    ),
                )
            val rotated =
                layer.planTiles(
                    MapState(
                        center = Point(32.0, 32.0),
                        zoom = 2.0,
                        bearing = 45.0,
                        viewport = Viewport(width = 400, height = 400, pixelRatio = 2.0),
                    ),
                )

            assertEquals(setOf(TileCoordinate(2, 2, 1)), unrotated.map { it.coordinate }.toSet())
            assertEquals(
                setOf(
                    TileCoordinate(2, 1, 1),
                    TileCoordinate(2, 2, 1),
                    TileCoordinate(2, 3, 1),
                    TileCoordinate(2, 1, 0),
                    TileCoordinate(2, 2, 0),
                    TileCoordinate(2, 3, 0),
                    TileCoordinate(2, 1, 2),
                    TileCoordinate(2, 2, 2),
                    TileCoordinate(2, 3, 2),
                ),
                rotated.map { it.coordinate }.toSet(),
            )
        } finally {
            layer.close()
        }
    }

    private fun trackingSource(requests: MutableList<TileCoordinate>): RasterTileSource =
        object : RasterTileSource {
            override val projection = Epsg3857Projection
            override val grid = TileGrid.WebMercator

            override fun cacheKey(request: TileRequest): String = request.coordinate.toString()

            override suspend fun readTile(request: TileRequest): ByteArray {
                requests += request.coordinate
                return byteArrayOf(1)
            }
        }

    private fun webMercatorMap(): MapState =
        MapState(
            center = Point(-15_000_000.0, 15_000_000.0),
            zoom = 2.0,
            viewport = Viewport(width = 256, height = 256),
            projection = Epsg3857Projection,
        )
}
