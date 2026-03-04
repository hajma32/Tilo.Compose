package eu.tilo.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import eu.tilo.compose.map.mapFeatures
import eu.tilo.compose.render.MapRenderer
import tilo.compose.core.feature.BaseStyle
import tilo.compose.core.geometry.Point
import tilo.compose.core.map.MapSettings
import tilo.compose.core.map.MapState

@Composable
@Preview
fun App() {
    val mapState = remember {
        MapState(
            center = Point(0.0, 0.0),
            zoom = 2.0,
            settings = MapSettings(minZoom = 0.0, maxZoom = 20.0)
        )
    }

    val features = remember {
        mapFeatures {
            point(
                key = "pt-1",
                x = 10.0,
                y = 10.0,
                style = BaseStyle(fillColor = 0xFFFF6D00)
            )

            lineString(
                key = "line-1",
                points = listOf(
                    Point(-80.0, -20.0),
                    Point(0.0, 30.0),
                    Point(80.0, -10.0)
                ),
                style = BaseStyle(strokeColor = 0xFF00ACC1, strokeWidth = 3.0)
            )

            polygon(
                key = "poly-1",
                rings = listOf(
                    listOf(
                        Point(-60.0, -40.0),
                        Point(-20.0, -40.0),
                        Point(-20.0, 0.0),
                        Point(-60.0, 0.0),
                        Point(-60.0, -40.0)
                    )
                ),
                style = BaseStyle(
                    strokeColor = 0xFF3949AB,
                    fillColor = 0x663949AB,
                    strokeWidth = 2.0
                )
            )
        }
    }

    MaterialTheme {
        MapRenderer(
            mapState = mapState,
            features = features,
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF6F8FA))
        )
    }
}
