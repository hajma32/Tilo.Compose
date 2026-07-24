@file:OptIn(ExperimentalTiloApi::class, ExperimentalTiloRenderingApi::class)

package tilo.samples

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tilo.compose.core.feature.Feature
import tilo.compose.core.feature.LineCap
import tilo.compose.core.feature.LineJoin
import tilo.compose.core.feature.PointShape
import tilo.compose.core.geometry.LineString
import tilo.compose.core.geometry.Point
import tilo.compose.core.geometry.Polygon
import tilo.compose.dsl.ExperimentalTiloApi
import tilo.compose.dsl.TiloMap
import tilo.compose.dsl.featureLayerStyle
import tilo.compose.dsl.immediateLod
import tilo.compose.dsl.wgs84
import tilo.compose.render.CanvasFramePerformanceEvent
import tilo.compose.render.ExperimentalTiloRenderingApi
import tilo.compose.render.RenderPerformanceEvent
import tilo.compose.render.RenderPerformanceLogger
import tilo.compose.render.VectorLayerPerformanceEvent
import tilo.compose.ui.DefaultMapDebugOverlay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

private data class VectorRenderingConfig(
    val idPrefix: String,
    val pointCount: Int,
    val lineCount: Int,
    val lineVertexCount: Int,
    val polygonCount: Int,
    val polygonSegmentCount: Int,
    val lodTolerancePx: Double? = null,
) {
    val totalVertexCount: Int
        get() = pointCount + lineCount * lineVertexCount + polygonCount * (polygonSegmentCount + 1)
}

private val EXTREME_CONFIG =
    VectorRenderingConfig("extreme", 10_000, 300, 200, 500, 60)

private val VECTOR_CONFIG =
    VectorRenderingConfig("vector", 5_000, 150, 200, 250, 60)

private val LOD_VECTOR_CONFIG =
    VectorRenderingConfig("lod-vector", 5_000, 150, 200, 250, 60, lodTolerancePx = 1.5)

private const val WEST = 14.395
private const val EAST = 14.480
private const val SOUTH = 50.045
private const val NORTH = 50.105

@Composable
internal fun BoxScope.ExtremeVectorRenderingSample() {
    VectorRenderingBenchmarkSample(
        sample = Sample.ExtremeVectorRendering,
        config = EXTREME_CONFIG,
        body = "10,000 points, 300 × 200-vertex lines and 500 × 61-vertex polygons.",
    )
}

@Composable
internal fun BoxScope.VectorRenderingSample() {
    VectorRenderingBenchmarkSample(
        sample = Sample.VectorRendering,
        config = VECTOR_CONFIG,
        body = "5,000 points, 150 × 200-vertex lines and 250 × 61-vertex polygons.",
    )
}

@Composable
internal fun BoxScope.LodVectorRenderingSample() {
    VectorRenderingBenchmarkSample(
        sample = Sample.LodVectorRendering,
        config = LOD_VECTOR_CONFIG,
        body =
            "5,000 points, 150 × 200-vertex lines and 250 × 61-vertex polygons " +
                "with zoom-dependent Douglas-Peucker LOD.",
    )
}

@Composable
private fun BoxScope.VectorRenderingBenchmarkSample(
    sample: Sample,
    config: VectorRenderingConfig,
    body: String,
) {
    val camera = rememberWebMercatorCamera(zoom = 12.2)
    val dataset = remember(config) { buildVectorDataset(config) }
    val performanceLogger = remember { ThrottledSamplePerformanceLogger() }

    TiloMap(
        cameraState = camera,
        modifier = Modifier.fillMaxSize(),
        renderPerformanceLogger = performanceLogger,
        layers = {
            featureLayer("${config.idPrefix}-points", dataset.points) {
                projection = wgs84()
                config.lodTolerancePx?.let { renderMode = immediateLod(it) }
                style =
                    featureLayerStyle {
                        point {
                            shape = PointShape.Circle
                            size = 3.dp
                            fill(0xCCF2663B)
                        }
                    }
            }
            featureLayer("${config.idPrefix}-lines", dataset.lines) {
                projection = wgs84()
                config.lodTolerancePx?.let { renderMode = immediateLod(it) }
                style =
                    featureLayerStyle {
                        line {
                            stroke(0xAA253E32, width = 1.dp) {
                                lineCap = LineCap.Round
                                lineJoin = LineJoin.Round
                            }
                        }
                    }
            }
            featureLayer("${config.idPrefix}-polygons", dataset.polygons) {
                projection = wgs84()
                config.lodTolerancePx?.let { renderMode = immediateLod(it) }
                style =
                    featureLayerStyle {
                        polygon {
                            fill(0x22BFED6F)
                            stroke(0x99253E32, width = 0.75.dp)
                        }
                    }
            }
        },
    )

    SampleInfoCard(
        sample = sample,
        body = "$body Drag and zoom to exercise immediate rendering.",
        code =
            config.lodTolerancePx?.let { tolerance ->
                "${config.totalVertexCount} source vertices · ImmediateLod ${tolerance}px"
            } ?: "${config.totalVertexCount} vertices · immediate mode",
    )
    DefaultMapDebugOverlay(
        cameraState = camera,
        alignment = Alignment.TopEnd,
    )
}

