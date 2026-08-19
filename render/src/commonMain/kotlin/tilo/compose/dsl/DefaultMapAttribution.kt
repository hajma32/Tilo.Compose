@file:OptIn(ExperimentalTiloApi::class)

package tilo.compose.dsl

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
import tilo.compose.render.generated.resources.Res
import tilo.compose.render.generated.resources.open_attribution

/** Minimal built-in credits used when a map does not provide a custom attribution slot. */
@Composable
internal fun BoxScope.DefaultMapAttribution(
    attributions: List<Attribution>,
    clickLabel: ((Attribution) -> String)?,
) {
    val uriHandler = LocalUriHandler.current
    val shape = RoundedCornerShape(6.dp)
    val textStyle = DefaultAttributionTextStyle.copy(color = MaterialTheme.colorScheme.onSurface)
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(
        modifier =
            Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.End + WindowInsetsSides.Bottom),
                ).padding(8.dp)
                .shadow(elevation = 2.dp, shape = shape)
                .background(MaterialTheme.colorScheme.surface, shape)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        attributions.forEachIndexed { index, attribution ->
            val url = attribution.url
            val traversalIndex = DEFAULT_ATTRIBUTION_TRAVERSAL_INDEX + index
            BasicText(
                text = attribution.label,
                modifier =
                    if (url == null) {
                        Modifier.semantics { this.traversalIndex = traversalIndex }
                    } else {
                        val resolvedClickLabel =
                            clickLabel?.invoke(attribution)
                                ?: stringResource(Res.string.open_attribution, attribution.label)
                        Modifier
                            .tiloMapFocusTarget(traversalIndex)
                            .clickable(
                                onClickLabel = resolvedClickLabel,
                                role = Role.Button,
                            ) { uriHandler.openUri(url) }
                    },
                style =
                    if (url == null) {
                        textStyle
                    } else {
                        textStyle.copy(textDecoration = TextDecoration.Underline)
                    },
            )
        }
    }
}

private val DefaultAttributionTextStyle = TextStyle(fontSize = 10.sp)
private const val DEFAULT_ATTRIBUTION_TRAVERSAL_INDEX = 4.0f
