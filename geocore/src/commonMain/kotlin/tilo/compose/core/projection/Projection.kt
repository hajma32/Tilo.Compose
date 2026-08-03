package tilo.compose.core.projection

import tilo.compose.core.geometry.Point
import tilo.compose.core.transform.Transformation

/**
 * Coordinate system identity used by map and layer metadata.
 *
 * Screen/world conversion stays in viewport math, but projections can normalize
 * their coordinate units to shared map zoom units via [worldUnitsPerMapUnit].
 */
interface Projection {
    val id: String

    /** CRS authority identifier or PROJ definition consumed by the platform transformation engine. */
    val definition: String
        get() = id

    /**
     * Coordinate units represented by one map world unit used in screen/zoom math.
     * Geographic coordinates use traditional GIS lon/lat degrees and therefore 1.0;
     * projected meter-based systems normalize to
     * meters-per-degree-at-equator so zoom semantics stay consistent.
     */
    val worldUnitsPerMapUnit: Double
        get() = 1.0
}

/**
 * A custom coordinate system connected to a known `reference` projection.
 *
 * The connection travels with the projection, so maps and layers using it do not have to register
 * the transformation separately. Tilo may compose this connection with a platform CRS provider to
 * reach projections other than `reference`.
 *
 * `toReference` converts coordinates from this projection to `reference`; `fromReference` performs
 * the inverse conversion. `id` is also the stable identity of that conversion: use a different id
 * if either conversion function changes.
 */
class ReferencedProjection(
    override val id: String,
    val reference: Projection,
    private val toReference: (Point) -> Point,
    private val fromReference: (Point) -> Point,
    override val worldUnitsPerMapUnit: Double = 1.0,
) : Projection {
    init {
        require(id.isNotBlank()) { "Projection id must not be blank" }
        require(id != reference.id) { "A projection cannot reference itself" }
        require(worldUnitsPerMapUnit.isFinite() && worldUnitsPerMapUnit > 0.0) {
            "worldUnitsPerMapUnit must be finite and positive"
        }
    }

    internal val referenceTransformation: Transformation<Projection, Projection> =
        object : Transformation<Projection, Projection> {
            override val source: Projection = this@ReferencedProjection
            override val target: Projection = reference

            override fun sourceToTarget(point: Point): Point = toReference(point)

            override fun targetToSource(point: Point): Point = fromReference(point)
        }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is ReferencedProjection &&
            id == other.id &&
            reference.id == other.reference.id &&
            reference.definition == other.reference.definition &&
            worldUnitsPerMapUnit == other.worldUnitsPerMapUnit

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + reference.id.hashCode()
        result = 31 * result + reference.definition.hashCode()
        result = 31 * result + worldUnitsPerMapUnit.hashCode()
        return result
    }

    override fun toString(): String = id
}
