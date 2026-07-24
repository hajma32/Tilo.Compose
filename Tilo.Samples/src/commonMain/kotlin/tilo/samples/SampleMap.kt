package tilo.samples

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds

@Composable
internal fun SampleMap(
    sample: Sample,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().clipToBounds()) {
        when (sample) {
            Sample.OpenStreetMap -> OpenStreetMapSample()
            Sample.Geometries -> GeometriesSample()
            Sample.CustomStyles -> CustomStylesSample()
            Sample.StyleLab -> StyleLabSample()
            Sample.Callout -> CalloutSample()
            Sample.NonMercator -> NonMercatorSample()
            Sample.Drawing -> DrawingSample()
            Sample.ExtremeVectorRendering -> ExtremeVectorRenderingSample()
        }
    }
}
