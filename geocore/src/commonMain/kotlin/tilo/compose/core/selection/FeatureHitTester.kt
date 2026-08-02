package tilo.compose.core.selection

import tilo.compose.core.feature.Feature
import tilo.compose.core.feature.FeatureLayerStyle
import tilo.compose.core.feature.GeometryStyle
import tilo.compose.core.feature.LineStyle
import tilo.compose.core.feature.PointStyle
import tilo.compose.core.feature.PolygonStyle
import tilo.compose.core.geometry.Geometry
import tilo.compose.core.geometry.LineString
import tilo.compose.core.geometry.MultiLineString
import tilo.compose.core.geometry.MultiPoint
import tilo.compose.core.geometry.MultiPolygon
import tilo.compose.core.geometry.Point
import tilo.compose.core.geometry.Polygon
import tilo.compose.core.geometry.distanceTo
import tilo.compose.core.geometry.distanceToBoundary
import tilo.compose.core.geometry.distanceToLine
import tilo.compose.core.geometry.isInside
import tilo.compose.core.map.ScreenPoint
import kotlin.math.max

/** Resolved feature-layer input for screen-space hit testing. */
data class FeatureHitTestLayer(
    val id: String,
    val features: List<FeatureHitTestFeature>,
    val style: FeatureLayerStyle = FeatureLayerStyle(),
)

/** A feature paired with the geometry transformed into the active map projection. */
data class FeatureHitTestFeature(
    val feature: Feature,
    val geometry: Geometry = feature.geometry,
)

/**
 * Performs style-aware hit testing in screen pixels while preserving the selected world point.
 *
 * `toleranceDip` is the minimum touch tolerance and `styleScale` converts DIP style dimensions
 * to screen pixels for the current display density.
 */
class FeatureHitTester(
    private val toleranceDip: Double = DEFAULT_TOLERANCE_DIP,
    private val styleScale: Double = 1.0,
) {
    fun hitTest(
        layers: List<FeatureHitTestLayer>,
        screenPoint: ScreenPoint,
        worldPoint: Point,
        worldToScreen: (Point) -> ScreenPoint,
    ): List<FeatureSelection> {
        val geometryScreenPoint = Point(screenPoint.x, screenPoint.y)
        val geometryWorldToScreen: (Point) -> Point = { world ->
            worldToScreen(world).let { screen -> Point(screen.x, screen.y) }
        }
        return buildList {
            layers.forEach { layer ->
                layer.features.forEach { item ->
                    if (item.geometry.hits(geometryScreenPoint, geometryWorldToScreen, item.feature, layer.style)) {
                        add(
                            FeatureSelection(
                                layerId = layer.id,
                                feature = item.feature,
                                worldPoint = worldPoint,
                                screenPoint = screenPoint,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun Geometry.hits(
        screenPoint: Point,
        worldToScreen: (Point) -> Point,
        feature: Feature,
        layerStyle: FeatureLayerStyle,
    ): Boolean =
        when (this) {
            is Point -> screenPoint.distanceTo(worldToScreen(this)) <= feature.pointTolerance(layerStyle)
            is MultiPoint ->
                points.any { point ->
                    screenPoint.distanceTo(worldToScreen(point)) <=
                        feature.pointTolerance(layerStyle)
                }
            is LineString -> hitLine(points, screenPoint, worldToScreen, feature.lineTolerance(layerStyle))
            is MultiLineString ->
                lines.any { line ->
                    hitLine(line.points, screenPoint, worldToScreen, feature.lineTolerance(layerStyle))
                }
            is Polygon -> hitPolygon(this, screenPoint, worldToScreen, feature.lineTolerance(layerStyle))
            is MultiPolygon ->
                polygons.any { polygon ->
                    hitPolygon(polygon, screenPoint, worldToScreen, feature.lineTolerance(layerStyle))
                }
        }

    private fun hitLine(
        points: List<Point>,
        screenPoint: Point,
        worldToScreen: (Point) -> Point,
        tolerance: Double,
    ): Boolean {
        if (points.size < 2) return false
        return screenPoint.distanceToLine(points.map(worldToScreen)) <= tolerance
    }

    private fun hitPolygon(
        polygon: Polygon,
        screenPoint: Point,
        worldToScreen: (Point) -> Point,
        tolerance: Double,
    ): Boolean {
        val screenPolygon = Polygon(polygon.rings.map { ring -> ring.map(worldToScreen) })
        return screenPoint.isInside(screenPolygon) || screenPoint.distanceToBoundary(screenPolygon) <= tolerance
    }

    private fun Feature.pointTolerance(layerStyle: FeatureLayerStyle): Double {
        val style = style ?: layerStyle.geometryStyleFor(geometry)
        val visualRadius =
            when (style) {
                is PointStyle ->
                    (max(style.size, style.icon?.size ?: 0.0) / 2.0 + (style.stroke?.width ?: 0.0))
                        .toScreenPixels()
                else -> 0.0
            }
        return max(toleranceDip.toScreenPixels(), visualRadius + TOUCH_PADDING_DIP.toScreenPixels())
    }

    private fun Feature.lineTolerance(layerStyle: FeatureLayerStyle): Double {
        val style = style ?: layerStyle.geometryStyleFor(geometry)
        val strokeWidth =
            when (style) {
                is LineStyle ->
                    max(
                        style.stroke.width.toScreenPixels(),
                        (style.casing?.outerWidth(style.stroke.width) ?: 0.0).toScreenPixels(),
                    )
                is PolygonStyle ->
                    max(
                        (style.stroke?.width ?: 0.0).toScreenPixels(),
                        (style.casing?.outerWidth(style.stroke?.width ?: 0.0) ?: 0.0).toScreenPixels(),
                    )
                else -> 0.0
            }
        return max(toleranceDip.toScreenPixels(), strokeWidth / 2.0 + TOUCH_PADDING_DIP.toScreenPixels())
    }

    private fun Double.toScreenPixels(): Double = this * styleScale

    private fun FeatureLayerStyle.geometryStyleFor(geometry: Geometry): GeometryStyle? =
        when (geometry) {
            is Point, is MultiPoint -> point
            is LineString, is MultiLineString -> line
            is Polygon, is MultiPolygon -> polygon
        }

    private companion object {
        const val DEFAULT_TOLERANCE_DIP = 48.0
        const val TOUCH_PADDING_DIP = 16.0
    }
}
