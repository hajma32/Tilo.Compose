package tilo.compose.core.transform

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.DoubleVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.useContents
import kotlinx.cinterop.value
import platform.Foundation.NSLock
import tilo.compose.core.geometry.Point
import tilo.compose.core.proj.PJ
import tilo.compose.core.proj.PJ_CONTEXT
import tilo.compose.core.proj.PJ_FWD
import tilo.compose.core.proj.PJ_TYPE
import tilo.compose.core.proj.proj_context_create
import tilo.compose.core.proj.proj_context_destroy
import tilo.compose.core.proj.proj_context_errno
import tilo.compose.core.proj.proj_coord
import tilo.compose.core.proj.proj_create
import tilo.compose.core.proj.proj_create_crs_to_crs
import tilo.compose.core.proj.proj_crs_get_coordinate_system
import tilo.compose.core.proj.proj_cs_get_axis_count
import tilo.compose.core.proj.proj_cs_get_axis_info
import tilo.compose.core.proj.proj_destroy
import tilo.compose.core.proj.proj_errno
import tilo.compose.core.proj.proj_errno_string
import tilo.compose.core.proj.proj_get_type
import tilo.compose.core.proj.proj_normalize_for_visualization
import tilo.compose.core.proj.proj_trans
import kotlin.math.abs
import kotlin.math.max

@OptIn(ExperimentalForeignApi::class)
private data class NativeTransform(
    val context: CPointer<PJ_CONTEXT>,
    val operation: CPointer<PJ>,
)

private data class TransformKey(
    val sourceCrs: String,
    val targetCrs: String,
)

private val transformLock = NSLock()

@OptIn(ExperimentalForeignApi::class)
private val transformCache = mutableMapOf<TransformKey, NativeTransform>()

private val worldUnitsPerMapUnitCache = mutableMapOf<String, Double>()

