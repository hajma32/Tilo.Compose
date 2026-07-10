package tilo.compose.core.transform

import org.locationtech.proj4j.CRSFactory
import org.locationtech.proj4j.CoordinateTransform
import org.locationtech.proj4j.CoordinateTransformFactory
import org.locationtech.proj4j.ProjCoordinate
import tilo.compose.core.geometry.Point
import java.util.concurrent.ConcurrentHashMap

private val crsFactory = CRSFactory()
private val transformFactory = CoordinateTransformFactory()
private val transformCache = ConcurrentHashMap<TransformKey, CoordinateTransform>()

actual fun proj4Transform(point: Point, sourceCrs: String, targetCrs: String): Point {
    val transform = transformCache.computeIfAbsent(TransformKey(sourceCrs, targetCrs)) { key ->
        val src = crsFactory.createFromName(key.sourceCrs)
        val tgt = crsFactory.createFromName(key.targetCrs)
        transformFactory.createTransform(src, tgt)
    }
    val srcPt = ProjCoordinate(point.x, point.y)
    val dstPt = ProjCoordinate()
    transform.transform(srcPt, dstPt)
    return Point(x = dstPt.x, y = dstPt.y)
}

private data class TransformKey(
    val sourceCrs: String,
    val targetCrs: String,
)
