# SeedMap Phase A Fullscreen UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current debug-like SeedMap screen with a Chunkbase-inspired fullscreen map UI, Xaero-like compact controls, a wider safe zoom range, coordinate-rich tooltips, and grouped options without changing the underlying hybrid-renderer architecture planned for Phase B.

**Architecture:** Phase A keeps the existing Cubiomes biome tile renderer and CevAPI feature logic, but extracts viewport math, layout geometry, UI theming, icon density policy, and tooltip formatting into focused helpers. `SeedMapScreen` becomes the composition host for a fullscreen viewport with floating controls; Phase B later plugs the Hybrid/Explored renderer into the same viewport and zoom interfaces without another UI rewrite.

**Tech Stack:** Java 25, Minecraft 26.2, Fabric Loader 0.19.3, Fabric API 0.152.1+26.2, Fabric Loom 1.17-SNAPSHOT, JUnit 5, existing Cubiomes/Jextract bindings.

**Spec:** `docs/superpowers/specs/2026-10-13-seedmap-ui-hybrid-map-design.md`

## Global Constraints

- Map UX follows the interaction/layout model of Chunkbase Seed Map; do not copy Chunkbase branding, logos, proprietary textures, or packaged assets.
- Button treatment should feel closer to Xaero: compact rectangular/icon controls, dark translucent backgrounds, subtle borders, and clear hover/active/disabled/selected states.
- Existing CevAPI features must remain available.
- The map is the dominant fullscreen surface; controls overlay it instead of reserving large permanent margins.
- Map remains draggable with left mouse drag.
- Zoom is smooth and cursor-centered.
- One additional meaningful zoom-out tier beyond the current minimum is mandatory in Phase A; the complete far-distance LOD range belongs to Phase B.
- Structure/map icons use newly created SeedMapper assets only; no Chunkbase asset files may be copied.
- Phase A scope covers fullscreen/UI/interaction/options plus the requested Xaero add/export compatibility fix. Hybrid renderer/cache work is Phase B; native waypoint HUD/beacon navigation and Xaero bridge polish are Phase C; Loot/QoL expansion is Phase D.
- Phase A must not introduce a cache whose RAM use grows with total explored world size.
- Build and tests run on JDK 25.

## Review Focus

- Very small windows / large GUI scale: controls must remain on-screen and the map viewport must stay usable; covered by Task 2 layout tests.
- Rapid wheel zoom or double-click zoom near viewport edges: the world point under the cursor must not jump and the zoom value must remain finite/clamped; covered by Task 1 zoom tests.
- Extremely dense structure views while zoomed out: low-priority markers must declutter instead of covering the map; covered by Task 5 icon-policy tests.
- Resize after panning/zooming: map center and zoom must survive widget/layout recreation without duplicate controls; covered by Task 2 geometry tests and Task 8 runtime smoke checks.
- Missing/unknown tooltip data: X/Z must always render while biome/source/Y/block fields degrade gracefully; covered by Task 6 tooltip tests.

---

## File Structure

### New focused files

- `src/main/java/dev/xpple/seedmapper/seedmap/SeedMapViewport.java` — fullscreen viewport bounds and screen↔quart coordinate transforms.
- `src/main/java/dev/xpple/seedmapper/seedmap/SeedMapZoom.java` — zoom clamping, wheel/double-click steps, cursor-anchored zoom-center math.
- `src/main/java/dev/xpple/seedmapper/seedmap/SeedMapUiLayout.java` — deterministic floating-control geometry for arbitrary screen sizes.
- `src/main/java/dev/xpple/seedmapper/seedmap/SeedMapUiTheme.java` — Xaero-like colors/border state selection.
- `src/main/java/dev/xpple/seedmapper/seedmap/SeedMapIconButton.java` — compact themed icon/text button.
- `src/main/java/dev/xpple/seedmapper/seedmap/SeedMapToolbar.java` — floating map controls and category launcher composition.
- `src/main/java/dev/xpple/seedmapper/seedmap/SeedMapIconPolicy.java` — zoom-dependent marker visibility/scale rules.
- `src/main/java/dev/xpple/seedmapper/seedmap/SeedMapFeatureCategory.java` — structure-filter groups for the compact Structures surface.
- `src/main/java/dev/xpple/seedmapper/seedmap/SeedMapTooltipData.java` — tooltip data/line formatting with guaranteed X/Z.
- `src/main/java/dev/xpple/seedmapper/seedmap/SeedMapOptionsCategory.java` — grouped options tab model.

