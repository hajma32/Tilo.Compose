# Getting Started

Tilo.Compose is designed around a simple Compose-first happy path:

```kotlin
val cameraState = rememberMapCameraState(
    center = Point(-650_000.0, -1_100_000.0),
    zoom = 11.5,
    projection = sjtsk(),
)

val ortofoto = rememberWMSLayer(
    id = "cuzk-ortofoto",
    capabilitiesUrl = "https://ags.cuzk.gov.cz/arcgis1/services/ORTOFOTO/MapServer/WMSServer",
    layerName = "0",
    projection = sjtsk(),
    format = "image/jpeg",
)

TiloMap(
    cameraState = cameraState,
    attributionContent = defaultAttributionContent(),
    scaleBarContent = defaultScaleBarContent(),
    cameraControlsContent = defaultZoomControlsContent(),
) {
    wmsTileLayer(ortofoto)
}
```

The important pieces are:

- `rememberMapCameraState(...)` owns the visible map position and projection.
- `rememberWMSLayer(...)` loads WMS capabilities and creates a tile layer state.
- `TiloMap { ... }` declares the visible layers.
- Optional UI slots add attribution, scale bar, and zoom controls without making
  them mandatory.
- `sjtsk()`, `wgs84()`, and `webMercator()` keep CRS usage readable.

Tilo.Compose is not limited to Web Mercator. The current showcase focuses on
maps in Czech S-JTSK / Krovak (`EPSG:5514`), with vector features transformed
from WGS84 where needed.
