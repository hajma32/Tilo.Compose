package tilo.compose.core.transform

import tilo.compose.core.geometry.Point
import tilo.compose.core.projection.Epsg4326Projection
import tilo.compose.core.projection.Projection

/**
 * Baseline transformation for same-CRS WGS84 geographic data.
 */
object Wgs84ToWgs84Transformation : Transformation<Projection, Projection> {
    override val source: Projection = Epsg4326Projection
    override val target: Projection = Epsg4326Projection

    override fun sourceToTarget(point: Point): Point = point

    override fun targetToSource(point: Point): Point = point
}
