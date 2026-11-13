---
id: PAR-INFRA-47
title: "Block flammability: TFC's flammable(flammability, fireSpreadSpeed) and ignitedByLava as CraftEngine burn settings on every generated block"
area: INFRA
status: open
wave: 22
deps: [PAR-INFRA-08, PAR-WORLD-10]
size: M
aliases: ["parity audit 2026-11-13: ExtendedProperties.flammable/flammableLike* (66 call sites) reach no CE block: no generator emits burnable, burn_chance or fire_spread_chance"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/ExtendedProperties.java"
  - "src/main/java/net/dries007/tfc/common/blocks/IForgeBlockExtension.java"
  - "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java"
  - "src/main/java/net/dries007/tfc/common/blocks/wood/Wood.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/Plant.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/fruit/FruitBlocks.java"
  - "src/main/java/net/dries007/tfc/common/blocks/crop/Crop.java"
  - "src/main/java/net/dries007/tfc/common/blocks/GroundcoverBlock.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/flammability/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/flammability/**"
  - "tools/datagen/src/main/resources/dev/tfp/datagen/flammability/**"
  - "itest/src/main/java/dev/tfp/itest/cases/Flammability*.java"
ui: true
in_game_steps:
  - "Build a 3x3 of tfp:wood/log/oak with tfp leaves above it (and thatch / planks when their families exist) on stone; light the bottom with flint and steel; wait 30 s; screenshots at 0, 10 and 30 s: fire spreads and consumes them at TFC's pace"
  - "Place a TFC log next to lava (one gap); wait 60 s; screenshot: it ignites (ignitedByLava)"
  - "Same setup with tfp:rock/raw/granite and a metal block: no fire spread"
plan:
  - "Current (audit 2026-11-13): TFC gives its blocks fire behaviour through ExtendedProperties.flammable(flammability, fireSpreadSpeed) and the presets flammableLikeLogs (5, 5), flammableLikePlanks (20, 5), flammableLikeLeaves (60, 30), flammableLikeWool (100, 60), plus ignitedByLava; NeoForge's FireBlock reads them through IForgeBlockExtension. No datagen emits CraftEngine's burnable / burn_chance / fire_spread_chance, so TFC logs, planks, leaves, thatch, plants, crops, barrels and wool-like blocks never burn and never catch from lava; only carriers that happen to be vanilla flammable states burn, by the carrier's values."
  - "Mapping (vanilla FireBlock.setFlammable(block, encouragement, flammability)): CE burn_chance = TFC fireSpreadSpeed (vanilla igniteOdds), CE fire_spread_chance = TFC flammability (vanilla burnOdds), CE burnable = flammability > 0 or ignitedByLava. Check the mapping against CE's own burn_data presets (leaves 30/60, wood 5/5, planks 5/20) before relying on it."
  - "datagen/flammability: a table from TFC id to (flammability, fireSpreadSpeed, ignitedByLava), produced from the TFC checkout per family (scratch oracle over TFCBlocks/Wood/Plant/FruitBlocks/Crop/GroundcoverBlock registrations, the result kept as a resource with its derivation documented, not hand-typed values), and a pass that runs last in Datagen.generate (one run line in the shared hub) and merges the three settings into every emitted CE block entry whose TFC id is in the table. The block generators belong to other items, so the pass works on their output entries by id and never edits their files."
  - "Guard test over content/generated: every CE block whose TFC counterpart is flammable carries the three settings with TFC's values, and none of the non-flammable ones (rock, ore, metal, soil) does; later families (DECO-01 planks, DECO-07 thatch, WORLD-12 plants, FARM-03 crops, PROC-32 barrels) are covered automatically when they land."
  - "Fire spread itself stays vanilla's (the real server block registered with fire by CE); record in the ledger that the burn settings sit on the real block and that the carrier's client look while burning is vanilla fire over the CE visual."
tests:
  - "tools/datagen: FlammabilityTableTest (values per family vs the TFC oracle, mapping rule), FlammabilityGuardTest over the generated YAML"
  - "itest: flammability.spread case lights a log and leaves cluster and checks it burns within 60 s, and a granite cluster does not"
done_criteria:
  - "Every generated CE block with a flammable TFC counterpart burns and catches from lava with TFC's values; non-flammable families do not; tests green; screenshots pass review"
---

# PAR-INFRA-47: Block flammability: TFC's flammable(flammability, fireSpreadSpeed) and ignitedByLava as CraftEngine burn settings on every generated block

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
