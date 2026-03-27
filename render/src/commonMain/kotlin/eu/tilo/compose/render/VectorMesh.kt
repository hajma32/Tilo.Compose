package eu.tilo.compose.render

import tilo.compose.core.feature.BaseStyle
import tilo.compose.core.geometry.Point

public enum class VectorMeshPrimitive {
    POLYGON_FILL,
    LINE
}

public data class VectorMeshStyle(
    val strokeColor: Long?,
    val fillColor: Long?,
    val strokeWidth: Double?
) {
    public companion object {
        public fun from(style: BaseStyle): VectorMeshStyle =
            VectorMeshStyle(
                strokeColor = style.strokeColor,
                fillColor = style.fillColor,
                strokeWidth = style.strokeWidth
            )
    }
}

public data class VectorMeshBatch(
    val primitive: VectorMeshPrimitive,
    val style: VectorMeshStyle,
    val vertices: List<Point>,
    val indices: List<Int>
)

public data class PreparedVectorTile(
    val tileKey: String,
    val signature: Int,
    val meshBatches: List<VectorMeshBatch>,
    val fallbackPolygons: List<RenderPolygon>
)

public data class PreparedVectorFrame(
    val meshBatches: List<VectorMeshBatch>,
    val fallbackPolygons: List<RenderPolygon>,
    val points: List<RenderPoint>,
    val labels: List<RenderLabel>
)
