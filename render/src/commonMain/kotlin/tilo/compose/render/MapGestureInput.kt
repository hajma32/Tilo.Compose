package tilo.compose.render

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import tilo.compose.core.geometry.Point
import tilo.compose.core.map.Map
import kotlin.math.ln

/**
 * Handles pan and pinch-to-zoom gestures on the map.
 * Calls [onChanged] after each gesture so the caller can trigger recomposition.
 */
internal fun Modifier.mapGestureInput(
    map: Map,
    onChanged: () -> Unit
): Modifier = pointerInput(map) {
    detectTransformGestures { centroid, pan, zoom, _ ->
        if (pan.x != 0f || pan.y != 0f) {
            map.panBy(-pan.x.toDouble(), -pan.y.toDouble())
        }
        if (zoom != 1.0f) {
            map.zoomBy(
                delta = ln(zoom.toDouble()) / ln(2.0),
                focus = Point(centroid.x.toDouble(), centroid.y.toDouble())
            )
        }
        onChanged()
    }
}

internal fun Modifier.mapTapInput(
    map: Map,
    onTap: ((screenPoint: Point, worldPoint: Point) -> Unit)?,
    onChanged: () -> Unit,
): Modifier {
    if (onTap == null) return this
    return pointerInput(map, onTap) {
        detectTapGestures { offset ->
            val screenPoint = Point(offset.x.toDouble(), offset.y.toDouble())
            onTap(screenPoint, map.screenToWorld(screenPoint))
            onChanged()
        }
    }
}
