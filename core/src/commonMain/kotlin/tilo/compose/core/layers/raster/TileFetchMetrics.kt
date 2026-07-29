package tilo.compose.core.layers.raster

/** Read-only snapshot of one raster runtime's in-memory tile-fetch activity. */
data class TileFetchMetrics(
    val cacheEntries: Int = 0,
    val maxCacheEntries: Int = 0,
    val cacheHits: Long = 0,
    val cacheMisses: Long = 0,
    val cacheEvictions: Long = 0,
    val sourceFetches: Long = 0,
    val coalescedRequests: Long = 0,
    val inFlightRequests: Int = 0,
    val succeeded: Long = 0,
    val missing: Long = 0,
    val failed: Long = 0,
    val suppressedByBackoff: Long = 0,
    val failureBackoffEntries: Int = 0,
)
