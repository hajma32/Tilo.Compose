package tilo.compose.render

import tilo.compose.core.map.MapState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Camera transform precomputed once for a vector draw.
 *
 * Unlike [MapState.worldToScreen], this avoids recalculating zoom/trigonometry and allocating a
 * point for every rendered vertex.
 */
internal class WorldToScreenTransform private constructor(
    private val scaleCos: Double,
    private val scaleSin: Double,
    private val translateX: Double,
    private val translateY: Double,
) {
    fun screenX(
        worldX: Double,
        worldY: Double,
    ): Double = worldX * scaleCos - worldY * scaleSin + translateX

    fun screenY(
        worldX: Double,
        worldY: Double,
    ): Double = -worldX * scaleSin - worldY * scaleCos + translateY

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
