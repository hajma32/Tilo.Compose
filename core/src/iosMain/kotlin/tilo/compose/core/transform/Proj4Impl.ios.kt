package tilo.compose.core.transform

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.cinterop.useContents
import platform.Foundation.NSLock
import tilo.compose.core.geometry.Point
import tilo.compose.core.proj.PJ
import tilo.compose.core.proj.PJ_CONTEXT
import tilo.compose.core.proj.PJ_FWD
import tilo.compose.core.proj.proj_context_create
import tilo.compose.core.proj.proj_context_destroy
import tilo.compose.core.proj.proj_context_errno
import tilo.compose.core.proj.proj_coord
import tilo.compose.core.proj.proj_create_crs_to_crs
import tilo.compose.core.proj.proj_destroy
import tilo.compose.core.proj.proj_errno
import tilo.compose.core.proj.proj_errno_string
import tilo.compose.core.proj.proj_normalize_for_visualization
import tilo.compose.core.proj.proj_trans

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
