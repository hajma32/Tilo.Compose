package eu.tilo.compose.transit

import androidx.compose.ui.unit.dp
import tilo.compose.core.feature.Data
import tilo.compose.core.feature.Feature
import tilo.compose.core.feature.LabelStyle
import tilo.compose.core.feature.PointShape
import tilo.compose.core.feature.PointStyle
import tilo.compose.dsl.pointStyle
import tilo.compose.dsl.smallLabelStyle

internal fun List<TransitVehicle>.toTransitFeatures(): List<Feature> =
    map { vehicle ->
        val presentation = TransitPresentations.getValue(vehicle.actualType)
        Feature(
            key = "transit-${vehicle.id}",
            geometry = vehicle.position,
            style = presentation.pointStyle,
            selectedStyle = SelectedTransitPointStyle,
            label = vehicle.lineName,
            labelPriority = presentation.labelPriority,
            labelStyle = presentation.labelStyle,
            selectedLabelStyle = SelectedTransitLabelStyle,
            data = Data(vehicle),
        )
    }

private data class TransitPresentation(
    val pointStyle: PointStyle,
    val labelStyle: LabelStyle,
    val labelPriority: Int,
)

private val TransitPresentations =
    TransitType.entries.associateWith { type ->
        val color =
            when (type) {
                TransitType.Service -> 0xFF6B7280
                TransitType.Tram -> 0xFFE53935
                TransitType.Trolleybus -> 0xFF2563EB
                TransitType.Bus -> 0xFF16A34A
                TransitType.Boat -> 0xFF0891B2
                TransitType.Train -> 0xFF7C3AED
                TransitType.Unknown -> 0xFF475569
            }
        val shape =
            when (type) {
                TransitType.Tram -> PointShape.Diamond
                TransitType.Trolleybus -> PointShape.Triangle
                TransitType.Bus -> PointShape.Square
                TransitType.Boat -> PointShape.Diamond
                TransitType.Train -> PointShape.Circle
                TransitType.Service, TransitType.Unknown -> PointShape.Circle
            }
        val priority =
            when (type) {
                TransitType.Train -> 130
                TransitType.Tram -> 120
                TransitType.Trolleybus -> 110
                TransitType.Bus -> 100
                TransitType.Boat -> 90
                TransitType.Service, TransitType.Unknown -> 50
            }
        TransitPresentation(
            pointStyle =
                pointStyle {
                    this.shape = shape
                    size = 19.dp
                    fill(color)
                    stroke(0xFFFFFFFF, width = 3.dp)
                },
            labelStyle =
                smallLabelStyle {
                    color(0xFFFFFFFF)
                    noHalo()
                    background(
                        color = color,
                        cornerRadius = 4.dp,
                        paddingHorizontal = 5.dp,
                        paddingVertical = 1.5.dp,
                    )
                    offsetY(15.dp)
                },
            labelPriority = priority,
        )
    }

private val SelectedTransitPointStyle =
    pointStyle {
        shape = PointShape.Diamond
        size = 27.dp
        fill(0xFFFFD54F)
        stroke(0xFF111827, width = 4.dp)
    }

private val SelectedTransitLabelStyle =
    smallLabelStyle {
        color(0xFF111827)
        noHalo()
        background(
            color = 0xFFFFD54F,
            cornerRadius = 4.dp,
            paddingHorizontal = 6.dp,
            paddingVertical = 2.dp,
        )
        offsetY(18.dp)
    }
