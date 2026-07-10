# Tilo.Compose DSL

This folder documents the user-facing Compose DSL exposed from
`tilo.compose.dsl`.

The DSL is intentionally small and unstable for now. Tilo.Compose is still a
work in progress, so names and behavior may change before a public 1.0 release.

## Start Here

- [Getting Started](getting-started.md)
- [Map And Layers](map-and-layers.md)
- [Features And Styles](features-and-styles.md)
- [Projections And CRS](projections-and-crs.md)
- [WMS Layers](wms-layers.md)

## Import Shape

Most application code should start with:

```kotlin
import tilo.compose.dsl.*
```

Optional plugins, such as drawing, are imported separately:

```kotlin
import tilo.compose.draw.*
```

