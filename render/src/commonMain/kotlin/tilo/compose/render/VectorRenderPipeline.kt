@file:OptIn(ExperimentalTiloRenderingApi::class)

package tilo.compose.render

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tilo.compose.core.layers.vector.VectorLayer
import tilo.compose.core.layers.vector.VectorRenderStrategy
import tilo.compose.core.map.MapState
import tilo.compose.core.selection.FeatureSelectionRef
import tilo.compose.render.backend.VectorBitmapRenderSceneLayer
import tilo.compose.render.backend.VectorBitmapSnapshot
import kotlin.math.abs
import kotlin.math.pow

internal data class VectorFrame(
    val commandsByLayer: Map<String, List<RenderCommand>>,
    val bitmapLayersByLayer: Map<String, VectorBitmapRenderSceneLayer>,
    val cacheKeysByLayer: Map<String, VectorLayerCacheKey>,
    val metrics: VectorFrameMetrics = VectorFrameMetrics(),
)

internal data class VectorFrameMetrics(
    val returnedFeatures: Int = 0,
    val visibleFeatures: Int = 0,
    val geometryCommands: Int = 0,
    val labelCommands: Int = 0,
    val bitmapLayersReused: Int = 0,
    val bitmapLayersRebuilt: Int = 0,
)

internal data class VectorLayerCacheKey(
    val layerId: String,
    val sourceIdentity: Int,
    val sourceVersion: Long,
    val renderStrategy: VectorRenderStrategy,
    val styleHash: Int,
    val pointIconsHash: Int,
    val selectedFeatureKeys: Set<String>,
)

internal class VectorRenderPipeline(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val bitmapRenderer: VectorBitmapRenderTarget = VectorBitmapRenderer(),
) {
    suspend fun buildFrame(
        vectorLayers: List<VectorLayer>,
        map: MapState,
        density: Density,
        layoutDirection: LayoutDirection,
        selectedFeatures: Set<FeatureSelectionRef> = emptySet(),
        reusableBitmapsByLayer: Map<String, VectorBitmapRenderSceneLayer> = emptyMap(),
    ): VectorFrame =
        withContext(dispatcher) {
            val commandsByLayer = mutableMapOf<String, List<RenderCommand>>()
            val bitmapLayersByLayer = mutableMapOf<String, VectorBitmapRenderSceneLayer>()
            val cacheKeysByLayer = mutableMapOf<String, VectorLayerCacheKey>()
            var returnedFeatures = 0
            var visibleFeatures = 0
            var geometryCommands = 0
            var labelCommands = 0
            var bitmapLayersReused = 0
            var bitmapLayersRebuilt = 0

            vectorLayers.forEach { layer ->
                val selectedFeatureKeys = selectedFeatures.keysForLayer(layer.id)
                cacheKeysByLayer[layer.id] = layer.cacheKey(selectedFeatureKeys, map.zoom)
                val features = layer.source.getFeatures(map)
                returnedFeatures += features.size
                val projected = transformFeaturesToMapProjection(features, layer.projection, map)
                val buildResult =
                    CommandBuilder.buildWithMetrics(
                        map = map,
                        features = projected,
                        layerId = layer.id,
                        selectedFeatureKeys = selectedFeatureKeys,
                        layerStyle = layer.style,
                    )
                val commands = buildResult.commands
                visibleFeatures += buildResult.visibleFeatureCount
                geometryCommands += buildResult.geometryCommandCount
                labelCommands += buildResult.labelCommandCount

                when (val strategy = layer.renderStrategy) {
                    VectorRenderStrategy.Immediate -> {
                        commandsByLayer[layer.id] = commands
                    }

                    is VectorRenderStrategy.ImmediateLod -> {
                        // Temporary integration fallback until screen-space simplification is implemented.
                        commandsByLayer[layer.id] = commands
                    }

                    is VectorRenderStrategy.CachedBitmap -> {
                        val labels = commands.filterIsInstance<RenderLabel>()
                        val geometry = commands.filterNot { it is RenderLabel }
                        commandsByLayer[layer.id] = labels
                        val bitmapLayer =
                            reusableBitmapsByLayer[layer.id]
                                ?.takeIf { it.snapshot.canCover(map, strategy) }
                                ?.also { bitmapLayersReused += 1 }
                                ?: run {
                                    bitmapLayersRebuilt += 1
                                    bitmapRenderer.render(
                                        layer = layer,
                                        commands = geometry,
                                        map = map,
                                        strategy = strategy,
                                        density = density,
                                        layoutDirection = layoutDirection,
                                    )
                                }
                        bitmapLayer?.let {
                            bitmapLayersByLayer[layer.id] = bitmapLayer
                        }
                    }
                }
            }

            VectorFrame(
                commandsByLayer = commandsByLayer,
                bitmapLayersByLayer = bitmapLayersByLayer,
                cacheKeysByLayer = cacheKeysByLayer,
                metrics =
                    VectorFrameMetrics(
                        returnedFeatures = returnedFeatures,
                        visibleFeatures = visibleFeatures,
                        geometryCommands = geometryCommands,
                        labelCommands = labelCommands,
                        bitmapLayersReused = bitmapLayersReused,
                        bitmapLayersRebuilt = bitmapLayersRebuilt,
                    ),
            )
        }

    suspend fun buildCommands(
        vectorLayers: List<VectorLayer>,
        map: MapState,
        selectedFeatures: Set<FeatureSelectionRef> = emptySet(),
    ): Map<String, List<RenderCommand>> =
        withContext(dispatcher) {
            buildMap {
                vectorLayers.forEach { layer ->
                    val selectedFeatureKeys = selectedFeatures.keysForLayer(layer.id)
                    val features = layer.source.getFeatures(map)
                    val projected = transformFeaturesToMapProjection(features, layer.projection, map)
                    put(
                        layer.id,
                        CommandBuilder.build(
                            map = map,
                            features = projected,
                            layerId = layer.id,
                            selectedFeatureKeys = selectedFeatureKeys,
                            layerStyle = layer.style,
                        ),
                    )
                }
            }
        }
}

