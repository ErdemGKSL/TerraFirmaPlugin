---
id: PAR-WORLD-59
title: "Plant pools into the hitbox selector: PlantBlockGen and PlantShapeGen stop choosing tripwire/sugar_cane/sapling/kelp themselves"
area: WORLD
status: done
wave: 28
deps: [PAR-WORLD-47]
size: M
aliases: ["PAR-WORLD-47 last plan line (split out)"]
tfc_refs:
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/plant/PlantBlockGen.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/plant/shape/**"
ui: false
plan:
  - "Split from PAR-WORLD-47 (2026-11-25): model CraftEngine's plant pools (tripwire 126, sugar_cane 15, sapling 8, kelp 25) as carrier-policy pools in hitbox/, let the selector hand PlantBlockGen and PlantShapeGen their pool states, and remove the exemptions in HardcodedCarrierTest (AUTO_STATE_SERIALISERS: plant/PlantBlockGen.java, plant/shape/PlantShapeGen.java) and GeneratedCarrierTest (tfp:plant/ auto_state skip)."
  - "Done: hitbox/PlantPools (pool sizes, the twigs' reserved tripwire state, the land pick order, the kelp model cap, the auto_state appearance); PlantBlockGen and PlantShapeGen take pools and appearances from it (output unchanged). HardcodedCarrierTest lost the plant exemptions; GeneratedCarrierTest lost the tfp:plant/ skip, counts distinct states per plant pool against its size and now also checks the 32 fruit-tree leaf pool appearances. PlantPoolsTest 3. Not this item: HardcodedCarrierTest fails on soil/SoilExtraBlocks.java writing auto_state, and GeneratedCarrierTest counts 7 more pool appearances than declared (97 vs 90), both from PAR-WORLD-42 (6643652)."
tests:
  - "datagen: HardcodedCarrierTest and GeneratedCarrierTest without plant exemptions; total plant-pool demand within each pool"
done_criteria:
  - "No generator outside hitbox/ writes an auto_state for a plant pool"
---

# PAR-WORLD-59: Plant pools into the hitbox selector

All fields live in the front matter above; the loop parses them. Follow `plans/parity/PROTOCOL.md`.
