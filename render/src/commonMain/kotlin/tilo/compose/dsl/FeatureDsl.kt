@file:OptIn(ExperimentalTiloApi::class)

package tilo.compose.dsl

import tilo.compose.core.feature.Data
import tilo.compose.core.feature.Feature
import tilo.compose.core.feature.GeometryStyle
import tilo.compose.core.feature.LabelStyle
import tilo.compose.core.geometry.Geometry
import tilo.compose.core.geometry.LineString
import tilo.compose.core.geometry.MultiLineString
import tilo.compose.core.geometry.MultiPoint
import tilo.compose.core.geometry.MultiPolygon
import tilo.compose.core.geometry.Point
import tilo.compose.core.geometry.Polygon

/**
 * Builds a list of vector features with stable keys and optional style/label
 * metadata.
 */
@ExperimentalTiloApi
fun features(block: FeatureListBuilder.() -> Unit): List<Feature> = FeatureListBuilder().apply(block).build()

/**
 * Builder used by [features].
 */
@ExperimentalTiloApi
@TiloDsl
class FeatureListBuilder {
    private val items = mutableListOf<Feature>()

    /**
     * Adds a feature with an already constructed geometry.
     */
    fun feature(
        key: String,
        geometry: Geometry,
        block: FeatureOptions.() -> Unit = {},
    ) {
        val options = FeatureOptions().apply(block)
        items +=
            Feature(
                key = key,
                geometry = geometry,
                style = options.style,
                selectedStyle = options.selectedStyle,
                label = options.label,
                labelPriority = options.labelPriority,
                labelStyle = options.labelStyle,
                selectedLabelStyle = options.selectedLabelStyle,
                data = options.data,
            )
    }

    /**
     * Adds a point feature.
     */
    fun point(
        key: String,
        x: Double,
        y: Double,
        block: FeatureOptions.() -> Unit = {},
    ) {
        feature(key = key, geometry = Point(x, y), block = block)
    }

    fun point(
        key: String,
        point: Point,
        block: FeatureOptions.() -> Unit = {},
    ) {
        feature(key = key, geometry = point, block = block)
    }

    /**
     * Adds a multi-point feature.
     */
    fun multiPoint(
        key: String,
        points: List<Point>,
        block: FeatureOptions.() -> Unit = {},
    ) {
        feature(key = key, geometry = MultiPoint(points), block = block)
    }

    /**
     * Adds a line feature.
     */
    fun line(
        key: String,
        points: List<Point>,
        block: FeatureOptions.() -> Unit = {},
    ) {
        feature(key = key, geometry = LineString(points), block = block)
    }

    fun lineString(
        key: String,
        points: List<Point>,
        block: FeatureOptions.() -> Unit = {},
    ) {
        line(key = key, points = points, block = block)
    }

    /**
     * Adds a multi-line feature.
     */
    fun multiLine(
        key: String,
        lines: List<LineString>,
        block: FeatureOptions.() -> Unit = {},
    ) {
        feature(key = key, geometry = MultiLineString(lines), block = block)
    }

    /**
     * Adds a polygon feature. Rings must be closed.
     */
    fun polygon(
        key: String,
        rings: List<List<Point>>,
        block: FeatureOptions.() -> Unit = {},
    ) {
        feature(key = key, geometry = Polygon(rings), block = block)
    }

    fun polygon(
        key: String,
        polygon: Polygon,
        block: FeatureOptions.() -> Unit = {},
    ) {
        feature(key = key, geometry = polygon, block = block)
    }

    /**
     * Adds a multi-polygon feature.
     */
    fun multiPolygon(
        key: String,
        polygons: List<Polygon>,
        block: FeatureOptions.() -> Unit = {},
    ) {
        feature(key = key, geometry = MultiPolygon(polygons), block = block)
    }

    internal fun build(): List<Feature> = items.toList()
}

/**
 * Optional metadata applied to a feature created in [features].
 */
@ExperimentalTiloApi
@TiloDsl
class FeatureOptions {
    /**
     * Text rendered near the feature geometry.
     */
    var label: String? = null

    /**
     * Optional collision priority. Higher values win; when not set, larger
     * labels are kept before smaller labels.
     */
    var labelPriority: Int? = null
    var style: GeometryStyle? = null
    var selectedStyle: GeometryStyle? = null
    var labelStyle: LabelStyle? = null
    var selectedLabelStyle: LabelStyle? = null
    var data: Data? = null

    /**
     * Configures label text, collision priority, and optional styles in one
     * call.
     */
    fun label(
        text: String,
        priority: Int? = null,
        style: LabelStyle? = null,
        selectedStyle: LabelStyle? = null,
    ) {
        label = text
        labelPriority = priority
        labelStyle = style
        selectedLabelStyle = selectedStyle
    }
}
