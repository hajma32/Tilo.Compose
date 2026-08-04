package tilo.compose.core.geometry

import kotlin.test.Test
import kotlin.test.assertFailsWith

class PointValidationTest {
    @Test
    fun coordinatesMustBeFinite() {
        assertFailsWith<IllegalArgumentException> { Point(Double.NaN, 0.0) }
        assertFailsWith<IllegalArgumentException> { Point(0.0, Double.POSITIVE_INFINITY) }
    }
}
