# Tilo.Compose API Roadmap

This document describes the current public API direction and the steps needed to make Tilo.Compose feel compose-first while still keeping advanced rendering, tiling, projection, and plugin capabilities available.

## Goal

Tilo.Compose should make the simple case simple:

```kotlin
TiloMap(
    cameraState = rememberMapCameraState(
        center = Point(16.6068, 49.1951),
        zoom = 12.0,
        projection = Epsg5514,
    )
) {
    tiles("ortofoto") {
        wms(...)
    }

    features("parcels", parcels) {
        projection = Epsg4326
        renderMode = cachedBitmap()
    }

    draw(drawState)
}
```

Advanced APIs should remain available, but users should not need them for the first working map.

## Current State

The project already has the right core building blocks:

- `TiloMap(...)` composable renders the map.
- `MapLayerBuilder` provides a layer DSL.
- `FeatureLayer` and `TileLayer` model vector and raster layers.
- `VectorRenderStrategy.Immediate` and `VectorRenderStrategy.CachedBitmap` support lightweight and heavier vector rendering.
- `DrawState` and `drawLayer` provide a plugin-shaped drawing flow.
- GeoCore owns platform-agnostic geometry, features, projections, styles, tile grids, and transformations.
- Render-specific code owns Compose canvas rendering, tile drawing, vector command building, label bitmap caching, and gesture input.

The weak spot is API ergonomics: the public surface still exposes too much engine-level detail for common use cases.
Raster layers are a special case of this problem: WMS is reasonably surfaced
through `rememberWMSLayer(...)`, but XYZ and other raster source types still
exist mostly as low-level engine building blocks.

## API Principles

- Compose-first API should be the default.
- Advanced rendering APIs should be available but optional.
- Users should declare layers, not assemble renderer internals.
- `viewport`, tile planning, cache invalidation, and render scheduling should be mostly hidden.
- Layer content changes should invalidate immediately.
- Projection and transformation behavior should be explicit, but not noisy.
- Public examples should use projection helpers such as `wgs84()`, `webMercator()`, and `sjtsk()` instead of raw EPSG object names.
- Plugins such as drawing should be usable with default UI or custom headless controls.

## Proposed Public Shape

## Examples Strategy

The project should have two different example surfaces with different goals.

### `composeApp`: Product-grade showcase

`composeApp` should become a real showcase application, not a loose sample catalog.

Working concept: **Field Planner**.

It should demonstrate Tilo.Compose as the foundation for a real field mapping app:

- raster basemaps such as ortofoto, OSM, or debug tiles,
- vector overlays for work areas, inspection routes, access points, restricted zones, and measurements,
- polygon, line, point, label, dash, pattern, and highlight styling,
- layer switching,
- selected feature details and callouts,
- drawing point, line, and polygon drafts,
- saving drawn features through user-owned `onSave`,
- cached bitmap rendering for heavier vector layers,
- immediate rendering for active selections and drawing drafts,
- spatial queries such as selecting or finding nearby features,
- optional debug/performance panel.

The UI should feel like a small product. It should avoid being labeled as a "features demo" and instead use real domain language such as `Work areas`, `Inspection routes`, `Access points`, `Restricted zones`, and `Draft drawing`.

### `samplesApp`: Documentation samples

Add a separate `samplesApp` for small, focused examples that map directly to documentation and API usage.

Candidate samples:

- basic map,
- XYZ tiles,
- WMS tiles,
- vector points,
- lines and polygons,
- styling,
- labels,
- layer switching,
- drawing,
- cached bitmap vector layer,
- spatial index,
- custom draw controls,
- custom tile layer.

`samplesApp` should optimize for minimal code and copy-pasteable examples. `composeApp` should optimize for product feel and breadth.

### Map Entrypoint

Rename the public composable entrypoint from `Map` to `TiloMap` to avoid confusion with core map state.

```kotlin
TiloMap(
    cameraState = cameraState,
    modifier = Modifier.fillMaxSize(),
) {
    +customLayer
}
```

