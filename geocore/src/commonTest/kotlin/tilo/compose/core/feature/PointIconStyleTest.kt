package tilo.compose.core.feature

import kotlin.test.Test
import kotlin.test.assertFailsWith

class PointIconStyleTest {
    @Test
    fun rejectsInvalidValues() {
        assertFailsWith<IllegalArgumentException> { PointIconStyle(id = "") }
        assertFailsWith<IllegalArgumentException> { PointIconStyle(id = "stop", size = 0.0) }
        assertFailsWith<IllegalArgumentException> { PointIconStyle(id = "stop", opacity = 1.1) }
    }
}
