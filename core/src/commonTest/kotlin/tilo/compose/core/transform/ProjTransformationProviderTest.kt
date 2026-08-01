package tilo.compose.core.transform

import tilo.compose.core.geometry.Point
import tilo.compose.core.projection.DefinedProjection
import tilo.compose.core.projection.Epsg3857Projection
import tilo.compose.core.projection.Epsg4326Projection
import tilo.compose.core.projection.Projection
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProjTransformationProviderTest {
    @Test
    fun platformProviderTransformsKnownEpsgPairWithoutExplicitRegistration() {
        val input = Point(x = 16.6068, y = 49.1951)
        val transformer = CrsTransformer(TransformationRegistry(providers = listOf(ProjTransformationProvider)))

        val transformed = transformer.sourceToTarget(input, Epsg4326Projection, Epsg3857Projection)

        val expectedX = input.x * WEB_MERCATOR_RADIUS * PI / 180.0
        val expectedY = WEB_MERCATOR_RADIUS * ln(tan(PI / 4.0 + input.y * PI / 360.0))
        assertEquals(expectedX, transformed.x, absoluteTolerance = 1e-6)
        assertEquals(expectedY, transformed.y, absoluteTolerance = 1e-6)
    }

    @Test
    fun platformProviderTransformsProjStringWithoutExplicitRegistration() {
        val projWgs84 =
            DefinedProjection(
                definition = "+proj=longlat +datum=WGS84 +no_defs",
                id = "TEST:PROJ-WGS84",
            )
        val transformer = CrsTransformer(TransformationRegistry(providers = listOf(ProjTransformationProvider)))
        val input = Point(x = 16.6068, y = 49.1951)

        val transformed = transformer.sourceToTarget(input, projWgs84, Epsg3857Projection)

        val expectedX = input.x * WEB_MERCATOR_RADIUS * PI / 180.0
        val expectedY = WEB_MERCATOR_RADIUS * ln(tan(PI / 4.0 + input.y * PI / 360.0))
        assertEquals(expectedX, transformed.x, absoluteTolerance = 1e-6)
        assertEquals(expectedY, transformed.y, absoluteTolerance = 1e-6)
    }

    @Test
    fun earlierProviderOverridesPlatformProvider() {
        val override =
            object : Transformation<Projection, Projection> {
                override val source = Epsg4326Projection
                override val target = Epsg3857Projection

                override fun sourceToTarget(point: Point): Point = Point(1.0, 2.0)

                override fun targetToSource(point: Point): Point = Point(3.0, 4.0)
            }
        val transformer =
            CrsTransformer(
                TransformationRegistry(
                    providers =
                        listOf(
                            TransformationProvider { source, target ->
                                when {
                                    source.id == override.source.id && target.id == override.target.id -> override
                                    else -> null
                                }
                            },
                            ProjTransformationProvider,
                        ),
                ),
            )

        assertEquals(
            Point(1.0, 2.0),
            transformer.sourceToTarget(Point(0.0, 0.0), Epsg4326Projection, Epsg3857Projection),
        )
        assertEquals(
            Point(3.0, 4.0),
            transformer.sourceToTarget(Point(0.0, 0.0), Epsg3857Projection, Epsg4326Projection),
        )
    }

    @Test
    fun returnsNullForUnsupportedCrsPair() {
        val unsupported =
            object : Projection {
                override val id: String = "NOT-A-REAL-CRS"
            }

        assertNull(ProjTransformationProvider.resolve(unsupported, Epsg4326Projection))
    }

    @Test
    fun platformProviderRemainsLastFallback() {
        val customTransformation =
            object : Transformation<Projection, Projection> {
                override val source = Epsg4326Projection
                override val target = Epsg3857Projection

                override fun sourceToTarget(point: Point): Point = Point(7.0, 8.0)

                override fun targetToSource(point: Point): Point = Point(9.0, 10.0)
            }
        val customProvider = TransformationProvider { _, _ -> customTransformation }
        val transformer =
            CrsTransformer(
                TransformationRegistry(
                    providers = listOf(customProvider, ProjTransformationProvider),
                ),
            )

        assertEquals(
            Point(7.0, 8.0),
            transformer.sourceToTarget(
                Point(0.0, 0.0),
                Epsg4326Projection,
                Epsg3857Projection,
            ),
        )
    }

    private companion object {
        const val WEB_MERCATOR_RADIUS = 6_378_137.0
    }
}
