# Drawing

The drawing plugin lives in `tilo.compose.draw`. It is intentionally headless:
the plugin owns drawing state and draft features, while the application owns
controls and saved data.

```kotlin
import tilo.compose.draw.*

var savedDrawings by remember { mutableStateOf(emptyList<Feature>()) }

val drawState = rememberDrawState(
    initialMode = DrawMode.Polygon,
    onSave = { feature ->
        savedDrawings = savedDrawings + feature
    },
)

TiloMap(
    cameraState = cameraState,
    onTapWorld = drawState::onMapTap,
    invalidationKey = drawState.revision,
) {
    featureLayer("saved-drawings", savedDrawings)
    drawLayer(drawState)
}
```

## State

Create state with:

- `rememberDrawState(...)` inside Compose
- `createDrawState(...)` outside Compose

Available constructor options:

- `initialMode`
- `style`
- `onSave`
- `onChange`

## Modes

`DrawMode` supports:

- `Point`
- `Line`
- `Polygon`

## Actions

`DrawState` exposes:

- `startDrawing()`
- `stopDrawing(clearDraft = true)`
- `toggleDrawing()`
- `selectMode(mode)`
- `onMapTap(point)`
- `save()`
- `clear()`
- `undo()`
- `redo()`

Useful state:

- `isDrawing`
- `mode`
- `draftPoints`
- `draftFeatures`
- `revision`
- `canSave`
- `canUndo`
- `canRedo`

Pass `revision` to `TiloMap(invalidationKey = ...)` when drawing should
invalidate cached vector rendering immediately.

## Drawing Layer

Add the draft layer with:

```kotlin
TiloMap(cameraState = cameraState) {
    drawLayer(
        state = drawState,
        id = "draw-layer",
        zIndex = 20,
        projection = wgs84(),
    )
}
```

The standalone `drawLayer(state, ...)` function returns a `FeatureLayer` if you
need to assemble layers manually.

## Style

Provide a custom `DrawStyle` when creating state:

```kotlin
val drawState = rememberDrawState(
    style = DefaultDrawStyle(
        point = pointStyle {
            size = 14.dp
            fill(0xFFFFC107)
            stroke(0xFF263238, width = 2.dp)
        },
        line = lineStyle {
            stroke(0xFFFFC107, width = 4.dp)
        },
        polygon = polygonStyle {
            fill(0x55FFC107)
            stroke(0xFFFF8F00, width = 3.dp)
        },
    )
)
```

Saved drawings are not managed by the draw plugin. `onSave` gives the completed
feature back to the application, and the application decides where and how to
store or render it.
