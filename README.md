# Tilo.Compose

Compose-first Kotlin Multiplatform map framework, currently in active
development.

Tilo.Compose is being built as a small, modular map stack for Compose apps:
GeoCore map state and layer contracts, runtime tile loading, simple vector
layers, projection handling, and a renderer that stays independent of CRS
details.

The table below is the current project shape: what the framework is intended to
contain, and how much of that is already implemented.

## Roadmap Status

| Area | Status | Notes |
| --- | --- | --- |
| Compose map renderer | ✅ Done | Canvas-based renderer with pan and zoom gestures. |
| Raster tile rendering | ✅ Done | WMS and XYZ tile layers render as raster images. |
| Tile placeholders | ✅ Done | Light-blue placeholders are drawn while image bytes load. |
| Tile planning | ✅ Done | Density-aware visible tile planning, limited visible tile count, and prefetch planning. |
| Tile fetching | ✅ Done | Shared HTTP client, in-memory byte cache, in-flight request deduplication, and fetch concurrency control. |
| Tile prefetching | ✅ Done | Nearby tiles are fetched outside the visible render result. |
| Raster fallback during navigation | ✅ Done | Previously decoded tiles can stay visible while new tiles load. |
| Simple vector features | ✅ Done | Points, line strings, multi-line strings, polygons, multi-polygons, and polygon holes. |
| Labels | ✅ Done | Basic labels are rendered for vector features. |
| Feature styling | 🟡 Basic | Basic fill, stroke, and point styling exists; this is not yet a full style system. |
| GeoCore split | ✅ Done | Platform-agnostic map/geometry/tile/projection contracts live in `Tilo.GeoCore`. |
| In-memory feature indexing | ✅ Done | Feature sources use `Tilo.SpatialIndex` for viewport queries. |
| CRS model | 🟡 Basic | Projection metadata exists for EPSG:4326, EPSG:3857, EPSG:5514, and identity projection. |
| CRS transformations | 🟡 Partial | GeoCore exposes contracts/registry only. Concrete transforms are injected by the app/runtime layer; Android demo currently wires EPSG:5514 explicitly. |
| Renderer CRS separation | ✅ Done | Renderer uses `Map.worldToScreen` / `screenToWorld` and does not contain projection-specific logic. |
| Vector tiles | ⚪ Not planned for current phase | Old vector tile and vector MBTiles experiments were removed. Current focus is simple vector layers. |
| MBTiles raster loading | ⬜ Not done | Raster MBTiles support can be added later if needed. |
| iOS production support | ⬜ Not done | KMP targets exist, but current validation focuses on Android/JVM. |
| Public Maven artifacts | ⬜ Not done | `Tilo.GeoCore` and `Tilo.SpatialIndex` are split into their own repos; Maven publishing is planned later. |

## Current Demo

The demo Android app currently:

- centers on Brno in `EPSG:5514`.
- renders CUZK orthophoto WMS tiles.
- overlays test vector features in `EPSG:4326`.
- lets you switch between geometry test screens:
  - multiple labeled points.
  - line string.
  - polygon.
  - multi-line string.
  - multi-polygon.
  - polygon with holes.

The demo is primarily a rendering/test harness, not a polished sample
application.

## Modules

- `:composeApp` - Android demo app.
- `:geocore` - Git submodule pointing to
  [hajma32/Tilo.GeoCore](https://github.com/hajma32/Tilo.GeoCore). Contains
  platform-agnostic map state, projections, geometry, feature sources, tile
  planning, and layer contracts.
- `:core` - runtime glue that should not live in GeoCore: HTTP-backed raster
  tile layers, tile byte fetching/cache/prefetch orchestration, and concrete
  transformation adapters used by the demo.
- `:render` - Compose renderer, gesture input, raster/vector render pipelines,
  labels, and canvas backend.
- `:spatial-index` - Git submodule pointing to
  [hajma32/Tilo.SpatialIndex](https://github.com/hajma32/Tilo.SpatialIndex).

## Repository Setup

Clone with submodules:

```bash
git clone --recurse-submodules git@github.com:hajma32/Tilo.Compose.git
cd Tilo.Compose
```

Or, after a normal clone:

```bash
git submodule update --init --recursive
```

The `geocore` and `spatial-index` submodules are required for local development.
`Tilo.GeoCore` depends on `Tilo.SpatialIndex` for fast viewport queries over
in-memory features.

## Build And Test

Android/JVM checks used during development:

```bash
./gradlew :spatial-index:jvmTest \
  :geocore:jvmTest \
  :core:testDebugUnitTest \
  :core:compileDebugKotlinAndroid \
  :render:compileDebugKotlinAndroid \
  :composeApp:compileDebugKotlinAndroid
```

Run the Android demo from Android Studio by opening this repository and launching
the `composeApp` Android configuration.

## Basic Usage Shape

```kotlin
val map = Map(
    center = Point(-650_000.0, -1_100_000.0),
    zoom = 11.5,
    projection = Epsg5514Projection
)

val layers = listOf(
    createOrtofotoTileLayer(id = "cuzk-ortofoto"),
    FeatureLayer(
        id = "features",
        zIndex = 1,
        projection = Epsg4326Projection,
        features = mapFeatures {
            point(
                key = "brno",
                x = 16.6068,
                y = 49.1951,
                label = "Brno"
            )
        }
    )
)

MapRenderer(
    map = map,
    layers = layers,
    tileDecoder = ::decodeImageBitmap
)
```

## Design Direction

- Keep `Tilo.GeoCore` platform-agnostic: no Compose, no HTTP, no image decode,
  and no concrete licensed projection engines.
- Keep `core` as runtime glue for platform/network-backed behavior that does
  not belong in GeoCore.
- Keep `render` CRS-agnostic.
- Inject concrete CRS transformations through `MapConfig` /
  `TransformationRegistry`; do not hard-wire them into GeoCore defaults.
- Prefer simple, explicit layer APIs over a large implicit style pipeline.
- Keep raster tiles and simple vector layers working well before reintroducing
  larger data-loading formats.
- Use Maven artifacts for extracted modules once APIs stabilize; submodules are
  useful during active development.

## License

MIT License. See [LICENSE](LICENSE).

The GeoCore and SpatialIndex submodules are also MIT licensed in their own
repositories.