Keep a lower-level renderer entrypoint available for advanced use:

```kotlin
MapRenderer(
    map = mapState,
    layers = layers,
    backend = backend,
    tileDecoder = tileDecoder,
)
```

### State

Introduce Compose-friendly state helpers:

```kotlin
val cameraState = rememberMapCameraState(
    center = Point(16.6068, 49.1951),
    zoom = 12.0,
    projection = Epsg5514,
)
```

Target split:

- `MapCameraState`: center, zoom, gestures, bounds.
- `MapConfig`: zoom limits, transformations, interaction policy.
- Internal viewport: managed by the composable, not user code.

### Layer DSL

Keep `Layer` as the advanced abstraction, but add high-level layer builders:

```kotlin
TiloMap(cameraState) {
    wmsTileLayer(ortofoto)

    featureLayer("roads", roads) {
        projection = wgs84()
        renderMode = immediate()
    }

    drawLayer(drawState)
}
```

Advanced users can still provide concrete layers:

```kotlin
TiloMap(cameraState) {
    +FeatureLayer(...)
    +customLayer
}
```

### Styles

Keep GeoCore style data classes as the stable platform-agnostic model, but add Compose-friendly builders for common usage:

```kotlin
val parcelStyle = polygonStyle {
    fill(0x3343A047)
    stroke(0xFF2E7D32, width = 3.0)
    dash(12.0, 7.0)
}
```

The core style classes remain useful for serialization, sharing between modules, and advanced customization.

### Drawing Plugin

Drawing should be a Tilo.Compose plugin module:

```kotlin
val drawState = rememberDrawState(
    onSave = { feature -> savedFeatures += feature }
)

TiloMap(cameraState) {
    drawLayer(drawState)
}

DrawControls(drawState)
```

The plugin should support:

- default Compose controls,
- custom user controls,
- point, line, and polygon modes,
- undo and redo,
- `onSave`,
- configurable draft style,
- no built-in saved layer policy.

### Invalidation

`invalidationKey` should become an advanced escape hatch, not a common API requirement.

Preferred behavior:

- changing layer list invalidates immediately,
- changing feature list invalidates immediately,
- changing draw state invalidates immediately,
- changing render strategy invalidates relevant caches,
- custom layers can expose a `version` or `cacheKey`.

### Namespaces

Public package names should use one consistent root:

- `tilo.compose.core.*` for platform-agnostic GeoCore models and contracts,
- `tilo.compose.dsl.*` for user-facing Compose-first APIs,
- `tilo.compose.render.*` for renderer implementation and advanced backends,
- `tilo.compose.draw.*` for the drawing plugin.

The showcase app can keep its own application package, but public examples
should import public helpers from `tilo.compose.dsl.*` plus optional plugins.

## Step-by-step Plan

### 1. Stabilize raster source APIs

Raster layers should have the same compose-first ergonomics as vector layers.
The renderer already works with the generic `TileLayer` abstraction and there
is a low-level `XYZTileLayer`, but v1.0 needs a clean public API for common
raster source types.

Target public shape:

```kotlin
TiloMap(cameraState) {
    xyzTileLayer(
        id = "osm",
        url = "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
        projection = webMercator(),
    )

    wmsTileLayer(cuzkOrtofoto)

    mbtilesLayer(
        id = "offline-krovak",
        source = offlineKrovakFile,
        projection = sjtsk(),
        grid = krovakTileGrid(),
    )
}
```

Scope:

- Add `xyzTileLayer(...)` DSL helper for `{z}/{x}/{y}` URL templates.
- Make Web Mercator XYZ the default for slippy-map sources.
- Keep `tms = true` support for flipped Y tile schemes.
- Add first-class raster MBTiles support as an offline XYZ/TMS-style provider.
- Keep `rasterLayer(layer)` and `+layer` as advanced escape hatches.
- Introduce clear source models such as `XyzRasterSource`, `WmsRasterSource`,
  `MbtilesRasterSource`, and later `WmtsRasterSource`.
