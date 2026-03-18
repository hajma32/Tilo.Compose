# SKILL: This is a framework, not a one-off project

TiloCompose is a reusable mapping framework. Code written here will be used in many different apps, contexts and configurations — not just the one currently being built.

## Rules

- **Design for the general case.** Don't hardcode assumptions that only hold for the current use case.
- **No magic constants without a name and a reason.** Every number that affects behaviour must be a named parameter with a sensible default.
- **Public API must be stable and intentional.** Think before making something public — it is a contract. Prefer `internal` when in doubt.
- **No side-effects in unexpected places.** A method named `loadTiles` loads tiles. It does not mutate map state, reset caches or fire analytics.
- **Interfaces over concrete types in public API.** `TileLayer`, not `WMSTileLayer`, in signatures that don't need to know the difference.
- **Behaviour must be configurable, not hardcoded.** If something might reasonably vary between users of the framework, make it a parameter.

## Examples

```kotlin
// ❌ WRONG — hardcoded assumption that only one use case ever needs 3 tiles
val zoom = (mapZoom + log2(3.0 * worldWidth / (nTilesX0 * dipWidth))).roundToInt()

// ✅ CORRECT — configurable with a sensible default
fun zoomForViewport(mapZoom: Double, viewport: Viewport, tilesAcross: Int = 3): Int
```

```kotlin
// ❌ WRONG — one-off colour that leaks into the framework
val fill = Color(0xFF1E88E5)

// ✅ CORRECT — style comes from the caller, framework has no opinion on colours
val fill = command.style.fillColor?.toColor() ?: return
```

```kotlin
// ❌ WRONG — assumes WMS is always the tile source
class MapRenderer(val wmsLayer: WMSTileLayer)

// ✅ CORRECT — framework accepts any implementation
class MapRenderer(val tileLayer: TileLayer?)
```

## Checklist before committing

- Would this code work for a WMS layer, an XYZ layer, and a custom offline layer without changes?
- Would this code work for EPSG:4326 and any other CRS without changes?
- If a user of the framework wanted to change this behaviour, could they do so without editing framework source?

