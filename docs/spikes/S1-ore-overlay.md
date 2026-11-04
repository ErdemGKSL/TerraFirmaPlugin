# S1: ore overlay census and entity budget (PAR-WORLD-17)

**Status:** measured 2026-11-04 on Paper 1.21.11 build 132 (census also on Folia 1.21.11), client 1.21.11 under Xvfb +
Mesa llvmpipe. CraftEngine 26.9.2, BetterModel 3.5.1, BetterHud 2.2.0-SNAPSHOT-449, PacketEvents 2.14.0. Tree at
`1b912b5` plus this item; the client cost was re-measured on `71fbeb0` plus the review fix (quads on real faces, F3 + 2
frame times). **Verdict: FAIL, owner question below. Defaults not changed** (`ore-overlay.cutoff-blocks: 48`,
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
- Two known biases of this estimate, in opposite directions:
  - vanilla probably **over-states** cave exposure against TFC. TFC's carvers are rarer (`configured_carver/cave.json`
    probability 0.02 and `deep_cave.json` 0.08 at y −56..48, against vanilla's `cave` 0.15 and
    `cave_extra_underground` 0.07; `canyon` is 0.01 in both), and TFC's `density_function/noise_caves.json` uses
    `cave_cheese`, `cave_layer`, `cave_entrance` and `spaghetti_roughness` without vanilla's `spaghetti_2d`/`3d` and pillar
    noises (noodle caves are separate: `noodle_caves.json`, `noodle_ridge_a/b.json`, `noodle_thickness.json`);
  - our ore count **under-states** TFC's (§2: only the cluster veins are placed).
- Run: `spikes/ore-overlay/run-all.sh <scratch> <port> 32` (vanilla seeds 1, 2, 3; TFC seeds 1, 2, 3; then Folia).
  Reports: `spikes/ore-overlay/out/ore-census-<kind>-<seed>-<platform>.json`. A census of 32 × 32 chunks takes
  50-65 s (vanilla) and 100-125 s (TFC, generation included) on 4 cores.

### Client cost (spike plugin, not shipped)
- `spikes/ore-overlay` (stand-alone Gradle build, plugin `TfpSpikeOreOverlay`): `oreoverlay field <player> <n>` sends a
  synthetic field of n packet-only `item_display` quads through PacketEvents, gated like the planned runtime. The chunks
  within 4 chunks of the player carry n / 81 quads each: spawned when the chunk enters the radius, removed when it
  leaves. **Each quad lies on a real exposed face:** the chunk is snapshotted on its region thread, the async pool lists
  every face of an occluding block whose neighbour is not occluding (the census' exposure rule; faces across the chunk
  edge are skipped) and picks n / 81 of them at random. The entity sits in the centre of the open cell, translated
  −0.49 along the face normal (0.01 in front of the face), `view_range` 0.75 (48 m). Any occluding block counts, so the
  field stands in for "n exposed ore faces" at the positions real faces have (≈ 1 360-1 550 candidate faces per chunk
  here, so every chunk can carry the 247 quads of the 20 k field).
- Model: `tfpspike:overlay`, one 16 × 16 quad (shade off, no AO) with TFC's `normal_native_gold` overlay texture.
  `build-pack.sh` copies the texture from the TFC checkout at run time. The quad faces −z in the model because the
  `item_display` renderer turns item models 180° about y; facing the other way it would sit on the face but be back-face
  culled.
- `oreoverlay stats` reports the packets and uncompressed bytes sent to the player (PacketEvents monitor listener, all
  packets), the spawns and removals, the candidate faces per chunk, the largest burst per chunk crossing and the
  server's average tick time.
