@file:OptIn(ExperimentalTiloApi::class)

package tilo.samples

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tilo.compose.core.feature.Data
import tilo.compose.core.feature.Feature
import tilo.compose.core.feature.PointShape
import tilo.compose.core.geometry.Point
import tilo.compose.core.selection.FeatureSelectionRef
import tilo.compose.dsl.ExperimentalTiloApi
import tilo.compose.dsl.TiloMap
import tilo.compose.dsl.featureLayerStyle
import tilo.compose.dsl.features
import tilo.compose.dsl.smallLabelStyle
import tilo.compose.dsl.wgs84
import tilo.compose.ui.defaultAttributionContent
import tilo.compose.ui.defaultCameraControlsContent
import tilo.compose.ui.defaultScaleBarContent

private const val CALLOUT_LAYER_ID = "sample-callouts"

private data class Place(
    val title: String,
    val category: String,
    val description: String,
)

@Composable
internal fun BoxScope.CalloutSample() {
    val camera = rememberWebMercatorCamera(center = Point(14.42, 50.083), zoom = 13.0)
    val features = remember { calloutFeatures() }
    val initiallySelected = features.first()
    var selectedPlace by remember { mutableStateOf(initiallySelected.place) }
    var selectedFeatures by remember {
        mutableStateOf(setOf(FeatureSelectionRef(CALLOUT_LAYER_ID, initiallySelected.key)))
    }

    TiloMap(
        cameraState = camera,
        modifier = Modifier.fillMaxSize(),
        onFeatureSelect = { hits ->
            selectedFeatures = hits.map { it.ref }.toSet()
            selectedPlace = hits.firstNotNullOfOrNull { it.feature.place }
        },
        selectedFeatures = selectedFeatures,
        attributionContent = defaultAttributionContent(),
        scaleBarContent = defaultScaleBarContent(),
        cameraControlsContent = defaultCameraControlsContent(),
        layers = {
            openStreetMapLayer()
            featureLayer(CALLOUT_LAYER_ID, features) {
                zIndex = 4
                projection = wgs84()
                style = calloutLayerStyle()
            }
        },
    )

    SampleInfoCard(
        sample = Sample.Callout,
        body = "Tilo returns feature hits; the callout remains ordinary Compose UI owned by your app.",
        code = "onFeatureSelect = { hits -> … }",
    )
    selectedPlace?.let { place ->
        CalloutCard(
            place = place,
            onClose = {
                selectedPlace = null
                selectedFeatures = emptySet()
            },
        )
    }
}

private fun calloutLayerStyle() =
    featureLayerStyle {
        point {
            shape = PointShape.Circle
            size = 18.dp
            fill(0xFFF2663B)
            stroke(0xFFFFFFFF, width = 3.5.dp)
        }
        label(smallLabelStyle())
        selectedPoint {
            shape = PointShape.Circle
            size = 27.dp
            fill(0xFFBFED6F)
            stroke(0xFF17201C, width = 4.dp)
        }
        selectedLabel(0xFF17201C)
    }

private fun calloutFeatures(): List<Feature> =
    features {
        point("rudolfinum", 14.4152, 50.0899) {
            label = "Rudolfinum"
            data =
                Data(
                    Place(
                        title = "Rudolfinum",
                        category = "Culture",
                        description =
                            "A concert hall and gallery on the Vltava — selected with Tilo, " +
                                "rendered with Compose.",
                    ),
                )
        }
        point("kampa", 14.4071, 50.0840) {
            label = "Kampa"
            data =
                Data(
                    Place(
                        title = "Kampa",
                        category = "Park",
                        description =
                            "A quiet island park below Charles Bridge. " +
                                "Tap another marker to replace this callout.",
                    ),
                )
        }
        point("powder-tower", 14.4278, 50.0873) {
            label = "Powder Tower"
            data =
                Data(
                    Place(
                        title = "Powder Tower",
                        category = "Landmark",
                        description =
                            "The map reports a stable feature reference; " +
                                "the application decides what UI appears next.",
                    ),
                )
        }
    }

private val Feature.place: Place?
    get() = data?.payload as? Place

@Composable
private fun BoxScope.CalloutCard(
    place: Place,
    onClose: () -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, end = 76.dp, bottom = 42.dp)
                .widthIn(max = 380.dp),
        color = Ink.copy(alpha = .97f),
        contentColor = Color.White,
        border = BorderStroke(1.dp, Color(0xFF526159)),
        shape = RoundedCornerShape(3.dp),
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(start = 17.dp, top = 14.dp, end = 10.dp, bottom = 15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = place.category.uppercase(),
                        color = Green,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        letterSpacing = .8.sp,
                    )
                    Text(
                        text = place.title,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                }
                Text(
                    text = "×",
                    modifier = Modifier.clickable(onClick = onClose).padding(10.dp),
                    color = Color(0xFFA3AEA8),
                    fontSize = 22.sp,
                )
            }
            Text(
                text = place.description,
                color = Color(0xFFC2CBC6),
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
        }
    }
}
