package tilo.compose.core.transform

import tilo.compose.core.geometry.Point
import tilo.compose.core.projection.Projection

/**
 * High-level entry point for point reprojection between CRS.
 *
 * This is engine-level GeoCore API. [TransformationRegistry.Default] intentionally has no platform
 * CRS provider; the Compose camera factory installs Tilo's PROJ/Proj4J integration. Direct users
 * must pass a registry when transforming independently defined CRS.
 */
class CrsTransformer(
    private val registry: TransformationRegistry = TransformationRegistry.Default,
) {
    fun sourceToTarget(
        point: Point,
        source: Projection,
        target: Projection,
    ): Point = registry.resolve(source, target).sourceToTarget(point)

    fun targetToSource(
        point: Point,
        source: Projection,
        target: Projection,
    ): Point = registry.resolve(source, target).targetToSource(point)
}
