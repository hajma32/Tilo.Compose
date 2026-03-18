# SKILL: Always check existing types in `core` before creating a new one

**Warning sign:** You are about to create a data class named `WorldBounds`, `Rect`, `Bounds`, `Box`, `Coordinate`, `Vec2`, `TileAddress`, or anything similar.

**Do this instead:**

1. Check `core/src/commonMain/kotlin/tilo/compose/core/geometry/` — canonical geometry types live here.
2. Check `core/src/commonMain/kotlin/tilo/compose/core/tile/` — tile types live here.
3. Check `core/src/commonMain/kotlin/tilo/compose/core/map/` — map types live here.

**Canonical types (always use these, never create a local copy):**

| Type | File | Use instead of |
|---|---|---|
| `BoundingBox` | `geometry/BoundingBox.kt` | `WorldBounds`, `Rect`, `Bounds`, `Box`, `AABB` |
| `Point` | `geometry/Point.kt` | `Vec2`, `Coordinate`, `LatLon`, `XY` |
| `Geometry` + subtypes | `geometry/Geometry.kt` + individual files | any local geometry classes |
| `TileCoordinate`, `TileBounds`, `TileRequest`, `Tile` | `tile/Tile.kt` | local tile address structs |
| `TileGrid` | `tile/TileGrid.kt` | local grid calculations |
| `Viewport` | `map/Viewport.kt` | local screen-size holders |

**If a canonical type is missing a property or method** (e.g. `BoundingBox.intersects()`), add it directly to the file in `core` — do not wrap it in a local type.

**Example of the mistake to avoid:**
```kotlin
// ❌ WRONG — WorldBounds is just BoundingBox under a different name
private data class WorldBounds(val minX: Double, val minY: Double, val maxX: Double, val maxY: Double)

// ✅ CORRECT — use BoundingBox from core
import tilo.compose.core.geometry.BoundingBox
```

