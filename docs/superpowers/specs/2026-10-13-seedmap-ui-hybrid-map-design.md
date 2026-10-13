# SeedMapper CevAPI — Fullscreen UI + Hybrid Map Design

Date: 2026-10-13
Branch: `mc-26.2-port`
Status: Approved; Phase A-D implementation plans written; Native execution selected

## 1. Goal

Turn the current SeedMap screen into a polished fullscreen mapping tool instead of a debug-style screen. The map UX should follow the interaction/layout model of Chunkbase Seed Map, while button treatment should feel closer to Xaero. Existing CevAPI features must remain available.

The map must combine two data qualities in one seamless view:

- explored/loaded chunks: real Minecraft world data at high quality;
- unexplored surroundings: best-possible seed-derived preview using Cubiomes/SeedMapper.

The user should be able to zoom very far out without excessive RAM/VRAM use. Explored quality must persist across game restarts through an SSD-backed cache.

## 2. Non-goals

- Do not copy Chunkbase or Xaero branding, logos, proprietary textures, or packaged assets.
- Do not require Xaero to be installed for SeedMapper navigation features.
- Do not keep every explored chunk permanently resident in RAM or VRAM.
- Do not replace the existing seed/structure calculation engine with a web service.
- Do not make the high-quality explored map depend on current render distance after the chunk has been cached.

## 3. Visual direction

### 3.1 Map layout and controls

Use Chunkbase as the primary map-UX reference:

- the map is the dominant fullscreen surface;
- compact floating controls rather than rows of large vanilla buttons;
- top/side tool groups for layers, structures, loot, waypoints, export, settings;
- bottom/right map controls for zoom, recenter, and fullscreen-related actions;
- structure icons behave like map markers rather than oversized GUI widgets;
- information appears in compact popups/tooltips near the selected map item.

### 3.2 Buttons

Use a Xaero-like control language:

- compact rectangular/icon buttons;
- dark translucent backgrounds;
- clear hover, active, disabled, and selected states;
- subtle border/highlight rather than vanilla beveled button styling;
- icon-first controls where the function is obvious;
- text labels only where ambiguity would otherwise be high.

### 3.3 Icons

Structure/map icons should follow the visual readability of Chunkbase:

- small, crisp, recognizable silhouettes;
- consistent optical size;
- compact marker footprint;
- category-aware color/icon treatment;
- automatic decluttering at distant zoom levels.

Icons may be newly drawn/recreated for SeedMapper but must not be copied from Chunkbase asset files.

## 4. Phase A — Fullscreen UI and interaction

### 4.1 Fullscreen map

`SeedMapScreen` becomes a true fullscreen map surface. The map viewport should use almost the entire client area; controls overlay the map instead of reserving large permanent margins.

Required behavior:

- responsive to arbitrary GUI scale and window resolution;
- map remains draggable with left mouse drag;
- cursor-centered wheel zoom;
- double-click zoom-in and Shift+double-click zoom-out where practical;
- optional zoom-box gesture may be added after core controls are stable;
- recenter-on-player action;
- escape/back preserves normal Minecraft screen behavior.

### 4.2 Zoom

Replace the current practical zoom range with a wider map-style range comparable to the referenced Chunkbase view.

Requirements:

- one additional meaningful zoom-out tier beyond the current minimum is mandatory;
- overall minimum should be set by world-view usability, not by the existing `pixelsPerBiome` clamp;
- zoom must remain smooth and cursor-centered;
- structures/icons are progressively filtered or clustered at very distant zoom;
- map rendering uses LOD tiles rather than rendering full-resolution explored data at every zoom level.

### 4.3 Toolbar and sidebar

Controls are grouped into logical surfaces:

- Map
- Structures
- Loot
- ESP
- Waypoints
- Minimap
- Export
- Settings / Advanced

The structure layer control should support quick all-on/all-off plus category filtering without occupying permanent rows across the top of the map.

### 4.4 Options redesign

Replace the current large collection of loosely arranged buttons with grouped panels/tabs.

Each category gets:

- title;
- concise description where useful;
- toggles/sliders with consistent spacing;
- reset-to-default for the group where appropriate;
- no duplicated controls between full map and nested option screens unless intentional.

## 5. Phase B — Hybrid map renderer

### 5.1 Rendering sources

The renderer exposes one common tile interface but can source pixels from two pipelines:

1. `ExploredTerrainProvider` — real loaded Minecraft chunk data.
2. `SeedTerrainProvider` — seed-derived preview from Cubiomes/SeedMapper.

A third `HybridTerrainProvider` selects the best available source per region: explored data wins when cached, otherwise seed preview is used.

