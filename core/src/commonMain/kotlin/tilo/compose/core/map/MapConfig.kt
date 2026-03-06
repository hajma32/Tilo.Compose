package tilo.compose.core.map

import tilo.compose.core.geometry.Point
import tilo.compose.core.projection.Projection
import tilo.compose.core.transform.Transformation
import tilo.compose.core.transform.Wgs84ToWgs84Transformation

/**
 * Unified map runtime configuration (limits + CRS transformations).
 */
data class MapConfig(
    val minZoom: Double = 0.0,
    val maxZoom: Double = 22.0,
    val wrapHorizontal: Boolean = true,
    private val transformations: List<Transformation<Projection, Projection>> =
        listOf(Wgs84ToWgs84Transformation)
) {

    fun withTransformation(transformation: Transformation<Projection, Projection>): MapConfig {
        return copy(transformations = transformations + transformation)
    }

    fun sourceToTarget(point: Point, source: Projection, target: Projection): Point {
        val transformation = requireTransformation(source, target)
        return transformation.sourceToTarget(point)
    }

    fun targetToSource(point: Point, source: Projection, target: Projection): Point {
        val transformation = requireTransformation(source, target)
        return transformation.targetToSource(point)
    }

    fun requireTransformation(
        source: Projection,
        target: Projection
    ): Transformation<Projection, Projection> {
        return transformations.firstOrNull { it.source === source && it.target === target }
            ?: throw IllegalStateException(
                "No transformation registered for ${source::class.simpleName} -> ${target::class.simpleName}."
            )
    }

    companion object {
        val Default = MapConfig()
    }
}
