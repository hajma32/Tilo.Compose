# MBTiles Vector Loading (Android)

This project now supports loading vector tiles (`.pbf`) from an MBTiles file packaged in Android `res/raw`.

## Dataset location

- `composeApp/src/androidMain/res/raw/osm-2020-02-10-v3.11_czech-republic_brno.mbtiles`

## How it works

1. `MainActivity` initializes `AndroidAppContext`.
2. `AndroidPlatform` creates `AndroidMbtilesVectorLoader`.
3. `AndroidMbtilesVectorLoader`:
   - copies the MBTiles file from `res/raw` to app cache,
   - opens SQLite `tiles` table,
   - reads tile `tile_data` blobs for requested `z/x/y`,
   - decompresses gzip payload when needed,
   - parses MVT protobuf payload (`.pbf`) with in-project parser,
   - maps geometry to core `Feature` objects.
4. `App` exposes a test screen: `MBTiles Vector (.pbf)`.

## Try it

Open the app and select **MBTiles Vector (.pbf)** in the drawer.

## Notes

- Current implementation focuses on geometry rendering (Point, LineString, Polygon).
- Styling is simple and layer-name based.
- Attributes/tags are not yet mapped to labels.
- Implementation is Android-only for now; iOS returns an empty feature list.

