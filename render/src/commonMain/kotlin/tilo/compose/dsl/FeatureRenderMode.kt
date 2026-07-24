@file:OptIn(ExperimentalTiloApi::class)

package tilo.compose.dsl

import tilo.compose.core.layers.vector.VectorRenderStrategy

/**
 * Compose DSL choice for rendering an in-memory feature layer.
 *
 * Concrete implementations stay private so the DSL does not expose renderer
 * engine types as part of its public contract.
 */
@ExperimentalTiloApi
sealed interface FeatureRenderMode

private data object ImmediateFeatureRenderMode : FeatureRenderMode

private data class ImmediateLodFeatureRenderMode(
    val tolerancePx: Double,
) : FeatureRenderMode

private data class CachedBitmapFeatureRenderMode(
    val scale: Double,
    val paddingPx: Int,
    val invalidateOnZoomDelta: Double,
) : FeatureRenderMode

/** Draw vector features directly every frame. */
@ExperimentalTiloApi
fun immediate(): FeatureRenderMode = ImmediateFeatureRenderMode

/**
 * Draw vector features directly after zoom-dependent Douglas-Peucker simplification.
 *
 * [tolerancePx] is the maximum screen-space deviation for lines and polygon rings.
 */
@ExperimentalTiloApi
fun immediateLod(tolerancePx: Double = 1.5): FeatureRenderMode {
    require(tolerancePx.isFinite() && tolerancePx > 0.0) {
        "tolerancePx must be finite and positive"
    }
    return ImmediateLodFeatureRenderMode(tolerancePx)
}

/**
 * Render vector features into an offscreen bitmap and reuse it while panning.
 *
 * This is useful for heavier, mostly static feature layers.
 */
@ExperimentalTiloApi
fun cachedBitmap(
    scale: Double = 1.0,
    paddingPx: Int = 128,
    invalidateOnZoomDelta: Double = 0.35,
): FeatureRenderMode {
    require(scale.isFinite() && scale > 0.0) { "scale must be finite and positive" }
    require(paddingPx >= 0) { "paddingPx must be non-negative" }
    require(invalidateOnZoomDelta.isFinite() && invalidateOnZoomDelta >= 0.0) {
        "invalidateOnZoomDelta must be finite and non-negative"
    }
    return CachedBitmapFeatureRenderMode(
        scale = scale,
        paddingPx = paddingPx,
        invalidateOnZoomDelta = invalidateOnZoomDelta,
    )
}

internal fun FeatureRenderMode.toVectorRenderStrategy(): VectorRenderStrategy =
    when (this) {
        ImmediateFeatureRenderMode -> VectorRenderStrategy.Immediate
        is ImmediateLodFeatureRenderMode -> VectorRenderStrategy.ImmediateLod(tolerancePx)
        is CachedBitmapFeatureRenderMode ->
            VectorRenderStrategy.CachedBitmap(
                scale = scale,
                paddingPx = paddingPx,
                invalidateOnZoomDelta = invalidateOnZoomDelta,
            )
    }
