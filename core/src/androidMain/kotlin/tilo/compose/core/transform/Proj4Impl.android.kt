package tilo.compose.core.transform

import org.locationtech.proj4j.CRSFactory
import org.locationtech.proj4j.CoordinateReferenceSystem
import org.locationtech.proj4j.CoordinateTransform
import org.locationtech.proj4j.CoordinateTransformFactory
import org.locationtech.proj4j.ProjCoordinate
import tilo.compose.core.geometry.Point
import java.util.concurrent.ConcurrentHashMap

private val crsFactory = CRSFactory()
private val transformFactory = CoordinateTransformFactory()
private val crsCache = ConcurrentHashMap<String, CoordinateReferenceSystem>()
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

internal actual fun proj4WorldUnitsPerMapUnit(definition: String): Double {
    val crs = coordinateReferenceSystem(definition)
    // Proj4J's public transform contract uses traditional GIS lon/lat degrees.
    if (crs.isGeographic) return 1.0

    val projection = crs.projection
    val unitToBase = projection.units.value
    require(unitToBase.isFinite() && unitToBase > 0.0) {
        "CRS $definition has an invalid coordinate unit conversion factor."
    }
    return metersPerMapUnit / unitToBase
}

private fun coordinateTransform(
    sourceCrs: String,
    targetCrs: String,
): CoordinateTransform =
    transformCache.computeIfAbsent(TransformKey(sourceCrs, targetCrs)) { key ->
        val src = coordinateReferenceSystem(key.sourceCrs)
        val tgt = coordinateReferenceSystem(key.targetCrs)
        transformFactory.createTransform(src, tgt)
    }

private fun coordinateReferenceSystem(definition: String): CoordinateReferenceSystem =
    crsCache.computeIfAbsent(definition) {
        if (definition.contains("+proj=", ignoreCase = true)) {
            crsFactory.createFromParameters(definition, definition)
        } else {
            crsFactory.createFromName(definition)
        }
    }

private data class TransformKey(
    val sourceCrs: String,
    val targetCrs: String,
)
