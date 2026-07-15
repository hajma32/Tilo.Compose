@file:OptIn(ExperimentalTiloApi::class, ExperimentalTiloRenderingApi::class)

package tilo.compose.dsl

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tilo.compose.core.feature.Feature
import tilo.compose.core.feature.FeatureLayerStyle
import tilo.compose.core.geometry.BoundingBox
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
import tilo.compose.core.map.MapCameraController
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
import tilo.compose.render.ExperimentalTiloRenderingApi
import tilo.compose.render.MapRenderer

/**
 * Immutable camera snapshot suitable for viewport-dependent data loading.
 *
 * [bounds] and [resolution] use the map projection's coordinate units. A snapshot is not ready
 * until the map has received a non-empty viewport from layout.
 */
@ExperimentalTiloApi
data class MapViewportSnapshot(
    val bounds: BoundingBox,
    val zoom: Double,
    val viewportWidth: Int,
    val viewportHeight: Int,
    val resolution: Double,
) {
    val isReady: Boolean
        get() = viewportWidth > 0 && viewportHeight > 0
}

/**
 * Mutable camera holder used by [TiloMap].
 *
 * Create it with [rememberMapCameraState] and pass the same instance to the map
 * across recompositions. Coordinates are expressed in [projection].
 */
@ExperimentalTiloApi
class MapCameraState internal constructor(
    internal val mapState: MapState,
) : MapCameraController {
    internal var revision by mutableIntStateOf(0)
        private set

    internal var cameraControlRevision by mutableIntStateOf(0)
        private set

    internal var zoomRevision by mutableIntStateOf(0)
        private set

    private var observableZoom by mutableDoubleStateOf(mapState.zoom)

    val center: Point
        get() = mapState.center

    val zoom: Double
        get() = observableZoom

    val projection: Projection
        get() = mapState.projection

    val config: MapConfig
        get() = mapState.config

    /**
     * Captures the current visible map rectangle for a viewport-dependent data source.
     *
     * Reading this from `snapshotFlow` observes camera and layout changes. [paddingFraction]
     * expands every side by the requested fraction of the visible width/height so callers can
     * prefetch around the screen and avoid reloading after every small pan.
     */
    fun viewportSnapshot(paddingFraction: Double = 0.0): MapViewportSnapshot {
        require(paddingFraction.isFinite() && paddingFraction >= 0.0) {
            "paddingFraction must be finite and non-negative"
        }
        revision // Establish a Compose snapshot read for camera and viewport changes.

        val viewport = mapState.viewport
        val topLeft = mapState.screenToWorld(Point(0.0, 0.0))
        val bottomRight = mapState.screenToWorld(
            Point(viewport.width.toDouble(), viewport.height.toDouble())
        )
        val minX = minOf(topLeft.x, bottomRight.x)
        val maxX = maxOf(topLeft.x, bottomRight.x)
        val minY = minOf(topLeft.y, bottomRight.y)
        val maxY = maxOf(topLeft.y, bottomRight.y)
        val padX = (maxX - minX) * paddingFraction
        val padY = (maxY - minY) * paddingFraction
        val bounds = BoundingBox.fromExtents(
            minX = minX - padX,
            maxX = maxX + padX,
            minY = minY - padY,
            maxY = maxY + padY,
        )

        return MapViewportSnapshot(
            bounds = bounds,
            zoom = mapState.zoom,
            viewportWidth = viewport.width,
            viewportHeight = viewport.height,
            resolution = if (viewport.width > 0) {
                (maxX - minX) / viewport.width
            } else {
                Double.POSITIVE_INFINITY
            },
        )
    }

    /**
     * Pans the camera by screen pixels.
     */
    fun panBy(dx: Double, dy: Double) {
        val previousCenter = mapState.center
        mapState.panBy(dx, dy)
        if (mapState.center != previousCenter) {
            markCameraChanged()
        }
    }

    /**
     * Animates a screen-pixel pan. Positive values use the same direction as [panBy].
     */
    suspend fun animatePanBy(
        dx: Double,
        dy: Double,
        animationSpec: AnimationSpec<Float> = DefaultPanAnimationSpec,
    ) {
        if (dx == 0.0 && dy == 0.0) return

        var previousProgress = 0f
        AnimationState(initialValue = 0f)
            .animateTo(targetValue = 1f, animationSpec = animationSpec) {
                val progressDelta = value - previousProgress
                previousProgress = value
                panBy(dx * progressDelta, dy * progressDelta)
            }
    }

    /**
     * Zooms in by [step] map zoom levels around the current viewport center.
     */
    fun zoomIn() {
        zoomIn(step = 1.0)
    }

    override fun zoomIn(step: Double) {
        zoomBy(step)
    }

    /**
     * Zooms out by [step] map zoom levels around the current viewport center.
     */
    fun zoomOut() {
        zoomOut(step = 1.0)
    }

    override fun zoomOut(step: Double) {
        zoomBy(-step)
    }

    /**
     * Changes zoom by [delta] levels. Pass [focus] in screen pixels to zoom
     * around a particular point; omit it for centered UI controls.
     */
    fun zoomBy(delta: Double) {
        zoomBy(delta = delta, focus = null)
    }

    override fun zoomBy(delta: Double, focus: Point?) {
        val previousCenter = mapState.center
        val previousZoom = mapState.zoom
        mapState.zoomBy(delta = delta, focus = focus)
        if (mapState.center != previousCenter || mapState.zoom != previousZoom) {
            markCameraChanged()
        }
    }

    /**
     * Animates zoom by [delta] levels. Pass [focus] in screen pixels to animate
     * around a particular point; omit it for centered UI controls.
     */
    suspend fun animateZoomBy(
        delta: Double,
        focus: Point? = null,
        animationSpec: AnimationSpec<Float> = DefaultZoomAnimationSpec,
    ) {
        val targetZoom = (mapState.zoom + delta).coerceIn(config.minZoom, config.maxZoom)
        if (targetZoom == mapState.zoom) return

        AnimationState(initialValue = mapState.zoom.toFloat())
            .animateTo(targetValue = targetZoom.toFloat(), animationSpec = animationSpec) {
                zoomBy(value.toDouble() - mapState.zoom, focus)
            }
    }

    suspend fun animateZoomIn(
        step: Double = 1.0,
        focus: Point? = null,
        animationSpec: AnimationSpec<Float> = DefaultZoomAnimationSpec,
    ) {
        animateZoomBy(delta = step, focus = focus, animationSpec = animationSpec)
    }

    suspend fun animateZoomOut(
        step: Double = 1.0,
        focus: Point? = null,
        animationSpec: AnimationSpec<Float> = DefaultZoomAnimationSpec,
    ) {
        animateZoomBy(delta = -step, focus = focus, animationSpec = animationSpec)
    }

    /** Centers the map on [bounds], leaving a density-independent [padding]. */
    fun fitBounds(bounds: BoundingBox, padding: Dp = 48.dp) {
        val previousCenter = mapState.center
        val previousZoom = mapState.zoom
        val requestedPaddingPx = padding.value * mapState.viewport.pixelRatio
        val smallestViewportDimension = minOf(mapState.viewport.width, mapState.viewport.height).toDouble()
        val maxPaddingPx = ((smallestViewportDimension - 1.0) / 2.0).coerceAtLeast(0.0)
        mapState.fitBounds(bounds, requestedPaddingPx.coerceAtMost(maxPaddingPx))
        if (mapState.center != previousCenter || mapState.zoom != previousZoom) {
            markCameraChanged()
        }
    }

    internal fun markChanged() {
        revision += 1
        if (mapState.zoom != observableZoom) {
            observableZoom = mapState.zoom
            zoomRevision += 1
        }
    }

    private fun markCameraChanged() {
        markChanged()
        cameraControlRevision += 1
    }

    private companion object {
        val DefaultZoomAnimationSpec: AnimationSpec<Float> =
            tween(durationMillis = 220, easing = FastOutSlowInEasing)
        val DefaultPanAnimationSpec: AnimationSpec<Float> =
            tween(durationMillis = 260, easing = FastOutSlowInEasing)
    }
}

