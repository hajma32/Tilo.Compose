package tilo.compose.dsl

import androidx.compose.ui.graphics.painter.Painter
import tilo.compose.core.layers.vector.FeatureLayer
import tilo.compose.core.layers.vector.VectorLayer
import tilo.compose.render.PointIconPainterLayer

internal class IconFeatureLayer(
    delegate: FeatureLayer,
    override val pointIconPainters: Map<String, Painter>,
) : VectorLayer by delegate,
    PointIconPainterLayer
