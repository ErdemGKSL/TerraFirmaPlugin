---
id: PAR-WORLD-46
title: "Write the CE plant blocks in generation: TfcTerrain places tfp:plant/* through PlantBlocks.ceRef, placeholders only as fallback"
area: WORLD
status: open
wave: 27
deps: [PAR-WORLD-12]
size: S
aliases: ["PAR-WORLD-12 follow-up"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/plant/Plant.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/TFCTallGrassBlock.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/worldgen/TfcTerrain.java"
  - "paper/src/test/java/dev/tfp/paper/worldgen/PlantWiringTest.java"
ui: true
in_game_steps:
  - "Fresh world; screenshot a meadow (GUI scale 2): TFC grasses and flowers with TFC textures, not vanilla flowers"
  - "tfp debug block (and /ce debug target-block) on a flower and on both halves of a tall plant: tfp:plant/<name> with age and half"
plan:
  - "PAR-WORLD-12 generated the land and floating plants (`tfp:plant/<name>`, 100 blocks) and added `PlantBlocks.ceRef(PlantState)` (`tfp:plant/rose[age=2,half=upper]`, null for the types still on placeholders). `TfcTerrain.decorate`'s plant target still writes `plantData(...)` placeholders: resolve `ceRef` through the CE state table like `customTreeData` (cache per `visuals.generation()`), and fall back to the placeholder when CE has not loaded or does not know the id."
  - "Both halves of a tall CE plant go together (lower and upper ceRef), never a CE lower half under a placeholder upper half. The emergent/one-block placeholder special cases of `PlantBlocks.write` apply only to placeholders. `PlantBlocks.free` must treat a CE plant already in the chunk as occupied (it reads Material; a CE plant reads as its carrier's material: tripwire, sugar cane or a sapling, never air)."
tests:
  - "paper: PlantWiringTest: ceRef for every CE type (age, half), null for water types, wild crops and corals; a tall plant writes two CE halves"
done_criteria:
  - "Fresh world shows TFC flowers/grasses (screenshot; moved here from PAR-WORLD-12, deferred check per PROTOCOL 6.5)"
---

# PAR-WORLD-46: Write the CE plant blocks in generation

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
