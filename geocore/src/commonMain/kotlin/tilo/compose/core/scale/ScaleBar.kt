package tilo.compose.core.scale

import tilo.compose.core.map.MapState
import tilo.compose.core.map.ScreenPoint
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** A rounded real-world distance and its corresponding on-screen width. */
data class ScaleBar(
    val distanceMeters: Double,
    val widthPx: Double,
    val label: String,
    val midpointLabel: String,
)

/** Derives a readable scale-bar measurement at the lower portion of the current viewport. */
object ScaleBarCalculator {
    fun calculate(
        map: MapState,
        maxWidthPx: Double = 160.0 * map.viewport.pixelRatio,
        screenY: Double =
            (map.viewport.height - 32.0 * map.viewport.pixelRatio)
                .coerceIn(0.0, map.viewport.height.toDouble()),
    ): ScaleBar? {
        if (map.viewport.width <= 0 || map.viewport.height <= 0 || maxWidthPx <= 0.0) {
            return null
        }

        val centerX = map.viewport.width / 2.0
        val from = map.screenToWorld(ScreenPoint(centerX - maxWidthPx / 2.0, screenY))
        val to = map.screenToWorld(ScreenPoint(centerX + maxWidthPx / 2.0, screenY))
        val maxDistanceMeters =
            map.config.distanceCalculator.distanceMeters(
                from = from,
                to = to,
                projection = map.projection,
                transformationRegistry = map.transformationRegistry,
            ) ?: return null

        if (maxDistanceMeters <= 0.0) return null

        val niceDistanceMeters = niceDistance(maxDistanceMeters)
        return ScaleBar(
            distanceMeters = niceDistanceMeters,
            widthPx = maxWidthPx * (niceDistanceMeters / maxDistanceMeters),
            label = formatDistance(niceDistanceMeters),
            midpointLabel = formatDistance(niceDistanceMeters / 2.0),
        )
    }

    internal fun niceDistance(maxDistanceMeters: Double): Double {
        val exponent = floor(log10(maxDistanceMeters))
        val base = 10.0.pow(exponent)
        val normalized = maxDistanceMeters / base
        val nice =
            when {
                normalized >= 5.0 -> 5.0
                normalized >= 2.0 -> 2.0
                else -> 1.0
            }
        return nice * base
    }

    internal fun formatDistance(distanceMeters: Double): String =
        if (distanceMeters >= 1000.0) {
            val kilometers = distanceMeters / 1000.0
            if (kilometers % 1.0 == 0.0) {
                "${kilometers.toInt()} km"
            } else {
                "$kilometers km"
            }
        } else if (distanceMeters >= 1.0) {
            "${distanceMeters.toInt()} m"
        } else {
            "${(distanceMeters * 100.0).toInt()} cm"
        }
}
