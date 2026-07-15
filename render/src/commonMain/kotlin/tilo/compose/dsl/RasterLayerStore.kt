package tilo.compose.dsl

import tilo.compose.core.layers.Attribution
import tilo.compose.core.layers.raster.RasterTileLayer
import tilo.compose.core.layers.raster.TileLayer
import tilo.compose.core.tile.TileCoordinate

/** Stable identity of a raster runtime created by the high-level map DSL. */
internal data class ManagedRasterLayerKey(
    val layerId: String,
    val configuration: Any,
)

internal class StoredRasterLayer(
    val layer: RasterTileLayer,
    private val update: (RasterLayerUpdate) -> Unit = {},
) {
    fun update(update: RasterLayerUpdate) {
        this.update.invoke(update)
    }
}

internal sealed interface RasterLayerUpdate {
    data object None : RasterLayerUpdate

    class TileReader(
        val readTile: suspend (TileCoordinate) -> ByteArray?,
    ) : RasterLayerUpdate
}

/** Lightweight presentation snapshot backed by a stable fetch/cache runtime. */
internal data class PresentedTileLayer(
    private val runtime: RasterTileLayer,
    override val id: String,
    override val zIndex: Int,
    override val visible: Boolean,
    override val minZoom: Double?,
    override val maxZoom: Double?,
    override val attributions: List<Attribution>,
) : TileLayer by runtime {
    init {
        require(minZoom == null || maxZoom == null || minZoom <= maxZoom) { "minZoom must not be greater than maxZoom" }
    }
}

/**
 * Owns raster runtimes across recompositions of one TiloMap instance.
 *
 * Entries are retired only from a Compose SideEffect after a successful
 * composition. An abandoned composition therefore cannot close layers still
 * used by the currently rendered tree.
 */
internal class RasterLayerStore {
    private val layers = mutableMapOf<ManagedRasterLayerKey, StoredRasterLayer>()

    fun getOrCreate(
        key: ManagedRasterLayerKey,
        create: () -> StoredRasterLayer,
    ): RasterTileLayer = layers.getOrPut(key, create).layer

    fun retain(
        activeKeys: Set<ManagedRasterLayerKey>,
        updates: Map<ManagedRasterLayerKey, RasterLayerUpdate> = emptyMap(),
    ) {
        activeKeys.forEach { key ->
            layers[key]?.update(updates[key] ?: RasterLayerUpdate.None)
        }
        val retiredKeys = layers.keys.filterNot(activeKeys::contains)
        retiredKeys.forEach { key ->
            layers.remove(key)?.layer?.close()
        }
    }

    fun close() {
        layers.values.forEach { stored -> stored.layer.close() }
        layers.clear()
    }
}
