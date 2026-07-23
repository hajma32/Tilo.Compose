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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tilo.compose.core.feature.Feature
import tilo.compose.core.feature.FeatureLayerStyle
import tilo.compose.core.geometry.BoundingBox
import tilo.compose.core.geometry.Point
import tilo.compose.core.layers.Attribution
import tilo.compose.core.layers.Layer
import tilo.compose.core.layers.LayerGroup
import tilo.compose.core.layers.LayerSink
import tilo.compose.core.layers.raster.RasterTileLayer
import tilo.compose.core.layers.raster.TileLayer
import tilo.compose.core.layers.raster.TileRowScheme
import tilo.compose.core.layers.raster.TileStoreTileSource
import tilo.compose.core.layers.raster.WMSAxisOrder
import tilo.compose.core.layers.raster.WMSCapabilities
import tilo.compose.core.layers.raster.WMSCapabilitiesLoader
import tilo.compose.core.layers.raster.XYZTileLayer
import tilo.compose.core.layers.vector.FeatureLayer
import tilo.compose.core.map.MapCameraController
import tilo.compose.core.map.MapConfig
import tilo.compose.core.map.MapState
import tilo.compose.core.projection.Epsg3857Projection
import tilo.compose.core.projection.IdentityProjection
import tilo.compose.core.projection.Projection
import tilo.compose.core.scale.ScaleBar
import tilo.compose.core.scale.ScaleBarCalculator
import tilo.compose.core.selection.FeatureSelection
import tilo.compose.core.selection.FeatureSelectionRef
import tilo.compose.core.tile.TileCoordinate
import tilo.compose.core.tile.TileGrid
import tilo.compose.render.ExperimentalTiloRenderingApi
import tilo.compose.render.MapRenderer
import tilo.compose.render.ResolvedLayerTree