### 5.2 Explored chunk sampling

When a client chunk is available, capture a compact surface representation rather than retaining the chunk object.

For each X/Z surface column store enough information to render the map later:

- top visible/surface Y;
- representative surface block/material color;
- water/ice/snow state where it changes appearance;
- optional biome id for tinting;
- flags needed for map shading.

Sampling must happen from data Minecraft already has loaded. The cache must not pin chunks or increase the game's chunk retention radius.

### 5.3 Surface color

Use Minecraft-like map colors as the base where possible, then apply controlled custom handling for important surfaces such as:

- grass/foliage biome tint;
- water depth/tint;
- sand/red sand;
- stone/deepslate exposed terrain;
- snow/ice;
- leaves;
- artificial surfaces/buildings when they are the top visible material.

The explored view should be recognizably block-derived when zoomed in, not a biome-color blob.

### 5.4 Relief and height shading

Apply subtle hill-shading from neighboring surface heights.

Goals:

- mountains, valleys, ridges, coastlines and cliffs are readable;
- flat areas remain visually calm;
- shading does not overpower actual surface color;
- the same lighting direction and strength are used across adjacent tiles to avoid seams.

The seed preview may use a lower-confidence terrain/height approximation when available, but must never present estimated height as exact explored data.

### 5.5 Seed preview improvements

The existing Cubiomes tile path remains the fallback but gets a new presentation layer:

- improved biome palette;
- sharper coast/river readability where available from the generator;
- optional terrain-style color variation;
- consistent hill/terrain cues where reliable;
- visual palette aligned with explored tiles so the boundary is less jarring.

The fallback must remain asynchronous and tile-cached as today; map interaction must not block on large seed calculations.

### 5.6 Hybrid seam behavior

A region transitions from seed preview to explored data when a real chunk tile is available.

Rules:

- no thick explored/unexplored border by default;
- identical tile geometry and coordinate system for both providers;
- color/shading pipelines should share common post-processing;
- explored tiles replace seed tiles atomically after completion;
- partial chunk data must not produce transparent holes or flicker.

## 6. Persistent explored cache and LOD

### 6.1 Disk-first persistence

Explored map data is persisted to disk and must not remain permanently in RAM.

Logical layout:

```text
seedmapper/map-cache/
  <world-id>/
    overworld/
    nether/
    end/
```

The exact physical format may be versioned binary files rather than one file per chunk to avoid filesystem overhead.

Cache identity must include enough information to prevent cross-world contamination:

- server/singleplayer world identity;
- dimension;
- cache format version;
- where needed, seed/generator identity.

### 6.2 RAM/GPU cache

Use bounded LRU-style caches:

- CPU tile cache: visible tiles + a small prefetch ring;
- GPU texture cache: only active/recently visible render tiles;
- eviction closes `DynamicTexture` resources immediately;
- cache budgets are configurable later, with conservative defaults.

No design may scale RAM usage linearly with the total number of chunks ever explored.

### 6.3 LOD pyramid

Generate or lazily derive lower-resolution tiles for distant zoom levels.

Conceptual levels:

```text
LOD0  ~ 1 map pixel per block in detailed explored view
LOD1  ~ 2x2 blocks per pixel
LOD2  ~ 4x4 blocks per pixel
LOD3+ progressively summarized tiles
```

The exact mapping is allowed to vary with GUI zoom, but maximum zoom-out must never require loading every block-resolution tile in the visible world area.

Downsampling should preserve coastlines, water, major terrain boundaries and overall relief instead of using nearest-neighbor color blobs.

## 7. Map view modes

The renderer exposes these user modes:

- `Hybrid` — explored data where available, seed preview elsewhere; default.
- `Explored` — only real/cached explored terrain, unknown regions visually distinct.
- `Seed` — seed-derived map only.
- `Biome` — clear categorical biome map.
- `Terrain` — surface/height-focused presentation.

Structure, loot, waypoint and grid displays are overlays and do not require separate base-map modes.

## 8. Tooltips and map interaction

Hovering the map should show compact map information.

For explored data:

```text
Biome
X: ...
Z: ...
Y: ...
Surface: ...
Source: Explored
```

For seed preview:

```text
Biome
X: ...
Z: ...
Source: Seed Prediction
```

Coordinates X/Z are mandatory. Height is only shown as exact when it came from real explored chunk data; estimated values must be labeled as estimates.

Selecting a structure/loot marker opens a compact action popup containing relevant actions such as:

- Waypoint
- Xaero
- Highlight
- Copy Coordinates
- Loot
- Navigate

