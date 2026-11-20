---
id: PAR-FARM-19
title: "CraftEngine farmland blocks per soil with hydration visual states"
area: FARM
status: open
wave: 28
deps: [PAR-FARM-01, PAR-INFRA-13]
size: M
aliases: ["found while doing PAR-FARM-01: the farmland device kind tfp:farmland and the hoe overlay exist, the tfp:farmland/<soil> CE blocks do not, so SoilListener cannot till yet"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/soil/FarmlandBlock.java"
  - "src/main/resources/assets/tfc/blockstates/farmland/"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/farm/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/farm/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxDemands.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/TfcShapes.java"
  - "tools/datagen/src/main/resources/dev/tfp/datagen/hitbox/tfc-shapes.json"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/GeneratedCarrierTest.java"
  - "content/generated/content/craftengine/tfp/configuration/farm/**"
ui: true
in_game_steps:
  - "hoe dirt near water (PAR-GATHER-05)"
  - "hold hoe and look at it; screenshot actionbar"
plan:
  - "Shared hitbox files: edit only your own rows and pins (PAR-CLIM-04's UNBLOCKED recipe). The farmland carrier is a shape of farmland[moisture]: see HitboxDemands notes on the farmland carrier."
  - "Datagen: 8 blocks tfp:farmland/<soil> (TFC strength 1.3, gravel sound, shape 16x15, the soil block below as drop: loot = the dirt block), moisture visual states from the TFC blockstates (dry to wet), registered under the tfp:farmland device kind."
  - "The hydration visual (moisture 0-7 from the hydration value) is applied lazily when the farmland is looked at or caught up, as the rest of the device blocks."
  - "Then PAR-FARM-01's in-game steps: till near water, hold the hoe and read the action bar."
tests:
  - "datagen: 8 blocks, states, loot vs TFC; hitbox carrier pins"
done_criteria:
  - "Farmland next to water shows hydrated and the actionbar readout"
---

# PAR-FARM-19: CraftEngine farmland blocks per soil with hydration visual states

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
