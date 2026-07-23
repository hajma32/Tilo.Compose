package tilo.compose.render

import tilo.compose.core.geometry.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RasterRenderingContractTest {
    /**
     * Verifies shared-edge rounding for horizontal and vertical tile neighbours.
     *
     * Input: three adjacent world-space bounds at fractional zoom and pixel ratio `1.5`.
     * Expected: each neighbour starts exactly where the preceding rectangle ends.
     */
    @Test
    fun adjacentTilesProduceGaplessNonOverlappingPixelRectsAtFractionalZoom() {
        val map = testMap(zoom = 0.37, width = 257, height = 193, pixelRatio = 1.5)
        val left =
            testTile(
                x = 0,
                topLeft = Point(-1.25, 0.8),
                bottomRight = Point(0.15, -0.6),
            ).screenRect(map)
        val right =
            testTile(
                x = 1,
                topLeft = Point(0.15, 0.8),
                bottomRight = Point(1.55, -0.6),
            ).screenRect(map)
        val below =
            testTile(
                x = 0,
                y = 1,
                topLeft = Point(-1.25, -0.6),
                bottomRight = Point(0.15, -2.0),
            ).screenRect(map)

        assertEquals(left.x + left.width, right.x)
        assertEquals(left.y + left.height, below.y)
    }

    /**
     * Verifies defensive normalization of reversed tile bounds.
     *
     * Input: top-left and bottom-right coordinates supplied in inverted order.
     * Expected: the computed screen rectangle still has positive width and height.
     */
    @Test
    fun invertedTileBoundsStillProducePositiveRect() {
        val rect =
            testTile(
                x = 0,
                topLeft = Point(2.0, -2.0),
                bottomRight = Point(-2.0, 2.0),
            ).screenRect(testMap())

        assertEquals(true, rect.width > 0)
        assertEquals(true, rect.height > 0)
    }

    /**
     * Verifies raster composition order across placeholder, overview, fallback, and current data.
     *
     * Input: one tile from each frame tier with distinct image identities.
     * Expected: current coverage is last, while every image remains aligned with its tile.
     */
    @Test
    fun currentTilesAreLastAndOverrideFallbackCoverage() {
        val placeholder = testTile(x = 1, bytes = null)
        val overview = testTile(x = 1, z = 1, bytes = byteArrayOf(2))
        val fallback = testTile(x = 1, z = 2, bytes = byteArrayOf(3))
        val current = testTile(x = 1, z = 3, bytes = byteArrayOf(4))
        val overviewImage = TestImageBitmap(width = 2)
        val fallbackImage = TestImageBitmap(width = 3)
        val currentImage = TestImageBitmap(width = 4)

        val merged =
            mergeRasterFrames(
                placeholderFrame = RasterFrame(mapOf("tiles" to listOf(placeholder)), emptyMap()),
                overviewFrame =
                    RasterFrame(
                        mapOf("tiles" to listOf(overview)),
                        mapOf("tiles" to listOf(overviewImage)),
                    ),
                fallbackFrame =
                    RasterFrame(
                        mapOf("tiles" to listOf(fallback)),
                        mapOf("tiles" to listOf(fallbackImage)),
                    ),
                currentFrame =
                    RasterFrame(
                        mapOf("tiles" to listOf(current)),
                        mapOf("tiles" to listOf(currentImage)),
                    ),
            )

        assertEquals(listOf(placeholder, overview, fallback, current), merged.tilesByLayer.getValue("tiles"))
        val images = merged.decodedImagesByLayer.getValue("tiles")
        assertNull(images[0])
        assertSame(overviewImage, images[1])
        assertSame(fallbackImage, images[2])
        assertSame(currentImage, images[3])
    }

    /**
     * Verifies filtering of non-renderable raster results without index drift.
     *
     * Input: three tiles with decoded images `[first, null, third]`.
     * Expected: output contains only the first and third tile with their matching images.
     */
    @Test
    fun renderableFilterKeepsTileAndImageIndexesTogether() {
        val tiles = listOf(testTile(1), testTile(2), testTile(3))
        val first = TestImageBitmap(width = 1)
        val third = TestImageBitmap(width = 3)

        val filtered =
            RasterFrame(
                tilesByLayer = mapOf("tiles" to tiles),
                decodedImagesByLayer = mapOf("tiles" to listOf(first, null, third)),
            ).withRenderableTilesOnly()

        assertEquals(listOf(tiles[0], tiles[2]), filtered.tilesByLayer.getValue("tiles"))
        assertEquals(listOf(first, third), filtered.decodedImagesByLayer.getValue("tiles"))
    }

    /**
     * Verifies that a replacement source cannot inherit fallback tiles by reusing a layer ID.
     *
     * Input: fallback content from one source and a placeholder from a new source under ID `tiles`.
     * Expected: only the new placeholder remains and the old decoded image is absent.
     */
    @Test
    fun sourceReplacementDropsFallbackForSameLayerId() {
        val oldSource = Any()
        val newSource = Any()
        val staleTile = testTile(x = 1, bytes = byteArrayOf(1))
        val newPlaceholder = testTile(x = 2, bytes = null)
        val staleImage = TestImageBitmap(width = 1)

        val merged =
            mergeRasterFrames(
                placeholderFrame =
                    RasterFrame(
                        tilesByLayer = mapOf("tiles" to listOf(newPlaceholder)),
                        decodedImagesByLayer = emptyMap(),
                        sourceIdentitiesByLayer = mapOf("tiles" to newSource),
                    ),
                fallbackFrame =
                    RasterFrame(
                        tilesByLayer = mapOf("tiles" to listOf(staleTile)),
                        decodedImagesByLayer = mapOf("tiles" to listOf(staleImage)),
                        sourceIdentitiesByLayer = mapOf("tiles" to oldSource),
                    ),
                overviewFrame = RasterFrame.Empty,
                currentFrame = RasterFrame.Empty,
                currentSourceIdentities = mapOf("tiles" to newSource),
            )

        assertEquals(listOf(newPlaceholder), merged.tilesByLayer.getValue("tiles"))
        assertEquals(listOf(null), merged.decodedImagesByLayer.getValue("tiles"))
        assertTrue(staleImage !in merged.decodedImagesByLayer.getValue("tiles"))
    }

    /**
     * Verifies that viewport changes preserve useful fallback from the same runtime source.
     *
     * Input: prior decoded content and a new placeholder sharing one source identity.
     * Expected: both tiles remain ordered for rendering and the prior decoded image is retained.
     */
    @Test
    fun viewportChangeKeepsFallbackForSameSource() {
        val source = Any()
        val fallbackTile = testTile(x = 1, bytes = byteArrayOf(1))
        val placeholder = testTile(x = 2, bytes = null)
        val fallbackImage = TestImageBitmap(width = 1)

        val merged =
            mergeRasterFrames(
                placeholderFrame =
                    RasterFrame(
                        tilesByLayer = mapOf("tiles" to listOf(placeholder)),
                        decodedImagesByLayer = emptyMap(),
                        sourceIdentitiesByLayer = mapOf("tiles" to source),
                    ),
                fallbackFrame =
                    RasterFrame(
                        tilesByLayer = mapOf("tiles" to listOf(fallbackTile)),
                        decodedImagesByLayer = mapOf("tiles" to listOf(fallbackImage)),
                        sourceIdentitiesByLayer = mapOf("tiles" to source),
                    ),
                overviewFrame = RasterFrame.Empty,
                currentFrame = RasterFrame.Empty,
                currentSourceIdentities = mapOf("tiles" to source),
            )

        assertEquals(listOf(placeholder, fallbackTile), merged.tilesByLayer.getValue("tiles"))
        assertEquals(listOf(null, fallbackImage), merged.decodedImagesByLayer.getValue("tiles"))
    }

    /**
     * Verifies ordering of decoded navigation history across zoom levels.
     *
     * Input: a newer fine frame followed by an older coarse frame in cache order.
     * Expected: combination draws coarse coverage first and fine detail above it, with image indexes aligned.
     */
    @Test
    fun rasterHistoryOrdersCoarseCoverageBeforeFineDetail() {
        val source = Any()
        val coarse = testTile(x = 1, z = 2)
        val fine = testTile(x = 2, z = 5)
        val coarseImage = TestImageBitmap(width = 2)
        val fineImage = TestImageBitmap(width = 5)

        val combined =
            combineRasterFrames(
                listOf(
                    RasterFrame(
                        tilesByLayer = mapOf("tiles" to listOf(fine)),
                        decodedImagesByLayer = mapOf("tiles" to listOf(fineImage)),
                        sourceIdentitiesByLayer = mapOf("tiles" to source),
                    ),
                    RasterFrame(
                        tilesByLayer = mapOf("tiles" to listOf(coarse)),
                        decodedImagesByLayer = mapOf("tiles" to listOf(coarseImage)),
                        sourceIdentitiesByLayer = mapOf("tiles" to source),
                    ),
                ),
            )

        assertEquals(listOf(coarse, fine), combined.tilesByLayer.getValue("tiles"))
        assertEquals(listOf(coarseImage, fineImage), combined.decodedImagesByLayer.getValue("tiles"))
    }

    /** Verifies that decoded history from a replaced source is never revived as fallback. */
    @Test
    fun rasterHistoryDropsFramesFromReplacedSource() {
        val oldSource = Any()
        val newSource = Any()
        val stale = testTile(x = 1, z = 2)
        val current = testTile(x = 2, z = 3)
        val staleImage = TestImageBitmap(width = 2)
        val currentImage = TestImageBitmap(width = 3)

        val combined =
            combineRasterFrames(
                listOf(
                    RasterFrame(
                        tilesByLayer = mapOf("tiles" to listOf(stale)),
                        decodedImagesByLayer = mapOf("tiles" to listOf(staleImage)),
                        sourceIdentitiesByLayer = mapOf("tiles" to oldSource),
                    ),
                    RasterFrame(
                        tilesByLayer = mapOf("tiles" to listOf(current)),
                        decodedImagesByLayer = mapOf("tiles" to listOf(currentImage)),
                        sourceIdentitiesByLayer = mapOf("tiles" to newSource),
                    ),
                ),
            )

        assertEquals(listOf(current), combined.tilesByLayer.getValue("tiles"))
        assertEquals(listOf(currentImage), combined.decodedImagesByLayer.getValue("tiles"))
        assertSame(newSource, combined.sourceIdentitiesByLayer.getValue("tiles"))
    }
}
