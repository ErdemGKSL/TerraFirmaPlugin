# S1: ore overlay census and entity budget (PAR-WORLD-17)

**Status:** measured 2026-11-04 on Paper 1.21.11 build 132 (census also on Folia 1.21.11), client 1.21.11 under Xvfb +
Mesa llvmpipe. CraftEngine 26.9.2, BetterModel 3.5.1, BetterHud 2.2.0-SNAPSHOT-449, PacketEvents 2.14.0. Tree at
`1b912b5` plus this item. **Verdict: FAIL, owner question below. Defaults not changed** (`ore-overlay.cutoff-blocks: 48`,
`max-per-player: 8000`).

Design under test: `docs/research/ore-entity-renderer-design.md` (ores drawn as packet-only `item_display` quads on exposed
faces, ARCHITECTURE.md §9.1).

## 1. Method

### Census (`ore.census`, itest)
- Code: `core/oreoverlay/census` (pure: `ExposureRule`, `ChunkView`, `ArrayChunkView`, `ExposureCensus`, `ChunkCensus`,
  `CensusReport`) and `itest/.../OreExposureCensusCase` (registered in `SelfTestSuite`).
- Area: 32 × 32 chunks centred on the world spawn chunk, plus a one-chunk ring for the border cells. Rows are generated with
  `getChunkAtAsync`, every chunk is snapshotted on its own region thread, classified into cell codes on the CPU pool, and
  the census of a row runs on the CPU pool once its neighbours are classified. No world writes, no force-loading.
- Exposure rule (`ExposureRule`, reused by PAR-INFRA-16): a face is exposed when the neighbour cell is air, a fluid
  (water, lava, bubble column, waterlogged) or any non-occluding state. Cells above the world are open, below are solid.
- Depth band of a face: **surface** when the open cell is at or above the column's `WORLD_SURFACE` height − 8, else
  **cave**.
- Targets:
  - our TFC world: every ore id the cluster veins can place (counted) and the 21 raw rocks (per-target only);
  - plain vanilla world, same seed: stone, deepslate, granite, diorite, andesite, tuff and every `*_ORE` (all counted). This
    is the cave-exposure proxy, because our world has no tunnels yet (PAR-WORLD-06).
- Radius totals: overlays a player sees with the gated radius (4 chunks, 81 chunks: the 48 m cutoff) and with full view
  distance 6 / 8 / 10 (169 / 289 / 441 chunks). The JSON uses 20 seeded sample positions per radius. The tables below
  also give the exhaustive figure over every window position in the area (from `per_chunk_raw`), which is more robust.
- Combined estimate per chunk = measured surface faces + max(measured cave faces, ore blocks × vanilla cave faces per
  block). Vanilla cave faces per block, mean of seeds 1 to 3: **0.0659**.
- Run: `spikes/ore-overlay/run-all.sh <scratch> <port> 32` (vanilla seeds 1, 2, 3; TFC seeds 1, 2, 3; then Folia).
  Reports: `spikes/ore-overlay/out/ore-census-<kind>-<seed>-<platform>.json`. A census of 32 × 32 chunks takes
  50-65 s (vanilla) and 100-125 s (TFC, generation included) on 4 cores.

### Client cost (spike plugin, not shipped)
- `spikes/ore-overlay` (stand-alone Gradle build, plugin `TfpSpikeOreOverlay`): `oreoverlay field <player> <n>` sends a
  synthetic field of n packet-only `item_display` quads through PacketEvents, gated like the planned runtime. The chunks
  within 4 chunks of the player carry n / 81 quads each: spawned when the chunk enters the radius, removed when it
  leaves. Each quad sits in an air cell with translation −0.49 along the face normal and `view_range` 0.75 (48 m).
- Model: `tfpspike:overlay`, one 16 × 16 quad (shade off, no AO) with TFC's `normal_native_gold` overlay texture.
  `build-pack.sh` copies the texture from the TFC checkout at run time.
- `oreoverlay stats` reports the packets and uncompressed bytes sent to the player (PacketEvents monitor listener, all
  packets), the spawns and removals, the largest per-check burst and the server's average tick time.
- Run: `spikes/ore-overlay/run-client-cost.sh` (seed 2 TFC world, Bot in creative 20 blocks above the terrain, pitch 20,
  view distance 10, F3 on). Fields of 0, 2 k, 8 k and 20 k quads, 12 s each, then an 8 k field while Bot is teleported
  5 blocks every 0.5 s (10 blocks/s) for 30 s.

## 2. Census results

### Vanilla world (exposure proxy), 32 × 32 chunks

