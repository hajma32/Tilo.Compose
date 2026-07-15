package tilo.compose.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import tilo.compose.core.geometry.Point

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
        val left = testTile(
            x = 0,
            topLeft = Point(-1.25, 0.8),
            bottomRight = Point(0.15, -0.6),
        ).screenRect(map)
        val right = testTile(
            x = 1,
            topLeft = Point(0.15, 0.8),
            bottomRight = Point(1.55, -0.6),
        ).screenRect(map)
        val below = testTile(
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
        val rect = testTile(
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

        val merged = mergeRasterFrames(
            placeholderFrame = RasterFrame(mapOf("tiles" to listOf(placeholder)), emptyMap()),
            overviewFrame = RasterFrame(mapOf("tiles" to listOf(overview)), mapOf("tiles" to listOf(overviewImage))),
            fallbackFrame = RasterFrame(mapOf("tiles" to listOf(fallback)), mapOf("tiles" to listOf(fallbackImage))),
            currentFrame = RasterFrame(mapOf("tiles" to listOf(current)), mapOf("tiles" to listOf(currentImage))),
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

        val filtered = RasterFrame(
            tilesByLayer = mapOf("tiles" to tiles),
            decodedImagesByLayer = mapOf("tiles" to listOf(first, null, third)),
        ).withRenderableTilesOnly()

        assertEquals(listOf(tiles[0], tiles[2]), filtered.tilesByLayer.getValue("tiles"))
        assertEquals(listOf(first, third), filtered.decodedImagesByLayer.getValue("tiles"))
    }
}
