@file:OptIn(ExperimentalTiloApi::class, ExperimentalTiloRenderingApi::class)

package tilo.compose.dsl

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import tilo.compose.core.feature.Feature
import tilo.compose.core.feature.FeatureLayerStyle
import tilo.compose.core.geometry.BoundingBox
import tilo.compose.core.geometry.Point
import tilo.compose.core.layers.Attribution
import tilo.compose.core.layers.Layer
import tilo.compose.core.layers.LayerGroup
import tilo.compose.core.layers.LayerSink
import tilo.compose.core.layers.raster.RasterHttpConfig
import tilo.compose.core.layers.raster.RasterHttpTransport
import tilo.compose.core.layers.raster.RasterTileLayer
import tilo.compose.core.layers.raster.TileLayer
import tilo.compose.core.layers.raster.TileRowScheme
import tilo.compose.core.layers.raster.TileStoreTileSource
import tilo.compose.core.layers.raster.WmsAxisOrder
import tilo.compose.core.layers.raster.WmsCapabilities
import tilo.compose.core.layers.raster.WmsCapabilitiesLoader
import tilo.compose.core.layers.raster.WmsImageFormat
import tilo.compose.core.layers.raster.WmsLayerOptions
import tilo.compose.core.layers.raster.WmsVersion
import tilo.compose.core.layers.raster.XyzTileLayer
import tilo.compose.core.layers.vector.FeatureLayer
import tilo.compose.core.map.CameraPosition
import tilo.compose.core.map.MapCameraController
import tilo.compose.core.map.MapConfig
import tilo.compose.core.map.MapState
import tilo.compose.core.map.ScreenPoint
import tilo.compose.core.projection.Epsg3857Projection
import tilo.compose.core.projection.IdentityProjection
import tilo.compose.core.projection.Projection
import tilo.compose.core.scale.ScaleBar
import tilo.compose.core.scale.ScaleBarCalculator
import tilo.compose.core.selection.FeatureSelection
import tilo.compose.core.selection.FeatureSelectionRef
import tilo.compose.core.tile.TileCoordinate
import tilo.compose.core.tile.TileGrid
import tilo.compose.core.transform.ProjTransformationProvider
import tilo.compose.core.transform.TransformationRegistry
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
 * Controls how pointer gestures manipulate a [TiloMap].
 *
 * `rotationThresholdDegrees` is the net angular displacement from the gesture's initial
 * orientation required before a two-finger gesture starts rotating the map. Movement up to the
 * threshold is ignored, which keeps ordinary pinch-to-zoom gestures from introducing accidental
 * bearing changes. Set it to `0.0` for immediate rotation.
 */
@ExperimentalTiloApi
data class MapGestureConfig(
    val rotationThresholdDegrees: Double = DEFAULT_ROTATION_THRESHOLD_DEGREES,
) {
    init {
        require(rotationThresholdDegrees.isFinite() && rotationThresholdDegrees >= 0.0) {
            "rotationThresholdDegrees must be finite and non-negative"
        }
    }

    companion object {
        val Default = MapGestureConfig()

        private const val DEFAULT_ROTATION_THRESHOLD_DEGREES = 8.0
    }
}

/**
 * Cross-cutting behavior for a [TiloMap] surface.
 *
 * Keeping these settings in one value lets the map API grow without adding overloads for every
 * combination of optional behavior.
 */
@ExperimentalTiloApi
data class TiloMapOptions(
    val accessibility: MapAccessibilityOptions = MapAccessibilityOptions(),
    val gestureConfig: MapGestureConfig = MapGestureConfig.Default,
)

/**
 * Immutable camera snapshot suitable for viewport-dependent data loading.
 *
 * `bounds` and `resolution` use the map projection's coordinate units. A snapshot is not ready
 * until the map has received a non-empty viewport from layout.
 */
