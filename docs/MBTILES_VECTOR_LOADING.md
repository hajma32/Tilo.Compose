# MBTiles Vector Loading

This project supports loading vector tiles (`.pbf`) from an MBTiles file through a shared Kotlin Multiplatform data pipeline.

## Dataset location

- Source dataset: `composeApp/src/androidMain/res/raw/brno.mbtiles`
- Android runtime copy: app database directory
- iOS runtime copy: app bundle -> cache directory

## How it works

1. `Platform.android.kt` / `Platform.ios.kt` create a platform-specific file provider and SQL driver factory.
2. `MbtilesVectorFeatureService` in `:data` wires the shared loading pipeline.
3. Shared `:data` layer:
   - opens the MBTiles database through SQLDelight,
   - reads `metadata` and `tiles` rows for requested `z/x/y`,
   - decompresses gzip payload when needed,
   - parses MVT protobuf payload (`.pbf`),
   - maps decoded vector tiles to core `Feature` objects.
4. `App` exposes a test screen: `MBTiles Vector (.pbf)`.

## Try it

Open the app and select **MBTiles Vector (.pbf)** in the drawer.

## Notes

- Current implementation focuses on geometry rendering (Point, LineString, Polygon).
- Styling is simple and layer-name based.
- The data/storage pipeline is shared between Android and iOS; only file access and SQL driver creation are platform-specific.

## Style config (map object)

Vector styles are configured by a map object in code (`VectorTileStyleConfigMap`).

- Top-level map key = one render layer / one batch (`water`, `roads`, ...).
- Top-level map insertion order = render order.
- Missing keys are not rendered (no implicit defaults).
- Each top-level layer contains sub-layers (e.g. `roads.primary`, `roads.secondary`).

The sample app config lives in `composeApp/src/commonMain/kotlin/eu/tilo/compose/MbtilesStyleConfig.kt`.