- Add `samplesApp` examples for XYZ, WMS, MBTiles, and custom raster layers.
- Add showcase usage for at least one public XYZ basemap.
- Document that tiles are not reprojected client-side; raster layer projection
  must match the map projection.

MBTiles notes:

- Treat raster MBTiles as a tile store/provider that returns tile bytes for
  `z/x/y` tile coordinates.
- Support standard Web Mercator MBTiles with sensible defaults.
- Support `scheme = TMS` / flipped Y, because many MBTiles files store
  `tile_row` that way.
- Support S-JTSK/Krovak MBTiles as a normal first-class use case through
  explicit `projection = sjtsk()` and `grid = krovakTileGrid()` configuration.
- Do not assume standard MBTiles metadata contains a complete `TileGrid`.
  Metadata such as `bounds`, `center`, `minzoom`, `maxzoom`, and `format` is
  useful, but not enough to describe arbitrary CRS tile matrices.
- Allow a custom MBTiles metadata reader/resolver for project-specific databases
  that store projection and grid metadata in custom metadata fields.
- Keep vector MBTiles out of scope for v1.0 raster support.

Extension points:

- Keep `TileLayer` as the fully custom escape hatch for unusual providers.
- Add or document a smaller `TileScheme`/`TileProvider` style abstraction so
  users can plug in exotic tiling without replacing fetching, caching, and
  rendering.
- Support custom regular grids through `Projection + TileGrid`.
- Support exotic or non-regular schemes through user-provided planning and tile
  bounds logic.
- Do not implement built-in polar, irregular matrix, sheet-based, or other
  specialized tiling systems for v1.0. The goal is to make those possible
  through stable interfaces, not to own every provider-specific scheme.

Later raster source candidates:

- WMTS.
- Bing/QuadKey.
- ArcGIS tiled REST.
- Custom URL builder.
- Additional local/offline tile providers beyond MBTiles.

### 2. Optimize vector pipeline

The vector renderer should be optimized around layers, viewport visibility, and
style batches rather than treating every feature as a fully independent render
unit.

Current state:

- `FeatureListSource` already uses `RBush` for viewport culling.
- `CommandBuilder` also performs a second bbox visibility check.
- `VectorRenderPipeline` already builds render data per `VectorLayer`.
- Styles currently come primarily from `Feature.style`, with geometry defaults
  as fallback.
- Render commands are still a flat per-layer list, not grouped by geometry type
  or resolved style.

Target public shape:

```kotlin
featureLayer("parcels", parcels) {
    style = polygonStyle {
        fill(0x3343A047)
        stroke(0xFF2E7D32, width = 2.0)
    }

    renderMode = cachedBitmap()
}
```

Feature-level style remains available as an override:

```kotlin
feature(
    key = "selected-parcel",
    geometry = selectedParcel,
    style = selectedParcelStyle,
)
```

Style resolution order:

1. `feature.style`, when set.
2. `layer.style`, when set.
3. geometry default style.

Pipeline changes:

- Add layer-level style to `VectorLayer` / `FeatureLayer` and the DSL.
- Keep feature-level style as an optional override.
- Keep viewport culling in `FeatureSource`; make the contract explicit.
- Avoid duplicate expensive culling where possible, while keeping a cheap final
  guard before command generation.
- Replace or supplement flat `RenderCommand` lists with batched render data.
- Batch by layer, geometry type, and resolved style.
- Keep labels as their own batch/cache path.
- Ensure cached bitmap rendering consumes the same batched model as immediate
  rendering.
- Keep custom backends in mind: the batch model should not be Compose Canvas
  specific.

Target internal shape:

```kotlin
VectorRenderLayer(
    id = "parcels",
    batches = listOf(
        PolygonBatch(style = parcelStyle, polygons = polygons),
        LineBatch(style = borderStyle, lines = lines),
        LabelBatch(labels = labels),
    ),
)
```

Expected benefits:

- Less repeated style resolution.
- Lower per-feature draw overhead.
- Cleaner cached bitmap rendering.
- Clearer path to alternate render backends.
- Better performance for large layers with many features sharing one style.

