@file:Suppress("unused")

package eu.tilo.compose.render.backend

import eu.tilo.compose.render.PreparedVectorFrame
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
        preparedFramesByLayer: Map<String, PreparedVectorFrame> = emptyMap()
    ): RenderScene {
        val sceneLayers = buildList {
            layers.forEach { layer ->
                when (layer) {
                    is TileLayer -> {
                        val tiles = tilesByLayer[layer.id].orEmpty()
                        if (tiles.isNotEmpty()) {
                            add(RasterRenderSceneLayer(id = layer.id, zIndex = layer.zIndex, tiles = tiles))
                        }
                    }

                    is VectorLayer -> {
                        val commands = commandsByLayer[layer.id].orEmpty()
                        val preparedFrame = preparedFramesByLayer[layer.id]
                        if (commands.isNotEmpty() || preparedFrame != null) {
                            add(
                                VectorRenderSceneLayer(
                                    id = layer.id,
                                    zIndex = layer.zIndex,
                                    commands = commands,
                                    preparedFrame = preparedFrame
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
