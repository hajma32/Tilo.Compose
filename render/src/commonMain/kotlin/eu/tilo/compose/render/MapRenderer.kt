package eu.tilo.compose.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import tilo.compose.core.feature.Feature
import tilo.compose.core.geometry.BoundingBox
import tilo.compose.core.map.Map
import tilo.compose.core.map.Viewport
import tilo.compose.core.projection.Projection
import tilo.compose.core.tile.Tile
import tilo.compose.core.layers.raster.TileLayer
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Compose-first map renderer (Skia-backed through Compose Canvas).
 *
 * Responsibilities: state wiring, LaunchedEffects, Modifier chain.
 * Rendering details are delegated to [drawTiles], [drawFeatures] and helpers.
 */
@Composable
fun MapRenderer(
    map: Map,
    features: List<Feature>,
    featuresSourceProjection: Projection? = null,
    tileLayer: TileLayer? = null,
    tileDecoder: ((ByteArray) -> ImageBitmap?)? = null,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    val boundsCache = remember { mutableMapOf<String, BoundingBox>() }
    val tileBitmapCache = remember { mutableMapOf<String, ImageBitmap?>() }
    val labelBitmapCache = remember { mutableMapOf<String, ImageBitmap>() }
    val offscreenLabelDrawScope = remember { CanvasDrawScope() }

    var stateVersion by remember { mutableStateOf(0) }
    var tiles by remember { mutableStateOf<List<Tile>>(emptyList()) }

    // ── Scene: build commands synchronously in the recompose pass ─────────────
    // No LaunchedEffect needed — build and map happen in one pass, no stale-closure risk.
    val projectedFeatures = transformFeaturesToMapProjection(features, featuresSourceProjection, map)
    val retained = CommandBuilder.build(map, projectedFeatures, boundsCache).associateBy { it.id }

    // ── Tile loading (debounced, deduplicated) ────────────────────────────────
    var lastTileKey by remember { mutableStateOf("") }

    LaunchedEffect(tileLayer, stateVersion, map.zoom, map.center) {
        if (tileLayer == null) {
            tiles = emptyList(); return@LaunchedEffect
        }
        val zoom = floor(map.zoom).toInt().coerceIn(0, 22)
        val cx = (map.center.x * 1000).roundToInt()
        val cy = (map.center.y * 1000).roundToInt()
        val key = "$zoom/$cx/$cy/${map.viewport.width}x${map.viewport.height}"
        if (key == lastTileKey) return@LaunchedEffect
        delay(60)
        lastTileKey = key
        tiles = withContext(Dispatchers.Default) { tileLayer.loadTiles(map) }
    }

    // ── Canvas ────────────────────────────────────────────────────────────────
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
            .mapGestureInput(map) { stateVersion++ }
    ) {
        if (tiles.isNotEmpty() && tileDecoder != null) {
            drawTiles(tiles, tileDecoder, tileBitmapCache, map)
        }
        drawFeatures(retained, labelBitmapCache, offscreenLabelDrawScope, textMeasurer)
    }
}
