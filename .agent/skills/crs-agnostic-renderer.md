# SKILL: CRS-agnostic renderer

The render module (`eu.tilo.compose.render`) must not contain any CRS-specific logic.

- **Forbidden:** importing or branching on `Wgs84WebMercatorProjection`, `IdentityProjection`, or any concrete CRS type.
- **Forbidden:** implementing projection math (Mercator, lon/lat conversions) in the render layer.
- **Allowed:** calling only `Map.worldToScreen()` and `Map.screenToWorld()` — they abstract the CRS.

