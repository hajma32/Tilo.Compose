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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tan
import tilo.compose.core.feature.Feature
import tilo.compose.core.geometry.LineString
import tilo.compose.core.geometry.MultiLineString
import tilo.compose.core.geometry.MultiPoint
import tilo.compose.core.geometry.MultiPolygon
import tilo.compose.core.geometry.Point
import tilo.compose.core.geometry.Polygon
import tilo.compose.core.layers.TileLayer
import tilo.compose.core.map.MapState
import tilo.compose.core.map.Viewport
import tilo.compose.core.tile.Tile
import tilo.compose.core.tile.source.Source
import tilo.compose.core.tile.source.WMSSource
import tilo.compose.core.projection.Projection

private const val TILE_GRID_SIDE = 3
private const val TILE_SIZE_PX = 256.0
private const val TILE_OVERFETCH_RING = 1
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
    mapState: MapState,
    features: List<Feature>,
    featuresSourceProjection: Projection? = null,
    modifier: Modifier = Modifier,
    tileLayer: TileLayer? = null,
    tileSource: Source? = null,
    tileCount: Int = 9,
    tileImageDecoder: ((ByteArray) -> ImageBitmap?)? = null
) {
    val density = LocalDensity.current
    var retained by remember { mutableStateOf<Map<String, RenderCommand>>(emptyMap()) }
    var stateVersion by remember { mutableStateOf(0) }
    var tiles by remember { mutableStateOf<List<Tile>>(emptyList()) }
    var lastTileRequestKey by remember { mutableStateOf<String?>(null) }
    val tileBitmapCache = remember { mutableMapOf<String, ImageBitmap?>() }
    val labelBitmapCache = remember { mutableMapOf<String, ImageBitmap>() }
    val offscreenLabelDrawScope = remember { CanvasDrawScope() }
    val textMeasurer = rememberTextMeasurer()

    val projectedFeatures = transformFeaturesToMapProjection(
        features = features,
        featuresSourceProjection = featuresSourceProjection,
        mapState = mapState
    )
    val current = CommandBuilder.build(mapState, projectedFeatures)
    val currentMap = current.associateBy { it.id }
    val ops = SceneDiff.diffMaps(retained, currentMap)

    LaunchedEffect(current, stateVersion) {
        retained = SceneDiff.apply(retained, ops)
    }

    LaunchedEffect(tileLayer, tileSource, stateVersion, mapState.zoom) {
        val source = tileLayer?.source ?: tileSource ?: run {
            tiles = emptyList()
            return@LaunchedEffect
        }

        // Small debounce prevents flood of network/decode work during fast gestures.
        delay(80)

        val zoomLevel = computeRenderTileZoom(mapState.zoom, mapState.viewport)
        val requestedTileCount = computeRequestedTileCount(
            zoom = mapState.zoom,
            viewport = mapState.viewport,
            tileZoomLevel = zoomLevel
        )

        val centerGlobal = lonLatToGlobalPixel(mapState.center.x, mapState.center.y, zoomLevel)
        val centerTileX = floor(centerGlobal.x / TILE_SIZE_PX).toInt()
        val centerTileY = floor(centerGlobal.y / TILE_SIZE_PX).toInt()
        val requestKey = listOf(
            zoomLevel,
            centerTileX,
            centerTileY,
            mapState.viewport.width,
            mapState.viewport.height,
            requestedTileCount
        ).joinToString(":")

        if (requestKey == lastTileRequestKey) return@LaunchedEffect
        lastTileRequestKey = requestKey

        tiles = withContext(Dispatchers.Default) {
            when {
                tileLayer != null -> {
                    val centerInLayerProjection = mapState.config.sourceToTarget(
                        point = mapState.center,
                        source = mapState.projection,
                        target = tileLayer.projection ?: mapState.projection
                    )
                    val requests = tileLayer.buildRequests(
                        zoomLevel = zoomLevel,
                        centerLon = centerInLayerProjection.x,
                        centerLat = centerInLayerProjection.y,
                        viewport = mapState.viewport,
                        tileCount = requestedTileCount
                    )
                    tileLayer.source.getTiles(requests)
                }

                source is WMSSource -> {
                    // WMSSource does not compute grid/BBOX. Without a TileLayer planner, return nothing.
                    emptyList()
                }

                else -> source.getTiles(
                    zoomLevel = zoomLevel,
                    viewport = mapState.viewport,
                    tileCount = requestedTileCount
                )
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                mapState.viewport = Viewport(
                    width = size.width,
                    height = size.height,
                    pixelRatio = density.density.toDouble()
                )
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
        drawTiles(
            tiles = tiles,
            mapState = mapState,
            tileBitmapCache = tileBitmapCache,
            decoder = tileImageDecoder
        )

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

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTiles(
    tiles: List<Tile>,
    mapState: MapState,
    tileBitmapCache: MutableMap<String, ImageBitmap?>,
    decoder: ((ByteArray) -> ImageBitmap?)?
) {
    val viewportWidth = mapState.viewport.width.toDouble()
    val viewportHeight = mapState.viewport.height.toDouble()
    if (viewportWidth <= 0.0 || viewportHeight <= 0.0) return

    val zTile = computeRenderTileZoom(mapState.zoom, mapState.viewport)
    val zoomScale = 2.0.pow(mapState.zoom - zTile)
    val screenSize = TILE_SIZE_PX * zoomScale

    // Draw all tiles intersecting the viewport plus one-ring overfetch buffer.
    val maxIndex = (2.0.pow(zTile.toDouble()).toInt() - 1).coerceAtLeast(0)
    val byCoord = tiles.associateBy { Triple(it.coordinate.z, it.coordinate.x, it.coordinate.y) }

    val centerGlobal = lonLatToGlobalPixel(mapState.center.x, mapState.center.y, zTile)
    val leftGlobal = centerGlobal.x - viewportWidth / (2.0 * zoomScale)
    val rightGlobal = centerGlobal.x + viewportWidth / (2.0 * zoomScale)
    val topGlobal = centerGlobal.y - viewportHeight / (2.0 * zoomScale)
    val bottomGlobal = centerGlobal.y + viewportHeight / (2.0 * zoomScale)

    val minTileX = floor(leftGlobal / TILE_SIZE_PX).toInt() - TILE_OVERFETCH_RING
    val maxTileX = floor(rightGlobal / TILE_SIZE_PX).toInt() + TILE_OVERFETCH_RING
    val minTileY = floor(topGlobal / TILE_SIZE_PX).toInt() - TILE_OVERFETCH_RING
    val maxTileY = floor(bottomGlobal / TILE_SIZE_PX).toInt() + TILE_OVERFETCH_RING

    for (rawY in minTileY..maxTileY) {
        for (rawX in minTileX..maxTileX) {
            val x = wrapTileX(rawX, zTile)
            val y = rawY.coerceIn(0, maxIndex)
            val tile = byCoord[Triple(zTile, x, y)]

            val tileGlobalX = x * TILE_SIZE_PX
            val tileGlobalY = y * TILE_SIZE_PX

            val screenX = (tileGlobalX - centerGlobal.x) * zoomScale + viewportWidth / 2.0
            val screenY = (tileGlobalY - centerGlobal.y) * zoomScale + viewportHeight / 2.0

            val tileKey = "$zTile/$x/$y"
            val image = tileBitmapCache[tileKey] ?: tile?.bytes?.let { bytes ->
                decoder?.invoke(bytes)
            }.also { decoded ->
                if (tile?.bytes != null) tileBitmapCache[tileKey] = decoded
            }

            if (image != null) {
                drawImage(
                    image = image,
                    dstOffset = IntOffset(screenX.toInt(), screenY.toInt()),
                    dstSize = IntSize(screenSize.toInt(), screenSize.toInt())
                )
            } else {
                val hasBytes = tile?.bytes != null
                val fill = if (hasBytes) Color(0xFFE3F2FD) else Color(0xFFDCE3EA)
                val border = if (hasBytes) Color(0xFF90CAF9) else Color(0xFFB0BEC5)

                drawRect(
                    color = fill,
                    topLeft = Offset(screenX.toFloat(), screenY.toFloat()),
                    size = Size(screenSize.toFloat(), screenSize.toFloat())
                )
                drawRect(
                    color = border,
                    topLeft = Offset(screenX.toFloat(), screenY.toFloat()),
                    size = Size(screenSize.toFloat(), screenSize.toFloat()),
                    style = Stroke(width = 1f)
                )
            }
        }
    }
}

private fun computeRequestedTileCount(zoom: Double, viewport: Viewport, tileZoomLevel: Int): Int {
    val zoomScale = 2.0.pow(zoom - tileZoomLevel)
    val tilesAcross = ceil(viewport.width / (TILE_SIZE_PX * zoomScale)).toInt().coerceAtLeast(1)
    val tilesDown = ceil(viewport.height / (TILE_SIZE_PX * zoomScale)).toInt().coerceAtLeast(1)
    val gridSide = max(
        TILE_GRID_SIDE,
        max(tilesAcross, tilesDown) + (2 * TILE_OVERFETCH_RING)
    )
    return gridSide * gridSide
}

private fun computeRenderTileZoom(zoom: Double, viewport: Viewport): Int {
    val roundedZoom = zoom.roundToInt().coerceAtLeast(0)
    val requiredScale = max(
        viewport.width / (TILE_GRID_SIDE * TILE_SIZE_PX),
        viewport.height / (TILE_GRID_SIDE * TILE_SIZE_PX)
    ).coerceAtLeast(1e-6)
    val maxUsableZoom = floor(zoom - (ln(requiredScale) / ln(2.0))).toInt()
    return min(roundedZoom, maxUsableZoom).coerceAtLeast(0)
}


private fun wrapTileX(x: Int, zoomLevel: Int): Int {
    val n = 2.0.pow(zoomLevel.toDouble()).toInt().coerceAtLeast(1)
    val mod = x % n
    return if (mod < 0) mod + n else mod
}

private fun lonLatToGlobalPixel(lon: Double, lat: Double, zoomLevel: Int): Point {
    val scale = TILE_SIZE_PX * 2.0.pow(zoomLevel.toDouble())
    val x = (lon + 180.0) / 360.0 * scale

    val clampedLat = lat.coerceIn(-85.05112878, 85.05112878)
    val latRad = clampedLat * PI / 180.0
    val y = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * scale

    return Point(x, y)
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
    mapState: MapState
): List<Feature> {
    val sourceProjection = featuresSourceProjection ?: return features
    if (sourceProjection === mapState.projection) return features

    return features.map { feature ->
        feature.copy(
            geometry = transformGeometry(
                geometry = feature.geometry,
                mapState = mapState,
                sourceProjection = sourceProjection
            )
        )
    }
}

private fun transformGeometry(
    geometry: tilo.compose.core.geometry.Geometry,
    mapState: MapState,
    sourceProjection: Projection
): tilo.compose.core.geometry.Geometry {
    fun transformPoint(p: Point): Point {
        return mapState.config.sourceToTarget(
            point = p,
            source = sourceProjection,
            target = mapState.projection
        )
    }

    return when (geometry) {
        is Point -> transformPoint(geometry)
        is MultiPoint -> MultiPoint(geometry.points.map(::transformPoint))
        is LineString -> LineString(geometry.points.map(::transformPoint))
        is MultiLineString -> MultiLineString(
            geometry.lines.map { line -> LineString(line.points.map(::transformPoint)) }
        )
        is Polygon -> Polygon(geometry.rings.map { ring -> ring.map(::transformPoint) })
        is MultiPolygon -> MultiPolygon(
            geometry.polygons.map { polygon ->
                Polygon(rings = polygon.rings.map { ring -> ring.map(::transformPoint) })
            }
        )
    }
}