/**
 * Receiver for the `TiloMap { ... }` layer DSL.
 *
 * Prefer high-level methods such as [wmsTileLayer], [featureLayer], and
 * [rasterLayer]. Use [layer] or unary `+` when integrating a custom layer.
 */
@ExperimentalTiloApi
@TiloDsl
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
        visible: Boolean = true,
        minZoom: Double? = null,
        maxZoom: Double? = null,
        projection: Projection = Epsg3857Projection,
        grid: TileGrid = TileGrid.defaultFor(projection),
        tms: Boolean = false,
        maxVisibleTiles: Int = 9,
        prefetchMargin: Int = 1,
        overviewZoomOffset: Int = 2,
        maxOverviewTiles: Int = 4,
        overviewPrefetchMargin: Int = 1,
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
                visible = visible,
                minZoom = minZoom,
                maxZoom = maxZoom,
                maxVisibleTiles = maxVisibleTiles,
                prefetchMargin = prefetchMargin,
                overviewZoomOffset = overviewZoomOffset,
                maxOverviewTiles = maxOverviewTiles,
                overviewPrefetchMargin = overviewPrefetchMargin,
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
        visible: Boolean = true,
        minZoom: Double? = null,
        maxZoom: Double? = null,
        scheme: TileRowScheme = TileRowScheme.TMS,
        sourceId: String = id,
        maxVisibleTiles: Int = 9,
        prefetchMargin: Int = 1,
        overviewZoomOffset: Int = 2,
        maxOverviewTiles: Int = 4,
        overviewPrefetchMargin: Int = 1,
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
                visible = visible,
                minZoom = minZoom,
                maxZoom = maxZoom,
                maxVisibleTiles = maxVisibleTiles,
                prefetchMargin = prefetchMargin,
                overviewZoomOffset = overviewZoomOffset,
                maxOverviewTiles = maxOverviewTiles,
                overviewPrefetchMargin = overviewPrefetchMargin,
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
        visible: Boolean = true,
        minZoom: Double? = null,
        maxZoom: Double? = null,
        projection: Projection? = null,
        renderMode: FeatureRenderMode = immediate(),
        style: FeatureLayerStyle = FeatureLayerStyle(),
        attribution: Attribution? = null,
        attributions: List<Attribution> = emptyList(),
    ) {
        layer(
            FeatureLayer(
                id = id,
                zIndex = zIndex,
                visible = visible,
                minZoom = minZoom,
                maxZoom = maxZoom,
                projection = projection,
                attributions = attributions.withSingle(attribution),
                features = features,
                renderStrategy = renderMode.toVectorRenderStrategy(),
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
            visible = options.visible,
            minZoom = options.minZoom,
            maxZoom = options.maxZoom,
            projection = options.projection,
            renderMode = options.renderMode,
            style = options.style,
            attribution = options.attribution,
            attributions = options.attributions,
        )
    }

    internal fun build(): List<Layer> = items.toList()
}