@ExperimentalTiloApi
data class MapViewportSnapshot(
    val bounds: BoundingBox,
    val zoom: Double,
    val viewportWidth: Int,
    val viewportHeight: Int,
    val resolution: Double,
    val bearing: Double = 0.0,
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

    private var observablePosition by mutableStateOf(mapState.cameraPosition)
    private val observableCenter = derivedStateOf(structuralEqualityPolicy()) { observablePosition.center }
    private val observableZoom = derivedStateOf(structuralEqualityPolicy()) { observablePosition.zoom }
    private val observableBearing = derivedStateOf(structuralEqualityPolicy()) { observablePosition.bearing }
    private var activeAnimationJob: Job? = null

    val center: Point
        get() = observableCenter.value

    val zoom: Double
        get() = observableZoom.value

    /** Clockwise map rotation in degrees, normalized to the `[0, 360)` range. */
    val bearing: Double
        get() = observableBearing.value

    /** Immutable observable snapshot of center, zoom, and bearing. */
    val position: CameraPosition
        get() = observablePosition

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
        val visible = mapState.viewportBounds()
        val minX = visible.minX
        val maxX = visible.maxX
        val minY = visible.minY
        val maxY = visible.maxY
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
            resolution = if (viewport.width > 0) mapState.resolution() else Double.POSITIVE_INFINITY,
            bearing = mapState.bearing,
        )
    }

    /**
     * Pans the camera by screen pixels.
     */
    fun panBy(
        dx: Double,
        dy: Double,
    ) {
        cancelCameraAnimation()
        panByInternal(dx, dy)
    }

    private fun panByInternal(
        dx: Double,
        dy: Double,
    ) {
        val previousCenter = mapState.center
        mapState.panBy(dx, dy)
        if (mapState.center != previousCenter) {
            markCameraChanged(mapState.cameraPosition)
        }
    }

    /** Sets the center in the active map projection. */
    fun setCenter(center: Point) {
        setCamera(position.copy(center = center))
    }

    /** Sets the absolute zoom level, constrained by [config]. */
    fun setZoom(zoom: Double) {
        setCamera(position.copy(zoom = zoom))
    }

    /** Atomically publishes a new center, zoom, and bearing. */
    fun setCamera(position: CameraPosition) {
        cancelCameraAnimation()
        setCameraInternal(position)
    }

    /** Convenience overload for atomically setting all camera components. */
    fun setCamera(
        center: Point,
        zoom: Double,
        bearing: Double = this.bearing,
    ) {
        setCamera(CameraPosition(center = center, zoom = zoom, bearing = bearing))
    }

    /** Animates center, zoom, and bearing together, using the shortest rotation around north. */
    suspend fun animateTo(
        position: CameraPosition,
        animationSpec: AnimationSpec<Float> = DefaultCameraAnimationSpec,
    ) = runCameraAnimation {
        val start = this.position
        val target =
            CameraPosition(
                center = position.center,
                zoom = position.zoom.coerceIn(config.minZoom, config.maxZoom),
                bearing = normalizeBearing(position.bearing),
            )
        if (target == start) return@runCameraAnimation
        val bearingDelta = shortestBearingDelta(start.bearing, target.bearing)
        var expectedRevision = mapState.cameraRevision

        AnimationState(initialValue = 0f)
            .animateTo(targetValue = 1f, animationSpec = animationSpec) {
                ensureCameraRevision(expectedRevision)
                val progress = value.toDouble()
                setCameraInternal(
                    CameraPosition(
                        center =
                            Point(
                                x = lerp(start.center.x, target.center.x, progress),
                                y = lerp(start.center.y, target.center.y, progress),
                            ),
                        zoom = lerp(start.zoom, target.zoom, progress),
                        bearing = start.bearing + bearingDelta * progress,
                    ),
                )
                expectedRevision = mapState.cameraRevision
            }
    }

    /**
     * Animates a screen-pixel pan. Positive values use the same direction as [panBy].
     */
    suspend fun animatePanBy(
        dx: Double,
        dy: Double,
        animationSpec: AnimationSpec<Float> = DefaultPanAnimationSpec,
    ) = runCameraAnimation {
        if (dx == 0.0 && dy == 0.0) return@runCameraAnimation

        var previousProgress = 0f
        var expectedRevision = mapState.cameraRevision
        AnimationState(initialValue = 0f)
            .animateTo(targetValue = 1f, animationSpec = animationSpec) {
                ensureCameraRevision(expectedRevision)
                val progressDelta = value - previousProgress
                previousProgress = value
                panByInternal(dx * progressDelta, dy * progressDelta)
                expectedRevision = mapState.cameraRevision
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
        focus: ScreenPoint?,
    ) {
        cancelCameraAnimation()
        zoomByInternal(delta, focus)
    }

    private fun zoomByInternal(
        delta: Double,
        focus: ScreenPoint?,
    ) {
        val previousCenter = mapState.center
        val previousZoom = mapState.zoom
        mapState.zoomBy(delta = delta, focus = focus)
        if (mapState.center != previousCenter || mapState.zoom != previousZoom) {
            markCameraChanged(mapState.cameraPosition)
        }
    }

    /**
     * Animates zoom by [delta] levels. Pass [focus] in screen pixels to animate
     * around a particular point; omit it for centered UI controls.
     */
    suspend fun animateZoomBy(
        delta: Double,
        focus: ScreenPoint? = null,
        animationSpec: AnimationSpec<Float> = DefaultZoomAnimationSpec,
    ) = runCameraAnimation {
        val targetZoom = (mapState.zoom + delta).coerceIn(config.minZoom, config.maxZoom)
        if (targetZoom == mapState.zoom) return@runCameraAnimation

        var expectedRevision = mapState.cameraRevision
        AnimationState(initialValue = mapState.zoom.toFloat())
            .animateTo(targetValue = targetZoom.toFloat(), animationSpec = animationSpec) {
                ensureCameraRevision(expectedRevision)
                zoomByInternal(value.toDouble() - mapState.zoom, focus)
                expectedRevision = mapState.cameraRevision
            }
    }

    suspend fun animateZoomIn(
        step: Double = 1.0,
        focus: ScreenPoint? = null,
        animationSpec: AnimationSpec<Float> = DefaultZoomAnimationSpec,
    ) {
        animateZoomBy(delta = step, focus = focus, animationSpec = animationSpec)
    }

    suspend fun animateZoomOut(
        step: Double = 1.0,
        focus: ScreenPoint? = null,
        animationSpec: AnimationSpec<Float> = DefaultZoomAnimationSpec,
    ) {
        animateZoomBy(delta = -step, focus = focus, animationSpec = animationSpec)
    }

    /** Rotates the map clockwise by [delta] degrees while keeping [focus] fixed on screen. */
    fun rotateBy(
        delta: Double,
        focus: ScreenPoint? = null,
    ) {
        cancelCameraAnimation()
        rotateByInternal(delta, focus)
    }

    private fun rotateByInternal(
        delta: Double,
        focus: ScreenPoint?,
    ) {
        val previousCenter = mapState.center
        val previousBearing = mapState.bearing
        mapState.rotateBy(delta, focus)
        if (mapState.center != previousCenter || mapState.bearing != previousBearing) {
            markCameraChanged(mapState.cameraPosition)
        }
    }

    /** Sets the clockwise map [bearing], taking the shortest path around north. */
    fun setBearing(
        bearing: Double,
        focus: ScreenPoint? = null,
    ) {
        require(bearing.isFinite()) { "bearing must be finite" }
        cancelCameraAnimation()
        rotateByInternal(shortestBearingDelta(mapState.bearing, bearing), focus)
    }

    /** Animates a clockwise rotation by [delta] degrees. */
    suspend fun animateRotateBy(
        delta: Double,
        focus: ScreenPoint? = null,
        animationSpec: AnimationSpec<Float> = DefaultRotationAnimationSpec,
    ) = runCameraAnimation {
        require(delta.isFinite()) { "delta must be finite" }
        if (delta == 0.0) return@runCameraAnimation
        val target = delta.toFloat()
        require(target.isFinite()) { "delta must be representable as Float" }

        var previousValue = 0.0
        var expectedRevision = mapState.cameraRevision
        AnimationState(initialValue = 0f)
            .animateTo(targetValue = target, animationSpec = animationSpec) {
                ensureCameraRevision(expectedRevision)
                rotateByInternal(value.toDouble() - previousValue, focus)
                previousValue = value.toDouble()
                expectedRevision = mapState.cameraRevision
            }
    }

    /** Animates to [bearing] using the shortest path around north. */
    suspend fun animateBearingTo(
        bearing: Double,
        focus: ScreenPoint? = null,
        animationSpec: AnimationSpec<Float> = DefaultRotationAnimationSpec,
    ) {
        require(bearing.isFinite()) { "bearing must be finite" }
        animateRotateBy(shortestBearingDelta(mapState.bearing, bearing), focus, animationSpec)
    }

    /** Resets bearing to north and centers [bounds], leaving density-independent [padding]. */
    fun fitBounds(
        bounds: BoundingBox,
        padding: Dp = 48.dp,
    ) {
        cancelCameraAnimation()
        val previousCenter = mapState.center
        val previousZoom = mapState.zoom
        val previousBearing = mapState.bearing
        val requestedPaddingPx = padding.value * mapState.viewport.pixelRatio
        val smallestViewportDimension = minOf(mapState.viewport.width, mapState.viewport.height).toDouble()
        val maxPaddingPx = ((smallestViewportDimension - 1.0) / 2.0).coerceAtLeast(0.0)
        mapState.fitBounds(bounds, requestedPaddingPx.coerceAtMost(maxPaddingPx))
        if (
            mapState.center != previousCenter ||
            mapState.zoom != previousZoom ||
            mapState.bearing != previousBearing
        ) {
            markCameraChanged(mapState.cameraPosition)
        }
    }

    internal fun markChanged() {
        publishPosition(mapState.cameraPosition)
    }

    internal fun cancelCameraAnimation() {
        activeAnimationJob?.cancel()
        activeAnimationJob = null
    }

    private fun setCameraInternal(position: CameraPosition) {
        val currentPosition = mapState.setCamera(position)
        if (currentPosition != observablePosition) {
            markCameraChanged(currentPosition)
        }
    }

    private fun publishPosition(currentPosition: CameraPosition) {
        revision += 1
        val previousPosition = observablePosition
        if (currentPosition.zoom != previousPosition.zoom) {
            zoomRevision += 1
        }
        if (currentPosition != previousPosition) {
            observablePosition = currentPosition
        }
    }

    private fun markCameraChanged(currentPosition: CameraPosition) {
        publishPosition(currentPosition)
        cameraControlRevision += 1
    }

    private suspend fun runCameraAnimation(block: suspend () -> Unit) {
        coroutineScope {
            val animationJob = coroutineContext.job
            activeAnimationJob?.takeIf { it !== animationJob }?.cancel()
            activeAnimationJob = animationJob
            try {
                block()
            } finally {
                if (activeAnimationJob === animationJob) {
                    activeAnimationJob = null
                }
            }
        }
    }

    private fun ensureCameraRevision(expectedRevision: Long) {
        if (mapState.cameraRevision != expectedRevision) {
            throw CancellationException("Camera changed outside the active animation")
        }
    }

    private companion object {
        val DefaultZoomAnimationSpec: AnimationSpec<Float> =
            tween(durationMillis = 220, easing = FastOutSlowInEasing)
        val DefaultPanAnimationSpec: AnimationSpec<Float> =
            tween(durationMillis = 260, easing = FastOutSlowInEasing)
        val DefaultRotationAnimationSpec: AnimationSpec<Float> =
            tween(durationMillis = 220, easing = FastOutSlowInEasing)
        val DefaultCameraAnimationSpec: AnimationSpec<Float> =
            tween(durationMillis = 300, easing = FastOutSlowInEasing)

        fun lerp(
            start: Double,
            end: Double,
            progress: Double,
        ): Double = start + (end - start) * progress

        fun normalizeBearing(value: Double): Double {
            val normalized = value % 360.0
            return if (normalized < 0.0) normalized + 360.0 else normalized
        }
    }
}

