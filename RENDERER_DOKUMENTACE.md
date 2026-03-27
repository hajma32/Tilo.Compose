# Soucasny renderer: jak funguje (stav k 2026-03)

 Tento dokument popisuje aktualni implementaci rendereru v projektu TiloCompose, tj. jak dnes probiha prevod `Feature -> RenderCommand -> Canvas` a jak funguje tile pipeline.
 Dokument je zaroven **prubezny changelog renderer architektury**. Kazda vyznamna zmena v renderu/projekcich/tile pipeline se zapisuje sem.

## Co renderer dela

Renderer je Compose komponenta `MapRenderer`, ktera:

1. prijima `MapState`, seznam `Feature` a volitelne tile vstupy,
2. prevadi feature na draw prikazy (`RenderCommand`) pres `CommandBuilder`,
3. diffuje scenu (`SceneDiff`) a drzi retained mapu prikazu,
4. vykresluje tile podklad + vektorove prikazy do Compose `Canvas`.

Hlavni soubory:

- `render/src/commonMain/kotlin/eu/tilo/compose/render/MapRenderer.kt`
- `render/src/commonMain/kotlin/eu/tilo/compose/render/CommandBuilder.kt`
- `render/src/commonMain/kotlin/eu/tilo/compose/render/SceneDiff.kt`
- `render/src/commonMain/kotlin/eu/tilo/compose/render/RenderCommand.kt`
- `render/src/commonMain/kotlin/eu/tilo/compose/render/ProjectedGeometry.kt`

Souvisejici tile cast:

- `core/src/commonMain/kotlin/tilo/compose/core/layers/TileLayer.kt`
- `core/src/commonMain/kotlin/tilo/compose/core/layers/impl/SimpleTileLayer.kt`
- `core/src/commonMain/kotlin/tilo/compose/core/tile/source/WMSSource.kt`

## Datovy tok renderu

### 1) Build commandu

V `MapRenderer` se pri zmene vstupu vola:

- `CommandBuilder.build(mapState, features)`
- vznikne `List<RenderCommand>`
- seznam se mapuje na `id -> command`

`RenderCommand` typy:

- `RenderPoint`
- `RenderLineString`
- `RenderPolygon`
- `RenderLabel`

### 2) Scene diff a retained stav

`SceneDiff.diffMaps(previous, next)` vytvori operace:

- `Add`
- `Update`
- `Remove`

`SceneDiff.apply(...)` vrati novou retained mapu prikazu. Aktualni kresleni v Canvas jede nad retained mapou, ne primo nad puvodnim seznamem feature.

### 3) Vykresleni do Canvas

Poradi vykreslovani v `MapRenderer`:

1. tile podklad (`drawTiles(...)`)
2. retained commandy (`RenderPoint/Line/Polygon/Label`)

`RenderLabel` se vykresluje jako bitmap text s halo (bily obrys) kvuli citelnosti.

## Jak funguje `CommandBuilder`

`CommandBuilder` ma dve cesty:

1. **Projected path (fast path)** pro `Wgs84WebMercatorCoordinateSystem`
2. **Fallback path** pro ostatni `CoordinateSystem`

### Projected path

- geometrii promita do normalizovaneho Mercator prostoru (`ProjectedGeometry`)
- cacheuje projekci i bounds (`projectedGeometryCache`, `geometryBoundsCache`)
- na obrazovku prevadi affine transformaci (`ScreenTransform`)

### Fallback path

- pouziva `mapState.worldToScreen(...)` po bodech
- bez mezivrstvy `ProjectedGeometry`

### Culling

Pred prevodem do commandu probiha hruby test pruniku feature bounds s viewport bounds (`visibleWorldBounds` + `intersects`).

## Labely

- Label je oddeleny command `RenderLabel` (text + anchor)
- Anchor je dnes stred bboxu geometrie (`projectedAnchor` nebo `labelAnchorWorld`)
- Text se rasterizuje do bitmapy a cacheuje (`labelBitmapCache`)

## Transparentni body (aktualni chovani)

