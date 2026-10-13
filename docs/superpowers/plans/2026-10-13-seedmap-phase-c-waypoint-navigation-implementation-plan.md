# SeedMap Phase C Waypoint Navigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a reliable SeedMapper-native waypoint/navigation system with direction HUD, distance, optional long-range beacon rendering and minimap/compass integration, while treating Xaero as an optional integration rather than a dependency.

**Architecture:** Phase C introduces one waypoint service/model consumed by map, HUD, minimap and beacon rendering. SimpleWaypoints remains the durable native waypoint backend; SeedMapper stores extra navigation presentation metadata keyed by world+waypoint name. The Xaero bridge created/fixed in Phase A is isolated behind a small interface and may be polished here, but native navigation remains fully functional when Xaero is absent.

**Tech Stack:** Java 25, Minecraft 26.2, Fabric API HUD/world render events, existing SimpleWaypoints 1.4, existing `RenderManager`/world render infrastructure, optional reflection-only Xaero integration.

**Spec:** `docs/superpowers/specs/2026-10-13-seedmap-ui-hybrid-map-design.md`

## Global Constraints

- SeedMapper navigation must work with no Xaero jars installed.
- Do not place a real Minecraft beacon block; the beacon is a client render effect.
- Direction HUD must remain useful even when a world-space beacon is clipped or too distant.
- Waypoint world/dimension identity must be respected; never render a target from another dimension as if local.
- Existing SimpleWaypoints null-safe handling remains mandatory.
- Existing map/minimap waypoints continue to work.
- No waypoint renderer may require the target chunk to be loaded.
- Build and tests run on JDK 25.

## Review Focus

- Waypoint behind the player / directly left-right: screen-edge marker must choose stable edges and never return NaN; covered by Task 3 geometry tests.
- Very distant waypoint coordinates: HUD distance remains correct and beacon code uses camera-relative doubles before conversion to render coordinates; covered by Task 4 tests/runtime.
- World/dimension change: active navigation to an incompatible target is hidden without deleting the waypoint; covered by Task 2 service tests.
- Xaero missing or reflection API changed: native add/navigate remains functional and Xaero action reports unavailable instead of crashing; covered by Task 6 bridge tests.
- Deleted/renamed SimpleWaypoints entries with stale presentation metadata: metadata is pruned/lazily ignored; covered by Task 2 reconciliation tests.

---

## File Structure

### New files

- `src/main/java/dev/xpple/seedmapper/waypoint/SeedWaypoint.java` — normalized waypoint snapshot.
- `src/main/java/dev/xpple/seedmapper/waypoint/WaypointPresentation.java` — color/icon/HUD/beacon/visibility metadata.
- `src/main/java/dev/xpple/seedmapper/waypoint/SeedWaypointService.java` — SimpleWaypoints adapter + presentation metadata/reconciliation.
- `src/main/java/dev/xpple/seedmapper/waypoint/NavigationTarget.java` — currently selected navigation target.
- `src/main/java/dev/xpple/seedmapper/waypoint/WaypointNavigationManager.java` — active target state per current world.
- `src/main/java/dev/xpple/seedmapper/waypoint/WaypointHudMath.java` — direction/screen-edge pure math.
- `src/main/java/dev/xpple/seedmapper/waypoint/WaypointHudOverlay.java` — icon/name/distance HUD.
- `src/main/java/dev/xpple/seedmapper/waypoint/WaypointBeaconRenderer.java` — client-side vertical beam/marker geometry.
- `src/main/java/dev/xpple/seedmapper/waypoint/XaeroWaypointBridge.java` — optional runtime bridge facade if not already created by Phase A; otherwise extend existing class.

### Existing files to modify

- `src/main/java/dev/xpple/seedmapper/SeedMapper.java` — register HUD/beacon systems.
- `src/main/java/dev/xpple/seedmapper/seedmap/SeedMapScreen.java` — use service/manager for waypoint and Navigate actions.
- `src/main/java/dev/xpple/seedmapper/seedmap/ManualWaypointCompassOverlay.java` — consume normalized service instead of raw duplicated SimpleWaypoints access.
- `src/main/java/dev/xpple/seedmapper/seedmap/SeedMapMinimapOverlay.java` — presentation metadata/active-target state.
- `src/main/java/dev/xpple/seedmapper/config/Configs.java` — native navigation settings/metadata persistence.

