package tilo.compose.core.transform

import tilo.compose.core.projection.Projection

/**
 * Runtime capability that creates transformations for supported CRS pairs.
 *
 * Platform transformation engines implement this contract once; map users do not
 * register individual transformations. Providers are consulted in runtime order. Return `null`
 * when the pair is unsupported so another provider or a projection-owned path can be used.
 * Resolution results, including unsupported pairs, are cached; implementations must therefore
 * return stable capabilities for their lifetime.
 */
fun interface TransformationProvider {
    fun resolve(
        source: Projection,
        target: Projection,
    ): Transformation<Projection, Projection>?
}

/** Raised when neither a projection-owned transformation nor a runtime provider supports a pair. */
class UnsupportedCrsTransformationException(
    val sourceId: String,
    val targetId: String,
) : IllegalStateException("No transformation available for $sourceId -> $targetId.")