private data class VectorRenderingDataset(
    val points: List<Feature>,
    val lines: List<Feature>,
    val polygons: List<Feature>,
)

private fun buildVectorDataset(config: VectorRenderingConfig): VectorRenderingDataset =
    VectorRenderingDataset(
        points = buildPointFeatures(config),
        lines = buildLineFeatures(config),
        polygons = buildPolygonFeatures(config),
    )

private fun buildPointFeatures(config: VectorRenderingConfig): List<Feature> {
    val columnCount = 100
    val rowCount = (config.pointCount + columnCount - 1) / columnCount
    return List(config.pointCount) { index ->
        val column = index % columnCount
        val row = index / columnCount
        Feature(
            key = "${config.idPrefix}-point-$index",
            geometry =
                Point(
                    x = WEST + (EAST - WEST) * column / (columnCount - 1).toDouble(),
                    y = SOUTH + (NORTH - SOUTH) * row / (rowCount - 1).coerceAtLeast(1).toDouble(),
                ),
        )
    }
}

private fun buildLineFeatures(config: VectorRenderingConfig): List<Feature> =
    List(config.lineCount) { lineIndex ->
        val baseY = SOUTH + (NORTH - SOUTH) * (lineIndex + 0.5) / config.lineCount
        val phase = lineIndex * 0.37
        val amplitude = (NORTH - SOUTH) / config.lineCount * 0.35
        Feature(
            key = "${config.idPrefix}-line-$lineIndex",
            geometry =
                LineString(
                    List(config.lineVertexCount) { vertexIndex ->
                        val fraction = vertexIndex.toDouble() / (config.lineVertexCount - 1).coerceAtLeast(1)
                        Point(
                            x = WEST + (EAST - WEST) * fraction,
                            y = baseY + sin(fraction * PI * 8.0 + phase) * amplitude,
                        )
                    },
                ),
        )
    }

private fun buildPolygonFeatures(config: VectorRenderingConfig): List<Feature> {
    val columnCount = 25
    val rowCount = (config.polygonCount + columnCount - 1) / columnCount
    return List(config.polygonCount) { polygonIndex ->
        val column = polygonIndex % columnCount
        val row = polygonIndex / columnCount
        val centerX = WEST + (EAST - WEST) * (column + 0.5) / columnCount
        val centerY = SOUTH + (NORTH - SOUTH) * (row + 0.5) / rowCount
        val radiusX = (EAST - WEST) / columnCount * 0.34
        val radiusY = (NORTH - SOUTH) / rowCount * 0.34
        val ring =
            List(config.polygonSegmentCount + 1) { vertexIndex ->
                val angle =
                    2.0 * PI * (vertexIndex % config.polygonSegmentCount) / config.polygonSegmentCount
                Point(
                    x = centerX + cos(angle) * radiusX,
                    y = centerY + sin(angle) * radiusY,
                )
            }
        Feature(
            key = "${config.idPrefix}-polygon-$polygonIndex",
            geometry = Polygon(rings = listOf(ring)),
        )
    }
}

private class ThrottledSamplePerformanceLogger : RenderPerformanceLogger {
    private val vectorMarks = mutableMapOf<String, TimeMark>()
    private var canvasMark: TimeMark? = null

    override fun log(event: RenderPerformanceEvent) {
        when (event) {
            is VectorLayerPerformanceEvent -> {
                val previous = vectorMarks[event.layerId]
                if (previous == null || previous.elapsedNow() >= LOG_INTERVAL) {
                    vectorMarks[event.layerId] = TimeSource.Monotonic.markNow()
                    printEvent(event)
                }
            }

            is CanvasFramePerformanceEvent -> {
                val previous = canvasMark
                if (previous == null || previous.elapsedNow() >= LOG_INTERVAL) {
                    canvasMark = TimeSource.Monotonic.markNow()
                    printEvent(event)
                }
            }
        }
    }

    private fun printEvent(event: RenderPerformanceEvent) {
        println("[TiloPerf] ${event.toLogLine()}")
    }

    private companion object {
        val LOG_INTERVAL = 1.seconds
    }
}
