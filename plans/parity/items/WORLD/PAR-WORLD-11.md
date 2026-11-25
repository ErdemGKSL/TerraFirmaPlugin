---
id: PAR-WORLD-11
title: "Tree generation: forest placement on every chunk, dead forests, mangroves"
area: WORLD
status: done
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
  - "core/src/main/java/dev/tfp/core/worldgen/tree/**"
  - "core/src/test/java/dev/tfp/core/worldgen/tree/**"
  - "core/src/test/resources/dev/tfp/core/tree/**"
  - "paper/src/main/java/dev/tfp/paper/worldgen/TfcTerrain.java"
  - "paper/src/main/java/dev/tfp/paper/worldgen/TreeResources.java"
ui: false
in_game_steps:
  - "Locate a mangrove biome via tfp debug chunk; screenshot"
plan:
  - "Unblocked 2026-11-25 (world session): files_owned widened to the real tree paths and the TfcTerrain.decorate hook line, as the blocked note asked."
  - "Close the 21 open species (19 dead entries, mangrove, dead mangrove) in core/tree/open_gaps.txt; place trees for every chunk on the region pass; forest density/type map compared with TFC."
  - "BLOCKED (2026-11-25): files_owned names core/src/main/java/dev/tfp/core/tree/**, which does not exist; the tree code lives in core/src/main/java/dev/tfp/core/worldgen/tree/** (TreeData loads only the forest_trees.json tag; the dead_forest_trees and mangrove_forest_trees tags, the mangrove's special root placer and the Forest/TreePlacer changes go there), the open gaps list is core/src/test/resources/dev/tfp/core/tree/open_gaps.txt (outside core/src/test/java), and choosing dead and mangrove forests per biome needs paper/.../worldgen/TfcTerrain.java besides TreeResources. Unblock: widen files_owned to core/src/main/java/dev/tfp/core/worldgen/tree/**, core/src/test/java/dev/tfp/core/worldgen/tree/**, core/src/test/resources/dev/tfp/core/tree/** and a hook line in TfcTerrain.decorate. No code changed."
  - "Done (2026-11-25): core Forest runs tfc:forest, tfc:dead_forest (lowlands, after the forest, shared tree world) and tfc:mangrove_forest (salt marshes) with their own feature randoms; TreeData loads the three tags and any entry; TreePlacer ports SpecialRootPlacer (base rise, root lines, tfc:tree_roots, returned trunk base) and the root placers return the base y; TreeBlock.Kind/TreeGround TREE_ROOTS; paper TreeResources.forest + TfcTerrain hook (waterlogged mangrove_roots placeholder in water) + one case line in TreeBlocks.placeholder (outside files_owned, required by the exhaustive switch). open_gaps.txt empty; tests: SpecialForestTest 4 new, TreePlacementGoldenTest checks all 40 entries, core tree tests 21 pass, paper worldgen tests pass. No oracle golden for the special root placer (ledger divergence). In-game check deferred to PAR-WORLD-55."
tests:
  - "core: TreePlacementGoldenTest open gaps empty"
done_criteria:
  - "core: TreePlacementGoldenTest open gaps empty (passes)"
  - "Deferred check tracked in PAR-WORLD-55"
---

# PAR-WORLD-11: Tree generation: forest placement on every chunk, dead forests, mangroves

> see_not_parity: NP-WORLD-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
