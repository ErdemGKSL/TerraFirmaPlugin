---
id: PAR-WORLD-50
title: "CE coral and sea pickle blocks: datagen for the 30 corals and tfp:sea_pickle on their vanilla twin carriers, items, loot, names; write them in generation; drops on support loss; wall fan placement"
area: WORLD
status: blocked
wave: 27
deps: [PAR-WORLD-25, PAR-WORLD-46]
size: M
aliases: ["PAR-WORLD-25 follow-up: its files_owned cannot reach the hitbox demands, Datagen or TfcTerrain"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/plant/coral/Coral.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/coral/TFCCoralPlantBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/coral/CoralWallFanBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/coral/TFCSeaPickleBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java"
  - "src/main/resources/assets/tfc/blockstates/coral"
  - "src/main/resources/assets/tfc/blockstates/sea_pickle.json"
  - "src/main/resources/data/tfc/loot_table/blocks/coral"
  - "src/main/resources/data/tfc/loot_table/blocks/sea_pickle.json"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/coral/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/coral/**"
  - "content/generated/content/craftengine/tfp/configuration/coral/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxDemands.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/TfcShapes.java"
  - "paper/src/main/java/dev/tfp/paper/worldgen/TfcTerrain.java"
  - "paper/src/main/java/dev/tfp/paper/coral/**"
  - "paper/src/test/java/dev/tfp/paper/coral/**"
  - "tools/datagen/src/main/resources/dev/tfp/datagen/hitbox/carrier-policy.json"
  - "tools/datagen/src/main/resources/dev/tfp/datagen/hitbox/tfc-shapes.json"
  - "tools/datagen/src/main/resources/dev/tfp/datagen/hitbox/vanilla-shapes-*.json"
ui: true
in_game_steps:
  - "Fresh world; tp Bot to a warm coral reef (tfp debug worldgen finds one); screenshot under water: TFC coral plants, fans, wall fans and sea pickles"
  - "Break a tube coral with an empty hand and a dead tube coral with an empty hand and with a pickaxe; screenshot the drops (TFC loot: the dead one only with the correct tool)"
  - "Place a living tube coral on dry land; wait 120 ticks; screenshot: it turned into dead tube coral; place a sea pickle out of water (no light) and in water (lit)"
  - "Break the block behind a wall fan: the fan breaks and drops its fan item"
plan:
  - "UNBLOCKED (2026-11-25, worker, precedent PAR-HEAT-23): files_owned widened to carrier-policy.json, tfc-shapes.json and the vanilla-shapes dumps as the BLOCKED line asks. The coral plant rows are written from the ported vanilla BaseCoralPlantBlock outline, as PAR-DECO-02 wrote its door rows."
  - "WAS BLOCKED (2026-11-25): the vanilla-twin carriers need files outside files_owned: tools/datagen/src/main/resources/dev/tfp/datagen/hitbox/carrier-policy.json reserves every vanilla coral fan and wall fan for the torch / wall_torch classes (PAR-HEAT-29, only_class) and lists minecraft:sea_pickle as natural with a hide mapping plus in object_ban; the vanilla coral plant states (minecraft:tube_coral, dead_tube_coral, ...) are not in the vanilla-shapes-*.json dumps at all (needs a re-dump through the project's dump tooling). Unblock: add those resource files to files_owned (after PAR-WORLD-59, which reworks hitbox/), release the coral fans from only_class torch/wall_torch (check TorchCarrierTest), make sea_pickle a twin (absent: TFC places no vanilla sea pickle once the CE ids are used; GroundcoverBlocks' clam/mollusk/mussel placeholder must move off it) and dump the 20 coral plant states. Then CoralBlockGen + HitboxDemands/TfcShapes rows as planned below."
  - "Done here (partial, commit 'PAR-WORLD-50 (partial)'): paper coral/CoralSupportQueue (support loss -> removal through the custom block with its drops, one tick after the physics update, face tests from the real block data so CE supports count) replaces the neighbour-runner registration in CoralModule; CoralSupportQueueTest 6. TfcTerrain writes PlantBlocks.coralRef through customBlockData (placeholder fallback), live once the CE blocks exist."
  - "PAR-WORLD-25 did the rules: core `coral/CoralRules` (ids, dead block, lootFrom, needsItem, scanForWater, support sides, die delay, sea pickle survival/light/count), paper `coral/CoralSupport` (neighbour-runner shape rule: support loss -> air), `coral/CoralDeath` (living coral out of water dies after 60..99 ticks, facing kept), `CoralModule` (service file line), `worldgen/PlantBlocks.coralRef` (the CE state of each generated coral/sea pickle). The CE blocks themselves are missing."
  - "Datagen coral/CoralBlockGen: `tfp:coral/<colour>_<type>` (30, TFC's names) with `waterlogged` (TFC fluid=salt_water; CE's hardcoded waterlogged behaviour, `settings.fluid_state: water` on the wet variant) and wall fans `facing`; `tfp:sea_pickle` with `pickles` 1..4 and `waterlogged`. TFC draws vanilla's models for all of them (blockstates name `minecraft:block/*`), so each appearance takes its vanilla twin state (`minecraft:tube_coral[waterlogged=..]`, `dead_tube_coral_wall_fan[facing=..,waterlogged=..]`, `sea_pickle[pickles=n,waterlogged=..]`) with the vanilla look, as PAR-HEAT-26 does for candles: add the demands with `HitboxAllocation.VANILLA_LOOK` and the twins to `HitboxDemands`, shape rows (BIG 2,0,2..14,15,14; SMALL 2,0,2..14,4,14; wall fan per facing; sea pickle per count) to `TfcShapes`. The twin's light is TFC's (`3 + 3 * pickles` when wet). Check that suppression (NP-VIS-03) never lets a real vanilla coral plant or sea pickle exist in a TFC world (worldgen already writes none once the CE ids are used)."
  - "Living: wet-grass sound, instabreak, no collision; dead: instabreak, `requiresCorrectToolForDrops` (TFC tags: check `mineable/pickaxe` for the dead corals); sea pickle slime sound. Items only for coral and dead coral (needsItem) and the sea pickle; loot delegated to TFC's tables (LootBreakListener). Names from TFC lang."
  - "Generation: `TfcTerrain.decorate` writes `PlantBlocks.coralRef` through the CE state table (as PAR-WORLD-46 does for `ceRef`), placeholder as fallback when CE has not loaded."
  - "Support loss drops: `CoralSupport` turns the block into air through the neighbour runner, which drops nothing; TFC's `updateShape` -> air destroys with drops (`updateOrDestroy`). Drop the TFC loot (LootService) when the runner removes a coral or sea pickle, or move the removal into `CoralDeath`'s apply step."
  - "Placement: wall fan `getStateForPlacement` (nearest horizontal looking direction whose opposite wall is sturdy, else no placement); sea pickle stacking (`canBeReplaced`, `seaPickleAdd`) and its placement fluid (salt water only; vanilla water stands in, NP-FLUID-01)."
tests:
  - "tools/datagen: 30 coral blocks + sea pickle, items only where TFC registers them, loot delegated to TFC's tables, every appearance on its vanilla twin"
  - "paper: PlantWiringTest / CoralRefTest: the terrain writes coralRef states; MockBukkit: a support loss drops the TFC loot"
done_criteria:
  - "Reefs generate TFC coral blocks and sea pickles; living corals die out of water; drops follow TFC loot; tests green; screenshots pass"
---

# PAR-WORLD-50: CE coral and sea pickle blocks

> see_not_parity: NP-FLUID-01, NP-VIS-03 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
