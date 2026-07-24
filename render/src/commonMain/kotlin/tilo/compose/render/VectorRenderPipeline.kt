@file:OptIn(ExperimentalTiloRenderingApi::class)

package tilo.compose.render

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import tilo.compose.core.feature.Feature
import tilo.compose.core.feature.FeatureLayerStyle
import tilo.compose.core.geometry.BoundingBox
import tilo.compose.core.layers.vector.VectorLayer
import tilo.compose.core.layers.vector.VectorRenderStrategy
import tilo.compose.core.map.MapState
import tilo.compose.core.map.Viewport
import tilo.compose.core.selection.FeatureSelectionRef
import tilo.compose.render.backend.VectorBitmapRenderSceneLayer
import tilo.compose.render.backend.VectorBitmapSnapshot
import kotlin.math.abs
import kotlin.math.pow
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal data class VectorFrame(
    val commandsByLayer: Map<String, List<RenderCommand>>,
    val cachedGeometryByLayer: Map<String, CachedGeometry> = emptyMap(),
    val bitmapLayersByLayer: Map<String, VectorBitmapRenderSceneLayer>,
    val cacheKeysByLayer: Map<String, VectorLayerCacheKey>,
)

internal data class VectorLayerCacheKey(
    val layerId: String,
    val sourceIdentity: Int,
    val sourceVersion: Long,
    val renderStrategy: VectorRenderStrategy,
    val styleHash: Int,
    val pointIconsHash: Int,
    val selectedFeatureKeys: Set<String>,
)