### 3. Stabilize naming

- Add `TiloMap` as the preferred public composable. Done.
- Remove the old `Map` composable name instead of deprecating it. Done.
- Rename core `Map` to `MapState` or introduce a wrapper state type. Started with `MapState` typealias.
- Remove aliasing needs from examples. Started in `composeApp`.

### 4. Introduce remembered state APIs

- Add `rememberMapCameraState`. Done.
- Add `rememberDrawState`. Done.
- Move viewport management fully behind the map composable. Started.
- Make examples use remembered state helpers. Started in `composeApp`.

### 5. Add high-level layer builders

- `rememberWMSLayer(...)` + `wmsTileLayer(state)` for capabilities-loaded WMS layers. Done.
- `rasterLayer(layer)` for pre-built raster tile layers. Done.
- `tileLayer(layer)` and `tileLayer(state)` stay as advanced aliases.
- `featureLayer(id, features) { ... }`. Done.
- `drawLayer(drawState)` is provided by the draw plugin. Done.
- Keep `+layer` for advanced usage.

### 6. Clean feature and style ergonomics

- Add style builders. Started with `pointStyle`, `lineStyle`, `polygonStyle`, `fill`, `stroke`, `dash`, `hatch`, and `dots`.
- Add feature builders that cover point, line, polygon, multipoint, multiline, multipolygon.
- Keep GeoCore data classes as the serialization-friendly model.

### 7. Make invalidation implicit

- Ensure layer list changes invalidate render state.
- Ensure feature content changes invalidate vector cache.
- Ensure draw state changes invalidate immediately.
- Keep `invalidationKey` only for custom advanced cases.

### 8. Integrate spatial indexing

- Add optional indexed feature sources.
- Use spatial index for visible feature queries.
- Keep list-backed feature source for small/simple data.
- Benchmark both paths and document expected tradeoffs.

### 9. Add selection, camera control, and interaction routing

Map interaction should become a first-class part of the public API instead of
being hidden inside renderer gesture code. Selection belongs to the core map
experience; editing belongs in a plugin, similar to drawing.

Selection target shape:

```kotlin
val selectionState = rememberSelectionState()

TiloMap(
    cameraState = cameraState,
    selectionState = selectionState,
    onFeatureClick = { feature, layer ->
        selectionState.select(layer.id, feature.key)
    },
    onMapClick = {
        selectionState.clear()
    },
)
```

Selection scope:

- Add feature picking / hit testing from screen tap to vector feature.
- Query candidates through visible/indexed feature sources.
- Support geometry-aware hit tests: point distance, line distance, polygon
  contains/edge tolerance.
- Resolve competing hits by layer z-index, feature order, and distance.
- Add `selectedStyle` / selection style modifier at layer level.
- Keep selection state app-owned and observable.
- Make selection usable by callouts, highlights, edit plugins, and custom app
  logic.

Camera control target shape:

```kotlin
cameraState.animateTo(
    center = Point(...),
    zoom = 14.0,
    rotation = 30.0,
)

cameraState.fitBounds(bounds)
cameraState.zoomIn()
cameraState.zoomOut()
cameraState.rotateTo(0.0)
```

Camera control scope:

- Add programmatic center/zoom changes.
- Add animated camera transitions.
- Add `fitBounds`.
- Add rotation/bearing support to camera state and renderer transforms.
- Add public helpers for zoom in/out and rotate/reset rotation.
- Keep camera control independent from layer rendering.

Event routing scope:

- Define how map gestures are routed between UI overlays, edit plugin, draw
  plugin, selection, and default map pan/zoom/rotate gestures.
- Allow handlers/plugins to consume events so lower-priority handlers do not
  also react.
- Prioritize active edit/draw interactions over selection.
- Prioritize selection over default map tap.
- Ensure overlay UI controls do not leak taps/drags to the map underneath.
- Support app-owned custom handlers for tap, long press, drag, and gesture
  interception.

Editing plugin target:

