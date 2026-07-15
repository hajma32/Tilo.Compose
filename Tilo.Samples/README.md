# Tilo Samples

This app is a small, runnable guide to the public Tilo API. Each map scenario lives
in a file named after the concept it teaches:

1. `OpenStreetMapSample.kt` — the smallest useful map
2. `GeometriesSample.kt` — points, lines, polygons, labels, and selection
3. `CustomStylesSample.kt` — per-feature styles
4. `CalloutSample.kt` — feature hits connected to ordinary Compose UI
5. `NonMercatorSample.kt` — a live WMS map in EPSG:5514
6. `DrawingSample.kt` — drawing state, history, and saved features

Start with `OpenStreetMapSample.kt`, then follow the numbered list. Shared camera
and basemap setup is in `MapDefaults.kt`; the application shell is intentionally
kept separate from the examples.

Run the Android app with:

```shell
./gradlew :tilo-samples:installDebug
```
