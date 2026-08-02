package tilo.compose.core.transform

import tilo.compose.core.geometry.Point
import tilo.compose.core.projection.Projection
import tilo.compose.core.projection.ReferencedProjection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class TransformationRegistryTest {
    @Test
    fun discoversProjectionOwnedTransformationInBothDirections() {
        val local = localProjection(reference = ReferenceProjection)
        val registry = TransformationRegistry.Default

        assertEquals(
            Point(2.0, 4.0),
            registry.resolve(local, ReferenceProjection).sourceToTarget(Point(6.0, 12.0)),
        )
        assertEquals(
            Point(6.0, 12.0),
            registry.resolve(ReferenceProjection, local).sourceToTarget(Point(2.0, 4.0)),
        )
    }

    @Test
    fun composesProjectionOwnedTransformationWithRuntimeProvider() {
        val local = localProjection(reference = ReferenceProjection)
        val resolved = OffsetTransformation(ReferenceProjection, TargetProjection, offset = 5.0)
        val registry =
            TransformationRegistry(
                providers =
                    listOf(
                        TransformationProvider { source, target ->
                            if (source.id == ReferenceProjection.id && target.id == TargetProjection.id) {
                                resolved
                            } else {
                                null
                            }
                        },
                    ),
            )

        assertEquals(
            Point(7.0, 9.0),
            registry.resolve(local, TargetProjection).sourceToTarget(Point(6.0, 12.0)),
        )
        assertEquals(
            Point(6.0, 12.0),
            registry.resolve(TargetProjection, local).sourceToTarget(Point(7.0, 9.0)),
        )
    }

    @Test
    fun projectionOwnedPathTakesPrecedenceOverProvider() {
        val local = localProjection(reference = ReferenceProjection)
        var directProviderCalls = 0
        val registry =
            TransformationRegistry(
                providers =
                    listOf(
                        TransformationProvider { source, target ->
                            when {
                                source.id == local.id && target.id == TargetProjection.id -> {
                                    directProviderCalls += 1
                                    OffsetTransformation(local, TargetProjection, offset = 100.0)
                                }

                                source.id == ReferenceProjection.id && target.id == TargetProjection.id ->
                                    OffsetTransformation(ReferenceProjection, TargetProjection, offset = 5.0)

                                else -> null
                            }
                        },
                    ),
            )

        val transformed = registry.resolve(local, TargetProjection).sourceToTarget(Point(6.0, 12.0))

        assertEquals(Point(7.0, 9.0), transformed)
        assertEquals(0, directProviderCalls)
    }

    @Test
    fun dynamicResolversAreConsultedInOrder() {
        val unsupported = TransformationProvider { _, _ -> null }
        val resolved = OffsetTransformation(SourceProjection, TargetProjection, offset = 5.0)
        val supported = TransformationProvider { _, _ -> resolved }
        val registry = TransformationRegistry(providers = listOf(unsupported, supported))

        assertSame(resolved, registry.resolve(SourceProjection, TargetProjection))
    }

    @Test
    fun cachesResolvedTransformationForEquivalentCrsPair() {
        var providerCalls = 0
        val resolved = OffsetTransformation(SourceProjection, TargetProjection, offset = 5.0)
        val registry =
            TransformationRegistry(
                providers =
                    listOf(
                        TransformationProvider { _, _ ->
                            providerCalls += 1
                            resolved
                        },
                    ),
            )

        assertSame(resolved, registry.resolve(SourceProjection, TargetProjection))
        assertSame(resolved, registry.resolve(SourceProjection, TargetProjection))
        assertEquals(1, providerCalls)
    }

    @Test
    fun sameIdWithDifferentDefinitionIsNotTreatedAsIdentity() {
        val source = DefinedTestProjection(id = "SHARED", definition = "SOURCE")
        val target = DefinedTestProjection(id = "SHARED", definition = "TARGET")
        val resolved = OffsetTransformation(source, target, offset = 5.0)
        val registry = TransformationRegistry(listOf(TransformationProvider { _, _ -> resolved }))

        assertSame(resolved, registry.resolve(source, target))
    }

    @Test
    fun rejectsResolverResultForAnotherCrsPair() {
        val wrongDirection = OffsetTransformation(TargetProjection, SourceProjection, offset = 5.0)
        val registry = TransformationRegistry(providers = listOf(TransformationProvider { _, _ -> wrongDirection }))

        assertFailsWith<IllegalArgumentException> {
            registry.resolve(SourceProjection, TargetProjection)
        }
    }

    @Test
    fun sameCrsUsesIdentityWithoutConsultingResolvers() {
        var resolverCalls = 0
        val registry =
            TransformationRegistry(
                providers =
                    listOf(
                        TransformationProvider { _, _ ->
                            resolverCalls += 1
                            null
                        },
                    ),
            )

        val identity = registry.resolve(SourceProjection, SourceProjection)

        assertEquals(Point(1.0, 2.0), identity.sourceToTarget(Point(1.0, 2.0)))
        assertEquals(0, resolverCalls)
    }

    @Test
    fun constructorDefensivelyCopiesProviders() {
        val providers = mutableListOf<TransformationProvider>()
        val registry = TransformationRegistry(providers)

        providers += TransformationProvider { _, _ -> null }

        assertEquals(emptyList(), registry.providers)
    }

    @Test
    fun equalProvidersProduceEqualRegistryValues() {
        val provider = TransformationProvider { _, _ -> null }
        val first = TransformationRegistry(listOf(provider))
        val second = TransformationRegistry(listOf(provider))

        assertNotSame(first, second)
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun reportsUnsupportedCrsPairWithTypedException() {
        val error =
            assertFailsWith<UnsupportedCrsTransformationException> {
                TransformationRegistry.Default.resolve(SourceProjection, TargetProjection)
            }

        assertEquals(SourceProjection.id, error.sourceId)
        assertEquals(TargetProjection.id, error.targetId)
    }

    private fun localProjection(reference: Projection): Projection =
        ReferencedProjection(
            id = "TEST:LOCAL",
            reference = reference,
            toReference = { point -> Point(point.x / 3.0, point.y / 3.0) },
            fromReference = { point -> Point(point.x * 3.0, point.y * 3.0) },
        )

    private object SourceProjection : Projection {
        override val id: String = "TEST:SOURCE"
    }

    private object ReferenceProjection : Projection {
        override val id: String = "TEST:REFERENCE"
    }

    private object TargetProjection : Projection {
        override val id: String = "TEST:TARGET"
    }

    private data class DefinedTestProjection(
        override val id: String,
        override val definition: String,
    ) : Projection

    private class OffsetTransformation(
        override val source: Projection,
        override val target: Projection,
        private val offset: Double,
    ) : Transformation<Projection, Projection> {
        override fun sourceToTarget(point: Point): Point = Point(point.x + offset, point.y + offset)

        override fun targetToSource(point: Point): Point = Point(point.x - offset, point.y - offset)
    }
}
