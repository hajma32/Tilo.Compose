package tilo.compose.render

import androidx.compose.ui.graphics.Matrix
import tilo.compose.core.geometry.Point
import tilo.compose.core.map.MapState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Affine world-to-screen transform whose camera-dependent terms are computed once per draw.
 *
 * The regular [MapState.worldToScreen] API intentionally favors convenience. Geometry rendering
 * applies the same transform to many thousands of vertices, so repeating zoom and trigonometric
 * calculations and allocating a point object for every vertex is unnecessarily expensive there.
 */
internal class WorldToScreenTransform private constructor(
    private val scaleCos: Double,
    private val scaleSin: Double,
    private val translateX: Double,
    private val translateY: Double,
) {
    val pixelScale: Float
        get() = kotlin.math.hypot(scaleCos, scaleSin).toFloat()

    fun screenX(
        worldX: Double,
        worldY: Double,
    ): Double = worldX * scaleCos - worldY * scaleSin + translateX

    fun screenY(
        worldX: Double,
        worldY: Double,
    ): Double = -worldX * scaleSin - worldY * scaleCos + translateY

    fun localToScreenMatrix(origin: Point): Matrix =
        Matrix().also { matrix ->
            matrix[0, 0] = scaleCos.toFloat()
            matrix[0, 1] = -scaleSin.toFloat()
            matrix[1, 0] = -scaleSin.toFloat()
            matrix[1, 1] = -scaleCos.toFloat()
            matrix[3, 0] = screenX(origin.x, origin.y).toFloat()
            matrix[3, 1] = screenY(origin.x, origin.y).toFloat()
        }

    companion object {
        fun from(map: MapState): WorldToScreenTransform {
            val pixelScale =
                2.0.pow(map.zoom) /
                    map.projection.worldUnitsPerMapUnit *
                    map.viewport.pixelRatio
            val radians = map.bearing * PI / 180.0
            val scaleCos = pixelScale * cos(radians)
            val scaleSin = pixelScale * sin(radians)
            return WorldToScreenTransform(
                scaleCos = scaleCos,
                scaleSin = scaleSin,
                translateX =
                    -map.center.x * scaleCos +
                        map.center.y * scaleSin +
                        map.viewport.width / 2.0,
                translateY =
                    map.center.x * scaleSin +
                        map.center.y * scaleCos +
                        map.viewport.height / 2.0,
            )
        }
    }
}
