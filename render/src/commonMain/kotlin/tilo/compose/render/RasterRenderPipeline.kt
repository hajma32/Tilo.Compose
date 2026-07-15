package tilo.compose.render

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tilo.compose.core.layers.raster.TileLayer
import tilo.compose.core.tile.Tile
import tilo.compose.core.map.MapState

internal data class RasterFrame(
    val tilesByLayer: Map<String, List<Tile>>,
    val decodedImagesByLayer: Map<String, List<ImageBitmap?>>,
) {
    companion object {
        val Empty = RasterFrame(
            tilesByLayer = emptyMap(),
            decodedImagesByLayer = emptyMap(),
        )
    }
}

internal class RasterRenderPipeline(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    fun buildPlaceholderFrame(tileLayers: List<TileLayer>, map: MapState): RasterFrame =
        RasterFrame(
            tilesByLayer = buildMap {
                tileLayers.forEach { layer ->
                    put(layer.id, layer.planTiles(map))
                }
            },
            decodedImagesByLayer = emptyMap(),
        )

    suspend fun buildVisibleFrame(
        tileLayers: List<TileLayer>,
        map: MapState,
        tileDecoder: ((ByteArray) -> ImageBitmap?)?,
    ): RasterFrame {
        val tilesByLayer = fetchVisibleTiles(tileLayers, map)
        val decodedImagesByLayer = decodeImages(tilesByLayer, tileDecoder)
        return RasterFrame(
            tilesByLayer = tilesByLayer,
            decodedImagesByLayer = decodedImagesByLayer,
        )
    }

    suspend fun buildOverviewFrame(
        tileLayers: List<TileLayer>,
        map: MapState,
        tileDecoder: ((ByteArray) -> ImageBitmap?)?,
    ): RasterFrame {
        val tilesByLayer = fetchOverviewTiles(tileLayers, map)
        val decodedImagesByLayer = decodeImages(tilesByLayer, tileDecoder)
        return RasterFrame(
            tilesByLayer = tilesByLayer,
            decodedImagesByLayer = decodedImagesByLayer,
        )
    }

    suspend fun prefetch(tileLayers: List<TileLayer>, map: MapState) {
        withContext(dispatcher) {
            tileLayers.forEach { layer ->
                layer.prefetchTiles(map)
            }
        }
    }

    suspend fun prefetchOverview(tileLayers: List<TileLayer>, map: MapState) {
        withContext(dispatcher) {
            tileLayers.forEach { layer ->
                layer.prefetchOverviewTiles(map)
            }
        }
    }

    private suspend fun fetchVisibleTiles(
        tileLayers: List<TileLayer>,
        map: MapState,
    ): Map<String, List<Tile>> =
        withContext(dispatcher) {
            buildMap {
                tileLayers.forEach { layer ->
                    put(layer.id, layer.loadTiles(map))
                }
            }
        }

    private suspend fun fetchOverviewTiles(
        tileLayers: List<TileLayer>,
        map: MapState,
    ): Map<String, List<Tile>> =
        withContext(dispatcher) {
            buildMap {
                tileLayers.forEach { layer ->
                    put(layer.id, layer.loadOverviewTiles(map))
                }
            }
        }

    private suspend fun decodeImages(
        tilesByLayer: Map<String, List<Tile>>,
        tileDecoder: ((ByteArray) -> ImageBitmap?)?,
    ): Map<String, List<ImageBitmap?>> =
        withContext(dispatcher) {
            buildMap {
                tilesByLayer.forEach { (layerId, tiles) ->
                    put(
                        layerId,
                        tiles.map { tile ->
                            decodeTile(tile.bytes, tileDecoder)
                        },
                    )
                }
            }
        }

    private fun decodeTile(
        bytes: ByteArray?,
        tileDecoder: ((ByteArray) -> ImageBitmap?)?,
    ): ImageBitmap? {
        if (bytes == null || tileDecoder == null) return null
        return try {
            tileDecoder(bytes)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            null
        }
    }
}