/**
 * Options for a vector layer declared with [MapLayerBuilder.featureLayer].
 */
@ExperimentalTiloApi
@TiloDsl
class FeatureLayerOptions {
    var zIndex: Int = 0
    var visible: Boolean = true
    var minZoom: Double? = null
    var maxZoom: Double? = null
    var projection: Projection? = null
    var renderMode: FeatureRenderMode = immediate()
    var style: FeatureLayerStyle = FeatureLayerStyle()
    var attribution: Attribution? = null
    var attributions: List<Attribution> = emptyList()
}

/**
 * Remembers camera state for [TiloMap].
 */
@Composable
@ExperimentalTiloApi
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
@ExperimentalTiloApi
Oprfun TiloMap(
    cameraState: MapCameraState,
    modifier: Modifier = Modifier,
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
            AttributionOverlay(
                cameraState = cameraState,
                layers = builtLayers,
                content = attributionContent,
            )
        }
        if (cameraControlsContent != null) {
            cameraControlsContent(cameraState)
        }
    }
}

@Composable
private fun BoxScope.AttributionOverlay(
    cameraState: MapCameraState,
    layers: List<Layer>,
    content: @Composable BoxScope.(List<Attribution>) -> Unit,
) {
    cameraState.zoomRevision
    val attributions = layers.filter { it.isVisibleAt(cameraState.zoom) }.attributions()
    if (attributions.isNotEmpty()) {
        content(attributions)
    }
}

@Composable
private fun MapRendererLayer(
    cameraState: MapCameraState,
    layers: List<Layer>,
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
