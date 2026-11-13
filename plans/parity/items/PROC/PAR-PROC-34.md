---
id: PAR-PROC-34
title: "Composter CE block: tfc:composter hitbox rows and demands, configuration/compost, in-game check"
area: PROC
status: open
wave: 12
deps: [PAR-INFRA-13]
size: M
aliases: ["follow-up of PAR-PROC-11 (its CraftEngine block and its in_game_steps, PROTOCOL 6.5)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/TFCComposterBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java"
  - "src/main/resources/assets/tfc/blockstates/composter.json"
  - "src/main/resources/assets/tfc/models/block/composter"
  - "src/main/resources/data/tfc/loot_table/blocks/composter.json"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxDemands.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/TfcShapes.java"
  - "tools/datagen/src/main/resources/dev/tfp/datagen/hitbox/tfc-shapes.json"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/GeneratedCarrierTest.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/compost/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/compost/**"
  - "content/generated/content/craftengine/tfp/configuration/compost/**"
  - "docs/verification/PAR-PROC-11/**"
ui: true
in_game_steps:
  - "tfp debug place tfp:composter"
  - "add 4 green 4 brown"
  - "tfp time add days 12 (only where the average rainfall is 150..350; elsewhere wait CompostRules.readyTicks at that spot)"
  - "sneak-right-click; screenshot"
plan:
  - "Deferred from PAR-PROC-11 (PROTOCOL 3 + 6.5): the composter runtime is done and tested (core/compost CompostBin, CompostRules, CompostAddition, ComposterState; paper/compost ComposterDevice kind tfp:composter, ComposterModule, PaperComposterEffects). The CraftEngine block is missing because its hitbox carriers must come from the selector (HardcodedCarrierTest) and HitboxDemands.java and tfc-shapes.json were outside PAR-PROC-11's files_owned."
  - "Hitbox: add TFC's composter outlines to tfc-shapes.json (TFCComposterBlock.SHAPES: a full cube minus the 14x14 well from max(2, stage * 2) up, one per stage 0..8) and HitboxDemands for tfp:composter. Vanilla minecraft:composter[level=0..8] has the same outlines; check whether it may be repurposed (vanilla recipe removed by TFC? item-only?) or pick the selector's carriers. Pin tfp:composter in GeneratedCarrierTest."
  - "Block entry: datagen/compost writes configuration/compost/composter.yml through DeviceBlockSpec + DeviceYaml.templated: the tfp:device behaviour, the properties stage (int 0..8, ComposterDevice.STAGE) and type (normal/ready/rotten, ComposterDevice.TYPE; the device writes both), TFC's strength / sound / tool read from TFCBlocks, the loot from TFC's loot table, the block item, TFC's models and textures (the per-stage and ready/rotten models of blockstates/composter.json). TFC's block properties from TFCBlocks.COMPOSTER: strength 0.6, wood sound, noOcclusion, flammable(60, 90). TFC's BottomSupportedDeviceBlock: needs a sturdy block below; test it or record it as a divergence. Add the section to CeYamlCheck if it is new."
  - "In-game wait (review of PAR-PROC-11): the ready ticks double or more when the average rainfall is below 100 or above 400, so run the check where rainfall is 150..350 or compute the wait from CompostRules.readyTicks at that spot. Do not screenshot a rotten extraction until tfp:rotten_compost exists (PAR-FARM-02); until then it gives nothing (ledger)."
  - "In-game check (PROTOCOL 6): run the steps with the bot (wheat / grass as greens, sticks / straw as browns), screenshot the filled, ready and emptied composter and the compost item spawned above; store docs/verification/PAR-PROC-11/. Done criterion of PAR-PROC-11 moved here: the bin shows type=ready after composterTicks and sneak extraction gives one compost."
tests:
  - "datagen: ComposterBlockTest (properties stage/type, carriers from the allocation, TFC's models)"
done_criteria:
  - "Screenshot: composter ready after 12 days and one compost after sneak extraction (docs/verification/PAR-PROC-11/)"
---

# PAR-PROC-34: Composter CE block: tfc:composter hitbox rows and demands, configuration/compost, in-game check

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
