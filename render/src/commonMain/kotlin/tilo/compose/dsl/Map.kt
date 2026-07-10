package tilo.compose.dsl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import tilo.compose.render.MapRenderer
import tilo.compose.render.backend.ComposeCanvasRenderBackend
import tilo.compose.render.backend.RenderBackend
import tilo.compose.core.feature.Feature
import tilo.compose.core.geometry.Point
import tilo.compose.core.layers.Layer
import tilo.compose.core.layers.LayerSink
import tilo.compose.core.layers.raster.TileLayer
import tilo.compose.core.layers.vector.FeatureLayer
import tilo.compose.core.layers.vector.VectorRenderStrategy
import tilo.compose.core.map.MapConfig
import tilo.compose.core.map.MapState
import tilo.compose.core.projection.IdentityProjection
import tilo.compose.core.projection.Projection

typealias FeatureRenderMode = VectorRenderStrategy

/**
 * Mutable camera holder used by [TiloMap].
 *
 * Create it with [rememberMapCameraState] and pass the same instance to the map
 * across recompositions. Coordinates are expressed in [projection].
 */
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

/**
 * Receiver for the `TiloMap { ... }` layer DSL.
 *
 * Prefer high-level methods such as [wmsTileLayer], [featureLayer], and
 * [rasterLayer]. Use [layer] or unary `+` when integrating a custom layer.
 */
class MapLayerBuilder : LayerSink {
    private val items = mutableListOf<Layer>()

    /**
     * Advanced escape hatch for custom or pre-built layers.
     */
    override fun layer(layer: Layer) {
        items += layer
    }

    /**
     * Advanced shorthand for adding a custom or pre-built layer.
     */
    operator fun Layer.unaryPlus() {
        layer(this)
    }

    /**
     * Adds a pre-built raster tile layer.
     */
    fun rasterLayer(layer: TileLayer?) {
        if (layer != null) {
            this.layer(layer)
        }
    }

    /**
     * Adds a WMS layer created by [rememberWMSLayer].
     *
     * The layer is skipped while capabilities are still loading or if loading
     * failed. Inspect [WMSLayerState.isLoading] and [WMSLayerState.error] for UI
     * feedback.
     */
    fun wmsTileLayer(state: WMSLayerState) {
        rasterLayer(state.layer)
    }

    /**
     * Advanced alias for adding a pre-built raster tile layer.
     */
    fun tileLayer(layer: TileLayer?) {
        rasterLayer(layer)
    }

    /**
     * Advanced alias for adding a WMS layer state.
     */
    fun tileLayer(state: WMSLayerState) {
        wmsTileLayer(state)
    }

    /**
     * Adds an in-memory vector feature layer.
     *
     * Use [projection] when feature coordinates differ from the map projection.
     * [renderMode] controls whether features are drawn immediately or cached to
     * a bitmap for smoother navigation on heavier layers.
     */
    fun featureLayer(
        id: String,
        features: List<Feature>,
        zIndex: Int = 0,
        projection: Projection? = null,
        renderMode: FeatureRenderMode = VectorRenderStrategy.Immediate,
    ) {
        layer(
            FeatureLayer(
                id = id,
                zIndex = zIndex,
                projection = projection,
                features = features,
                renderStrategy = renderMode,
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
            renderMode = options.renderMode,
        )
    }

    internal fun build(): List<Layer> = items.toList()
}

/**
 * Options for a vector layer declared with [MapLayerBuilder.featureLayer].
 */
class FeatureLayerOptions {
    var zIndex: Int = 0
    var projection: Projection? = null
    var renderMode: FeatureRenderMode = VectorRenderStrategy.Immediate
}

/**
 * Draw vector features directly every frame.
 */
fun immediate(): FeatureRenderMode = VectorRenderStrategy.Immediate

/**
 * Render vector features into an offscreen bitmap and reuse it while panning.
 *
 * This is useful for heavier, mostly static feature layers.
 */
fun cachedBitmap(
    scale: Double = 1.0,
    paddingPx: Int = 128,
    invalidateOnZoomDelta: Double = 0.35,
): FeatureRenderMode =
    VectorRenderStrategy.CachedBitmap(
        scale = scale,
        paddingPx = paddingPx,
        invalidateOnZoomDelta = invalidateOnZoomDelta,
    )

/**
 * Remembers camera state for [TiloMap].
 */
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

/**
 * Compose map surface.
 *
 * Declare raster, vector, drawing, and custom layers in [layers].
 */
@Composable
fun TiloMap(
    cameraState: MapCameraState,
    modifier: Modifier = Modifier,
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
        modifier = modifier,
        backend = backend,
        onTapWorld = onTapWorld,
        invalidationKey = invalidationKey,
    )
}
