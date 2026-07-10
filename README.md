# Tilo.Compose

Compose-first Kotlin Multiplatform map toolkit.

Tilo.Compose is an early-stage map framework for Compose apps. It focuses on a
simple happy path for raster tiles, vector feature layers, labels, drawing, and
CRS-aware map state.

Unlike web-map-only toolkits, Tilo.Compose is designed for maps that need to
work in local and national coordinate reference systems, not only WGS84 or Web
Mercator. The current showcase uses Czech S-JTSK / Krovak (`EPSG:5514`) as a
first-class map projection.

> Work in progress: the project is still unstable and APIs may change before a
> public 1.0 release.

## Quick Example

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

val places = remember {
    features {
        point("brno", 16.6068, 49.1951) {
            label = "Brno"
            style = pointStyle {
                size = 14.0
                fill(0xFF43A047)
                stroke(0xFF263238, width = 2.0)
            }
        }
    }
}

TiloMap(
    cameraState = cameraState,
) {
    wmsTileLayer(ortofoto)

    featureLayer("places", places) {
        projection = wgs84()
        renderMode = cachedBitmap()
    }
}
```

## Current Status

| Area | Status | Notes |
| --- | --- | --- |
| Compose map renderer | ✅ Done | Canvas renderer with pan and zoom gestures. |
| Public DSL | ✅ Done | `tilo.compose.dsl` exposes the Compose-first API. |
| Raster tile rendering | ✅ Done | WMS and XYZ tile layers render as raster images. |
| WMS capabilities | ✅ Done | WMS layers can be created from GetCapabilities. |
| Tile planning | ✅ Done | Density-aware planning with limited visible tile count. |
| Tile fetching | ✅ Done | Shared HTTP client, byte cache, in-flight deduplication, and concurrency control. |
| Tile prefetching | ✅ Done | Nearby tiles are fetched outside the visible viewport. |
| Raster fallback | ✅ Done | Existing tiles can remain visible while sharper tiles load. |
| Simple vector features | ✅ Done | Points, lines, polygons, multi-geometries, and polygon holes. |
| Labels | ✅ Done | Labels render through a bitmap cache. |
| Feature styling | ✅ Done | Fill, stroke, dash, hatch, dot patterns, and point shapes. |
| Drawing plugin | ✅ Done | Point, line, and polygon drafts with save callback and history state. |
| GeoCore split | ✅ Done | Platform-agnostic contracts live in `Tilo.GeoCore`. |
| Spatial indexing | ✅ Done | Feature sources use `Tilo.SpatialIndex` for viewport queries. |
| CRS model | ✅ Done | Human-readable DSL helpers wrap WGS84, Web Mercator, S-JTSK/Krovak, and identity. |
| Non-Web-Mercator maps | ✅ Done | Map state, WMS tiles, and feature layers can work in projections such as EPSG:5514. |
| CRS transformations | 🟡 Partial | GeoCore exposes contracts/registry; concrete transforms are injected by runtime/app code. |
| Vector tiles | ⚪ Not planned | Current focus is raster tiles and simple vector layers. |
| Raster MBTiles | ⬜ Not done | Can be added later if needed. |
| iOS production support | ⬜ Not done | KMP targets exist; validation currently focuses on Android/JVM. |
| Public Maven artifacts | ⬜ Not done | Planned after API stabilization. |

## License

MIT License. See [LICENSE](LICENSE).

GeoCore and SpatialIndex are also MIT licensed in their own repositories.
