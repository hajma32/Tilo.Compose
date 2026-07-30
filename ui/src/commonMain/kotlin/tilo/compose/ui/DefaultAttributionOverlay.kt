@file:OptIn(tilo.compose.dsl.ExperimentalTiloApi::class)

package tilo.compose.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import tilo.compose.core.layers.Attribution
import tilo.compose.dsl.tiloMapFocusTarget
import tilo.compose.ui.generated.resources.Res
import tilo.compose.ui.generated.resources.open_attribution

/** Displays active provider credits at the bottom end of a map and opens linked credits on tap. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BoxScope.DefaultAttributionOverlay(attributions: List<Attribution>) =
    DefaultAttributionOverlay(attributions, MapUiAccessibility())

/** Displays active provider credits with configurable accessibility text. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BoxScope.DefaultAttributionOverlay(
    attributions: List<Attribution>,
    accessibility: MapUiAccessibility,
) {
    val uriHandler = LocalUriHandler.current
    val shape = RoundedCornerShape(8.dp)
    val containerColor = MaterialTheme.colorScheme.surface
    val textStyle = AttributionTextStyle.copy(color = MaterialTheme.colorScheme.onSurface)
    val linkTextStyle = textStyle.copy(textDecoration = TextDecoration.Underline)
    FlowRow(
        modifier =
            Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.End + WindowInsetsSides.Bottom),
                ).padding(8.dp)
                .shadow(elevation = 4.dp, shape = shape)
                .background(containerColor, shape = shape)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        attributions.forEachIndexed { attributionIndex, attribution ->
            val traversalIndex = ATTRIBUTION_TRAVERSAL_INDEX + attributionIndex
            val linkModifier =
                attribution.url?.let { url ->
                    val clickLabel =
                        accessibility.attributionClickLabel?.invoke(attribution)
                            ?: stringResource(Res.string.open_attribution, attribution.label)
                    Modifier
                        .tiloMapFocusTarget(traversalIndex)
                        .clickable(
                            onClickLabel = clickLabel,
                            role = Role.Button,
                        ) { uriHandler.openUri(url) }
                } ?: Modifier.semantics { this.traversalIndex = traversalIndex }
            BasicText(
                text = attribution.label,
                modifier = linkModifier,
                style = if (attribution.url != null) linkTextStyle else textStyle,
            )
        }
    }
}

private val AttributionTextStyle =
    TextStyle(
        fontSize = 11.sp,
    )
