package tilo.compose.dsl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import tilo.compose.core.layers.raster.RasterTileFailure

/** Current data availability of a ready raster runtime. */
@ExperimentalTiloApi
enum class RasterLayerAvailability {
    Unknown,
    Available,
    Degraded,
    Offline,
    Empty,
}

/** Cumulative structured diagnostics for one attached raster runtime. */
@ExperimentalTiloApi
data class RasterLayerDiagnostics(
    val requested: Long = 0,
    val succeeded: Long = 0,
    val missing: Long = 0,
    val failed: Long = 0,
    val decodeFailures: Long = 0,
    val lastFailure: RasterTileFailure? = null,
)

/** Initialization state of a raster source declared in [TiloMap]. */
@ExperimentalTiloApi
sealed interface RasterLayerStatus {
    /** The state is not currently attached to a raster declaration. */
    data object Idle : RasterLayerStatus

    /** Source metadata or another prerequisite is being loaded. */
    data object Loading : RasterLayerStatus

    /** The raster runtime is ready. Individual tile requests may still fail. */
    data object Ready : RasterLayerStatus

    /** The raster runtime could not be initialized. */
    data class Failed(
        val error: Throwable,
    ) : RasterLayerStatus
}

/**
 * Optional observable state shared by all high-level raster layer declarations.
 *
 * Create one instance per layer with [rememberRasterLayerState] and pass it to
 * `wmsTileLayer`, `xyzTileLayer`, `osmLayer`, or `tileStoreLayer`. [status]
 * describes source initialization, [availability] distinguishes healthy,
 * degraded, offline, and empty data, and [diagnostics] contains structured
 * per-tile outcome counts. [lastTileError] remains as a compatibility view of
 * the latest failure cause and can be null for failures such as HTTP status.
 *
 * [retry] retires the current runtime and creates it again. For WMS this repeats
 * GetCapabilities; for other raster sources it clears the owned runtime/cache
 * and schedules fresh tile requests.
 */
@Stable
@ExperimentalTiloApi
class RasterLayerState internal constructor() {
    var status: RasterLayerStatus by mutableStateOf(RasterLayerStatus.Idle)
        private set

    var lastTileError: Throwable? by mutableStateOf(null)
        private set

    var availability: RasterLayerAvailability by mutableStateOf(RasterLayerAvailability.Unknown)
        private set

    var diagnostics: RasterLayerDiagnostics by mutableStateOf(RasterLayerDiagnostics())
        private set

    private var retryRevision by mutableIntStateOf(0)

    fun retry() {
        lastTileError = null
        availability = RasterLayerAvailability.Unknown
        diagnostics = RasterLayerDiagnostics()
        status = RasterLayerStatus.Idle
        retryRevision += 1
    }

    fun clearTileError() {
        lastTileError = null
    }

    internal val retryKey: Int
        get() = retryRevision

    internal fun loading() {
        lastTileError = null
        status = RasterLayerStatus.Loading
    }

    internal fun ready(clearTileError: Boolean = false) {
        if (clearTileError) {
            lastTileError = null
        }
        status = RasterLayerStatus.Ready
    }

    internal fun initializationFailed(error: Throwable) {
        lastTileError = null
        status = RasterLayerStatus.Failed(error)
    }

    internal fun tileFailed(error: Throwable) {
        lastTileError = error
    }

    internal fun publishDiagnostics(
        value: RasterLayerDiagnostics,
        availability: RasterLayerAvailability,
    ) {
        diagnostics = value
        this.availability = availability
    }

    internal fun publishTileError(error: Throwable?) {
        lastTileError = error
    }

    internal fun idle() {
        status = RasterLayerStatus.Idle
    }
}

/** Remembers observable state for one high-level raster layer declaration. */
@Composable
@ExperimentalTiloApi
fun rememberRasterLayerState(): RasterLayerState = remember { RasterLayerState() }
