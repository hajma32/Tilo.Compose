package tilo.compose.render

import tilo.compose.core.feature.Feature
import tilo.compose.core.geometry.Geometry
import tilo.compose.core.geometry.LineString
import tilo.compose.core.geometry.MultiLineString
import tilo.compose.core.geometry.MultiPoint
import tilo.compose.core.geometry.MultiPolygon
import tilo.compose.core.geometry.Point
import tilo.compose.core.geometry.Polygon
import tilo.compose.core.geometry.bounds
import tilo.compose.core.map.MapState
import tilo.compose.core.projection.Projection
import tilo.compose.core.transform.Transformation
import tilo.compose.core.transform.TransformationRegistry
import tilo.spatial.RBush
import tilo.spatial.SpatialRect

/**
 * Re-projects [features] from [featuresSourceProjection] into the map's own projection.
 * Returns the original list unchanged when no source projection is given or projections match.
 */
internal fun transformFeaturesToMapProjection(
    features: List<Feature>,
    featuresSourceProjection: Projection?,
    map: MapState,
): List<Feature> {
    val source = featuresSourceProjection ?: return features
    if (source.sameCrsAs(map.projection)) return features
    val transformation = map.transformationRegistry.resolve(source, map.projection)

    return features.map { feature ->
        feature.copy(
            geometry = transformGeometry(feature.geometry, transformation),
        )
    }
}

private fun transformGeometry(
    geometry: Geometry,
    transformation: Transformation<Projection, Projection>,
): Geometry {
    fun tp(p: Point) = transformation.sourceToTarget(p)

    return when (geometry) {
        is Point -> tp(geometry)
        is MultiPoint -> MultiPoint(geometry.points.map(::tp))
        is LineString -> LineString(geometry.points.map(::tp))
        is MultiLineString -> MultiLineString(geometry.lines.map { LineString(it.points.map(::tp)) })
        is Polygon -> Polygon(geometry.rings.map { ring -> ring.map(::tp) })
        is MultiPolygon -> MultiPolygon(geometry.polygons.map { Polygon(it.rings.map { ring -> ring.map(::tp) }) })
    }
}

/** Caches CRS-only geometry work independently of viewport-dependent command generation. */
internal class FeatureProjectionCache {
    internal data class ProjectionKey(
        val sourceIdentity: Any,
        val sourceId: String,
        val sourceDefinition: String,
        val targetId: String,
        val targetDefinition: String,
        val registry: TransformationRegistry,
    )

    private class LayerEntry(
        val key: ProjectionKey,
        val transformation: Transformation<Projection, Projection>,
    ) {
        val geometriesByFeatureKey = mutableMapOf<String, ProjectedGeometry>()
        var spatialIndex = projectedFeatureIndex(emptyList())
        var sourceFeatures: List<Feature> = emptyList()
        var sourceVersion: Long? = null
        var indexedFeatures: List<Feature> = emptyList()
        var snapshot: FeatureProjectionSnapshot? = null
    }

    private data class ProjectedGeometry(
        val sourceFeature: Feature,
        val projectedFeature: Feature,
    )

    private val entriesByLayerId = mutableMapOf<String, LayerEntry>()

    fun transform(
        layerId: String,
        sourceIdentity: Any,
        sourceVersion: Long,
        features: List<Feature>,
        source: Projection?,
        map: MapState,
    ): List<Feature> {
        if (source == null || source.sameCrsAs(map.projection)) {
            entriesByLayerId.remove(layerId)
            return features
        }

        val key =
            ProjectionKey(
                sourceIdentity = sourceIdentity,
                sourceId = source.id,
                sourceDefinition = source.definition,
                targetId = map.projection.id,
                targetDefinition = map.projection.definition,
                registry = map.transformationRegistry,
            )
        val entry =
            entriesByLayerId[layerId]
                ?.takeIf { it.key == key }
                ?: LayerEntry(
                    key = key,
                    transformation = map.transformationRegistry.resolve(source, map.projection),
                ).also { entriesByLayerId[layerId] = it }

        if (
            entry.sourceVersion == sourceVersion &&
            (entry.sourceFeatures === features || entry.sourceFeatures == features)
        ) {
            if (entry.sourceFeatures !== features) {
                entry.snapshot = entry.snapshot?.withSourceFeatures(features)
            }
            entry.sourceFeatures = features
            return entry.spatialIndex.search(map.bufferedVisibleBounds())
        }

        val projected =
            features.map { feature ->
                val previous = entry.geometriesByFeatureKey[feature.key]
                if (previous?.sourceFeature == feature) {
                    previous.projectedFeature
                } else {
                    val projectedGeometry =
                        if (previous?.sourceFeature?.geometry == feature.geometry) {
                            previous.projectedFeature.geometry
                        } else {
                            transformGeometry(feature.geometry, entry.transformation)
                        }
                    feature.copy(geometry = projectedGeometry).also { projectedFeature ->
                        entry.geometriesByFeatureKey[feature.key] =
                            ProjectedGeometry(
                                sourceFeature = feature,
                                projectedFeature = projectedFeature,
                            )
                    }
                }
            }
        entry.geometriesByFeatureKey.keys.retainAll(features.mapTo(mutableSetOf()) { it.key })
        if (entry.indexedFeatures != projected) {
            // Published indexes are immutable snapshots. Replace instead of mutating so hit testing
            // can safely query the previous completed frame while the next one is being prepared.
            entry.spatialIndex = projectedFeatureIndex(projected)
            entry.indexedFeatures = projected
        }
        entry.sourceFeatures = features
        entry.sourceVersion = sourceVersion
        entry.snapshot =
            FeatureProjectionSnapshot(
                key = entry.key,
                sourceVersion = sourceVersion,
                sourceFeaturesByKey = features.associateBy(Feature::key),
                spatialIndex = entry.spatialIndex,
            )
        return entry.spatialIndex.search(map.bufferedVisibleBounds())
    }

