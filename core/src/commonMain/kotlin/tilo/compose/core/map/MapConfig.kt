package tilo.compose.core.map

import tilo.compose.core.projection.Projection
import tilo.compose.core.transform.CrsTransformer
import tilo.compose.core.transform.Transformation
import tilo.compose.core.transform.TransformationRegistry

/**
 * Unified map runtime configuration (limits + CRS transformations).
 */
data class MapConfig(
    val minZoom: Double = 0.0,
    val maxZoom: Double = 22.0,
    val wrapHorizontal: Boolean = true,
    val transformationRegistry: TransformationRegistry = TransformationRegistry.Default
) {
    val transformer: CrsTransformer
        get() = CrsTransformer(transformationRegistry)

    fun withTransformation(transformation: Transformation<Projection, Projection>): MapConfig {
        return copy(transformationRegistry = transformationRegistry.withTransformation(transformation))
    }

    companion object {
        val Default = MapConfig()
    }
}
