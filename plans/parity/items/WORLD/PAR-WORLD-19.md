---
id: PAR-WORLD-19
title: "Ore overlay S1 rerun on a world with caves: census, exposed-vein reference screenshots, GPU client cost"
area: WORLD
status: done
wave: 20
deps: [PAR-WORLD-06, PAR-WORLD-08]
size: S
aliases: ["deferred check of PAR-WORLD-17 (PROTOCOL 6.5)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/world/ChunkNoiseFiller.java"
  - "src/main/resources/data/tfc/worldgen/configured_carver"
  - "src/generated/resources/data/tfc/worldgen/density_function/noise_caves.json"
files_owned:
  - "docs/verification/PAR-WORLD-19/**"
  - "docs/spikes/S1-ore-overlay.md"
  - "spikes/ore-overlay/**"
ui: true
in_game_steps:
  - "Census: `spikes/ore-overlay/run-all.sh <scratch> <port> 32` (PAR-WORLD-17 harness) on our world once PAR-WORLD-06 carves tunnels and canyons; compare the measured TFC-world faces per chunk and gated-radius totals with the S1 combined estimate"
  - "Exposed vein reference: `spikes/ore-overlay/run-vein-shots.sh` on a cave wall or cliff taken from the new census' `exposed_example`, noon, pitch 20 deg, at 4, 16, 32, 48 and 64 blocks; 8x crop of one ore face"
  - "Client cost on a client with a real GPU (not Xvfb + llvmpipe): `spikes/ore-overlay/run-client-cost.sh` steps for 0, 2 k, 8 k and 20 k quads, frame time read from the F3 + 2 chart (avg over the last 240 frames) or a logged frame-time average over 10 s, with the BetterHud panel off (`hud turn off Bot`) and the AFK frame cap off (`inactivityFpsLimit:minimized`), server MSPT from `mspt` / `spark tps`"
plan:
  - "Current: PAR-WORLD-17 (S1) measured our world without tunnels (≤ 42 exposed ore faces in 1024 chunks per seed, all on lake or sea beds) and estimated the cave exposure from a vanilla world; its exposed-vein reference could only show a sea-bed ore, and its client cost ran on llvmpipe."
  - "Run after PAR-WORLD-08 so the census counts every TFC vein type (disc and pipe veins, kaolin_disc, geodes, the montane second pass), not only the 33 cluster veins S1 saw."
  - "Done (2026-11-25): itest OreExposureCensusCase.tfcTargets counts every vein type (main tag cluster/disc/pipe, biome tags montane/collisional_mountains/river, the geode's tfp:ore layer; itest is outside files_owned, minimal edit for this item's census). spikes/ore-overlay/analyze.py gives the all-window radius 3/4/VD figures. Census rerun with caves on Paper 1.21.11 seeds 1-3 (32 x 32) and Folia 1.21.11 (4 x 4), ore.census passes on both; docs/spikes/S1-ore-overlay.md §2.3/§2.4/§4: gated p95 8.9 k / 11.3 k / 4.6 k (fails 2 of 3), 40 m p95 7.0 k / 7.6 k / 3.1 k (passes); measured cave faces per ore block 0.087-0.113 (> vanilla proxy 0.066). Refused chunks (65/62/12, VolcanoBlocks.rockBlockId index -1) -> follow-up PAR-WORLD-60."
  - "Pending in-game check (container cannot run client): the exposed-vein reference shots (run-vein-shots.sh on a cave-wall ore, 4/16/32/48/64 m, 8x crop, into docs/verification/PAR-WORLD-19/) and the GPU client cost (run-client-cost.sh 0/2k/8k/20k quads on a real GPU); steps in S1 §5."
  - "Rerun the three steps above and update docs/spikes/S1-ore-overlay.md (census tables, verdict) with the measured numbers."
tests:
  - "itest: ore.census passes on Paper and Folia 1.21.11"
done_criteria:
  - "docs/spikes/S1-ore-overlay.md updated with the cave-world census and the GPU frame times; screenshots in docs/verification/PAR-WORLD-19/"
---

# PAR-WORLD-19: Ore overlay S1 rerun on a world with caves

> Claimed: worker session_011osUp5nmwNAYj7g53gEDAm, 2026-11-20

Deferred in-game check of PAR-WORLD-17 (PROTOCOL §6.5). Paths in `tfc_refs` are relative to the TFC checkout
(`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
