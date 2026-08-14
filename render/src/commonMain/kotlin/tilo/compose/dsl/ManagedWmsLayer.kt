@file:OptIn(ExperimentalTiloApi::class)

package tilo.compose.dsl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import tilo.compose.core.layers.Attribution
import tilo.compose.core.layers.raster.RasterTileDiagnosticEvent
import tilo.compose.core.layers.raster.RasterTileLayer
import tilo.compose.core.layers.raster.TileLayer

internal data class ManagedWmsLayerKey(
    val layerId: String,
    val configuration: Any,
)

internal class ManagedWmsLayerDeclaration(
    val key: ManagedWmsLayerKey,
    val id: String,
    val zIndex: Int,
    val visible: Boolean,
    val opacity: Double = 1.0,
    val minZoom: Double?,
    val maxZoom: Double?,
    val attributions: List<Attribution>,
    val state: RasterLayerState?,
    val onError: ((Throwable) -> Unit)?,
    val create: suspend (
        onError: (Throwable) -> Unit,
        onDiagnostic: suspend (RasterTileDiagnosticEvent) -> Unit,
    ) -> RasterTileLayer,
)

/** Owns one asynchronously created WMS runtime for a stable source configuration. */
internal class ManagedWmsRuntime {
    var layer: RasterTileLayer? by mutableStateOf(null)
        private set

    fun replace(next: RasterTileLayer?) {
        if (layer === next) return
        val previous = layer
        layer = next
        previous?.close()
    }

    fun close() {
        replace(null)
    }
}

/** Resolves one declarative WMS entry without exposing a separate public remember step. */
@Composable
@Suppress("TooGenericExceptionCaught")
internal fun rememberManagedWmsLayer(declaration: ManagedWmsLayerDeclaration): TileLayer? {
    val runtime = remember(declaration.key) { ManagedWmsRuntime() }
    val diagnostics =
        remember(declaration.key) {
            MutableRasterLayerDiagnostics(declaration.state, declaration.onError)
        }

    SideEffect {
        diagnostics.update(declaration.state, declaration.onError)
    }
    DisposableEffect(runtime) {
        onDispose {
            diagnostics.retire()
            runtime.close()
        }
    }
    LaunchedEffect(runtime) {
        diagnostics.loading()
        try {
            runtime.replace(declaration.create(diagnostics::tileFailed, diagnostics::onDiagnostic))
            diagnostics.ready()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            runtime.replace(null)
            diagnostics.initializationFailed(error)
        }
    }

    return runtime.layer?.let { layer ->
        PresentedTileLayer(
            runtime = layer,
            id = declaration.id,
            zIndex = declaration.zIndex,
            visible = declaration.visible,
            opacity = declaration.opacity,
            minZoom = declaration.minZoom,
            maxZoom = declaration.maxZoom,
            attributions = declaration.attributions,
            diagnostics = diagnostics,
        )
    }
}
