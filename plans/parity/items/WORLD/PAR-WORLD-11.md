---
id: PAR-WORLD-11
title: "Tree generation: forest placement on every chunk, dead forests, mangroves"
area: WORLD
status: blocked
wave: 23
deps: [PAR-WORLD-10]
size: M
aliases: ["M2-GEN-12"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/world/feature/tree/ForestFeature.java"
  - "src/main/java/net/dries007/tfc/world/feature/tree/TreeHelpers.java"
  - "src/main/resources/data/tfc/structure"
files_owned:
  - "core/src/main/java/dev/tfp/core/tree/**"
  - "core/src/test/java/dev/tfp/core/tree/**"
  - "paper/src/main/java/dev/tfp/paper/worldgen/TreeResources.java"
ui: true
in_game_steps:
  - "Locate a mangrove biome via tfp debug chunk; screenshot"
plan:
  - "Close the 21 open species (19 dead entries, mangrove, dead mangrove) in core/tree/open_gaps.txt; place trees for every chunk on the region pass; forest density/type map compared with TFC."
  - "BLOCKED (2026-11-25): files_owned names core/src/main/java/dev/tfp/core/tree/**, which does not exist; the tree code lives in core/src/main/java/dev/tfp/core/worldgen/tree/** (TreeData loads only the forest_trees.json tag; the dead_forest_trees and mangrove_forest_trees tags, the mangrove's special root placer and the Forest/TreePlacer changes go there), the open gaps list is core/src/test/resources/dev/tfp/core/tree/open_gaps.txt (outside core/src/test/java), and choosing dead and mangrove forests per biome needs paper/.../worldgen/TfcTerrain.java besides TreeResources. Unblock: widen files_owned to core/src/main/java/dev/tfp/core/worldgen/tree/**, core/src/test/java/dev/tfp/core/worldgen/tree/**, core/src/test/resources/dev/tfp/core/tree/** and a hook line in TfcTerrain.decorate. No code changed."
tests:
  - "core: TreePlacementGoldenTest open gaps empty"
done_criteria:
  - "Dead forests and mangroves appear in their biomes (screenshot)"
---

# PAR-WORLD-11: Tree generation: forest placement on every chunk, dead forests, mangroves

> see_not_parity: NP-WORLD-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
