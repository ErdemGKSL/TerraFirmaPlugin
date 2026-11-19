---
id: PAR-WORLD-08
title: "Disc, pipe and kaolin veins; montane second pass; ore distribution statistics"
area: WORLD
status: done
wave: 19
deps: [PAR-INFRA-15, PAR-INFRA-16]
size: M
aliases: ["M2-GEN-10"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/world/feature/vein/DiscVeinFeature.java"
  - "src/main/java/net/dries007/tfc/world/feature/vein/PipeVeinFeature.java"
  - "src/main/java/net/dries007/tfc/world/feature/vein/KaolinDiscVeinFeature.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/worldgen/ore/**"
  - "core/src/test/java/dev/tfp/core/worldgen/ore/**"
  - "paper/src/main/java/dev/tfp/paper/worldgen/OreVeinResources.java"
ui: false
in_game_steps: []
plan:
  - "Port DiscVein, PipeVein and KaolinDiscVein with golden placement masks; the montane second pass; a 100-chunk distribution statistic per vein type compared with TFC (±10%)."
  - "Ore overlay (2026-10-22): veins write the CE ore ids only; the look is the host rock + exposed-face overlays (PAR-INFRA-15/16). Never emit or assume a full-cube ore visual; after the new vein types land, rerun PAR-WORLD-17's `ore.census` and PAR-REL-12's `ore.overlay.budget` and record the numbers."
  - "Montane second pass: OreVeins runs each biome vein tag's types after the main tag with their own salted positional random and one long drawn first; without that draw the first output of `at(x, y, z)` correlates the two passes (measured: P(both pass) 0.19 instead of 0.06 at density 0.25) and the two-pass statistic came out up to 13% below TFC's."
  - "Pending (delegated server run, not done in this container): rerun PAR-WORLD-17's `ore.census` and PAR-REL-12's `ore.overlay.budget` after the terrain places the new types; moved with the terrain wiring of the biome passes, the kaolin disc and the projected/near-lava/rock-writing types to PAR-WORLD-30 (TfcTerrain is not in this item's files)."
  - "Done: PAR-WORLD-08: Veins/DiscVein/PipeVein/KaolinDisc/VeinConfig in core, VeinData.parseAny, OreVeins biome passes + WorldView, OreVeinResources loads every type and the terrain places the main-tag disc/pipe types that need no world view and write only ores; VeinTypesGoldenTest (6 tests: 19 seeds, 76 vein hashes, 228 TFC placement masks, kaolin decisions, 30 distribution statistics within 10%/4 sigma of TFC); ledger rows updated; follow-up PAR-WORLD-30."
tests:
  - "core: OreVeinGoldenTest extended (VeinTypesGoldenTest + VeinTypesGolden for the disc, kaolin and pipe types)"
  - "core: distribution test"
done_criteria:
  - "All vein types generate; stats within 10% of TFC"
---

# PAR-WORLD-08: Disc, pipe and kaolin veins; montane second pass; ore distribution statistics

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