### Existing files to modify

- `src/main/java/dev/xpple/seedmapper/seedmap/SeedMapScreen.java` — wire viewport, toolbar, zoom, tooltip, grouped options, and icon policy into existing map behavior.
- `src/main/java/dev/xpple/seedmapper/seedmap/SeedMapRenderCore.java` — render clipping/viewport helpers only; no Phase B terrain changes.
- `src/main/java/dev/xpple/seedmapper/seedmap/FeatureToggleWidget.java` — use new themed marker-toggle presentation where applicable.
- `src/main/java/dev/xpple/seedmapper/seedmap/CustomStructureToggleWidget.java` — match compact themed marker-toggle presentation.
- `src/main/java/dev/xpple/seedmapper/config/Configs.java` — Phase A default zoom floor and UI settings that must persist.
- `src/main/java/dev/xpple/seedmapper/seedmap/MapFeature.java` — point built-in markers at the new original SeedMapper map-icon set.
- `src/main/java/dev/xpple/seedmapper/waypoint/XaeroWaypointBridge.java` — optional reflection-only live Xaero bridge.
- `src/main/java/dev/xpple/seedmapper/waypoint/XaeroWaypointFileExport.java` — durable Xaero file export/backup/dedup logic.
- `src/main/resources/assets/seedmapper/textures/map_icons/*.png` — newly created original marker icons inspired by Chunkbase readability.

### Tests to add

- `src/test/java/dev/xpple/seedmapper/seedmap/SeedMapZoomTest.java`
- `src/test/java/dev/xpple/seedmapper/seedmap/SeedMapUiLayoutTest.java`
- `src/test/java/dev/xpple/seedmapper/seedmap/SeedMapUiThemeTest.java`
- `src/test/java/dev/xpple/seedmapper/seedmap/SeedMapFeatureCategoryTest.java`
- `src/test/java/dev/xpple/seedmapper/seedmap/SeedMapIconPolicyTest.java`
- `src/test/java/dev/xpple/seedmapper/seedmap/SeedMapTooltipDataTest.java`
- `src/test/java/dev/xpple/seedmapper/seedmap/SeedMapOptionsCategoryTest.java`
- `src/test/java/dev/xpple/seedmapper/waypoint/XaeroWaypointBridgeTest.java`
- `src/test/java/dev/xpple/seedmapper/waypoint/XaeroWaypointFileExportTest.java`

---

### Task 1: Extract viewport and cursor-anchored zoom math

**Files:**
- Create: `src/main/java/dev/xpple/seedmapper/seedmap/SeedMapViewport.java`
- Create: `src/main/java/dev/xpple/seedmapper/seedmap/SeedMapZoom.java`
- Test: `src/test/java/dev/xpple/seedmapper/seedmap/SeedMapZoomTest.java`

**Interfaces:**
- Produces: `SeedMapViewport.fullscreen(int screenWidth, int screenHeight) -> SeedMapViewport`
- Produces: `boolean SeedMapViewport.contains(double screenX, double screenY)`
- Produces: `QuartPos2f SeedMapViewport.screenToQuart(double screenX, double screenY, QuartPos2f centerQuart, double pixelsPerBiome)`
- Produces: `double SeedMapZoom.clamp(double pixelsPerBiome)`
- Produces: `double SeedMapZoom.wheel(double currentPixelsPerBiome, double scrollY)`
- Produces: `double SeedMapZoom.doubleClickIn(double currentPixelsPerBiome)` and `doubleClickOut(...)`
- Produces: `QuartPos2f SeedMapZoom.anchoredCenter(SeedMapViewport viewport, QuartPos2f currentCenter, double mouseX, double mouseY, double oldPixelsPerBiome, double newPixelsPerBiome)`
- Phase A constants: minimum `0.5D`, maximum `100.0D`, wheel sensitivity `0.2D`, double-click factor `2.0D`. Phase B may lower the minimum once LOD exists.

- [ ] **Step 1: Write failing zoom tests**

Add tests asserting:
- `clamp(Double.NaN)` returns the minimum rather than NaN.
- `clamp(0.01)` returns `0.5D`; `clamp(500)` returns `100.0D`.
- wheel zoom remains finite and clamped for large positive/negative deltas.
- double-click in/out multiplies/divides by `2.0D` within bounds.
- anchored zoom keeps `viewport.screenToQuart(mouseX, mouseY, center, zoom)` equal before and after zoom within floating tolerance.
- fullscreen viewport contains `(0,0)` and `(width-1,height-1)` but not negative/out-of-range coordinates.

