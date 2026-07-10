package eu.tilo.compose.render

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tilo.compose.core.layers.vector.VectorLayer
import tilo.compose.core.map.Map as MapState

internal class VectorRenderPipeline(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    suspend fun buildCommands(
        vectorLayers: List<VectorLayer>,
        map: MapState,
    ): Map<String, List<RenderCommand>> =
        withContext(dispatcher) {
            buildMap {
                vectorLayers.forEach { layer ->
                    val features = layer.source.getFeatures(map)
                    val projected = transformFeaturesToMapProjection(features, layer.projection, map)
                    put(layer.id, CommandBuilder.build(map, projected))
                }
            }
        }
}
