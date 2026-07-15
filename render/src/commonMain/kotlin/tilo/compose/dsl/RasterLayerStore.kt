@file:OptIn(ExperimentalTiloApi::class)

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
    private val retire: () -> Unit = {},
) {
    fun update(update: RasterLayerUpdate) {
        this.update.invoke(update)
    }

    fun retire() {
        retire.invoke()
        layer.close()
    }
}

internal sealed interface RasterLayerUpdate {
    data object None : RasterLayerUpdate

    class Source(
        val state: RasterLayerState?,
        val onError: ((Throwable) -> Unit)?,
    ) : RasterLayerUpdate

    class TileStore(
        val readTile: suspend (TileCoordinate) -> ByteArray?,
        val state: RasterLayerState?,
        val onError: ((Throwable) -> Unit)?,
    ) : RasterLayerUpdate
}

internal class MutableRasterLayerDiagnostics(
    state: RasterLayerState?,
    onError: ((Throwable) -> Unit)?,
) {
    private var state = state
    private var onError = onError
    private var phase: RasterLayerPhase = RasterLayerPhase.Idle

    fun update(
        state: RasterLayerState?,
        onError: ((Throwable) -> Unit)?,
    ) {
        if (this.state !== state) {
            this.state?.idle()
            this.state = state
            publishPhase()
        }
        this.onError = onError
    }

    fun loading() {
        if (phase == RasterLayerPhase.Retired) return
        phase = RasterLayerPhase.Loading
        state?.loading()
    }

    fun ready() {
        if (phase == RasterLayerPhase.Retired) return
        val enteringReady = phase != RasterLayerPhase.Ready
        phase = RasterLayerPhase.Ready
        state?.ready(clearTileError = enteringReady)
    }

    fun initializationFailed(error: Throwable) {
        if (phase == RasterLayerPhase.Retired) return
        phase = RasterLayerPhase.Failed(error)
        state?.initializationFailed(error)
        onError?.invoke(error)
    }

    fun tileFailed(error: Throwable) {
        if (phase == RasterLayerPhase.Retired) return
        state?.tileFailed(error)
        onError?.invoke(error)
    }

    fun retire() {
        if (phase == RasterLayerPhase.Retired) return
        phase = RasterLayerPhase.Retired
        val retiredState = state
        state = null
        onError = null
        retiredState?.idle()
    }

    private fun publishPhase() {
        when (val currentPhase = phase) {
            RasterLayerPhase.Idle,
            RasterLayerPhase.Retired,
            -> state?.idle()

            RasterLayerPhase.Loading -> state?.loading()
            RasterLayerPhase.Ready -> state?.ready(clearTileError = true)
            is RasterLayerPhase.Failed -> state?.initializationFailed(currentPhase.error)
        }
    }
}

private sealed interface RasterLayerPhase {
    data object Idle : RasterLayerPhase

    data object Loading : RasterLayerPhase

    data object Ready : RasterLayerPhase

    data class Failed(
        val error: Throwable,
    ) : RasterLayerPhase

    data object Retired : RasterLayerPhase
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
        val retiredKeys = layers.keys.filterNot(activeKeys::contains)
        retiredKeys.forEach { key ->
            layers.remove(key)?.retire()
        }
        activeKeys.forEach { key ->
            layers[key]?.update(updates[key] ?: RasterLayerUpdate.None)
        }
    }

    fun close() {
        layers.values.forEach(StoredRasterLayer::retire)
        layers.clear()
    }
}