### Tests

- `src/test/java/dev/xpple/seedmapper/waypoint/SeedWaypointServiceTest.java`
- `src/test/java/dev/xpple/seedmapper/waypoint/WaypointNavigationManagerTest.java`
- `src/test/java/dev/xpple/seedmapper/waypoint/WaypointHudMathTest.java`
- `src/test/java/dev/xpple/seedmapper/waypoint/WaypointPresentationTest.java`
- `src/test/java/dev/xpple/seedmapper/waypoint/XaeroWaypointBridgeTest.java`

---

### Task 1: Define normalized waypoint and presentation models

**Interfaces:**
- `record SeedWaypoint(String name, ResourceKey<Level> dimension, BlockPos pos, int color, boolean visible)`.
- `record WaypointPresentation(int color, String icon, boolean showOnMap, boolean showOnMinimap, boolean showHud, boolean showBeacon, boolean showCompass)` with defaults: map/minimap `true`, HUD/beacon/compass `false` except current legacy compass-enabled names migrate to compass `true`.
- Presentation serialization key is `<worldIdentifier>\u001f<waypointName>` so worlds cannot collide.

- [ ] Write model/default/serialization tests.
- [ ] Verify RED, implement, verify GREEN.
- [ ] Commit `feat: define seedmapper waypoint presentation model`.

### Task 2: Centralize SimpleWaypoints access and active navigation state

**Interfaces:**
- `List<SeedWaypoint> SeedWaypointService.list(Minecraft minecraft)` returns empty on missing world identifier/null API map.
- `Optional<SeedWaypoint> find(Minecraft, String name)`.
- `SeedWaypoint add(Minecraft, ResourceKey<Level>, String name, BlockPos pos, int color)` and `boolean remove(...)`.
- `WaypointPresentation presentation(String worldId, String name)` / `setPresentation(...)`.
- `record NavigationTarget(String worldIdentifier, SeedWaypoint waypoint)`.
- `void WaypointNavigationManager.navigate(NavigationTarget target)`, `clear()`, `Optional<NavigationTarget> activeFor(String worldId, ResourceKey<Level> dimension)`.

- [ ] Fake-backend tests for null map, duplicate naming, stale metadata and cross-dimension active target hiding.
- [ ] Implement service so no new screen directly calls `SimpleWaypointsAPI.getWorldWaypoints()`.
- [ ] Migrate existing map and manual compass reads incrementally.
- [ ] Run existing `SeedMapScreenWaypointTest` plus new tests.
- [ ] Commit `refactor: centralize seedmapper waypoint access`.

### Task 3: Add robust screen-edge direction HUD

**Interfaces:**
- `record HudMarker(double x, double y, double angleRadians, boolean onScreen)`.
- `HudMarker WaypointHudMath.project(Vec2 playerXZ, double playerYawRadians, Vec2 targetXZ, int screenWidth, int screenHeight, int margin)`.
- `String formatDistance(double blocks)` uses `m` below 1000 and one-decimal `km` at/above 1000.

HUD behavior:
- if target direction projects inside the central safe rectangle, marker may sit near that direction; otherwise clamp to screen edge with margin `12`.
- marker always shows compact direction icon; selected target additionally shows name + distance.
- behind-player and exact-axis directions must remain stable.

- [ ] Tests for N/E/S/W, diagonals, behind player, target equal to player, 320x240 and 4K-like dimensions, and 999m/1000m formatting.
- [ ] Implement pure math then `WaypointHudOverlay` with Fabric HUD registry.
- [ ] Add settings: `WaypointHudEnabled=true`, `WaypointHudMargin=12`, `WaypointHudShowDistance=true`.
- [ ] Commit `feat: add waypoint direction hud`.

