# 🗺️ Tilo.Compose

Tilo.Compose is an WORK IN PROGRESS full-featured Kotlin Multiplatform map framework for Android and iOS, built with a Compose-first mindset. You compose your map declaratively from building blocks (layers, features, controls) — you don't imperatively drive rendering. The goal is to provide a powerful, extensible foundation for real-world mapping apps: custom coordinate transforms, advanced drawing tools, snapping and offline tile support, and a plugin system so teams can add features without touching the core.

Why pick TiloCompose
- Compose-first: author maps the same way you write UI in Compose — declaratively and state-driven.
- Declarative: describe layers, features and behaviour; the runtime applies diffs and keeps the view consistent.
- Extensible & scalable: designed for larger projects — plugin-friendly, performant rendering, and offline-ready workflows.

Important notes
- Vector tiles are not planned for this project. Focus is on raster tiles, MBTiles-based offline sources and in-memory vector features (points, lines, polygons) rendered and styled by the framework.
- MBTiles support: first-class support for reading raster tiles and packaged resources from MBTiles files for offline or local use.
- Custom transforms: full support for custom coordinate transforms and projections (including national systems such as Křovák).
- Snapping & routing helpers: snapping to lines/paths and vertices, geometry snapping utilities and hooks for routing integration.

Core features (planned / target)
- Core engine: panning, zooming, rotating, high-quality transforms and projection helpers.
- Tile layers: raster tile support (HTTP tile sources, MBTiles, caching, LOD).
- Vector features: in-memory points, lines and polygons with styling, hit-testing and event handling.
- Labels: layout, simple collision avoidance and prioritized placement for map labels.
- Drawing & editing: interactive drawing tools, snapping to features/paths/points, undo/redo and editing UI primitives.
- Theming & styling: style-driven appearance, runtime theme switching and scalable style rules.
- Plugins: extension points for layers, tools, importers/exporters and custom behaviours.

Timeline
- [ ] Core engine (pan, zoom, rotate, transforms)
- [ ] Tile layers (HTTP + MBTiles)
- [ ] Vector features (in-memory)
- [ ] Labels
- [ ] Drawing & snapping
- [ ] Theming & plugins

Compose-first example

A tiny illustrative snippet showing the idea (pseudo-API):

```kotlin
@Composable
fun MyMap() {
  Map(
    center = LatLng(50.0755, 14.4378),
    zoom = 12f,
    projection = Projection.Krovak // example of a custom projection
  ) {
    RasterTileLayer(urlTemplate = "https://tile.example.com/{z}/{x}/{y}.png")

    // MBTiles as a local raster source
    RasterTileLayer(mbtiles = File("/data/maps/czech.mbtiles"))

    FeatureLayer(features = sampleFeatures) { feature ->
      when (feature.type) {
        FeatureType.Point -> PointStyle(color = Color.Red, radius = 6.dp)
        FeatureType.Line -> LineStyle(color = Color.Blue, width = 2.dp)
        FeatureType.Polygon -> FillStyle(fillColor = Color(0x803388CC))
      }
    }

    // drawing tool with snapping enabled
    DrawingTool(snapping = SnappingConfig(enabled = true, snapTo = listOf(SnapTarget.Vertex, SnapTarget.Edge)))

    MapControls()
  }
}
```
## Contributing

See `CONTRIBUTING.md` for contribution guidelines, commit message conventions and the PR checklist.

Learn more about Kotlin Multiplatform: https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html