- [ ] **Step 2: Run tests to verify RED**

Run: `./gradlew.bat test --tests "dev.xpple.seedmapper.seedmap.SeedMapZoomTest"`
Expected: FAIL because `SeedMapViewport` / `SeedMapZoom` do not exist.

- [ ] **Step 3: Implement the two pure helpers**

Keep all math independent of Minecraft widgets/rendering. `SeedMapViewport` represents the entire client surface; floating controls do not shrink the map viewport.

- [ ] **Step 4: Run focused tests to verify GREEN**

Run: `./gradlew.bat test --tests "dev.xpple.seedmapper.seedmap.SeedMapZoomTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/xpple/seedmapper/seedmap/SeedMapViewport.java src/main/java/dev/xpple/seedmapper/seedmap/SeedMapZoom.java src/test/java/dev/xpple/seedmapper/seedmap/SeedMapZoomTest.java
git commit -m "feat: add fullscreen seed map viewport and zoom model"
```

### Task 2: Define deterministic fullscreen control geometry

**Files:**
- Create: `src/main/java/dev/xpple/seedmapper/seedmap/SeedMapUiLayout.java`
- Test: `src/test/java/dev/xpple/seedmapper/seedmap/SeedMapUiLayoutTest.java`

**Interfaces:**
- Produces: `SeedMapUiLayout.Layout SeedMapUiLayout.compute(int width, int height)`
- `Layout` exposes `SeedMapViewport viewport()`, `Rect primaryToolbar()`, `Rect categoryRail()`, `Rect zoomControls()`, `Rect statusBar()`, and `boolean compactMode()`.
- `Rect` exposes `x()`, `y()`, `width()`, `height()`, `contains(...)`.
- Exact UI constants: outer margin `6`, standard control size `22`, control gap `3`, panel padding `4`; compact mode activates when width `< 640` or height `< 360`.

- [ ] **Step 1: Write failing layout tests**

Assert for `2560x1440`, `854x480`, `640x360`, and `320x240` that:
- viewport is full screen;
- every floating rect is fully inside the screen;
- zoom controls are bottom-right;
- category rail is left/top aligned;
- compact mode is false at `854x480`, true at `320x240`;
- rects that must not overlap (`primaryToolbar`, `zoomControls`) do not intersect.

- [ ] **Step 2: Run focused tests to verify RED**

Run: `./gradlew.bat test --tests "dev.xpple.seedmapper.seedmap.SeedMapUiLayoutTest"`
Expected: FAIL because `SeedMapUiLayout` does not exist.

- [ ] **Step 3: Implement `SeedMapUiLayout`**

The viewport remains fullscreen at every resolution. Only floating chrome reflows/compacts.

- [ ] **Step 4: Run tests to verify GREEN**

Run: `./gradlew.bat test --tests "dev.xpple.seedmapper.seedmap.SeedMapUiLayoutTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/xpple/seedmapper/seedmap/SeedMapUiLayout.java src/test/java/dev/xpple/seedmapper/seedmap/SeedMapUiLayoutTest.java
git commit -m "feat: add responsive seed map ui layout"
```

### Task 3: Add Xaero-like button theme and compact control widget

**Files:**
- Create: `src/main/java/dev/xpple/seedmapper/seedmap/SeedMapUiTheme.java`
- Create: `src/main/java/dev/xpple/seedmapper/seedmap/SeedMapIconButton.java`
- Test: `src/test/java/dev/xpple/seedmapper/seedmap/SeedMapUiThemeTest.java`

**Interfaces:**
- Produces: `int SeedMapUiTheme.background(boolean hovered, boolean active, boolean selected)`
- Produces: `int SeedMapUiTheme.border(boolean hovered, boolean active, boolean selected)`
- Produces: `int SeedMapUiTheme.foreground(boolean active, boolean selected)`
- Produces: `SeedMapIconButton(int x, int y, int width, int height, Component label, @Nullable Identifier icon, Button.OnPress onPress, BooleanSupplier selected)`.
- Visual states: dark translucent default, brighter hover, accent-border selected, reduced-alpha disabled. No vanilla beveled texture.

- [ ] **Step 1: Write failing theme tests**

