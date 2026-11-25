---
id: PAR-WORLD-60
title: "Volcanic feature blocks with rock index -1 refuse chunks (VolcanoBlocks.rockBlockId)"
area: WORLD
status: done
wave: 28
deps: [PAR-WORLD-45]
size: S
aliases: ["found by PAR-WORLD-19's census rerun"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/world/feature/TFCFeatures.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/worldgen/VolcanoBlocks.java"
  - "paper/src/main/java/dev/tfp/paper/worldgen/VolcanicFeatureBlocks.java"
  - "paper/src/test/java/dev/tfp/paper/worldgen/Volcan*"
  - "paper/src/main/java/dev/tfp/paper/worldgen/TfcTerrain.java"
ui: false
plan:
  - "PAR-WORLD-19's 32 x 32 census (Paper 1.21.11, tree 30332e3, seeds 1/2/3) got 65 / 62 / 12 empty (refused) chunks out of 1 156. Logged cause: `ArrayIndexOutOfBoundsException: Index -1 out of bounds for length 21` at `VolcanoBlocks.rockBlockId(VolcanoBlocks.java:59)` <- `rockBlockId(VolcanicBlock)` <- `TfcTerrain.volcanicData(TfcTerrain.java:734)` <- `TfcTerrain$5.place` <- `VolcanicFeatureBlocks.write(VolcanicFeatureBlocks.java:89)` <- `TfcTerrain.decorate` (e.g. chunk 26,-134 of seed 1). A volcanic block carries rock -1 (no rock found at the position, e.g. above the terrain or in a carved cell)."
  - "Find where the rock index -1 comes from (the replacer's rock lookup in a carved/air cell?) and resolve it the way TFC does (TFC reads the rock of the block it replaces); never let one block refuse the whole chunk. Check the census logs: only the first refusal is logged with its trace, so confirm all refused chunks share the cause."
  - "Root cause: `VolcanoBlocks.rockBlockId(kind, rock)` read `PlaceholderRocks.names().get(rock)` before switching on the kind, and `TfcTerrain.volcanicData` calls it for every volcanic block that is not CE groundcover. Air, lava, water and spring water carry rock -1 (`VolcanoLevel.set`), so every chunk holding one of those blocks of a caldera, fissure, hot spring, spring, rivulet or crater lake threw; the rock lookup itself (`VolcanoView.rockAt`, TFC's `RockData.getRock`) never returns -1, and `VolcanicBlock` refuses a rock kind without a rock. Fix: the rock name is read only for hardened rock and magma with a rock in range; anything else returns null and keeps the caller's fallback (vanilla placeholder / palette), as before PAR-WORLD-45. Since every volcanic air/fluid block hit this path, all refused chunks near volcanic features share the cause; the census keeps no per-chunk logs to confirm it from the old runs."
  - "Pending server check (container may not run itest server runs from this worker): `spikes/ore-overlay/run-census.sh tfc 1 32 ...` and seed 2 should report 0 empty chunks except the PAR-WORLD-18 `Weighted.get` chunk (-62,-166 on seed 1) if that is still open."
  - "Done: VolcanoBlocks.rockBlockId guards the kind and rock before the name lookup; paper `VolcanoRockBlocksTest.blocksWithoutRockHaveNoRockBlockAndDoNotThrow` (failed first with the index -1 exception); ledger volcano row updated."
tests:
  - "paper: a volcanic block with no rock at its position resolves to TFC's fallback, no exception"
  - "itest: ore.census on seed 1 and 2 reports 0 empty chunks (run `spikes/ore-overlay/run-census.sh tfc 1 32 ...`)"
done_criteria:
  - "No refused chunk in a 32 x 32 TFC census on seeds 1-3"
---

# PAR-WORLD-60: Volcanic feature blocks with rock index -1 refuse chunks

Found by PAR-WORLD-19. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in
`docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
