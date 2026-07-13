package eu.tilo.compose.cuzk

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ZabagedGeneralizationTest {

    @Test
    fun basemapUsesThreeZoomDependentGeometryProfiles() {
        assertEquals(ZabagedGeometryProfile.Overview, ZabagedGeometryProfile.forZoom(12.99))
        assertEquals(ZabagedGeometryProfile.Network, ZabagedGeometryProfile.forZoom(13.0))
        assertEquals(ZabagedGeometryProfile.Detailed, ZabagedGeometryProfile.forZoom(14.0))
    }

    @Test
    fun overviewPolygonsAreSimplifiedAndFilteredByVisiblePixelArea() {
        val query = LayerQuery.forLayer(ZabagedLayer.ArableLand, zoom = 11.5)

        assertEquals(40.0, query.maximumOffset(resolution = 10.0))
        assertEquals("(1=1) AND Shape_Area >= 128000.0", query.whereClause(resolution = 10.0))
        assertTrue("Overview" in query.variant)
    }

    @Test
    fun linearBasemapGeometryIsSimplifiedWithoutAreaPredicate() {
        val query = LayerQuery.forLayer(ZabagedLayer.Watercourses, zoom = 11.5)

        assertEquals(40.0, query.maximumOffset(resolution = 10.0))
        assertTrue(query.whereClause(resolution = 10.0).startsWith("jmeno IN"))
        assertTrue("Svratka" in query.whereClause(resolution = 10.0))
    }

    @Test
    fun immediateOrientationLayersKeepFineTolerance() {
        val query = LayerQuery.forLayer(ZabagedLayer.Roads, zoom = 11.5)

        assertEquals(2.5, query.maximumOffset(resolution = 10.0))
        assertFalse("Shape_Area" in query.whereClause(resolution = 10.0))
        assertFalse("paprsek" in query.whereClause(resolution = 10.0))
    }

    @Test
    fun overviewCarriesOnlyBroadLandColorsFromZoomTen() {
        assertEquals(10.0, ZabagedLayer.ArableLand.minimumZoom)
        assertEquals(10.0, ZabagedLayer.Grassland.minimumZoom)
        assertEquals(13.0, ZabagedLayer.OrchardsAndGardens.minimumZoom)
        assertEquals(13.0, ZabagedLayer.MaintainedGreenery.minimumZoom)
        assertEquals(13.0, ZabagedLayer.CategorizedForest.minimumZoom)
        assertEquals(10.0, ZabagedLayer.SettlementAreas.minimumZoom)
    }

    @Test
    fun everyZabagedLayerIsHardCutBelowZoomTen() {
        assertTrue(ZabagedLayer.entries.all { layer -> layer.minimumZoom >= 10.0 })
    }

    @Test
    fun overviewUsesCategorySpecificLargeVisibleAreaCutoffs() {
        val forest = LayerQuery.forLayer(ZabagedLayer.Forest, zoom = 10.5)
        val water = LayerQuery.forLayer(ZabagedLayer.WaterAreas, zoom = 10.5)
        val settlement = LayerQuery.forLayer(ZabagedLayer.SettlementAreas, zoom = 10.5)

        assertEquals("(1=1) AND Shape_Area >= 128000.0", forest.whereClause(resolution = 10.0))
        assertEquals("(1=1) AND Shape_Area >= 25600.0", water.whereClause(resolution = 10.0))
        assertEquals("(1=1) AND Shape_Area >= 51200.0", settlement.whereClause(resolution = 10.0))
    }

    @Test
    fun networkZoomStillRejectsSmallLandPolygons() {
        val forest = LayerQuery.forLayer(ZabagedLayer.Forest, zoom = 13.25)
        val water = LayerQuery.forLayer(ZabagedLayer.WaterAreas, zoom = 13.25)
        val settlement = LayerQuery.forLayer(ZabagedLayer.SettlementAreas, zoom = 13.25)

        assertEquals("(1=1) AND Shape_Area >= 12000.0", forest.whereClause(resolution = 10.0))
        assertEquals("(1=1) AND Shape_Area >= 2400.0", water.whereClause(resolution = 10.0))
        assertEquals("(1=1) AND Shape_Area >= 4800.0", settlement.whereClause(resolution = 10.0))
    }
}