internal fun VectorLayer.cacheKey(
    selectedFeatureKeys: Set<String> = emptySet(),
    zoom: Double? = null,
): VectorLayerCacheKey =
    VectorLayerCacheKey(
        layerId = id,
        sourceIdentity = source.hashCode(),
        sourceVersion = source.version,
        renderStrategy = renderStrategy,
        styleHash = (zoom?.let(style::resolveAtZoom) ?: style).hashCode(),
        pointIconsHash = (this as? PointIconPainterLayer)?.pointIconPainters.orEmpty().hashCode(),
        selectedFeatureKeys = selectedFeatureKeys,
    )

internal fun VectorBitmapSnapshot.canCover(
    map: MapState,
    strategy: VectorRenderStrategy.CachedBitmap,
): Boolean {
    if (abs(map.zoom - zoom) > strategy.invalidateOnZoomDelta + ZOOM_COMPARISON_EPSILON) return false
    if (abs(map.bearing - bearing) > BEARING_COMPARISON_EPSILON) return false

    val anchor = map.worldToScreen(center)
    val scale = 2.0.pow(map.zoom - zoom)
    val halfWidth = displayWidth * scale / 2.0
    val halfHeight = displayHeight * scale / 2.0
    return anchor.x - halfWidth <= 0.0 &&
        anchor.y - halfHeight <= 0.0 &&
        anchor.x + halfWidth >= map.viewport.width &&
        anchor.y + halfHeight >= map.viewport.height
}

private const val ZOOM_COMPARISON_EPSILON = 1e-9
private const val BEARING_COMPARISON_EPSILON = 1e-9

private fun Set<FeatureSelectionRef>.keysForLayer(layerId: String): Set<String> =
    asSequence()
        .filter { it.layerId == layerId }
        .map { it.featureKey }
        .toSet()