@OptIn(ExperimentalForeignApi::class)
internal actual fun proj4Transform(
    point: Point,
    sourceCrs: String,
    targetCrs: String,
): Point {
    if (sourceCrs == targetCrs) return point

    return transformLock.withLock {
        val transform =
            transformCache.getOrPut(TransformKey(sourceCrs, targetCrs)) {
                createTransform(sourceCrs, targetCrs)
            }
        val output =
            proj_trans(
                transform.operation,
                PJ_FWD,
                proj_coord(point.x, point.y, 0.0, 0.0),
            )
        val errorCode = proj_errno(transform.operation)
        require(errorCode == 0) {
            "PROJ transformation $sourceCrs -> $targetCrs failed: ${projError(errorCode)}"
        }

        output.useContents {
            val x = xy.x
            val y = xy.y
            require(x.isFinite() && y.isFinite()) {
                "PROJ transformation $sourceCrs -> $targetCrs returned non-finite coordinates."
            }
            Point(x = x, y = y)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun supportsProj4Transform(
    sourceCrs: String,
    targetCrs: String,
): Boolean {
    if (sourceCrs == targetCrs) return true

    return transformLock.withLock {
        try {
            transformCache.getOrPut(TransformKey(sourceCrs, targetCrs)) {
                createTransform(sourceCrs, targetCrs)
            }
            true
        } catch (_: IllegalArgumentException) {
            false
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun proj4WorldUnitsPerMapUnit(definition: String): Double =
    transformLock.withLock {
        worldUnitsPerMapUnitCache.getOrPut(definition) {
            readWorldUnitsPerMapUnit(definition)
        }
    }

@OptIn(ExperimentalForeignApi::class)
private fun readWorldUnitsPerMapUnit(definition: String): Double {
    val context = requireNotNull(proj_context_create()) { "PROJ could not allocate a CRS context." }
    val metadataDefinition = definition.asCrsDefinition()
    val crs =
        proj_create(context, metadataDefinition)
            ?: run {
                val error = projError(proj_context_errno(context))
                proj_context_destroy(context)
                throw IllegalArgumentException("PROJ could not read CRS $definition: $error")
            }
    val coordinateSystem =
        proj_crs_get_coordinate_system(context, crs)
            ?: run {
                proj_destroy(crs)
                proj_context_destroy(context)
                throw IllegalArgumentException("PROJ could not read coordinate-system metadata for $definition.")
            }

    return try {
        when (proj_get_type(crs)) {
            PJ_TYPE.PJ_TYPE_GEOGRAPHIC_CRS,
            PJ_TYPE.PJ_TYPE_GEOGRAPHIC_2D_CRS,
            PJ_TYPE.PJ_TYPE_GEOGRAPHIC_3D_CRS,
            // proj_normalize_for_visualization exposes traditional GIS lon/lat degrees.
            -> 1.0
            PJ_TYPE.PJ_TYPE_PROJECTED_CRS,
            PJ_TYPE.PJ_TYPE_DERIVED_PROJECTED_CRS,
            -> metersPerMapUnit / projectedUnitToSi(context, coordinateSystem, definition)
            else -> throw IllegalArgumentException(
                "CRS $definition is not a supported geographic or projected coordinate system.",
            )
        }
    } finally {
        proj_destroy(coordinateSystem)
        proj_destroy(crs)
        proj_context_destroy(context)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun projectedUnitToSi(
    context: CPointer<PJ_CONTEXT>,
    coordinateSystem: CPointer<PJ>,
    definition: String,
): Double {
    require(proj_cs_get_axis_count(context, coordinateSystem) >= 2) {
        "CRS $definition does not expose two horizontal coordinate axes."
    }
    val first = coordinateAxisUnit(context, coordinateSystem, 0, definition)
    val second = coordinateAxisUnit(context, coordinateSystem, 1, definition)
    require(first.isFinite() && first > 0.0) {
        "CRS $definition has an invalid coordinate unit conversion factor."
    }
    require(second.isFinite() && second > 0.0 && first.approximatelyEquals(second)) {
        "CRS $definition has inconsistent horizontal coordinate units."
    }
    return first
}

@OptIn(ExperimentalForeignApi::class)
private fun coordinateAxisUnit(
    context: CPointer<PJ_CONTEXT>,
    coordinateSystem: CPointer<PJ>,
    axis: Int,
    definition: String,
): Double =
    memScoped {
        val factor = alloc<DoubleVar>()
        require(
            proj_cs_get_axis_info(
                context,
                coordinateSystem,
                axis,
                null,
                null,
                null,
                factor.ptr,
                null,
                null,
                null,
            ) != 0,
        ) {
            "PROJ could not read coordinate units for axis $axis of $definition."
        }
        factor.value
    }

private fun String.asCrsDefinition(): String =
    if (
        contains("+proj=", ignoreCase = true) &&
        !contains("+type=crs", ignoreCase = true)
    ) {
        "$this +type=crs"
    } else {
        this
    }

private fun Double.approximatelyEquals(other: Double): Boolean =
    abs(this - other) <= max(abs(this), abs(other)) * UNIT_COMPARISON_EPSILON

@OptIn(ExperimentalForeignApi::class)
private fun createTransform(
    sourceCrs: String,
    targetCrs: String,
): NativeTransform {
    val context =
        requireNotNull(proj_context_create()) {
            "PROJ could not allocate a transformation context."
        }
    val rawOperation = proj_create_crs_to_crs(context, sourceCrs, targetCrs, null)
    if (rawOperation == null) {
        val error = projError(proj_context_errno(context))
        proj_context_destroy(context)
        throw IllegalArgumentException(
            "PROJ could not create transformation $sourceCrs -> $targetCrs: $error",
        )
    }

    val normalizedOperation = proj_normalize_for_visualization(context, rawOperation)
    proj_destroy(rawOperation)
    if (normalizedOperation == null) {
        val error = projError(proj_context_errno(context))
        proj_context_destroy(context)
        throw IllegalArgumentException(
            "PROJ could not normalize transformation $sourceCrs -> $targetCrs: $error",
        )
    }
    return NativeTransform(context = context, operation = normalizedOperation)
}

@OptIn(ExperimentalForeignApi::class)
private fun projError(errorCode: Int): String =
    if (errorCode == 0) {
        "unknown error"
    } else {
        proj_errno_string(errorCode)?.toKString() ?: "error $errorCode"
    }

private inline fun <T> NSLock.withLock(block: () -> T): T {
    lock()
    return try {
        block()
    } finally {
        unlock()
    }
}

private const val UNIT_COMPARISON_EPSILON = 1e-12