Assert that default/hover/selected/disabled state colors are distinct, deterministic ARGB values and disabled alpha is lower than active alpha.

- [ ] **Step 2: Run focused tests to verify RED**

Run: `./gradlew.bat test --tests "dev.xpple.seedmapper.seedmap.SeedMapUiThemeTest"`
Expected: FAIL because the theme does not exist.

- [ ] **Step 3: Implement theme and button rendering**

`SeedMapIconButton.extractContents(...)` draws the background/border from `SeedMapUiTheme`, centers the icon when present, and only renders text when the label is intentionally visible.

- [ ] **Step 4: Re-style existing toggle widgets**

Update `FeatureToggleWidget` and `CustomStructureToggleWidget` to use the same selected/disabled visual language while preserving Ctrl-isolate behavior.

- [ ] **Step 5: Run focused and existing waypoint tests**

Run: `./gradlew.bat test --tests "dev.xpple.seedmapper.seedmap.SeedMapUiThemeTest" --tests "dev.xpple.seedmapper.seedmap.SeedMapScreenWaypointTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/dev/xpple/seedmapper/seedmap/SeedMapUiTheme.java src/main/java/dev/xpple/seedmapper/seedmap/SeedMapIconButton.java src/main/java/dev/xpple/seedmapper/seedmap/FeatureToggleWidget.java src/main/java/dev/xpple/seedmapper/seedmap/CustomStructureToggleWidget.java src/test/java/dev/xpple/seedmapper/seedmap/SeedMapUiThemeTest.java
git commit -m "feat: add compact xaero style map controls"
```

### Task 4: Convert `SeedMapScreen` to fullscreen map + floating toolbar

**Files:**
- Create: `src/main/java/dev/xpple/seedmapper/seedmap/SeedMapToolbar.java`
- Create: `src/main/java/dev/xpple/seedmapper/seedmap/SeedMapFeatureCategory.java`
- Modify: `src/main/java/dev/xpple/seedmapper/seedmap/SeedMapScreen.java`
- Modify: `src/main/java/dev/xpple/seedmapper/seedmap/SeedMapRenderCore.java`
- Modify: `src/main/java/dev/xpple/seedmapper/config/Configs.java`
- Test: extend `src/test/java/dev/xpple/seedmapper/seedmap/SeedMapUiLayoutTest.java`
- Test: `src/test/java/dev/xpple/seedmapper/seedmap/SeedMapFeatureCategoryTest.java`

**Interfaces:**
- `SeedMapToolbar.Actions` supplies `Runnable` callbacks for Map, Structures, Loot, ESP, Waypoints, Minimap, Export, Settings, zoom-in, zoom-out, and recenter-player.
- `SeedMapToolbar.create(SeedMapUiLayout.Layout layout, Actions actions) -> List<SeedMapIconButton>`.
- `SeedMapFeatureCategory` values: `SETTLEMENTS`, `TEMPLES_RUINS`, `MAJOR`, `UNDERGROUND`, `OTHER`; each exposes `boolean contains(MapFeature feature)`. `SETTLEMENTS` covers village/outpost, `TEMPLES_RUINS` covers temple/ruin-style markers, `MAJOR` covers stronghold/monument/mansion/ancient city/end city/fortress/bastion/trial chambers, `UNDERGROUND` covers mineshaft/geode/ore-vein/canyon-style markers, and `OTHER` owns every remaining filterable feature. `PLAYER_ICON` and `WAYPOINT` are excluded from category filtering.
- Quick all-on/all-off and category filtering apply to structure/features only and must never implicitly disable `PLAYER_ICON` or manually created `WAYPOINT` markers.
- `SeedMapScreen` stores one `SeedMapViewport viewport` and one `SeedMapUiLayout.Layout uiLayout` rebuilt in `init()/resize()`.
- Existing `horizontalPadding()/verticalPadding()` map-hit logic is replaced by `viewport.contains(...)` for the main map only.
- Full-map zoom is clamped by `SeedMapZoom` (`0.5D..100.0D`) and is no longer blocked by a legacy persisted `Configs.SeedMapMinPixelsPerBiome = 1.0D`. Keep `SeedMapMinPixelsPerBiome`/`sanitizePixelsPerBiome(...)` for minimap/backward compatibility until the Phase B zoom/LOD migration; set its new default to `0.5D` without rewriting explicit user values.

- [ ] **Step 1: Add failing toolbar geometry assertions**

