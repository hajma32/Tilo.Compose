@file:OptIn(ExperimentalTiloApi::class)

package tilo.samples

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tilo.compose.core.feature.Feature
import tilo.compose.core.feature.LabelTextAlign
import tilo.compose.core.feature.LineCap
import tilo.compose.core.feature.LineJoin
import tilo.compose.core.geometry.Point
import tilo.compose.dsl.ExperimentalTiloApi
import tilo.compose.dsl.TiloMap
import tilo.compose.dsl.featureLayerStyle
import tilo.compose.dsl.features
import tilo.compose.dsl.wgs84
import tilo.compose.ui.DefaultZoomControls
import tilo.compose.ui.defaultAttributionContent
import tilo.compose.ui.defaultScaleBarContent

/** Visual regression playground for zoom styles, multi-line alignment, and casing width. */
@Composable
internal fun BoxScope.StyleLabSample() {
    val camera = rememberWebMercatorCamera(center = Point(14.426, 50.075), zoom = 13.0)
    val route = remember { styleLabRoute() }
    val alignedLabels = remember { styleLabLabels() }

    TiloMap(
        cameraState = camera,
        modifier = Modifier.fillMaxSize(),
        attributionContent = defaultAttributionContent(),
        scaleBarContent = defaultScaleBarContent(),
        cameraControlsContent = { DefaultZoomControls(it) },
        layers = {
            openStreetMapLayer()
            layerGroup(
                id = "style-lab-overlays",
                zIndex = 3,
                minZoom = 11.0,
            ) {
                featureLayer("zoom-style", route) {
                    zIndex = 0
                    projection = wgs84()
                    style =
                        featureLayerStyle {
                            line {
                                casing(0xFFFFFFFF, width = 2.dp) {
                                    lineCap = LineCap.Round
                                    lineJoin = LineJoin.Round
                                }
                                stroke(0xFFF2663B, width = 6.dp) {
                                    lineCap = LineCap.Round
                                    lineJoin = LineJoin.Round
                                }
                            }
                            label {
                                color(0xFF17201C)
                                background(0xFFFFFFFF, opacity = 0.92)
                            }
                            zoom(minZoom = 14.0) {
                                line {
                                    casing(0xFFFFFFFF, width = 2.dp) {
                                        lineCap = LineCap.Round
                                        lineJoin = LineJoin.Round
                                    }
                                    stroke(0xFF2F6FEB, width = 20.dp) {
                                        lineCap = LineCap.Round
                                        lineJoin = LineJoin.Round
                                    }
                                }
                                hideLabels()
                            }
                        }
                }
                LabelTextAlign.entries.forEachIndexed { index, alignment ->
                    featureLayer("alignment-${alignment.name.lowercase()}", listOf(alignedLabels[index])) {
                        zIndex = 10 + index
                        projection = wgs84()
                        style = alignmentStyle(alignment)
                    }
                }
            }
        },
    )

    SampleInfoCard(
        sample = Sample.StyleLab,
        body =
            "Zoom from 13 to 14: the route changes from 6 dp orange to 20 dp blue and its label disappears. " +
                "The grouped overlays also exercise left, center and right multi-line alignment.",
        code = "layerGroup(\"style-lab-overlays\") { zoom(minZoom = 14.0) { … } }",
    )
    MapPill("Casing width is +2 dp · zoom across 14")
}

private fun alignmentStyle(alignment: LabelTextAlign) =
    featureLayerStyle {
        point {
            fill(0x00000000)
            noStroke()
        }
        label {
            textAlign = alignment
            color(0xFFFFFFFF)
            noHalo()
            offsetY(0.dp)
            background(
                color = 0xFF17201C,
                cornerRadius = 3.dp,
                paddingHorizontal = 7.dp,
                paddingVertical = 5.dp,
            )
        }
    }

private fun styleLabRoute(): List<Feature> =
    features {
        line(
            key = "zoom-route",
            points =
                listOf(
                    Point(14.392, 50.065),
                    Point(14.408, 50.071),
                    Point(14.426, 50.068),
                    Point(14.445, 50.073),
                    Point(14.460, 50.068),
                ),
        ) {
            label = "6 dp + 2 dp casing\nlabel hides at zoom 14"
        }
    }

private fun styleLabLabels(): List<Feature> =
    features {
        point("left", Point(14.400, 50.087)) { label = "LEFT\nlonger second line" }
        point("center", Point(14.426, 50.091)) { label = "CENTER\nlonger second line" }
        point("right", Point(14.452, 50.087)) { label = "RIGHT\nlonger second line" }
    }
