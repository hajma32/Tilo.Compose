package tilo.compose.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tilo.compose.core.layers.Attribution

@Composable
fun BoxScope.DefaultAttributionOverlay(attributions: List<Attribution>) {
    val uriHandler = LocalUriHandler.current
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(8.dp)
            .shadow(elevation = 4.dp, shape = shape)
            .background(Color.White, shape = shape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        attributions.forEachIndexed { index, attribution ->
            if (index > 0) {
                BasicText(
                    text = " | ",
                    style = AttributionTextStyle,
                )
            }
            BasicText(
                text = attribution.label,
                modifier = attribution.url?.let { url ->
                    Modifier.clickable { uriHandler.openUri(url) }
                } ?: Modifier,
                style = if (attribution.url != null) AttributionLinkTextStyle else AttributionTextStyle,
            )
        }
    }
}

private val AttributionTextStyle = TextStyle(
    color = Color(0xFF111827),
    fontSize = 11.sp,
)

private val AttributionLinkTextStyle = AttributionTextStyle.copy(
    color = Color(0xFF111827),
    textDecoration = TextDecoration.Underline,
)