- Build `Edit` as a plugin like `Draw`.
- Reuse selection and hit testing as inputs.
- Support vertex handles, moving vertices, inserting/deleting vertices, moving
  whole geometries, save/cancel, and undo/redo.
- Keep persistence app-owned through callbacks.

### 10. Polish drawing as a plugin

- Add default `DrawControls`.
- Keep controls replaceable.
- Make `DrawStyle` configurable.
- Ensure save flow is user-owned.
- Add a clean drawing example.

### 11. Stabilize layer lifecycle and metadata

Layers need a common lifecycle model before the showcase grows into a real app
with many raster, vector, draw, edit, and custom layers.

Scope:

- Add `visible` as a first-class layer concern.
- Add `minZoom` / `maxZoom` visibility gates.
- Add `opacity` for raster and vector layers.
- Add layer groups for UI organization and bulk toggling.
- Add load/render priority for tile queues and expensive vector layers.
- Add attribution and debug metadata to layer definitions.
- Add dispose hooks for custom layers that own caches, file handles, database
  connections, or native resources.
- Ensure changing layer lifecycle properties invalidates rendering immediately.

### 12. Add loading, error, and diagnostics state

WMS capabilities already expose a small loading/error state, but v1 needs a
general model for raster and vector layers.

Scope:

- Track loading state per layer.
- Track tile request failures and retry state.
- Track feature source failures and empty results.
- Expose retry hooks without forcing one retry policy on every provider.
- Support offline/empty states for local providers such as MBTiles.
- Surface layer diagnostics in a structured way for UI and debug tooling.
- Keep user-facing errors separate from low-level debug details such as failed
  URLs or raw exceptions.

### 13. Add attribution and legal metadata

Raster basemaps and public data sources usually require visible attribution.
This should be hard to forget.

Scope:

- Add attribution metadata per layer.
- Support plain text, links, and optional logo/image references.
- Provide a default attribution overlay component.
- Allow apps to render attribution themselves when they need custom UI.
- Support multiple visible layer attributions at once.
- Include license/source metadata for docs and debug panels.
- Add examples for OSM, CUZK, and offline/custom data sources.

### 14. Improve label placement

Label bitmap caching exists, but placement needs its own roadmap because labels
can dominate perceived map quality and performance.

Scope:

- Add collision detection.
- Add label priority.
- Add min/max zoom visibility for labels.
- Add label anchors, offsets, and alignment options.
- Keep label rendering/cache separate from geometry batches.
- Support selected/hovered feature labels with higher priority.
- Consider line-following labels later, after point/polygon labels are stable.

### 15. Add performance and debug tooling

Map rendering needs observability while we tune raster, vector, labels, and
interaction.

Scope:

- Add optional debug overlay.
- Show FPS or frame time.
- Show visible tile count, tile zoom, queued requests, cache hit/miss, and
  failed tile count.
- Show visible feature count per layer.
- Show label count and collision/drop count.
- Show current projection, center, zoom, rotation, and viewport.
- Toggle debug drawing for tile bounds, feature bounds, viewport query bounds,
  and selected/hit-test candidates.
- Keep debug tooling opt-in and out of the normal public happy path.

### 16. Expand renderer testing strategy

The renderer needs more than unit tests once batching, selection, labels, and
camera rotation are involved.

Scope:

- Add deterministic fake tile provider.
- Add deterministic fake feature source.
- Add hit-testing tests for points, lines, polygons, and overlapping layers.
- Add tile-grid tests for Web Mercator, S-JTSK/Krovak, and custom grids.
- Add vector batching tests for style resolution and grouping.
- Add screenshot/golden tests for representative map scenes.
- Add label placement tests once collision detection exists.
- Keep performance tests separate from correctness tests.

### 17. Plan accessibility support

Accessibility is not the first implementation priority, but the API should not
make it impossible.

Scope:

- Add semantic descriptions for selected features and callouts.
- Make default controls expose accessible labels.
- Keep edit/draw handles large enough for touch interaction.
- Plan keyboard controls for zoom, pan, rotate, select, and cancel.
- Expose selected feature state to assistive UI.
- Document how apps can provide domain-specific accessibility text.

