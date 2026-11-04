---
id: PAR-WORLD-19
title: "Ore overlay S1 rerun on a world with caves: census, exposed-vein reference screenshots, GPU client cost"
area: WORLD
status: open
wave: 20
deps: [PAR-WORLD-06]
size: S
aliases: ["deferred check of PAR-WORLD-17 (PROTOCOL 6.5)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/world/ChunkNoiseFiller.java"
  - "src/main/resources/data/tfc/worldgen/configured_carver"
files_owned:
  - "docs/verification/PAR-WORLD-19/**"
ui: true
in_game_steps:
  - "Census: `spikes/ore-overlay/run-all.sh <scratch> <port> 32` (PAR-WORLD-17 harness) on our world once PAR-WORLD-06 carves tunnels and canyons; compare the measured TFC-world faces per chunk and gated-radius totals with the S1 combined estimate"
  - "Exposed vein reference: `spikes/ore-overlay/run-vein-shots.sh` on a cave wall or cliff taken from the new census' `exposed_example`, noon, pitch 20 deg, at 4, 16, 32, 48 and 64 blocks; 8x crop of one ore face"
  - "Client cost on a client with a real GPU (not Xvfb + llvmpipe): `spikes/ore-overlay/run-client-cost.sh` steps for 0, 2 k, 8 k and 20 k quads, F3 frame time"
plan:
  - "Current: PAR-WORLD-17 (S1) measured our world without tunnels (≤ 42 exposed ore faces in 1024 chunks per seed, all on lake or sea beds) and estimated the cave exposure from a vanilla world; its exposed-vein reference could only show a sea-bed ore, and its client cost ran on llvmpipe."
  - "Rerun the three steps above and update docs/spikes/S1-ore-overlay.md (census tables, verdict) with the measured numbers."
tests:
  - "itest: ore.census passes on Paper and Folia 1.21.11"
done_criteria:
  - "docs/spikes/S1-ore-overlay.md updated with the cave-world census and the GPU frame times; screenshots in docs/verification/PAR-WORLD-19/"
---

# PAR-WORLD-19: Ore overlay S1 rerun on a world with caves

Deferred in-game check of PAR-WORLD-17 (PROTOCOL §6.5). Paths in `tfc_refs` are relative to the TFC checkout
(`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
