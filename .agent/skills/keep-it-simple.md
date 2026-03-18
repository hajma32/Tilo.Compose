# SKILL: Write as little code as possible

**Less code = less bugs, less maintenance, less confusion.**

## Rules

- **No unnecessary variables.** If a value is used once, inline it.
- **No helper math.** Before introducing a formula or a conversion, ask: does a simpler expression exist?
- **No defensive complexity.** Don't add abstractions, wrappers or intermediate types unless there is a concrete, present need.
- **No speculative code.** Don't write code for future use cases that don't exist yet.

## Examples

```kotlin
// ❌ WRONG — unnecessary variable
val dipWidth = viewport.width / viewport.pixelRatio
return (mapZoom + log2(tilesAcross * worldWidth / (nTilesX0 * dipWidth)))

// ✅ CORRECT — inlined
return (mapZoom + log2(tilesAcross * worldWidth / (nTilesX0 * (viewport.width / viewport.pixelRatio))))
```

```kotlin
// ❌ WRONG — roundabout math
val scale = 2.0.pow(zoom) * pixelRatio
val wx = (screen.x - width / 2.0) / scale + center.x

// ✅ CORRECT — one clean scale, same result
val scale = 2.0.pow(zoom)
val wx = (screen.x / pixelRatio - width / (2.0 * pixelRatio)) / scale + center.x
```

## When a variable IS justified

- It is used **more than once**.
- It has a non-obvious meaning that a name would clarify for the reader.
- Inlining it would make a line exceed ~120 characters.

