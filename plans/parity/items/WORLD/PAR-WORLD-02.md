---
id: PAR-WORLD-02
title: "Bukkit biome binding and climate call sites on the real chunk data"
area: WORLD
status: open
wave: 16
deps: []
size: M
aliases: ["M2-GEN-07"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/world/TFCChunkGenerator.java"
  - "src/main/java/net/dries007/tfc/world/biome/TFCBiomes.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/worldgen/BiomeKeys.java"
  - "paper/src/main/java/dev/tfp/paper/worldgen/TfcChunkGenerator.java"
  - "paper/src/main/java/dev/tfp/paper/climate/ChunkClimateSource.java"
  - "paper/src/test/java/dev/tfp/paper/worldgen/TfcChunkGenerator*"
  - "paper/src/test/java/dev/tfp/paper/climate/ChunkClimate*"
ui: true
in_game_steps:
  - "tfp debug chunk"
  - "Screenshot F3 at the same spot"
plan:
  - "Replace remaining `ChunkClimate.NEUTRAL` call sites with the chunk data climate."
  - "Bind TFC biome ids to the datapack base biomes in BiomeProvider (not vanilla placeholders); keep seasonal variants for the packet layer only."
  - "Server check: F3 biome name is the TFC biome."
tests:
  - "paper: no NEUTRAL call site (grep test)"
  - "itest: worldgen.terrain_generates still green"
done_criteria:
  - "`tfp debug chunk` and F3 agree on biome and temperature at 3 sites"
---

# PAR-WORLD-02: Bukkit biome binding and climate call sites on the real chunk data

> see_not_parity: NP-WORLD-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
