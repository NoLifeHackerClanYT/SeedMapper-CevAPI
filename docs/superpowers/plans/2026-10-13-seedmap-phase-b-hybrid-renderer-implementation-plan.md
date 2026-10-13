# SeedMap Phase B Hybrid Renderer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the biome-blob-only base map with a bounded-memory hybrid renderer that shows explored Minecraft chunks at block-derived quality, persists them to disk, falls back to a high-quality seed preview elsewhere, and supports long-range LOD zoom.

**Architecture:** Phase B introduces a canonical 256x256 render-tile geometry shared by seed and explored providers. Real client chunks are sampled into compact surface records on the client thread, persisted through a versioned disk store, then colorized/LOD-rendered asynchronously; the viewport requests only visible tiles plus a small prefetch ring through bounded CPU/GPU caches. `HybridTerrainProvider` prefers explored data and uses `SeedTerrainProvider` for every missing pixel/region so the map never shows holes.

**Tech Stack:** Java 25, Minecraft 26.2, Fabric Loader 0.19.3, Fabric API lifecycle events, Fabric Loom 1.17-SNAPSHOT, JUnit 5, Minecraft `DynamicTexture`, existing Cubiomes/Jextract bindings, Java NIO/Deflater/CRC32.

**Spec:** `docs/superpowers/specs/2026-10-13-seedmap-ui-hybrid-map-design.md`

## Global Constraints

- Phase A fullscreen viewport/zoom/UI contracts are inputs and must not be rewritten.
- Explored chunks use real client world data; unknown areas remain seed-derived.
- Explored data is disk-backed and must not cause RAM/VRAM usage to grow with total world exploration.
- Sampling must not pin chunks or increase Minecraft render/chunk distance.
- LOD0 is one map pixel per block; LODn covers `2^n x 2^n` blocks per pixel.
- Canonical render tiles are 256x256 pixels at every LOD.
- Opening the map must not scan the complete explored cache.
- Seed calculations remain asynchronous.
- Corrupt/unknown cache data degrades to seed fallback, never a client crash.
- World/dimension switches invalidate stale asynchronous work.
- Build and tests run on JDK 25.

## Review Focus

- Rapid world/server/dimension switching while tile work is running: stale results must be discarded; covered by Task 2 and Task 8 generation-token tests.
- Corrupt/truncated/unknown-version cache files: read returns empty/rebuildable state without propagating decode exceptions; covered by Task 3 codec/store tests.
- Huge explored worlds: opening or zooming must touch only requested tile/chunk files, not enumerate all cache content; covered by Task 7 cache request-count tests.
- Partially explored render tiles: explored pixels replace seed pixels without transparent holes or hard missing squares; covered by Task 6 hybrid composition tests.
- Surface updates after a chunk changes while loaded: the corresponding explored chunk is marked dirty/resampled without continuously scanning every loaded chunk; covered by Task 4 dirty-capture tests and runtime smoke.

---

## File Structure

### New map model/provider files

- `src/main/java/dev/xpple/seedmapper/seedmap/terrain/MapDataSource.java` — `SEED_PREDICTION` / `EXPLORED`.
- `src/main/java/dev/xpple/seedmapper/seedmap/terrain/MapViewMode.java` — `HYBRID`, `EXPLORED`, `SEED`, `BIOME`, `TERRAIN`.
- `src/main/java/dev/xpple/seedmapper/seedmap/terrain/MapTileKey.java` — canonical LOD/tile coordinates.
- `src/main/java/dev/xpple/seedmapper/seedmap/terrain/MapTileData.java` — immutable 256x256 ARGB data plus explored/source mask.
- `src/main/java/dev/xpple/seedmapper/seedmap/terrain/MapSample.java` — tooltip/query sample for one world X/Z.
- `src/main/java/dev/xpple/seedmapper/seedmap/terrain/MapTileProvider.java` — asynchronous tile/sample contract.
- `src/main/java/dev/xpple/seedmapper/seedmap/terrain/SeedTerrainProvider.java` — Cubiomes-backed fallback.
- `src/main/java/dev/xpple/seedmapper/seedmap/terrain/ExploredTerrainProvider.java` — disk-backed explored renderer.
- `src/main/java/dev/xpple/seedmapper/seedmap/terrain/HybridTerrainProvider.java` — source composition.
- `src/main/java/dev/xpple/seedmapper/seedmap/terrain/TerrainColorizer.java` — common palette/post-processing.
- `src/main/java/dev/xpple/seedmapper/seedmap/terrain/TerrainShading.java` — deterministic height/slope shading.
- `src/main/java/dev/xpple/seedmapper/seedmap/terrain/MapTileDownsampler.java` — LOD derivation.

### New explored cache files

