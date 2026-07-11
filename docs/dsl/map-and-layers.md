# Map And Layers

`TiloMap` is the main Compose entrypoint:

```kotlin
TiloMap(
    cameraState = cameraState,
    modifier = Modifier.fillMaxSize(),
) {
    wmsTileLayer(ortofoto)

    featureLayer("places", places) {
        projection = wgs84()
    }
}
```

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
    )
}
```

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
    )
}
```

Raster tiles are not reprojected on the client. The raster layer projection
must match the map projection.

`tileStoreLayer(...)` is a generic provider API. A dedicated `mbTilesLayer(...)`
helper will be added when Tilo.Compose ships a bundled MBTiles reader.

Advanced code can add pre-built raster layers with `rasterLayer(layer)`.

## Feature Layers

Use `featureLayer` for in-memory vector features:

```kotlin
featureLayer("roads", roads) {
    projection = wgs84()
    renderMode = cachedBitmap()
}
```

`projection` describes the feature coordinates. If it differs from the map
projection, Tilo.Compose uses the map transformation registry.

`renderMode` controls vector rendering:

- `immediate()` draws features directly.
- `cachedBitmap()` renders heavier layers into a reusable bitmap.

## Custom Layers

For advanced integrations, concrete layer objects can still be added:

```kotlin
TiloMap(cameraState) {
    +customLayer
}
```

This is intended as an escape hatch, not the default happy path.
