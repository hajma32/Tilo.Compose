package eu.tilo.compose.cuzk

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import tilo.compose.core.feature.Feature
import tilo.compose.core.geometry.BoundingBox
import tilo.compose.dsl.MapCameraState
import tilo.compose.dsl.MapViewportSnapshot

internal class ZabagedLayerState {
    var landFeatures: List<Feature> by mutableStateOf(emptyList())
        internal set
    var buildingFeatures: List<Feature> by mutableStateOf(emptyList())
        internal set
    var boundaries: List<Feature> by mutableStateOf(emptyList())
        internal set
    var roads: List<Feature> by mutableStateOf(emptyList())
        internal set
    var streets: List<Feature> by mutableStateOf(emptyList())
        internal set
    var municipalities: List<Feature> by mutableStateOf(emptyList())
        internal set
    var isLoading: Boolean by mutableStateOf(false)
        internal set
    var errorMessage: String? by mutableStateOf(null)
        internal set

    val featureCount: Int
        get() = landFeatures.size + buildingFeatures.size + boundaries.size + roads.size +
            streets.size + municipalities.size

    private val landFeaturesByLayer = mutableMapOf<ZabagedLayer, List<Feature>>()

    internal fun setFeatures(layer: ZabagedLayer, features: List<Feature>) {
        if (layer.isBasemap) {
            if (layer == ZabagedLayer.Buildings) {
                buildingFeatures = features
            } else {
                setLandFeatures(mapOf(layer to features))
            }
            return
        }
        when (layer) {
            ZabagedLayer.AdministrativeBoundaries -> boundaries = features
            ZabagedLayer.Municipalities -> municipalities = features
            ZabagedLayer.Roads -> roads = features
            ZabagedLayer.Streets -> streets = features
            else -> Unit
        }
    }

    internal fun setLandFeatures(featuresByLayer: Map<ZabagedLayer, List<Feature>>) {
        landFeaturesByLayer.putAll(featuresByLayer)
        landFeatures = ZabagedLayer.entries
            .filter { layer -> layer.isBasemap && layer != ZabagedLayer.Buildings }
            .flatMap { landFeaturesByLayer[it].orEmpty() }
    }

    internal fun clear() {
        landFeaturesByLayer.clear()
        landFeatures = emptyList()
        buildingFeatures = emptyList()
        boundaries = emptyList()
        municipalities = emptyList()
        roads = emptyList()
        streets = emptyList()
        isLoading = false
        errorMessage = null
    }
}

@Composable
internal fun rememberZabagedLayerState(
    cameraState: MapCameraState,
    basemapEnabled: Boolean,
    overlayEnabled: Boolean,
): ZabagedLayerState {
    val state = remember(cameraState) { ZabagedLayerState() }
    val service = remember(cameraState) { ZabagedService() }

    DisposableEffect(service) {
        onDispose(service::close)
    }

    LaunchedEffect(cameraState, basemapEnabled, overlayEnabled, service) {
        if (!basemapEnabled && !overlayEnabled) {
            state.clear()
            return@LaunchedEffect
        }

        val coverage = mutableMapOf<ZabagedLayer, LoadedCoverage>()
        snapshotFlow { cameraState.viewportSnapshot() }
            .collectLatest { snapshot ->
                if (!snapshot.isReady) return@collectLatest
                delay(QueryDebounceMillis)

                val requestedLayers = ZabagedLayer.entries.mapNotNull { layer ->
                    val layerEnabled = if (layer.isBasemap) basemapEnabled else overlayEnabled
                    if (!layerEnabled || snapshot.zoom < layer.minimumZoom) {
                        state.setFeatures(layer, emptyList())
                        coverage.remove(layer)
                        null
                    } else {
                        LayerQuery.forLayer(layer, snapshot.zoom)
                            .takeUnless { query -> coverage.contains(snapshot, query) }
                    }
                }
                if (requestedLayers.isEmpty()) return@collectLatest

                state.isLoading = true
                state.errorMessage = null
                val queryBounds = snapshot.bounds.expanded(QueryPaddingFraction)
                val errors = mutableListOf<String>()
                val (immediateQueries, basemapQueries) = requestedLayers.partition { query ->
                    !query.layer.isBasemap
                }
                val (buildingQueries, landQueries) = basemapQueries.partition { query ->
                    query.layer == ZabagedLayer.Buildings
                }
                loadImmediateQueryBatch(
                    queries = immediateQueries,
                    service = service,
                    state = state,
                    coverage = coverage,
                    queryBounds = queryBounds,
                    resolution = snapshot.resolution,
                    errors = errors,
                )
                loadBasemapQueryBatch(
                    queries = landQueries,
                    service = service,
                    coverage = coverage,
                    queryBounds = queryBounds,
                    resolution = snapshot.resolution,
                    errors = errors,
                    publish = state::setLandFeatures,
                )
                loadBasemapQueryBatch(
                    queries = buildingQueries,
                    service = service,
                    coverage = coverage,
                    queryBounds = queryBounds,
                    resolution = snapshot.resolution,
                    errors = errors,
                    publish = { featuresByLayer ->
                        state.buildingFeatures = featuresByLayer[ZabagedLayer.Buildings].orEmpty()
                    },
                )
                state.errorMessage = errors.takeIf { it.isNotEmpty() }?.joinToString(" · ")
                state.isLoading = false
            }
    }

    return state
}

