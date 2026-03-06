package tilo.compose.core.transform

import tilo.compose.core.geometry.Point
import tilo.compose.core.projection.Projection
import tilo.compose.core.projection.Wgs84WebMercatorProjection

/**
 * Baseline transformation for same-CRS WGS84 data.
 */
object Wgs84ToWgs84Transformation : Transformation<Projection, Projection> {
    override val source: Projection = Wgs84WebMercatorProjection
    override val target: Projection = Wgs84WebMercatorProjection

    override fun sourceToTarget(point: Point): Point = point

    override fun targetToSource(point: Point): Point = point
}

