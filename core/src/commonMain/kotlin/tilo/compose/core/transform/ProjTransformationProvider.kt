package tilo.compose.core.transform

import tilo.compose.core.geometry.Point
import tilo.compose.core.projection.Projection

/**
 * Platform-backed runtime provider for CRS identifiers supported by PROJ or Proj4J.
 *
 * Android resolves identifiers through Proj4J and its EPSG dataset. Apple targets use the bundled
 * PROJ database. Transformation objects are lightweight; platform implementations cache compiled
 * coordinate operations by source and target identifier.
 */
object ProjTransformationProvider : TransformationProvider {
    override fun resolve(
        source: Projection,
        target: Projection,
    ): Transformation<Projection, Projection>? =
        if (
            supportsProj4Transform(source.definition, target.definition) &&
            supportsProj4Transform(target.definition, source.definition)
        ) {
            ProjTransformation(source, target)
        } else {
            null
        }
}

private class ProjTransformation(
    override val source: Projection,
    override val target: Projection,
) : Transformation<Projection, Projection> {
    override fun sourceToTarget(point: Point): Point = proj4Transform(point, source.definition, target.definition)

    override fun targetToSource(point: Point): Point = proj4Transform(point, target.definition, source.definition)
}
