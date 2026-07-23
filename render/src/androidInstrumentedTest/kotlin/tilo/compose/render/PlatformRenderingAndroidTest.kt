@file:OptIn(ExperimentalTiloRenderingApi::class)

package tilo.compose.render

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.runner.RunWith
import tilo.compose.core.feature.ColorValue
import tilo.compose.core.feature.Feature
import tilo.compose.core.feature.FillStyle
import tilo.compose.core.feature.LineStyle
import tilo.compose.core.feature.PointIconStyle
import tilo.compose.core.feature.PointStyle
import tilo.compose.core.feature.PolygonStyle
import tilo.compose.core.feature.StrokeStyle
import tilo.compose.core.geometry.Point
import tilo.compose.core.map.MapState
import tilo.compose.core.map.Viewport
import tilo.compose.core.tile.Tile
import tilo.compose.core.tile.TileBounds
import tilo.compose.core.tile.TileCoordinate
import tilo.compose.render.backend.RasterRenderSceneLayer
import tilo.compose.render.backend.RenderScene
import tilo.compose.render.backend.VectorBitmapRenderSceneLayer
import tilo.compose.render.backend.VectorBitmapSnapshot
import tilo.compose.render.backend.drawRenderScene
import tilo.compose.render.backend.drawVectorBitmapLayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class PlatformRenderingAndroidTest {
    @Test
    fun registeredPointIconRendersAtPointCenter() {
        val command =
            RenderPoint(
                id = "icon",
                point = Point(0.0, 0.0),
                style =
                    PointStyle(
                        fill = null,
                        stroke = null,
                        icon = PointIconStyle(id = "stop", size = 20.0),
                    ),
            )

        val result =
            renderBitmap {
                drawFeatureGeometry(
                    commands = listOf(command),
                    map = MapState(viewport = Viewport(64, 64)),
                    pointIconPainters = mapOf("stop" to ColorPainter(Color.Red)),
                )
            }

        assertEquals(RED, result.argbAt(32, 32))
        assertEquals(0, result.argbAt(20, 20))
    }

    /**
     * Verifies the real Android bitmap decoder with valid and invalid encoded data.
     *
     * Input: a valid 1 × 1 PNG fixture and a three-byte corrupt payload.
     * Expected: a 1 × 1 image for PNG and `null` for corrupt bytes.
     */
    @Test
    fun platformDecoderAcceptsRealPngAndRejectsCorruptBytes() {
        val image = decodeTileImageBitmap(onePixelPng)

        assertNotNull(image)
        assertEquals(1, image.width)
        assertEquals(1, image.height)
        assertNull(decodeTileImageBitmap(byteArrayOf(0x01, 0x02, 0x03)))
    }

    /**
     * Verifies pixel-perfect coverage of four adjacent raster tiles on Android Canvas.
     *
     * Input: a 64 × 64 viewport split into four differently colored predecoded tiles.
     * Expected: every pixel is opaque and each quadrant center has its assigned color.
     */
    @Test
    fun rasterTwoByTwoHasNoTransparentSeams() {
        val map = MapState(viewport = Viewport(64, 64))
        val tiles = quadrantTiles()
        val colors = listOf(RED, GREEN, BLUE, YELLOW)
        val images = colors.map(::solidBitmap)

        val result =
            renderBitmap {
                drawTiles(tiles, tileDecoder = { error("predecoded tiles must not decode again") }, map, images)
            }

        assertTrue(result.readArgb().all { argb -> argb ushr 24 == 0xFF })
        assertEquals(
            colors,
            listOf(result.argbAt(16, 16), result.argbAt(48, 16), result.argbAt(16, 48), result.argbAt(48, 48)),
        )
    }

    @Test
    fun rasterTilesRotateAroundViewportCenterWithoutSeams() {
        val map = MapState(bearing = 90.0, viewport = Viewport(64, 64))
        val images = listOf(RED, GREEN, BLUE, YELLOW).map(::solidBitmap)

        val result =
            renderBitmap {
                drawTiles(
                    quadrantTiles(),
                    tileDecoder = { error("predecoded tiles must not decode again") },
                    map,
                    images,
                )
            }

        assertTrue(result.readArgb().all { argb -> argb ushr 24 == 0xFF })
        assertEquals(
            listOf(GREEN, YELLOW, RED, BLUE),
            listOf(result.argbAt(16, 16), result.argbAt(48, 16), result.argbAt(16, 48), result.argbAt(48, 48)),
        )
    }

    /** Verifies that raster layer opacity reaches decoded tile pixels. */
    @Test
    fun rasterOpacityIsAppliedToDecodedTiles() {
        val map = MapState(viewport = Viewport(64, 64))

        val result =
            renderBitmap {
                drawTiles(
                    tiles = quadrantTiles(),
                    tileDecoder = { error("predecoded tiles must not decode again") },
                    map = map,
                    decodedImages = List(4) { solidBitmap(RED) },
                    opacity = 0.5,
                )
            }

        val pixel = result.argbAt(16, 16)
        assertEquals(0x80, pixel ushr 24)
        assertEquals(0x00FF0000, pixel and 0x00FFFFFF)
    }

    /**
     * Verifies that fallback and current tiles are composited before raster layer opacity is applied.
     *
     * Input: two opaque, fully overlapping generations in one raster scene layer at opacity `0.5`.
     * Expected: the resulting alpha stays `0.5`, rather than accumulating to `0.75` as generations change.
     */
    @Test
    fun rasterOpacityIsAppliedOnceAcrossOverlappingTileGenerations() {
        val map = MapState(viewport = Viewport(64, 64))
        val overlappingTile = tile(0, 0, Point(-32.0, 32.0), Point(32.0, -32.0))
        val density = Density(1f)
        val layoutDirection = LayoutDirection.Ltr
        val textMeasurer =
            TextMeasurer(
                defaultFontFamilyResolver =
                    createFontFamilyResolver(
                        InstrumentationRegistry.getInstrumentation().targetContext,
                    ),
                defaultDensity = density,
                defaultLayoutDirection = layoutDirection,
            )

        val result =
            renderBitmap {
                drawRenderScene(
                    scene =
                        RenderScene(
                            listOf(
                                RasterRenderSceneLayer(
                                    id = "tiles",
                                    zIndex = 0,
                                    tiles = listOf(overlappingTile, overlappingTile),
                                    decodedImages = listOf(solidBitmap(RED), solidBitmap(RED)),
                                    opacity = 0.5,
                                ),
                            ),
                        ),
                    map = map,
                    tileDecoder = { error("predecoded tiles must not decode again") },
                    offscreenLabelDrawScope = CanvasDrawScope(),
                    textMeasurer = textMeasurer,
                    labelBitmapCache = LabelBitmapCache(),
                )
            }

        val pixel = result.argbAt(32, 32)
        assertEquals(0x80, pixel ushr 24)
        assertEquals(0x00FF0000, pixel and 0x00FFFFFF)
    }

    /**
     * Verifies that a live-camera placeholder covers space exposed when an older fallback shrinks during zoom-out.
     *
     * Input: a full-viewport placeholder and a loaded fallback covering only the centered half of the current viewport.
     * Expected: the center shows the fallback while every outer sample shows placeholder, never transparent Canvas.
     */
    @Test
    fun livePlaceholderCoversViewportAroundStaleZoomFallback() {
        val map = MapState(viewport = Viewport(64, 64))
        val livePlaceholder = tile(0, 0, Point(-32.0, 32.0), Point(32.0, -32.0))
        val staleFallback = tile(0, 0, Point(-16.0, 16.0), Point(16.0, -16.0))
        val density = Density(1f)
        val layoutDirection = LayoutDirection.Ltr
        val textMeasurer =
            TextMeasurer(
                defaultFontFamilyResolver =
                    createFontFamilyResolver(
                        InstrumentationRegistry.getInstrumentation().targetContext,
                    ),
                defaultDensity = density,
                defaultLayoutDirection = layoutDirection,
            )

        val result =
            renderBitmap {
                drawRenderScene(
                    scene =
                        RenderScene(
                            listOf(
                                RasterRenderSceneLayer(
                                    id = "tiles",
                                    zIndex = 0,
                                    tiles = listOf(staleFallback),
                                    decodedImages = listOf(solidBitmap(RED)),
                                    placeholderTiles = listOf(livePlaceholder),
                                ),
                            ),
                        ),
                    map = map,
                    tileDecoder = { error("predecoded tiles must not decode again") },
                    offscreenLabelDrawScope = CanvasDrawScope(),
                    textMeasurer = textMeasurer,
                    labelBitmapCache = LabelBitmapCache(),
                )
            }

        assertEquals(RED, result.argbAt(32, 32))
        listOf(Offset(4f, 4f), Offset(60f, 4f), Offset(4f, 60f), Offset(60f, 60f)).forEach { point ->
            assertEquals(0xFFE3F2FD.toInt(), result.argbAt(point.x.toInt(), point.y.toInt()))
        }
        assertTrue(result.readArgb().all { argb -> argb ushr 24 == 0xFF })
    }

    /**
     * Verifies the concrete placeholder drawn for a tile without a decoded image.
     *
     * Input: a 2 × 2 raster grid whose bottom-right image is `null`.
     * Expected: that quadrant has placeholder color `#E3F2FD`; loaded quadrants keep their colors.
     */
    @Test
    fun missingRasterTileRendersDeterministicPlaceholder() {
        val map = MapState(viewport = Viewport(64, 64))
        val images = listOf(solidBitmap(RED), solidBitmap(GREEN), solidBitmap(BLUE), null)

        val result =
            renderBitmap {
                drawTiles(quadrantTiles(), tileDecoder = { null }, map, images)
            }

        assertEquals(0xFFE3F2FD.toInt(), result.argbAt(48, 48))
        assertEquals(RED, result.argbAt(16, 16))
    }

    /**
     * Verifies real Canvas coverage for point, line, polygon, and polygon-hole geometry.
     *
     * Input: blue point, green line, and red polygon with an interior ring.
     * Expected: representative pixels match those colors while the hole stays transparent.
     */
    @Test
    fun vectorPointLineAndPolygonHoleProduceExpectedCoverage() {
        val map = MapState(viewport = Viewport(64, 64))
        val polygon =
            RenderPolygon(
                id = "polygon",
                rings =
                    listOf(
                        ring(4.0, 20.0, 28.0, -20.0),
                        ring(12.0, 8.0, 20.0, -8.0),
                    ),
                style =
                    PolygonStyle(
                        fill = FillStyle(ColorValue(RED.toUInt().toULong())),
                        casing = null,
                        stroke = null,
                    ),
            )
        val line =
            RenderLineString(
                id = "line",
                points = listOf(Point(-28.0, 20.0), Point(-4.0, 20.0)),
                style =
                    LineStyle(
                        casing = null,
                        stroke = StrokeStyle(ColorValue(GREEN.toUInt().toULong()), width = 4.0),
                    ),
            )
        val point =
            RenderPoint(
                id = "point",
                point = Point(-16.0, 0.0),
                style = PointStyle(size = 10.0, fill = FillStyle(ColorValue(BLUE.toUInt().toULong())), stroke = null),
            )

        val result = renderBitmap { drawFeatureGeometry(listOf(polygon, line, point), map) }

        assertEquals(BLUE, result.argbAt(16, 32))
        assertEquals(GREEN, result.argbAt(16, 12))
        assertEquals(RED, result.argbAt(38, 32))
        assertEquals(0, result.argbAt(48, 32)) // polygon hole remains transparent
    }

    @Test
    fun vectorGeometryUsesCameraBearing() {
        val map = MapState(bearing = 90.0, viewport = Viewport(64, 64))
        val point =
            RenderPoint(
                id = "east",
                point = Point(16.0, 0.0),
                style = PointStyle(size = 8.0, fill = FillStyle(ColorValue(RED.toUInt().toULong())), stroke = null),
            )

        val result = renderBitmap { drawFeatureGeometry(listOf(point), map) }

        assertEquals(RED, result.argbAt(32, 16))
        assertEquals(0, result.argbAt(48, 32))
    }

    /**
     * Verifies that selected and normal feature styles remain visually distinguishable.
     *
     * Input: two points sharing blue normal and red selected styles; only one is selected.
     * Expected: sampled centers are blue for normal and red for selected.
     */
    @Test
    fun selectedAndUnselectedVectorStylesRemainVisuallyDistinct() {
        val red = PointStyle(size = 12.0, fill = FillStyle(ColorValue(RED.toUInt().toULong())), stroke = null)
        val blue = PointStyle(size = 12.0, fill = FillStyle(ColorValue(BLUE.toUInt().toULong())), stroke = null)
        val commands =
            CommandBuilder.build(
                map = MapState(viewport = Viewport(64, 64)),
                features =
                    listOf(
                        Feature(geometry = Point(-12.0, 0.0), key = "normal", style = blue, selectedStyle = red),
                        Feature(geometry = Point(12.0, 0.0), key = "selected", style = blue, selectedStyle = red),
                    ),
                layerId = "points",
                selectedFeatureKeys = setOf("selected"),
            )

        val result = renderBitmap { drawFeatureGeometry(commands, MapState(viewport = Viewport(64, 64))) }

        assertEquals(BLUE, result.argbAt(20, 32))
        assertEquals(RED, result.argbAt(44, 32))
    }

    /**
     * Verifies placement and scaling of a cached vector bitmap at fractional zoom.
     *
     * Input: a 10 × 10 magenta snapshot centered at zoom zero, displayed at zoom `0.5`.
     * Expected: the viewport center is magenta and a pixel outside the scaled bitmap is transparent.
     */
    @Test
    fun cachedVectorBitmapRemainsCenteredAtFractionalZoom() {
        val source = solidBitmap(MAGENTA, width = 10, height = 10)
        val layer =
            VectorBitmapRenderSceneLayer(
                id = "cached",
                zIndex = 0,
                bitmap = source,
                snapshot = VectorBitmapSnapshot(Point(0.0, 0.0), 0.0, 10, 10, 10, 10),
            )
        val map = MapState(zoom = 0.5, viewport = Viewport(64, 64))

        val result = renderBitmap { drawVectorBitmapLayer(layer, map) }

        assertEquals(MAGENTA, result.argbAt(32, 32))
        assertEquals(0, result.argbAt(20, 20))
    }

    @Test
    fun cachedVectorBitmapTracksBearingWhileReplacementRenders() {
        val source =
            Bitmap
                .createBitmap(20, 20, Bitmap.Config.ARGB_8888)
                .apply {
                    repeat(height) { y ->
                        repeat(width) { x -> setPixel(x, y, if (x < width / 2) RED else BLUE) }
                    }
                }.asImageBitmap()
        val layer =
            VectorBitmapRenderSceneLayer(
                id = "cached",
                zIndex = 0,
                bitmap = source,
                snapshot = VectorBitmapSnapshot(Point(0.0, 0.0), 0.0, 20, 20, 20, 20, bearing = 0.0),
            )
        val map = MapState(bearing = 90.0, viewport = Viewport(64, 64))

        val result = renderBitmap { drawVectorBitmapLayer(layer, map) }

        assertEquals(BLUE, result.argbAt(32, 24))
        assertEquals(RED, result.argbAt(32, 40))
    }

    /**
     * Verifies label collision using real Android font measurement and bitmap drawing.
     *
     * Input: overlapping labels where an unselected label has priority `100` and the other is selected.
     * Expected: only the selected label is placed and its drawing produces non-transparent pixels.
     */
    @Test
    fun collidingLabelsRenderOnlySelectedWinner() {
        val density = Density(1f)
        val layoutDirection = LayoutDirection.Ltr
        val textMeasurer =
            TextMeasurer(
                defaultFontFamilyResolver =
                    createFontFamilyResolver(
                        InstrumentationRegistry.getInstrumentation().targetContext,
                    ),
                defaultDensity = density,
                defaultLayoutDirection = layoutDirection,
            )
        val labels =
            listOf(
                RenderLabel(
                    id = "priority",
                    text = "PRIORITY",
                    anchor = Point(0.0, 0.0),
                    labelPriority = 100,
                ),
                RenderLabel(
                    id = "selected",
                    text = "SELECTED",
                    anchor = Point(0.0, 0.0),
                    selected = true,
                ),
            )
        val bitmap = ImageBitmap(64, 64)
        val labelCache = LabelBitmapCache()
        var placed = emptyList<PlacedLabel>()
        CanvasDrawScope().draw(
            density = density,
            layoutDirection = layoutDirection,
            canvas = Canvas(bitmap),
            size = Size(64f, 64f),
        ) {
            placed =
                LabelLayoutEngine().layout(
                    labels = labels,
                    map = MapState(viewport = Viewport(64, 64)),
                    drawScope = this,
                    textMeasurer = textMeasurer,
                    labelBitmapCache = labelCache,
                )
            drawPlacedLabels(
                labels = placed,
                offscreenDrawScope = CanvasDrawScope(),
                textMeasurer = textMeasurer,
                labelBitmapCache = labelCache,
            )
        }

        assertEquals(listOf("selected"), placed.map { it.command.id })
        assertTrue(bitmap.readArgb().any { argb -> argb ushr 24 != 0 })
    }

    private fun renderBitmap(block: DrawScope.() -> Unit): ImageBitmap {
        val bitmap = ImageBitmap(64, 64)
        CanvasDrawScope().draw(
            density = Density(1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = Canvas(bitmap),
            size = Size(64f, 64f),
            block = block,
        )
        return bitmap
    }

    private fun solidBitmap(
        color: Int,
        width: Int = 1,
        height: Int = 1,
    ): ImageBitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }.asImageBitmap()

    private fun quadrantTiles(): List<Tile> =
        listOf(
            tile(0, 0, Point(-32.0, 32.0), Point(0.0, 0.0)),
            tile(1, 0, Point(0.0, 32.0), Point(32.0, 0.0)),
            tile(0, 1, Point(-32.0, 0.0), Point(0.0, -32.0)),
            tile(1, 1, Point(0.0, 0.0), Point(32.0, -32.0)),
        )

    private fun tile(
        x: Int,
        y: Int,
        topLeft: Point,
        bottomRight: Point,
    ): Tile = Tile(TileCoordinate(0, x, y), TileBounds(topLeft, bottomRight), byteArrayOf(1))

    private fun ring(
        minX: Double,
        maxY: Double,
        maxX: Double,
        minY: Double,
    ): List<Point> =
        listOf(
            Point(minX, maxY),
            Point(maxX, maxY),
            Point(maxX, minY),
            Point(minX, minY),
            Point(minX, maxY),
        )

    private fun ImageBitmap.readArgb(): IntArray = IntArray(width * height).also { pixels -> readPixels(pixels) }

    private fun ImageBitmap.argbAt(
        x: Int,
        y: Int,
    ): Int = readArgb()[y * width + x]

    private companion object {
        const val RED: Int = -0x10000
        const val GREEN: Int = -0xFF0100
        const val BLUE: Int = -0xFFFF01
        const val YELLOW: Int = -0x100
        const val MAGENTA: Int = -0xFF01

        val onePixelPng =
            byteArrayOf(
                0x89.toByte(),
                0x50,
                0x4E,
                0x47,
                0x0D,
                0x0A,
                0x1A,
                0x0A,
                0x00,
                0x00,
                0x00,
                0x0D,
                0x49,
                0x48,
                0x44,
                0x52,
                0x00,
                0x00,
                0x00,
                0x01,
                0x00,
                0x00,
                0x00,
                0x01,
                0x08,
                0x04,
                0x00,
                0x00,
                0x00,
                0xB5.toByte(),
                0x1C,
                0x0C,
                0x02,
                0x00,
                0x00,
                0x00,
                0x0B,
                0x49,
                0x44,
                0x41,
                0x54,
                0x78,
                0xDA.toByte(),
                0x63,
                0x64,
                0xF8.toByte(),
                0x0F,
                0x00,
                0x01,
                0x05,
                0x01,
                0x01,
                0x27,
                0x18,
                0xE3.toByte(),
                0x66,
                0x00,
                0x00,
                0x00,
                0x00,
                0x49,
                0x45,
                0x4E,
                0x44,
                0xAE.toByte(),
                0x42,
                0x60,
                0x82.toByte(),
            )
    }
}
