@file:OptIn(ExperimentalTiloApi::class)

package tilo.compose.dsl

import tilo.compose.core.layers.vector.VectorRenderStrategy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FeatureRenderModeTest {
    @Test
    fun convertsDslModesToEngineStrategies() {
        assertEquals(VectorRenderStrategy.Immediate, immediate().toVectorRenderStrategy())
        assertEquals(
            VectorRenderStrategy.ImmediateLod(tolerancePx = 2.0),
            immediateLod(tolerancePx = 2.0).toVectorRenderStrategy(),
        )
        assertEquals(
            VectorRenderStrategy.CachedBitmap(scale = 2.0, paddingPx = 64, invalidateOnZoomDelta = 0.5),
            cachedBitmap(scale = 2.0, paddingPx = 64, invalidateOnZoomDelta = 0.5).toVectorRenderStrategy(),
        )
    }

    @Test
    fun rejectsInvalidCachedBitmapConfiguration() {
        assertFailsWith<IllegalArgumentException> { cachedBitmap(scale = 0.0) }
        assertFailsWith<IllegalArgumentException> { cachedBitmap(paddingPx = -1) }
        assertFailsWith<IllegalArgumentException> { cachedBitmap(invalidateOnZoomDelta = Double.NaN) }
    }

    @Test
    fun rejectsInvalidImmediateLodConfiguration() {
        assertFailsWith<IllegalArgumentException> { immediateLod(tolerancePx = 0.0) }
        assertFailsWith<IllegalArgumentException> { immediateLod(tolerancePx = Double.NaN) }
    }
}
