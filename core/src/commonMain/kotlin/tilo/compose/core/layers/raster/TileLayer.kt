package tilo.compose.core.layers.raster

import tilo.compose.core.layers.Layer
import tilo.compose.core.map.Map
import tilo.compose.core.projection.Projection
import tilo.compose.core.tile.Tile
import tilo.compose.core.tile.TileGrid

/**
 * A layer that provides raster tiles.
 *
 * Implementations know their [grid] and [projection] and build the list of
 * [tilo.compose.core.tile.TileRequest]s that cover the current map view. They also fetch the tile
 * bytes and return [tilo.compose.core.tile.Tile]s ready for the renderer.
 *
 * The renderer only positions tiles using [tilo.compose.core.map.Map.worldToScreen] on the
 * bounds carried by each [tilo.compose.core.tile.Tile] — no CRS logic in the renderer.
 */
interface TileLayer : Layer {
    val grid: TileGrid
    override val projection: Projection

    /**
     * Returns tiles visible for the current [map] state.
     * Implementations should suspend and return tiles with bytes already fetched.
     */
    suspend fun loadTiles(map: Map): List<Tile>

    fun validateProjection(map: Map) {
        require(map.projection.id == projection.id) {
            "Tile layer '$id' uses ${projection.id}, but map uses ${map.projection.id}. Tiles are not reprojected client-side."
        }
    }
}
