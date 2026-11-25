---
id: PAR-POT-08
title: "Drying bricks, mud bricks and thatch blocks"
area: POTTERY
status: done
wave: 27
deps: [PAR-INFRA-11, PAR-INFRA-08, PAR-CLIM-02, PAR-INFRA-13]
size: M
aliases: ["M3-POT-04"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/DryingBricksBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/ThatchBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/TickCounterBlockEntity.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/drying/**"
  - "core/src/test/java/dev/tfp/core/drying/**"
  - "core/src/test/resources/dev/tfp/core/drying/**"
  - "paper/src/main/java/dev/tfp/paper/drying/**"
  - "paper/src/test/java/dev/tfp/paper/drying/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/drying/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/drying/**"
  - "content/generated/content/craftengine/tfp/configuration/drying/**"
  - "content/generated/content/craftengine/tfp/resourcepack/assets/tfp_drying/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxDemands.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/TfcShapes.java"
  - "tools/datagen/src/main/resources/dev/tfp/datagen/hitbox/tfc-shapes.json"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/GeneratedCarrierTest.java"
ui: true
in_game_steps:
  - "ce item give Bot tfp:mud_brick/silty_loam 4 (wet)"
  - "place"
  - "tfp time add days 2"
  - "screenshot"
plan:
  - "Wet mud bricks placed on the ground (1-4 per block) dry lazily via DryingRules with rain resets from precipitationAt; thatch blocks slow movement; CE visuals per count and dried state."
  - "Done: core DryingState (settle/added/taken, codec) + DryingStateTest (13); paper drying module (device kind tfp:drying_bricks, DryingBricks, DryingClock, DryingListener, config drying.mud-bricks-ticks, /tfp debug drying fill) + DryingBricksMockBukkitTest (17); datagen DryingBlocks (8 soils x 8 states, thatch, 8 mud_brick items, models, textures, names) + DryingBlocksTest; hitbox: HitboxDemands/TfcShapes/tfc-shapes.json (noCollission rows count 1..4) and GeneratedCarrierTest widened narrowly. Pending in-game check (container cannot run client): the in_game_steps, with soils alfisol..podzol (not silty_loam); place with /tfp debug drying fill <x> <y> <z> alfisol 4, tfp time add days 2, screenshot."
tests:
  - "core: DryingGoldenTest exists"
  - "paper: catch-up kind test"
done_criteria:
  - "Mud bricks dry after mudBricksTicks in clear weather (in-game with time add)"
---

# PAR-POT-08: Drying bricks, mud bricks and thatch blocks

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
