---
id: PAR-WORLD-22
title: "TFC world generator settings as server config (flat bedrock, spawn distance and centre, climate scales, continentalness, grass density); close the spawn-settings and river open gaps"
area: WORLD
status: open
wave: 28
deps: [PAR-WORLD-02, PAR-WORLD-05, PAR-WORLD-16]
size: M
aliases: ["parity audit 2026-11-06: NP-CLIENT-05 substitute (config file) not planned by any item; spawn_settings_open_gaps.txt and river_open_gaps.txt have no owner"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/world/settings/Settings.java"
  - "src/main/java/net/dries007/tfc/world/settings/RockLayerSettings.java"
  - "src/main/java/net/dries007/tfc/world/TFCChunkGenerator.java"
  - "src/main/java/net/dries007/tfc/world/feature/DynamicDensityRandomPatchFeature.java"
  - "src/main/java/net/dries007/tfc/client/screen/CreateTFCWorldScreen.java"
  - "src/main/java/net/dries007/tfc/world/surface/builder/RiverSurfaceBuilder.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/worldgen/settings/**"
  - "core/src/test/java/dev/tfp/core/worldgen/settings/**"
  - "core/src/test/resources/dev/tfp/core/worldgen/spawn_settings*"
  - "core/src/test/resources/dev/tfp/core/worldgen/river_*"
  - "core/src/test/java/dev/tfp/core/worldgen/SpawnSettingsGoldenTest.java"
  - "core/src/test/java/dev/tfp/core/worldgen/RiverRulesGoldenTest.java"
  - "paper/src/main/java/dev/tfp/paper/worldgen/WorldSettings*.java"
  - "paper/src/test/java/dev/tfp/paper/worldgen/WorldSettings*"
  - "paper/src/main/resources/worldgen-settings.yml"
  - "paper/src/main/java/dev/tfp/paper/worldgen/TfcChunkGenerator.java"
ui: false
in_game_steps: []
plan:
  - "Current (audit 2026-11-06): NOT-PARITY NP-CLIENT-05 replaces TFC's Create TFC World screen with `bukkit.yml` + our config file, but no item builds that config: TFC's `Settings` (flat_bedrock, spawn_distance, spawn_center_x/z, temperature_scale, temperature_constant, rainfall_scale, rainfall_constant, continentalness, finite_continents, grass_density, rock layer settings) are hard-coded defaults here (for example `PlantPlacer.GRASS_DENSITY = 0.5f`, `OverworldClimate.DEFAULT_TEMPERATURE_SCALE`). `spawn_settings_open_gaps.txt` (flat_bedrock, grass_density, spawn_chunk_tries, spawn_column_window, spawn_search_step_divisor) and `river_open_gaps.txt` (river biome feature tags, surface.river_builder) have no owning item."
  - "Add an immutable `WorldSettings` record in core/worldgen/settings with TFC's codec field names and defaults from the TFC world preset; read it per world from `worldgen-settings.yml` (per-world sections keyed by world name, defaults otherwise) when the generator is created; persist the values used at creation in the world folder so later edits cannot change an existing world (TFC stores them in level.dat)."
  - "Thread it through every consumer: flat bedrock in the base fill, spawn search distance/centre, climate scale/constant in OverworldClimate (HUD, snow, precipitation, seasonal remap all take the world's settings, not the default), continentalness and finite continents in the region generator, grass density in PlantPlacer; replace the constants."
  - "Close spawn_settings_open_gaps.txt (port the spawn chunk tries, column window and search step divisor) and river_open_gaps.txt (river biome feature tags through the existing feature rules, the river surface builder with PAR-WORLD-05's builder table) with goldens; both files empty at done."
  - "Ledger: a Divergences row for the settings source (server config file instead of the world-creation screen), linked to NP-CLIENT-05."
tests:
  - "core: WorldSettingsTest (codec defaults equal TFC's preset; round trip)"
  - "core: SpawnSettingsGoldenTest and RiverRulesGoldenTest with empty open-gap files"
  - "core: golden region/climate sample with non-default temperature_scale and finite_continents vs the TFC oracle"
  - "paper: MockBukkit test that a world's stored settings win over a later config edit"
done_criteria:
  - "Every TFC Settings field is configurable per world and used by the generator and climate; both open-gap files are empty; tests green; ledger divergence row added"
---

# PAR-WORLD-22: TFC world generator settings as server config; close the spawn-settings and river open gaps

> see_not_parity: NP-CLIENT-05 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