Right-click on empty map space creates/selects a temporary map marker and exposes waypoint/navigation actions.

## 9. Phase C — Waypoint system

### 9.1 Xaero integration

Fix Xaero waypoint creation and export so SeedMapper-created waypoints actually appear in Xaero when Xaero is installed.

Integration must correctly resolve:

- world/server identity;
- dimension;
- Xaero subworld where relevant;
- waypoint set;
- name;
- coordinates;
- color/icon metadata supported by the integration path.

The implementation should prefer a supported runtime/API integration when available. File-format export remains available as an explicit export action, not as a fake replacement for live add-to-Xaero.

### 9.2 SeedMapper-native navigation

SeedMapper remains usable without Xaero.

Native waypoint presentation may include:

- screen-edge direction marker;
- distance display;
- optional world-space beacon/vertical beam;
- compass/minimap marker;
- color/icon controls;
- per-waypoint visibility toggles.

The beacon is a SeedMapper render effect, not a placed Minecraft beacon block, and should remain useful beyond normal chunk/render distance when the target coordinates are known.

## 10. Phase D — Loot / QoL

Unify map actions and loot actions around the same waypoint/highlight/copy/navigation behavior.

Loot viewer improvements may include:

- item and enchantment filtering;
- sorting by distance;
- compact tags/icons;
- map jump/open action;
- add waypoint / add Xaero waypoint;
- highlight;
- copy coordinates.

These improvements must not regress the null-safe SimpleWaypoints handling added during the Minecraft 26.2 port.

## 11. Component boundaries

Avoid adding more responsibilities directly to the already-large `SeedMapScreen`.

Target components:

- `SeedMapViewport` — coordinate conversion, pan/zoom state, visible tile calculation.
- `SeedMapToolbar` / grouped panels — fullscreen controls and visual state.
- `MapTileProvider` — common tile-source contract.
- `SeedTerrainProvider` — Cubiomes fallback tiles.
- `ExploredTerrainProvider` — real chunk-derived tiles.
- `HybridTerrainProvider` — source selection.
- `ExploredChunkSampler` — converts Minecraft chunks into compact surface data.
- `ExploredMapStore` — persistent disk format and versioning.
- `MapTileCache` — bounded CPU/GPU tile lifetime and LOD selection.
- `TerrainColorizer` — shared surface/biome palette and post-processing.
- `TerrainShading` — height/slope shading.
- `MapOverlayRenderer` — structures, loot, waypoints, player marker, grid.
- `MapTooltipController` — hover/selection information.
- `XaeroWaypointBridge` — optional Xaero integration isolated from core waypoint code.

Existing classes (`SeedMapScreen`, `SeedMapRenderCore`, `Tile`, `TilePos`, minimap classes) should be migrated incrementally rather than replaced in one rewrite.

## 12. Data flow

Explored path:

```text
Minecraft client chunk loaded/updated
        -> ExploredChunkSampler
        -> compact surface chunk data
        -> ExploredMapStore (disk)
        -> LOD tile generation/cache
        -> HybridTerrainProvider
        -> viewport-visible GPU textures
```

Seed path:

```text
Seed + dimension + generator settings
        -> existing Cubiomes async biome/terrain calculation
        -> SeedTerrainProvider tiles
        -> shared color/shading post-processing
        -> HybridTerrainProvider fallback
```

UI path:

```text
mouse/keyboard
        -> SeedMapViewport
        -> visible world bounds + LOD
        -> tile providers/cache
        -> base map
        -> overlays/icons/tooltips
```

## 13. Performance and resource limits

The design must stay responsive on long-lived worlds with large explored areas.

Requirements:

- explored cache persistence is disk-backed, not RAM-backed;
- only viewport-relevant tiles plus a small prefetch margin are decoded/uploaded;
- GPU textures are explicitly closed on eviction;
- background chunk sampling is rate-limited so world play is not stalled by map generation;
- LOD generation runs off the render thread wherever Minecraft API safety allows;
- seed calculations remain asynchronous;
- opening the map must not scan the entire explored cache;
- zooming far out must not trigger block-resolution loading for the full visible area.

Initial implementation should favor predictable bounded memory over aggressive preloading. Performance budgets will be validated with real MegaCraft8 runtime testing rather than guessed solely from unit tests.

## 14. Failure handling

The map should degrade gracefully:

