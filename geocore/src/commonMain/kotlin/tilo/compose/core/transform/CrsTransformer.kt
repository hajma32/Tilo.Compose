package tilo.compose.core.transform

import tilo.compose.core.geometry.Point
import tilo.compose.core.projection.Projection

/**
 * High-level entry point for point reprojection between CRS.
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