- `src/main/java/dev/xpple/seedmapper/seedmap/explored/MapWorldKey.java` — world/dimension/generator cache identity.
- `src/main/java/dev/xpple/seedmapper/seedmap/explored/ExploredChunkData.java` — compact 16x16 surface representation.
- `src/main/java/dev/xpple/seedmapper/seedmap/explored/ExploredChunkSampler.java` — `LevelChunk` -> compact data.
- `src/main/java/dev/xpple/seedmapper/seedmap/explored/ExploredChunkCodec.java` — versioned binary encode/decode.
- `src/main/java/dev/xpple/seedmapper/seedmap/explored/ExploredMapStore.java` — atomic disk persistence.
- `src/main/java/dev/xpple/seedmapper/seedmap/explored/ExploredChunkCaptureManager.java` — lifecycle/dirty queue and generation token.
- `src/main/java/dev/xpple/seedmapper/seedmap/cache/BoundedTileCache.java` — CPU LRU.
- `src/main/java/dev/xpple/seedmapper/seedmap/cache/MapTextureCache.java` — GPU texture LRU and close-on-evict.
- `src/main/java/dev/xpple/seedmapper/mixin/ClientChunkSurfaceDirtyMixin.java` — mark changed client chunks dirty.

### Existing files to modify

- `src/main/java/dev/xpple/seedmapper/SeedMapper.java` — register client chunk/level lifecycle hooks.
- `src/main/java/dev/xpple/seedmapper/seedmap/SeedMapScreen.java` — request/render canonical tiles, switch view mode, query samples.
- `src/main/java/dev/xpple/seedmapper/seedmap/SeedMapRenderCore.java` — canonical tile blit and resource cleanup.
- `src/main/java/dev/xpple/seedmapper/config/Configs.java` — view mode/cache budgets/shading toggles with safe defaults.
- `src/main/resources/mixins.seedmapper.json` — register dirty-chunk mixin.

### Tests to add

- `src/test/java/dev/xpple/seedmapper/seedmap/terrain/MapTileKeyTest.java`
- `src/test/java/dev/xpple/seedmapper/seedmap/explored/MapWorldKeyTest.java`
- `src/test/java/dev/xpple/seedmapper/seedmap/explored/ExploredChunkCodecTest.java`
- `src/test/java/dev/xpple/seedmapper/seedmap/explored/ExploredMapStoreTest.java`
- `src/test/java/dev/xpple/seedmapper/seedmap/terrain/TerrainShadingTest.java`
- `src/test/java/dev/xpple/seedmapper/seedmap/terrain/MapTileDownsamplerTest.java`
- `src/test/java/dev/xpple/seedmapper/seedmap/terrain/HybridTerrainProviderTest.java`
- `src/test/java/dev/xpple/seedmapper/seedmap/cache/BoundedTileCacheTest.java`
- `src/test/java/dev/xpple/seedmapper/seedmap/cache/MapTextureCacheTest.java`

---

### Task 1: Define canonical tile geometry, samples and map modes

**Files:** create `MapDataSource`, `MapViewMode`, `MapTileKey`, `MapTileData`, `MapSample`, `MapTileProvider`; add `MapTileKeyTest`.

**Interfaces:**
- `record MapTileKey(int lod, int x, int z)` with `static final int PIXELS = 256`, `int blocksPerPixel()`, `int blockSpan()`, `int minBlockX()`, `int minBlockZ()`.
- `record MapTileData(MapTileKey key, int[] argb, BitSet exploredPixels)`; constructor rejects arrays/masks inconsistent with 256x256 pixels.
- `record MapSample(int x, int z, MapDataSource source, String biome, OptionalInt y, String surface, boolean estimatedHeight)`.
- `interface MapTileProvider { CompletableFuture<MapTileData> request(MapTileKey key, long generation); CompletableFuture<Optional<MapSample>> sample(int blockX, int blockZ, long generation); }`

- [ ] Write failing tests for LOD spans/negative tile coordinates/data-size validation.
- [ ] Run focused tests; expect RED.
- [ ] Implement the pure model/contracts.
- [ ] Run focused tests; expect GREEN.
- [ ] Commit: `feat: define hybrid map tile contracts`.

### Task 2: Add stable world/cache identity and stale-work generation tokens

**Files:** create `MapWorldKey`; extend `ExploredChunkCaptureManager` skeleton; test `MapWorldKeyTest`.

**Interfaces:**
- `record MapWorldKey(String worldIdentifier, Identifier dimension, long seed, int generatorFlags)`.
- `String MapWorldKey.cacheHash()` is full lowercase SHA-256 hex of all four fields with separators.
- `Path MapWorldKey.resolveUnder(Path root)` returns `<root>/<cacheHash>/<sanitized-dimension>`.
- `long ExploredChunkCaptureManager.generation()` and `long beginWorld(MapWorldKey key)` increment a monotonic token; `boolean isCurrent(long token)` gates async completion.

