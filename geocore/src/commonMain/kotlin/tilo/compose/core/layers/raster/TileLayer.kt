package tilo.compose.core.layers.raster

import tilo.compose.core.layers.Layer
import tilo.compose.core.map.MapState
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
 * The renderer only positions tiles using [tilo.compose.core.map.MapState.worldToScreen] on the
 * bounds carried by each [tilo.compose.core.tile.Tile] — no CRS logic in the renderer.
 */
interface TileLayer : Layer {
    val grid: TileGrid
    override val projection: Projection

    /**
     * Identity of the runtime source that owns this layer's tile content.
     *
     * Presentation-only wrappers should preserve this value. Renderers use it
     * to keep fallback tiles across viewport changes and discard them when a
     * different source replaces a layer under the same [id].
     */
    val sourceIdentity: Any
        get() = this

    /**
     * Returns visible tile positions for the current [map] state without fetching bytes.
     * Renderers can use these tiles as placeholders while [loadTiles] is still in flight.
     */
    fun planTiles(map: MapState): List<Tile> = emptyList()

    /**
     * Returns tiles visible for the current [map] state.
     * Implementations should suspend and return tiles with bytes already fetched.
     */
    suspend fun loadTiles(map: MapState): List<Tile>

    /**
     * Returns a coarse, quickly loadable tile coverage for the current [map].
     *
     * Renderers can use these tiles as a temporary overview while the sharper
     * visible tile set is still loading, for example after a fast fling.
     */
    suspend fun loadOverviewTiles(map: MapState): List<Tile> = emptyList()

    /**
     * Prefetches coarse nearby tiles for the current [map] state.
     *
     * This is useful during kinetic panning: a renderer can keep a low-detail
     * overview warm around the moving viewport without waiting for sharp tiles.
     */
    suspend fun prefetchOverviewTiles(map: MapState) = Unit

    /**
     * Prefetches nearby tiles for the current [map] state without changing the
     * rendered result. Implementations may no-op when prefetching is unsupported.
     */
    suspend fun prefetchTiles(map: MapState) = Unit

    fun validateProjection(map: MapState) {
        require(map.projection.id == projection.id) {
            "Tile layer '$id' uses ${projection.id}, but map uses ${map.projection.id}. Tiles are not reprojected client-side."
        }
    }
}
