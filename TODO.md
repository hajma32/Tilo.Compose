# TODO

## Raster API

- Add injectable HTTP tile loading for `XYZTileSource` and `WMSTileSource`.
  This should cover tests, custom headers, auth, user-agent, retry policy, and
  rate limiting without making the happy-path DSL noisy.
- Handle or explicitly document WMS 1.3.0 axis-order behavior, especially
  `EPSG:4326` BBOX ordering.
- Design a future `TileMatrixSet` or equivalent escape hatch for non-regular
  raster grids such as polar, irregular matrix, or sheet-based tiling systems.
- Add a real `mbTilesLayer(...)` helper once platform SQLite access and metadata
  resolution exist. It should sit above `tileStoreLayer(...)`, not replace it.

## Default UI

- Add style objects for default overlays, starting with `DefaultScaleBarStyle`
  and `DefaultAttributionStyle`, so users can tune colors, padding, typography,
  halo, and shadow without copying the default composables.

## Vector Rendering

- Move symbol placement out of the Compose canvas backend once another backend
  exists. The renderer should provide a backend-agnostic placement pass for
  labels/symbols, with the backend responsible only for drawing accepted
  placements.
- Add a label metrics cache keyed like `LabelBitmapCache`, so collision layout
  can avoid repeated `TextMeasurer.measure(...)` calls during pan/zoom.

## iOS CRS

- Build or vendor a static `PROJ.xcframework` for `ios-arm64` and
  `ios-arm64-simulator`, then enable the native backend documented in
  `docs/ios-proj.md`.
- Bundle `proj.db` when we need arbitrary EPSG lookup beyond the built-in
  `EPSG:4326`, `EPSG:3857`, and `EPSG:5514` definitions.
- Add Android/iOS parity tests for `EPSG:4326 <-> EPSG:5514`.
