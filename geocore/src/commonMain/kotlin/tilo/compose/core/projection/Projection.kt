package tilo.compose.core.projection

import tilo.compose.core.geometry.Point
import tilo.compose.core.transform.Transformation

/**
 * Coordinate system identity used by map and layer metadata.
 *
 * Screen/world conversion stays in viewport math, but projections can normalize
 * their raw CRS units to shared map zoom units via [worldUnitsPerMapUnit].
 */
interface Projection {
    val id: String

    /** CRS authority identifier or PROJ definition consumed by the platform transformation engine. */
    val definition: String
        get() = id

    /**
     * Raw CRS units represented by one map world unit used in screen/zoom math.
     * Geographic lon/lat uses 1.0; projected meter-based systems normalize to
     * meters-per-degree-at-equator so zoom semantics stay consistent.
     */
    val worldUnitsPerMapUnit: Double
        get() = 1.0
}

/** A projection described directly by an authority identifier or a PROJ string. */
class DefinedProjection(
    override val definition: String,
    override val id: String = definition,
    override val worldUnitsPerMapUnit: Double = 1.0,
) : Projection {
    init {
        require(definition.isNotBlank()) { "Projection definition must not be blank" }
        require(id.isNotBlank()) { "Projection id must not be blank" }
        require(worldUnitsPerMapUnit.isFinite() && worldUnitsPerMapUnit > 0.0) {
            "worldUnitsPerMapUnit must be finite and positive"
        }
    }

    override fun toString(): String = id
}

/**
 * A custom coordinate system connected to a known [reference] projection.
 *
 * The connection travels with the projection, so maps and layers using it do not have to register
 * the transformation separately. Tilo may compose this connection with a platform CRS provider to
 * reach projections other than [reference].
 *
 * [toReference] converts coordinates from this projection to [reference]; [fromReference] performs
 * the inverse conversion.
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

    override fun toString(): String = id
}