private suspend fun loadImmediateQueryBatch(
    queries: List<LayerQuery>,
    service: ZabagedService,
    state: ZabagedLayerState,
    coverage: MutableMap<ZabagedLayer, LoadedCoverage>,
    queryBounds: BoundingBox,
    resolution: Double,
    errors: MutableList<String>,
) {
    supervisorScope {
        queries.map { query ->
            launch {
                try {
                    val features = service.query(
                        layer = query.layer,
                        bounds = queryBounds,
                        maximumOffset = query.maximumOffset(resolution),
                        whereClause = query.whereClause(resolution),
                        waterLabelDetail = query.waterLabelDetail,
                        roadDetail = query.roadDetail,
                    )
                    // Publish each layer as soon as its own request completes. Slower
                    // siblings must not hold back already available orientation data.
                    state.setFeatures(query.layer, features)
                    coverage[query.layer] = LoadedCoverage(
                        bounds = queryBounds,
                        resolution = resolution,
                        variant = query.variant,
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    errors += failure.message ?: "${query.layer.name} request failed"
                    state.errorMessage = errors.joinToString(" · ")
                }
            }
        }.joinAll()
    }
}

private suspend fun loadBasemapQueryBatch(
    queries: List<LayerQuery>,
    service: ZabagedService,
    coverage: MutableMap<ZabagedLayer, LoadedCoverage>,
    queryBounds: BoundingBox,
    resolution: Double,
    errors: MutableList<String>,
    publish: (Map<ZabagedLayer, List<Feature>>) -> Unit,
) {
    val results = supervisorScope {
        queries.map { query ->
            async {
                query to try {
                    Result.success(
                        service.query(
                            layer = query.layer,
                            bounds = queryBounds,
                            maximumOffset = query.maximumOffset(resolution),
                            whereClause = query.whereClause(resolution),
                            waterLabelDetail = query.waterLabelDetail,
                            roadDetail = query.roadDetail,
                        )
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    Result.failure(failure)
                }
            }
        }.awaitAll()
    }

    val completedFeatures = mutableMapOf<ZabagedLayer, List<Feature>>()
    results.forEach { (query, result) ->
        result.onSuccess { features ->
            completedFeatures[query.layer] = features
            coverage[query.layer] = LoadedCoverage(
                bounds = queryBounds,
                resolution = resolution,
                variant = query.variant,
            )
        }.onFailure { failure ->
            errors += failure.message ?: "${query.layer.name} request failed"
        }
    }
    if (completedFeatures.isNotEmpty()) {
        // Swap all newly fetched background geometry at once. Rebuilding the
        // cached bitmap once avoids flashing through partially assembled maps.
        publish(completedFeatures)
    }
}

private data class LoadedCoverage(
    val bounds: BoundingBox,
    val resolution: Double,
    val variant: String,
)

internal data class LayerQuery(
    val layer: ZabagedLayer,
    val baseWhereClause: String,
    val variant: String,
    val waterLabelDetail: ZabagedWaterLabelDetail?,
    val roadDetail: ZabagedRoadDetail?,
    val geometryProfile: ZabagedGeometryProfile,
    val watercourseDetail: ZabagedWatercourseDetail?,
) {
    fun maximumOffset(resolution: Double): Double =
        resolution * if (layer.isBasemap) {
            geometryProfile.toleranceInPixels
        } else if (layer == ZabagedLayer.Roads) {
            RoadGeometryToleranceInPixels
        } else {
            OtherImmediateGeometryToleranceInPixels
        }

    fun whereClause(resolution: Double): String {
        if (!layer.supportsAreaFiltering) return baseWhereClause
        val minimumArea = resolution * resolution * geometryProfile.minimumAreaInPixels *
            layer.areaFilterMultiplier
        return "($baseWhereClause) AND Shape_Area >= $minimumArea"
    }

    companion object {
        fun forLayer(layer: ZabagedLayer, zoom: Double): LayerQuery {
            val geometryProfile = ZabagedGeometryProfile.forZoom(zoom)
            val watercourseDetail = if (layer == ZabagedLayer.Watercourses) {
                ZabagedWatercourseDetail.forZoom(zoom)
            } else {
                null
            }
            val boundaryDetail = if (layer == ZabagedLayer.AdministrativeBoundaries) {
                ZabagedBoundaryDetail.forZoom(zoom)
            } else {
                null
            }
            val municipalityDetail = if (layer == ZabagedLayer.Municipalities) {
                ZabagedMunicipalityDetail.forZoom(zoom)
            } else {
                null
            }
            val waterLabelDetail = if (layer == ZabagedLayer.Watercourses) {
                ZabagedWaterLabelDetail.forZoom(zoom)
            } else {
                null
            }
            val roadDetail = if (layer == ZabagedLayer.Roads) {
                ZabagedRoadDetail.forZoom(zoom)
            } else {
                null
            }
            return LayerQuery(
                layer = layer,
                baseWhereClause = boundaryDetail?.whereClause
                    ?: municipalityDetail?.whereClause
                    ?: roadDetail?.whereClause
                    ?: watercourseDetail?.whereClause
                    ?: "1=1",
                variant = listOfNotNull(
                    boundaryDetail?.name,
                    municipalityDetail?.name,
                    waterLabelDetail?.name,
                    roadDetail?.name,
                    watercourseDetail?.name,
                    geometryProfile.name.takeIf { layer.isBasemap },
                ).joinToString("-").ifEmpty { "default" },
                waterLabelDetail = waterLabelDetail,
                roadDetail = roadDetail,
                geometryProfile = geometryProfile,
                watercourseDetail = watercourseDetail,
            )
        }
    }
}

private fun Map<ZabagedLayer, LoadedCoverage>.contains(
    snapshot: MapViewportSnapshot,
    query: LayerQuery,
): Boolean {
    val loaded = get(query.layer) ?: return false
    val resolutionRatio = snapshot.resolution / loaded.resolution
    return loaded.variant == query.variant &&
        resolutionRatio in 0.5..2.0 &&
        loaded.bounds.contains(snapshot.bounds)
}

private fun BoundingBox.contains(other: BoundingBox): Boolean =
    minX <= other.minX && maxX >= other.maxX && minY <= other.minY && maxY >= other.maxY

private fun BoundingBox.expanded(fraction: Double): BoundingBox {
    val paddingX = (maxX - minX) * fraction
    val paddingY = (maxY - minY) * fraction
    return BoundingBox.fromExtents(
        minX = minX - paddingX,
        maxX = maxX + paddingX,
        minY = minY - paddingY,
        maxY = maxY + paddingY,
    )
}

private const val QueryDebounceMillis = 300L
private const val QueryPaddingFraction = 0.25
private const val RoadGeometryToleranceInPixels = 0.25
private const val OtherImmediateGeometryToleranceInPixels = 0.75
