# SeedMap Phase D Loot and QoL Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish the redesign by unifying map/loot/waypoint actions, adding map-jump/navigation polish, improving Loot Viewer filtering/sorting/tags, and cleaning settings/hover behavior without regressing the 26.2 stability fixes.

**Architecture:** Phase D extracts marker/loot actions into one action service and one compact popup model used by map structures, loot entries and temporary map markers. Loot Viewer keeps its existing data source but moves filtering/sorting into pure testable helpers. UI polish reuses the Phase A theme and Phase C waypoint service rather than adding another parallel action path.

**Tech Stack:** Java 25, Minecraft 26.2, Fabric UI/render APIs, existing LootExportHelper/Cubiomes loot data, Phase A UI theme, Phase C waypoint/navigation service, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-10-13-seedmap-ui-hybrid-map-design.md`

## Global Constraints

- Preserve null-safe SimpleWaypoints handling everywhere; no direct unchecked `getWorldWaypoints()` use may be reintroduced.
- Existing Loot Viewer highlight/waypoint/copy behavior remains available while being routed through shared actions.
- Marker popup actions are contextual: unavailable actions are hidden/disabled rather than crashing.
- X/Z coordinates are always available in map/loot action UI.
- Xaero-specific actions are optional and depend on bridge availability.
- UI uses the Phase A Xaero-like button/theme language and original SeedMapper assets.
- Build and tests run on JDK 25.

## Review Focus

- Loot list becomes empty after filtering while scrolled: scroll offset clamps to zero and no row/button indexing crashes; covered by Task 3 tests.
- Waypoint backend returns null/empty map: action availability and toggles remain safe; covered by Task 1 action-service tests plus existing waypoint regression test.
- Selected structure disappears after zoom/filter refresh: popup closes or resolves by immutable marker context instead of retaining stale widget references; covered by Task 2 popup tests.
- Map jump across a different dimension: action is disabled or prompts dimension mismatch; never silently centers wrong coordinates; covered by Task 4 tests.
- Huge loot result set: filtering/sorting stays deterministic and UI only creates buttons for visible rows; covered by Task 3 helper tests and runtime smoke.

---

## File Structure

### New files

- `src/main/java/dev/xpple/seedmapper/seedmap/action/MapActionType.java` — `WAYPOINT`, `XAERO`, `HIGHLIGHT`, `COPY_COORDS`, `LOOT`, `NAVIGATE`, `OPEN_MAP`.
- `src/main/java/dev/xpple/seedmapper/seedmap/action/MapActionContext.java` — immutable position/dimension/label/source payload.
- `src/main/java/dev/xpple/seedmapper/seedmap/action/MapActionService.java` — availability + execution for shared actions.
- `src/main/java/dev/xpple/seedmapper/seedmap/action/MapActionResult.java` — success/message result.
- `src/main/java/dev/xpple/seedmapper/seedmap/ui/MapMarkerPopup.java` — compact action popup using Phase A theme.
- `src/main/java/dev/xpple/seedmapper/seedmap/loot/LootFilter.java` — pure item/enchantment/text matching.
- `src/main/java/dev/xpple/seedmapper/seedmap/loot/LootSort.java` — distance/name/loot-count order model.
- `src/main/java/dev/xpple/seedmapper/seedmap/loot/LootViewModel.java` — filtered/sorted immutable rows and counts.
- `src/main/java/dev/xpple/seedmapper/seedmap/ui/HoverAnimator.java` — tiny deterministic hover interpolation helper.

### Existing files to modify

- `src/main/java/dev/xpple/seedmapper/seedmap/SeedMapScreen.java` — replace bespoke context-menu action code with shared popup/action service.
- `src/main/java/dev/xpple/seedmapper/seedmap/LootTableScreen.java` — consume filter/sort/action model; add open-map/Xaero/navigate.
- `src/main/java/dev/xpple/seedmapper/seedmap/ChestLootWidget.java` — share compact action affordances where relevant.
- `src/main/java/dev/xpple/seedmapper/config/Configs.java` — QoL preferences/default sort/filter persistence.
- Phase A options/category screen files — final settings cleanup only.

### Tests

- `src/test/java/dev/xpple/seedmapper/seedmap/action/MapActionServiceTest.java`
- `src/test/java/dev/xpple/seedmapper/seedmap/ui/MapMarkerPopupTest.java`
- `src/test/java/dev/xpple/seedmapper/seedmap/loot/LootFilterTest.java`
- `src/test/java/dev/xpple/seedmapper/seedmap/loot/LootViewModelTest.java`
- `src/test/java/dev/xpple/seedmapper/seedmap/ui/HoverAnimatorTest.java`

---

### Task 1: Centralize marker/loot actions

**Interfaces:**
- `record MapActionContext(ResourceKey<Level> dimension, BlockPos pos, String label, Optional<MapFeature> feature, Optional<LootExportHelper.LootEntry> lootEntry)`.
- `boolean MapActionService.isAvailable(MapActionType type, MapActionContext context)`.
- `MapActionResult execute(MapActionType type, MapActionContext context)`.
- COPY_COORDS writes `x y z`/`x ~ z` according to known Y; WAYPOINT delegates to `SeedWaypointService`; XAERO delegates to `XaeroWaypointBridge`; HIGHLIGHT delegates to current ESP/render manager; NAVIGATE delegates to `WaypointNavigationManager`; LOOT opens only when loot data exists; OPEN_MAP returns a center request consumed by the owning screen.

- [ ] Write fake-dependency tests for every action availability/result, including Xaero unavailable and null/empty waypoint backend.
- [ ] Verify RED, implement service, verify GREEN.
- [ ] Search production source and ensure new action code does not directly call unchecked `getWorldWaypoints()`.
- [ ] Commit `refactor: centralize seed map actions`.

### Task 2: Replace old context menu with compact marker popup

**Interfaces:**
- `MapMarkerPopup.open(MapActionContext context, int anchorX, int anchorY, Collection<MapActionType> actions)`.
- popup clamps into screen bounds and lays out icon-first actions using `SeedMapUiTheme`/`SeedMapIconButton`.
- context is immutable; popup does not retain `FeatureWidget` references.

Behavior:
- structure click/right-click exposes relevant `Waypoint / Xaero / Highlight / Copy / Loot / Navigate` actions.
- empty-map right-click uses temporary marker context and exposes waypoint/copy/navigate actions.
- clicking elsewhere closes popup; resize closes/reflows safely.

- [ ] Test popup bounds at all four screen corners, unavailable action filtering, immutable context after source widget removal.
- [ ] Implement and migrate `SeedMapScreen` bespoke `ContextMenu` usage.
- [ ] Run Phase A UI tests and full waypoint tests.
- [ ] Commit `feat: add unified map marker action popup`.

### Task 3: Improve Loot Viewer filtering, sorting and row virtualization

**Interfaces:**
- `record LootFilter(String text, Optional<String> enchantment, Optional<String> itemId)` with `boolean matches(LootEntry)`.
- `enum LootSort { DISTANCE_ASC, DISTANCE_DESC, NAME, ITEM_COUNT_DESC }`.
- `LootViewModel.build(List<LootEntry> source, LootFilter filter, LootSort sort, BlockPos playerPos)` returns immutable rows and total/matched chest/item counts.

Behavior:
- free-text filter matches item display/id, structure/piece and enchantment names case-insensitively.
- distance remains default sort to preserve current behavior.
- scroll offset clamps whenever filter/sort changes.
- only visible rows own interactive buttons; rebuilding filter results does not leave stale buttons.
- compact tags/icons show key loot traits without duplicating full tooltip text.

- [ ] Test empty filter, no matches, multi-token text, enchantment match, stable tie ordering, distance sort and scroll clamp.
- [ ] Implement helpers then wire `LootTableScreen`.
- [ ] Add search/sort controls using Phase A compact theme.
- [ ] Run existing loot/waypoint regression tests.
- [ ] Commit `feat: improve loot viewer filtering and sorting`.

### Task 4: Add Loot Viewer map jump, Xaero and navigate actions

**Behavior:**
- every loot result row exposes shared actions: Highlight, Waypoint, Copy, Map, Navigate; Xaero appears only if available.
- `Map` closes/returns to the owning SeedMap and centers on the entry coordinates without changing zoom unexpectedly.
- if Loot Viewer was opened from a non-map screen, Map creates/opens a SeedMap screen for the same world seed/dimension using existing screen factory path.
- cross-dimension map jump is disabled with a clear message unless a correct target-dimension SeedMap can be constructed.

- [ ] Test map-center request preserves zoom and target coordinates.
- [ ] Test action set with Xaero available/unavailable and dimension mismatch.
- [ ] Wire shared actions into visible loot rows.
- [ ] Runtime smoke map -> loot -> map round-trip.
- [ ] Commit `feat: connect loot viewer to map navigation`.

### Task 5: Final icon/tag/hover polish

**Interfaces:**
- `double HoverAnimator.step(double current, boolean hovered, double deltaSeconds)` targets `1.0` hovered / `0.0` idle, clamps `0..1`, no frame-rate-dependent overshoot.

Behavior:
- buttons/popup rows use subtle hover highlight/fade, not large movement.
- selected/active waypoint/highlight states use clear persistent accent independent of hover.
- compact tags reuse original SeedMapper icons/assets; no copied Chunkbase/Xaero textures.
- animation can be disabled through config (`SeedMapUiAnimations=true` default) for accessibility/performance.

- [ ] Test 0/1 clamping, large delta, disabled-animation immediate state.
- [ ] Implement and apply only to map/loot redesigned controls.
- [ ] Commit `style: polish seed map hover and tags`.

### Task 6: Settings cleanup and advanced controls

Behavior:
- remove obsolete duplicated controls left behind by pre-Phase-A UI.
- Advanced contains cache/debug/dev-only controls; common Map/Waypoint/Loot options stay in their category.
- expose safe reset buttons per category.
- add cache status/read-only diagnostics: current world cache path/hash, CPU/GPU tile counts, cache format version; no expensive directory scan on opening settings.
- keep existing config keys/migration whenever possible rather than resetting user choices.

- [ ] Extend Phase A options-category tests to assert no duplicated registered control IDs and all public actions remain reachable.
- [ ] Implement diagnostics via current in-memory cache/store state only.
- [ ] Run config migration regression test with the real legacy config fixture/path behavior.
- [ ] Commit `refactor: finish seed map settings cleanup`.

### Task 7: Final MegaCraft8 acceptance sweep

- [ ] Full Gradle `clean build --stacktrace` on JDK 25.
- [ ] Install backed-up test JAR into MegaCraft8.
- [ ] Verify fullscreen UI, maximum zoom-out and Hybrid/Explored/Seed/Biome/Terrain modes.
- [ ] Verify explored cache survives restart and memory stays bounded.
- [ ] Verify structure popup: copy, waypoint, Xaero, highlight, loot, navigate.
- [ ] Verify empty-map temporary marker actions.
- [ ] Verify Loot Viewer filter/sort/no-results, map jump, waypoint/Xaero/highlight/copy/navigate.
- [ ] Verify HUD direction + beacon + minimap/compass navigation.
- [ ] Verify Xaero present, then dev run without Xaero.
- [ ] Re-run Baritone, ESP outline/fill, minimap, saved seeds, Wurst import, datapack structures and legacy config startup.
- [ ] Confirm no new SeedMapper crash report/log exception; record final JAR SHA-256 and verified commit in PR.