- Run: `spikes/ore-overlay/run-client-cost.sh <run> <port> <display> <shots>` (seed 2 TFC world, view distance 10, client
  render distance 6, 1280 × 720):
  - client options: `inactivityFpsLimit:minimized` (the default `afk` caps the frame rate at 30 after a minute without
    input, the "T:30" of the first capture), vsync off, `maxFps` 260;
  - Bot types `/hud turn off`, so the BetterHud date panel no longer covers the first F3 line; Bot in spectator;
  - stone test area: a 64 × 64 stepped stone quarry at spawn (8 terraces 2 high and 3 wide, floor at y 64), Bot hovering
    south of the rim at floor + 26, looking north, pitch 30;
  - F3 and the frame-time chart (F3 + 2) on. Fields of 0, 2 k, 8 k and 20 k quads, 27 s each, then a screenshot: the
    chart's min / avg / max cover its last 240 frames, ≈ 15 s at 15-16 fps, all after the field was spawned;
  - flight: 8 k field, then Bot moves east 5 blocks every 0.5 s (10 blocks/s) for 30 s, 20 blocks above the world
    surface looking ahead and down (pitch 35); screenshots at 10, 20 and 30 s;
  - server: Paper `mspt` (5 s average) and `spark tps` after each field.
- Differences from the plan's wording: the spike command is `oreoverlay field|clear|stats` (a stand-alone spike plugin;
  `tfp debug overlayfield` / `tfp debug packets` would have put spike code into the shipped plugin), the stone test area
  is built by `run-client-cost.sh` (`spikes/client/testroom.sh` builds a grass colour room), and the frame time comes from
  the F3 + 2 chart average, not a separate log.

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

- **These measured faces per chunk (0 / 42 / 0 in the whole area, at most 26-29 in one chunk) are a lower bound** until
  our world has caves (PAR-WORLD-06): today the only open cells next to ore are lakes and sea beds. The cave-world rerun is
  PAR-WORLD-19. The 42 faces of seed 2 lie on a sea bed at water level (exposed example 336, 61, 547). No cliff or
  outcrop shows an ore.
- Ore density is **2-3.5 × the research estimate** (mean ≈ 300 per chunk lowland, `ore-entity-renderer-design.md` §4.1).
  The median is close to it (60-351), but large clusters lift the mean: a chunk inside one holds up to ≈ 8 100 ore blocks.
