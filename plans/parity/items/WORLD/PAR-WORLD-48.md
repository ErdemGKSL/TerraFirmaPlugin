---
id: PAR-WORLD-48
title: "Plant lifecycle looks, climate tint and soil rules: TFC's seasonal plant stages (tfc:plant model loader), per-climate grass/foliage colour, TFC canSurvive per plant type"
area: WORLD
status: open
wave: 28
deps: [PAR-WORLD-12, PAR-WORLD-46]
size: L
aliases: ["PAR-WORLD-12 follow-up"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/client/model/PlantBlockModel.java"
  - "src/main/java/net/dries007/tfc/client/TFCColors.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/Plant.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/PlantBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/ShortGrassBlock.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/plant/stage/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/plant/stage/**"
  - "core/src/main/java/dev/tfp/core/plant/lifecycle/**"
  - "core/src/test/java/dev/tfp/core/plant/lifecycle/**"
  - "paper/src/main/java/dev/tfp/paper/plant/lifecycle/**"
ui: true
in_game_steps:
  - "Same meadow in spring, summer, autumn, winter (tfp debug calendar): the flowers change stage as TFC's (blooming/seeding/dying/dormant/sprouting/budding)"
plan:
  - "PAR-WORLD-12 draws every plant at its first lifecycle stage, ages 0 and 1 share the age-1 look, the multipart base of canna/water canna is dropped, and tinted faces are baked with TFC's default climate colour (10 C, 250 mm). TFC picks the stage per block on the client from the calendar, the climate (rainfall variance, temperature scale) and the plant's bloom fractions (PlantBlockModel.getModelFromCalendar), and tints by the climate colour maps."
  - "Design first (ARCHITECTURE.md section): the stage is client-side in TFC, so the per-stage looks (1113 TFC plant models) cannot all have their own visual state. Candidates: a packet-time rewrite of the plant states by season (like the seasonal biome remap) with a shared per-stage visual budget; or a few stage groups. Decide with the budget numbers, record divergences."
  - "Soil: PAR-WORLD-12's bush_block only refuses air, water and lava below. Port TFC's canSurvive per type (bush_plantable_on, grass_plantable_on, sand for beach grass, dry plantable) as a tfp: behaviour reading our CE soil ids."
tests:
  - "core: stage of a plant vs TFC's getModelFromCalendar golden values"
done_criteria:
  - "Plants change look with the seasons as in TFC; plants break on wrong soil as in TFC"
---

# PAR-WORLD-48: Plant lifecycle looks, climate tint and soil rules

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
