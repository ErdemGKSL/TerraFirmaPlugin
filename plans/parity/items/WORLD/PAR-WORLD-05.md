---
id: PAR-WORLD-05
title: "Ice sheets, sea ice and icebergs at generation; lava flows; remaining biome surface builders"
area: WORLD
status: open
wave: 18
deps: [PAR-WORLD-04, PAR-CLIM-04]
size: M
aliases: ["M2-GEN-08 (remaining)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/world/surface/builder"
  - "src/main/java/net/dries007/tfc/world/feature/IceAndSnowFeature.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/worldgen/surface/**"
  - "core/src/test/java/dev/tfp/core/worldgen/surface/**"
ui: false
in_game_steps: []
plan:
  - "Port the remaining builders (biome table currently covers 46 biomes with the normal builder fallback) and the ice and snow feature; sea ice at generation with icebergs via core/weather/SeaIce."
  - "Golden column checks against the TFC oracle as for SurfaceGoldenTest."
tests:
  - "core: SurfaceGoldenTest extended to every builder"
done_criteria:
  - "Every TFC biome uses its own builder; golden green"
---

# PAR-WORLD-05: Ice sheets, sea ice and icebergs at generation; lava flows; remaining biome surface builders

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
