# Features And Styles

Use `features { ... }` to build in-memory vector features:

```kotlin
val places = features {
    point("brno", 16.6068, 49.1951) {
        label("Brno", style = largeLabelStyle())
        style = pointStyle {
            size = 14.dp
            fill(0xFF43A047)
            stroke(0xFF263238, width = 2.dp)
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
        label("Inspection route")
        style = lineStyle {
            casing(0xFFFFFFFF, width = 7.dp)
            stroke(0xFF1E88E5, width = 4.dp) {
                dash(12.dp, 8.dp)
            }
        }
    }

    polygon("work-area", listOf(workAreaRing)) {
        label("Work area", priority = 10)
        style = polygonStyle {
            fill(0x3343A047)
            casing(0xFFFFFFFF, width = 6.dp)
            stroke(0xFF2E7D32, width = 3.dp)
        }
    }
}
```

## Style Builders

Use:

- `pointStyle { ... }`
- `lineStyle { ... }`
- `polygonStyle { ... }`
- `labelStyle { ... }`
- `smallLabelStyle { ... }`
- `mediumLabelStyle { ... }`
- `largeLabelStyle { ... }`
- `extraLargeLabelStyle { ... }`
- `featureLayerStyle { ... }`

Common style operations:

```kotlin
polygonStyle {
    fill(0x3343A047)
    casing(0xFFFFFFFF, width = 6.dp)
    stroke(0xFF2E7D32, width = 3.dp) {
        dash(12.dp, 8.dp)
    }
}
```

## Layer Styles And Overrides

Use `featureLayerStyle { ... }` when most features in a layer should share the
same styling:

```kotlin
featureLayer("places", places) {
    style = featureLayerStyle {
        point {
            size = 18.dp
            fill(0xFF2563EB)
            stroke(0xFFFFFFFF, width = 3.dp)
        }
        line {
            casing(0xFFFFFFFF, width = 7.dp)
            stroke(0xFF2563EB, width = 4.dp)
        }
        polygon {
            fill(0x332563EB)
            casing(0xFFFFFFFF, width = 7.dp)
            stroke(0xFF2563EB, width = 4.dp)
        }
        label(mediumLabelStyle())
        selectedPoint {
            size = 24.dp
            fill(0xFFFFD54F)
            stroke(0xFF111827, width = 4.dp)
        }
        selectedLabel(largeLabelStyle())
    }
}
```

Individual features can override geometry and label styles:

```kotlin
point("praha", 14.4378, 50.0755) {
    label(
        text = "Praha",
        priority = 100,
        style = extraLargeLabelStyle(),
    )
    selectedStyle = pointStyle {
        size = 26.dp
        fill(0xFFFFD54F)
        stroke(0xFF111827, width = 4.dp)
    }
}
```

## Label Styles

Labels support size, color, font weight, italic text, halo, background, bitmap
padding, vertical offset, selected styles, and collision priority.

```kotlin
line("river", riverPoints) {
    labelStyle = smallLabelStyle {
        color(0xFF2563EB)
        fontStyle = LabelFontStyle.Italic
        halo(width = 3.dp)
        offsetY(-2.dp)
    }
    label = "Vltava"
}

line("highway", highwayPoints) {
    label(
        text = "D1",
        priority = 50,
        style = smallLabelStyle {
            color(0xFFFFFFFF)
            noHalo()
            background(
                color = 0xFFE53935,
                cornerRadius = 4.dp,
                paddingHorizontal = 6.dp,
                paddingVertical = 2.dp,
            )
            offsetY(4.dp)
        },
    )
}
```

When no explicit `labelPriority` is set, larger labels win collisions before
smaller labels. Selected labels are kept ahead of non-selected labels.

Pattern fills are available for polygons:

```kotlin
polygonStyle {
    fill(0x3326A69A) {
        hatch(
            angleDegrees = 35.0,
            spacing = 10.dp,
            strokeColor = 0xFF00796B,
            strokeWidth = 1.dp,
        )
    }
}
```

Dots are available too:

```kotlin
polygonStyle {
    fill(0x33AB47BC) {
        dots(
            spacing = 12.dp,
            radius = 2.dp,
            color = 0xFF8E24AA,
        )
    }
}
```
