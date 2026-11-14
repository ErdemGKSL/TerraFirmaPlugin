---
id: PAR-HEAT-06
title: "Firepit block: placement, fuel, lazy temperature, ash, extinguish by rain and water"
area: HEAT
status: done
wave: 13
deps: [PAR-HEAT-05, PAR-INFRA-08, PAR-HEAT-04, PAR-CLIM-02, PAR-INFRA-13]
size: M
aliases: ["M3-HEAT-03 (firepit)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/FirepitBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/AbstractFirepitBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/FirepitBlockEntity.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/firepit/**"
  - "core/src/test/java/dev/tfp/core/firepit/**"
  - "core/src/test/resources/dev/tfp/core/firepit/**"
  - "paper/src/main/java/dev/tfp/paper/firepit/**"
  - "paper/src/test/java/dev/tfp/paper/firepit/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/Firepit.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/FirepitTest.java"
  - "content/generated/content/craftengine/tfp/devices/firepit.yml"
  - "content/generated/content/craftengine/tfp/configuration/devices/firepit.yml"
  - "core/src/main/java/dev/tfp/core/cooking/Firepit*"
  - "core/src/test/java/dev/tfp/core/cooking/Firepit*"
  - "core/src/main/java/dev/tfp/core/cooking/Cooking*"
  - "core/src/test/java/dev/tfp/core/cooking/Cooking*"
ui: false
in_game_steps: []
plan:
  - "Current: core/cooking FirepitRules (lazy advance) golden-tested; firepit.yml generated outside configuration/ (not loaded)."
  - "Device kind `firepit` (PAR-INFRA-02): fuel slots, input, outputs as DeviceInventory; FirepitState with lastUpdateTick; advance on interaction/GUI open/chunk load."
  - "Lit/unlit visual states with luminance; smoke particle only on interaction; rain and water bucket extinguish (precipitationAt from PAR-CLIM-02)."
  - "Cooking via HeatingService (PAR-HEAT-04); ash and dirtiness from fuel purity as TFC AbstractFirepitBlockEntity."
  - "Move firepit.yml into configuration/devices/ so CE loads it."
  - "Deferred (PROTOCOL §3/§6.5): writing configuration/devices/firepit.yml changes DatagenTest's fixture expectation (non_occluding gains tfp:firepit), a file outside this item; the move, the TFC firepit models (AssetPipeline), the itest `firepit.cook`, the in-game steps and the screenshot criterion moved to PAR-HEAT-19. `Firepit.loadedYaml` already builds the loaded entry (FirepitTest pins it)."
  - "Done: core/firepit FirepitSim (TFC serverTick replayed lazily per calendar tick from the last update, stops once unlit, airless and cold: smoke every 20 ticks, burn x2 in rain or with air, consumeFuel with 50% ash and dirtiness, extinguish, light, intakeAir, handleCooking with addTemp and the recipe test on the temperature before the tick, ticksUntilOut), FirepitData (TFC's fields + lit, smoke level, sampled rain; versioned codec), FirepitFuels (fuel definitions by item from the registry); paper/firepit FirepitDevice (kind tfp:firepit, 7 slots with TFC's limits and isItemValid, catch-up moves consumed fuel and cascades, cooks the input through HeatingService into outputs 5/6; right click: tryInsertLog, ash pickup when unlit, shovel/water bucket douse (temperature above 0 C), stand-in input/output by hand until PAR-HEAT-07; neighbour callback: pending light/build from FireStarting, lost floor breaks it; removal drops the ash), PaperFirepitEffects, FirepitModule (FireLightable for strong starts, FirepitFactory for stick+log piles, PrecipitationService rain, #tfc:solid_top_face floor). Datagen: Firepit.loadedYaml. Tests: core FirepitSimTest (13), paper FirepitMockBukkitTest (11, incl. fuel burn catch-up over 2 in-game hours equals FirepitRules and raw beef cooked on tick 202), FirepitTest +1. In-game check: PAR-HEAT-19."
tests:
  - "paper: MockBukkit fuel burn catch-up over 2 in-game hours equals FirepitRules"
  - "itest: `firepit.cook` cooks raw meat (moved to PAR-HEAT-19)"
done_criteria:
  - "Deferred check tracked in PAR-HEAT-19"
---

# PAR-HEAT-06: Firepit block: placement, fuel, lazy temperature, ash, extinguish by rain and water

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