Extend layout tests to assert the toolbar button count/slots can fit all eight category launchers plus map controls in normal mode and reflow in compact mode.

- [ ] **Step 2: Write category-filter tests**

Add `SeedMapFeatureCategoryTest` asserting representative membership for every named category, that every filterable `MapFeature` belongs to exactly one category, and that `PLAYER_ICON`/`WAYPOINT` belong to none.

- [ ] **Step 3: Run tests to verify RED**

Run: `./gradlew.bat test --tests "dev.xpple.seedmapper.seedmap.SeedMapUiLayoutTest" --tests "dev.xpple.seedmapper.seedmap.SeedMapFeatureCategoryTest"`
Expected: FAIL on the new toolbar/category assertions.

- [ ] **Step 4: Implement `SeedMapFeatureCategory`, `SeedMapToolbar`, and wire fullscreen viewport**

Wire `SeedMapScreen.effectivePixelsPerBiome()` and the full-map `setPixelsPerBiome(...)` path through `SeedMapZoom.clamp(...)`, not the legacy config floor. Remove permanent top rows of structure toggle widgets from the map surface. The map renders behind floating controls. Existing actions remain reachable through toolbar/category panels. The Structures surface must provide quick all-on/all-off plus `SETTLEMENTS`, `TEMPLES_RUINS`, `MAJOR`, `UNDERGROUND`, and `OTHER` filtering without restoring a permanent row of icons.

- [ ] **Step 5: Wire cursor-centered wheel zoom and double-click gestures**

Use `SeedMapZoom.anchoredCenter(...)` before applying the new zoom. Left double-click zooms in around cursor; Shift+left-double-click zooms out. Recenter becomes an explicit toolbar action instead of consuming normal double-click.

- [ ] **Step 6: Preserve center/zoom on resize**

`resize()` must rebuild UI chrome without replacing `centerQuart` or current zoom. Ensure widgets are not duplicated after repeated resizes.

- [ ] **Step 7: Run full unit test suite**

