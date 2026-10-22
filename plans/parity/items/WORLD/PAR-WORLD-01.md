---
id: PAR-WORLD-01
title: "Rendered region and river maps compared with TFC"
area: WORLD
status: open
wave: 16
deps: []
size: S
aliases: ["M2-GEN-03", "M2-GEN-04 (map)", "gaps: M2-GEN-03/04 maps"]
tfc_refs:
  - "src/test/java/net/dries007/tfc/test/drawing/RegionGeneratorTests.java"
  - "src/test/java/net/dries007/tfc/test/drawing/Artist.java"
  - "src/main/java/net/dries007/tfc/world/region/RegionGenerator.java"
  - "src/main/java/net/dries007/tfc/world/river/Flow.java"
files_owned:
  - "core/src/test/java/dev/tfp/core/worldgen/region/RegionMapRender*"
  - "core/src/test/java/dev/tfp/core/worldgen/river/RiverMapRender*"
  - "docs/worldgen/maps/**"
ui: false
in_game_steps: []
plan:
  - "Render region and river maps for seeds 42, -7, 20260917 with our port and with TFC's Artist (run TFC's drawing test in the scratch checkout), commit both PNGs under docs/worldgen/maps and a pixel-diff summary."
  - "Mark M2-GEN-03 / M2-GEN-04 map checks closed if the diff is 0."
tests:
  - "core: pixel hash per seed pinned"
done_criteria:
  - "Side-by-side maps committed; diff 0 or every difference explained in the ledger"
---

# PAR-WORLD-01: Rendered region and river maps compared with TFC

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
