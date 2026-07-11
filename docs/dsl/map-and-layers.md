# Map And Layers

`TiloMap` is the main Compose entrypoint:

```kotlin
TiloMap(
    cameraState = cameraState,
    modifier = Modifier.fillMaxSize(),
    onFeatureSelect = { selections -> /* show app UI */ },
    selectedFeatures = selectedRefs,
    attributionContent = defaultAttributionContent(),
    scaleBarContent = defaultScaleBarContent(),
    cameraControlsContent = defaultZoomControlsContent(),
) {
    wmsTileLayer(ortofoto)

    featureLayer("places", places) {
        projection = wgs84()
    }
}
```

The `TiloMap` content block is only for layers. Default UI is injected through
content slots so applications can use the provided overlays or replace them with
their own composables.

## Camera State

Create camera state with `rememberMapCameraState`:

```kotlin
val cameraState = rememberMapCameraState(
    center = Point(-650_000.0, -1_100_000.0),
    zoom = 11.5,
    projection = sjtsk(),
)
```

Coordinates are expressed in the selected map projection.

Camera state also exposes programmatic zoom helpers:

```kotlin
cameraState.zoomIn()
cameraState.zoomOut()
cameraState.zoomBy(delta = 0.5)
```

Animated zoom helpers are suspend functions, intended for UI controls:

```kotlin
scope.launch {
    cameraState.animateZoomIn()
}
```

## Raster Layers

Use `wmsTileLayer(state)` for WMS layers created by `rememberWMSLayer`:

```kotlin
TiloMap(cameraState) {
    wmsTileLayer(ortofoto)
}
```

Use `xyzTileLayer(...)` for public slippy-map tile services. Web Mercator is
the default:

```kotlin
TiloMap(cameraState) {
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

Useful `xyzTileLayer` options:

- `zIndex`
- `projection`
- `grid`
- `tms`
- `maxVisibleTiles`
- `prefetchMargin`
- `attribution`
- `attributions`

Use `tileStoreLayer(...)` for local or app-owned tile stores. The tile reader is
provided by the application so platform-specific SQLite access and custom
metadata stay outside the renderer:

```kotlin
val krovakGrid = tileGrid(
    originX = -925_000.0,
    originY = -920_000.0,
    worldWidth = 450_000.0,
)

TiloMap(cameraState) {
    tileStoreLayer(
        id = "offline-krovak",
        projection = sjtsk(),
        grid = krovakGrid,
        readTile = offlineTiles::readTile,
        scheme = TileRowScheme.TMS,
    )
}
```

Raster tiles are not reprojected on the client. The raster layer projection
must match the map projection.

`tileStoreLayer(...)` is a generic provider API. A dedicated `mbTilesLayer(...)`
helper will be added when Tilo.Compose ships a bundled MBTiles reader.

Advanced code can add pre-built raster layers with `rasterLayer(layer)`.
`tileLayer(layer)` and `tileLayer(wmsState)` are aliases for raster layer
integration.

## Feature Layers

Use `featureLayer` for in-memory vector features:

```kotlin
featureLayer("roads", roads) {
    projection = wgs84()
    renderMode = cachedBitmap()
    style = featureLayerStyle {
        line {
            casing(0xFFFFFFFF, width = 7.dp)
            stroke(0xFF2563EB, width = 4.dp)
        }
        label(mediumLabelStyle())
    }
}
```

`projection` describes the feature coordinates. If it differs from the map
projection, Tilo.Compose uses the map transformation registry.

`renderMode` controls vector rendering:

- `immediate()` draws features directly.
- `cachedBitmap()` renders heavier layers into a reusable bitmap.

Feature selection is first class. `onFeatureSelect` receives all features hit by
a tap, in draw order. Pass `selectedFeatures` back to the map to render selected
styles:

```kotlin
TiloMap(
    cameraState = cameraState,
    onFeatureSelect = { hits -> selectedRefs = hits.map { it.ref }.toSet() },
    selectedFeatures = selectedRefs,
) {
    featureLayer("places", places)
}
```

## Attribution And Default UI

Layers can carry one or more attribution records. `attribution(...)` is a small
helper for the common single-attribution case:

```kotlin
xyzTileLayer(
    id = "osm",
    urlTemplate = "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
    attribution = attribution(
        label = "(c) OpenStreetMap contributors",
        url = "https://www.openstreetmap.org/copyright",
    ),
)
```

Use `defaultAttributionContent()`, `defaultScaleBarContent()`, and
`defaultZoomControlsContent()` from `tilo.compose.ui` to opt into the default
overlays.

## Custom Layers

For advanced integrations, concrete layer objects can still be added:

```kotlin
TiloMap(cameraState) {
    +customLayer
}
```

This is intended as an escape hatch, not the default happy path.
