package tilo.compose.core.transform

import tilo.compose.core.geometry.Point

internal actual fun proj4Transform(point: Point, sourceCrs: String, targetCrs: String): Point =
    if (sourceCrs == targetCrs) {
        point
    } else {
        throw UnsupportedOperationException(
            "PROJ-backed CRS transformation $sourceCrs -> $targetCrs is not available on iOS.",
        )
    }
