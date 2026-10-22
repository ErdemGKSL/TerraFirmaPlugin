---
id: PAR-WORLD-17
title: "Ore overlay spike S1: measure exposed ore faces per chunk and the overlay entity budget"
area: WORLD
status: open
wave: 4
deps: [PAR-REL-01]
size: M
aliases: ["research: ce-block-visual-workarounds-2 S1", "owner decision 2026-10-22: ores as exposed-face overlays"]
tfc_refs:
  - "src/main/resources/data/tfc/worldgen/configured_feature/vein"
  - "src/main/resources/data/tfc/worldgen/configured_carver"
  - "src/main/resources/data/tfc/tags/worldgen/placed_feature/in_biome/veins.json"
  - "src/main/java/net/dries007/tfc/world/feature/vein/VeinFeature.java"
  - "src/main/java/net/dries007/tfc/world/feature/vein/ClusterVeinFeature.java"
  - "src/main/java/net/dries007/tfc/world/noise/Metaballs3D.java"
  - "src/main/java/net/dries007/tfc/world/ChunkNoiseFiller.java"
  - "src/main/java/net/dries007/tfc/common/blocks/rock/Ore.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/oreoverlay/census/**"
  - "core/src/test/java/dev/tfp/core/oreoverlay/census/**"
  - "itest/src/main/java/dev/tfp/itest/cases/OreExposureCensusCase.java"
  - "spikes/ore-overlay/**"
  - "docs/spikes/S1-ore-overlay.md"
ui: true
in_game_steps:
  - "Headless Paper 1.21.11 + client 1.21.11 (spikes/client, PAR-REL-01 recipe), TFC world seed 1; then the same seed as a plain vanilla world (exposure proxy, PAR-WORLD-06 has not ported the cave tunnels yet)"
  - "Census: run the itest case `ore.census` (or `tfp debug orecensus 32` from the spike plugin) over 32x32 chunks around spawn for seeds 1, 2, 3; it writes `itest/build/selftest-run/<platform>-<mc>/ore-census.json`"
  - "Exposed vein reference (before the overlay exists): tfp time set noon; tp Bot to a cave wall or cliff with a visible vein from the census output (`exposed_example`), pitch 20 deg; screenshot at 4, 16, 32, 48 and 64 blocks; 8x crop of one ore face"
  - "Client cost: in a stone test area (spikes/client/testroom.sh), spawn a synthetic field of packet-only overlay quads around Bot with the spike command `tfp debug overlayfield <n>` for n = 2000, 8000, 20000 (one item_display per face, positioned in the air cell, translation -0.49 along the normal, view_range for a 48 m cutoff); for each n record the F3 frame time average over 10 s, client FPS, join packet count and bytes (PacketEvents metrics `tfp debug packets`), server MSPT (spark); screenshot the F3 screen"
  - "Repeat the client-cost step with Bot flying through the field at 10 blocks/s to record chunk-crossing bursts"
plan:
  - "Current: no measurement exists. Round 1 (docs/research/block-budget-workarounds.md 5.1) guessed 6-50 exposed ores per chunk; docs/research/ore-entity-renderer-design.md 4 estimates from the TFC vein data at the pin: about 300 ore blocks per chunk (lowland), 400 (mountains), 1200-1500 in a chunk cut by a large vein; with 5 % / 12 % exposure and 1.5 / 2 faces per exposed ore: about 22 faces per chunk typical, 96 worst average, 450 in a hot chunk; with the 4-chunk send radius (81 chunks) about 1.8 k typical and 7.8 k worst overlay entities per player."
  - "Census logic (pure, core/oreoverlay/census): `ExposureCensus.count(ChunkView)` over a chunk plus a 1-block border: ore blocks, ores with at least one exposing neighbour (air, cave air, water, lava, any non-occluding state), exposed faces per direction, split into depth bands (air cell at or above WORLD_SURFACE heightmap - 8 = surface outcrop, else cave), per ore id. The same exposing-neighbour rule as the runtime (PAR-INFRA-16 reuses `ExposureRule`)."
  - "itest case `ore.census` (registered in the SelfTestSuite hub): pregenerate the area, take chunk snapshots on each chunk's region thread, run the census on the CPU pool, write p50/p95/max per chunk, per-ore totals and the totals within 4 chunks and at view distance 6/8/10 of 20 sample player positions; on a vanilla world count stone-family blocks and vanilla ores with an exposing neighbour as the exposure proxy (TFC 1.21 has vanilla-style noise and noodle caves plus carvers; ours has no tunnels yet)."
  - "Combine: TFC estimate = our ore density per chunk x the vanilla exposure fraction by depth band; report both the measured TFC-world numbers (lower bound until caves exist) and the combined estimate."
  - "Client-cost spike plugin in spikes/ore-overlay (not shipped): a `/tfp debug overlayfield <n>` style command that sends n packet-only item_display quads with a test overlay item model (one TFC overlay texture, e.g. normal_native_gold, shade false, constant tint per face) through PacketEvents, and removes them on a second call."
  - "Write docs/spikes/S1-ore-overlay.md: method, tables, screenshots, the pass/fail verdict against the gate (p95 faces within the gated radius <= 8 k per player; client frame time +<= 2 ms at 8 k; join MSPT unchanged within noise) and the recommended defaults for `ore-overlay.cutoff-blocks` and `max-per-player`. If it fails, record the owner question (lower cutoff, lower cap, per-ore entities) instead of changing defaults silently."
  - "Note for PAR-WORLD-06: rerun `ore.census` on our world once the cave tunnels exist and update the spike doc."
tests:
  - "core: ExposureCensusTest on synthetic chunks (single ore in air: 6 faces; ore in solid: 0; ore under water: 1; ore next to glass/leaves: exposed; columnar border cases across the chunk edge)"
  - "itest: `ore.census` runs on Paper 1.21.11 and Folia 1.21.11 without thread-ownership violations (run delegated per PROTOCOL 5.3)"
done_criteria:
  - "docs/spikes/S1-ore-overlay.md has the census tables (3 seeds, p50/p95/max, gated radius and view distance 6/8/10 totals), the vanilla exposure proxy, the client-cost table for 2 k/8 k/20 k quads and a pass/fail verdict"
  - "Screenshots of the exposed-vein reference and of the synthetic field (F3 visible) in docs/verification/PAR-WORLD-17/"
  - "ARCHITECTURE.md 9.1 and docs/research/ore-entity-renderer-design.md 4 updated with the measured numbers (or the owner question recorded)"
---

# PAR-WORLD-17: Ore overlay spike S1: measure exposed ore faces per chunk and the overlay entity budget

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.

Wave note: added after the owner decision of 2026-10-22 (ores as exposed-face overlays, `ARCHITECTURE.md` §9.1). Wave 4
already holds four items; this item is a fifth there on purpose (files disjoint from PAR-HUD-05, PAR-HUD-08, PAR-INFRA-10
and PAR-INFRA-11) so that the measurement lands before the datagen and runtime items (PAR-INFRA-15, PAR-INFRA-16).
It supersedes the gem-only exposure count in PAR-WORLD-03 (dep added there).
