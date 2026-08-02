@file:OptIn(ExperimentalTiloRenderingApi::class)

package tilo.compose.render

import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tilo.compose.core.feature.Feature
import tilo.compose.core.feature.FeatureLayerStyle
import tilo.compose.core.layers.vector.VectorLayer
import tilo.compose.core.layers.vector.VectorRenderStrategy
import tilo.compose.core.map.MapState
import tilo.compose.core.selection.FeatureSelectionRef
import tilo.compose.core.transform.TransformationRegistry
import tilo.compose.render.backend.VectorBitmapRenderSceneLayer
import tilo.compose.render.backend.VectorBitmapSnapshot
import kotlin.math.abs
import kotlin.math.pow

internal data class VectorFrame(
    val commandsByLayer: Map<String, List<RenderCommand>>,
    val bitmapLayersByLayer: Map<String, VectorBitmapRenderSceneLayer>,
    val cacheKeysByLayer: Map<String, VectorLayerCacheKey>,
    val projectionSnapshotsByLayer: Map<String, FeatureProjectionSnapshot> = emptyMap(),
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
    val source: Any,
    val sourceVersion: Long,
    val renderStrategy: VectorRenderStrategy,
    val style: FeatureLayerStyle,
    val pointIcons: Map<String, Painter>,
    val selectedFeatureKeys: Set<String>,
    val sourceProjectionId: String?,
    val sourceProjectionDefinition: String?,
    val targetProjectionId: String?,
    val targetProjectionDefinition: String?,
    val targetWorldUnitsPerMapUnit: Double?,
    val targetPixelRatio: Double?,
    val transformationRegistry: TransformationRegistry?,
    val density: Float?,
    val fontScale: Float?,
    val layoutDirection: LayoutDirection?,
)

