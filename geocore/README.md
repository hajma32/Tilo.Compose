# Tilo Compose GeoCore

Platform-agnostic geospatial core for Tilo map libraries.

The `geocore` module contains the model and math pieces that do not depend on Compose,
Android, iOS, HTTP clients, image decoding, or licensed projection engines.

## Scope

- Geometry models: points, lines, polygons, multi-geometries, and bounding boxes.
- Feature models and in-memory feature sources.
- Map state and viewport math.
- Projection metadata.
- CRS transformation contracts and registry.
- Tile coordinates, tile bounds, tile grids, and tile request planning.
- Layer contracts for raster/vector data.
- In-memory spatial querying through
  [Tilo.SpatialIndex](https://github.com/hajma32/Tilo.SpatialIndex).

## Explicitly Out Of Scope

- Compose rendering.
- Android/iOS UI code.
- HTTP clients and tile byte fetching.
- Image decoding.
- Concrete licensed projection implementations such as proj4/proj4j bridges.
- Hard-wired default CRS transformations.

Applications and renderers should inject any concrete CRS transformations they
need through `MapConfig` / `TransformationRegistry`.

## Module Boundary

GeoCore answers questions like:

- What is the current map state?
- Which tiles cover the viewport?
- Which in-memory features can intersect the viewport?
- Which transformation contract should be used between two projections?

`Tilo.Compose` answers questions like:

- How are those tiles fetched and decoded on a platform?
- How are features and tiles drawn in Compose?
- How do gestures update map state?

## License

MIT License. See the repository [LICENSE](../LICENSE).