Run: `./gradlew.bat test`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/dev/xpple/seedmapper/seedmap/SeedMapToolbar.java src/main/java/dev/xpple/seedmapper/seedmap/SeedMapFeatureCategory.java src/main/java/dev/xpple/seedmapper/seedmap/SeedMapScreen.java src/main/java/dev/xpple/seedmapper/seedmap/SeedMapRenderCore.java src/main/java/dev/xpple/seedmapper/config/Configs.java src/test/java/dev/xpple/seedmapper/seedmap/SeedMapUiLayoutTest.java src/test/java/dev/xpple/seedmapper/seedmap/SeedMapFeatureCategoryTest.java
git commit -m "feat: make seed map fullscreen with floating controls"
```

### Task 5: Replace map markers with a compact original icon set and declutter policy

**Files:**
- Create: `src/main/java/dev/xpple/seedmapper/seedmap/SeedMapIconPolicy.java`
- Modify: `src/main/java/dev/xpple/seedmapper/seedmap/MapFeature.java`
- Modify: `src/main/java/dev/xpple/seedmapper/seedmap/SeedMapScreen.java`
- Create: `src/main/resources/assets/seedmapper/textures/map_icons/*.png`
- Test: `src/test/java/dev/xpple/seedmapper/seedmap/SeedMapIconPolicyTest.java`

**Interfaces:**
- Produces: `double SeedMapIconPolicy.scale(double pixelsPerBiome)`.
- Produces: `boolean SeedMapIconPolicy.visible(MapFeature feature, double pixelsPerBiome)`.
- Rules: player marker and manually selected waypoint remain visible at every Phase A zoom; world spawn remains visible down to `0.5D`; common structures may hide at the farthest tier; high-value structures (stronghold, mansion, monument, ancient city, end city, fortress, bastion) remain visible at `0.5D`.
- Built-in marker assets use a consistent nominal `16x16` footprint and original SeedMapper artwork.

- [ ] **Step 1: Write failing policy tests**

Assert visibility/scale at `4.0D`, `1.0D`, and `0.5D`, including player/waypoint always-visible behavior and common-structure decluttering at the farthest tier.

- [ ] **Step 2: Run tests to verify RED**

Run: `./gradlew.bat test --tests "dev.xpple.seedmapper.seedmap.SeedMapIconPolicyTest"`
Expected: FAIL because `SeedMapIconPolicy` does not exist.

- [ ] **Step 3: Create original map-marker assets**

Create new SeedMapper-owned 16x16 PNG icons that use Chunkbase only as readability inspiration: crisp silhouette, consistent optical size, category-aware color. Do not trace/copy source PNGs from Chunkbase.

- [ ] **Step 4: Point `MapFeature` at the new assets and apply policy**

`SeedMapScreen.isFeatureWidgetVisible(...)` must also consult `SeedMapIconPolicy.visible(...)`; draw size uses `scale(...)` without changing hit-testing semantics at normal zoom.

- [ ] **Step 5: Run tests and resource existence check**

Run: `./gradlew.bat test --tests "dev.xpple.seedmapper.seedmap.SeedMapIconPolicyTest"`
Expected: PASS. Also run `./gradlew.bat processResources` and verify no missing-resource errors.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/dev/xpple/seedmapper/seedmap/SeedMapIconPolicy.java src/main/java/dev/xpple/seedmapper/seedmap/MapFeature.java src/main/java/dev/xpple/seedmapper/seedmap/SeedMapScreen.java src/main/resources/assets/seedmapper/textures/map_icons src/test/java/dev/xpple/seedmapper/seedmap/SeedMapIconPolicyTest.java
git commit -m "feat: add compact seed map markers and decluttering"
```

### Task 6: Add map tooltip with guaranteed X/Z coordinates

**Files:**
- Create: `src/main/java/dev/xpple/seedmapper/seedmap/SeedMapTooltipData.java`
- Modify: `src/main/java/dev/xpple/seedmapper/seedmap/SeedMapScreen.java`
- Test: `src/test/java/dev/xpple/seedmapper/seedmap/SeedMapTooltipDataTest.java`

**Interfaces:**
- `SeedMapTooltipData(String biomeName, int x, int z, @Nullable Integer y, @Nullable String surfaceBlock, Source source)`.
- `Source` initially contains `SEED_PREVIEW`; Phase B adds `EXPLORED_CHUNK` without changing callers.
- `List<Component> lines()` always includes biome when nonblank plus `X: <x>` and `Z: <z>`; Y/block are included only when known; source is shown as `Seed Prediction` in Phase A.

- [ ] **Step 1: Write failing tooltip tests**

Assert:
- X and Z are always present, including unknown biome/Y/block.
- blank biome is omitted, not rendered as `null`.
- `SEED_PREVIEW` renders `Source: Seed Prediction`.
- future optional Y/block values append cleanly without changing X/Z lines.

- [ ] **Step 2: Run focused tests to verify RED**

Run: `./gradlew.bat test --tests "dev.xpple.seedmapper.seedmap.SeedMapTooltipDataTest"`
Expected: FAIL because the record does not exist.

- [ ] **Step 3: Implement tooltip data and map hover card**

Build tooltip position from the cursor quart/block coordinate already maintained by `handleMapMouseMoved`. Render a compact dark card near the cursor without obscuring the selected marker; clamp card bounds to the screen.

- [ ] **Step 4: Run focused tests to verify GREEN**

Run: `./gradlew.bat test --tests "dev.xpple.seedmapper.seedmap.SeedMapTooltipDataTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/xpple/seedmapper/seedmap/SeedMapTooltipData.java src/main/java/dev/xpple/seedmapper/seedmap/SeedMapScreen.java src/test/java/dev/xpple/seedmapper/seedmap/SeedMapTooltipDataTest.java
git commit -m "feat: add coordinate rich seed map tooltip"
```

### Task 7: Replace the long Options screen with grouped categories

**Files:**
- Create: `src/main/java/dev/xpple/seedmapper/seedmap/SeedMapOptionsCategory.java`
- Modify: `src/main/java/dev/xpple/seedmapper/seedmap/SeedMapScreen.java`
- Test: `src/test/java/dev/xpple/seedmapper/seedmap/SeedMapOptionsCategoryTest.java`

**Interfaces:**
- Enum order and labels: `MAP`, `STRUCTURES`, `LOOT`, `ESP`, `WAYPOINTS`, `MINIMAP`, `EXPORT`, `ADVANCED`.
- Existing `OptionsScreen` keeps its existing actions but renders one category at a time using the themed left rail and a centered/right content panel.
- Category ownership:
  - MAP: Seed Input, Locate Biome, Saved Seeds.
  - STRUCTURES: Structure Overlay, Lootable Structures Only, Locate Structure, Datapack Structures, Datapack Settings.
  - LOOT: Locate Loot, Loot Viewer.
  - ESP: target/profile/run/clear/range controls.
  - WAYPOINTS: Import Wurst and current waypoint-related settings/actions available in the screen.
  - MINIMAP: toggle and Minimap Settings.
  - EXPORT: Export JSON and Export Xaero; Xaero live add/export refresh is fixed in Task 8.
  - ADVANCED: Datapack URL/import, Keybinds, remaining advanced/dev controls.

- [ ] **Step 1: Write failing category tests**

Assert exact enum order, display labels, and stable category IDs used by the UI.

- [ ] **Step 2: Run focused tests to verify RED**

Run: `./gradlew.bat test --tests "dev.xpple.seedmapper.seedmap.SeedMapOptionsCategoryTest"`
Expected: FAIL because the enum does not exist.

- [ ] **Step 3: Implement category model and grouped options layout**

The selected category persists while opening/closing nested option screens during one SeedMap session. Use `SeedMapIconButton`/theme instead of vanilla full-width button stacks for category navigation; individual text-heavy controls may remain labeled. Add Reset-to-default for groups where the current config has a meaningful default, and do not duplicate a control between the map chrome and Options unless the duplicate is intentional navigation/shortcut UI.

- [ ] **Step 4: Verify all current actions remain reachable**

Compare against the existing pre-redesign `OptionsScreen` action list and ensure Seed Input, structure/biome/loot locators, Loot Viewer, every ESP action, datapack actions, minimap, keybinds, saved seeds, JSON/Xaero export, and Wurst import are still present.

- [ ] **Step 5: Run tests**

Run: `./gradlew.bat test --tests "dev.xpple.seedmapper.seedmap.SeedMapOptionsCategoryTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/dev/xpple/seedmapper/seedmap/SeedMapOptionsCategory.java src/main/java/dev/xpple/seedmapper/seedmap/SeedMapScreen.java src/test/java/dev/xpple/seedmapper/seedmap/SeedMapOptionsCategoryTest.java
git commit -m "feat: group seed map options into categories"
```

### Task 8: Fix live Xaero add and export refresh

**Files:**
- Create: `src/main/java/dev/xpple/seedmapper/waypoint/XaeroWaypointBridge.java`
- Create: `src/main/java/dev/xpple/seedmapper/waypoint/XaeroWaypointFileExport.java`
- Modify: `src/main/java/dev/xpple/seedmapper/seedmap/SeedMapScreen.java`
- Test: `src/test/java/dev/xpple/seedmapper/waypoint/XaeroWaypointBridgeTest.java`
- Test: `src/test/java/dev/xpple/seedmapper/waypoint/XaeroWaypointFileExportTest.java`

**Interfaces:**
- `boolean XaeroWaypointBridge.isAvailable()` checks Fabric mod presence plus reflective runtime availability without a hard Xaero dependency.
- `record XaeroWaypointSpec(String stableId, String name, BlockPos pos, int color)` is the shared live/file-export payload.
- `XaeroWaypointBridge.Result addLive(String stableId, String name, BlockPos pos, int color)` mirrors a SeedMapper marker into Xaero's active runtime immediately.
- `XaeroWaypointBridge.Result addAllLive(Collection<XaeroWaypointSpec> specs)` is used after export so Xaero does not require a restart/file reload.
- Bridge origin identifier is `seedmapper:waypoints`.
- Runtime target for the currently installed Xaero 26.6 line is the public session/third-party waypoint path discovered in MegaCraft8: `XaeroMinimapSession.getCurrentSession()` -> `BuiltInHudModules.MINIMAP` -> `MinimapSession` -> Xaero third-party waypoint collection. All use is reflection-isolated.
- `XaeroWaypointFileExport.export(Path gameDir, String worldIdentifier, ResourceKey<Level> dimension, Collection<XaeroWaypointSpec> specs)` keeps durable `mw$default_1.txt` export behavior with backup and deduplication.

**Observed baseline:** the current code already writes syntactically valid entries into `xaero/minimap/<world>/dim%*/mw$default_1.txt` in MegaCraft8, but Xaero does not live-reload those external file edits. Phase A must therefore keep durable export and also mirror added/exported markers into Xaero's active runtime.

- [ ] **Step 1: Write bridge/file-export regression tests**

Assert:
- Xaero absent => `isAvailable=false`, live add returns unavailable without exception.
- reflection backend failure => error result, native SeedMapper remains usable.
- file export preserves existing `sets:`/waypoint lines, deduplicates coordinates, creates backup, and escapes colon in names.
- Overworld/Nether/End resolve to `dim%0`, `dim%-1`, `dim%1`.

- [ ] **Step 2: Run tests to verify RED**

Run: `./gradlew.bat test --tests "dev.xpple.seedmapper.waypoint.XaeroWaypointBridgeTest" --tests "dev.xpple.seedmapper.waypoint.XaeroWaypointFileExportTest"`
Expected: FAIL because bridge/export classes do not exist.

- [ ] **Step 3: Extract current file writer from `SeedMapScreen`**

Move folder resolution, line encoding, set preservation, backup and deduplication into `XaeroWaypointFileExport`; preserve compatibility with the real MegaCraft8 layout under `xaero/minimap`.

- [ ] **Step 4: Implement optional live bridge**

Use reflection only. Construct Xaero `Waypoint` instances and register/update them under the SeedMapper third-party origin/stable id in the active session. Cache reflective handles only after successful resolution; clear/re-resolve after session/world change. Never throw Xaero reflection failures into map rendering/UI.

- [ ] **Step 5: Wire both user paths**

`Add Xaero Waypoint` performs durable/native source action as applicable plus immediate `addLive`. `Export Xaero` writes the durable waypoint file and mirrors all newly exported entries through `addAllLive`, then reports counts for persisted/live-added/skipped items.

- [ ] **Step 6: Run tests and real Xaero runtime smoke**

In MegaCraft8 (Xaero Minimap 26.6.0 + World Map 1.47.0), add one waypoint and export visible structures. Both must appear in Xaero during the same session without Minecraft restart. Restart once and verify durable exported markers remain.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/dev/xpple/seedmapper/waypoint/XaeroWaypointBridge.java src/main/java/dev/xpple/seedmapper/waypoint/XaeroWaypointFileExport.java src/main/java/dev/xpple/seedmapper/seedmap/SeedMapScreen.java src/test/java/dev/xpple/seedmapper/waypoint/XaeroWaypointBridgeTest.java src/test/java/dev/xpple/seedmapper/waypoint/XaeroWaypointFileExportTest.java
git commit -m "fix: integrate seed map waypoints with xaero runtime"
```

