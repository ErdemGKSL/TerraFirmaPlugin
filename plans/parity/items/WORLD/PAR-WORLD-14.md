---
id: PAR-WORLD-14
title: "Volcanic features: springs, rivulets, flood-fill lakes, calderas of other cones"
area: WORLD
status: open
wave: 25
deps: []
size: M
aliases: ["M2-GEN-14"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/world/volcano"
  - "src/main/java/net/dries007/tfc/world/feature/HotSpringFeature.java"
  - "src/main/java/net/dries007/tfc/world/feature/RivuletFeature.java"
  - "src/main/java/net/dries007/tfc/world/feature/FloodFillLakeFeature.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/worldgen/volcano/**"
  - "core/src/test/java/dev/tfp/core/worldgen/volcano/**"
  - "core/src/test/resources/dev/tfp/core/worldgen/volcano*"
ui: false
in_game_steps: []
plan:
  - "Close the 51 open ids of volcano_features_open_gaps.txt (springs, rivulets, flood fill lakes, cone/shield patches, crater selectors) with goldens."
tests:
  - "core: VolcanoFeatureGoldenTest open gaps empty"
done_criteria:
  - "Open gaps empty; showcase seeds documented in docs/worldgen"
---

# PAR-WORLD-14: Volcanic features: springs, rivulets, flood-fill lakes, calderas of other cones

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
