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

### 1. Stabilize naming

- Add `TiloMap` as the preferred public composable. Done.
- Remove the old `Map` composable name instead of deprecating it. Done.
- Rename core `Map` to `MapState` or introduce a wrapper state type. Started with `MapState` typealias.
- Remove aliasing needs from examples. Started in `composeApp`.

### 2. Introduce remembered state APIs

- Add `rememberMapCameraState`. Done.
- Add `rememberDrawState`. Done.
- Move viewport management fully behind the map composable. Started.
- Make examples use remembered state helpers. Started in `composeApp`.

### 3. Add high-level layer builders

- `rememberWMSLayer(...)` + `wmsTileLayer(state)` for capabilities-loaded WMS layers. Done.
- `rasterLayer(layer)` for pre-built raster tile layers. Done.
- `tileLayer(layer)` and `tileLayer(state)` stay as advanced aliases.
- `featureLayer(id, features) { ... }`. Done.
- `drawLayer(drawState)` is provided by the draw plugin. Done.
- Keep `+layer` for advanced usage.

### 4. Clean feature and style ergonomics

- Add style builders. Started with `pointStyle`, `lineStyle`, `polygonStyle`, `fill`, `stroke`, `dash`, `hatch`, and `dots`.
- Add feature builders that cover point, line, polygon, multipoint, multiline, multipolygon.
- Keep GeoCore data classes as the serialization-friendly model.

### 5. Make invalidation implicit

- Ensure layer list changes invalidate render state.
- Ensure feature content changes invalidate vector cache.
- Ensure draw state changes invalidate immediately.
- Keep `invalidationKey` only for custom advanced cases.

### 6. Integrate spatial indexing

- Add optional indexed feature sources.
- Use spatial index for visible feature queries.
- Keep list-backed feature source for small/simple data.
- Benchmark both paths and document expected tradeoffs.

### 7. Polish drawing as a plugin

- Add default `DrawControls`.
- Keep controls replaceable.
- Make `DrawStyle` configurable.
- Ensure save flow is user-owned.
- Add a clean drawing example.

### 8. Prepare v1 documentation

- Update README around the new compose-first API.
- Add examples for raster, vector, styling, labels, layer switching, drawing, and cached vectors.
- Document advanced rendering strategies separately.

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
