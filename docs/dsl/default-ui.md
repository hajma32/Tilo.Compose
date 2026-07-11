# Default UI

Tilo.Compose keeps map UI optional. `TiloMap` exposes content slots, and the
`tilo.compose.ui` module provides default implementations for common overlays.

```kotlin
import tilo.compose.ui.*

TiloMap(
    cameraState = cameraState,
    attributionContent = defaultAttributionContent(),
    scaleBarContent = defaultScaleBarContent(),
    cameraControlsContent = defaultZoomControlsContent(),
) {
    xyzTileLayer(
        id = "osm",
        urlTemplate = "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
        attribution = attribution(
            label = "(c) OpenStreetMap contributors",
            url = "https://www.openstreetmap.org/copyright",
        ),
    )
}
```

## Content Slots

`TiloMap` supports:

- `attributionContent: BoxScope.(List<Attribution>) -> Unit`
- `scaleBarContent: BoxScope.(ScaleBar) -> Unit`
- `cameraControlsContent: BoxScope.(MapCameraState) -> Unit`

If a slot is `null`, that overlay is not shown.

## Default Helpers

Use these when the built-in UI is enough:

- `defaultAttributionContent()`
- `defaultScaleBarContent()`
- `defaultZoomControlsContent()`

Or call the composables directly from your own slot:

```kotlin
TiloMap(
    cameraState = cameraState,
    scaleBarContent = { scaleBar ->
        DefaultScaleBar(scaleBar)
    },
) {
    // layers
}
```

## Provided Composables

- `DefaultAttributionOverlay(attributions)` renders layer attributions in a
  clickable bottom-right box.
- `DefaultScaleBar(scaleBar)` renders a CRS-aware scale bar in the bottom-left
  corner.
- `DefaultZoomControls(cameraState, zoomStep)` renders zoom in/out buttons for
  any `MapCameraController`.
- `DefaultZoomControls(zoomStep, onZoomBy)` renders the same buttons with a
  custom suspend callback, which is useful for animated `MapCameraState`
  controls.

The current default UI is intentionally small. Style objects for these overlays
are planned, so applications will be able to tune the defaults without copying
the composables.