- [ ] Test identical inputs => identical hash; any world/dimension/seed/flags difference => different hash; filesystem-hostile identifiers never leak into the path.
- [ ] Test `beginWorld` invalidates a prior generation token.
- [ ] Implement identity/token logic.
- [ ] Run tests and commit `feat: isolate explored map caches by world`.

### Task 3: Implement versioned explored chunk codec and atomic disk store

**Files:** create `ExploredChunkData`, `ExploredChunkCodec`, `ExploredMapStore`; tests codec/store.

**Data contract:** each chunk contains 256 columns: `short[] surfaceY`, `int[] surfaceArgb`, block palette + `short[] blockIndex`, biome palette + `short[] biomeIndex`, `byte[] flags`. Palette entries are resource-location strings so cached tooltip metadata survives restart without relying on transient numeric registry IDs.

**Disk format:** magic `0x534D4150` (`SMAP`), format version `1`, compressed payload, CRC32. Store files at `SeedMapper/map-cache/v1/<worldHash>/<dimension>/chunks/r.<regionX>.<regionZ>/<chunkX>.<chunkZ>.smap`, with region buckets based on 32x32 chunks. Writes use sibling temp file + atomic move where supported.

- [ ] Write round-trip, negative-coordinate, truncation, CRC-corruption and unknown-version tests using `@TempDir`.
- [ ] Verify RED.
- [ ] Implement codec and store; `read(...)` returns `Optional.empty()` for invalid/corrupt/unknown data after logging, never throws into UI code.
- [ ] Assert store reads only the requested path and does not enumerate the world cache root.
- [ ] Run tests and commit `feat: persist explored chunk map data`.

### Task 4: Capture real client chunks without retaining them

**Files:** create `ExploredChunkSampler`, finish `ExploredChunkCaptureManager`, add `ClientChunkSurfaceDirtyMixin`, modify `SeedMapper.java` and mixin JSON.

**Interfaces:**
- `ExploredChunkData ExploredChunkSampler.sample(ClientLevel level, LevelChunk chunk)`; must copy all needed values and retain no `LevelChunk`, `BlockState`, `Biome` holder or level reference.
- `void ExploredChunkCaptureManager.onChunkLoad(ClientLevel level, LevelChunk chunk)` samples on client thread then queues only immutable `ExploredChunkData` for background persistence.
- `void markDirty(LevelChunk chunk)` deduplicates chunk positions.
- END_CLIENT_TICK processes at most `2` dirty chunks per tick by default; chunk-load capture remains immediate but disk writes are background.
- `ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE` calls `beginWorld(...)`/clears dirty state.

Sampling uses `Heightmap.Types.WORLD_SURFACE` as the initial surface and performs a bounded downward correction when the returned state is air; store top material color, Y, biome identifier, surface block identifier and water/ice/snow flags.

- [ ] Add pure queue/dedup/rate-limit tests through injectable sampler/store fakes.
- [ ] Implement Fabric `ClientChunkEvents.CHUNK_LOAD` registration.
- [ ] Add mixin injection after client `LevelChunk#setBlockState` to call `markDirty`; compile-time descriptor is verified against 26.2 mappings before commit.
- [ ] Run full tests and Loom mixin validation.
- [ ] Commit `feat: capture explored client chunks`.

### Task 5: Implement shared terrain color and relief shading

**Files:** create `TerrainColorizer`, `TerrainShading`; tests shading/color rules.

**Interfaces:**
- `int TerrainColorizer.exploredColor(int baseMapColor, String blockId, String biomeId, byte flags)`.
- `int TerrainColorizer.seedColor(int cubiomesBiomeId)`.
- `int TerrainShading.shade(int argb, int centerY, int westY, int northY, int eastY, int southY)`.

Shading uses one fixed northwest light direction across all tiles, clamps brightness adjustment to +/-18%, and preserves alpha. Water/grass/foliage tint paths remain deterministic; snow/ice/sand/stone remain recognizable.

- [ ] Test flat terrain unchanged, rising/falling slopes produce opposite bounded brightness changes, seams use identical neighbor inputs, alpha preserved.
- [ ] Implement colorizer/shading and run tests.
- [ ] Commit `feat: add map terrain coloring and relief shading`.

### Task 6: Adapt seed renderer and compose explored-over-seed hybrid tiles

**Files:** create `SeedTerrainProvider`, `ExploredTerrainProvider`, `HybridTerrainProvider`; modify existing biome tile calculation only behind provider adapter; add hybrid tests.

