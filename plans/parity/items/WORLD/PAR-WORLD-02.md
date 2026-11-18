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
  - "tools/datagen/src/test/java/dev/tfp/datagen/SeasonalBiomes*"
  - "content/generated/content/**"
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
  - "UNBLOCKED (manager, 2026-11-18): files_owned widened to the paths the BLOCKED line lists (SeasonalBiomes datagen + tests, SeasonalBiomeRemap, SeasonalBiomeHandler, TfcTerrain, generated content). Do the datagen base biomes first, then the remap and handler, then the provider; keep the seasonal remap working (it is the climate feature), drop the vanilla-placeholder divergence row. Files are shared with the climate items: edit only what the base-biome change needs and keep others on rebase."
  - "BLOCKED (2026-11-18): binding the BiomeProvider to datapack base biomes needs files outside files_owned. (1) No base TFC biomes exist: the only tfp: biomes are the tfp:seasonal/<biome>/t<T>r<R> palette variants, so a new datagen output (base biome per TFC biome, e.g. tfp:<biome>, empty features/carvers/spawners) is needed in tools/datagen/src/main/java/dev/tfp/datagen/SeasonalBiomes.java or a new generator there, plus content/generated. (2) The packet layer is keyed on the vanilla placeholders: paper/src/main/java/dev/tfp/paper/climate/SeasonalBiomeRemap.java (BASE_COUNT, BASE_OF_BIOME, baseIdOf hard-codes the minecraft: prefix) and paper/src/main/java/dev/tfp/paper/packet/SeasonalBiomeHandler.java (base network ids) must read the new base ids, or the seasonal remap stops matching. (3) paper/src/main/java/dev/tfp/paper/worldgen/TfcTerrain.java (vanillaBiomes(), Registry.BIOME.get(NamespacedKey.minecraft(key))) builds the provider table. To unblock: add those paths (and paper/src/test/java/dev/tfp/paper/climate/SeasonalBiomeRemap*, tools/datagen/src/test/**/SeasonalBiomes*) to files_owned, then replace BiomeKeys.VANILLA with the 125 tfp: base ids, update the remap and handler, and drop the vanilla-placeholder divergence row. The NEUTRAL part is already done: no paper/src/main call site passes ChunkClimate.NEUTRAL (all read ChunkClimateSource), only the grep test is owed."
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