internal fun shortestBearingDelta(
    from: Double,
    to: Double,
): Double {
    val delta = (to - from) % 360.0
    return when {
        delta > 180.0 -> delta - 360.0
        delta < -180.0 -> delta + 360.0
        else -> delta
    }
}

/**
 * Receiver for the `TiloMap { ... }` layer DSL.
 *
 * Prefer high-level methods such as [wmsTileLayer] and [featureLayer]. Use
 * [layer] when integrating a custom or pre-built layer.
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
                loadWmsCapabilities = DEFAULT_WMS_CAPABILITIES_LOADER,
            ),
        )

    internal companion object {
        private val DEFAULT_WMS_CAPABILITIES_LOADER:
            suspend (String, RasterHttpConfig) -> WmsCapabilities = { url, http ->
                WmsCapabilitiesLoader().load(url, http)
            }

        fun managed(
            rasterLayerStore: RasterLayerStore,
            loadWmsCapabilities: suspend (String, RasterHttpConfig) -> WmsCapabilities =
                DEFAULT_WMS_CAPABILITIES_LOADER,
        ): MapLayerBuilder =
            MapLayerBuilder(
                MapLayerBuildContext(
                    rasterLayerStore = rasterLayerStore,
                    loadWmsCapabilities = loadWmsCapabilities,
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
        attributions: List<Attribution> = emptyList(),
        opacity: Double = 1.0,
        layers: MapLayerBuilder.() -> Unit,
    ) = addLayerGroup(id, zIndex, visible, minZoom, maxZoom, attributions, opacity, layers)

    private fun addLayerGroup(
        id: String,
        zIndex: Int,
        visible: Boolean,
        minZoom: Double?,
        maxZoom: Double?,
        attributions: List<Attribution>,
        opacity: Double,
        layers: MapLayerBuilder.() -> Unit,
    ) {
        validateLayerPresentation(opacity, minZoom, maxZoom)
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
                attributions = attributions.toList(),
                children = childBuilder.items.toList(),
            )
    }

    /**
     * Adds a WMS raster layer discovered through GetCapabilities.
     *
     * The map loads capabilities asynchronously, owns the resulting raster
     * runtime, and closes it when the declaration leaves composition. The layer
     * is skipped until loading succeeds. Optional presentation, loading, WMS,
     * and HTTP settings live in [WmsTileLayerOptions]. Styles are specified one
     * per layer name; an empty style list selects every layer's default style.
     */
    fun wmsTileLayer(
        id: String,
        capabilitiesUrl: String,
        layerNames: List<String>,
        projection: Projection,
        block: WmsTileLayerOptions.() -> Unit = {},
    ) {
        val names = layerNames.toList()
        require(names.isNotEmpty()) { "At least one WMS layer name is required." }
        require(names.none(String::isBlank)) { "WMS layer names must not be blank." }
        require(names.none { ',' in it }) { "WMS layer names must not contain commas." }
        val options = WmsTileLayerOptions().apply(block)
        val styles = options.styles.toList()
        require(styles.isEmpty() || styles.size == names.size) {
            "WMS styles must be empty or contain exactly one entry per layer name."
        }
        require(styles.none { ',' in it }) { "WMS style names must not contain commas." }
        validateLayerPresentation(options.opacity, options.minZoom, options.maxZoom)
        require(options.tileSize > 0) { "WMS tileSize must be positive." }
        validateRasterLoading(
            options.maxVisibleTiles,
            options.prefetchMargin,
            options.overviewZoomOffset,
            options.maxOverviewTiles,
            options.overviewPrefetchMargin,
        )
        registerLayerId(id)
        val axisOrder = options.axisOrder ?: WmsAxisOrder.forCrs(projection.id)
        val http = options.http
        val configuration =
            WmsRasterConfiguration(
                capabilitiesUrl = capabilitiesUrl,
                layerNames = names,
                projectionId = projection.id,
                projectionDefinition = projection.definition,
                projectionWorldUnitsPerMapUnit = projection.worldUnitsPerMapUnit,
                styles = styles,
                format = options.format,
                version = options.version,
                axisOrder = axisOrder,
                transparent = options.transparent,
                tileSize = options.tileSize,
                maxVisibleTiles = options.maxVisibleTiles,
                prefetchMargin = options.prefetchMargin,
                overviewZoomOffset = options.overviewZoomOffset,
                maxOverviewTiles = options.maxOverviewTiles,
                overviewPrefetchMargin = options.overviewPrefetchMargin,
                http = http,
                retryKey = options.state?.retryKey ?: 0,
            )
        val key =
            ManagedWmsLayerKey(
                layerId = id,
                configuration = configuration,
            )
        items +=
            MapLayerItem.ManagedWms(
                ManagedWmsLayerDeclaration(
                    key = key,
                    id = id,
                    zIndex = options.zIndex,
                    visible = options.visible,
                    opacity = options.opacity,
                    minZoom = options.minZoom,
                    maxZoom = options.maxZoom,
                    attributions = options.attributions.toList(),
                    state = options.state,
                    onError = options.onError,
                    create = { reportError, reportDiagnostic ->
                        val capabilities =
                            context.loadWmsCapabilities(configuration.capabilitiesUrl, configuration.http)
                        capabilities.createTileLayer(
                            id = id,
                            layerNames = configuration.layerNames,
                            projection = projection,
                            tileSize = configuration.tileSize,
                            options =
                                WmsLayerOptions(
                                    styles = configuration.styles,
                                    format = configuration.format,
                                    version = configuration.version,
                                    axisOrder = configuration.axisOrder,
                                    transparent = configuration.transparent,
                                    maxVisibleTiles = configuration.maxVisibleTiles,
                                    prefetchMargin = configuration.prefetchMargin,
                                    overviewZoomOffset = configuration.overviewZoomOffset,
                                    maxOverviewTiles = configuration.maxOverviewTiles,
                                    overviewPrefetchMargin = configuration.overviewPrefetchMargin,
                                    http = configuration.http,
                                    onError = reportError,
                                    onDiagnostic = reportDiagnostic,
                                ),
                        )
                    },
                ),
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
        ) {
            this.zIndex = zIndex
            this.visible = visible
            this.opacity = opacity
            this.minZoom = minZoom
            this.maxZoom = maxZoom
            projection = Epsg3857Projection
            prefetchMargin = 0
            overviewZoomOffset = 0
            maxOverviewTiles = 0
            overviewPrefetchMargin = 0
            attributions = listOf(OPEN_STREET_MAP_ATTRIBUTION)
            this.state = state
            this.onError = onError
        }
    }

    /**
     * Adds a URL-template raster layer using `{z}`, `{x}`, `{y}` placeholders.
     *
     * Web Mercator is the default because that is what public XYZ slippy-map
     * services normally use. Optional source, presentation, loading, and HTTP
     * settings live in [XyzTileLayerOptions].
     */
    fun xyzTileLayer(
        id: String,
        urlTemplate: String,
        block: XyzTileLayerOptions.() -> Unit = {},
    ) {
        val options = XyzTileLayerOptions().apply(block)
        val projection = options.projection
        val grid = options.grid ?: TileGrid.defaultFor(projection)
        val attributions = options.attributions.toList()
        managedRasterLayer(
            id = id,
            zIndex = options.zIndex,
            visible = options.visible,
            opacity = options.opacity,
            minZoom = options.minZoom,
            maxZoom = options.maxZoom,
            attributions = attributions,
            configuration =
                XyzRasterConfiguration(
                    projectionId = projection.id,
                    projectionDefinition = projection.definition,
                    projectionWorldUnitsPerMapUnit = projection.worldUnitsPerMapUnit,
                    grid = grid,
                    urlTemplate = urlTemplate,
                    tms = options.tms,
                    maxVisibleTiles = options.maxVisibleTiles,
                    prefetchMargin = options.prefetchMargin,
                    overviewZoomOffset = options.overviewZoomOffset,
                    maxOverviewTiles = options.maxOverviewTiles,
                    overviewPrefetchMargin = options.overviewPrefetchMargin,
                    http = options.http,
                    retryKey = options.state?.retryKey ?: 0,
                ),
            update = RasterLayerUpdate.Source(options.state, options.onError),
        ) {
            val diagnostics = MutableRasterLayerDiagnostics(options.state, options.onError)
            StoredRasterLayer(
                layer =
                    XyzTileLayer(
                        id = id,
                        projection = projection,
                        grid = grid,
                        urlTemplate = urlTemplate,
                        tms = options.tms,
                        zIndex = options.zIndex,
                        visible = options.visible,
                        opacity = options.opacity,
                        minZoom = options.minZoom,
                        maxZoom = options.maxZoom,
                        maxVisibleTiles = options.maxVisibleTiles,
                        prefetchMargin = options.prefetchMargin,
                        overviewZoomOffset = options.overviewZoomOffset,
                        maxOverviewTiles = options.maxOverviewTiles,
                        overviewPrefetchMargin = options.overviewPrefetchMargin,
                        attributions = attributions,
                        http = options.http,
                        onError = diagnostics::tileFailed,
                        onDiagnostic = diagnostics::onDiagnostic,
                    ),
                diagnostics = diagnostics,
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
        attributions: List<Attribution> = emptyList(),
        state: RasterLayerState? = null,
        onError: ((Throwable) -> Unit)? = null,
        opacity: Double = 1.0,
    ) {
        val resolvedAttributions = attributions.toList()
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
                    projectionDefinition = projection.definition,
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
            val diagnostics = MutableRasterLayerDiagnostics(state, onError, localSource = true)
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
                        onDiagnostic = diagnostics::onDiagnostic,
                    ),
                diagnostics = diagnostics,
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
     * Adds an in-memory vector feature layer.
     *
     * Optional presentation, projection, and rendering settings live in
     * [FeatureLayerOptions].
     */
    fun featureLayer(
        id: String,
        features: List<Feature>,
        block: FeatureLayerOptions.() -> Unit = {},
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
                options.attributions.toList(),
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
            attributions = attributions,
            features = features,
            renderStrategy = renderMode.toVectorRenderStrategy(),
            style = style,
        )

    internal val managedWmsDeclarations: List<ManagedWmsLayerDeclaration>
        get() = items.managedWmsDeclarations()

    internal val managedRasterKeys: Set<ManagedRasterLayerKey>
        get() = context.managedRasterKeys

    internal val managedRasterUpdates: Map<ManagedRasterLayerKey, RasterLayerUpdate>
        get() = context.managedRasterUpdates

    internal fun build(resolvedWmsLayers: Map<ManagedWmsLayerKey, TileLayer?> = emptyMap()): List<Layer> =
        items.buildLayers(resolvedWmsLayers)

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
        val blankId = ids.firstOrNull(String::isBlank)
        require(blankId == null) { "Layer id must not be blank" }
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
        require(id.isNotBlank()) { "Layer id must not be blank" }
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
        validateLayerPresentation(opacity, minZoom, maxZoom)
        registerLayerId(id)
        val store = context.rasterLayerStore
        val layer =
            if (store == null) {
                create().layer
            } else {
                val key = ManagedRasterLayerKey(layerId = id, configuration = configuration)
                context.managedRasterKeys += key
                context.managedRasterUpdates[key] = update
                val stored = store.getOrCreateStored(key, create)
                PresentedTileLayer(
                    runtime = stored.layer,
                    id = id,
                    zIndex = zIndex,
                    visible = visible,
                    opacity = opacity,
                    minZoom = minZoom,
                    maxZoom = maxZoom,
                    attributions = attributions,
                    diagnostics = stored.diagnostics,
                )
            }
        items += MapLayerItem.LayerValue(layer)
    }
}