### Task 9: Full verification and MegaCraft8 runtime smoke test

**Files:**
- Modify only if verification finds defects in Phase A files above.

**Interfaces:**
- Consumes all Phase A interfaces.
- Produces a build artifact that starts in MegaCraft8 without regressing prior 26.2 fixes.

- [ ] **Step 1: Run formatting/diff sanity checks**

Run: `git diff --check`
Expected: exit code 0 aside from non-fatal line-ending warnings.

- [ ] **Step 2: Run complete test suite**

Run: `./gradlew.bat clean test`
Expected: BUILD SUCCESSFUL and all tests pass.

- [ ] **Step 3: Run full build**

Run: `./gradlew.bat clean build`
Expected: BUILD SUCCESSFUL; JAR created under `build/libs/`.

- [ ] **Step 4: Install the test JAR into MegaCraft8 with backup**

Replace only `E:\Games\Minecraft\Instances\MegaCraft8\mods\SeedMapper-2.24.0-CevAPI_v0.22-MC26.2.jar`; save the previous JAR in Downloads with a `pre-phase-a-ui` suffix and verify SHA-256 of source/destination match.

- [ ] **Step 5: Runtime smoke checklist**

In MegaCraft8 verify:
- map opens fullscreen at 2560x1440 and at least one smaller window size;
- pan works anywhere not occupied by floating controls;
- wheel zoom anchors to cursor;
- double-click zoom-in and Shift+double-click zoom-out work;
- farthest Phase A zoom (`0.5D`) does not freeze and markers declutter;
- resize preserves center/zoom and does not duplicate widgets;
- tooltip always shows X/Z;
- every Options category opens and all old actions remain reachable;
- Loot Viewer and existing SimpleWaypoints fixes still work;
- Add Xaero Waypoint and Export Xaero appear live in Xaero without restart and survive restart via durable export;
- no new `ERROR`, Mixin, `NullPointerException`, or render crash appears in `latest.log`.

- [ ] **Step 6: Commit any verification-only fixes, then push**

If no fixes are needed, push the existing task commits. If fixes are needed, make one focused verification commit and rerun Steps 1–5 before push.
