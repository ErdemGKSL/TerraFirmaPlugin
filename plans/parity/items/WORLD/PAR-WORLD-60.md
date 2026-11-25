---
id: PAR-WORLD-60
title: "Volcanic feature blocks with rock index -1 refuse chunks (VolcanoBlocks.rockBlockId)"
area: WORLD
status: open
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
tests:
  - "paper: a volcanic block with no rock at its position resolves to TFC's fallback, no exception"
  - "itest: ore.census on seed 1 and 2 reports 0 empty chunks (run `spikes/ore-overlay/run-census.sh tfc 1 32 ...`)"
done_criteria:
  - "No refused chunk in a 32 x 32 TFC census on seeds 1-3"
---

# PAR-WORLD-60: Volcanic feature blocks with rock index -1 refuse chunks

> Claimed: worker session_011osUp5, 2026-11-25

Found by PAR-WORLD-19. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in
`docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
