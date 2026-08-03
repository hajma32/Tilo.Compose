<img src="docs/images/tilo-compose-logo.svg" alt="Tilo.Compose" width="420">

Compose-first Kotlin Multiplatform maps and GIS toolkit.

Tilo.Compose is an **open-source Kotlin Multiplatform toolkit** for building
modern **map and GIS applications with Compose**. It scales from a simple
OpenStreetMap view with a few markers to complex GIS workflows with multiple
coordinate systems.

The framework provides a **declarative Compose API**, tiled raster layers (WMS,
XYZ, and custom tile stores), **projection-aware rendering**, vector geometry
and styling, label placement, feature selection, interactive drawing, spatial
indexing, and extension points for custom data sources and coordinate
transformations.

> ⚠️ Tilo is currently in alpha. Public APIs may change before 1.0.

## Showcase

The `Tilo.Samples` Android app exercises the public API with real maps: a minimal
OpenStreetMap layer, feature selection and app-owned callouts, interactive
drawing, and live ČÚZK ortofoto rendered directly in S-JTSK (`EPSG:5514`).

<table width="100%">
  <tr>
    <td width="20%"><img src="docs/images/showcase-samples-osm.png" alt="Tilo.Samples minimal OpenStreetMap XYZ layer" width="100%"></td>
    <td width="20%"><img src="docs/images/showcase-samples-geometries.png" alt="Tilo.Samples points, line, polygon, labels, and selected feature styling" width="100%"></td>
    <td width="20%"><img src="docs/images/showcase-samples-callout.png" alt="Tilo.Samples feature selection with an app-owned Compose callout" width="100%"></td>
    <td width="20%"><img src="docs/images/showcase-samples-drawing.png" alt="Tilo.Samples polygon drawing with undo, redo, clear, and save controls" width="100%"></td>
    <td width="20%"><img src="docs/images/showcase-samples-non-mercator.png" alt="Tilo.Samples live ČÚZK ortofoto rendered in S-JTSK EPSG:5514" width="100%"></td>
  </tr>
</table>

## Installation

```kotlin
repositories {
    google()
    mavenCentral()
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("eu.tilomaps:tilo-compose:0.1.4-alpha10")
            // Optional drawing plugin:
            implementation("eu.tilomaps:tilo-compose-draw:0.1.4-alpha10")
        }
    }
}
```

## Quick Example

```kotlin
@OptIn(ExperimentalTiloApi::class)
@Composable
fun MapScreen() {
    val brno = Point(16.6068, 49.1951)
    val cameraState = rememberMapCameraState(
        initialCenter = Wgs84ToEpsg5514Transformation.sourceToTarget(brno),
        initialZoom = 11.5,
        projection = sjtsk(),
    )

    val places = remember {
        features {
            point("brno", brno) {
                label("Brno", style = largeLabelStyle())
                style = pointStyle {
                    size = 14.dp
                    fill(0xFF43A047)
                    stroke(0xFF263238, width = 2.dp)
                }
            }
        }
    }

    TiloMap(
        cameraState = cameraState,
        modifier = Modifier.fillMaxSize(),
    ) {
        wmsTileLayer(
            id = "cuzk-ortofoto",
            capabilitiesUrl = "https://ags.cuzk.gov.cz/arcgis1/services/ORTOFOTO/MapServer/WMSServer",
            layerName = "0",
            projection = sjtsk(),
            format = "image/jpeg",
        )

        featureLayer("places", places) {
            projection = wgs84()
            renderMode = cachedBitmap()
        }
    }
}
```

See the [web API reference](https://tilomaps.eu/reference.html) for the current
public API shape, including map layers, raster sources, features, styles,
labels, selection, default UI overlays, drawing, tile grids, and projections.

The remaining alpha releases will focus on API stability and making Tilo
Compose pleasant and intuitive to use.

## License

MIT License. See [LICENSE](LICENSE).

The GeoCore module is part of this repository. SpatialIndex is also MIT
licensed in its own repository.
The Android `core` artifact uses Proj4J and `proj4j-epsg`; the iOS artifact
bundles [PROJ](https://proj.org/). Both platform distributions include their
applicable software licenses and the full
[EPSG Dataset Terms of Use](https://epsg.org/terms-of-use.html). See
[third-party notices](THIRD_PARTY_NOTICES.md).