- corrupt explored-cache records: skip/rebuild the affected tile, do not crash the client;
- unknown cache version: ignore/migrate through an explicit version path, never deserialize blindly;
- missing Xaero: hide/disable Xaero-specific actions while native SeedMapper waypoint actions remain functional;
- unavailable seed/generator data: explored map remains usable where real data exists;
- missing explored tile: immediately show seed fallback instead of an empty hole;
- failed background tile generation: log once with tile coordinates and keep the previous/fallback tile;
- world switch/dimension switch: cancel stale async work and prevent results from being inserted into the new world's cache.

## 15. Testing strategy

### 15.1 Unit tests

Cover independently testable logic:

- pan/zoom world-to-screen and screen-to-world transforms;
- cursor-centered zoom invariants;
- LOD selection thresholds;
- cache-key world/dimension separation;
- disk-format encode/decode/version checks;
- LRU eviction and resource lifecycle bookkeeping;
- height/slope shading calculations;
- explored-vs-seed source selection;
- icon declutter rules;
- tooltip coordinate/source formatting;
- Xaero bridge fallback behavior where testable without Xaero runtime.

### 15.2 Integration/runtime tests

Use the existing JDK 25 + LLVM/jextract build environment and MegaCraft8 for real validation.

Required runtime scenarios:

1. open fullscreen SeedMap in a fresh world with no explored cache;
2. move through new terrain and verify explored tiles replace seed preview;
3. unload chunks and verify explored quality persists;
4. restart Minecraft and verify disk-cached explored tiles return;
5. switch Overworld/Nether/End and verify cache isolation;
6. switch worlds/servers and verify no cross-world tile bleed;
7. zoom from detailed block-level view to maximum zoom-out without major hitching;
8. stress test a large explored region and observe RAM/VRAM behavior;
9. verify structure/loot/waypoint overlays at multiple zoom levels;
10. verify SeedMapper with Xaero present and absent;
11. verify tooltip X/Z everywhere and exact Y only on real explored data;
12. re-run existing 26.2 startup, SimpleWaypoints, loot viewer, and Baritone smoke tests.

## 16. Delivery order

Implementation is deliberately incremental.

### Phase A — UX foundation

1. extract viewport/pan/zoom model from `SeedMapScreen`;
2. fullscreen map bounds;
3. Chunkbase-like zoom range/behavior and controls;
4. X/Z tooltip foundation;
5. new Xaero-style button/control primitives;
6. grouped toolbar/sidebar/options;
7. structure icon redesign/declutter behavior;
8. Xaero add/export fix.

### Phase B — map quality

1. common tile-provider abstraction;
2. explored chunk sampler;
3. explored disk store;
4. explored surface renderer;
5. relief shading;
6. bounded RAM/GPU caches;
7. LOD pyramid;
8. seed-preview palette/terrain improvements;
9. hybrid source composition;
10. map modes: Hybrid / Explored / Seed / Biome / Terrain.

### Phase C — navigation

1. native waypoint model cleanup;
2. direction HUD + distance;
3. beacon/vertical marker;
4. compass/minimap integration;
5. Xaero bridge polish.

### Phase D — QoL

1. unified marker popup actions;
2. loot viewer/map jump polish;
3. icon/tag polish;
4. animation/hover polish;
5. settings cleanup and advanced controls.

## 17. Acceptance criteria

The work is considered complete when all of the following are true:

- SeedMap opens as a polished fullscreen map and remains usable at the current MegaCraft8 GUI scale/resolution;
- map navigation feels map-native rather than like dragging a debug texture;
- maximum zoom-out is at least comparable in visible world coverage to the provided Chunkbase reference;
- structure icons and map controls follow the agreed Chunkbase/Xaero visual direction without copied branded assets;
- tooltips always expose X/Z and distinguish explored data from seed prediction;
- explored chunks render at substantially higher detail than current biome quart tiles;
- explored map quality survives chunk unload and Minecraft restart;
- explored history does not cause unbounded RAM/VRAM growth;
- unexplored areas remain filled by a high-quality seed-derived preview;
- hybrid transitions do not show obvious holes or thick chunk borders;
- relief/height is visually readable in explored terrain;
- Xaero waypoint add/export works when Xaero is installed;
- SeedMapper-native navigation remains functional without Xaero;
- existing structures, loot, ESP, minimap, Baritone integration and custom CevAPI features continue to work;
- full Gradle build/tests pass on JDK 25;
- MegaCraft8 runtime smoke tests pass without new SeedMapper crashes.

## 18. Compatibility and migration

This work targets Minecraft 26.2 and the current CevAPI port branch. Existing user configuration should be preserved where possible. New UI/map/cache settings must receive safe defaults so an existing config can start without manual edits.

The explored disk-cache format must include a version number from its first release, allowing later format changes without corrupting old worlds or crashing startup.
