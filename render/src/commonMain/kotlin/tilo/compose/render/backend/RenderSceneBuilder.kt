@file:Suppress("unused")
@file:OptIn(ExperimentalTiloRenderingApi::class)

package tilo.compose.render.backend

import androidx.compose.ui.graphics.ImageBitmap
import tilo.compose.render.ExperimentalTiloRenderingApi
import tilo.compose.render.RenderCommand
import tilo.compose.core.layers.Layer
import tilo.compose.core.layers.raster.TileLayer
import tilo.compose.core.layers.vector.VectorLayer
import tilo.compose.core.tile.Tile

internal object RenderSceneBuilder {

    fun build(
        layers: List<Layer>,
        tilesByLayer: Map<String, List<Tile>>,
        commandsByLayer: Map<String, List<RenderCommand>>,
        vectorBitmapsByLayer: Map<String, VectorBitmapRenderSceneLayer> = emptyMap(),
        decodedImagesByLayer: Map<String, List<ImageBitmap?>> = emptyMap()
    ): RenderScene {
        val sceneLayers = buildList {
            layers.forEach { layer ->
                when (layer) {
                    is TileLayer -> {
                        val tiles = tilesByLayer[layer.id].orEmpty()
                        if (tiles.isNotEmpty()) {
                            val images = decodedImagesByLayer[layer.id]
                            add(RasterRenderSceneLayer(id = layer.id, zIndex = layer.zIndex, tiles = tiles, decodedImages = images))
                        }
                    }

                    is VectorLayer -> {
                        vectorBitmapsByLayer[layer.id]?.let { bitmapLayer ->
                            add(bitmapLayer)
                        }
                        val commands = commandsByLayer[layer.id].orEmpty()
                        if (commands.isNotEmpty()) {
                            add(
                                VectorRenderSceneLayer(
                                    id = layer.id,
                                    zIndex = layer.zIndex,
                                    commands = commands
                                )
                            )
                        }
                    }
                }
            }
        }

        return RenderScene(sceneLayers)
    }
}
