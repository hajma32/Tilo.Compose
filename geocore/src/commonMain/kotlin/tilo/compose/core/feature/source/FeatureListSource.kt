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
            projection?.id == other.projection?.id &&
            projection?.definition == other.projection?.definition

    /**
     * Feature layers are commonly rebuilt by Compose even when their content did not change.
     * Keep their renderer cache identity stable across those equivalent instances.
     */
    override fun hashCode(): Int {
        var result = 31 * version.hashCode() + projection?.id.hashCode()
        result = 31 * result + projection?.definition.hashCode()
        return result
    }

    private val index =
        RBush<Feature>(maxEntries = maxEntries) { feature ->
            feature.geometry.bounds().let { bounds ->
                SpatialRect(bounds.minX, bounds.minY, bounds.maxX, bounds.maxY)
            }
        }.load(this.features)

    override fun getFeatures(map: MapState): List<Feature> {
        val source = projection
        if (
            source != null &&
            (source.id != map.projection.id || source.definition != map.projection.definition)
        ) {
            // Inverse-transforming only viewport corners is not conservative for non-linear CRS
            // operations. The long-lived render cache projects and indexes these candidates once.
            return features
        }
        val visible = visibleBounds(map).toSpatialRect()
        return index.search(visible)
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

private fun BoundingBox.toSpatialRect(): SpatialRect = SpatialRect(minX, minY, maxX, maxY)
