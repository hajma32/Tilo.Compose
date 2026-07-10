# Features And Styles

Use `features { ... }` to build in-memory vector features:

```kotlin
val places = features {
    point("brno", 16.6068, 49.1951) {
        label = "Brno"
        style = pointStyle {
            size = 14.0
            fill(0xFF43A047)
            stroke(0xFF263238, width = 2.0)
        }
    }
}
```

Feature keys should be stable. They are used by rendering and caching code to
track feature identity.

## Geometry Builders

The DSL currently supports:

- `point`
- `multiPoint`
- `line`
- `lineString`
- `multiLine`
- `polygon`
- `multiPolygon`
- `feature` for an already constructed geometry

Example:

```kotlin
val overlays = features {
    line("inspection-route", routePoints) {
        label = "Inspection route"
        style = lineStyle {
            stroke(0xFF1E88E5, width = 4.0) {
                dash(12.0, 8.0)
            }
        }
    }

    polygon("work-area", listOf(workAreaRing)) {
        label = "Work area"
        style = polygonStyle {
            fill(0x3343A047)
            stroke(0xFF2E7D32, width = 2.5)
        }
    }
}
```

## Style Builders

Use:

- `pointStyle { ... }`
- `lineStyle { ... }`
- `polygonStyle { ... }`

Common style operations:

```kotlin
polygonStyle {
    fill(0x3343A047)
    stroke(0xFF2E7D32, width = 2.5) {
        dash(12.0, 8.0)
    }
}
```

Pattern fills are available for polygons:

```kotlin
polygonStyle {
    fill(0x3326A69A) {
        hatch(
            angleDegrees = 35.0,
            spacing = 10.0,
            strokeColor = 0xFF00796B,
            strokeWidth = 1.2,
        )
    }
}
```

