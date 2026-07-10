package eu.tilo.compose.render

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import eu.tilo.compose.render.backend.ComposeCanvasRenderBackend
import eu.tilo.compose.render.backend.RenderBackend
import tilo.compose.core.feature.Feature
import tilo.compose.core.geometry.Point
import tilo.compose.core.layers.Layer
import tilo.compose.core.layers.raster.TileLayer
import tilo.compose.core.layers.vector.FeatureLayer
import tilo.compose.core.layers.vector.VectorRenderStrategy
import tilo.compose.core.map.MapConfig
import tilo.compose.core.map.MapState
import tilo.compose.core.projection.IdentityProjection
import tilo.compose.core.projection.Projection

class MapCameraState internal constructor(
    internal val mapState: MapState,
) {
    val center: Point
        get() = mapState.center

    val zoom: Double
        get() = mapState.zoom

    val projection: Projection
        get() = mapState.projection

    val config: MapConfig
        get() = mapState.config
}

class MapLayerBuilder {
    private val items = mutableListOf<Layer>()

    fun layer(layer: Layer) {
        items += layer
    }

    operator fun Layer.unaryPlus() {
        layer(this)
    }

    fun tileLayer(layer: TileLayer?) {
        if (layer != null) {
            this.layer(layer)
        }
    }

    fun tileLayer(state: WMSTileLayerState) {
        tileLayer(state.layer)
    }

    fun featureLayer(
        id: String,
        features: List<Feature>,
        zIndex: Int = 0,
        projection: Projection? = null,
        renderStrategy: VectorRenderStrategy = VectorRenderStrategy.Immediate,
    ) {
        layer(
            FeatureLayer(
                id = id,
                zIndex = zIndex,
                projection = projection,
                features = features,
                renderStrategy = renderStrategy,
            )
        )
    }

    fun featureLayer(
        id: String,
        features: List<Feature>,
        block: FeatureLayerOptions.() -> Unit,
    ) {
        val options = FeatureLayerOptions().apply(block)
        featureLayer(
            id = id,
            features = features,
            zIndex = options.zIndex,
            projection = options.projection,
            renderStrategy = options.renderStrategy,
        )
    }

    internal fun build(): List<Layer> = items.toList()
}

class FeatureLayerOptions {
    var zIndex: Int = 0
    var projection: Projection? = null
    var renderStrategy: VectorRenderStrategy = VectorRenderStrategy.Immediate
}

fun immediate(): VectorRenderStrategy = VectorRenderStrategy.Immediate

fun cachedBitmap(
    scale: Double = 1.0,
    paddingPx: Int = 128,
    invalidateOnZoomDelta: Double = 0.35,
): VectorRenderStrategy =
    VectorRenderStrategy.CachedBitmap(
        scale = scale,
        paddingPx = paddingPx,
        invalidateOnZoomDelta = invalidateOnZoomDelta,
    )

@Composable
fun rememberMapCameraState(
    center: Point = Point(0.0, 0.0),
    zoom: Double = 0.0,
    projection: Projection = IdentityProjection,
    config: MapConfig = MapConfig.Default,
): MapCameraState =
    remember {
        MapCameraState(
            mapState = MapState(
                center = center,
                zoom = zoom,
                projection = projection,
                config = config,
            )
        )
    }

@Composable
fun TiloMap(
    cameraState: MapCameraState,
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
        map = cameraState.mapState,
        layers = layerBuilder.build(),
        tileDecoder = tileDecoder,
        modifier = modifier,
        backend = backend,
        onTapWorld = onTapWorld,
        invalidationKey = invalidationKey,
    )
}
