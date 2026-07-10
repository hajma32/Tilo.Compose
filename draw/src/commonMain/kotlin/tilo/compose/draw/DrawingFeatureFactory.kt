package tilo.compose.draw

import tilo.compose.core.feature.Feature
import tilo.compose.core.geometry.LineString
import tilo.compose.core.geometry.Point
import tilo.compose.core.geometry.Polygon

class DrawingFeatureFactory(
    private val style: DrawStyle = DefaultDrawStyle(),
) {
    fun draftFeatures(mode: DrawMode, points: List<Point>): List<Feature> =
        buildList {
            points.forEachIndexed { index, point ->
                add(
                    Feature(
                        key = "draft-point-$index",
                        geometry = point,
                        style = style.point,
                    )
                )
            }
            drawingFeature(key = "draft-shape", mode = mode, points = points)?.let(::add)
        }

    fun drawingFeature(
        key: String,
        mode: DrawMode,
        points: List<Point>,
    ): Feature? =
        when (mode) {
            DrawMode.Point -> points.lastOrNull()?.let { point ->
                Feature(
                    key = key,
                    geometry = point,
                    style = style.point,
                )
            }
            DrawMode.Line -> points.takeIf { it.size >= 2 }?.let { line ->
                Feature(
                    key = key,
                    geometry = LineString(line),
                    style = style.line,
                )
            }
            DrawMode.Polygon -> points.takeIf { it.size >= 3 }?.let { ring ->
                Feature(
                    key = key,
                    geometry = Polygon(rings = listOf(ring + ring.first())),
                    style = style.polygon,
                )
            }
        }
}
