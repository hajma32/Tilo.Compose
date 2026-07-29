package tilo.compose.core.layers.raster

import tilo.compose.core.tile.TileCoordinate

/** Stable category of a recoverable raster tile failure. */
enum class RasterTileFailureKind {
    NetworkUnavailable,
    HttpStatus,
    InvalidPayload,
    Decode,
    Source,
}

/**
 * Structured debug details for one recoverable raster tile failure.
 *
 * `message` and `cause` may contain low-level transport or source details and
 * should not be shown directly as user-facing copy.
 */
data class RasterTileFailure(
    val kind: RasterTileFailureKind,
    val coordinate: TileCoordinate,
    val message: String,
    val httpStatus: Int? = null,
    val cause: Throwable? = null,
)

/** Outcome counts for one tile batch requested by a raster consumer. */
data class RasterTileBatchSummary(
    val purpose: RasterTileRequestPurpose = RasterTileRequestPurpose.Visible,
    val requested: Int,
    val succeeded: Int,
    val missing: Int,
    val failed: Int,
    val networkFailures: Int = 0,
)

/** Rendering purpose of a raster tile batch. */
enum class RasterTileRequestPurpose {
    Visible,
    Overview,
    Prefetch,
}

/** Diagnostic events emitted by a raster runtime without interrupting healthy tiles. */
sealed interface RasterTileDiagnosticEvent {
    data class Failure(
        val failure: RasterTileFailure,
    ) : RasterTileDiagnosticEvent

    data class BatchCompleted(
        val summary: RasterTileBatchSummary,
    ) : RasterTileDiagnosticEvent
}

/** Detailed result used by built-in raster sources and diagnostic transports. */
sealed interface TileReadResult {
    /** Encoded image bytes were loaded. */
    class Success(
        val bytes: ByteArray,
    ) : TileReadResult

    /** The source has no tile for this coordinate. */
    data object Missing : TileReadResult

    /** The request failed in a classified, recoverable way. */
    data class Failure(
        val kind: RasterTileFailureKind,
        val message: String,
        val httpStatus: Int? = null,
        val cause: Throwable? = null,
    ) : TileReadResult
}

/** Raster source that preserves missing and failure reasons for diagnostics. */
interface DiagnosticRasterTileSource : RasterTileSource {
    suspend fun readTileResult(request: tilo.compose.core.tile.TileRequest): TileReadResult

    override suspend fun readTile(request: tilo.compose.core.tile.TileRequest): ByteArray? =
        when (val result = readTileResult(request)) {
            is TileReadResult.Success -> result.bytes
            TileReadResult.Missing,
            is TileReadResult.Failure,
            -> null
        }
}
