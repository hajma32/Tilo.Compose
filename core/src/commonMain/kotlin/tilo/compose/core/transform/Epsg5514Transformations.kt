package tilo.compose.core.transform

import tilo.compose.core.geometry.Point
import tilo.compose.core.projection.Epsg4326Projection
import tilo.compose.core.projection.Epsg5514Projection
import tilo.compose.core.projection.Projection

object Epsg5514ToWgs84Transformation : Transformation<Projection, Projection> {
    override val source: Projection = Epsg5514Projection
    override val target: Projection = Epsg4326Projection

    override fun sourceToTarget(point: Point): Point = proj4Transform(point, source.id, target.id)

    override fun targetToSource(point: Point): Point = proj4Transform(point, target.id, source.id)
}

object Wgs84ToEpsg5514Transformation : Transformation<Projection, Projection> {
    override val source: Projection = Epsg4326Projection
    override val target: Projection = Epsg5514Projection

    override fun sourceToTarget(point: Point): Point = proj4Transform(point, source.id, target.id)

    override fun targetToSource(point: Point): Point = proj4Transform(point, target.id, source.id)
}
