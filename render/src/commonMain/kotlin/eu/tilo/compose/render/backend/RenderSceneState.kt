package eu.tilo.compose.render.backend

import eu.tilo.compose.render.RenderCommand
import tilo.compose.core.layers.Layer
import tilo.compose.core.tile.Tile

internal data class RenderSceneState(
    val tilesByLayer: Map<String, List<Tile>> = emptyMap(),
    val commandsByLayer: Map<String, List<RenderCommand>> = emptyMap()
) {
    fun withTiles(layers: List<Layer>, nextTilesByLayer: Map<String, List<Tile>>): RenderSceneState =
        copy(tilesByLayer = nextTilesByLayer).retainOnly(layers)

    fun withCommands(layers: List<Layer>, nextCommandsByLayer: Map<String, List<RenderCommand>>): RenderSceneState =
        copy(commandsByLayer = nextCommandsByLayer).retainOnly(layers)

    fun retainOnly(layers: List<Layer>): RenderSceneState {
        val activeIds = layers.asSequence().map(Layer::id).toSet()
        return copy(
            tilesByLayer = tilesByLayer.filterKeys(activeIds::contains),
            commandsByLayer = commandsByLayer.filterKeys(activeIds::contains)
        )
    }

    fun toScene(layers: List<Layer>): RenderScene =
        RenderSceneBuilder.build(
            layers = layers,
            tilesByLayer = tilesByLayer,
            commandsByLayer = commandsByLayer
        )
}