Aby se nesnizoval vykon u dat, kde chceme jen popisky, jsou transparentni point markery vyrizeny na dvou mistech:

1. `CommandBuilder` transparentni pointy vubec negeneruje (`emptyList()`)
2. `MapRenderer` ma jeste runtime guard v `RenderPoint` vetvi

Tj. pro style s `fillColor == 0x00000000L` se marker nekresli.

## Tile pipeline

`MapRenderer` ma samostatnou coroutine pipeline pro tile:

- debounce `delay(80)` proti zahlceni pri gestech,
- vypocet `zoomLevel` (`computeRenderTileZoom`) a `requestedTileCount` (`computeRequestedTileCount`),
- request key (`zoom/tileX/tileY/viewport/requestedTileCount`) proti duplicitnim requestum,
- fetch na `Dispatchers.Default`.

Scenare:

- je-li predan `tileLayer`, renderer vola `tileLayer.buildRequests(...)` a pak `tileLayer.source.getTiles(requests)`
- je-li predan jen `WMSSource`, renderer vraci prazdno (WMSSource sam neplanuje grid)
- je-li predan obecny `Source`, vola `source.getTiles(zoomLevel, viewport, tileCount)`

`drawTiles(...)`:

- prepocita tile pozice do screen prostoru,
- pouzije `tileBitmapCache` (key `z/x/y`),
- pokud bitmapa neni, vykresli fallback obdelnik.

## Interakce a state

`MapRenderer` obsluhuje gesta:

- pan: `mapState.panBy(...)`
- zoom: `mapState.zoomBy(...)` s log2 prevodem gesta

Na `onSizeChanged` aktualizuje:

- `mapState.viewport.width/height`
- `mapState.viewport.pixelRatio = LocalDensity.current.density`

Zmena velikosti/gest zvysuje `stateVersion`, cimz se obnovi relevantni `LaunchedEffect` vetve.

## Vykonove optimalizace, ktere uz tam jsou

- retained scene + diff (`SceneDiff`)
- culling pres world bounds
- projected geometry cache + bounds cache
- tile request key (deduplikace)
- debounce tile stahovani
- cache tile bitmap
- cache label bitmap
- skip transparent point markeru

## Zname limity aktualni implementace

1. `MapRenderer` je stale "god object" (gesta, tile planning, draw, cache, label raster v jednom souboru).
2. Label placement je jednoduche (stred bboxu), bez kolizniho managementu.
3. `SceneDiff` pouziva pouze `id`+`equals`, bez jemnejsi invalidace po vrstvach.
4. Tile pipeline nema formalni scheduler/frontu priorit; je to jednoducha coroutine logika.
5. `WMSSource` nejde pouzit samostatne bez `TileLayer` planneru.

## Migrace: jedna mapa = jedna projekce (krok 1)

Aktualni rozhodnuti architektury:

- jedna `MapState` = jedna **map projection**,
- vrstvy mohou mit vlastni `sourceCrs`,
- data vrstvy se maji pred renderem transformovat do projekce mapy.

Co je uz zavedeno:
 
 - `MapState` pouziva `projection` jako jedine API pro projekci mapy,
 - puvodni `coordSys` alias byl odstraneny (zadna zpetna kompatibilita),
 - `MapState` nese `MapConfig`, ze ktereho renderer taha dostupne transformace,
 - `MapConfig` je jednotna konfigurace mapy (zoom limity + transformace),
 - samostatny `MapSettings` byl odstraneny,
 - `CommandBuilder` rozhoduje fast path podle `mapState.projection`,
 - `CoordinateSystem` je pouze metadata CRS (`id`),
 - world/screen prevod je presunut do `Viewport` (`worldToScreen(...)`, `screenToWorld(...)`),
 - `Viewport` je ciste cartesian (kamera + zoom + velikost viewportu),
 - transformace mezi CRS musi probehnout pred vstupem dat do viewport prevodu (`Transformation` / `MapConfig`),
 - `Layer` ma metadata `sourceCrs` (default `null`),
 - `TileLayer` publikuje `sourceCrs` ze sveho `source`,
 - `WMSSource` publikuje `sourceCrs`/`sourceCrsParameterName`.
 - `TileLayer` ma `addressingStrategy` enum (bez injektovanych planner trid),
 - sdilena tile math logika je presunuta do `core.tile.utils` (`TilePlanner`, `TileRequestFactory`),
 - WebMercator tile index/BBOX matika je v `core.tile.utils.WebMercatorTileMath`,
 - `SimpleTileLayer` pouze vybere strategii enumem a interni utility vytvori requesty.
 - `TileLayer` pocita viditelne tiles z viewport bounds (`visibleTiles`) misto stareho odhadu `tileCount`,
 - `TilePlanner` uz neplanuje kolem stredu mapy; pouze expanduje `TileRange -> List<TileCoordinate>`,
 - `WebMercatorTileMath` byl zredukovan na funkce nutne pro world center prevod a WMS BBOX vypocet.