private const val OPEN_STREET_MAP_URL = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
private val OPEN_STREET_MAP_ATTRIBUTION =
    Attribution(
        label = "© OpenStreetMap contributors",
        url = "https://www.openstreetmap.org/copyright",
    )

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
        val bottomRight =
            mapState.screenToWorld(
                Point(viewport.width.toDouble(), viewport.height.toDouble()),
            )
        val minX = minOf(topLeft.x, bottomRight.x)
        val maxX = maxOf(topLeft.x, bottomRight.x)
        val minY = minOf(topLeft.y, bottomRight.y)
        val maxY = maxOf(topLeft.y, bottomRight.y)
        val padX = (maxX - minX) * paddingFraction
        val padY = (maxY - minY) * paddingFraction
        val bounds =
            BoundingBox.fromExtents(
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
            resolution =
                if (viewport.width > 0) {
                    (maxX - minX) / viewport.width
                } else {
                    Double.POSITIVE_INFINITY
                },
        )
    }

    /**
     * Pans the camera by screen pixels.
     */
    fun panBy(
        dx: Double,
        dy: Double,
    ) {
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

    /** Changes zoom by [delta] levels around the current viewport center. */
    fun zoomBy(delta: Double) {
        zoomBy(delta = delta, focus = null)
    }

    override fun zoomBy(
        delta: Double,
        focus: Point?,
    ) {
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
    fun fitBounds(
        bounds: BoundingBox,
        padding: Dp = 48.dp,
    ) {
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
class MapLayerBuilder private constructor(
    private val context: MapLayerBuildContext,
) : LayerSink {
    private val items = mutableListOf<MapLayerItem>()

    constructor() :
        this(
            MapLayerBuildContext(
                rasterLayerStore = null,
                loadWMSCapabilities = DEFAULT_WMS_CAPABILITIES_LOADER,
            ),
        )

    internal companion object {
        private val DEFAULT_WMS_CAPABILITIES_LOADER: suspend (String) -> WMSCapabilities = { url ->
            WMSCapabilitiesLoader().load(url)
        }

        fun managed(
            rasterLayerStore: RasterLayerStore,
            loadWMSCapabilities: suspend (String) -> WMSCapabilities = DEFAULT_WMS_CAPABILITIES_LOADER,
        ): MapLayerBuilder =
            MapLayerBuilder(
                MapLayerBuildContext(
                    rasterLayerStore = rasterLayerStore,
                    loadWMSCapabilities = loadWMSCapabilities,
                ),
            )
    }

    /**
     * Advanced escape hatch for custom or pre-built layers.
     *
     * The builder borrows [layer]. The caller retains ownership and remains
     * responsible for closing it if it owns resources.
     */
    override fun layer(layer: Layer) {
        registerLayerTree(layer)
        items += MapLayerItem.LayerValue(layer)
    }

    /**
     * Advanced shorthand for adding a custom or pre-built layer.
     * Ownership remains with the caller.
     */
    operator fun Layer.unaryPlus() {
        layer(this)
    }

    /**
     * Adds a composite layer whose children occupy one ordered slot among this builder's layers.
     *
     * Child [Layer.zIndex] values are local to the group. Visibility and zoom limits declared here
     * constrain all descendants. Group opacity is multiplied with each descendant's opacity. The
     * nested block supports the same layer DSL, including further groups and managed raster layers.
     */
    fun layerGroup(
        id: String,
        zIndex: Int = 0,
        visible: Boolean = true,
        minZoom: Double? = null,
        maxZoom: Double? = null,
        attribution: Attribution? = null,
        attributions: List<Attribution> = emptyList(),
        layers: MapLayerBuilder.() -> Unit,
    ) = addLayerGroup(id, zIndex, visible, minZoom, maxZoom, attribution, attributions, 1.0, layers)

    /** Adds a composite layer with [opacity] multiplied into every descendant. */
    fun layerGroup(
        id: String,
        zIndex: Int = 0,
        visible: Boolean = true,
        minZoom: Double? = null,
        maxZoom: Double? = null,
        attribution: Attribution? = null,
        attributions: List<Attribution> = emptyList(),
        opacity: Double,
        layers: MapLayerBuilder.() -> Unit,
    ) = addLayerGroup(id, zIndex, visible, minZoom, maxZoom, attribution, attributions, opacity, layers)

    private fun addLayerGroup(
        id: String,
        zIndex: Int,
        visible: Boolean,
        minZoom: Double?,
        maxZoom: Double?,
        attribution: Attribution?,
        attributions: List<Attribution>,
        opacity: Double,
        layers: MapLayerBuilder.() -> Unit,
    ) {
        require(minZoom == null || maxZoom == null || minZoom <= maxZoom) {
            "minZoom must not be greater than maxZoom"
        }
        require(opacity in 0.0..1.0) { "opacity must be between 0.0 and 1.0" }
        registerLayerId(id)
        val childBuilder = MapLayerBuilder(context)
        childBuilder.layers()
        items +=
            MapLayerItem.Group(
                id = id,
                zIndex = zIndex,
                visible = visible,
                opacity = opacity,
                minZoom = minZoom,
                maxZoom = maxZoom,
                attributions = attributions.withSingle(attribution),
                children = childBuilder.items.toList(),
            )
    }

    /**
     * Adds a pre-built raster tile layer.
     *
     * The builder borrows [layer] and never closes it. The caller must keep it
     * alive while any map uses it and close resource-owning layers afterwards.
     */
    fun rasterLayer(layer: TileLayer?) {
        if (layer != null) {
            this.layer(layer)
        }
    }

    /**
     * Adds a WMS raster layer discovered through GetCapabilities.
     *
     * The map loads capabilities asynchronously, owns the resulting raster
     * runtime, and closes it when the declaration leaves composition. The layer
     * is skipped until loading succeeds. [onError] receives capabilities and
     * tile transport failures without cancelling healthy tiles. Pass [state]
     * to observe initialization, distinguish tile errors, and trigger retry.
     * Prefetching and coarse overview loading are opt-in.
     */
    fun wmsTileLayer(
        id: String,
        capabilitiesUrl: String,
        layerName: String,
        projection: Projection,
        styles: String = "",
        format: String? = null,
        getMapVersion: String = "1.1.1",
        axisOrder: WMSAxisOrder = WMSAxisOrder.forCrs(projection.id),
        zIndex: Int = 0,
        visible: Boolean = true,
        minZoom: Double? = null,
        maxZoom: Double? = null,
        tileSize: Int = 256,
        maxVisibleTiles: Int = 9,
        prefetchMargin: Int = 0,
        overviewZoomOffset: Int = 0,
        maxOverviewTiles: Int = 4,
        overviewPrefetchMargin: Int = 0,
        attribution: Attribution? = null,
        attributions: List<Attribution> = emptyList(),
        state: RasterLayerState? = null,
        onError: ((Throwable) -> Unit)? = null,
        opacity: Double = 1.0,
    ) {
        require(opacity in 0.0..1.0) { "opacity must be between 0.0 and 1.0" }
        require(context.layerIds.add(id)) {
            "Duplicate layer id '$id'. Layer IDs must be unique within one TiloMap."
        }
        val resolvedAttributions = attributions.withSingle(attribution)
        val key =
            ManagedWMSLayerKey(
                layerId = id,
                configuration =
                    WMSRasterConfiguration(
                        capabilitiesUrl = capabilitiesUrl,
                        layerName = layerName,
                        projectionId = projection.id,
                        projectionWorldUnitsPerMapUnit = projection.worldUnitsPerMapUnit,
                        styles = styles,
                        format = format,
                        getMapVersion = getMapVersion,
                        axisOrder = axisOrder,
                        tileSize = tileSize,
                        maxVisibleTiles = maxVisibleTiles,
                        prefetchMargin = prefetchMargin,
                        overviewZoomOffset = overviewZoomOffset,
                        maxOverviewTiles = maxOverviewTiles,
                        overviewPrefetchMargin = overviewPrefetchMargin,
                        retryKey = state?.retryKey ?: 0,
                    ),
            )
        items +=
            MapLayerItem.ManagedWMS(
                ManagedWMSLayerDeclaration(
                    key = key,
                    id = id,
                    zIndex = zIndex,
                    visible = visible,
                    opacity = opacity,
                    minZoom = minZoom,
                    maxZoom = maxZoom,
                    attributions = resolvedAttributions,
                    state = state,
                    onError = onError,
                    create = { reportError ->
                        val capabilities = context.loadWMSCapabilities(capabilitiesUrl)
                        capabilities.createTileLayer(
                            id = id,
                            layerName = layerName,
                            projection = projection,
                            styles = styles,
                            format = format ?: capabilities.formats.firstOrNull() ?: "image/png",
                            getMapVersion = getMapVersion,
                            axisOrder = axisOrder,
                            zIndex = 0,
                            visible = true,
                            minZoom = null,
                            maxZoom = null,
                            tileSize = tileSize,
                            maxVisibleTiles = maxVisibleTiles,
                            prefetchMargin = prefetchMargin,
                            overviewZoomOffset = overviewZoomOffset,
                            maxOverviewTiles = maxOverviewTiles,
                            overviewPrefetchMargin = overviewPrefetchMargin,
                            attributions = emptyList(),
                            onError = reportError,
                        )
                    },
                ),
            )
    }

    /** Adds several WMS sublayers as one composited GetMap tile layer. */
    fun wmsTileLayer(
        id: String,
        capabilitiesUrl: String,
        layerNames: List<String>,
        projection: Projection,
        styles: String = "",
        format: String? = null,
        getMapVersion: String = "1.1.1",
        axisOrder: WMSAxisOrder = WMSAxisOrder.forCrs(projection.id),
        zIndex: Int = 0,
        visible: Boolean = true,
        minZoom: Double? = null,
        maxZoom: Double? = null,
        tileSize: Int = 256,
        maxVisibleTiles: Int = 9,
        prefetchMargin: Int = 0,
        overviewZoomOffset: Int = 0,
        maxOverviewTiles: Int = 4,
        overviewPrefetchMargin: Int = 0,
        attribution: Attribution? = null,
        attributions: List<Attribution> = emptyList(),
        state: RasterLayerState? = null,
        onError: ((Throwable) -> Unit)? = null,
        opacity: Double = 1.0,
    ) {
        require(layerNames.isNotEmpty()) { "At least one WMS layer name is required." }
        wmsTileLayer(
            id = id,
            capabilitiesUrl = capabilitiesUrl,
            layerName = layerNames.joinToString(","),
            projection = projection,
            styles = styles,
            format = format,
            getMapVersion = getMapVersion,
            axisOrder = axisOrder,
            zIndex = zIndex,
            visible = visible,
            opacity = opacity,
            minZoom = minZoom,
            maxZoom = maxZoom,
            tileSize = tileSize,
            maxVisibleTiles = maxVisibleTiles,
            prefetchMargin = prefetchMargin,
            overviewZoomOffset = overviewZoomOffset,
            maxOverviewTiles = maxOverviewTiles,
            overviewPrefetchMargin = overviewPrefetchMargin,
            attribution = attribution,
            attributions = attributions,
            state = state,
            onError = onError,
        )
    }

    /**
     * Adds the standard OpenStreetMap Web Mercator basemap.
     *
     * This is a convenience preset for [xyzTileLayer] with the public OSM tile
     * URL and required contributor attribution. Prefetching and coarse overview
     * loading are explicitly disabled to respect the OpenStreetMap tile usage
     * policy. Applications remain responsible for following the rest of it.
     * [state] uses the same observable lifecycle and retry contract as WMS.
     * [onError] receives tile transport failures without cancelling healthy tiles.
     */
    fun osmLayer(
        id: String = "osm",
        zIndex: Int = 0,
        visible: Boolean = true,
        minZoom: Double? = null,
        maxZoom: Double? = null,
        state: RasterLayerState? = null,
        onError: ((Throwable) -> Unit)? = null,
        opacity: Double = 1.0,
    ) {
        xyzTileLayer(
            id = id,
            urlTemplate = OPEN_STREET_MAP_URL,
            zIndex = zIndex,
            visible = visible,
            opacity = opacity,
            minZoom = minZoom,
            maxZoom = maxZoom,
            projection = Epsg3857Projection,
            prefetchMargin = 0,
            overviewZoomOffset = 0,
            maxOverviewTiles = 0,
            overviewPrefetchMargin = 0,
            attribution = OPEN_STREET_MAP_ATTRIBUTION,
            state = state,
            onError = onError,
        )
    }

    /**
     * Adds a URL-template raster layer using `{z}`, `{x}`, `{y}` placeholders.
     *
     * Web Mercator is the default because that is what public XYZ slippy-map
     * services normally use. Pass [projection] and [grid] for custom grids.
     * Prefetching and coarse overview loading are opt-in.
     * [state] observes readiness, recoverable tile errors, and explicit retry.
     * [onError] receives source failures without cancelling healthy tiles.
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
        prefetchMargin: Int = 0,
        overviewZoomOffset: Int = 0,
        maxOverviewTiles: Int = 4,
        overviewPrefetchMargin: Int = 0,
        attribution: Attribution? = null,
        attributions: List<Attribution> = emptyList(),
        state: RasterLayerState? = null,
        onError: ((Throwable) -> Unit)? = null,
        opacity: Double = 1.0,
    ) {
        val resolvedAttributions = attributions.withSingle(attribution)
        managedRasterLayer(
            id = id,
            zIndex = zIndex,
            visible = visible,
            opacity = opacity,
            minZoom = minZoom,
            maxZoom = maxZoom,
            attributions = resolvedAttributions,
            configuration =
                XyzRasterConfiguration(
                    projectionId = projection.id,
                    projectionWorldUnitsPerMapUnit = projection.worldUnitsPerMapUnit,
                    grid = grid,
                    urlTemplate = urlTemplate,
                    tms = tms,
                    maxVisibleTiles = maxVisibleTiles,
                    prefetchMargin = prefetchMargin,
                    overviewZoomOffset = overviewZoomOffset,
                    maxOverviewTiles = maxOverviewTiles,
                    overviewPrefetchMargin = overviewPrefetchMargin,
                    retryKey = state?.retryKey ?: 0,
                ),
            update = RasterLayerUpdate.Source(state, onError),
        ) {
            val diagnostics = MutableRasterLayerDiagnostics(state, onError)
            StoredRasterLayer(
                layer =
                    XYZTileLayer(
                        id = id,
                        projection = projection,
                        grid = grid,
                        urlTemplate = urlTemplate,
                        tms = tms,
                        zIndex = zIndex,
                        visible = visible,
                        opacity = opacity,
                        minZoom = minZoom,
                        maxZoom = maxZoom,
                        maxVisibleTiles = maxVisibleTiles,
                        prefetchMargin = prefetchMargin,
                        overviewZoomOffset = overviewZoomOffset,
                        maxOverviewTiles = maxOverviewTiles,
                        overviewPrefetchMargin = overviewPrefetchMargin,
                        attributions = resolvedAttributions,
                        onError = diagnostics::tileFailed,
                    ),
                update = { update ->
                    if (update is RasterLayerUpdate.Source) {
                        diagnostics.update(update.state, update.onError)
                        diagnostics.ready()
                    }
                },
                retire = diagnostics::retire,
            )
        }
    }

    /**
     * Adds a raster layer backed by an app-owned z/x/y tile store.
     *
     * The caller provides the tile reader so platform-specific SQLite access and
     * project-specific metadata stay outside the renderer. This supports
     * WebMercator, S-JTSK/Krovak, or any custom [projection] + [grid] pair.
     * [sourceId] is the stable identity of the stored content; change it when
     * switching databases or revisions that must not share cached tiles.
     * Prefetching and coarse overview loading are opt-in.
     * [state] observes readiness, recoverable reader errors, and explicit retry.
     * [onError] receives reader failures without cancelling healthy tiles.
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
        prefetchMargin: Int = 0,
        overviewZoomOffset: Int = 0,
        maxOverviewTiles: Int = 4,
        overviewPrefetchMargin: Int = 0,
        attribution: Attribution? = null,
        attributions: List<Attribution> = emptyList(),
        state: RasterLayerState? = null,
        onError: ((Throwable) -> Unit)? = null,
        opacity: Double = 1.0,
    ) {
        val resolvedAttributions = attributions.withSingle(attribution)
        managedRasterLayer(
            id = id,
            zIndex = zIndex,
            visible = visible,
            opacity = opacity,
            minZoom = minZoom,
            maxZoom = maxZoom,
            attributions = resolvedAttributions,
            configuration =
                TileStoreRasterConfiguration(
                    projectionId = projection.id,
                    projectionWorldUnitsPerMapUnit = projection.worldUnitsPerMapUnit,
                    grid = grid,
                    scheme = scheme,
                    sourceId = sourceId,
                    maxVisibleTiles = maxVisibleTiles,
                    prefetchMargin = prefetchMargin,
                    overviewZoomOffset = overviewZoomOffset,
                    maxOverviewTiles = maxOverviewTiles,
                    overviewPrefetchMargin = overviewPrefetchMargin,
                    retryKey = state?.retryKey ?: 0,
                ),
            update = RasterLayerUpdate.TileStore(readTile, state, onError),
        ) {
            val reader = MutableTileReader(readTile)
            val diagnostics = MutableRasterLayerDiagnostics(state, onError)
            StoredRasterLayer(
                layer =
                    RasterTileLayer(
                        id = id,
                        source =
                            TileStoreTileSource(
                                projection = projection,
                                grid = grid,
                                scheme = scheme,
                                sourceId = sourceId,
                                readTile = reader::read,
                            ),
                        zIndex = zIndex,
                        visible = visible,
                        opacity = opacity,
                        minZoom = minZoom,
                        maxZoom = maxZoom,
                        maxVisibleTiles = maxVisibleTiles,
                        prefetchMargin = prefetchMargin,
                        overviewZoomOffset = overviewZoomOffset,
                        maxOverviewTiles = maxOverviewTiles,
                        overviewPrefetchMargin = overviewPrefetchMargin,
                        attributions = resolvedAttributions,
                        onError = diagnostics::tileFailed,
                    ),
                update = { update ->
                    if (update is RasterLayerUpdate.TileStore) {
                        reader.delegate = update.readTile
                        diagnostics.update(update.state, update.onError)
                        diagnostics.ready()
                    }
                },
                retire = diagnostics::retire,
            )
        }
    }

    /**
     * Advanced alias for adding a pre-built raster tile layer.
     * Ownership remains with the caller; see [rasterLayer].
     */
    fun tileLayer(layer: TileLayer?) {
        rasterLayer(layer)
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
        opacity: Double = 1.0,
    ) {
        validatePointIconReferences(
            layerId = id,
            features = features,
            layerStyle = style,
            registeredIconIds = emptySet(),
        )
        layer(
            createFeatureLayer(
                id,
                features,
                zIndex,
                visible,
                opacity,
                minZoom,
                maxZoom,
                projection,
                renderMode,
                style,
                attribution,
                attributions,
            ),
        )
    }

    fun featureLayer(
        id: String,
        features: List<Feature>,
        block: FeatureLayerOptions.() -> Unit,
    ) {
        val options = FeatureLayerOptions().apply(block)
        validatePointIconReferences(
            layerId = id,
            features = features,
            layerStyle = options.style,
            registeredIconIds = options.pointIconPainters.keys,
        )
        val featureLayer =
            createFeatureLayer(
                id,
                features,
                options.zIndex,
                options.visible,
                options.opacity,
                options.minZoom,
                options.maxZoom,
                options.projection,
                options.renderMode,
                options.style,
                options.attribution,
                options.attributions,
            )
        layer(
            if (options.pointIconPainters.isEmpty()) {
                featureLayer
            } else {
                IconFeatureLayer(featureLayer, options.pointIconPainters.toMap())
            },
        )
    }

    private fun createFeatureLayer(
        id: String,
        features: List<Feature>,
        zIndex: Int,
        visible: Boolean,
        opacity: Double,
        minZoom: Double?,
        maxZoom: Double?,
        projection: Projection?,
        renderMode: FeatureRenderMode,
        style: FeatureLayerStyle,
        attribution: Attribution?,
        attributions: List<Attribution>,
    ): FeatureLayer =
        FeatureLayer(
            id = id,
            zIndex = zIndex,
            visible = visible,
            opacity = opacity,
            minZoom = minZoom,
            maxZoom = maxZoom,
            projection = projection,
            attributions = attributions.withSingle(attribution),
            features = features,
            renderStrategy = renderMode.toVectorRenderStrategy(),
            style = style,
        )

    internal val managedWMSDeclarations: List<ManagedWMSLayerDeclaration>
        get() = items.managedWMSDeclarations()

    internal val managedRasterKeys: Set<ManagedRasterLayerKey>
        get() = context.managedRasterKeys

    internal val managedRasterUpdates: Map<ManagedRasterLayerKey, RasterLayerUpdate>
        get() = context.managedRasterUpdates

    internal fun build(resolvedWMSLayers: Map<ManagedWMSLayerKey, TileLayer?> = emptyMap()): List<Layer> =
        items.buildLayers(resolvedWMSLayers)

    private fun registerLayerTree(layer: Layer) {
        val ids =
            buildList {
                fun collect(current: Layer) {
                    add(current.id)
                    if (current is LayerGroup) {
                        current.children.forEach(::collect)
                    }
                }
                collect(layer)
            }
        val duplicateWithinTree =
            ids
                .groupingBy { it }
                .eachCount()
                .entries
                .firstOrNull { it.value > 1 }
                ?.key
        require(duplicateWithinTree == null) {
            "Duplicate layer id '$duplicateWithinTree'. Layer IDs must be unique within one TiloMap."
        }
        val duplicateInMap = ids.firstOrNull(context.layerIds::contains)
        require(duplicateInMap == null) {
            "Duplicate layer id '$duplicateInMap'. Layer IDs must be unique within one TiloMap."
        }
        context.layerIds += ids
    }

    private fun registerLayerId(id: String) {
        require(context.layerIds.add(id)) {
            "Duplicate layer id '$id'. Layer IDs must be unique within one TiloMap."
        }
    }

    private fun managedRasterLayer(
        id: String,
        zIndex: Int,
        visible: Boolean,
        opacity: Double,
        minZoom: Double?,
        maxZoom: Double?,
        attributions: List<Attribution>,
        configuration: Any,
        update: RasterLayerUpdate = RasterLayerUpdate.None,
        create: () -> StoredRasterLayer,
    ) {
        require(opacity in 0.0..1.0) { "opacity must be between 0.0 and 1.0" }
        require(context.layerIds.add(id)) {
            "Duplicate layer id '$id'. Layer IDs must be unique within one TiloMap."
        }
        val store = context.rasterLayerStore
        val layer =
            if (store == null) {
                create().layer
            } else {
                val key = ManagedRasterLayerKey(layerId = id, configuration = configuration)
                context.managedRasterKeys += key
                context.managedRasterUpdates[key] = update
                PresentedTileLayer(
                    runtime = store.getOrCreate(key, create),
                    id = id,
                    zIndex = zIndex,
                    visible = visible,
                    opacity = opacity,
                    minZoom = minZoom,
                    maxZoom = maxZoom,
                    attributions = attributions,
                )
            }
        items += MapLayerItem.LayerValue(layer)
    }
}

private class MapLayerBuildContext(
    val rasterLayerStore: RasterLayerStore?,
    val loadWMSCapabilities: suspend (String) -> WMSCapabilities,
) {
    val layerIds = mutableSetOf<String>()
    val managedRasterKeys = mutableSetOf<ManagedRasterLayerKey>()
    val managedRasterUpdates = mutableMapOf<ManagedRasterLayerKey, RasterLayerUpdate>()
}

private sealed interface MapLayerItem {
    class LayerValue(
        val layer: Layer,
    ) : MapLayerItem

    class ManagedWMS(
        val declaration: ManagedWMSLayerDeclaration,
    ) : MapLayerItem

    class Group(
        val id: String,
        val zIndex: Int,
        val visible: Boolean,
        val opacity: Double,
        val minZoom: Double?,
        val maxZoom: Double?,
        val attributions: List<Attribution>,
        val children: List<MapLayerItem>,
    ) : MapLayerItem
}

private fun List<MapLayerItem>.managedWMSDeclarations(): List<ManagedWMSLayerDeclaration> =
    flatMap { item ->
        when (item) {
            is MapLayerItem.Group -> item.children.managedWMSDeclarations()
            is MapLayerItem.LayerValue -> emptyList()
            is MapLayerItem.ManagedWMS -> listOf(item.declaration)
        }
    }

private fun List<MapLayerItem>.buildLayers(resolvedWMSLayers: Map<ManagedWMSLayerKey, TileLayer?>): List<Layer> =
    mapNotNull { item ->
        when (item) {
            is MapLayerItem.LayerValue -> item.layer
            is MapLayerItem.ManagedWMS -> resolvedWMSLayers[item.declaration.key]
            is MapLayerItem.Group ->
                LayerGroup(
                    id = item.id,
                    children = item.children.buildLayers(resolvedWMSLayers),
                    zIndex = item.zIndex,
                    visible = item.visible,
                    opacity = item.opacity,
                    minZoom = item.minZoom,
                    maxZoom = item.maxZoom,
                    attributions = item.attributions,
                )
        }
    }

internal data class XyzRasterConfiguration(
    val projectionId: String,
    val projectionWorldUnitsPerMapUnit: Double,
    val grid: TileGrid,
    val urlTemplate: String,
    val tms: Boolean,
    val maxVisibleTiles: Int,
    val prefetchMargin: Int,
    val overviewZoomOffset: Int,
    val maxOverviewTiles: Int,
    val overviewPrefetchMargin: Int,
    val retryKey: Int,
)

internal data class TileStoreRasterConfiguration(
    val projectionId: String,
    val projectionWorldUnitsPerMapUnit: Double,
    val grid: TileGrid,
    val scheme: TileRowScheme,
    val sourceId: String,
    val maxVisibleTiles: Int,
    val prefetchMargin: Int,
    val overviewZoomOffset: Int,
    val maxOverviewTiles: Int,
    val overviewPrefetchMargin: Int,
    val retryKey: Int,
)

internal data class WMSRasterConfiguration(
    val capabilitiesUrl: String,
    val layerName: String,
    val projectionId: String,
    val projectionWorldUnitsPerMapUnit: Double,
    val styles: String,
    val format: String?,
    val getMapVersion: String,
    val axisOrder: WMSAxisOrder,
    val tileSize: Int,
    val maxVisibleTiles: Int,
    val prefetchMargin: Int,
    val overviewZoomOffset: Int,
    val maxOverviewTiles: Int,
    val overviewPrefetchMargin: Int,
    val retryKey: Int,
)

private class MutableTileReader(
    var delegate: suspend (TileCoordinate) -> ByteArray?,
) {
    suspend fun read(coordinate: TileCoordinate): ByteArray? = delegate(coordinate)
}

/**
 * Options for a vector layer declared with [MapLayerBuilder.featureLayer].
 */
@ExperimentalTiloApi
@TiloDsl
class FeatureLayerOptions {
    internal val pointIconPainters = linkedMapOf<String, Painter>()

    var zIndex: Int = 0
    var visible: Boolean = true
    var opacity: Double = 1.0
    var minZoom: Double? = null
    var maxZoom: Double? = null
    var projection: Projection? = null
    var renderMode: FeatureRenderMode = immediate()
    var style: FeatureLayerStyle = FeatureLayerStyle()
    var attribution: Attribution? = null
    var attributions: List<Attribution> = emptyList()

    /**
     * Registers a bitmap or vector [Painter] for use by point styles in this layer.
     *
     * The [id] must be unique within the layer. Reference it with
     * [PointStyleBuilder.icon].
     */
    fun pointIcon(
        id: String,
        painter: Painter,
    ) {
        require(id.isNotBlank()) { "Point icon id must not be blank" }
        require(pointIconPainters.put(id, painter) == null) { "Point icon id '$id' is already registered" }
    }
}

/**
 * Remembers camera state for [TiloMap].
 *
 * [initialCenter] and [initialZoom] initialize a newly remembered state and are
 * not reapplied by later recompositions. [projection] and [config] are immutable
 * properties of [MapCameraState]; changing either replaces the remembered state.
 */
@Composable
@ExperimentalTiloApi
fun rememberMapCameraState(
    initialCenter: Point = Point(0.0, 0.0),
    initialZoom: Double = 0.0,
    projection: Projection = IdentityProjection,
    config: MapConfig = MapConfig.Default,
): MapCameraState =
    remember(projection, config) {
        MapCameraState(
            mapState =
                MapState(
                    center = initialCenter,
                    zoom = initialZoom,
                    projection = projection,
                    config = config,
                ),
        )
    }

/**
 * Compose map surface.
 *
 * Declare raster, vector, drawing, and custom layers in [layers].
 * Unexpected render branch failures are reported through [onRenderError];
 * ordinary missing or undecodable raster tiles remain isolated.
 */
@Composable
@ExperimentalTiloApi
fun TiloMap(
    cameraState: MapCameraState,
    modifier: Modifier = Modifier,
    onTapWorld: ((Point) -> Unit)? = null,
    onFeatureSelect: ((List<FeatureSelection>) -> Unit)? = null,
    onRenderError: ((Throwable) -> Unit)? = null,
    selectedFeatures: Set<FeatureSelectionRef> = emptySet(),
    attributionContent: (@Composable BoxScope.(List<Attribution>) -> Unit)? = null,
    scaleBarContent: (@Composable BoxScope.(ScaleBar) -> Unit)? = null,
    cameraControlsContent: (@Composable BoxScope.(MapCameraState) -> Unit)? = null,
    invalidationKey: Any? = null,
    layers: MapLayerBuilder.() -> Unit,
) {
    val builtLayers = rememberManagedMapLayers(layers)
    Box(modifier = modifier) {
        MapRendererLayer(
            cameraState = cameraState,
            layers = builtLayers,
            onTapWorld = onTapWorld,
            onFeatureSelect = onFeatureSelect,
            onRenderError = onRenderError,
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

/**
 * UI-free lifecycle boundary for the map layer DSL.
 *
 * Keeping ownership here makes the runtime/cache behavior testable with the
 * Compose runtime alone, without rendering a platform canvas.
 */
@Composable
internal fun rememberManagedMapLayers(layers: MapLayerBuilder.() -> Unit): List<Layer> {
    val rasterLayerStore = remember { RasterLayerStore() }
    val layerBuilder = MapLayerBuilder.managed(rasterLayerStore)
    layerBuilder.layers()
    val resolvedWMSLayers =
        buildMap {
            layerBuilder.managedWMSDeclarations.forEach { declaration ->
                key(declaration.key) {
                    put(declaration.key, rememberManagedWMSLayer(declaration))
                }
            }
        }
    val builtLayers = layerBuilder.build(resolvedWMSLayers)
    val activeRasterKeys = layerBuilder.managedRasterKeys.toSet()
    val activeRasterUpdates = layerBuilder.managedRasterUpdates.toMap()
    SideEffect {
        rasterLayerStore.retain(activeRasterKeys, activeRasterUpdates)
    }
    DisposableEffect(rasterLayerStore) {
        onDispose(rasterLayerStore::close)
    }
    return builtLayers
}

@Composable
private fun BoxScope.AttributionOverlay(
    cameraState: MapCameraState,
    layers: List<Layer>,
    content: @Composable BoxScope.(List<Attribution>) -> Unit,
) {
    cameraState.zoomRevision
    val layerTree = remember(layers) { ResolvedLayerTree.resolve(layers) }
    val attributions = layerTree.activeAttributions(cameraState.zoom)
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
    onRenderError: ((Throwable) -> Unit)?,
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
        onRenderError = onRenderError,
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
