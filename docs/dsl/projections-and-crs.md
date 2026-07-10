# Projections And CRS

Tilo.Compose is built for maps that need more than WGS84 and Web Mercator.

The DSL provides human-readable projection helpers:

```kotlin
wgs84()
webMercator()
sjtsk()
identityProjection()
```

Current helpers map to:

| Helper | CRS |
| --- | --- |
| `wgs84()` | EPSG:4326 |
| `webMercator()` | EPSG:3857 |
| `sjtsk()` | EPSG:5514, S-JTSK / Krovak East North |
| `identityProjection()` | Cartesian identity coordinates |

## Map Projection

The map camera projection defines the coordinate system of the map state:

```kotlin
val cameraState = rememberMapCameraState(
    center = Point(-650_000.0, -1_100_000.0),
    zoom = 11.5,
    projection = sjtsk(),
)
```

## Layer Projection

Feature layers can declare their own projection:

```kotlin
featureLayer("places", places) {
    projection = wgs84()
}
```

If feature coordinates differ from the map projection, Tilo.Compose asks the
map transformation registry to transform them before rendering.

## Transformations

GeoCore owns the transformation contracts and registry. Concrete CRS transforms
are injected by runtime or application code, especially when licensed projection
engines are involved.

This keeps GeoCore platform-agnostic and avoids hard-wiring concrete transform
implementations into the data model.

