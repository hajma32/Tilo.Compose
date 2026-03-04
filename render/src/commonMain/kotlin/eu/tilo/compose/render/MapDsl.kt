package eu.tilo.compose.map

import tilo.compose.core.feature.BaseStyle
import tilo.compose.core.feature.Feature
import tilo.compose.core.geometry.LineString
import tilo.compose.core.geometry.Point
import tilo.compose.core.geometry.Polygon

/** Declarative builder for map features used by Compose-first UI code. */
class MapFeatureBuilder {
    private val items = mutableListOf<Feature>()

    fun point(key: String, x: Double, y: Double, style: BaseStyle? = null, label: String? = null) {
        items += Feature(key = key, geometry = Point(x, y), style = style, label = label)
    }

    fun lineString(key: String, points: List<Point>, style: BaseStyle? = null, label: String? = null) {
        items += Feature(key = key, geometry = LineString(points), style = style, label = label)
    }

    fun polygon(key: String, rings: List<List<Point>>, style: BaseStyle? = null, label: String? = null) {
        items += Feature(key = key, geometry = Polygon(rings), style = style, label = label)
    }

    internal fun build(): List<Feature> = items.toList()
}

fun mapFeatures(block: MapFeatureBuilder.() -> Unit): List<Feature> {
    val builder = MapFeatureBuilder()
    builder.block()
    return builder.build()
}