    fun snapshot(
        layerId: String,
        sourceIdentity: Any,
        sourceVersion: Long,
        source: Projection?,
        map: MapState,
    ): FeatureProjectionSnapshot? {
        source ?: return null
        val entry = entriesByLayerId[layerId] ?: return null
        val expectedKey =
            ProjectionKey(
                sourceIdentity = sourceIdentity,
                sourceId = source.id,
                sourceDefinition = source.definition,
                targetId = map.projection.id,
                targetDefinition = map.projection.definition,
                registry = map.transformationRegistry,
            )
        return entry.snapshot?.takeIf { entry.key == expectedKey && it.sourceVersion == sourceVersion }
    }

    fun retainLayers(layerIds: Set<String>) {
        entriesByLayerId.keys.retainAll(layerIds)
    }
}

internal class FeatureProjectionSnapshot(
    internal val key: FeatureProjectionCache.ProjectionKey,
    internal val sourceVersion: Long,
    private val sourceFeaturesByKey: Map<String, Feature>,
    private val spatialIndex: RBush<Feature>,
) {
    fun query(map: MapState): List<Feature> = spatialIndex.search(map.bufferedVisibleBounds())

    fun sourceFeature(key: String): Feature = sourceFeaturesByKey.getValue(key)

    internal fun withSourceFeatures(features: List<Feature>): FeatureProjectionSnapshot =
        FeatureProjectionSnapshot(
            key = key,
            sourceVersion = sourceVersion,
            sourceFeaturesByKey = features.associateBy(Feature::key),
            spatialIndex = spatialIndex,
        )
}

/** Main-thread handoff of immutable indexes produced by the latest completed vector frame. */
internal class FeatureProjectionSnapshotStore {
    private var snapshotsByLayerId: Map<String, FeatureProjectionSnapshot> = emptyMap()

    fun publish(snapshots: Map<String, FeatureProjectionSnapshot>) {
        snapshotsByLayerId = snapshots
    }

    fun find(
        layerId: String,
        sourceIdentity: Any,
        sourceVersion: Long,
        source: Projection,
        map: MapState,
    ): FeatureProjectionSnapshot? {
        val snapshot = snapshotsByLayerId[layerId] ?: return null
        val key = snapshot.key
        return snapshot.takeIf {
            key.sourceIdentity == sourceIdentity &&
                snapshot.sourceVersion == sourceVersion &&
                key.sourceId == source.id &&
                key.sourceDefinition == source.definition &&
                key.targetId == map.projection.id &&
                key.targetDefinition == map.projection.definition &&
                key.registry == map.transformationRegistry
        }
    }
}

private fun projectedFeatureIndex(features: List<Feature>): RBush<Feature> =
    RBush<Feature> { feature ->
        feature.geometry.bounds().let { bounds ->
            SpatialRect(bounds.minX, bounds.minY, bounds.maxX, bounds.maxY)
        }
    }.load(features)

private fun MapState.bufferedVisibleBounds(): SpatialRect {
    val visible = viewportBounds()
    val padX = (visible.maxX - visible.minX) * VIEWPORT_QUERY_PADDING
    val padY = (visible.maxY - visible.minY) * VIEWPORT_QUERY_PADDING
    return SpatialRect(
        minX = visible.minX - padX,
        minY = visible.minY - padY,
        maxX = visible.maxX + padX,
        maxY = visible.maxY + padY,
    )
}

private fun Projection.sameCrsAs(other: Projection): Boolean = id == other.id && definition == other.definition

private const val VIEWPORT_QUERY_PADDING = 0.1
