package tilo.compose.core.feature.source

import tilo.compose.core.feature.Feature
import tilo.compose.core.geometry.BoundingBox
import tilo.compose.core.geometry.bounds
import tilo.compose.core.map.MapState
import tilo.compose.core.projection.Projection
import tilo.spatial.RBush
import tilo.spatial.SpatialRect

/**
 * Simple in-memory list-backed feature source.
 */
class FeatureListSource(
    features: List<Feature>,
    private val projection: Projection? = null,
    maxEntries: Int = 9,
) : FeatureSource {
    override val supportsBufferedQueries: Boolean = true

    private val features = features.toList()

    override val version: Long = this.features.hashCode().toLong()

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is FeatureListSource &&
            features == other.features &&
            projection?.id == other.projection?.id

    /**
     * Feature layers are commonly rebuilt by Compose even when their content did not change.
     * Keep their renderer cache identity stable across those equivalent instances.
     */
    override fun hashCode(): Int = 31 * version.hashCode() + projection?.id.hashCode()

    private val index =
        RBush<Feature>(maxEntries = maxEntries) { feature ->
            feature.geometry.bounds().let { bounds ->
                SpatialRect(bounds.minX, bounds.minY, bounds.maxX, bounds.maxY)
            }
        }.load(this.features)

    override fun getFeatures(map: MapState): List<Feature> {
        val queryBounds = visibleBounds(map).toSourceBounds(map)
        return index.search(queryBounds)
    }

    private fun BoundingBox.toSourceBounds(map: MapState): SpatialRect {
        val source = projection
        if (source == null || source.id == map.projection.id) {
            return SpatialRect(minX, minY, maxX, maxY)
        }

        val points =
            listOf(topLeft, topRight, bottomLeft, bottomRight)
                .map { point -> map.transformSourceToTarget(point, map.projection, source) }

        return SpatialRect(
            minX = points.minOf { it.x },
            minY = points.minOf { it.y },
            maxX = points.maxOf { it.x },
            maxY = points.maxOf { it.y },
        )
    }

    private fun visibleBounds(map: MapState): BoundingBox {
        val visible = map.viewportBounds()

        val padX = (visible.maxX - visible.minX) * VIEWPORT_QUERY_PADDING
        val padY = (visible.maxY - visible.minY) * VIEWPORT_QUERY_PADDING

        return BoundingBox.fromExtents(
            minX = visible.minX - padX,
            maxX = visible.maxX + padX,
            minY = visible.minY - padY,
            maxY = visible.maxY + padY,
        )
    }

    private companion object {
        const val VIEWPORT_QUERY_PADDING = 0.1
    }
}
