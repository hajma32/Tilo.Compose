package tilo.compose.core.map

import kotlin.test.Test
import kotlin.test.assertFailsWith

class MapConfigTest {
    @Test
    fun rejectsInvalidZoomLimits() {
        assertFailsWith<IllegalArgumentException> { MapConfig(minZoom = Double.NaN) }
        assertFailsWith<IllegalArgumentException> { MapConfig(maxZoom = Double.POSITIVE_INFINITY) }
        assertFailsWith<IllegalArgumentException> { MapConfig(minZoom = 10.0, maxZoom = 5.0) }
    }
}