internal class VectorRenderPipeline(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val bitmapRenderer: VectorBitmapRenderTarget = VectorBitmapRenderer(),
) {
    private var layerCaches: Map<String, LayerPreparationCache> = emptyMap()

    suspend fun buildFrame(
        vectorLayers: List<VectorLayer>,
        map: MapState,
        density: Density,
        layoutDirection: LayoutDirection,
        selectedFeatures: Set<FeatureSelectionRef> = emptySet(),
        reusableBitmapsByLayer: Map<String, VectorBitmapRenderSceneLayer> = emptyMap(),
        performanceLogger: RenderPerformanceLogger? = null,
    ): VectorFrame =
        withContext(dispatcher) {
            val previousCaches = layerCaches
            val results = coroutineScope {
                vectorLayers.map { layer ->
                    async {
                        buildLayer(
                            layer = layer,
                            map = map,
                            density = density,
                            layoutDirection = layoutDirection,
                            selectedFeatures = selectedFeatures,
                            reusableBitmapsByLayer = reusableBitmapsByLayer,
                            performanceLogger = performanceLogger,
                            previousCache = previousCaches[layer.id],
                        )
                    }
                }.awaitAll()
            }
            results.forEach { result -> performanceLogger?.log(result.performanceEvent) }
            layerCaches = results.associate { it.layer.id to it.cache }
            VectorFrame(
                commandsByLayer = results.associate { result -> result.layer.id to result.commands },
                cachedGeometryByLayer =
                    results.mapNotNull { result -> result.cachedGeometry?.let { result.layer.id to it } }.toMap(),
                bitmapLayersByLayer = results.mapNotNull { result -> result.bitmapLayer?.let { result.layer.id to it } }.toMap(),
                cacheKeysByLayer = results.associate { it.layer.id to it.cacheKey },
            )
        }

    private suspend fun buildLayer(
        layer: VectorLayer,
        map: MapState,
        density: Density,
        layoutDirection: LayoutDirection,
        selectedFeatures: Set<FeatureSelectionRef>,
        reusableBitmapsByLayer: Map<String, VectorBitmapRenderSceneLayer>,
        performanceLogger: RenderPerformanceLogger?,
        previousCache: LayerPreparationCache?,
    ): LayerBuildResult {
        val layerStart = performanceLogger?.let { TimeSource.Monotonic.markNow() }
        val selectedFeatureKeys = selectedFeatures.keysForLayer(layer.id)
        val queryStart = performanceLogger?.let { TimeSource.Monotonic.markNow() }
        val reusableQuery = previousCache?.query?.takeIf { it.canServe(layer, map) }
        val query = reusableQuery ?: createBufferedQuery(layer, map)
        val queryMillis = queryStart?.elapsedMillis() ?: 0.0

        val projectionStart = performanceLogger?.let { TimeSource.Monotonic.markNow() }
        val projectionKey = ProjectionCacheKey.from(layer, map)
        val reusableProjected = previousCache?.takeIf {
            it.projectionKey == projectionKey && it.query === query
        }
        val priorProjectedByKey =
            previousCache
                ?.takeIf { it.projectionKey == projectionKey }
                ?.projectedByKey
                .orEmpty()
        var projectedFeatureCacheHits = 0
        val projected = reusableProjected?.projectedFeatures ?: query.features.map { feature ->
            priorProjectedByKey[feature.key]
                ?.takeIf { it.sourceFeature == feature }
                ?.projectedFeature
                ?.also { projectedFeatureCacheHits++ }
                ?: transformFeatureToMapProjection(feature, layer.projection, map)
        }
        if (reusableProjected != null) projectedFeatureCacheHits = projected.size
        val projectedByKey =
            reusableProjected?.projectedByKey ?: buildMap(projected.size) {
                query.features.forEachIndexed { index, sourceFeature ->
                    put(sourceFeature.key, ProjectedFeatureEntry(sourceFeature, projected[index]))
                }
            }
        val projectionMillis = projectionStart?.elapsedMillis() ?: 0.0

        val commandStart = performanceLogger?.let { TimeSource.Monotonic.markNow() }
        val lodToleranceWorld =
            (layer.renderStrategy as? VectorRenderStrategy.ImmediateLod)?.worldTolerance(map)
        val preparedKey =
            PreparedLayerKey(
                featureKeys = reusableProjected?.preparedKey?.featureKeys ?: projected.map(Feature::key),
                resolvedStyle = layer.style.resolveAtZoom(map.zoom),
                selectedFeatureKeys = selectedFeatureKeys,
                lodToleranceWorld = lodToleranceWorld,
                labelCamera =
                    if (query.hasLabels) {
                        LabelCameraKey(map.zoom, map.bearing)
                    } else {
                        null
                    },
            )
        val reusablePreparation = previousCache?.takeIf {
            it.projectionKey == projectionKey && it.preparedKey == preparedKey
        }
        val renderFeatures =
            if (reusablePreparation == null) {
                lodToleranceWorld?.let { tolerance -> GeometryLodSimplifier.simplify(projected, tolerance) }
                    ?: projected
            } else {
                projected
            }
        val commands = reusablePreparation?.commands ?: CommandBuilder.build(
            map = query.map,
            features = renderFeatures,
            layerId = layer.id,
            selectedFeatureKeys = selectedFeatureKeys,
            layerStyle = layer.style,
        )
        val pixelScale = WorldToScreenTransform.from(map).pixelScale.coerceAtLeast(MIN_PIXEL_SCALE)
        val cachedGeometryKey = CachedGeometryKey(pixelScale, density.density)
        val reusableCachedGeometry = previousCache?.cachedGeometry?.takeIf {
            previousCache.cachedGeometryKey == cachedGeometryKey && previousCache.commands === commands
        }
        val commandBuildMillis = commandStart?.elapsedMillis() ?: 0.0

        var bitmapMillis = 0.0
        var reusedBitmap = false
        var bitmapLayer: VectorBitmapRenderSceneLayer? = null
        val immediateCommands: List<RenderCommand>
        val immediateCachedGeometry: CachedGeometry?
        when (val strategy = layer.renderStrategy) {
            VectorRenderStrategy.Immediate,
            is VectorRenderStrategy.ImmediateLod -> {
                immediateCommands = commands
                immediateCachedGeometry =
                    reusableCachedGeometry
                        ?: CachedGeometry.build(
                            commands = commands,
                            pointWorldUnitsPerPixel = (density.density / pixelScale).toDouble(),
                        )
            }
            is VectorRenderStrategy.CachedBitmap -> {
                immediateCommands = commands.filterIsInstance<RenderLabel>()
                immediateCachedGeometry = null
                val bitmapStart = performanceLogger?.let { TimeSource.Monotonic.markNow() }
                val reusableBitmap = reusableBitmapsByLayer[layer.id]?.takeIf { it.snapshot.canCover(map, strategy) }
                reusedBitmap = reusableBitmap != null
                bitmapLayer = reusableBitmap ?: bitmapRenderer.render(
                    layer = layer,
                    commands = commands.filterNot { it is RenderLabel },
                    map = map,
                    strategy = strategy,
                    density = density,
                    layoutDirection = layoutDirection,
                )
                bitmapMillis = bitmapStart?.elapsedMillis() ?: 0.0
            }
        }

        val performanceEvent =
            VectorLayerPerformanceEvent(
                layerId = layer.id,
                featureCount = query.features.size,
                commandCount = commands.size,
                vertexCount = commands.vertexCount(),
                queryMillis = queryMillis,
                projectionMillis = projectionMillis,
                commandBuildMillis = commandBuildMillis,
                bitmapMillis = bitmapMillis,
                reusedBitmap = reusedBitmap,
                queryCacheHit = reusableQuery != null,
                projectedFeatureCacheHits = projectedFeatureCacheHits,
                commandCacheHit = reusablePreparation != null,
                totalMillis = layerStart?.elapsedMillis() ?: 0.0,
            )
        return LayerBuildResult(
            layer = layer,
            commands = immediateCommands,
            cachedGeometry = immediateCachedGeometry,
            bitmapLayer = bitmapLayer,
            cacheKey = layer.cacheKey(selectedFeatureKeys, map.zoom),
            performanceEvent = performanceEvent,
            cache =
                LayerPreparationCache(
                    query = query,
                    projectionKey = projectionKey,
                    projectedByKey = projectedByKey,
                    projectedFeatures = projected,
                    preparedKey = preparedKey,
                    commands = commands,
                    cachedGeometryKey = cachedGeometryKey,
                    cachedGeometry = immediateCachedGeometry,
                ),
        )
    }

    private fun createBufferedQuery(layer: VectorLayer, map: MapState): BufferedQuery {
        val viewportScale = if (layer.source.supportsBufferedQueries) QUERY_VIEWPORT_SCALE else 1.0
        val queryMap =
            MapState(
                center = map.center,
                zoom = map.zoom,
                bearing = map.bearing,
                projection = map.projection,
                config = map.config,
                viewport =
                    Viewport(
                        width = (map.viewport.width * viewportScale).toInt(),
                        height = (map.viewport.height * viewportScale).toInt(),
                        pixelRatio = map.viewport.pixelRatio,
                    ),
            )
        return BufferedQuery(
            sourceIdentity = layer.source.hashCode(),
            sourceVersion = layer.source.version,
            buffered = layer.source.supportsBufferedQueries,
            map = queryMap,
            coverage = queryMap.viewportBounds(),
            features = layer.source.getFeatures(queryMap),
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
                    val renderFeatures =
                        (layer.renderStrategy as? VectorRenderStrategy.ImmediateLod)?.let { strategy ->
                            GeometryLodSimplifier.simplify(projected, strategy.worldTolerance(map))
                        } ?: projected
                    put(
                        layer.id,
                        CommandBuilder.build(
                            map = map,
                            features = renderFeatures,
                            layerId = layer.id,
                            selectedFeatureKeys = selectedFeatureKeys,
                            layerStyle = layer.style,
                        ),
                    )
                }
            }
        }

    private data class LayerBuildResult(
        val layer: VectorLayer,
        val commands: List<RenderCommand>,
        val cachedGeometry: CachedGeometry?,
        val bitmapLayer: VectorBitmapRenderSceneLayer?,
        val cacheKey: VectorLayerCacheKey,
        val performanceEvent: VectorLayerPerformanceEvent,
        val cache: LayerPreparationCache,
    )

    private data class LayerPreparationCache(
        val query: BufferedQuery,
        val projectionKey: ProjectionCacheKey,
        val projectedByKey: Map<String, ProjectedFeatureEntry>,
        val projectedFeatures: List<Feature>,
        val preparedKey: PreparedLayerKey,
        val commands: List<RenderCommand>,
        val cachedGeometryKey: CachedGeometryKey,
        val cachedGeometry: CachedGeometry?,
    )

    private data class BufferedQuery(
        val sourceIdentity: Int,
        val sourceVersion: Long,
        val buffered: Boolean,
        val map: MapState,
        val coverage: BoundingBox,
        val features: List<Feature>,
    ) {
        val hasLabels: Boolean = features.any { it.label?.isNotBlank() == true }

        fun canServe(layer: VectorLayer, currentMap: MapState): Boolean =
            buffered && layer.source.supportsBufferedQueries &&
                sourceIdentity == layer.source.hashCode() &&
                sourceVersion == layer.source.version &&
                map.projection.id == currentMap.projection.id &&
                map.zoom == currentMap.zoom &&
                (!hasLabels ||
                    map.bearing == currentMap.bearing) &&
                currentMap.viewportBounds().let { visible ->
                    coverage.minX <= visible.minX && coverage.maxX >= visible.maxX &&
                        coverage.minY <= visible.minY && coverage.maxY >= visible.maxY
                }
    }

    private data class ProjectionCacheKey(
        val sourceIdentity: Int,
        val sourceVersion: Long,
        val sourceProjectionId: String?,
        val targetProjectionId: String,
        val transformationRegistryHash: Int,
    ) {
        companion object {
            fun from(layer: VectorLayer, map: MapState) =
                ProjectionCacheKey(
                    sourceIdentity = layer.source.hashCode(),
                    sourceVersion = layer.source.version,
                    sourceProjectionId = layer.projection?.id,
                    targetProjectionId = map.projection.id,
                    transformationRegistryHash = map.config.transformationRegistry.hashCode(),
                )
        }
    }

    private data class ProjectedFeatureEntry(val sourceFeature: Feature, val projectedFeature: Feature)

    private data class PreparedLayerKey(
        val featureKeys: List<String>,
        val resolvedStyle: FeatureLayerStyle,
        val selectedFeatureKeys: Set<String>,
        val lodToleranceWorld: Double?,
        val labelCamera: LabelCameraKey?,
    )

    private data class LabelCameraKey(val zoom: Double, val bearing: Double)

    private data class CachedGeometryKey(val pixelScale: Float, val density: Float)

    private companion object {
        const val QUERY_VIEWPORT_SCALE = 2.0
        const val MIN_PIXEL_SCALE = 0.000001f
    }
}