- **The ore count is itself a lower bound of TFC's.** Our world places only TFC's 33 cluster veins. It does not yet place
  the disc veins (gravel, lignite, bituminous coal, graphite, sulfur, salts, lapis, gems such as amethyst and opal,
  `ruby_marble_belt`), the pipe veins (diamond, emerald, the dikes), `kaolin_disc`, `tfc:geode`, or the montane second
  pass in mountain biomes (all recorded in the ledger's Divergences table). TFC's real overlay count is therefore higher
  still; the FAIL verdict below does not depend on them.
- The grade mix matches TFC's weights. Seed 1: rich bismuthinite 186 per chunk, normal 77, poor 47 = 60.0 / 24.8 / 15.2 %,
  exactly the 60 / 25 / 15 weights of `vein/normal_bismuthinite.json` at the pin, which is also the dominant bismuthinite
  vein (size 40, density 0.6, y −80..20, against size 20 and density 0.3 for the surface and montane veins). The same
  rich-heavy weights apply to `normal_native_silver`, `normal_sphalerite` and `rich_native_gold`.
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

Quads on real exposed faces of a stone quarry and the terrain around it (§1). Frame time = the F3 + 2 chart over its last
240 frames (≈ 15 s), read from the screenshot (`frame-time-3x.png`). Server = Paper `mspt`, 5 s average.

| Quads | Client FPS (F3) | Frame time avg (min / max) | Δ avg vs 0 | Packets for the field | Uncompressed bytes per quad | Server MSPT (5 s avg) |
|---|---|---|---|---|---|---|
| 0 | 16 | 63 ms (48 / 93) | — | — | — | 2.4 ms |
| 2 025 | 16 | 65 ms (54 / 96) | +2 ms | 4 050 | ≈ 125 B (spawn + data) | 2.7 ms |
| 8 019 | 15 | 69 ms (58 / 94) | **+6 ms** | 16 038 | ≈ 125 B | 1.9 ms |
| 20 007 | 13 | 78 ms (68 / 99) | +15 ms | 40 014 | ≈ 125 B | 1.8 ms |
| 8 019, flying 10 blocks/s (10 / 20 / 30 s) | 15 / 18 / 9 | 70 / 67 / 71 ms (max 112 / 118 / 148) | +4 to +8 ms | 24 057 spawned, 16 038 removed in 30 s | — | 11.1 ms (new terrain generated) |

- Packets: one `ADD_ENTITY` and one `SET_ENTITY_DATA` per quad. 8 k quads ≈ 1 MB uncompressed, sent in one burst on start
  (16 038 packets). The design estimated 1.6 MB for 7.8 k. Bytes per quad = the difference of the 8 k and 2 k totals per
  extra quad.
- Moving: each chunk crossing sends one new row of 9 chunks (≈ 890 quads at 8 k, ≈ 1 800 packets) and removes one row (9
  `REMOVE_ENTITIES`). At 10 blocks/s that is about one crossing every 1.6 s. The frame-time maximum rises from 94 to
  112-148 ms during the flight; the average stays within the static 8 k figure.
- Server: sending is cheap. The tick time does not rise with the field (2.4 → 1.9 ms at 8 k, all within the noise of a
  4-core machine shared with other runs). The flying run's 11 ms comes from generating new terrain, not from the overlay.
- Client: on this software renderer each quad costs ≈ 0.7-0.8 µs per frame, roughly linear in the count. llvmpipe
  rasterises on the same 4 CPU cores the server uses, so these absolute numbers cannot judge the 2 ms gate for a real
  client. What carries over is the per-entity CPU work (entity dispatch, light lookup, matrix per display). GPU
  measurement is pending: PAR-WORLD-19.
- The first capture of this item (random positions in the air, the quad facing away from its normal because of the
  item_display turn, the AFK cap at 30 fps, frame time derived from the FPS counter) gave +21 ms at 8 k. It is superseded by
  this table.
- Screenshots (F3 line 1 and the F3 + 2 chart visible, BetterHud panel off): `docs/verification/PAR-WORLD-17/field-0-f3.png`,
  `field-2000-f3.png`, `field-8000-f3.png`, `field-20000-f3.png`; flight `field-8000-flying-10s-f3.png`, `-20s-`, `-30s-`
  (at 30 s Bot is over the sea, where the sea-bed quads are under water and fog); readouts at 3×: `frame-time-3x.png`;
  quads at 8×: `quads-8x.png` (on the quarry floor) and `quads-edge-8x.png` (on terrace tops and on the south-facing walls,
  flush with the face and inside the block outline).

## 4. Verdict against the gate

| Gate (design §4.3) | Result |
|---|---|
| p95 faces within the gated radius ≤ 8 k per player | **Fails on the combined estimate** for 2 of 3 seeds (all-window p95 9.4 k and 8.6 k; 8 % and 17 % of positions above 8 k; max 10.3 k). Passes on today's world (0, no caves) and at a 40 m cutoff (p95 ≤ 6.3 k). Both the cluster-only ore count (higher in TFC) and the vanilla cave proxy (probably lower in TFC) are approximations, see §1 and §2 |
| Client frame time + ≤ 2 ms at 8 k | **Fails on the test client**: +6 ms average over 240 frames at 8 k (+2 ms at 2 k) on llvmpipe. A GPU measurement is still needed (PAR-WORLD-19) |
| Join MSPT unchanged within noise | Passes (2.4 → 1.9 ms at 8 k; 1.8 ms at 20 k) |

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
and its 8× crop are deferred to **PAR-WORLD-19** (depends on PAR-WORLD-06 and PAR-WORLD-08), together with the census
rerun on a world with caves and the GPU client cost.

The `exposed_example` of a **vanilla** census report (for example 176, 71, 128 of vanilla seed 1) is not an ore: the
vanilla proxy counts stone, deepslate, granite, diorite, andesite, tuff and the vanilla ores together (§1), and the example
is the first counted block with a surface-band face in the chunk with the most surface faces, almost always stone. A shot of it shows plain stone
by design; it is no vein reference.

## 6. For PAR-WORLD-06 and later

- Rerun `ore.census` (`spikes/ore-overlay/run-all.sh`) once tunnels and canyons exist and replace the combined estimate
  with measured numbers (PAR-WORLD-19).
- The default self-test suite runs `ore.census` on a 4 × 4 area (`-Dtfp.oreCensus.chunks=<n>` for more,
  `-Dtfp.oreCensus.caveFacesPerBlock=<x>` adds the estimate).
