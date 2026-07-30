package tilo.compose.dsl

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import org.jetbrains.compose.resources.stringResource
import tilo.compose.render.generated.resources.Res
import tilo.compose.render.generated.resources.interactive_map
import tilo.compose.render.generated.resources.map_camera_state
import kotlin.math.roundToInt

private const val MAP_TRAVERSAL_INDEX = 0.0f

private data class RegisteredFocusTarget(
    val token: Any,
    val traversalIndex: Float,
    val registrationOrder: Long,
    val requester: FocusRequester,
)

internal class MapFocusTraversal {
    private val targets = mutableListOf<RegisteredFocusTarget>()
    private var nextRegistrationOrder = 0L

    fun register(
        token: Any,
        traversalIndex: Float,
        requester: FocusRequester,
    ) {
        targets +=
            RegisteredFocusTarget(
                token = token,
                traversalIndex = traversalIndex,
                registrationOrder = nextRegistrationOrder++,
                requester = requester,
            )
    }

    fun unregister(token: Any) {
        targets.removeAll { it.token === token }
    }

    fun moveFrom(
        token: Any,
        forward: Boolean,
    ): Boolean = candidatesFrom(token, forward).any { it.requester.requestFocus() }

    fun neighborRequester(
        token: Any,
        forward: Boolean,
    ): FocusRequester? = candidatesFrom(token, forward).firstOrNull()?.requester

    private fun candidatesFrom(
        token: Any,
        forward: Boolean,
    ): List<RegisteredFocusTarget> {
        val ordered =
            targets.sortedWith(
                compareBy(RegisteredFocusTarget::traversalIndex, RegisteredFocusTarget::registrationOrder),
            )
        val currentIndex = ordered.indexOfFirst { it.token === token }
        if (currentIndex < 0) return emptyList()
        val candidates =
            if (forward) {
                ordered.subList(currentIndex + 1, ordered.size)
            } else {
                ordered.subList(0, currentIndex).asReversed()
            }
        return candidates
    }
}

internal val LocalTiloMapFocusTraversal = staticCompositionLocalOf<MapFocusTraversal?> { null }

/**
 * Registers a focusable element in the keyboard and accessibility traversal of the surrounding
 * [TiloMap].
 *
 * The map itself uses index `0`. Default controls use positive indices; applications can place
 * custom controls anywhere in that order. Outside a [TiloMap], this modifier has no effect.
 * Place it before the modifier that creates the focus target, such as `clickable` or `focusable`.
 */
@Composable
@ExperimentalTiloApi
fun Modifier.tiloMapFocusTarget(traversalIndex: Float): Modifier {
    require(traversalIndex.isFinite()) { "traversalIndex must be finite" }
    val traversal = LocalTiloMapFocusTraversal.current ?: return this
    val focusManager = LocalFocusManager.current
    val token = remember(traversal) { Any() }
    val requester = remember(traversal) { FocusRequester() }
    DisposableEffect(traversal, token, traversalIndex, requester) {
        traversal.register(token, traversalIndex, requester)
        onDispose { traversal.unregister(token) }
    }
    return onKeyEvent { event ->
        if (event.isPlainTabKeyDown()) {
            traversal.moveFrom(token, forward = !event.isShiftPressed) ||
                focusManager.moveFocus(
                    if (event.isShiftPressed) FocusDirection.Previous else FocusDirection.Next,
                )
        } else {
            false
        }
    }.focusRequester(requester)
        .focusProperties {
            next = traversal.neighborRequester(token, forward = true) ?: FocusRequester.Default
            previous = traversal.neighborRequester(token, forward = false) ?: FocusRequester.Default
        }.semantics { this.traversalIndex = traversalIndex }
}

/**
 * Accessibility and hardware-keyboard behavior for a [TiloMap] surface.
 *
 * Null descriptions use resource-backed defaults bundled with the render artifact. Applications
 * can replace either description when they need domain-specific wording.
 */
