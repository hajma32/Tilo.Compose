package eu.tilo.compose.render

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import eu.tilo.compose.render.backend.ComposeCanvasRenderBackend
import eu.tilo.compose.render.backend.RenderBackend
import tilo.compose.core.geometry.Point
import tilo.compose.core.layers.Layer
import tilo.compose.core.map.Map as MapState

class MapLayerBuilder {
    private val items = mutableListOf<Layer>()

    fun layer(layer: Layer) {
        items += layer
    }

    operator fun Layer.unaryPlus() {
        layer(this)
    }

    internal fun build(): List<Layer> = items.toList()
}

@Composable
fun Map(
    state: MapState,
    modifier: Modifier = Modifier,
    tileDecoder: ((ByteArray) -> ImageBitmap?)? = null,
    backend: RenderBackend = ComposeCanvasRenderBackend,
    onTapWorld: ((Point) -> Unit)? = null,
    invalidationKey: Any? = null,
    layers: MapLayerBuilder.() -> Unit,
) {
    val layerBuilder = MapLayerBuilder()
    layerBuilder.layers()
    MapRenderer(
        map = state,
        layers = layerBuilder.build(),
        tileDecoder = tileDecoder,
        modifier = modifier,
        backend = backend,
        onTapWorld = onTapWorld,
        invalidationKey = invalidationKey,
    )
}
