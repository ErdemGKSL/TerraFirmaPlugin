---
id: PAR-WORLD-59
title: "Plant pools into the hitbox selector: PlantBlockGen and PlantShapeGen stop choosing tripwire/sugar_cane/sapling/kelp/weeping_vines themselves"
area: WORLD
status: open
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
  - "Split from PAR-WORLD-47 (2026-11-25): model CraftEngine's plant pools (tripwire 126, sugar_cane 15, sapling 8, kelp 25, weeping_vines 25) as carrier-policy pools in hitbox/, let the selector hand PlantBlockGen and PlantShapeGen their pool states, and remove the exemptions in HardcodedCarrierTest (AUTO_STATE_SERIALISERS: plant/PlantBlockGen.java, plant/shape/PlantShapeGen.java) and GeneratedCarrierTest (tfp:plant/ auto_state skip)."
tests:
  - "datagen: HardcodedCarrierTest and GeneratedCarrierTest without plant exemptions; total plant-pool demand within each pool"
done_criteria:
  - "No generator outside hitbox/ writes an auto_state for a plant pool"
---

# PAR-WORLD-59: Plant pools into the hitbox selector

All fields live in the front matter above; the loop parses them. Follow `plans/parity/PROTOCOL.md`.
