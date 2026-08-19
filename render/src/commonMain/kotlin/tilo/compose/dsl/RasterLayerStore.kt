@file:OptIn(ExperimentalTiloApi::class)

package tilo.compose.dsl

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
    private val beforeDispatch: suspend () -> Unit = {},
    private val lock: RasterDiagnosticsLock = rasterDiagnosticsLock(),
) {
    constructor(
        state: RasterLayerState?,
        onError: ((Throwable) -> Unit)?,
    ) : this(state = state, onError = onError, localSource = false)

    // Application callbacks are claimed under this lock and invoked after releasing it. Retirement
    // prevents new claims without making Compose disposal wait for application code already running.

    private var lifecycle: RasterDiagnosticsLifecycle =
        RasterDiagnosticsLifecycle.Active(
            state = state,
            onError = onError,
            phase = RasterLayerPhase.Idle,
        )
    private var snapshot = RasterLayerDiagnostics()
    private var availability = RasterLayerAvailability.Unknown

    fun update(
        state: RasterLayerState?,
        onError: ((Throwable) -> Unit)?,
    ) = lock.withLock {
        val current = activeLifecycle() ?: return@withLock
        val updated = current.copy(state = state, onError = onError)
        lifecycle = updated
        if (current.state !== state) {
            current.state?.idle()
            publishPhase(updated)
            publishDiagnostics(updated)
        }
    }

    fun loading() =
        lock.withLock {
            val current = updatePhase(RasterLayerPhase.Loading) ?: return@withLock
            publishPhase(current)
            publishDiagnostics(current)
        }

    fun ready() =
        lock.withLock {
            val current = updatePhase(RasterLayerPhase.Ready) ?: return@withLock
            publishPhase(current)
            publishDiagnostics(current)
        }

    fun initializationFailed(error: Throwable) {
        val onError =
            lock.withLock {
                val current = updatePhase(RasterLayerPhase.Failed(error)) ?: return@withLock null
                publishPhase(current)
                current.onError.takeIf { isCurrent(current) }
            }
        onError?.invoke(error)
    }

    fun tileFailed(error: Throwable) {
        val onError = lock.withLock { activeLifecycle()?.onError }
        onError?.invoke(error)
    }

    suspend fun onDiagnostic(event: RasterTileDiagnosticEvent) {
        lock.withLock {
            if (activeLifecycle() == null) return@withLock
            when (event) {
                is RasterTileDiagnosticEvent.Failure -> {
                    snapshot = snapshot.copy(lastFailure = event.failure)
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
        }
        beforeDispatch()
        lock.withLock {
            activeLifecycle()?.let(::publishDiagnostics)
        }
    }

    suspend fun decodeFailed(
        failure: RasterTileFailure,
        affectsAvailability: Boolean,
    ) {
        lock.withLock {
            if (activeLifecycle() == null) return@withLock
            snapshot =
                snapshot.copy(
                    decodeFailures = snapshot.decodeFailures + 1,
                    lastFailure = failure,
                )
            if (affectsAvailability) availability = RasterLayerAvailability.Degraded
        }
        beforeDispatch()
        val onError =
            lock.withLock {
                val current = activeLifecycle() ?: return@withLock null
                publishDiagnostics(current)
                current.onError.takeIf { failure.cause != null && isCurrent(current) }
            }
        failure.cause?.let { error -> onError?.invoke(error) }
    }

    suspend fun snapshot(): RasterLayerDiagnostics = lock.withLock { snapshot }

    fun retire() =
        lock.withLock {
            val retired = lifecycle
            lifecycle = RasterDiagnosticsLifecycle.Retired
            if (retired is RasterDiagnosticsLifecycle.Active) retired.state?.idle()
        }

    private fun updatePhase(phase: RasterLayerPhase): RasterDiagnosticsLifecycle.Active? {
        val current = activeLifecycle() ?: return null
        val updated = current.copy(phase = phase)
        lifecycle = updated
        return updated
    }

    private fun activeLifecycle(): RasterDiagnosticsLifecycle.Active? = lifecycle as? RasterDiagnosticsLifecycle.Active

    private fun isCurrent(current: RasterDiagnosticsLifecycle.Active): Boolean = lifecycle === current

    private fun publishPhase(current: RasterDiagnosticsLifecycle.Active) {
        if (!isCurrent(current)) return
        when (val phase = current.phase) {
            RasterLayerPhase.Idle -> current.state?.idle()

            RasterLayerPhase.Loading -> current.state?.loading()
            RasterLayerPhase.Ready -> current.state?.ready()
            is RasterLayerPhase.Failed -> current.state?.initializationFailed(phase.error)
        }
    }

    private fun publishDiagnostics(current: RasterDiagnosticsLifecycle.Active) {
        if (!isCurrent(current)) return
        current.state?.publishDiagnostics(snapshot, availability)
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

private sealed interface RasterDiagnosticsLifecycle {
    data class Active(
        val state: RasterLayerState?,
        val onError: ((Throwable) -> Unit)?,
        val phase: RasterLayerPhase,
    ) : RasterDiagnosticsLifecycle

    data object Retired : RasterDiagnosticsLifecycle
}

private sealed interface RasterLayerPhase {
    data object Idle : RasterLayerPhase

    data object Loading : RasterLayerPhase

    data object Ready : RasterLayerPhase

    data class Failed(
        val error: Throwable,
    ) : RasterLayerPhase
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
