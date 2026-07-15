@file:OptIn(ExperimentalTiloRenderingApi::class)

package tilo.compose.render

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import tilo.compose.core.geometry.Point
import tilo.compose.core.map.MapState
import tilo.compose.core.map.Viewport
import tilo.compose.core.tile.Tile
import tilo.compose.core.tile.TileBounds
import tilo.compose.core.tile.TileCoordinate

internal class TestImageBitmap(
    override val width: Int = 1,
    override val height: Int = 1,
    private val pixels: IntArray = IntArray(width * height),
) : ImageBitmap {
    override val colorSpace: ColorSpace = ColorSpaces.Srgb
    override val hasAlpha: Boolean = true
    override val config: ImageBitmapConfig = ImageBitmapConfig.Argb8888

    override fun readPixels(
        buffer: IntArray,
        startX: Int,
        startY: Int,
        width: Int,
        height: Int,
        bufferOffset: Int,
        stride: Int,
    ) {
        repeat(height) { row ->
            repeat(width) { column ->
                buffer[bufferOffset + row * stride + column] =
                    pixels[(startY + row) * this.width + startX + column]
            }
        }
    }

    override fun prepareToDraw() = Unit
}

internal fun testMap(
    center: Point = Point(0.0, 0.0),
    zoom: Double = 0.0,
    width: Int = 256,
    height: Int = 256,
    pixelRatio: Double = 1.0,
): MapState =
    MapState(
        center = center,
        zoom = zoom,
        viewport = Viewport(width = width, height = height, pixelRatio = pixelRatio),
    )

internal fun testTile(
    x: Int,
    y: Int = 0,
    z: Int = 0,
    bytes: ByteArray? = byteArrayOf(x.toByte()),
    topLeft: Point = Point(x.toDouble(), 1.0),
    bottomRight: Point = Point(x + 1.0, 0.0),
): Tile =
    Tile(
        coordinate = TileCoordinate(z = z, x = x, y = y),
        bounds = TileBounds(topLeft = topLeft, bottomRight = bottomRight),
        bytes = bytes,
    )