**Interfaces:**
- Seed provider derives canonical tiles asynchronously from existing Cubiomes data and `TerrainColorizer.seedColor`.
- Explored provider reads only chunk files intersecting the requested `MapTileKey`; missing chunks remain transparent in its internal tile plus explored mask.
- Hybrid provider requests seed + explored and replaces only `exploredPixels`; no alpha holes are allowed in HYBRID mode.
- EXPLORED mode uses a distinct unknown-region checker/dim color rather than seed pixels.
- SEED and BIOME never read explored storage; TERRAIN prefers explored shading and seed terrain approximation where available.

- [ ] Test fully unexplored => seed tile; partially explored => only explored mask replaces seed; fully explored => explored result; failed explored read => seed result.
- [ ] Test stale generation completion is discarded.
- [ ] Implement providers and run focused tests.
- [ ] Commit `feat: compose explored and seed map terrain`.

### Task 7: Add bounded CPU/GPU caches and LOD pyramid

**Files:** create `BoundedTileCache`, `MapTextureCache`, `MapTileDownsampler`; tests all three.

**Defaults:** CPU rendered-tile cache max `96` tiles; GPU texture cache max `48` textures; visible viewport requests one-tile prefetch ring. Config values may be added but defaults must stay bounded.

**Interfaces:**
- `BoundedTileCache<K,V>(int maxEntries)` access-order LRU; optional eviction callback.
- `MapTextureCache.put(MapTileKey, DynamicTexture)` closes replaced/evicted textures exactly once; `close()` closes all.
- `MapTileDownsampler.downsample(MapTileData nw, ne, sw, se, MapTileKey parent)` creates one 256x256 parent tile using 2x2 area averaging weighted to preserve water/major boundary contrast; explored parent pixel is explored only when at least one source sample is explored, with source color chosen from averaged samples.
- LOD selection from Phase A zoom is deterministic: choose the highest-detail LOD whose blocks-per-screen-pixel does not exceed 2x; never request LOD0 for maximum far zoom.

- [ ] Test LRU order, eviction callbacks, texture-close bookkeeping using a closeable test adapter, and downsample colors/masks.
- [ ] Test far zoom requests bounded tile counts independent of total explored cache size.
- [ ] Implement and run tests.
- [ ] Commit `feat: bound map tile memory and add lod`.

### Task 8: Integrate canonical tiles, view modes and tooltip samples into fullscreen map

**Files:** modify `SeedMapScreen`, `SeedMapRenderCore`, `Configs`; optionally add `MapOverlayRenderer` only for moving existing overlay draw calls out of `SeedMapScreen` without behavior changes.

**Interfaces/behavior:**
- default `Configs.SeedMapViewMode = HYBRID`.
- Map category from Phase A exposes `Hybrid / Explored / Seed / Biome / Terrain`.
- viewport computes visible `MapTileKey`s for selected LOD; request visible + one-ring prefetch; draw only visible tiles.
- tooltip uses provider `sample(...)`: exact explored Y/block/source where cached; seed preview labels source as `Seed Prediction` and never reports estimated Y as exact.
- world/dimension changes call provider/cache close/invalidate before new generation begins.

- [ ] Add tests for mode persistence/default and visible-tile-key calculation at normal/far zoom.
- [ ] Wire provider selection and render path.
- [ ] Verify existing structure/loot/waypoint overlays remain above the new base map.
- [ ] Run full unit suite and build.
- [ ] Commit `feat: integrate hybrid terrain into seed map`.

### Task 9: MegaCraft8 persistence/performance runtime verification

**Files:** no production changes unless a test exposes a defect; record any fixes in focused commits.

- [ ] Build with portable JDK 25 and `./gradlew.bat clean build --stacktrace`.
- [ ] Backup/replace the MegaCraft8 SeedMapper JAR.
- [ ] Fresh area: open map before exploration and verify seed fallback.
- [ ] Travel/load chunks and verify explored detail replaces fallback.
- [ ] Move away/unload, reopen map, verify explored quality persists.
- [ ] Restart Minecraft, verify disk cache returns without scanning whole cache.
- [ ] Verify Overworld/Nether/End and a second world/server do not bleed tiles.
- [ ] Zoom from block detail to maximum zoom-out; observe responsiveness and task/log counts.
- [ ] Explore enough area to validate bounded RAM/VRAM behavior; verify texture eviction/close logging in debug mode rather than keeping all tiles resident.
- [ ] Corrupt one copied test cache file and verify map falls back instead of crashing.
- [ ] Re-run structure, Loot Viewer, SimpleWaypoints, minimap and Baritone smoke tests.
- [ ] Push verified Phase B commits and record the verified head/JAR hash in the PR.
