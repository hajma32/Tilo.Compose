package tilo.compose.dsl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import tilo.compose.render.MapRenderer
import tilo.compose.render.backend.ComposeCanvasRenderBackend
import tilo.compose.render.backend.RenderBackend
import tilo.compose.core.feature.Feature
import tilo.compose.core.feature.FeatureLayerStyle
import tilo.compose.core.geometry.Point
import tilo.compose.core.layers.Attribution
import tilo.compose.core.layers.Layer
import tilo.compose.core.layers.LayerSink
import tilo.compose.core.layers.raster.RasterTileLayer
import tilo.compose.core.layers.raster.TileLayer
import tilo.compose.core.layers.raster.TileRowScheme
import tilo.compose.core.layers.raster.TileStoreTileSource
import tilo.compose.core.layers.raster.XYZTileLayer
import tilo.compose.core.layers.vector.FeatureLayer
import tilo.compose.core.layers.vector.VectorRenderStrategy
import tilo.compose.core.map.MapConfig
import tilo.compose.core.map.MapState
import tilo.compose.core.projection.Epsg3857Projection
import tilo.compose.core.projection.IdentityProjection
import tilo.compose.core.projection.Projection
import tilo.compose.core.selection.FeatureSelection
import tilo.compose.core.selection.FeatureSelectionRef
import tilo.compose.core.scale.ScaleBar
import tilo.compose.core.scale.ScaleBarCalculator
import tilo.compose.core.tile.TileCoordinate
import tilo.compose.core.tile.TileGrid

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
    internal var revision by mutableIntStateOf(0)
        private set

    internal var cameraControlRevision by mutableIntStateOf(0)
        private set

    val center: Point
        get() = mapState.center

    val zoom: Double
        get() = mapState.zoom

    val projection: Projection
        get() = mapState.projection

    val config: MapConfig
        get() = mapState.config

    /**
     * Zooms in by [step] map zoom levels around the current viewport center.
     */
    fun zoomIn(step: Double = 1.0) {
        zoomBy(step)
    }

    /**
     * Zooms out by [step] map zoom levels around the current viewport center.
     */
    fun zoomOut(step: Double = 1.0) {
        zoomBy(-step)
    }

    /**
     * Changes zoom by [delta] levels. Pass [focus] in screen pixels to zoom
     * around a particular point; omit it for centered UI controls.
     */
    fun zoomBy(delta: Double, focus: Point? = null) {
        val previousCenter = mapState.center
        val previousZoom = mapState.zoom
        mapState.zoomBy(delta = delta, focus = focus)
        if (mapState.center != previousCenter || mapState.zoom != previousZoom) {
            markChanged()
            cameraControlRevision += 1
        }
    }

    internal fun markChanged() {
        revision += 1
    }
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
     * Adds a URL-template raster layer using `{z}`, `{x}`, `{y}` placeholders.
     *
     * Web Mercator is the default because that is what public XYZ slippy-map
     * services normally use. Pass [projection] and [grid] for custom grids.
     */
    fun xyzTileLayer(
        id: String,
        urlTemplate: String,
        zIndex: Int = 0,
        projection: Projection = Epsg3857Projection,
        grid: TileGrid = TileGrid.defaultFor(projection),
        tms: Boolean = false,
        maxVisibleTiles: Int = 9,
        prefetchMargin: Int = 1,
        attribution: Attribution? = null,
        attributions: List<Attribution> = emptyList(),
    ) {
        layer(
            XYZTileLayer(
                id = id,
                projection = projection,
                grid = grid,
                urlTemplate = urlTemplate,
                tms = tms,
                zIndex = zIndex,
                maxVisibleTiles = maxVisibleTiles,
                prefetchMargin = prefetchMargin,
                attributions = attributions.withSingle(attribution),
            )
        )
    }

    /**
     * Adds a raster layer backed by an app-owned z/x/y tile store.
     *
     * The caller provides the tile reader so platform-specific SQLite access and
     * project-specific metadata stay outside the renderer. This supports
     * WebMercator, S-JTSK/Krovak, or any custom [projection] + [grid] pair.
     */
    fun tileStoreLayer(
        id: String,
        projection: Projection,
        grid: TileGrid,
        readTile: suspend (TileCoordinate) -> ByteArray?,
        zIndex: Int = 0,
        scheme: TileRowScheme = TileRowScheme.TMS,
        sourceId: String = id,
        maxVisibleTiles: Int = 9,
        prefetchMargin: Int = 1,
        attribution: Attribution? = null,
        attributions: List<Attribution> = emptyList(),
    ) {
        layer(
            RasterTileLayer(
                id = id,
                source = TileStoreTileSource(
                    projection = projection,
                    grid = grid,
                    scheme = scheme,
                    sourceId = sourceId,
                    readTile = readTile,
                ),
                zIndex = zIndex,
                maxVisibleTiles = maxVisibleTiles,
                prefetchMargin = prefetchMargin,
                attributions = attributions.withSingle(attribution),
            )
        )
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
        style: FeatureLayerStyle = FeatureLayerStyle(),
    ) {
        layer(
            FeatureLayer(
                id = id,
                zIndex = zIndex,
                projection = projection,
                features = features,
                renderStrategy = renderMode,
                style = style,
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
            style = options.style,
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
    var style: FeatureLayerStyle = FeatureLayerStyle()
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
    onFeatureSelect: ((List<FeatureSelection>) -> Unit)? = null,
    selectedFeatures: Set<FeatureSelectionRef> = emptySet(),
    attributionContent: (@Composable BoxScope.(List<Attribution>) -> Unit)? = null,
    scaleBarContent: (@Composable BoxScope.(ScaleBar) -> Unit)? = null,
    cameraControlsContent: (@Composable BoxScope.(MapCameraState) -> Unit)? = null,
    invalidationKey: Any? = null,
    layers: MapLayerBuilder.() -> Unit,
) {
    val layerBuilder = MapLayerBuilder()
    layerBuilder.layers()
    val builtLayers = layerBuilder.build()
    Box(modifier = modifier) {
        MapRendererLayer(
            cameraState = cameraState,
            layers = builtLayers,
            backend = backend,
            onTapWorld = onTapWorld,
            onFeatureSelect = onFeatureSelect,
            selectedFeatures = selectedFeatures,
            invalidationKey = invalidationKey,
        )
        if (scaleBarContent != null) {
            ScaleBarOverlay(
                cameraState = cameraState,
                content = scaleBarContent,
            )
        }
        if (attributionContent != null) {
            val attributions = builtLayers.attributions()
            if (attributions.isNotEmpty()) {
                attributionContent(attributions)
            }
        }
        if (cameraControlsContent != null) {
            cameraControlsContent(cameraState)
        }
    }
}

@Composable
private fun MapRendererLayer(
    cameraState: MapCameraState,
    layers: List<Layer>,
    backend: RenderBackend,
    onTapWorld: ((Point) -> Unit)?,
    onFeatureSelect: ((List<FeatureSelection>) -> Unit)?,
    selectedFeatures: Set<FeatureSelectionRef>,
    invalidationKey: Any?,
) {
    val cameraControlRevision = cameraState.cameraControlRevision
    MapRenderer(
        map = cameraState.mapState,
        layers = layers,
        modifier = Modifier.fillMaxSize(),
        backend = backend,
        onTapWorld = onTapWorld,
        onFeatureSelect = onFeatureSelect,
        selectedFeatures = selectedFeatures,
        invalidationKey = invalidationKey to cameraControlRevision,
        onMapChanged = cameraState::markChanged,
    )
}

@Composable
private fun BoxScope.ScaleBarOverlay(
    cameraState: MapCameraState,
    content: @Composable BoxScope.(ScaleBar) -> Unit,
) {
    cameraState.revision
    ScaleBarCalculator.calculate(cameraState.mapState)?.let { scaleBar ->
        content(scaleBar)
    }
}