### 18. Release readiness

Before v1.0, the project needs a clear release boundary. This is not a map
feature, but it is required for the library to feel stable and safe to adopt.

Public API boundary:

- Define which packages are public and stable.
- Keep `tilo.compose.dsl.*` as the primary user-facing API.
- Keep `tilo.compose.core.*` / GeoCore as stable platform-agnostic models and
  contracts.
- Keep `tilo.compose.render.*` mostly internal or advanced.
- Keep plugin packages such as `tilo.compose.draw.*` explicit and documented.
- Add annotations such as `@TiloExperimentalApi` and `@TiloInternalApi` if we
  need to expose unstable escape hatches.
- Audit imports in README, docs, samples, and showcase so they only use intended
  public APIs.

Versioning and migration policy:

- Use semantic versioning after v1.0.
- Define what counts as a breaking change.
- Mark experimental APIs before publishing them broadly.
- Provide migration notes for intentional breaking changes.
- Keep release notes/changelog as part of the release process.

Publishing readiness:

- Define Maven coordinates for Tilo.Compose, GeoCore, SpatialIndex, and plugins.
- Configure sources and documentation artifacts.
- Include license and project metadata in published artifacts.
- Add CI release workflow for tags.
- Decide whether first releases go to Maven Central, GitHub Packages, or both.
- Add README badges once CI and publishing are stable.

Projection and transform boundary:

- Keep projection definitions and transform contracts stable.
- Do not hard-wire licensed transform implementations into GeoCore or public
  APIs that should stay platform-agnostic.
- Make transform implementation injection clear in docs and examples.
- Document how apps provide transforms for CRS beyond built-in helpers.

Release scope guard:

- Treat the roadmap above as the v1 scope.
- Move additional polish ideas into post-v1 unless they unblock stability,
  safety, or public API coherence.

### 19. Prepare v1 documentation

- Update README around the new compose-first API.
- Add examples for raster, vector, styling, labels, layer switching, drawing, and cached vectors.
- Document advanced rendering strategies separately.

### 20. Nice-to-have: animation hooks

Animations are desirable for polish, especially around drawing, editing,
selection, and camera movement, but they should not complicate the v1 core data
model.

Potential use cases:

- Draft line/polygon preview follows the pointer smoothly while drawing.
- Vertex handles pulse or fade in/out during edit mode.
- Selected feature highlight fades in/out.
- Drawing points can have lightweight drop/confirm feedback.
- Style transitions can animate when features become selected/unselected.
- Camera transitions can animate center, zoom, and rotation.

Design direction:

- Keep serializable GeoCore style data static.
- Treat animations as Compose/render/plugin runtime behavior.
- Add an animation clock or frame time to render-time style resolution.
- Allow plugins or layers to request another render frame while animation is
  active.
- Make time-dependent invalidation explicit so static layers stay cheap.
- Keep animated draw/edit overlays separate from the stable feature source
  model.

This is not required for the first stable API pass. The important v1 decision is
to leave room for time-dependent layers and render invalidation without forcing
animation concepts into every style and feature.

## Open Decisions

- Should the public camera state be called `MapState`, `MapCameraState`, or `CameraState`?
- `TiloMap` and high-level helpers live in `tilo.compose.dsl`; renderer internals stay in `tilo.compose.render`.
- Projection helpers should stay as human-readable functions (`wgs84()`, `webMercator()`, `sjtsk()`) while precise EPSG objects remain available in GeoCore for advanced use.
- Should layer DSL builders return concrete layer objects or register directly into the map builder?
- How much of style builder API should use `Double` vs Compose units such as `Dp`?

## Near-term Recommendation

Start with the smallest API improvement that unlocks better examples:

1. Add `TiloMap` as the public entrypoint.
2. Add `rememberMapCameraState`.
3. Add `rememberDrawState`.
4. Convert the current example to the new API.
5. Add `featureLayer` and `tileLayer` DSL helpers.

After that, the rest of the cleanup becomes much easier to evaluate from real usage.
