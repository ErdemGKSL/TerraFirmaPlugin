---
id: PAR-METAL-26
title: "Bellows visuals: pressed animation, TFC model and the blow sound"
area: METAL
status: done
wave: 33
deps: [PAR-METAL-11]
size: M
aliases: ["follow-up of PAR-METAL-11"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/BellowsBlock.java"
  - "src/main/java/net/dries007/tfc/client/render/blockentity/BellowsBlockEntityRenderer.java"
  - "src/main/resources/assets/tfc/models/block/bellows.json"
  - "src/main/resources/assets/tfc/sounds.json"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/bellows/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/bellows/**"
  - "paper/src/main/java/dev/tfp/paper/bellows/PaperBellowsEffects.java"
  - "paper/src/main/java/dev/tfp/paper/bellows/BellowsVisuals.java"
  - "paper/src/test/java/dev/tfp/paper/bellows/BellowsVisualsTest.java"
  # widened narrowly (one wiring line or the bellows demand each):
  - "paper/src/main/java/dev/tfp/paper/bellows/BellowsModule.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/ForgeDevices.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/ForgeDevicesTest.java"
ui: true
in_game_steps:
  - "bellows: right-click; screenshots at ticks 0, 5, 10, 15, 20 show the plates moving; the blow sound plays"
plan:
  - "PAR-METAL-11 pushes air and plays a vanilla hiss; the block itself stays the dirt_path placeholder. Draw TFC's bellows model (front 16x16x2, middle 12x12x12, back plate) with an entity_renderer whose back plate offset follows BellowsMotion.extension (a few morphs per push), register tfc:block.bellows.blow as tfp:block.bellows.blow in a datagen sounds file and use it in PaperBellowsEffects. Crank-driven extension comes with PAR-MECH-05."
tests:
  - "datagen: the sound and the model are generated; paper: the morph schedule follows the stroke"
done_criteria:
  - "The bellows visibly compresses on a push and plays TFC's blow sound"
---

# PAR-METAL-26: Bellows visuals

Follow-up of PAR-METAL-11. Follow `plans/parity/PROTOCOL.md`.

- Done: `BellowsAppearance` (datagen: `facing` x `pressed` 0..4 spec with item-display models, TFC's blow sound as `tfp_bellows:block.bellows.blow`), `BellowsVisuals` (morph schedule from `BellowsMotion.extension`, ~9 region-delayed block state writes per push), `PaperBellowsEffects` plays the pack sound and starts the morphs. Tests: datagen `BellowsAppearanceTest` (3), paper `BellowsVisualsTest` (4). Pending in-game check: see `in_game_steps` (and the deferred note in the run report).
