# Accessibility

Tilo Compose exposes the map as one focusable accessibility surface. The surface reports a
resource-backed description plus its current zoom and rotation using the platform locale. Individual canvas-rendered geographic
features are intentionally not separate accessibility nodes in v1; applications should announce
feature selection or provide an accompanying accessible list when feature-level navigation is
required.

## Default controls

The optional zoom, compass, scale, and attribution helpers provide:

- button roles and click actions on the complete control surface;
- disabled states at the minimum/maximum zoom and when the compass already points north;
- 48 dp default camera-control targets;
- a readable scale description and focusable attribution links; and
- deterministic forward and reverse focus traversal between the map, zoom controls, compass, and
  attribution.

Default text comes from Compose resources, and numeric camera state uses the platform locale.
Applications can replace UI descriptions with
`MapUiAccessibility` and map descriptions with `MapAccessibilityOptions`:

```kotlin
val mapAccessibility =
    MapAccessibilityOptions(
        contentDescription = "Public transport map",
        stateDescription = { camera ->
            "Zoom ${camera.zoom}, heading ${camera.bearing} degrees"
        },
    )

val uiAccessibility =
    MapUiAccessibility(
        zoomInDescription = "Increase map zoom",
        zoomOutDescription = "Decrease map zoom",
        resetNorthDescription = "Point map north",
        scaleBarDescription = { scale -> "Map scale ${scale.label}" },
    )

TiloMap(
    cameraState = cameraState,
    accessibility = mapAccessibility,
    cameraControlsContent = defaultCameraControlsContent(accessibility = uiAccessibility),
    attributionContent = defaultAttributionContent(accessibility = uiAccessibility),
    scaleBarContent = defaultScaleBarContent(accessibility = uiAccessibility),
) {
    // Layers
}
```

Custom focusable controls inside a `TiloMap` slot can join the same keyboard and accessibility
traversal order. Use an index between the surrounding default controls and place the modifier before
`clickable` or `focusable`:

```kotlin
Modifier
    .tiloMapFocusTarget(traversalIndex = 2.5f)
    .clickable { /* custom map action */ }
```

Only controls and linked attributions that are actually composed participate in traversal. Missing
or disabled destinations are skipped without changing visual overlay stacking.

## Hardware keyboard

Give focus to the map surface before using map shortcuts. Keyboard events are handled only by the
focused map and are not intercepted while a control, attribution link, text field, or other sibling
has focus.

| Key | Action |
|---|---|
| Arrow keys | Pan by `keyboardPanStepPx` screen pixels |
| `+`, `Shift+=`, numpad `+` | Zoom in by `keyboardZoomStep` |
| `-`, numpad `-` | Zoom out by `keyboardZoomStep` |
| `Home` | Reset rotation to north |
| `Tab` / `Shift+Tab` | Move through the map controls and attribution links |

Set `keyboardNavigationEnabled = false` in `MapAccessibilityOptions` to remove the map surface from
keyboard focus and disable these shortcuts. Modifier keys such as Ctrl, Alt, and Meta are never
consumed by map navigation.

The shared semantics and focus contract is exercised on the iOS simulator, while the fuller
interaction matrix (including text-input isolation and click behavior) runs on an Android API 35
emulator in CI.
