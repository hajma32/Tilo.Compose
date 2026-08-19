@file:OptIn(ExperimentalTiloApi::class)

package tilo.samples

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tilo.compose.core.feature.Feature
import tilo.compose.core.feature.LineCap
import tilo.compose.core.feature.LineJoin
import tilo.compose.core.feature.PointShape
import tilo.compose.core.geometry.Point
import tilo.compose.core.selection.FeatureSelectionRef
import tilo.compose.dsl.ExperimentalTiloApi
import tilo.compose.dsl.TiloMap
import tilo.compose.dsl.featureLayerStyle
import tilo.compose.dsl.features
import tilo.compose.dsl.mediumLabelStyle
import tilo.compose.dsl.wgs84
import tilo.compose.ui.defaultAttributionContent
import tilo.compose.ui.defaultCameraControlsContent
import tilo.compose.ui.defaultScaleBarContent

private const val GEOMETRY_LAYER_ID = "sample-geometries"

@Composable
internal fun BoxScope.GeometriesSample() {
    val camera = rememberWebMercatorCamera(zoom = 12.2)
    val features = remember { geometryFeatures() }
    var selectedFeatures by remember { mutableStateOf<Set<FeatureSelectionRef>>(emptySet()) }

    TiloMap(
        cameraState = camera,
        modifier = Modifier.fillMaxSize(),
        onFeatureSelect = { hits -> selectedFeatures = hits.map { it.ref }.toSet() },
        selectedFeatures = selectedFeatures,
        attributionContent = defaultAttributionContent(),
        scaleBarContent = defaultScaleBarContent(),
        cameraControlsContent = defaultCameraControlsContent(),
        layers = {
            openStreetMapLayer()
            featureLayer(GEOMETRY_LAYER_ID, features) {
                zIndex = 2
                projection = wgs84()
                style = geometryLayerStyle()
            }
        },
    )

    SampleInfoCard(
        sample = Sample.Geometries,
        body = "Tap a point, line or polygon. One feature layer handles geometry, labels and selected styles.",
        code = "featureLayer(\"places\", features)",
    )
    if (selectedFeatures.isNotEmpty()) {
        val suffix = if (selectedFeatures.size == 1) "" else "s"
        MapPill("${selectedFeatures.size} feature$suffix selected")
    }
}

private fun geometryLayerStyle() =
    featureLayerStyle {
        point {
            shape = PointShape.Circle
            size = 18.dp
            fill(0xFFF2663B)
            stroke(0xFFFFFFFF, width = 3.5.dp)
        }
        line {
            casing(0xFFFFFFFF, width = 3.5.dp) {
                lineCap = LineCap.Round
                lineJoin = LineJoin.Round
            }
            stroke(0xFF253E32, width = 4.5.dp) {
                lineCap = LineCap.Round
                lineJoin = LineJoin.Round
            }
        }
        polygon {
            fill(0x55BFED6F)
            stroke(0xFF253E32, width = 3.dp) { lineJoin = LineJoin.Round }
        }
        label(mediumLabelStyle())
        selectedPoint {
            shape = PointShape.Circle
            size = 25.dp
            fill(0xFFBFED6F)
            stroke(0xFF17201C, width = 4.dp)
        }
        selectedLine {
            casing(0xFFFFFFFF, width = 4.dp) { lineCap = LineCap.Round }
            stroke(0xFFF2663B, width = 7.dp) { lineCap = LineCap.Round }
        }
        selectedPolygon {
            fill(0x77F2663B)
            stroke(0xFFF2663B, width = 5.dp)
        }
        selectedLabel(0xFF17201C)
    }

private fun geometryFeatures(): List<Feature> =
    features {
        point("castle", 14.4005, 50.0909) { label = "Prague Castle" }
        point("station", 14.4361, 50.0831) { label = "Main station" }
        lineString(
            key = "river",
            points =
                listOf(
                    Point(14.392, 50.105),
                    Point(14.405, 50.094),
                    Point(14.413, 50.084),
                    Point(14.414, 50.074),
                    Point(14.405, 50.061),
                ),
        ) { label = "Vltava" }
        polygon(
            key = "park",
            rings =
                listOf(
                    listOf(
                        Point(14.408, 50.096),
                        Point(14.425, 50.097),
                        Point(14.430, 50.089),
                        Point(14.420, 50.084),
                        Point(14.407, 50.088),
                        Point(14.408, 50.096),
                    ),
                ),
        ) { label = "Letná" }
    }
