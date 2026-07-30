package tilo.compose.core.transform

import tilo.compose.core.geometry.Point
import tilo.compose.core.projection.Projection

/**
 * Resolves point transformations between source and target CRS.
 *
 * Same-CRS transforms are handled implicitly as identity.
 */
class TransformationRegistry(
    val transformations: List<Transformation<Projection, Projection>> = emptyList(),
) {
    private data class Key(
        val sourceId: String,
        val targetId: String,
    )

    private val transformationsByKey = transformations.associateBy { Key(it.source.id, it.target.id) }

    fun find(
        source: Projection,
        target: Projection,
    ): Transformation<Projection, Projection>? {
        if (source.id == target.id) return SameProjectionTransformation(source, target)
        return transformationsByKey[Key(source.id, target.id)]
    }

    fun resolve(
        source: Projection,
        target: Projection,
    ): Transformation<Projection, Projection> =
        find(source, target)
            ?: error("No transformation registered for ${source.id} -> ${target.id}.")

    fun withTransformation(transformation: Transformation<Projection, Projection>): TransformationRegistry =
        TransformationRegistry(
            transformations =
                (transformations + transformation)
                    .distinctBy { it.source.id to it.target.id },
        )

    private class SameProjectionTransformation(
        override val source: Projection,
        override val target: Projection,
    ) : Transformation<Projection, Projection> {
        override fun sourceToTarget(point: Point): Point = point

        override fun targetToSource(point: Point): Point = point
    }

    companion object {
        val Default = TransformationRegistry()
    }
}
