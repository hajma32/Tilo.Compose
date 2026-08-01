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

internal actual fun proj4Transform(
    point: Point,
    sourceCrs: String,
    targetCrs: String,
): Point {
    if (sourceCrs == targetCrs) return point

    val transform = coordinateTransform(sourceCrs, targetCrs)
    val srcPt = ProjCoordinate(point.x, point.y)
    val dstPt = ProjCoordinate()
    transform.transform(srcPt, dstPt)
    return Point(x = dstPt.x, y = dstPt.y)
}

internal actual fun supportsProj4Transform(
    sourceCrs: String,
    targetCrs: String,
): Boolean {
    if (sourceCrs == targetCrs) return true
    return try {
        coordinateTransform(sourceCrs, targetCrs)
        true
    } catch (_: RuntimeException) {
        false
    }
}

private fun coordinateTransform(
    sourceCrs: String,
    targetCrs: String,
): CoordinateTransform =
    transformCache.computeIfAbsent(TransformKey(sourceCrs, targetCrs)) { key ->
        val src = crsFactory.create(key.sourceCrs)
        val tgt = crsFactory.create(key.targetCrs)
        transformFactory.createTransform(src, tgt)
    }

private fun CRSFactory.create(definition: String) =
    if (definition.contains("+proj=")) {
        createFromParameters(definition, definition)
    } else {
        createFromName(definition)
    }

private data class TransformKey(
    val sourceCrs: String,
    val targetCrs: String,
)
