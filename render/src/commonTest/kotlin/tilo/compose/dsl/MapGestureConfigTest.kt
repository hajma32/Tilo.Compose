@file:OptIn(ExperimentalTiloApi::class)

package tilo.compose.dsl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MapGestureConfigTest {
    @Test
    fun defaultRotationThresholdFavorsIntentionalRotation() {
        assertEquals(8.0, MapGestureConfig.Default.rotationThresholdDegrees)
    }

    @Test
    fun rotationThresholdMustBeFiniteAndNonNegative() {
        assertFailsWith<IllegalArgumentException> {
            MapGestureConfig(rotationThresholdDegrees = -1.0)
        }
        assertFailsWith<IllegalArgumentException> {
            MapGestureConfig(rotationThresholdDegrees = Double.NaN)
        }
        assertFailsWith<IllegalArgumentException> {
            MapGestureConfig(rotationThresholdDegrees = Double.POSITIVE_INFINITY)
        }
    }
}
