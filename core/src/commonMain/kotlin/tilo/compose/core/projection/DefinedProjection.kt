package tilo.compose.core.projection

import tilo.compose.core.transform.proj4WorldUnitsPerMapUnit

/**
 * A projection described directly by an authority identifier or a PROJ string.
 * Geographic coordinates use traditional GIS longitude/latitude degrees.
 */
class DefinedProjection(
    override val definition: String,
    override val id: String = definition,
) : Projection {
    override val worldUnitsPerMapUnit: Double =
        proj4WorldUnitsPerMapUnit(
            definition.also {
                require(it.isNotBlank()) { "Projection definition must not be blank" }
            },
        )

    init {
        require(id.isNotBlank()) { "Projection id must not be blank" }
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is DefinedProjection &&
            definition == other.definition &&
            id == other.id

    override fun hashCode(): Int = 31 * definition.hashCode() + id.hashCode()

    override fun toString(): String = id
}