Dotcene soubory v kroku 1:

- `core/src/commonMain/kotlin/tilo/compose/core/map/MapState.kt`
- `render/src/commonMain/kotlin/eu/tilo/compose/render/CommandBuilder.kt`
- `core/src/commonMain/kotlin/tilo/compose/core/layers/Layer.kt`
- `core/src/commonMain/kotlin/tilo/compose/core/layers/TileLayer.kt`
- `core/src/commonMain/kotlin/tilo/compose/core/tile/source/WMSSource.kt`
- `core/src/commonMain/kotlin/tilo/compose/core/tile/utils/AddressingStrategy.kt`
- `core/src/commonMain/kotlin/tilo/compose/core/tile/utils/TilePlanner.kt`
- `core/src/commonMain/kotlin/tilo/compose/core/tile/utils/TileRequestFactory.kt`
- `core/src/commonMain/kotlin/tilo/compose/core/tile/utils/WmsBbox.kt`
- `core/src/commonMain/kotlin/tilo/compose/core/tile/utils/WebMercatorTileMath.kt`
- `core/src/commonMain/kotlin/tilo/compose/core/layers/impl/SimpleTileLayer.kt`

Co zatim neni hotove (plan kroku 2):

- centralni `CrsTransformer` pro source CRS -> map projection,
- jednotna pipeline reprojekce pro vector i tile vrstvy,
- oddeleni WMS-specific tile math od obecne `CoordinateSystem` abstrakce.

Co je nove pripraveno pro krok 2:

- nove rozhrani `Transformation<S, T>` v `core.transform` s metodami `sourceToTarget(...)` a `targetToSource(...)`,
- zakladni implementace `Wgs84ToWgs84Transformation`, ktera pro stejny CRS vraci bod beze zmeny.
- novy `MapConfig` drzi registry transformaci a pri nenalezeni transformace vyhazuje vyjimku (`IllegalStateException`).

Nove soubory:

- `core/src/commonMain/kotlin/tilo/compose/core/map/MapConfig.kt`
- `core/src/commonMain/kotlin/tilo/compose/core/transform/Transformation.kt`
- `core/src/commonMain/kotlin/tilo/compose/core/transform/Wgs84ToWgs84Transformation.kt`

Napojeni v rendereru:

- `MapRenderer` prevadi `mapState.center` do `tileLayer.sourceProjection` pomoci `mapState.config.sourceToTarget(...)` pred `buildRequests(...)`,
- pri chybejici transformaci renderer selze okamzite vyjimkou z `MapConfig`.

## Backend boundary

Renderer ma minimalni backend-agnostickou vrstvu nad aktualni command pipeline:

- `render/.../backend/RenderScene.kt`
- `render/.../backend/RenderBackend.kt`
- `render/.../backend/RenderSceneBuilder.kt`
- `render/.../backend/ComposeCanvasRenderBackend.kt`

### Co je oddeleno

`MapRenderer` uz nestavi draw pipeline primo jen pro Compose Canvas, ale nejdriv vytvori `RenderScene`:

- raster layers -> `RasterRenderSceneLayer`
- vector layers -> `VectorRenderSceneLayer`

Aktualni i jediny pouzivany backend je `ComposeCanvasRenderBackend`.
