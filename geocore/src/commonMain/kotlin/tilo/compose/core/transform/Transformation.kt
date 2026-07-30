package tilo.compose.core.transform

import tilo.compose.core.geometry.Point
import tilo.compose.core.projection.Projection

/**
 * Converts world coordinates between source and target coordinate systems/projectios.
 */
interface Transformation<S : Projection, T : Projection> {
    val source: S
    val target: T

    fun sourceToTarget(point: Point): Point

    fun targetToSource(point: Point): Point
}
