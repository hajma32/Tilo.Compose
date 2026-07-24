@file:OptIn(ExperimentalTiloRenderingApi::class)

package tilo.compose.render

import kotlin.math.round

/** Receives opt-in timing events from vector preparation and Canvas rendering. */
@ExperimentalTiloRenderingApi
fun interface RenderPerformanceLogger {
    /** Called on the thread that performed the measured work. Implementations should return quickly. */
    fun log(event: RenderPerformanceEvent)
}

/** Timing event emitted when [RenderPerformanceLogger] is configured. */
@ExperimentalTiloRenderingApi
sealed interface RenderPerformanceEvent {
    val totalMillis: Double

    fun toLogLine(): String
}

/** CPU timings for preparing one vector layer after a render request. */
@ExperimentalTiloRenderingApi
data class VectorLayerPerformanceEvent(
    val layerId: String,
    val featureCount: Int,
    val commandCount: Int,
    val vertexCount: Int,
    val queryMillis: Double,
    val projectionMillis: Double,
    val commandBuildMillis: Double,
    val bitmapMillis: Double,
    val reusedBitmap: Boolean,
    val queryCacheHit: Boolean = false,
    val projectedFeatureCacheHits: Int = 0,
    val commandCacheHit: Boolean = false,
    override val totalMillis: Double,
) : RenderPerformanceEvent {
    override fun toLogLine(): String =
        "vector layer=$layerId total=${totalMillis.ms()} query=${queryMillis.ms()} " +
            "projection=${projectionMillis.ms()} commands=${commandBuildMillis.ms()} " +
            "bitmap=${bitmapMillis.ms()} reusedBitmap=$reusedBitmap features=$featureCount " +
            "commandsCount=$commandCount vertices=$vertexCount queryCacheHit=$queryCacheHit " +
            "projectionCacheHits=$projectedFeatureCacheHits commandCacheHit=$commandCacheHit"
}

/** CPU timings for one Compose Canvas draw. GPU execution can continue after this event. */
@ExperimentalTiloRenderingApi
data class CanvasFramePerformanceEvent(
    val labelCount: Int,
    val placedLabelCount: Int,
    val vectorCommandCount: Int,
    val vectorStyleBatchCount: Int,
    val vectorRenderBatchCount: Int,
    val labelCollectionMillis: Double,
    val labelLayoutMillis: Double,
    val rasterDrawMillis: Double,
    val vectorDrawMillis: Double,
    val labelDrawMillis: Double,
    override val totalMillis: Double,
) : RenderPerformanceEvent {
    override fun toLogLine(): String =
        "canvasCpuRecord total=${totalMillis.ms()} labelCollect=${labelCollectionMillis.ms()} " +
            "labelLayout=${labelLayoutMillis.ms()} raster=${rasterDrawMillis.ms()} " +
            "vector=${vectorDrawMillis.ms()} labelDraw=${labelDrawMillis.ms()} " +
            "labels=$labelCount placedLabels=$placedLabelCount vectorCommands=$vectorCommandCount " +
            "vectorStyleBatches=$vectorStyleBatchCount vectorRenderBatches=$vectorRenderBatchCount"
}

private fun Double.ms(): String = "${round(this * 100.0) / 100.0}ms"