private fun validateLayerPresentation(
    opacity: Double,
    minZoom: Double?,
    maxZoom: Double?,
) {
    require(opacity in 0.0..1.0) { "opacity must be between 0.0 and 1.0" }
    require(minZoom == null || minZoom.isFinite()) { "minZoom must be finite" }
    require(maxZoom == null || maxZoom.isFinite()) { "maxZoom must be finite" }
    require(minZoom == null || maxZoom == null || minZoom <= maxZoom) {
        "minZoom must not be greater than maxZoom"
    }
}

private fun validateRasterLoading(
    maxVisibleTiles: Int,
    prefetchMargin: Int,
    overviewZoomOffset: Int,
    maxOverviewTiles: Int,
    overviewPrefetchMargin: Int,
) {
    require(maxVisibleTiles > 0) { "maxVisibleTiles must be positive." }
    require(prefetchMargin >= 0) { "prefetchMargin must not be negative." }
    require(overviewZoomOffset >= 0) { "overviewZoomOffset must not be negative." }
    require(maxOverviewTiles >= 0) { "maxOverviewTiles must not be negative." }
    require(overviewPrefetchMargin >= 0) { "overviewPrefetchMargin must not be negative." }
}

private class MapLayerBuildContext(
    val rasterLayerStore: RasterLayerStore?,
    val loadWmsCapabilities: suspend (String, RasterHttpConfig) -> WmsCapabilities,
) {
    val layerIds = mutableSetOf<String>()
    val managedRasterKeys = mutableSetOf<ManagedRasterLayerKey>()
    val managedRasterUpdates = mutableMapOf<ManagedRasterLayerKey, RasterLayerUpdate>()
}

