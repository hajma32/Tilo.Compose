# CRS-agnostic implementation plan

Tento dokument popisuje implementační plán pro přechod na architekturu **jedna mapa = jedna projekce (`map.projection`)**.

## Základní rozhodnutí

- `Map` interně vždy pracuje jen v `map.projection`.
- `Viewport` zůstává čistě kartézský a nezná žádné CRS.
- Reprojekce se týká pouze **geometrií**.
- **Tiles se nikdy nereprojektují klientsky**.
- Tile server musí umět dodat dlaždice přímo v projekci mapy.

## Co už tato iterace zavádí

- explicitní projekce `EPSG:4326`, `EPSG:3857`, `EPSG:5514`,
- centrální `TransformationRegistry` + `CrsTransformer`,
- transformace `EPSG:4326 <-> EPSG:3857`,
- `TileLayer.projection` + fail-fast validace, že `tileLayer.projection == map.projection`,
- sjednocení demo aplikace na `EPSG:4326`, aby tile pipeline odpovídala novému kontraktu.

## Milestone 1: foundation layer

### 1. Explicitní CRS identity

Soubory:

- `core/.../projection/Epsg4326Projection.kt`
- `core/.../projection/Epsg3857Projection.kt`
- `core/.../projection/Epsg5514Projection.kt`
- `core/.../projection/Wgs84WebMercatorProjection.kt`

Cíl:

- přestat míchat `EPSG:4326` a `EPSG:3857` pod jedním názvem,
- ponechat zpětně kompatibilní alias pro starý `Wgs84WebMercatorProjection`.

### 2. Centrální transform registry

Soubory:

- `core/.../transform/Transformation.kt`
- `core/.../transform/TransformationRegistry.kt`
- `core/.../transform/CrsTransformer.kt`
- `core/.../map/MapConfig.kt`
- `core/.../map/Map.kt`

Cíl:

- mít jedno místo pro lookup transformací,
- implicitně podporovat identitu při `source == target`,
- držet transformace v `MapConfig`, ne rozptýleně v rendereru.

### 3. První reálné transformace

Soubory:

- `core/.../transform/Wgs84ToWgs84Transformation.kt`
- `core/.../transform/Wgs84ToWebMercatorTransformation.kt`
- `core/.../transform/WebMercatorToWgs84Transformation.kt`

Cíl:

- bezpečně pokrýt běžný případ `EPSG:4326 <-> EPSG:3857`,
- připravit registry API pro další CRS.

Poznámka:

- `EPSG:5514` je v této iteraci přidané jako explicitní `Projection` identita.
- Přesná transformační matematika `4326 <-> 5514` bude následovat v další iteraci.
- Důvod: potřebujeme ji zavést ověřeně a test-first, ne odhadnout bez validačních dat.

## Milestone 2: geometrie pipeline

Soubory:

- `render/.../FeatureProjection.kt`
- `render/.../MapRenderer.kt`
- případně call-site v `composeApp/.../App.kt`

Cíl:

- před vstupem do rendereru vždy převést geometrii do `map.projection`,
- renderer nechat úplně bez CRS logiky,
- zachovat možnost fast-path optimalizací až nad správnými vstupy.

## Milestone 3: tile kontrakt bez reprojekce

Soubory:

- `core/.../tile/TileLayer.kt`
- `core/.../tile/WMSTileLayer.kt`
- `core/.../tile/XYZTileLayer.kt`
- případné další tile implementace

Cíl:

- každá tile vrstva musí deklarovat `projection`,
- při `loadTiles(map)` se ověří shoda s `map.projection`,
- při neshodě se vyhodí okamžitá chyba s jasnou zprávou.

Dopad:

- tile server musí umět vracet data přímo v projekci mapy,
- žádné klientské přepočty rastru.

## Milestone 4: EPSG:5514 transformace

Cíl:

- přidat přesnou transformaci `EPSG:4326 <-> EPSG:5514`,
- registrovat ji do `TransformationRegistry.Default`,
- doplnit testy na referenční body z ČR.

Požadavky na implementaci:

- jasně potvrzená osa a znaménka,
- referenční testovací body,
- round-trip tolerance,
- žádná „přibližná“ implementace bez validačních dat.

## Testy

Minimální sada:

- stabilní `id` pro `EPSG:4326`, `EPSG:3857`, `EPSG:5514`,
- round-trip `4326 -> 3857 -> 4326`,
- `TransformationRegistry` resolve pro stejné CRS i registrované transformace,
- `Map.transformSourceToTarget(...)` přes `MapConfig`,
- fail-fast při neshodě `tileLayer.projection != map.projection`.

## Known limits po této iteraci

- `EPSG:5514` má zatím explicitní metadata, ale ještě ne finální transformační matematiku.
- Tile pipeline stále předpokládá, že `TileGrid` je už definovaný v CRS vrstvy.
- Renderer zatím nemá speciální optimalizace pro jiné CRS než stávající fallback/cartesian cestu.

## Doporučené pořadí dalších iterací

1. dokončit a otestovat `EPSG:5514` transformace,
2. napojit geometrie pipeline explicitně přes `featuresSourceProjection`,
3. odstranit staré názvy a komentáře, které míchají `4326` a `3857`,
4. teprve potom řešit optimalizace a specializované fast-path pro vybrané projekce.