internal class VectorRenderPipeline(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val bitmapRenderer: VectorBitmapRenderTarget = VectorBitmapRenderer(),
) {
    private val featureProjectionCache = FeatureProjectionCache()

    suspend fun buildFrame(
        vectorLayers: List<VectorLayer>,
        map: MapState,
        density: Density,
        layoutDirection: LayoutDirection,
        selectedFeatures: Set<FeatureSelectionRef> = emptySet(),
        reusableBitmapsByLayer: Map<String, VectorBitmapRenderSceneLayer> = emptyMap(),
    ): VectorFrame =
        withContext(dispatcher) {
            featureProjectionCache.retainLayers(vectorLayers.mapTo(mutableSetOf()) { it.id })
            val commandsByLayer = mutableMapOf<String, List<RenderCommand>>()
            val bitmapLayersByLayer = mutableMapOf<String, VectorBitmapRenderSceneLayer>()
            val cacheKeysByLayer = mutableMapOf<String, VectorLayerCacheKey>()
            val projectionSnapshotsByLayer = mutableMapOf<String, FeatureProjectionSnapshot>()
            var returnedFeatures = 0
            var visibleFeatures = 0
            var geometryCommands = 0
            var labelCommands = 0
            var bitmapLayersReused = 0
            var bitmapLayersRebuilt = 0

            vectorLayers.forEach { layer ->
                val selectedFeatureKeys = selectedFeatures.keysForLayer(layer.id)
                cacheKeysByLayer[layer.id] =
                    layer.cacheKey(selectedFeatureKeys, map.zoom, map, density, layoutDirection)
                val strategy = layer.renderStrategy
                val reusableBitmap =
                    (strategy as? VectorRenderStrategy.CachedBitmap)?.let { cachedStrategy ->
                        reusableBitmapsByLayer[layer.id]
                            ?.takeIf { it.snapshot.canCover(map, cachedStrategy) }
                            ?.takeIf {
                                layer.source.supportsBufferedQueries ||
                                    it.snapshot.matchesCamera(map, cachedStrategy)
                            }
                    }
                if (reusableBitmap != null && !layer.style.resolveAtZoom(map.zoom).labelsVisible) {
                    featureProjectionCache
                        .snapshot(layer.id, layer.source, layer.source.version, layer.projection, map)
                        ?.let { projectionSnapshotsByLayer[layer.id] = it }
                    commandsByLayer[layer.id] = emptyList()
                    bitmapLayersByLayer[layer.id] = reusableBitmap
                    bitmapLayersReused += 1
                    return@forEach
                }
                val contentMap =
                    if (
                        strategy is VectorRenderStrategy.CachedBitmap &&
                        reusableBitmap == null &&
                        layer.source.supportsBufferedQueries
                    ) {
                        map.forBitmapBuffer(strategy)
                    } else {
                        map
                    }
                val features = layer.source.getFeatures(contentMap)
                returnedFeatures += features.size
                val projected =
                    featureProjectionCache.transform(
                        layerId = layer.id,
                        sourceIdentity = layer.source,
                        sourceVersion = layer.source.version,
                        features = features,
                        source = layer.projection,
                        map = contentMap,
                    )
                featureProjectionCache
                    .snapshot(layer.id, layer.source, layer.source.version, layer.projection, contentMap)
                    ?.let { projectionSnapshotsByLayer[layer.id] = it }
                val buildResult =
                    buildFrameCommands(
                        layer = layer,
                        map = map,
                        contentMap = contentMap,
                        features = projected,
                        selectedFeatureKeys = selectedFeatureKeys,
                        includeGeometry = reusableBitmap == null,
                    )
                val commands = buildResult.commands
                visibleFeatures += buildResult.visibleFeatureCount
                geometryCommands += buildResult.geometryCommandCount
                labelCommands += buildResult.labelCommandCount

                when (strategy) {
                    VectorRenderStrategy.Immediate -> {
                        commandsByLayer[layer.id] = commands
                    }

                    is VectorRenderStrategy.CachedBitmap -> {
                        val labels = commands.filterIsInstance<RenderLabel>()
                        val geometry = commands.filterNot { it is RenderLabel }
                        commandsByLayer[layer.id] = labels
                        val bitmapLayer =
                            reusableBitmap
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
                projectionSnapshotsByLayer = projectionSnapshotsByLayer,
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
            featureProjectionCache.retainLayers(vectorLayers.mapTo(mutableSetOf()) { it.id })
            buildMap {
                vectorLayers.forEach { layer ->
                    val selectedFeatureKeys = selectedFeatures.keysForLayer(layer.id)
                    val features = layer.source.getFeatures(map)
                    val projected =
                        featureProjectionCache.transform(
                            layerId = layer.id,
                            sourceIdentity = layer.source,
                            sourceVersion = layer.source.version,
                            features = features,
                            source = layer.projection,
                            map = map,
                        )
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

private fun buildFrameCommands(
    layer: VectorLayer,
    map: MapState,
    contentMap: MapState,
    features: List<Feature>,
    selectedFeatureKeys: Set<String>,
    includeGeometry: Boolean,
): CommandBuildResult {
    if (contentMap === map) {
        return CommandBuilder.buildWithMetrics(
            map = map,
            features = features,
            layerId = layer.id,
            selectedFeatureKeys = selectedFeatureKeys,
            layerStyle = layer.style,
            includeGeometry = includeGeometry,
        )
    }

    val bufferedGeometry =
        CommandBuilder.buildWithMetrics(
            map = contentMap,
            features = features,
            layerId = layer.id,
            selectedFeatureKeys = selectedFeatureKeys,
            layerStyle = layer.style,
        )
    val viewportLabels =
        CommandBuilder.buildWithMetrics(
            map = map,
            features = features,
            layerId = layer.id,
            selectedFeatureKeys = selectedFeatureKeys,
            layerStyle = layer.style,
            includeGeometry = false,
        )
    return CommandBuildResult(
        commands = bufferedGeometry.commands.filterNot { it is RenderLabel } + viewportLabels.commands,
        visibleFeatureCount = viewportLabels.visibleFeatureCount,
        geometryCommandCount = bufferedGeometry.geometryCommandCount,
        labelCommandCount = viewportLabels.labelCommandCount,
    )
}

internal fun VectorLayer.cacheKey(
    selectedFeatureKeys: Set<String> = emptySet(),
    zoom: Double? = null,
    map: MapState? = null,
    density: Density? = null,
    layoutDirection: LayoutDirection? = null,
): VectorLayerCacheKey =
    VectorLayerCacheKey(
        layerId = id,
        source = source,
        sourceVersion = source.version,
        renderStrategy = renderStrategy,
        style = zoom?.let(style::resolveAtZoom) ?: style,
        pointIcons = (this as? PointIconPainterLayer)?.pointIconPainters.orEmpty(),
        selectedFeatureKeys = selectedFeatureKeys,
        sourceProjectionId = projection?.id,
        sourceProjectionDefinition = projection?.definition,
        targetProjectionId = map?.projection?.id,
        targetProjectionDefinition = map?.projection?.definition,
        targetWorldUnitsPerMapUnit = map?.projection?.worldUnitsPerMapUnit,
        targetPixelRatio = map?.viewport?.pixelRatio,
        transformationRegistry = map?.transformationRegistry,
        density = density?.density,
        fontScale = density?.fontScale,
        layoutDirection = layoutDirection,
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

private fun VectorBitmapSnapshot.matchesCamera(
    map: MapState,
    strategy: VectorRenderStrategy.CachedBitmap,
): Boolean =
    center == map.center &&
        zoom == map.zoom &&
        bearing == map.bearing &&
        displayWidth == map.viewport.width + strategy.paddingPx * 2 &&
        displayHeight == map.viewport.height + strategy.paddingPx * 2

private fun MapState.forBitmapBuffer(strategy: VectorRenderStrategy.CachedBitmap): MapState =
    forOffscreenViewport(
        width = viewport.width + strategy.paddingPx * 2,
        height = viewport.height + strategy.paddingPx * 2,
        bitmapScale = 1.0,
    )

private const val ZOOM_COMPARISON_EPSILON = 1e-9
private const val BEARING_COMPARISON_EPSILON = 1e-9

private fun Set<FeatureSelectionRef>.keysForLayer(layerId: String): Set<String> =
    asSequence()
        .filter { it.layerId == layerId }
        .map { it.featureKey }
        .toSet()
