@file:OptIn(ExperimentalTiloApi::class)

package tilo.samples

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import tilo.compose.core.feature.Feature
import tilo.compose.core.feature.LineCap
import tilo.compose.core.feature.LineJoin
import tilo.compose.core.geometry.Point
import tilo.compose.dsl.ExperimentalTiloApi
import tilo.compose.dsl.TiloMap
import tilo.compose.dsl.features
import tilo.compose.dsl.lineStyle
import tilo.compose.dsl.mediumLabelStyle
import tilo.compose.dsl.pointStyle
import tilo.compose.dsl.polygonStyle
import tilo.compose.dsl.smallLabelStyle
import tilo.compose.dsl.wgs84
import tilo.compose.ui.DefaultZoomControls
import tilo.compose.ui.defaultAttributionContent
import tilo.compose.ui.defaultScaleBarContent
import tilocompose.tilo_samples.generated.resources.Res
import tilocompose.tilo_samples.generated.resources.sample_stop_bitmap
import tilocompose.tilo_samples.generated.resources.sample_stop_vector

@Composable
internal fun BoxScope.CustomStylesSample() {
    val camera = rememberWebMercatorCamera(center = Point(14.426, 50.079), zoom = 13.2)
    val features = remember { customStyleFeatures() }
    val vectorStopIcon = painterResource(Res.drawable.sample_stop_vector)
    val bitmapStopIcon = painterResource(Res.drawable.sample_stop_bitmap)

    TiloMap(
        cameraState = camera,
        modifier = Modifier.fillMaxSize(),
        attributionContent = defaultAttributionContent(),
        scaleBarContent = defaultScaleBarContent(),
        cameraControlsContent = { DefaultZoomControls(it) },
        layers = {
            openStreetMapLayer(opacity = 0.65)
            layerGroup(id = "styled-content", zIndex = 3, opacity = 0.8) {
                featureLayer("custom-styles", features) {
                    opacity = 0.75
                    projection = wgs84()
                    pointIcon("vector-stop", vectorStopIcon)
                    pointIcon("bitmap-stop", bitmapStopIcon)
                }
            }
        },
    )

    SampleInfoCard(
        sample = Sample.CustomStyles,
        body =
            "Styles can live on a layer or on an individual feature. " +
                "The faded raster and grouped vectors demonstrate cascading layer opacity.",
        code = "layerGroup(opacity = 0.8) { featureLayer(...) { opacity = 0.75 } }",
    )
}

private fun customStyleFeatures(): List<Feature> =
    features {
        polygon(
            key = "styled-park",
            rings =
                listOf(
                    listOf(
                        Point(14.405, 50.096),
                        Point(14.423, 50.098),
                        Point(14.431, 50.091),
                        Point(14.422, 50.085),
                        Point(14.406, 50.088),
                        Point(14.405, 50.096),
                    ),
                ),
        ) {
            label = "Letná Park"
            style =
                polygonStyle {
                    fill(0x77BFED6F)
                    casing(0xFFFFFFFF, width = 4.dp) { lineJoin = LineJoin.Round }
                    stroke(0xFF253E32, width = 3.dp) { lineJoin = LineJoin.Round }
                }
            labelStyle = mediumLabelStyle { color(0xFF17201C) }
        }

        line(
            key = "styled-route",
            points =
                listOf(
                    Point(14.397, 50.087),
                    Point(14.407, 50.085),
                    Point(14.418, 50.083),
                    Point(14.429, 50.080),
                    Point(14.442, 50.082),
                    Point(14.454, 50.088),
                ),
        ) {
            label = "Tilo route"
            style =
                lineStyle {
                    casing(0xFFFFFFFF, width = 4.dp) {
                        lineCap = LineCap.Round
                        lineJoin = LineJoin.Round
                    }
                    stroke(0xFFF2663B, width = 6.dp) {
                        lineCap = LineCap.Round
                        lineJoin = LineJoin.Round
                    }
                }
            labelStyle =
                smallLabelStyle {
                    color(0xFFFFFFFF)
                    noHalo()
                    background(0xFF17201C, cornerRadius = 3.dp, paddingHorizontal = 6.dp, paddingVertical = 3.dp)
                }
        }

        listOf(
            Triple("stop-a", "A", Point(14.407, 50.085)),
            Triple("stop-b", "B", Point(14.429, 50.080)),
            Triple("stop-c", "C", Point(14.454, 50.088)),
        ).forEachIndexed { index, (key, labelText, location) ->
            point(key, location) {
                label = labelText
                style =
                    pointStyle {
                        noFill()
                        noStroke()
                        icon(
                            id = if (index == 1) "bitmap-stop" else "vector-stop",
                            size = 32.dp,
                            tint = if (index == 2) 0xFF2F6FEB else null,
                        )
                    }
                labelStyle =
                    smallLabelStyle {
                        color(0xFFFFFFFF)
                        noHalo()
                        offsetY(0.dp)
                    }
            }
        }
    }
