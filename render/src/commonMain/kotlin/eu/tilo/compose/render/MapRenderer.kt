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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas as GraphicsCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import tilo.compose.core.feature.Feature
import tilo.compose.core.geometry.LineString
import tilo.compose.core.geometry.MultiLineString
import tilo.compose.core.geometry.MultiPoint
import tilo.compose.core.geometry.MultiPolygon
import tilo.compose.core.geometry.Point
import tilo.compose.core.geometry.Polygon
import tilo.compose.core.map.Map
import tilo.compose.core.map.Viewport
import tilo.compose.core.projection.Projection
import kotlin.math.ln

private const val LABEL_VERTICAL_PADDING_PX = 8f
private const val LABEL_HALO_RADIUS_PX = 1f
private const val LABEL_BITMAP_PADDING_PX = 2

/**
 * Compose-first map renderer (Skia-backed through Compose Canvas).
 * UI declares features; renderer builds commands, diffs scene and draws retained commands.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun MapRenderer(
    map: Map,
    features: List<Feature>,
    featuresSourceProjection: Projection? = null,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var retained by remember { mutableStateOf<kotlin.collections.Map<String, RenderCommand>>(emptyMap()) }
    var stateVersion by remember { mutableStateOf(0) }
    val labelBitmapCache = remember { mutableMapOf<String, ImageBitmap>() }
    val offscreenLabelDrawScope = remember { CanvasDrawScope() }
    val textMeasurer = rememberTextMeasurer()

    val projectedFeatures = transformFeaturesToMapProjection(
        features = features,
        featuresSourceProjection = featuresSourceProjection,
        map = map
    )
    val current = CommandBuilder.build(map, projectedFeatures)
    val currentMap = current.associateBy { it.id }
    val ops = SceneDiff.diffMaps(retained, currentMap)

    LaunchedEffect(current, stateVersion) {
        retained = SceneDiff.apply(retained, ops)
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                map.viewport = Viewport(
                    width = size.width,
                    height = size.height,
                    pixelRatio = density.density.toDouble()
                )
                stateVersion++
            }
            .pointerInput(map) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    // Pan follows finger direction, while map center moves in opposite screen delta.
                    if (pan != Offset.Zero) {
                        map.panBy(-pan.x.toDouble(), -pan.y.toDouble())
                    }

                    // Gesture zoom is multiplicative; map zoomBy expects additive delta in log2 scale.
                    if (zoom > 0.0f && zoom != 1.0f) {
                        val zoomDelta = ln(zoom.toDouble()) / ln(2.0)
                        map.zoomBy(zoomDelta, Point(centroid.x.toDouble(), centroid.y.toDouble()))
                    }

                    stateVersion++
                }
            }
    ) {
        retained.values.forEach { command ->
            when (command) {
                is RenderPoint -> {
                    if (command.style.fillColor == 0x00000000L) return@forEach
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

                is RenderLabel -> {
                    val labelColor = command.style.strokeColor?.toColor() ?: Color(0xFF111827)
                    val labelBitmap = getOrCreateLabelBitmap(
                        text = command.text,
                        textColor = labelColor,
                        textMeasurer = textMeasurer,
                        cache = labelBitmapCache,
                        offscreenDrawScope = offscreenLabelDrawScope
                    )
                    val topLeft = Offset(
                        x = command.anchor.x.toFloat() - (labelBitmap.width / 2f),
                        y = command.anchor.y.toFloat() + LABEL_VERTICAL_PADDING_PX
                    )
                    drawImage(
                        image = labelBitmap,
                        dstOffset = IntOffset(topLeft.x.toInt(), topLeft.y.toInt()),
                        dstSize = IntSize(labelBitmap.width, labelBitmap.height)
                    )
                }
            }
        }
    }
}

private fun Long.toColor(): Color = Color((this and 0xFFFFFFFFL).toInt())

private fun androidx.compose.ui.graphics.drawscope.DrawScope.getOrCreateLabelBitmap(
    text: String,
    textColor: Color,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    cache: MutableMap<String, ImageBitmap>,
    offscreenDrawScope: CanvasDrawScope
): ImageBitmap {
    val cacheKey = "$text|${textColor.toArgb()}"
    cache[cacheKey]?.let { return it }

    val labelStyle = TextStyle(color = textColor, fontSize = 12.sp)
    val textLayout = textMeasurer.measure(text = text, style = labelStyle)

    val haloPadding = LABEL_BITMAP_PADDING_PX + LABEL_HALO_RADIUS_PX.toInt()
    val width = (textLayout.size.width + haloPadding * 2).coerceAtLeast(1)
    val height = (textLayout.size.height + haloPadding * 2).coerceAtLeast(1)

    val bitmap = ImageBitmap(width, height)
    val canvas = GraphicsCanvas(bitmap)
    val baseTopLeft = Offset(haloPadding.toFloat(), haloPadding.toFloat())
    val haloStyle = labelStyle.copy(color = Color.White)
    val haloOffsets = arrayOf(
        Offset(-LABEL_HALO_RADIUS_PX, 0f),
        Offset(LABEL_HALO_RADIUS_PX, 0f),
        Offset(0f, -LABEL_HALO_RADIUS_PX),
        Offset(0f, LABEL_HALO_RADIUS_PX),
        Offset(-LABEL_HALO_RADIUS_PX, -LABEL_HALO_RADIUS_PX),
        Offset(LABEL_HALO_RADIUS_PX, -LABEL_HALO_RADIUS_PX),
        Offset(-LABEL_HALO_RADIUS_PX, LABEL_HALO_RADIUS_PX),
        Offset(LABEL_HALO_RADIUS_PX, LABEL_HALO_RADIUS_PX)
    )

    offscreenDrawScope.draw(
        density = this,
        layoutDirection = layoutDirection,
        canvas = canvas,
        size = Size(width.toFloat(), height.toFloat())
    ) {
        haloOffsets.forEach { offset ->
            drawText(
                textMeasurer = textMeasurer,
                text = text,
                topLeft = baseTopLeft + offset,
                style = haloStyle
            )
        }
        drawText(
            textMeasurer = textMeasurer,
            text = text,
            topLeft = baseTopLeft,
            style = labelStyle
        )
    }

    cache[cacheKey] = bitmap
    return bitmap
}

private fun transformFeaturesToMapProjection(
    features: List<Feature>,
    featuresSourceProjection: Projection?,
    map: Map
): List<Feature> {
    val sourceProjection = featuresSourceProjection ?: return features
    if (sourceProjection === map.projection) return features

    return features.map { feature ->
        feature.copy(
            geometry = transformGeometry(
                geometry = feature.geometry,
                map = map,
                sourceProjection = sourceProjection
            )
        )
    }
}

private fun transformGeometry(
    geometry: tilo.compose.core.geometry.Geometry,
    map: Map,
    sourceProjection: Projection
): tilo.compose.core.geometry.Geometry {
    fun tp(p: Point) = map.transformSourceToTarget(p, sourceProjection, map.projection)

    return when (geometry) {
        is Point -> tp(geometry)
        is MultiPoint -> MultiPoint(geometry.points.map(::tp))
        is LineString -> LineString(geometry.points.map(::tp))
        is MultiLineString -> MultiLineString(geometry.lines.map { LineString(it.points.map(::tp)) })
        is Polygon -> Polygon(geometry.rings.map { ring -> ring.map(::tp) })
        is MultiPolygon -> MultiPolygon(geometry.polygons.map { Polygon(it.rings.map { ring -> ring.map(::tp) }) })
    }
}
