# Tilo.Compose DSL

This folder documents the user-facing Compose DSL exposed from
`tilo.compose.dsl`.

The DSL is intentionally small and unstable for now. Tilo.Compose is still a
work in progress, so names and behavior may change before a public 1.0 release.

All public declarations in `tilo.compose.dsl` are marked with
`@ExperimentalTiloApi`. Applications can acknowledge the pre-release API
contract at the narrowest practical scope:

```kotlin
@OptIn(ExperimentalTiloApi::class)
@Composable
fun MapScreen() {
    // TiloMap, rememberMapCameraState, and layer/style DSL usage
}
```

The marker currently uses warning-level opt-in so early adopters receive a
visible compatibility warning without being blocked from compiling.

## Start Here

- [Getting Started](getting-started.md)
- [Map And Layers](map-and-layers.md)
- [Features And Styles](features-and-styles.md)
- [Projections And CRS](projections-and-crs.md)
- [WMS Layers](wms-layers.md)
- [Default UI](default-ui.md)
- [Drawing](drawing.md)

## Import Shape

Most application code should start with:

```kotlin
import tilo.compose.dsl.*
```

Optional plugins, such as drawing, are imported separately:

```kotlin
import tilo.compose.draw.*
```

Default map overlays live in the UI module:

```kotlin
import tilo.compose.ui.*
```

## Complete DSL Surface

The current public DSL is intentionally compact. This is the full high-level
surface as of now.

### Map

- `TiloMap(...)`
- `rememberMapCameraState(...)`
- `MapCameraState.center`
- `MapCameraState.zoom`
- `MapCameraState.projection`
- `MapCameraState.config`
- `MapCameraState.zoomIn(step)`
- `MapCameraState.zoomOut(step)`
- `MapCameraState.zoomBy(delta, focus)`
- `MapCameraState.animateZoomIn(step, focus, animationSpec)`
- `MapCameraState.animateZoomOut(step, focus, animationSpec)`
- `MapCameraState.animateZoomBy(delta, focus, animationSpec)`

`TiloMap` accepts:

- `modifier`
- `onTapWorld`
- `onFeatureSelect`
- `onRenderError`
- `selectedFeatures`
- `attributionContent`
- `scaleBarContent`
- `cameraControlsContent`
- `invalidationKey`
- `layers`

### Layers

Inside `TiloMap { ... }`:

- `wmsTileLayer(...)`
- `xyzTileLayer(...)`
- `tileStoreLayer(...)`
- `featureLayer(id, features, ...)`
- `featureLayer(id, features) { ... }`
- `rasterLayer(layer)`
- `tileLayer(layer)`
- `layer(layer)`
- `+layer`

Feature layer options:

- `zIndex`
- `projection`
- `renderMode`
- `style`

Render modes:

- `immediate()`
- `cachedBitmap(scale, paddingPx, invalidateOnZoomDelta)`

### Raster Helpers

- `rememberRasterLayerState()`
- `RasterLayerStatus`
- `webMercatorTileGrid()`
- `tileGrid(...)`
- `attribution(label, url)`

### Projection Helpers

- `wgs84()`
- `webMercator()`
- `sjtsk()`
- `epsg5514()`
- `identityProjection()`

### Features

- `features { ... }`
- `feature(key, geometry) { ... }`
- `point(key, x, y) { ... }`
- `point(key, point) { ... }`
- `multiPoint(key, points) { ... }`
- `line(key, points) { ... }`
- `lineString(key, points) { ... }`
- `multiLine(key, lines) { ... }`
- `polygon(key, rings) { ... }`
- `polygon(key, polygon) { ... }`
- `multiPolygon(key, polygons) { ... }`

Feature options:

- `label`
- `labelPriority`
- `style`
- `selectedStyle`
- `labelStyle`
- `selectedLabelStyle`
- `data`
- `label(text, priority, style, selectedStyle)`

### Styles

- `argb(value)`
- `pointStyle { ... }`
- `lineStyle { ... }`
- `polygonStyle { ... }`
- `labelStyle { ... }`
- `smallLabelStyle { ... }`
- `mediumLabelStyle { ... }`
- `largeLabelStyle { ... }`
- `extraLargeLabelStyle { ... }`
- `featureLayerStyle { ... }`

Style builders expose:

- feature layer: `point`, `line`, `polygon`, `label`, `selectedPoint`,
  `selectedLine`, `selectedPolygon`, `selectedLabel`
- point: `shape`, `size`, `icon`, `fill`, `noFill`, `stroke`, `noStroke`
- line: `casing`, `noCasing`, `stroke`
- polygon: `fill`, `noFill`, `casing`, `noCasing`, `stroke`, `noStroke`
- label: `color`, `fontSize`, `fontWeight`, `fontStyle`, `halo`,
  `noHalo`, `background`, `noBackground`, `bitmapPadding`, `offsetY`
- fill patterns: `hatch`, `dots`
- stroke options: `lineCap`, `lineJoin`, `dash`

### Default UI

From `tilo.compose.ui`:

- `defaultAttributionContent()`
- `defaultScaleBarContent()`
- `defaultZoomControlsContent()`
- `DefaultAttributionOverlay(...)`
- `DefaultScaleBar(...)`
- `DefaultZoomControls(...)`

### Drawing Plugin

From `tilo.compose.draw`:

- `rememberDrawState(...)`
- `createDrawState(...)`
- `drawLayer(state, ...)`
- `LayerSink.drawLayer(state, ...)`
- `DrawMode.Point`
- `DrawMode.Line`
- `DrawMode.Polygon`
- `DrawState.startDrawing()`
- `DrawState.stopDrawing(clearDraft)`
- `DrawState.toggleDrawing()`
- `DrawState.selectMode(mode)`
- `DrawState.onMapTap(point)`
- `DrawState.save()`
- `DrawState.clear()`
- `DrawState.undo()`
- `DrawState.redo()`
- `DrawStyle`
- `DefaultDrawStyle`
