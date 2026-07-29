package tilo.compose.render

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tilo.compose.core.layers.raster.TileLayer
import tilo.compose.core.map.MapState
import tilo.compose.core.tile.Tile
import tilo.compose.core.tile.TileCoordinate

internal data class RasterFrame(
    val tilesByLayer: Map<String, List<Tile>>,
    val decodedImagesByLayer: Map<String, List<ImageBitmap?>>,
    val sourceIdentitiesByLayer: Map<String, Any> = emptyMap(),
) {
    companion object {
        val Empty =
            RasterFrame(
                tilesByLayer = emptyMap(),
                decodedImagesByLayer = emptyMap(),
                sourceIdentitiesByLayer = emptyMap(),
            )
    }
}

internal class RasterRenderPipeline(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    fun buildPlaceholderFrame(
        tileLayers: List<TileLayer>,
        map: MapState,
    ): RasterFrame =
        RasterFrame(
            tilesByLayer =
                buildMap {
                    tileLayers.forEach { layer ->
                        put(layer.id, layer.planTiles(map))
                    }
                },
            decodedImagesByLayer = emptyMap(),
            sourceIdentitiesByLayer = tileLayers.sourceIdentitiesByLayer(),
        )

    suspend fun buildVisibleFrame(
        tileLayers: List<TileLayer>,
        map: MapState,
        onDecodeFailure: (suspend (String, TileCoordinate, Throwable) -> Unit)? = null,
        tileDecoder: ((ByteArray) -> ImageBitmap?)?,
    ): RasterFrame {
        val tilesByLayer = fetchVisibleTiles(tileLayers, map)
        val decodedImagesByLayer = decodeImages(tilesByLayer, tileDecoder, onDecodeFailure)
        return RasterFrame(
            tilesByLayer = tilesByLayer,
            decodedImagesByLayer = decodedImagesByLayer,
            sourceIdentitiesByLayer = tileLayers.sourceIdentitiesByLayer(),
        )
    }

    suspend fun buildOverviewFrame(
        tileLayers: List<TileLayer>,
        map: MapState,
        onDecodeFailure: (suspend (String, TileCoordinate, Throwable) -> Unit)? = null,
        tileDecoder: ((ByteArray) -> ImageBitmap?)?,
    ): RasterFrame {
        val tilesByLayer = fetchOverviewTiles(tileLayers, map)
        val decodedImagesByLayer = decodeImages(tilesByLayer, tileDecoder, onDecodeFailure)
        return RasterFrame(
            tilesByLayer = tilesByLayer,
            decodedImagesByLayer = decodedImagesByLayer,
            sourceIdentitiesByLayer = tileLayers.sourceIdentitiesByLayer(),
        )
    }

    suspend fun prefetch(
        tileLayers: List<TileLayer>,
        map: MapState,
    ) {
        withContext(dispatcher) {
            tileLayers.forEach { layer ->
                layer.prefetchTiles(map)
            }
        }
    }

    suspend fun prefetchOverview(
        tileLayers: List<TileLayer>,
        map: MapState,
    ) {
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
        onDecodeFailure: (suspend (String, TileCoordinate, Throwable) -> Unit)?,
    ): Map<String, List<ImageBitmap?>> =
        withContext(dispatcher) {
            buildMap {
                tilesByLayer.forEach { (layerId, tiles) ->
                    put(
                        layerId,
                        tiles.map { tile ->
                            decodeTile(layerId, tile, tileDecoder, onDecodeFailure)
                        },
                    )
                }
            }
        }

    // Injected platform decoders have no shared exception type, so this boundary must isolate Exception.
    // Follow-up: Replace the nullable/throwing decoder API with a typed result and narrow this boundary.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun decodeTile(
        layerId: String,
        tile: Tile,
        tileDecoder: ((ByteArray) -> ImageBitmap?)?,
        onDecodeFailure: (suspend (String, TileCoordinate, Throwable) -> Unit)?,
    ): ImageBitmap? {
        val bytes = tile.bytes
        if (bytes == null || tileDecoder == null) return null
        val image =
            try {
                tileDecoder(bytes)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                reportDecodeFailure(onDecodeFailure, layerId, tile.coordinate, error)
                return null
            }
        if (image == null) {
            reportDecodeFailure(
                onDecodeFailure,
                layerId,
                tile.coordinate,
                TileImageDecodeException("Tile image decoder returned no image"),
            )
        }
        return image
    }

    private suspend fun reportDecodeFailure(
        callback: (suspend (String, TileCoordinate, Throwable) -> Unit)?,
        layerId: String,
        coordinate: TileCoordinate,
        error: Throwable,
    ) {
        try {
            callback?.invoke(layerId, coordinate, error)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Decode diagnostics are observational and must not fail the raster frame.
        }
    }
}

private class TileImageDecodeException(
    message: String,
) : IllegalArgumentException(message)

internal fun List<TileLayer>.sourceIdentitiesByLayer(): Map<String, Any> =
    associate { layer -> layer.id to layer.sourceIdentity }
