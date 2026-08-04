@file:OptIn(ExperimentalTiloApi::class)

package tilo.compose.dsl

import kotlin.test.Test
import kotlin.test.assertFailsWith

class FeatureDslValidationTest {
    @Test
    fun duplicateKeysAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            features {
                point(key = "station", x = 14.0, y = 50.0)
                point(key = "station", x = 15.0, y = 51.0)
            }
        }
    }

    @Test
    fun failedFeatureDoesNotReserveItsKey() {
        val builder = FeatureListBuilder()
        assertFailsWith<IllegalStateException> {
            builder.point(key = "station", x = 14.0, y = 50.0) {
                error("Invalid options")
            }
        }

        builder.point(key = "station", x = 15.0, y = 51.0)
    }
}
