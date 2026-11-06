---
id: PAR-CLIM-11
title: "Climate in worlds without the TFC generator: biome-based climate model for the Nether, the End and vanilla worlds (temperature, rainfall, consumers)"
area: CLIMATE
status: open
wave: 27
deps: [PAR-CLIM-01, PAR-CLIM-02, PAR-WORLD-02, PAR-WORLD-16]
size: M
aliases: ["parity audit 2026-11-06: BiomeBasedClimateModel / ClimateModels / SelectClimateModelEvent named by no item"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/climate/BiomeBasedClimateModel.java"
  - "src/main/java/net/dries007/tfc/util/climate/ClimateModel.java"
  - "src/main/java/net/dries007/tfc/util/climate/ClimateModels.java"
  - "src/main/java/net/dries007/tfc/util/climate/Climate.java"
  - "src/main/java/net/dries007/tfc/util/events/SelectClimateModelEvent.java"
  - "src/main/java/net/dries007/tfc/ForgeEventHandler.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/climate/model/**"
  - "core/src/test/java/dev/tfp/core/climate/model/**"
  - "paper/src/main/java/dev/tfp/paper/climate/ClimateModels*.java"
  - "paper/src/test/java/dev/tfp/paper/climate/ClimateModels*"
  - "paper/src/main/java/dev/tfp/paper/climate/PlayerClimateService.java"
  - "paper/src/test/java/dev/tfp/paper/climate/PlayerClimateService*"
  - "paper/src/main/java/dev/tfp/paper/climate/ChunkClimateSource.java"
ui: true
in_game_steps:
  - "execute in minecraft:the_nether run tp Bot 0 64 0; wait 40 ticks; screenshot the HUD temperature readout at GUI scale 2"
  - "execute in minecraft:the_end run tp Bot 0 64 0; wait 40 ticks; screenshot the HUD temperature readout"
  - "tfp debug chunk in both dimensions (chat) to show the model name, temperature and rainfall"
plan:
  - "Current (audit 2026-11-06): only the overworld with our generator has a climate; `PlayerClimateService` publishes an unknown reading in other dimensions, and every climate consumer (HUD, food decay, snow, precipitation, crops, animals, thermometer) has nothing there. TFC selects a model per level through SelectClimateModelEvent: OverworldClimateModel for the overworld with a TFC chunk generator, otherwise BiomeBasedClimateModel (average temperature `Climate.fromVanilla(biome temperature at pos)`, rainfall 300 mm if the biome has precipitation else 0, the ClimateModel default seasonal/daily variation, no rain events)."
  - "core/climate/model: a sealed `ClimateModel` with `Overworld` (wrapping OverworldClimate) and `BiomeBased` (pure: takes the vanilla biome temperature and precipitation flag captured on the region thread) ported with TFC's constants and `Climate.fromVanilla`."
  - "paper: `ClimateModels` picks the model per world exactly as TFC (overworld + our generator -> overworld model; any other world, Nether, End, a vanilla-generated overworld -> biome-based) and exposes one lookup used by PlayerClimateService and ChunkClimateSource; captures read the biome on the owning region thread and compute async."
  - "Consumers keep working without TFC chunk data: HUD readout, food decay temperature, snow/ice and precipitation services skip or use the biome model as TFC does; record any consumer that TFC also disables outside the overworld."
  - "Ledger: feature row and a Divergences row only if a value differs (for example Paper biome temperature access)."
tests:
  - "core: BiomeBasedClimateGoldenTest (Climate.fromVanilla values and rainfall for sample vanilla biomes vs TFC)"
  - "paper: MockBukkit model selection per world type (TFC overworld, vanilla overworld, nether, end)"
  - "paper: PlayerClimateService publishes a biome-based reading in the Nether"
done_criteria:
  - "Every world has a climate model chosen as TFC does; the HUD shows a temperature in the Nether and the End; tests green; screenshots pass"
---

# PAR-CLIM-11: Climate in worlds without the TFC generator: biome-based climate model for the Nether, the End and vanilla worlds

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
