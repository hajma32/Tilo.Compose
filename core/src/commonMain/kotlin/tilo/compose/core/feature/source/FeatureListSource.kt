package tilo.compose.core.feature.source

import tilo.compose.core.feature.Feature
import tilo.compose.core.geometry.BoundingBox
import tilo.compose.core.geometry.Point
import tilo.compose.core.geometry.bounds
import tilo.compose.core.map.Map
import tilo.compose.core.projection.Projection
import tilo.spatial.RBush
import tilo.spatial.SpatialRect

/**
 * Simple in-memory list-backed feature source.
 */
class FeatureListSource(
    features: List<Feature>,
    private val projection: Projection? = null,
    maxEntries: Int = 9
) : FeatureSource {
    private val index = RBush<Feature>(maxEntries = maxEntries) { feature ->
        feature.geometry.bounds().let { bounds ->
            SpatialRect(bounds.minX, bounds.minY, bounds.maxX, bounds.maxY)
        }
    }.load(features)

    override fun getFeatures(map: Map): List<Feature> {
        val queryBounds = visibleBounds(map).toSourceBounds(map)
        return index.search(queryBounds)
    }

    private fun BoundingBox.toSourceBounds(map: Map): SpatialRect {
        val source = projection
        if (source == null || source.id == map.projection.id) {
            return SpatialRect(minX, minY, maxX, maxY)
        }

        val points = listOf(topLeft, topRight, bottomLeft, bottomRight)
            .map { point -> map.transformSourceToTarget(point, map.projection, source) }

        return SpatialRect(
            minX = points.minOf { it.x },
            minY = points.minOf { it.y },
            maxX = points.maxOf { it.x },
            maxY = points.maxOf { it.y }
        )
    }

    private fun visibleBounds(map: Map): BoundingBox {
        val topLeft = map.screenToWorld(Point(0.0, 0.0))
        val bottomRight = map.screenToWorld(Point(map.viewport.width.toDouble(), map.viewport.height.toDouble()))

        val minX = minOf(topLeft.x, bottomRight.x)
        val maxX = maxOf(topLeft.x, bottomRight.x)
        val minY = minOf(topLeft.y, bottomRight.y)
        val maxY = maxOf(topLeft.y, bottomRight.y)

        val padX = (maxX - minX) * VIEWPORT_QUERY_PADDING
        val padY = (maxY - minY) * VIEWPORT_QUERY_PADDING

        return BoundingBox.fromExtents(
            minX = minX - padX,
            maxX = maxX + padX,
            minY = minY - padY,
            maxY = maxY + padY
        )
    }

    private companion object {
        const val VIEWPORT_QUERY_PADDING = 0.1
    }
}