internal fun VectorLayer.cacheKey(
    selectedFeatureKeys: Set<String> = emptySet(),
    zoom: Double? = null,
): VectorLayerCacheKey =
    VectorLayerCacheKey(
        layerId = id,
        sourceIdentity = source.hashCode(),
        sourceVersion = source.version,
        renderStrategy = renderStrategy,
        styleHash = (zoom?.let(style::resolveAtZoom) ?: style).hashCode(),
        pointIconsHash = (this as? PointIconPainterLayer)?.pointIconPainters.orEmpty().hashCode(),
        selectedFeatureKeys = selectedFeatureKeys,
    )

private fun VectorRenderStrategy.ImmediateLod.worldTolerance(map: MapState): Double =
    tolerancePx / WorldToScreenTransform.from(map).pixelScale.coerceAtLeast(0.000001f)

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

private const val ZOOM_COMPARISON_EPSILON = 1e-9
private const val BEARING_COMPARISON_EPSILON = 1e-9

private fun Set<FeatureSelectionRef>.keysForLayer(layerId: String): Set<String> =
    asSequence()
        .filter { it.layerId == layerId }
        .map { it.featureKey }
        .toSet()

private fun TimeMark.elapsedMillis(): Double =
    elapsedNow().inWholeNanoseconds / NANOS_PER_MILLISECOND

private fun List<RenderCommand>.vertexCount(): Int =
    sumOf { command ->
        when (command) {
            is RenderPoint -> 1
            is RenderLineString -> command.points.size
            is RenderPolygon -> command.rings.sumOf { ring -> ring.size }
            is RenderLabel -> 0
        }
    }

private const val NANOS_PER_MILLISECOND = 1_000_000.0
