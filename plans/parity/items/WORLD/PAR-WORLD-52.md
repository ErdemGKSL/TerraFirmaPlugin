---
id: PAR-WORLD-52
title: "A world's own rock_layer_settings in every rock consumer (terrain fill, volcanoes, groundcover, karst), not only TFC's preset"
area: WORLD
status: open
wave: 30
deps: [PAR-WORLD-23, PAR-WORLD-43]
size: S
aliases: ["PAR-WORLD-23 follow-up: PlaceholderRocks keeps a static API over the preset's walk"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/world/settings/RockLayerSettings.java"
  - "src/main/java/net/dries007/tfc/world/chunkdata/RegionChunkDataGenerator.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/worldgen/TfcTerrain.java"
  - "core/src/main/java/dev/tfp/core/worldgen/chunk/PlaceholderRocks.java"
  - "core/src/main/java/dev/tfp/core/worldgen/groundcover/LooseRocks.java"
  - "core/src/main/java/dev/tfp/core/worldgen/groundcover/UndergroundLooseRocks.java"
  - "core/src/main/java/dev/tfp/core/worldgen/volcano/**"
  - "paper/src/main/java/dev/tfp/paper/worldgen/Volcan*.java"
  - "core/src/test/java/dev/tfp/core/worldgen/rock/WorldRockLayers*"
ui: false
in_game_steps: []
plan:
  - "PAR-WORLD-23 ported TFC's layer walk (core/worldgen/rock/RockLayerSampler) and made PlaceholderRocks.index delegate to RockLayerSampler.PRESET. A world whose worldgen-settings.yml gives its own rock_layer_settings (PAR-WORLD-22 stores and validates it) still gets the preset's rocks: TfcTerrain, the volcano features and the groundcover call the static PlaceholderRocks API, and TfcTerrain builds its RegionGenerator with the preset sampler for the karst task."
  - "Pass the world's sampler (RockLayerSampler.decode(settings.rockLayerSettings())) into TfcTerrain and every PlaceholderRocks.index caller (an instance or an extra parameter); index rocks by the config's rock ids, refusing a rock id without exported rock settings at load."
tests:
  - "core: a non-preset config changes the rock of a sample and the karst flag consistently"
done_criteria:
  - "A world's rock_layer_settings decides its rock columns and karst points; tests green; ledger row of PAR-WORLD-23 updated"
---

# PAR-WORLD-52: A world's own rock_layer_settings in every rock consumer

> Claimed: worker agent-a0d3502f, 2026-11-25

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
