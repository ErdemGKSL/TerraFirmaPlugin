---
id: PAR-WORLD-47
title: "Remaining plant shapes as CE blocks: water plants (waterlogged), creeping, epiphytes, vines, weeping/twisting columns, kelp, cacti, bamboo, intertidal plant-likes"
area: WORLD
status: done
wave: 27
deps: [PAR-WORLD-12]
size: L
aliases: ["PAR-WORLD-12 follow-up"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/plant/Plant.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/WaterPlantBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/TallWaterPlantBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/TFCSeagrassBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/CreepingPlantBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/EpiphytePlantBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/BodyPlantBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/TopPlantBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/TFCKelpBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/TFCCactusBlock.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/plant/water/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/plant/shape/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/plant/water/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/plant/shape/**"
  - "content/generated/content/craftengine/tfp/configuration/plant_water/**"
  - "content/generated/content/craftengine/tfp/configuration/plant_shape/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxDemands.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/GeneratedCarrierTest.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/HardcodedCarrierTest.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/plant/PlantBlockGen.java"
ui: false
plan:
  - "Decision 2026-11-25 (world session, no owner needed): combine the options. Creeping plants, pipe plants and other rare/multiface shapes: hitbox-only vanilla carrier + entity_renderer item displays (0 pool states, like snow piles and PAR-WORLD-53). Water plants: one shared look per plant family per state class (kelp pool 25) with the species texture on the entity_renderer where families overflow. Record each as a ledger divergence. Commit WIP early."
  - "BLOCKED (owner decision on the visual budget, 2026-11-25): the 70 remaining plants of Plant.java (31 types) do not fit any carrier budget without a choice only the owner can make. The plant pools are full (sugar_cane 15/15, sapling 8/8, tripwire 123/126). Counted from TFC's blockstates at c3b4d9536: 10 creeping plants (moss, ivy, morning glory, philodendron, reindeer lichen, 2 lichens, 2 anemones, barnacles, mussels: multipart over 6 face booleans, up to 64 looks each, waterlogged for the ocean ones), 5 epiphytes x 4 facings, 3 rotatable ocean plants x 6 facings, the giant kelp body and flower and the saguaro body and top (6 pipe connections each: 64 looks), jungle vines (5 faces), 26 water plants (water, grass-water and tall-water types: about 67 looks with their ages and halves, all waterlogged; the kelp pool holds 25), 16 columns and tops, 3 perched epiphytes, barrel cactus (collision), bamboo. The multiface vanilla carriers that never occur in a TFC world (glow_lichen 128, sculk_vein 128, resin_clump 128 on 1.21.4+, vine 32) can hold one creeping plant each, not ten. Options for the owner: (a) creeping plants share looks per family (one carrier each for land creepers, stone lichens, anemones, rock creepers: 4 carriers, merged textures); (b) the rare shapes (epiphytes, rotatables, pipes, columns) as HITBOX_ONLY carriers with entity_renderer looks, which costs packets per block for dense kelp forests and vine curtains; (c) reduce the face states to the 6 single-face looks plus a generic one (as TFC's item model) on a few free full-cube or pool states. Pick one (or a mix) and this item can be split per family; PAR-WORLD-13 already places all of them in core (PlantResources.WRITTEN_TYPES keeps them out until their blocks exist)."
  - "PAR-WORLD-12 generated the 13 land/floating block types (`dev.tfp.datagen.plant.PlantBlockGen.TYPES`; tripwire 123/126, sugar_cane 15/15, sapling 8/8 used). The other 31 types of Plant.java (70 plants) have no CE block: WATER(_FRESH), GRASS_WATER(_FRESH), TALL_WATER(_FRESH) need a waterlogged carrier and TFC's `fluid` property (kelp pool 25, seagrass/tall_seagrass once the worldgen placeholders are gone, else the hitbox selector's carriers + entity_renderer); CREEPING/CREEPING_STONE/OCEAN_* and EPIPHYTE are multi-face (6 booleans); VINE, WEEPING/TWISTING (+_TOP) are columns; CACTUS/BRANCHING_CACTUS need collision; BAMBOO/KELP/KELP_TREE have their own growth."
  - "Reuse PlantBlockGen's Blockstate/Assets helpers (make them package-visible if needed: they are in PAR-WORLD-12's glob, edit them minimally and say so) and its tint baking. Pick carriers with the hitbox selector (PAR-INFRA-13) where the pools are full; record pool and entity counts. Then give PlantBlocks.ceRef the new types (PAR-WORLD-46 file)."
  - "Move the plant-pool choice into the selector: PlantBlockGen picks tripwire/sugar_cane/sapling itself (HardcodedCarrierTest exempts it until then); model the plant pools in hitbox/ (carrier-policy pools) and remove the exemption. Also widen files_owned to hitbox/** for that step or split it out. BlockCoverage now names this item as the owner of the remaining plant/* blocks."
  - "Done (2026-11-25): datagen `plant/shape/PlantShapeGen` writes the 30 plants of the 13 types that neither PlantBlockGen nor PAR-FARM-25's GrowingPlantBlocks (which landed meanwhile with vines, columns, kelp, giant kelp, saguaro, creepers and epiphytes as hitbox-only item displays) generate: water, grass-water and tall-water plants, ocean creepers and rotatables, barrel cactus, saguaro fruit, bamboo (`configuration/plant_shape`, `plant_water`), properties read from TFC's blockstates (half, faces, facing, age, open, leaves; waterlogged for the tall water plants). Kelp pool 25/25 (24 single-part water looks + one shared empty state); every other look one item display per model part, on the empty kelp state or a hitbox-only carrier from `HitboxDemands.PLANT_SHAPE_ROWS` (14 dry plants on the berry bush and barrel rows, weight -100: no new carrier). files_owned widened (plan-authorised): HitboxDemands, the two hitbox carrier tests, minimal PlantBlockGen edits (Assets public, plants(tfc, types), water/vine tint names). Tests: PlantShapeGenTest 6, GeneratedCarrierTest pins the 14 ids. Ledger: feature row + divergences. The pool move into the selector is PAR-WORLD-59. ceRef for the new types: PAR-WORLD-46."
tests:
  - "tools/datagen: counts vs Plant.java for every remaining type; pools within budget"
done_criteria:
  - "Every Plant.java entry has a CE block; water plants render waterlogged"
---

# PAR-WORLD-47: Remaining plant shapes as CE blocks

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