private sealed interface MapLayerItem {
    class LayerValue(
        val layer: Layer,
    ) : MapLayerItem

    class ManagedWms(
        val declaration: ManagedWmsLayerDeclaration,
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

private fun List<MapLayerItem>.managedWmsDeclarations(): List<ManagedWmsLayerDeclaration> =
    flatMap { item ->
        when (item) {
            is MapLayerItem.Group -> item.children.managedWmsDeclarations()
            is MapLayerItem.LayerValue -> emptyList()
            is MapLayerItem.ManagedWms -> listOf(item.declaration)
        }
    }

private fun List<MapLayerItem>.buildLayers(resolvedWmsLayers: Map<ManagedWmsLayerKey, TileLayer?>): List<Layer> =
    mapNotNull { item ->
        when (item) {
            is MapLayerItem.LayerValue -> item.layer
            is MapLayerItem.ManagedWms -> resolvedWmsLayers[item.declaration.key]
            is MapLayerItem.Group ->
                LayerGroup(
                    id = item.id,
                    children = item.children.buildLayers(resolvedWmsLayers),
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
    val projectionDefinition: String,
    val projectionWorldUnitsPerMapUnit: Double,
    val grid: TileGrid,
    val urlTemplate: String,
    val tms: Boolean,
    val maxVisibleTiles: Int,
    val prefetchMargin: Int,
    val overviewZoomOffset: Int,
    val maxOverviewTiles: Int,
    val overviewPrefetchMargin: Int,
    val http: RasterHttpConfig,
    val retryKey: Int,
) {
    override fun toString(): String =
        "XyzRasterConfiguration(projectionId=$projectionId, tms=$tms, " +
            "loading=[$maxVisibleTiles,$prefetchMargin,$overviewZoomOffset,$maxOverviewTiles," +
            "$overviewPrefetchMargin], http=$http, retryKey=$retryKey)"
}

internal data class TileStoreRasterConfiguration(
    val projectionId: String,
    val projectionDefinition: String,
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

internal data class WmsRasterConfiguration(
    val capabilitiesUrl: String,
    val layerNames: List<String>,
    val projectionId: String,
    val projectionDefinition: String,
    val projectionWorldUnitsPerMapUnit: Double,
    val styles: List<String>,
    val format: WmsImageFormat?,
    val version: WmsVersion?,
    val axisOrder: WmsAxisOrder,
    val transparent: Boolean,
    val tileSize: Int,
    val maxVisibleTiles: Int,
    val prefetchMargin: Int,
    val overviewZoomOffset: Int,
    val maxOverviewTiles: Int,
    val overviewPrefetchMargin: Int,
    val http: RasterHttpConfig,
    val retryKey: Int,
) {
    override fun toString(): String =
        "WmsRasterConfiguration(projectionId=$projectionId, version=$version, transparent=$transparent, " +
            "tileSize=$tileSize, " +
            "layerCount=${layerNames.size}, styleCount=${styles.size}, http=$http, retryKey=$retryKey)"
}

private class MutableTileReader(
    var delegate: suspend (TileCoordinate) -> ByteArray?,
) {
    suspend fun read(coordinate: TileCoordinate): ByteArray? = delegate(coordinate)
}

/** HTTP settings shared by WMS metadata and tile requests, or by one XYZ source. */
@ExperimentalTiloApi
@TiloDsl
class RasterHttpOptions internal constructor(
    config: RasterHttpConfig = RasterHttpConfig(),
) {
    var headers: Map<String, String> = config.headers
    var transport: RasterHttpTransport? = config.transport

    /** Allows WMS request headers on a GetMap origin different from GetCapabilities. */
    var allowCrossOriginHeaders: Boolean = config.allowCrossOriginHeaders

    /** Change this stable semantic key when [transport] must recreate the raster runtime. */
    var transportKey: Any = config.transportKey ?: Unit

    /** Adds or replaces one request header. */
    fun header(
        name: String,
        value: String,
    ) {
        require(name.isNotBlank()) { "HTTP header name must not be blank." }
        headers = headers.filterKeys { !it.equals(name, ignoreCase = true) } + (name to value)
    }

    /** Configures an `Authorization: Bearer …` header. */
    fun bearerToken(token: String) {
        require(token.isNotBlank()) { "Bearer token must not be blank." }
        header("Authorization", "Bearer $token")
    }

    internal fun build(): RasterHttpConfig =
        RasterHttpConfig(
            headers = headers.toMap(),
            transport = transport,
            transportKey = transportKey,
            allowCrossOriginHeaders = allowCrossOriginHeaders,
        )
}

/** Optional settings for [MapLayerBuilder.wmsTileLayer]. */
@ExperimentalTiloApi
@TiloDsl
class WmsTileLayerOptions {
    var styles: List<String> = emptyList()
    var format: WmsImageFormat? = null
    var version: WmsVersion? = null
    var axisOrder: WmsAxisOrder? = null

    /**
     * Requests transparent WMS background and no-data pixels from the server.
     *
     * This does not change client-side [opacity]. The default is false, and the
     * selected image format must support transparency for the server to honour the request.
     * With no explicit [format], Tilo selects an advertised transparency-capable
     * format and fails layer creation if the service offers none. An explicit
     * [format] overrides that automatic selection.
     */
    var transparent: Boolean = false
    var zIndex: Int = 0
    var visible: Boolean = true

    /** Client-side alpha applied while composing the complete WMS tile layer. */
    var opacity: Double = 1.0
    var minZoom: Double? = null
    var maxZoom: Double? = null
    var tileSize: Int = 256
    var maxVisibleTiles: Int = 9
    var prefetchMargin: Int = 0
    var overviewZoomOffset: Int = 0
    var maxOverviewTiles: Int = 4
    var overviewPrefetchMargin: Int = 0
    var attributions: List<Attribution> = emptyList()
    var state: RasterLayerState? = null
    var onError: ((Throwable) -> Unit)? = null

    internal var http: RasterHttpConfig = RasterHttpConfig()
        private set

    /** Configures authentication, headers, or a custom transport for capabilities and tiles. */
    fun http(block: RasterHttpOptions.() -> Unit) {
        http = RasterHttpOptions(http).apply(block).build()
    }
}

/** Optional settings for [MapLayerBuilder.xyzTileLayer]. */
@ExperimentalTiloApi
@TiloDsl
class XyzTileLayerOptions {
    var projection: Projection = Epsg3857Projection
    var grid: TileGrid? = null
    var tms: Boolean = false
    var zIndex: Int = 0
    var visible: Boolean = true
    var opacity: Double = 1.0
    var minZoom: Double? = null
    var maxZoom: Double? = null
    var maxVisibleTiles: Int = 9
    var prefetchMargin: Int = 0
    var overviewZoomOffset: Int = 0
    var maxOverviewTiles: Int = 4
    var overviewPrefetchMargin: Int = 0
    var attributions: List<Attribution> = emptyList()
    var state: RasterLayerState? = null
    var onError: ((Throwable) -> Unit)? = null

    internal var http: RasterHttpConfig = RasterHttpConfig()
        private set

    /** Configures authentication, headers, or a custom transport for tile requests. */
    fun http(block: RasterHttpOptions.() -> Unit) {
        http = RasterHttpOptions(http).apply(block).build()
    }
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
 * Remembers camera state for [TiloMap] from one immutable initial camera position.
 *
 * `initialPosition` is used only when the state is first created. [projection] and [config] are
 * immutable properties of [MapCameraState]; changing either replaces the remembered state.
 */
@Composable
@ExperimentalTiloApi
fun rememberMapCameraState(
    initialPosition: CameraPosition,
    projection: Projection = IdentityProjection,
    config: MapConfig = MapConfig.Default,
): MapCameraState =
    remember(projection, config) {
        MapCameraState(
            mapState =
                MapState(
                    center = initialPosition.center,
                    zoom = initialPosition.zoom,
                    projection = projection,
                    config = config,
                    transformationRegistry = TransformationRegistry(providers = listOf(ProjTransformationProvider)),
                    bearing = initialPosition.bearing,
                ),
        )
    }

/**
 * Convenience overload that remembers camera state from separate initial components.
 *
 * [initialCenter], [initialZoom], and [initialBearing] initialize a newly remembered state and are
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
    initialBearing: Double = 0.0,
): MapCameraState =
    rememberMapCameraState(
        initialPosition = CameraPosition(initialCenter, initialZoom, initialBearing),
        projection = projection,
        config = config,
    )

/**
 * Compose map surface.
 *
 * Declare raster, vector, drawing, and custom layers in [layers].
 * Interactive content in the overlay slots is placed above the map and owns pointer sequences
 * that it consumes. Non-interactive overlay space remains transparent to map input. A map drag,
 * pinch, rotation, or double tap does not invoke the single-tap callbacks. For a single map tap,
 * [onFeatureSelect] runs before [onTapWorld], including when selection has no hits.
 * [TiloMapOptions.gestureConfig] controls gesture activation thresholds; by default, rotation
 * requires an intentional eight-degree turn. Rendering diagnostics are published when
 * [diagnosticsState] is provided.
 * Unexpected render branch failures are reported through [onRenderError];
 * ordinary missing or undecodable raster tiles remain isolated.
 */
@Composable
@ExperimentalTiloApi
fun TiloMap(
    cameraState: MapCameraState,
    modifier: Modifier = Modifier,
    options: TiloMapOptions = TiloMapOptions(),
    diagnosticsState: MapDiagnosticsState? = null,
    onTapWorld: ((Point) -> Unit)? = null,
    onFeatureSelect: ((List<FeatureSelection>) -> Unit)? = null,
    onRenderError: ((Throwable) -> Unit)? = null,
    selectedFeatures: Set<FeatureSelectionRef> = emptySet(),
    attributionContent: (@Composable BoxScope.(List<Attribution>) -> Unit)? = null,
    scaleBarContent: (@Composable BoxScope.(ScaleBar) -> Unit)? = null,
    cameraControlsContent: (@Composable BoxScope.(MapCameraState) -> Unit)? = null,
    layers: MapLayerBuilder.() -> Unit,
) = TiloMapImpl(
    cameraState = cameraState,
    modifier = modifier,
    gestureConfig = options.gestureConfig,
    accessibility = options.accessibility,
    onTapWorld = onTapWorld,
    onFeatureSelect = onFeatureSelect,
    onRenderError = onRenderError,
    selectedFeatures = selectedFeatures,
    attributionContent = attributionContent,
    scaleBarContent = scaleBarContent,
    cameraControlsContent = cameraControlsContent,
    diagnosticsState = diagnosticsState,
    layers = layers,
)

@Composable
private fun TiloMapImpl(
    cameraState: MapCameraState,
    modifier: Modifier,
    gestureConfig: MapGestureConfig,
    accessibility: MapAccessibilityOptions,
    onTapWorld: ((Point) -> Unit)?,
    onFeatureSelect: ((List<FeatureSelection>) -> Unit)?,
    onRenderError: ((Throwable) -> Unit)?,
    selectedFeatures: Set<FeatureSelectionRef>,
    attributionContent: (@Composable BoxScope.(List<Attribution>) -> Unit)?,
    scaleBarContent: (@Composable BoxScope.(ScaleBar) -> Unit)?,
    cameraControlsContent: (@Composable BoxScope.(MapCameraState) -> Unit)?,
    diagnosticsState: MapDiagnosticsState?,
    layers: MapLayerBuilder.() -> Unit,
) {
    val builtLayers = rememberManagedMapLayers(layers)
    val focusTraversal = remember { MapFocusTraversal() }
    CompositionLocalProvider(LocalTiloMapFocusTraversal provides focusTraversal) {
        Box(modifier = modifier) {
            MapRendererLayer(
                cameraState = cameraState,
                accessibility = accessibility,
                layers = builtLayers,
                gestureConfig = gestureConfig,
                onTapWorld = onTapWorld,
                onFeatureSelect = onFeatureSelect,
                onRenderError = onRenderError,
                selectedFeatures = selectedFeatures,
                diagnosticsState = diagnosticsState,
            )
            BottomMapOverlays(
                cameraState = cameraState,
                layers = builtLayers,
                attributionContent = attributionContent,
                scaleBarContent = scaleBarContent,
            )
            if (cameraControlsContent != null) {
                cameraControlsContent(cameraState)
            }
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
    val resolvedWmsLayers =
        buildMap {
            layerBuilder.managedWmsDeclarations.forEach { declaration ->
                key(declaration.key) {
                    put(declaration.key, rememberManagedWmsLayer(declaration))
                }
            }
        }
    val builtLayers = layerBuilder.build(resolvedWmsLayers)
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
private fun BoxScope.BottomMapOverlays(
    cameraState: MapCameraState,
    layers: List<Layer>,
    attributionContent: (@Composable BoxScope.(List<Attribution>) -> Unit)?,
    scaleBarContent: (@Composable BoxScope.(ScaleBar) -> Unit)?,
) {
    val scaleBar =
        scaleBarContent?.let {
            cameraState.revision
            ScaleBarCalculator.calculate(cameraState.mapState)
        }
    val attributions =
        attributionContent
            ?.let {
                cameraState.zoomRevision
                remember(layers) { ResolvedLayerTree.resolve(layers) }.activeAttributions(cameraState.zoom)
            }.orEmpty()

    when {
        scaleBar != null && attributions.isNotEmpty() -> {
            Row(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                Box { scaleBarContent(scaleBar) }
                Box(modifier = Modifier.weight(1.0f)) {
                    attributionContent?.invoke(this, attributions)
                }
            }
        }

        scaleBar != null -> scaleBarContent(scaleBar)
        attributions.isNotEmpty() -> attributionContent?.invoke(this, attributions)
    }
}

@Composable
private fun MapRendererLayer(
    cameraState: MapCameraState,
    accessibility: MapAccessibilityOptions,
    layers: List<Layer>,
    gestureConfig: MapGestureConfig,
    onTapWorld: ((Point) -> Unit)?,
    onFeatureSelect: ((List<FeatureSelection>) -> Unit)?,
    onRenderError: ((Throwable) -> Unit)?,
    selectedFeatures: Set<FeatureSelectionRef>,
    diagnosticsState: MapDiagnosticsState?,
) {
    cameraState.revision
    MapRenderer(
        map = cameraState.mapState,
        layers = layers,
        diagnosticsState = diagnosticsState,
        gestureConfig = gestureConfig,
        modifier = Modifier.fillMaxSize().mapAccessibility(cameraState, accessibility),
        onTapWorld = onTapWorld,
        onFeatureSelect = onFeatureSelect,
        onRenderError = onRenderError,
        selectedFeatures = selectedFeatures,
        onMapChanged = cameraState::markChanged,
        onCameraInteractionStarted = cameraState::cancelCameraAnimation,
    )
}
