package eu.tilo.compose.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.ln
import tilo.compose.core.feature.Feature
import tilo.compose.core.geometry.Point
import tilo.compose.core.map.MapState
import tilo.compose.core.map.Viewport

/**
 * Compose-first map renderer (Skia-backed through Compose Canvas).
 * UI declares features; renderer builds commands, diffs scene and draws retained commands.
 */
@Composable
fun MapRenderer(
    mapState: MapState,
    features: List<Feature>,
    modifier: Modifier = Modifier
) {
    var retained by remember { mutableStateOf<Map<String, RenderCommand>>(emptyMap()) }
    var stateVersion by remember { mutableStateOf(0) }

    val current = CommandBuilder.build(mapState, features)
    val currentMap = current.associateBy { it.id }
    val ops = SceneDiff.diffMaps(retained, currentMap)

    LaunchedEffect(current, stateVersion) {
        retained = SceneDiff.apply(retained, ops)
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                mapState.viewport = Viewport(width = size.width, height = size.height)
                stateVersion++
            }
            .pointerInput(mapState) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    // Pan follows finger direction, while map center moves in opposite screen delta.
                    if (pan != Offset.Zero) {
                        mapState.panBy(-pan.x.toDouble(), -pan.y.toDouble())
                    }

                    // Gesture zoom is multiplicative; map zoomBy expects additive delta in log2 scale.
                    if (zoom > 0.0f && zoom != 1.0f) {
                        val zoomDelta = ln(zoom.toDouble()) / ln(2.0)
                        mapState.zoomBy(zoomDelta, Point(centroid.x.toDouble(), centroid.y.toDouble()))
                    }

                    stateVersion++
                }
            }
    ) {
        retained.values.forEach { command ->
            when (command) {
                is RenderPoint -> {
                    val fill = command.style.fillColor?.toColor() ?: Color(0xFF1E88E5)
                    drawCircle(
                        color = fill,
                        radius = command.radius.toFloat(),
                        center = Offset(command.point.x.toFloat(), command.point.y.toFloat())
                    )
                }

                is RenderLineString -> {
                    if (command.points.size < 2) return@forEach
                    val stroke = command.style.strokeColor?.toColor() ?: Color(0xFF1E88E5)
                    val width = (command.style.strokeWidth ?: 2.0).toFloat()
                    command.points.zipWithNext { a, b ->
                        drawLine(
                            color = stroke,
                            start = Offset(a.x.toFloat(), a.y.toFloat()),
                            end = Offset(b.x.toFloat(), b.y.toFloat()),
                            strokeWidth = width
                        )
                    }
                }

                is RenderPolygon -> {
                    val fill = command.style.fillColor?.toColor() ?: Color(0x331E88E5)
                    val stroke = command.style.strokeColor?.toColor() ?: Color(0xFF1E88E5)
                    val width = (command.style.strokeWidth ?: 1.5).toFloat()

                    val path = Path()
                    command.rings.forEach { ring ->
                        if (ring.isNotEmpty()) {
                            path.moveTo(ring.first().x.toFloat(), ring.first().y.toFloat())
                            ring.drop(1).forEach { p -> path.lineTo(p.x.toFloat(), p.y.toFloat()) }
                            path.close()
                        }
                    }

                    drawPath(path = path, color = fill)
                    drawPath(path = path, color = stroke, style = Stroke(width))
                }
            }
        }
    }
}

private fun Long.toColor(): Color = Color((this and 0xFFFFFFFFL).toInt())

