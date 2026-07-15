# WMS Layers

Declare WMS directly inside the map layer DSL. The map loads GetCapabilities
asynchronously and owns the resulting raster runtime. Add the same optional
state used by other raster declarations when the UI needs loading, error, or
retry controls:

```kotlin
val ortofotoState = rememberRasterLayerState()

TiloMap(cameraState) {
    wmsTileLayer(
        id = "cuzk-ortofoto",
        capabilitiesUrl = "https://ags.cuzk.gov.cz/arcgis1/services/ORTOFOTO/MapServer/WMSServer",
        layerName = "0",
        projection = sjtsk(),
        format = "image/jpeg",
        attribution = attribution("(c) CUZK"),
        state = ortofotoState,
        onError = { error -> reportMapError(error) },
    )
}
```

The layer is omitted while capabilities are loading or after initialization
fails. Observe `ortofotoState.status` as `Idle`, `Loading`, `Ready`, or
`Failed(error)`. A transient initialization failure can be retried explicitly:

```kotlin
if (ortofotoState.status is RasterLayerStatus.Failed) {
    RetryButton(onClick = ortofotoState::retry)
}
```

`onError` receives both GetCapabilities failures and later tile transport
failures. Tile failures are recoverable: they keep `status` as `Ready` and are
also available through `lastTileError`. A failure in one tile does not cancel
healthy tile requests.

Useful options include:

- `id`
- `capabilitiesUrl`
- `layerName` or `layerNames`
- `projection`
- `styles`
- `format`
- `getMapVersion`
- `axisOrder`
- `zIndex`
- `visible`, `minZoom`, and `maxZoom`
- tile visibility and prefetch limits
- `attribution` or `attributions`
- `state`
- `onError`

Changing presentation-only values such as visibility or z-index preserves the
loaded capabilities, raster runtime, and tile cache. Changing source
configuration retires the previous runtime and starts a new capabilities load.

## Projection

The `projection` argument should match the CRS requested from the WMS service:

```kotlin
TiloMap(cameraState) {
    wmsTileLayer(
        id = "cuzk-ortofoto",
        capabilitiesUrl = "...",
        layerName = "0",
        projection = sjtsk(),
    )
}
```

The capabilities loader derives request details where possible. Manual raster
layer construction remains an advanced escape hatch.
