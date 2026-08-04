package tilo.compose.core.feature

import kotlin.test.Test
import kotlin.test.assertFailsWith

class StyleValidationTest {
    @Test
    fun opacityMustBeWithinUnitRange() {
        assertFailsWith<IllegalArgumentException> { StrokeStyle(opacity = Double.NaN) }
        assertFailsWith<IllegalArgumentException> { CasingStyle(opacity = -0.1) }
        assertFailsWith<IllegalArgumentException> { FillStyle(opacity = 1.1) }
        assertFailsWith<IllegalArgumentException> {
            LabelBackgroundStyle(color = ColorValue.Black, opacity = Double.POSITIVE_INFINITY)
        }
    }

    @Test
    fun drawingDimensionsMustBeFiniteAndNonNegative() {
        assertFailsWith<IllegalArgumentException> { StrokeStyle(width = -1.0) }
        assertFailsWith<IllegalArgumentException> { CasingStyle(width = Double.NaN) }
        assertFailsWith<IllegalArgumentException> { PointStyle(size = 0.0) }
        assertFailsWith<IllegalArgumentException> { LabelStyle(fontSize = Double.POSITIVE_INFINITY) }
        assertFailsWith<IllegalArgumentException> { LabelStyle(haloWidth = -1.0) }
        assertFailsWith<IllegalArgumentException> {
            LabelBackgroundStyle(color = ColorValue.Black, cornerRadius = -1.0)
        }
    }

    @Test
    fun patternDimensionsMustBeFiniteAndPositive() {
        assertFailsWith<IllegalArgumentException> { FillPattern.Hatch(spacing = 0.0) }
        assertFailsWith<IllegalArgumentException> { FillPattern.Hatch(angleDegrees = Double.NaN) }
        assertFailsWith<IllegalArgumentException> { FillPattern.Dots(spacing = -1.0) }
        assertFailsWith<IllegalArgumentException> { FillPattern.Dots(radius = Double.POSITIVE_INFINITY) }
    }

    @Test
    fun dashPatternRequiresFinitePositivePairs() {
        assertFailsWith<IllegalArgumentException> { DashPattern(emptyList()) }
        assertFailsWith<IllegalArgumentException> { DashPattern(listOf(1.0)) }
        assertFailsWith<IllegalArgumentException> { DashPattern(listOf(1.0, 0.0)) }
        assertFailsWith<IllegalArgumentException> { DashPattern(listOf(1.0, 2.0), phase = Double.NaN) }
    }
}