| Seed | Origin chunk | Target blocks per chunk p50 | Exposed per block | Faces per exposed | Cave faces per block | Vanilla ores only: exposed per block / faces per block |
|---|---|---|---|---|---|---|
| 1 | -6, -6 | 26 177 | 4.22 % | 1.55 | 0.0647 | 3.77 % / 0.058 |
| 2 | -18, -16 | 30 788 | 3.51 % | 1.57 | 0.0538 | 2.71 % / 0.043 |
| 3 | -16, -16 | 26 970 | 5.09 % | 1.57 | 0.0791 | 4.50 % / 0.071 |

Surface-band exposure of stone in vanilla is close to zero (0.03-0.09 % of blocks). Soil and grass cover the stone. In
vanilla, nearly every exposed face is a cave face. Round 1 assumed 5 % exposure and 1.5 faces per exposed ore. The measured
3.5-5.1 % and 1.55-1.57 agree.

### Our TFC world (measured, no tunnels yet), 32 × 32 chunks

| Seed | Origin chunk | Ore blocks per chunk p50 / p95 / max (mean) | Exposed ore faces, whole area | Faces per chunk max | Gated radius p95 | Raw rock exposed per block |
|---|---|---|---|---|---|---|
| 1 | -93, -171 | 322 / 4 747 / 8 093 (1 074) | 26 (cave) | 26 | 0 | 0.10 % |
| 2 | 9, 6 | 60 / 3 350 / 8 193 (620) | 42 (surface) | 29 | 0 | 0.005 % |
| 3 | -3, 174 | 351 / 4 243 / 8 134 (1 003) | 0 | 0 | 0 | 0.007 % |

- This is the lower bound until caves exist. Our world today exposes almost no ore. The 42 faces of seed 2 lie on a sea
  bed at water level (exposed example 336, 61, 547). No cliff or outcrop shows an ore.
- Ore density is **2-3.5 × the research estimate** (mean ≈ 300 per chunk lowland, `ore-entity-renderer-design.md` §4.1).
  The median is close to it (60-351), but large clusters lift the mean: a chunk inside one holds up to ≈ 8 100 ore blocks.
  The grade mix also leans to rich (for example seed 1: rich bismuthinite 186 per chunk, normal 77, poor 47). TFC's
  per-vein weights alone would not give that. PAR-WORLD-08 (distribution statistics) should check the per-ore densities
  against TFC.
- Seed 1 has one **refused chunk** (-62, -166: `IllegalStateException: no element above 100.0` in `Weighted.get`). The
  census reports it in `empty_chunks` and treats its cells as solid, so the empty chunk exposes no neighbour. Follow-up:
  PAR-WORLD-18.

### Combined estimate (measured surface + ore blocks × vanilla cave exposure)

| Seed | Faces per chunk p50 / p95 / max (mean) | Gated radius, 20 samples p50 / p95 / max | Gated radius, all windows p50 / p95 / max (share > 8 k) | 40 m cutoff (radius 3, 49 chunks), all windows p50 / p95 / max | VD 6 p50 / p95 | VD 8 p50 / p95 | VD 10 p50 / p95 |
|---|---|---|---|---|---|---|---|
| 1 | 21 / 313 / 533 (71) | 5 775 / 8 488 / 8 538 | 6 055 / **9 388** / 10 287 (17 %) | 3 577 / 6 242 / 8 131 | 13 058 / 15 998 | 18 828 / 23 750 | 33 022 / 37 944 |
| 2 | 4 / 221 / 540 (41) | 3 568 / 6 407 / 6 890 | 3 844 / 6 796 / 8 356 (0.3 %) | 2 169 / 4 630 / 6 094 | 6 928 / 12 738 | 14 258 / 16 599 | 20 650 / 24 570 |
| 3 | 23 / 279 / 536 (66) | 4 397 / 8 040 / 9 253 | 4 566 / **8 579** / 10 051 (8 %) | 2 951 / 5 860 / 6 961 | 9 263 / 14 755 | 15 182 / 22 560 | 25 174 / 28 414 |

Research assumptions for comparison: 22 faces per chunk typical and 96 worst average. Gated radius 1.8 k typical and 7.8 k
worst. The measured median chunk (4-23) is close to "typical". The mean (41-71) and the gated totals are 2-3 × higher,
because the cluster veins are denser than estimated, and the overlay count follows the ore count. Per-ore entities
instead of per-face would divide everything by 1.55.

## 3. Client cost (seed 2, Paper 1.21.11, client 1.21.11 on Xvfb + llvmpipe, 1280 × 720)

