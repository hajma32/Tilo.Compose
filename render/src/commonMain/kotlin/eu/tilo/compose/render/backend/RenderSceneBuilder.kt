@file:Suppress("unused")

package eu.tilo.compose.render.backend

import androidx.compose.ui.graphics.ImageBitmap
import eu.tilo.compose.render.RenderCommand
import tilo.compose.core.layers.Layer
import tilo.compose.core.layers.raster.TileLayer
import tilo.compose.core.layers.vector.VectorLayer
import tilo.compose.core.tile.Tile

object RenderSceneBuilder {

    fun build(
        layers: List<Layer>,
        tilesByLayer: Map<String, List<Tile>>,
        commandsByLayer: Map<String, List<RenderCommand>>,
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
