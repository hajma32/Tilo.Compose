package tilo.compose.core.tile.source

import tilo.compose.core.map.Viewport
import tilo.compose.core.tile.Tile

/** Generic tile source contract. */
interface Source {
    /**
     * @param zoomLevel slippy-map zoom level (typically 0..22)
     * @param viewport current map viewport in pixels
     * @param tileCount desired number of tiles around source center
     */
    fun getTiles(
        zoomLevel: Int,
        viewport: Viewport,
        tileCount: Int = 9
    ): List<Tile>
}