@ExperimentalTiloApi
data class MapAccessibilityOptions(
    val contentDescription: String? = null,
    val stateDescription: ((MapCameraState) -> String)? = null,
    val keyboardNavigationEnabled: Boolean = true,
    val keyboardPanStepPx: Double = 64.0,
    val keyboardZoomStep: Double = 1.0,
) {
    init {
        require(contentDescription == null || contentDescription.isNotBlank()) {
            "contentDescription must be null or non-blank"
        }
        require(keyboardPanStepPx.isFinite() && keyboardPanStepPx > 0.0) {
            "keyboardPanStepPx must be finite and positive"
        }
        require(keyboardZoomStep.isFinite() && keyboardZoomStep > 0.0) {
            "keyboardZoomStep must be finite and positive"
        }
    }
}

@Composable
@ExperimentalTiloApi
internal fun Modifier.mapAccessibility(
    cameraState: MapCameraState,
    options: MapAccessibilityOptions,
): Modifier {
    val resolvedContentDescription = options.contentDescription ?: stringResource(Res.string.interactive_map)
    val resolvedStateDescription =
        if (options.stateDescription != null) {
            options.stateDescription.invoke(cameraState)
        } else {
            val localeIdentifier = currentAccessibilityLocaleIdentifier()
            val zoom = cameraState.zoom
            val roundedBearing = cameraState.bearing.roundToInt()
            val formattedZoom =
                remember(zoom, localeIdentifier) {
                    formatAccessibilityNumber(
                        zoom,
                        fractionDigits = 1,
                        localeIdentifier = localeIdentifier,
                    )
                }
            val formattedBearing =
                remember(roundedBearing, localeIdentifier) {
                    formatAccessibilityNumber(
                        roundedBearing.toDouble(),
                        fractionDigits = 0,
                        localeIdentifier = localeIdentifier,
                    )
                }
            stringResource(
                Res.string.map_camera_state,
                formattedZoom,
                formattedBearing,
            )
        }

    val accessibilityModifier =
        semantics {
            contentDescription = resolvedContentDescription
            stateDescription = resolvedStateDescription
        }.onPreviewKeyEvent { event ->
            handleMapKeyboardEvent(event, cameraState, options)
        }
    return if (options.keyboardNavigationEnabled) {
        accessibilityModifier
            .tiloMapFocusTarget(MAP_TRAVERSAL_INDEX)
            .focusable()
    } else {
        accessibilityModifier
    }
}

@ExperimentalTiloApi
private fun handleMapKeyboardEvent(
    event: KeyEvent,
    cameraState: MapCameraState,
    options: MapAccessibilityOptions,
): Boolean {
    if (!options.keyboardNavigationEnabled || event.type != KeyEventType.KeyDown) {
        return false
    }
    if (event.isAltPressed || event.isCtrlPressed || event.isMetaPressed) {
        return false
    }

    return when (event.key) {
        Key.DirectionLeft -> handled { cameraState.panBy(dx = -options.keyboardPanStepPx, dy = 0.0) }
        Key.DirectionRight -> handled { cameraState.panBy(dx = options.keyboardPanStepPx, dy = 0.0) }
        Key.DirectionUp -> handled { cameraState.panBy(dx = 0.0, dy = -options.keyboardPanStepPx) }
        Key.DirectionDown -> handled { cameraState.panBy(dx = 0.0, dy = options.keyboardPanStepPx) }
        Key.Plus, Key.NumPadAdd -> handled { cameraState.zoomBy(options.keyboardZoomStep) }
        Key.Equals ->
            if (event.isShiftPressed) {
                handled { cameraState.zoomBy(options.keyboardZoomStep) }
            } else {
                false
            }
        Key.Minus, Key.NumPadSubtract -> handled { cameraState.zoomBy(-options.keyboardZoomStep) }
        Key.Home -> handled { cameraState.setBearing(0.0) }
        else -> false
    }
}

private inline fun handled(action: () -> Unit): Boolean {
    action()
    return true
}

private fun KeyEvent.isPlainTabKeyDown(): Boolean =
    key == Key.Tab &&
        type == KeyEventType.KeyDown &&
        !isAltPressed &&
        !isCtrlPressed &&
        !isMetaPressed
