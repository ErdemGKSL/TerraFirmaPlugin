---
id: PAR-WORLD-01
title: "Rendered region and river maps compared with TFC"
area: WORLD
status: done
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
  - "Done: RegionMapRenderColours ports TFC's taskColor schemes, Artist gradients and drawWithRivers; RegionMapRenderTest (15 maps x 3 seeds, TFC's window centre (0, 75) radius 200) and RiverMapRenderTest (river fractal map, 3 seeds) pin pixel hashes. A scratch oracle compiled TFC's region/river/noise/layer classes at c3b4d9536 and rendered the same maps: pixel diff 0 over all 48 images. Side-by-sides and the diff table in docs/worldgen/maps/README.md; ledger divergence row updated; M2-GEN-03 / M2-GEN-04 map checks closed."
tests:
  - "core: pixel hash per seed pinned"
done_criteria:
  - "Side-by-side maps committed; diff 0 or every difference explained in the ledger"
---

# PAR-WORLD-01: Rendered region and river maps compared with TFC

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
