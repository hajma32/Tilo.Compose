package tilo.compose.core.feature

import kotlin.jvm.JvmInline

/** Marker for platform-neutral point, line, and polygon presentation models. */
sealed interface GeometryStyle

/**
 * Platform-neutral color packed as unsigned alpha, red, green, and blue bytes (`0xAARRGGBB`).
 */
@JvmInline
value class ColorValue(
    val argb: ULong,
) {
    companion object {
        val Transparent = ColorValue(0x00000000u)
        val Black = ColorValue(0xFF111827u)
        val White = ColorValue(0xFFFFFFFFu)
        val Blue = ColorValue(0xFF1E88E5u)
    }
}

/** Shape used at the endpoints of a stroked line. */
enum class LineCap {
    Butt,
    Round,
    Square,
}

/** Shape used where consecutive segments of a stroke meet. */
enum class LineJoin {
    Miter,
    Round,
    Bevel,
}

/** Alternating painted and skipped lengths for a stroke, expressed in DIP. */
data class DashPattern(
    val intervals: List<Double>,
    val phase: Double = 0.0,
)

/** Foreground stroke styling with dimensions in DIP and opacity in `0.0..1.0`. */
data class StrokeStyle(
    val color: ColorValue = ColorValue.Black,
    val width: Double = 1.0,
    val opacity: Double = 1.0,
    val lineCap: LineCap = LineCap.Butt,
    val lineJoin: LineJoin = LineJoin.Miter,
    val dash: DashPattern? = null,
)

/**
 * Outline drawn underneath a line or polygon stroke.
 *
 * `width` is the additional total width around the foreground stroke, not the
 * absolute width of the backing stroke. For example, a 6 DIP stroke with a
 * 2 DIP casing is rendered using an 8 DIP backing stroke, leaving 1 DIP
 * visible on either side.
 */
data class CasingStyle(
    val color: ColorValue = ColorValue.White,
    val width: Double = 2.0,
    val opacity: Double = 1.0,
    val lineCap: LineCap = LineCap.Butt,
    val lineJoin: LineJoin = LineJoin.Miter,
    val dash: DashPattern? = null,
) {
    /** Absolute backing-stroke width for a foreground stroke of [strokeWidth]. */
    fun outerWidth(strokeWidth: Double): Double = strokeWidth + width
}

/** Optional repeating pattern painted over a fill color. */
sealed interface FillPattern {
    data class Hatch(
        val angleDegrees: Double = 45.0,
        val spacing: Double = 8.0,
        val stroke: StrokeStyle = StrokeStyle(width = 1.0),
    ) : FillPattern

    data class Dots(
        val spacing: Double = 8.0,
        val radius: Double = 1.5,
        val color: ColorValue = ColorValue.Black,
    ) : FillPattern
}

/** Interior fill styling for points and polygons. */
data class FillStyle(
    val color: ColorValue = ColorValue.Transparent,
    val opacity: Double = 1.0,
    val pattern: FillPattern? = null,
)

/** Built-in vector shape used when a point does not use an icon. */
enum class PointShape {
    Circle,
    Square,
    Diamond,
    Triangle,
    Cross,
}

/** Platform-neutral point symbol; `size` and stroke dimensions are in DIP. */
data class PointStyle(
    val shape: PointShape = PointShape.Circle,
    val size: Double = 14.0,
    val fill: FillStyle? = FillStyle(color = ColorValue.Blue),
    val stroke: StrokeStyle? = StrokeStyle(color = ColorValue.White, width = 2.5),
    val icon: PointIconStyle? = null,
) : GeometryStyle

/** Platform-neutral line symbol composed from an optional casing and foreground stroke. */
data class LineStyle(
    val casing: CasingStyle? =
        CasingStyle(
            color = ColorValue.White,
            width = 2.0,
            lineCap = LineCap.Round,
            lineJoin = LineJoin.Round,
        ),
    val stroke: StrokeStyle =
        StrokeStyle(
            color = ColorValue.Blue,
            width = 3.0,
            lineCap = LineCap.Round,
            lineJoin = LineJoin.Round,
        ),
) : GeometryStyle

/** Platform-neutral polygon symbol with independent fill, casing, and stroke. */
data class PolygonStyle(
    val fill: FillStyle? = FillStyle(color = ColorValue(0x331E88E5u)),
    val casing: CasingStyle? =
        CasingStyle(
            color = ColorValue.White,
            width = 2.0,
            lineJoin = LineJoin.Round,
        ),
    val stroke: StrokeStyle? =
        StrokeStyle(
            color = ColorValue.Blue,
            width = 2.0,
            lineJoin = LineJoin.Round,
        ),
) : GeometryStyle