| Quads | Client FPS (F3) | Frame time | Δ vs 0 | Packets for the field | Uncompressed bytes per quad | Server average tick time |
|---|---|---|---|---|---|---|
| 0 | 16 | 62.5 ms | — | — | — | 5.07 ms |
| 2 025 | 15 | 66.7 ms | +4.2 ms | 4 050 | ≈ 120 B (spawn + data) | 5.28 ms |
| 8 019 | 12 | 83.3 ms | **+20.8 ms** | 16 038 | ≈ 120 B | 5.36 ms |
| 20 007 | 9 | 111 ms | +48.6 ms | 40 014 | ≈ 120 B | 6.59 ms |
| 8 019, flying 10 blocks/s | 11 | 91 ms | +28.5 ms | 24 057 spawned, 16 038 removed in 30 s | — | 9.33 ms (new terrain generated) |

- Packets: one `ADD_ENTITY` and one `SET_ENTITY_DATA` per quad. 8 k quads ≈ 0.95 MB uncompressed, sent in one burst on
  start (16 038 packets). The design estimated 1.6 MB for 7.8 k.
- Moving: each chunk crossing sends one new row of 9 chunks (≈ 890 quads at 8 k, ≈ 1 800 packets) and removes one row (9
  `REMOVE_ENTITIES`). At 10 blocks/s that is about one crossing every 1.6 s.
- Server: sending is cheap. The tick time stays within noise up to 8 k. The flying run's 9.3 ms comes from generating new
  terrain, not from the overlay.
- Client: on this software renderer each quad costs ≈ 2.4-2.6 µs per frame, linear in the count. llvmpipe rasterises on
  the same 4 CPU cores the server uses, so these absolute numbers **cannot judge the 2 ms gate**. What carries over to a
  real client is the per-entity CPU work (entity dispatch, light lookup, matrix per display), and at 8 k that is likely
  several ms on desktop CPUs as well. GPU measurement is pending: PAR-WORLD-19.
- Screenshots (F3 visible): `docs/verification/PAR-WORLD-17/field-*-f3.png`, FPS counters at 8×: `fps-counter-8x.png`,
  overlay quads at 8×: `overlay-quads-8x.png`.

## 4. Verdict against the gate

| Gate (design §4.3) | Result |
|---|---|
| p95 faces within the gated radius ≤ 8 k per player | **Fails on the combined estimate** for 2 of 3 seeds (all-window p95 9.4 k and 8.6 k; 8 % and 17 % of positions above 8 k; max 10.3 k). Passes on today's world (0, no caves) and at a 40 m cutoff (p95 ≤ 6.3 k) |
| Client frame time + ≤ 2 ms at 8 k | **Fails on the test client** (+20.8 ms on llvmpipe). A GPU measurement is still needed (PAR-WORLD-19) |
| Join MSPT unchanged within noise | Passes (5.07 → 5.36 ms at 8 k) |

**Owner question (recorded, defaults unchanged):** the overlay as designed needs a decision before PAR-INFRA-16:
1. **Lower the cutoff to 40 m** (3-chunk radius, 49 chunks): p95 4.6-6.2 k on the estimate, within the cap everywhere
   measured. Specks fade one ring earlier.
2. **Keep 48 m and rely on `max-per-player: 8000` nearest-first**: in 8-17 % of positions near large veins the farthest
   faces (> ≈ 40 m) are dropped. This does not reduce client cost below 8 k.
3. **Lower the cap** (for example 4 000) to cut client cost. The cap then binds in most positions near veins.
4. **Per-ore entities instead of per-face** (÷ 1.55 entities, lighting error, see design §3.1), or a different client
   path entirely if the GPU measurement also shows several ms at 8 k.

Recommended defaults if the owner picks option 1: `ore-overlay.cutoff-blocks: 40`, `max-per-player: 6000`,
`surface-outcrops-full-range: true` (outcrops are rare: 0-42 faces per 1 024 chunks today).

## 5. Exposed vein reference

Our world has no tunnels yet, so it has no exposed ore on a cave wall or cliff. The only exposed ores are on lake and sea
beds at water level. The attempted reference (`docs/verification/PAR-WORLD-17/vein-seabed-*.png`, the exposed example
336, 61, 547 of seed 2) shows water and no recognisable ore at 4 m or 64 m. The cave-wall reference at 4/16/32/48/64 m
and its 8× crop are deferred to **PAR-WORLD-19** (depends on PAR-WORLD-06), together with the census rerun on a world
with caves and the GPU client cost.

## 6. For PAR-WORLD-06 and later

- Rerun `ore.census` (`spikes/ore-overlay/run-all.sh`) once tunnels and canyons exist and replace the combined estimate
  with measured numbers (PAR-WORLD-19).
- The default self-test suite runs `ore.census` on a 4 × 4 area (`-Dtfp.oreCensus.chunks=<n>` for more,
  `-Dtfp.oreCensus.caveFacesPerBlock=<x>` adds the estimate).
