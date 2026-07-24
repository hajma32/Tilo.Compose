package tilo.compose.render

import tilo.compose.core.feature.Feature
import tilo.compose.core.geometry.Geometry
import tilo.compose.core.geometry.LineString
import tilo.compose.core.geometry.MultiLineString
import tilo.compose.core.geometry.MultiPoint
import tilo.compose.core.geometry.MultiPolygon
import tilo.compose.core.geometry.Point
import tilo.compose.core.geometry.Polygon
import tilo.compose.core.map.MapState
import tilo.compose.core.projection.Projection

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
    if (source === map.projection) return features

    return features.map { feature ->
        transformFeatureToMapProjection(feature, source, map)
    }
}

internal fun transformFeatureToMapProjection(
    feature: Feature,
    featuresSourceProjection: Projection?,
    map: MapState,
): Feature {
    val source = featuresSourceProjection ?: return feature
    if (source === map.projection || source.id == map.projection.id) return feature
    return feature.copy(geometry = transformGeometry(feature.geometry, map, source))
}

private fun transformGeometry(
    geometry: Geometry,
    map: MapState,
    source: Projection,
): Geometry {
    fun tp(p: Point) = map.transformSourceToTarget(p, source, map.projection)

    return when (geometry) {
        is Point -> tp(geometry)
        is MultiPoint -> MultiPoint(geometry.points.map(::tp))
        is LineString -> LineString(geometry.points.map(::tp))
        is MultiLineString -> MultiLineString(geometry.lines.map { LineString(it.points.map(::tp)) })
        is Polygon -> Polygon(geometry.rings.map { ring -> ring.map(::tp) })
        is MultiPolygon -> MultiPolygon(geometry.polygons.map { Polygon(it.rings.map { ring -> ring.map(::tp) }) })
    }
}
