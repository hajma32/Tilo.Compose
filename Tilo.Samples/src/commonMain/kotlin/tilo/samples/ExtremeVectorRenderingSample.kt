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

private const val POINT_COUNT = 10_000
private const val LINE_COUNT = 300
private const val LINE_VERTEX_COUNT = 200
private const val POLYGON_COUNT = 500
private const val POLYGON_SEGMENT_COUNT = 60
private const val TOTAL_VERTEX_COUNT =
    POINT_COUNT + LINE_COUNT * LINE_VERTEX_COUNT + POLYGON_COUNT * (POLYGON_SEGMENT_COUNT + 1)

private const val WEST = 14.395
private const val EAST = 14.480
private const val SOUTH = 50.045
private const val NORTH = 50.105

@Composable
internal fun BoxScope.ExtremeVectorRenderingSample() {
    val camera = rememberWebMercatorCamera(zoom = 12.2)
    val dataset = remember { buildExtremeVectorDataset() }
    val performanceLogger = remember { ThrottledSamplePerformanceLogger() }

    TiloMap(
        cameraState = camera,
        modifier = Modifier.fillMaxSize(),
        renderPerformanceLogger = performanceLogger,
        layers = {
            featureLayer("extreme-points", dataset.points) {
                projection = wgs84()
                style =
                    featureLayerStyle {
                        point {
                            shape = PointShape.Circle
                            size = 3.dp
                            fill(0xCCF2663B)
                        }
                    }
            }
            featureLayer("extreme-lines", dataset.lines) {
                projection = wgs84()
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
            featureLayer("extreme-polygons", dataset.polygons) {
                projection = wgs84()
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
        sample = Sample.ExtremeVectorRendering,
        body =
            "10,000 points, 300 × 200-vertex lines and 500 × 61-vertex polygons. " +
                "Drag and zoom to exercise immediate rendering.",
        code = "$TOTAL_VERTEX_COUNT vertices · immediate mode",
    )
    DefaultMapDebugOverlay(
        cameraState = camera,
        alignment = Alignment.TopEnd,
    )
}

private data class ExtremeVectorDataset(
    val points: List<Feature>,
    val lines: List<Feature>,
    val polygons: List<Feature>,
)

private fun buildExtremeVectorDataset(): ExtremeVectorDataset =
    ExtremeVectorDataset(
        points = buildPointFeatures(),
        lines = buildLineFeatures(),
        polygons = buildPolygonFeatures(),
    )

private fun buildPointFeatures(): List<Feature> =
    List(POINT_COUNT) { index ->
        val column = index % 100
        val row = index / 100
        Feature(
            key = "stress-point-$index",
            geometry =
                Point(
                    x = WEST + (EAST - WEST) * column / 99.0,
                    y = SOUTH + (NORTH - SOUTH) * row / 99.0,
                ),
        )
    }

private fun buildLineFeatures(): List<Feature> =
    List(LINE_COUNT) { lineIndex ->
        val baseY = SOUTH + (NORTH - SOUTH) * (lineIndex + 0.5) / LINE_COUNT
        val phase = lineIndex * 0.37
        val amplitude = (NORTH - SOUTH) / LINE_COUNT * 0.35
        Feature(
            key = "stress-line-$lineIndex",
            geometry =
                LineString(
                    List(LINE_VERTEX_COUNT) { vertexIndex ->
                        val fraction = vertexIndex.toDouble() / (LINE_VERTEX_COUNT - 1)
                        Point(
                            x = WEST + (EAST - WEST) * fraction,
                            y = baseY + sin(fraction * PI * 8.0 + phase) * amplitude,
                        )
                    },
                ),
        )
    }

private fun buildPolygonFeatures(): List<Feature> =
    List(POLYGON_COUNT) { polygonIndex ->
        val column = polygonIndex % 25
        val row = polygonIndex / 25
        val centerX = WEST + (EAST - WEST) * (column + 0.5) / 25.0
        val centerY = SOUTH + (NORTH - SOUTH) * (row + 0.5) / 20.0
        val radiusX = (EAST - WEST) / 25.0 * 0.34
        val radiusY = (NORTH - SOUTH) / 20.0 * 0.34
        val ring =
            List(POLYGON_SEGMENT_COUNT + 1) { vertexIndex ->
                val angle = 2.0 * PI * (vertexIndex % POLYGON_SEGMENT_COUNT) / POLYGON_SEGMENT_COUNT
                Point(
                    x = centerX + cos(angle) * radiusX,
                    y = centerY + sin(angle) * radiusY,
                )
            }
        Feature(
            key = "stress-polygon-$polygonIndex",
            geometry = Polygon(rings = listOf(ring)),
        )
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
