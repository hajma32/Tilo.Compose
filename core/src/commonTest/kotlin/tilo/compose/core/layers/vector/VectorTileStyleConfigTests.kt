package tilo.compose.core.layers.vector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VectorTileStyleConfigTests {

    @Test
    fun parsePresetOrderRespectsExplicitOrderAndEnabledFlags() {
        val config = """
            order = landuse, water, roads
            roads.enabled = false
        """.trimIndent()

        val ids = VectorTileStyleConfig.parsePresetOrder(config)

        assertEquals(listOf("landuse", "water"), ids)
    }

    @Test
    fun parsePresetOrderFallsBackToDefaultBatchWhenOrderMissing() {
        val config = """
            roads.enabled = true
            water.enabled = true
        """.trimIndent()

        val ids = VectorTileStyleConfig.parsePresetOrder(config)

        assertEquals(DefaultVectorTileBasemapStyleConfig.defaultOrder, ids)
    }

    @Test
    fun parsePresetOrderFallsBackToDefaultWhenEverythingDisabled() {
        val config = """
            order = landuse, water, buildings
            landuse.enabled = false
            water.enabled = false
            buildings.enabled = false
        """.trimIndent()

        val ids = VectorTileStyleConfig.parsePresetOrder(config)

        assertEquals(DefaultVectorTileBasemapStyleConfig.defaultOrder, ids)
    }

    @Test
    fun parseStylesFailsForUnknownPreset() {
        val config = "order = landuse, unknown"

        assertFailsWith<IllegalStateException> {
            VectorTileStyleConfig.parseStyles(config)
        }
    }
}
