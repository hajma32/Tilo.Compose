package tilo.compose.core.transform

import org.locationtech.proj4j.CRSFactory
import org.locationtech.proj4j.CoordinateTransformFactory
import org.locationtech.proj4j.ProjCoordinate
import tilo.compose.core.geometry.Point


actual fun proj4Transform(point: Point, sourceCrs: String, targetCrs: String): Point {
    val crsFactory = CRSFactory()
    val ctFactory = CoordinateTransformFactory()

    val src = crsFactory.createFromName(sourceCrs)
    val tgt = crsFactory.createFromName(targetCrs)
    val transform = ctFactory.createTransform(src, tgt)

    val srcPt = ProjCoordinate(point.x, point.y)
    val dstPt = ProjCoordinate()
    transform.transform(srcPt, dstPt)
    return Point(x = dstPt.x, y = dstPt.y)
}

