# WMS Layers

Use `rememberWMSLayer` to create a WMS tile layer from GetCapabilities:

```kotlin
val ortofoto = rememberWMSLayer(
    id = "cuzk-ortofoto",
    capabilitiesUrl = "https://ags.cuzk.gov.cz/arcgis1/services/ORTOFOTO/MapServer/WMSServer",
    layerName = "0",
    projection = sjtsk(),
    format = "image/jpeg",
)
```

Then add it to the map:

```kotlin
TiloMap(cameraState) {
    wmsTileLayer(ortofoto)
}
```

## Loading State

`rememberWMSLayer` returns `WMSLayerState`.

Useful state:

```kotlin
ortofoto.isLoading
ortofoto.error
```

The internal tile layer is intentionally hidden. In normal application code,
pass the state to `wmsTileLayer(...)` instead of manually assembling raster
layers.

## Projection

The `projection` argument should match the CRS requested from the WMS service:

```kotlin
rememberWMSLayer(
    id = "cuzk-ortofoto",
    capabilitiesUrl = "...",
    layerName = "0",
    projection = sjtsk(),
)
```

The capabilities loader derives layer bounds and request details where possible.
Manual raster layer construction remains an advanced escape hatch.
