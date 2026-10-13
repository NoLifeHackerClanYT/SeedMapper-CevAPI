# SeedMap Redesign Roadmap / Handoff

Date: 2026-10-13
Branch: `mc-26.2-port`
Execution mode: **Native**

## Canonical design

`docs/superpowers/specs/2026-10-13-seedmap-ui-hybrid-map-design.md`

Visual direction locked by user:
- map layout/interaction and map-marker readability: Chunkbase-inspired;
- compact button/control treatment: Xaero-inspired;
- no copied Chunkbase/Xaero branding or asset files;
- Hybrid base map: explored real chunks at high quality + seed-derived preview for unexplored regions;
- explored map history persists on disk and does not stay unbounded in RAM/VRAM.

## Execution order

1. **Phase A — Fullscreen UI / UX / Xaero add-export compatibility**
   - Plan: `docs/superpowers/plans/2026-10-13-seedmap-phase-a-ui-implementation-plan.md`
   - Fullscreen map, zoom, Chunkbase-style marker presentation, Xaero-style buttons, X/Z tooltip, grouped Options.
   - Fix live Xaero Add Waypoint + Export refresh in this phase.

2. **Phase B — Hybrid explored/seed terrain renderer**
   - Plan: `docs/superpowers/plans/2026-10-13-seedmap-phase-b-hybrid-renderer-implementation-plan.md`
   - Canonical 256x256 tiles, LOD0=1 block/pixel, explored chunk sampler, versioned SSD cache, relief shading, bounded CPU/GPU caches, Hybrid/Explored/Seed/Biome/Terrain modes.

3. **Phase C — Native waypoint navigation**
   - Plan: `docs/superpowers/plans/2026-10-13-seedmap-phase-c-waypoint-navigation-implementation-plan.md`
   - Central waypoint model/service, direction HUD, distance, optional beacon beam, map/minimap/compass integration, Xaero bridge polish.

4. **Phase D — Loot / QoL / final polish**
   - Plan: `docs/superpowers/plans/2026-10-13-seedmap-phase-d-loot-qol-implementation-plan.md`
   - Unified map actions, marker popup, Loot Viewer filter/sort/map jump/navigation/Xaero, icon/tag/hover polish, settings cleanup, final acceptance sweep.

## Current baseline before redesign implementation

- Minecraft: `26.2`
- Fabric Loader: `0.19.3`
- Fabric API: `0.152.1+26.2`
- Fabric Loom: `1.17-SNAPSHOT`
- SimpleWaypoints: `1.4.0`
- Java build/runtime validation: portable Temurin JDK 25
- MegaCraft8 currently contains Xaero Minimap `26.6.0` and Xaero World Map `1.47.0`.
- Existing 26.2 compatibility fixes before redesign include legacy BlockColors migration and null-safe SimpleWaypoints handling in SeedMap/Loot Viewer.

## Xaero finding that must be remembered

The existing SeedMapper Xaero export already wrote syntactically valid entries into the real MegaCraft8 path, e.g. `xaero/minimap/Neue Welt/dim%0/mw$default_1.txt`. The practical failure is that Xaero does not live-reload those external file edits. Phase A therefore keeps durable file export **and** mirrors added/exported waypoints into Xaero's active runtime through an optional reflection-only bridge.

Xaero 26.6 exposes a usable third-party runtime path including:
- `xaero.common.XaeroMinimapSession.getCurrentSession()`;
- `xaero.hud.minimap.BuiltInHudModules.MINIMAP`;
- `xaero.hud.minimap.module.MinimapSession`;
- `xaero.hud.minimap.waypoint.thirdparty.ThirdPartyWaypointManager` / `ThirdPartyWaypoints`;
- `xaero.common.minimap.waypoints.Waypoint`.

No hard Xaero dependency should be added.

## Native execution rule

Implement one task at a time using the plan checkbox order and TDD: RED test -> minimal implementation -> GREEN -> focused/full verification -> commit. Do not skip ahead across phases when an earlier phase is not verified in MegaCraft8.

## New-chat continuation phrases

After Phase A is verified, the user can say:
> `Phase A ist fertig. Mach Native mit Phase B nach docs/superpowers/plans/2026-10-13-seedmap-phase-b-hybrid-renderer-implementation-plan.md weiter.`

Likewise replace `B` with `C` or `C` with `D`. Always read this roadmap, the canonical spec, and the named phase plan before continuing.
