@file:OptIn(ExperimentalTiloApi::class)

package tilo.compose.dsl

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tilo.compose.core.layers.Attribution
import tilo.compose.core.layers.raster.RasterTileDiagnosticEvent
import tilo.compose.core.layers.raster.RasterTileFailure
import tilo.compose.core.layers.raster.RasterTileLayer
import tilo.compose.core.layers.raster.TileFetchMetrics
import tilo.compose.core.layers.raster.TileLayer
import tilo.compose.core.tile.TileCoordinate

/** Stable identity of a raster runtime created by the high-level map DSL. */
internal data class ManagedRasterLayerKey(
    val layerId: String,
    val configuration: Any,
)

internal class StoredRasterLayer(
    val layer: RasterTileLayer,
    val diagnostics: MutableRasterLayerDiagnostics? = null,
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
    private val localSource: Boolean,
) {
    constructor(
        state: RasterLayerState?,
        onError: ((Throwable) -> Unit)?,
    ) : this(state = state, onError = onError, localSource = false)

    private var state = state
    private var onError = onError
    private var phase: RasterLayerPhase = RasterLayerPhase.Idle
    private var snapshot = RasterLayerDiagnostics()
    private var availability = RasterLayerAvailability.Unknown
    private val mutex = Mutex()

    fun update(
        state: RasterLayerState?,
        onError: ((Throwable) -> Unit)?,
    ) {
        if (this.state !== state) {
            this.state?.idle()
            this.state = state
            publishPhase()
            publishDiagnostics()
            state?.publishTileError(snapshot.lastFailure?.cause)
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

    suspend fun onDiagnostic(event: RasterTileDiagnosticEvent) {
        mutex.withLock {
            if (phase == RasterLayerPhase.Retired) return
            when (event) {
                is RasterTileDiagnosticEvent.Failure -> {
                    snapshot = snapshot.copy(lastFailure = event.failure)
                    state?.publishTileError(event.failure.cause)
                }
                is RasterTileDiagnosticEvent.BatchCompleted -> {
                    val summary = event.summary
                    snapshot =
                        snapshot.copy(
                            requested = snapshot.requested + summary.requested,
                            succeeded = snapshot.succeeded + summary.succeeded,
                            missing = snapshot.missing + summary.missing,
                            failed = snapshot.failed + summary.failed,
                        )
                    if (summary.purpose == tilo.compose.core.layers.raster.RasterTileRequestPurpose.Visible) {
                        availability = nextAvailability(availability, summary, localSource)
                    }
                }
            }
            publishDiagnostics()
        }
    }

    suspend fun decodeFailed(
        failure: RasterTileFailure,
        affectsAvailability: Boolean,
    ) {
        val errorCallback =
            mutex.withLock {
                if (phase == RasterLayerPhase.Retired) return
                snapshot =
                    snapshot.copy(
                        decodeFailures = snapshot.decodeFailures + 1,
                        lastFailure = failure,
                    )
                if (affectsAvailability) availability = RasterLayerAvailability.Degraded
                publishDiagnostics()
                state?.publishTileError(failure.cause)
                onError
            }
        failure.cause?.let { error -> errorCallback?.invoke(error) }
    }

    suspend fun snapshot(): RasterLayerDiagnostics = mutex.withLock { snapshot }

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

    private fun publishDiagnostics() {
        state?.publishDiagnostics(snapshot, availability)
    }
}

private fun nextAvailability(
    current: RasterLayerAvailability,
    summary: tilo.compose.core.layers.raster.RasterTileBatchSummary,
    localSource: Boolean,
): RasterLayerAvailability =
    when {
        summary.failed > 0 && summary.succeeded > 0 -> RasterLayerAvailability.Degraded
        summary.failed > 0 && summary.networkFailures > 0 -> RasterLayerAvailability.Offline
        summary.failed > 0 -> RasterLayerAvailability.Degraded
        summary.succeeded > 0 -> RasterLayerAvailability.Available
        localSource && summary.requested > 0 && summary.missing == summary.requested -> RasterLayerAvailability.Empty
        summary.requested > 0 -> RasterLayerAvailability.Available
        else -> current
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
    override val opacity: Double,
    override val minZoom: Double?,
    override val maxZoom: Double?,
    override val attributions: List<Attribution>,
    private val diagnostics: MutableRasterLayerDiagnostics? = null,
) : TileLayer by runtime {
    init {
        require(opacity in 0.0..1.0) { "opacity must be between 0.0 and 1.0" }
        require(minZoom == null || maxZoom == null || minZoom <= maxZoom) { "minZoom must not be greater than maxZoom" }
    }

    suspend fun tileFetchMetrics(): TileFetchMetrics = runtime.tileFetchMetrics()

    suspend fun reportDecodeFailure(
        failure: RasterTileFailure,
        affectsAvailability: Boolean,
    ) {
        diagnostics?.decodeFailed(failure, affectsAvailability)
    }

    suspend fun rasterDiagnostics(): RasterLayerDiagnostics? = diagnostics?.snapshot()
}

internal suspend fun TileLayer.tileFetchMetricsOrNull(): TileFetchMetrics? =
    when (this) {
        is PresentedTileLayer -> tileFetchMetrics()
        is RasterTileLayer -> tileFetchMetrics()
        else -> null
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

    fun getOrCreateStored(
        key: ManagedRasterLayerKey,
        create: () -> StoredRasterLayer,
    ): StoredRasterLayer = layers.getOrPut(key, create)

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
