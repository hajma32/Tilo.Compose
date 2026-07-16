package eu.tilo.publication.smoke

import tilo.compose.core.geometry.Point
import tilo.compose.core.transform.Wgs84ToEpsg5514Transformation

/** Forces the published iOS core artifact and its embedded PROJ archive into the consumer link. */
fun transformPublishedIosCoordinate(point: Point): Point =
    Wgs84ToEpsg5514Transformation.sourceToTarget(point)
