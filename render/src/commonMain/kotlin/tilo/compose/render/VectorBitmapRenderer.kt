@file:OptIn(ExperimentalTiloRenderingApi::class)

package tilo.compose.render

import androidx.compose.ui.graphics.Canvas as GraphicsCanvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import tilo.compose.render.backend.VectorBitmapRenderSceneLayer
import tilo.compose.render.backend.VectorBitmapSnapshot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tilo.compose.core.layers.vector.VectorLayer
import tilo.compose.core.layers.vector.VectorRenderStrategy
import tilo.compose.core.map.Viewport
import kotlin.math.ceil

internal fun interface VectorBitmapRenderTarget {
    suspend fun render(
        layer: VectorLayer,
        commands: List<RenderCommand>,
        map: tilo.compose.core.map.MapState,
        strategy: VectorRenderStrategy.CachedBitmap,
        density: Density,
        layoutDirection: LayoutDirection,
    ): VectorBitmapRenderSceneLayer?
}

internal class VectorBitmapRenderer(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : VectorBitmapRenderTarget {
    override suspend fun render(
        layer: VectorLayer,
        commands: List<RenderCommand>,
        map: tilo.compose.core.map.MapState,
        strategy: VectorRenderStrategy.CachedBitmap,
        density: Density,
        layoutDirection: LayoutDirection,
    ): VectorBitmapRenderSceneLayer? =
        withContext(dispatcher) {
            val displayWidth = map.viewport.width + strategy.paddingPx * 2
            val displayHeight = map.viewport.height + strategy.paddingPx * 2
            if (displayWidth <= 0 || displayHeight <= 0) return@withContext null

            val bitmapScale = strategy.scale.coerceAtLeast(1.0)
            val bitmapWidth = ceil(displayWidth * bitmapScale).toInt().coerceAtLeast(1)
            val bitmapHeight = ceil(displayHeight * bitmapScale).toInt().coerceAtLeast(1)
            val bitmap = ImageBitmap(bitmapWidth, bitmapHeight)
            val canvas = GraphicsCanvas(bitmap)
            val drawScope = CanvasDrawScope()
            val bitmapMap = tilo.compose.core.map.MapState(
                center = map.center,
                zoom = map.zoom,
                projection = map.projection,
                config = map.config,
                viewport = Viewport(
                    width = bitmapWidth,
                    height = bitmapHeight,
                    pixelRatio = map.viewport.pixelRatio * bitmapScale,
                ),
            )

            drawScope.draw(
                density = density,
                layoutDirection = layoutDirection,
                canvas = canvas,
                size = androidx.compose.ui.geometry.Size(bitmapWidth.toFloat(), bitmapHeight.toFloat()),
            ) {
                drawFeatureGeometry(
                    commands = commands,
                    map = bitmapMap,
                )
            }

            VectorBitmapRenderSceneLayer(
                id = layer.id,
                zIndex = layer.zIndex,
                bitmap = bitmap,
                snapshot = VectorBitmapSnapshot(
                    center = map.center,
                    zoom = map.zoom,
                    bitmapWidth = bitmapWidth,
                    bitmapHeight = bitmapHeight,
                    displayWidth = displayWidth,
                    displayHeight = displayHeight,
                ),
            )
        }
}
