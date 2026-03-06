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
    val transformations: List<Transformation<Projection, Projection>> =
        listOf(Wgs84ToWgs84Transformation)
) {
    fun withTransformation(transformation: Transformation<Projection, Projection>): MapConfig {
        return copy(transformations = transformations + transformation)
    }

    companion object {
        val Default = MapConfig()
    }
}
