@file:OptIn(ExperimentalTiloApi::class)

package tilo.compose.dsl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import tilo.compose.core.layers.vector.VectorRenderStrategy

class FeatureRenderModeTest {
    @Test
    fun convertsDslModesToEngineStrategies() {
        assertEquals(VectorRenderStrategy.Immediate, immediate().toVectorRenderStrategy())
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
}
