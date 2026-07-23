@file:Suppress("unused")
@file:OptIn(ExperimentalTiloRenderingApi::class)

package tilo.compose.render.backend

import androidx.compose.ui.graphics.ImageBitmap
import tilo.compose.core.layers.Layer
import tilo.compose.core.layers.raster.TileLayer
import tilo.compose.core.layers.vector.VectorLayer
import tilo.compose.core.tile.Tile
import tilo.compose.render.ExperimentalTiloRenderingApi
import tilo.compose.render.PointIconPainterLayer
import tilo.compose.render.RenderCommand

internal object RenderSceneBuilder {
    fun build(
        layers: List<Layer>,
        tilesByLayer: Map<String, List<Tile>>,
        commandsByLayer: Map<String, List<RenderCommand>>,
        vectorBitmapsByLayer: Map<String, VectorBitmapRenderSceneLayer> = emptyMap(),
        decodedImagesByLayer: Map<String, List<ImageBitmap?>> = emptyMap(),
        effectiveOpacitiesByLayerId: Map<String, Double> = emptyMap(),
    ): RenderScene {
        val sceneLayers =
            buildList {
                layers.forEach { layer ->
                    val opacity = effectiveOpacitiesByLayerId[layer.id] ?: layer.opacity
                    when (layer) {
                        is TileLayer -> {
                            val tiles = tilesByLayer[layer.id].orEmpty()
                            if (tiles.isNotEmpty()) {
                                val images = decodedImagesByLayer[layer.id]
                                add(
                                    RasterRenderSceneLayer(
                                        id = layer.id,
                                        zIndex = layer.zIndex,
                                        opacity = opacity,
                                        tiles = tiles,
                                        decodedImages = images,
                                    ),
                                )
                            }
                        }

                        is VectorLayer -> {
                            vectorBitmapsByLayer[layer.id]?.let { bitmapLayer ->
                                add(bitmapLayer.copy(opacity = opacity))
                            }
                            val commands = commandsByLayer[layer.id].orEmpty()
                            if (commands.isNotEmpty()) {
                                add(
                                    VectorRenderSceneLayer(
                                        id = layer.id,
                                        zIndex = layer.zIndex,
                                        opacity = opacity,
                                        commands = commands,
                                        pointIconPainters =
                                            (layer as? PointIconPainterLayer)
                                                ?.pointIconPainters
                                                .orEmpty(),
                                    ),
                                )
                            }
                        }
                    }
                }
            }

        return RenderScene(sceneLayers)
    }
}