/** Portable label font weights supported by every renderer. */
enum class LabelFontWeight {
    Normal,
    Medium,
    SemiBold,
    Bold,
}

/** Portable label font posture supported by every renderer. */
enum class LabelFontStyle {
    Normal,
    Italic,
}

/** Physical alignment of text inside a multi-line label bitmap. */
enum class LabelTextAlign {
    Left,
    Center,
    Right,
}

/** Rounded background painted behind a label, with dimensions in DIP. */
data class LabelBackgroundStyle(
    val color: ColorValue,
    val opacity: Double = 1.0,
    val cornerRadius: Double = 4.0,
    val paddingHorizontal: Double = 5.0,
    val paddingVertical: Double = 2.0,
)

/**
 * Platform-neutral text label styling.
 *
 * All dimensions are DIP. `offsetY` moves the label down for positive values;
 * `haloWidth` and `bitmapPadding` contribute to the generated bitmap bounds.
 */
data class LabelStyle(
    val color: ColorValue = ColorValue.Black,
    val fontSize: Double = 12.0,
    val fontWeight: LabelFontWeight = LabelFontWeight.Bold,
    val fontStyle: LabelFontStyle = LabelFontStyle.Normal,
    val haloColor: ColorValue = ColorValue.White,
    val haloWidth: Double = 3.0,
    val background: LabelBackgroundStyle? = null,
    val bitmapPadding: Double = 2.0,
    val offsetY: Double = 12.0,
    val textAlign: LabelTextAlign = LabelTextAlign.Center,
)

/**
 * Default and selected presentation for every geometry kind in a feature layer.
 *
 * Feature-level styles take precedence. `zoomRules` are applied in declaration
 * order after these base values.
 */
data class FeatureLayerStyle(
    val point: PointStyle? = null,
    val line: LineStyle? = null,
    val polygon: PolygonStyle? = null,
    val label: LabelStyle? = null,
    val selectedPoint: PointStyle? = null,
    val selectedLine: LineStyle? = null,
    val selectedPolygon: PolygonStyle? = null,
    val selectedLabel: LabelStyle? = null,
    val labelsVisible: Boolean = true,
    val zoomRules: List<FeatureLayerStyleZoomRule> = emptyList(),
) {
    /**
     * Resolves this style for [zoom]. Matching rules are applied in declaration
     * order, so a later rule overrides fields supplied by an earlier rule.
     */
    fun resolveAtZoom(zoom: Double): FeatureLayerStyle {
        require(zoom.isFinite()) { "zoom must be finite" }
        var resolved = copy(zoomRules = emptyList())
        zoomRules.forEach { rule ->
            if (rule.matches(zoom)) resolved = rule.applyTo(resolved)
        }
        return resolved
    }
}

/**
 * Partial layer-style replacement active from `minZoom` (inclusive) until
 * `maxZoomExclusive` (exclusive). Null style fields inherit the value resolved
 * by preceding rules or the base [FeatureLayerStyle].
 */
data class FeatureLayerStyleZoomRule(
    val minZoom: Double? = null,
    val maxZoomExclusive: Double? = null,
    val point: PointStyle? = null,
    val line: LineStyle? = null,
    val polygon: PolygonStyle? = null,
    val label: LabelStyle? = null,
    val selectedPoint: PointStyle? = null,
    val selectedLine: LineStyle? = null,
    val selectedPolygon: PolygonStyle? = null,
    val selectedLabel: LabelStyle? = null,
    val labelsVisible: Boolean? = null,
) {
    init {
        require(minZoom == null || minZoom.isFinite()) { "minZoom must be finite" }
        require(maxZoomExclusive == null || maxZoomExclusive.isFinite()) { "maxZoomExclusive must be finite" }
        require(minZoom == null || maxZoomExclusive == null || minZoom < maxZoomExclusive) {
            "minZoom must be lower than maxZoomExclusive"
        }
    }

    fun matches(zoom: Double): Boolean =
        (minZoom == null || zoom >= minZoom) &&
            (maxZoomExclusive == null || zoom < maxZoomExclusive)

    internal fun applyTo(style: FeatureLayerStyle): FeatureLayerStyle =
        style.copy(
            point = point ?: style.point,
            line = line ?: style.line,
            polygon = polygon ?: style.polygon,
            label = label ?: style.label,
            selectedPoint = selectedPoint ?: style.selectedPoint,
            selectedLine = selectedLine ?: style.selectedLine,
            selectedPolygon = selectedPolygon ?: style.selectedPolygon,
            selectedLabel = selectedLabel ?: style.selectedLabel,
            labelsVisible = labelsVisible ?: style.labelsVisible,
            zoomRules = emptyList(),
        )
}
