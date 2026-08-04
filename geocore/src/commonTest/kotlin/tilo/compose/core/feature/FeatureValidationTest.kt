package tilo.compose.core.feature

import tilo.compose.core.geometry.Point
import kotlin.test.Test
import kotlin.test.assertFailsWith

class FeatureValidationTest {
    @Test
    fun blankKeyIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            Feature(key = "  ", geometry = Point(0.0, 0.0))
        }
    }
}
