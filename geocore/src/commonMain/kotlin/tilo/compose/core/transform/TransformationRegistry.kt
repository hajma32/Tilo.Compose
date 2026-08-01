package tilo.compose.core.transform

import tilo.compose.core.geometry.Point
import tilo.compose.core.projection.Projection
import tilo.compose.core.projection.ReferencedProjection

/**
 * Runtime infrastructure that resolves projection-owned transformations and platform providers.
 *
 * Application-facing map configuration does not expose this type. Same-CRS transforms are handled
 * implicitly as identity.
 */
class TransformationRegistry(
    providers: List<TransformationProvider> = emptyList(),
) {
    private data class Key(
        val sourceId: String,
        val targetId: String,
    )

    /** Runtime providers consulted in declaration order after projection-owned transformations. */
    val providers: List<TransformationProvider> = providers.toList()

    fun find(
        source: Projection,
        target: Projection,
    ): Transformation<Projection, Projection>? = find(source, target, emptySet())

    private fun find(
        source: Projection,
        target: Projection,
        visited: Set<Key>,
    ): Transformation<Projection, Projection>? {
        if (source.id == target.id) return SameProjectionTransformation(source, target)

        val key = Key(source.id, target.id)
        if (key in visited) return null
        val nextVisited = visited + key

        return directProjectionTransformation(source, target)
            ?: resolveFromProviders(source, target)
            ?: findThroughReferences(source, target, nextVisited)
    }

    private fun findThroughReferences(
        source: Projection,
        target: Projection,
        visited: Set<Key>,
    ): Transformation<Projection, Projection>? {
        val throughSource =
            (source as? ReferencedProjection)?.referenceTransformation?.let { first ->
                find(first.target, target, visited)?.let { second -> CompositeTransformation(first, second) }
            }
        return throughSource
            ?: (target as? ReferencedProjection)?.referenceTransformation?.let { targetToReference ->
                val second = ReversedTransformation(targetToReference)
                find(source, second.source, visited)?.let { first -> CompositeTransformation(first, second) }
            }
    }

    private fun directProjectionTransformation(
        source: Projection,
        target: Projection,
    ): Transformation<Projection, Projection>? =
        when {
            source is ReferencedProjection && source.reference.id == target.id ->
                source.referenceTransformation
            target is ReferencedProjection && target.reference.id == source.id ->
                ReversedTransformation(target.referenceTransformation)
            else -> null
        }

    private fun resolveFromProviders(
        source: Projection,
        target: Projection,
    ): Transformation<Projection, Projection>? {
        providers.forEach { provider ->
            provider.resolve(source, target)?.let { transformation ->
                validateProviderResult(transformation, source, target)
                return transformation
            }
            provider.resolve(target, source)?.let { transformation ->
                validateProviderResult(transformation, target, source)
                return ReversedTransformation(transformation)
            }
        }
        return null
    }

    private fun validateProviderResult(
        transformation: Transformation<Projection, Projection>,
        source: Projection,
        target: Projection,
    ) {
        require(
            transformation.source.id == source.id && transformation.target.id == target.id,
        ) {
            "Transformation provider returned ${transformation.source.id} -> " +
                "${transformation.target.id} for requested ${source.id} -> ${target.id}."
        }
    }

    fun resolve(
        source: Projection,
        target: Projection,
    ): Transformation<Projection, Projection> =
        find(source, target)
            ?: throw UnsupportedCrsTransformationException(source.id, target.id)

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is TransformationRegistry &&
            providers == other.providers

    override fun hashCode(): Int = providers.hashCode()

    private class SameProjectionTransformation(
        override val source: Projection,
        override val target: Projection,
    ) : Transformation<Projection, Projection> {
        override fun sourceToTarget(point: Point): Point = point

        override fun targetToSource(point: Point): Point = point
    }

    private class ReversedTransformation(
        private val delegate: Transformation<Projection, Projection>,
    ) : Transformation<Projection, Projection> {
        override val source: Projection = delegate.target
        override val target: Projection = delegate.source

        override fun sourceToTarget(point: Point): Point = delegate.targetToSource(point)

        override fun targetToSource(point: Point): Point = delegate.sourceToTarget(point)
    }

    private class CompositeTransformation(
        private val first: Transformation<Projection, Projection>,
        private val second: Transformation<Projection, Projection>,
    ) : Transformation<Projection, Projection> {
        override val source: Projection = first.source
        override val target: Projection = second.target

        override fun sourceToTarget(point: Point): Point = second.sourceToTarget(first.sourceToTarget(point))

        override fun targetToSource(point: Point): Point = first.targetToSource(second.targetToSource(point))
    }

    companion object {
        val Default = TransformationRegistry()
    }
}
