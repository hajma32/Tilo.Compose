package tilo.compose.render

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import tilo.compose.render.backend.VectorBitmapRenderSceneLayer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tilo.compose.core.layers.vector.VectorLayer
import tilo.compose.core.layers.vector.VectorRenderStrategy
import tilo.compose.core.selection.FeatureSelectionRef
import tilo.compose.core.map.Map as MapState

internal data class VectorFrame(
    val commandsByLayer: Map<String, List<RenderCommand>>,
    val bitmapLayersByLayer: Map<String, VectorBitmapRenderSceneLayer>,
    val cacheKeysByLayer: Map<String, VectorLayerCacheKey>,
)

internal data class VectorLayerCacheKey(
    val layerId: String,
    val sourceIdentity: Int,
    val sourceVersion: Long,
    val renderStrategy: VectorRenderStrategy,
    val styleHash: Int,
    val selectedFeatureKeys: Set<String>,
)

internal class VectorRenderPipeline(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val bitmapRenderer: VectorBitmapRenderer = VectorBitmapRenderer(),
) {
    suspend fun buildFrame(
        vectorLayers: List<VectorLayer>,
        map: MapState,
        density: Density,
        layoutDirection: LayoutDirection,
        selectedFeatures: Set<FeatureSelectionRef> = emptySet(),
    ): VectorFrame =
        withContext(dispatcher) {
            val commandsByLayer = mutableMapOf<String, List<RenderCommand>>()
            val bitmapLayersByLayer = mutableMapOf<String, VectorBitmapRenderSceneLayer>()
            val cacheKeysByLayer = mutableMapOf<String, VectorLayerCacheKey>()

            vectorLayers.forEach { layer ->
                val selectedFeatureKeys = selectedFeatures.keysForLayer(layer.id)
                cacheKeysByLayer[layer.id] = layer.cacheKey(selectedFeatureKeys)
                val features = layer.source.getFeatures(map)
                val projected = transformFeaturesToMapProjection(features, layer.projection, map)
                val commands = CommandBuilder.build(
                    map = map,
                    features = projected,
                    layerId = layer.id,
                    selectedFeatureKeys = selectedFeatureKeys,
                    layerStyle = layer.style,
                )

                when (val strategy = layer.renderStrategy) {
                    VectorRenderStrategy.Immediate -> {
                        commandsByLayer[layer.id] = commands
                    }

                    is VectorRenderStrategy.CachedBitmap -> {
                        val labels = commands.filterIsInstance<RenderLabel>()
                        val geometry = commands.filterNot { it is RenderLabel }
                        commandsByLayer[layer.id] = labels
                        bitmapRenderer.render(
                            layer = layer,
                            commands = geometry,
                            map = map,
                            strategy = strategy,
                            density = density,
                            layoutDirection = layoutDirection,
                        )?.let { bitmapLayer ->
                            bitmapLayersByLayer[layer.id] = bitmapLayer
                        }
                    }
                }
            }

            VectorFrame(
                commandsByLayer = commandsByLayer,
                bitmapLayersByLayer = bitmapLayersByLayer,
                cacheKeysByLayer = cacheKeysByLayer,
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
                        )
                    )
                }
            }
        }
}

internal fun VectorLayer.cacheKey(selectedFeatureKeys: Set<String> = emptySet()): VectorLayerCacheKey =
    VectorLayerCacheKey(
        layerId = id,
        sourceIdentity = source.hashCode(),
        sourceVersion = source.version,
        renderStrategy = renderStrategy,
        styleHash = style.hashCode(),
        selectedFeatureKeys = selectedFeatureKeys,
    )

private fun Set<FeatureSelectionRef>.keysForLayer(layerId: String): Set<String> =
    asSequence()
        .filter { it.layerId == layerId }
        .map { it.featureKey }
        .toSet()
