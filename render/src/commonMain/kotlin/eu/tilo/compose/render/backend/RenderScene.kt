@file:Suppress("unused")

package eu.tilo.compose.render.backend

import eu.tilo.compose.render.PreparedVectorFrame
import eu.tilo.compose.render.RenderCommand
import tilo.compose.core.tile.Tile

sealed interface RenderSceneLayer {
    val id: String
    val zIndex: Int
}

data class RasterRenderSceneLayer(
    override val id: String,
    override val zIndex: Int,
    val tiles: List<Tile>
) : RenderSceneLayer

class VectorRenderSceneLayer(
    override val id: String,
    override val zIndex: Int,
    val commands: List<RenderCommand>
) : RenderSceneLayer {
    internal var preparedFrame: PreparedVectorFrame? = null
        private set

    internal constructor(
        id: String,
        zIndex: Int,
        commands: List<RenderCommand>,
        preparedFrame: PreparedVectorFrame?
    ) : this(id = id, zIndex = zIndex, commands = commands) {
        this.preparedFrame = preparedFrame
    }
}

data class RenderScene(
    val layers: List<RenderSceneLayer>
) {
    companion object {
        val Empty = RenderScene(emptyList())
    }
}