### Task 4: Add optional world-space beacon/vertical marker

**Interfaces:**
- `WaypointBeaconRenderer.register()` hooks world render once.
- `List<BeaconGeometry> buildGeometry(Camera camera, NavigationTarget target, double maxDistance)` is pure-testable geometry prep.
- Default `WaypointBeaconEnabled=false`, `WaypointBeaconMaxDistance=8192.0D`, height span `384` blocks centered around target/world range where practical.

Rendering rules:
- no chunk lookup at target position;
- compute target minus camera in double precision before float submission;
- translucent narrow vertical beam plus small base marker;
- if target is farther than configured max or projection clips, skip beam but HUD remains active;
- no depth-writing that obscures normal world geometry.

- [ ] Geometry tests for 10, 1000 and 8000 block targets, over-max target, negative coordinates and cross-dimension rejection.
- [ ] Implement through existing 26.2 world-render custom-geometry path used by `RenderManager` rather than obsolete immediate buffer APIs.
- [ ] Runtime verify beam does not require loaded target chunk.
- [ ] Commit `feat: add waypoint beacon renderer`.

### Task 5: Unify map, minimap and compass presentation

**Behavior:**
- map marker visibility honors `showOnMap`.
- minimap marker/label honors `showOnMinimap`.
- legacy `ManualWaypointCompassOverlay` becomes a compact compass consumer of `showCompass`; keep old config migration so existing enabled labels remain enabled.
- active navigation target receives selected/highlight treatment on map/minimap.
- waypoint context UI exposes `Navigate`, `HUD`, `Beacon`, `Compass`, `Map`, `Minimap` toggles and color/icon edit where practical.

- [ ] Add service/presentation tests proving each visibility flag is independent.
- [ ] Migrate consumers without changing unrelated structure rendering.
- [ ] Run full tests and commit `feat: unify waypoint presentation across map and hud`.

### Task 6: Polish optional Xaero bridge after Phase A compatibility fix

**Interfaces:**
- `boolean XaeroWaypointBridge.isAvailable()`.
- Preserve Phase A `addLive(...)` / `addAllLive(...)`; add `Result addOrUpdate(SeedWaypoint waypoint, WaypointPresentation presentation)` as a convenience that derives the stable id and delegates to the same live bridge.
- `Result remove(String stableId)` where supported by the runtime bridge.
- Bridge origin identifier is `seedmapper:waypoints`; stable third-party waypoint id is world/dimension/name-derived.

Implementation preference for Xaero 26.6 runtime: use reflection around `XaeroMinimapSession.getCurrentSession()`, `BuiltInHudModules.MINIMAP`, current `MinimapSession`, and Xaero's third-party waypoint collection (`ThirdPartyWaypointManager`/`ThirdPartyWaypoints`) if the API path remains available. Never add a hard runtime dependency; all class lookup/invocation is isolated and failure returns an unavailable/error result.

- [ ] Unit-test unavailable/reflection-failure paths using injectable bridge backend.
- [ ] Runtime-test MegaCraft8 with Xaero Minimap 26.6.0 + World Map 1.47.0: added SeedMapper waypoint appears without restart.
- [ ] Runtime-test a dev run without Xaero: SeedMapper native waypoint/navigation actions remain enabled; Xaero action is disabled/hidden.
- [ ] Commit `feat: polish optional xaero waypoint bridge`.

### Task 7: MegaCraft8 navigation runtime verification

- [ ] Navigate to waypoints at 100m, 2km and >5km; direction icon/distance stay correct.
- [ ] Enable beacon and verify best-effort beam while HUD always remains useful.
- [ ] Unload target chunk and verify navigation still renders.
- [ ] Switch dimension/world and confirm incompatible target hides without deletion.
- [ ] Restart and verify presentation metadata persists.
- [ ] Verify map/minimap/compass flags independently.
- [ ] Verify Xaero present and absent scenarios.
- [ ] Re-run SeedMap, Loot Viewer, ESP and Baritone smoke tests; full Gradle build green.
- [ ] Push verified Phase C head/JAR hash to PR notes.
