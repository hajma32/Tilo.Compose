package tilo.compose.dsl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import tilo.compose.core.layers.raster.RasterTileFailure

/** Fetch and decode diagnostics for one raster layer. */
data class MapRasterLayerMetrics(
    val succeeded: Long = 0,
    val missing: Long = 0,
    val failed: Long = 0,
    val suppressedByBackoff: Long = 0,
    val failureBackoffEntries: Int = 0,
    val decodeFailures: Long = 0,
    val lastFailure: RasterTileFailure? = null,
)

/** Aggregate tile-cache activity for the raster runtimes currently attached to a map. */
data class MapTileCacheMetrics(
    val entries: Int = 0,
    val maxEntries: Int = 0,
    val hits: Long = 0,
    val misses: Long = 0,
    val evictions: Long = 0,
    val sourceFetches: Long = 0,
    val coalescedRequests: Long = 0,
    val inFlightRequests: Int = 0,
)

/** Raster work represented by the latest diagnostic snapshot. */
data class MapTileMetrics(
    val planned: Int = 0,
    val loaded: Int = 0,
    val missing: Int = 0,
    val decoded: Int = 0,
    val displayed: Int = 0,
    val cache: MapTileCacheMetrics = MapTileCacheMetrics(),
    val layers: Map<String, MapRasterLayerMetrics> = emptyMap(),
)

/** Vector feature and cached-bitmap work represented by the latest vector frame. */
data class MapFeatureMetrics(
    val returned: Int = 0,
    val visible: Int = 0,
    val geometryCommands: Int = 0,
    val bitmapLayersReused: Int = 0,
    val bitmapLayersRebuilt: Int = 0,
)

/** Label placement and bitmap-cache activity represented by the latest canvas draw. */
data class MapLabelMetrics(
    val candidates: Int = 0,
    val placed: Int = 0,
    val rejected: Int = 0,
    val cacheEntries: Int = 0,
    val maxCacheEntries: Int = 0,
    val layoutHits: Long = 0,
    val layoutMisses: Long = 0,
    val bitmapHits: Long = 0,
    val bitmapMisses: Long = 0,
)

/** Point-in-time rendering diagnostics published by one map instance. */
data class MapRenderMetrics(
    val tiles: MapTileMetrics = MapTileMetrics(),
    val features: MapFeatureMetrics = MapFeatureMetrics(),
    val labels: MapLabelMetrics = MapLabelMetrics(),
)

/**
 * Observable diagnostics owned by one `TiloMap`.
 *
 * Passing this state to a map opts that map into render diagnostics. The default
 * debug overlay observes [metrics], and applications may use the same snapshots
 * in custom diagnostic UI.
 */
@Stable
class MapDiagnosticsState {
    var metrics: MapRenderMetrics by mutableStateOf(MapRenderMetrics())
        private set

    internal fun publishTiles(value: MapTileMetrics) {
        if (metrics.tiles != value) metrics = metrics.copy(tiles = value)
    }

    internal fun publishDisplayedTiles(displayed: Int) {
        if (metrics.tiles.displayed != displayed) {
            metrics = metrics.copy(tiles = metrics.tiles.copy(displayed = displayed))
        }
    }

    internal fun publishFeatures(value: MapFeatureMetrics) {
        if (metrics.features != value) metrics = metrics.copy(features = value)
    }

    internal fun publishLabels(value: MapLabelMetrics) {
        if (metrics.labels != value) metrics = metrics.copy(labels = value)
    }
}

/** Remembers diagnostics for one map and its debug overlay. */
@Composable
fun rememberMapDiagnosticsState(): MapDiagnosticsState = remember { MapDiagnosticsState() }
