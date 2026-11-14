---
id: PAR-HEAT-19
title: "Firepit loaded in game: configuration/devices/firepit.yml, TFC firepit models, self-test firepit.cook and the in-game check"
area: HEAT
status: open
wave: 14
deps: [PAR-HEAT-06]
size: S
aliases: ["follow-up of PAR-HEAT-06 (the CraftEngine move needs DatagenTest, the models need AssetPipeline, the itest case and its in-game criterion)"]
tfc_refs:
  - "src/main/resources/assets/tfc/blockstates/firepit.json"
  - "src/main/resources/assets/tfc/models/block/firepit_lit.json"
  - "src/main/resources/assets/tfc/models/block/firepit_unlit.json"
  - "src/main/java/net/dries007/tfc/common/blockentities/FirepitBlockEntity.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/Firepit.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/FirepitTest.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/DatagenTest.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/AssetPipeline.java"
  - "content/generated/content/craftengine/tfp/configuration/devices/firepit.yml"
  - "itest/src/main/java/dev/tfp/itest/cases/FirepitCookCase.java"
  - "docs/verification/PAR-HEAT-06/**"
ui: true
in_game_steps:
  - "tfp debug place tfp:firepit[lit=false]; drop 3 sticks and an oak log on a grass block and light them with a firestarter (or `tfp debug place` then right-click an oak log into it and light it)"
  - "right-click the lit firepit with ce item give Bot tfp:food/beef"
  - "tfp time add hours 1"
  - "tfp debug device (the input slot empty, cooked beef in output slot 5); right-click with an empty hand takes it"
  - "tfp debug place tfp:firepit[lit=false]; right-click an oak log into it; light it with a firestarter; the block shows TFC's lit model (logs, coals, flame), not a bare daylight_detector"
  - "tfp time add hours 1; tfp debug device: read the ash and the temperature (block entity present, kind tfp:firepit)"
  - "with fuel and in rain: tfp debug device shows the burn going twice as fast and the firepit still lit (rain does not extinguish, per the ledger)"
  - "douse a warm lit firepit with a water bucket (bucket turns empty) and another with a shovel (one damage); both show the unlit model; a shovel on an unlit firepit with ash gives the wood ash first"
  - "Screenshots at GUI scale 2 and 3 of the lit and the unlit firepit (the firepit GUI itself is PAR-HEAT-07)"
plan:
  - "Current: PAR-HEAT-06 ships the firepit device kind (paper/firepit), its catch-up (core/firepit FirepitSim) and `Firepit.loadedYaml` (template tfp:block/device with the tfp:device behaviour), but does not write it: a firepit in configuration/ makes DatagenTest#writesOreOverlayItemsTemplatesAndRuntimeData's `non_occluding` list non-empty (`tfp:firepit`, which is right: TFC's firepit is noOcclusion)."
  - "Write `Firepit.LOADED_OUTPUT` in `Firepit.run` (`out.put(LOADED_OUTPUT, loadedYaml(tfc))`), flip FirepitTest's `isNull` check, and change the DatagenTest expectation to `containsExactly(\"tfp:firepit\")` (the fixture now has a device)."
  - "Copy TFC's firepit_lit / firepit_unlit models and their textures (devices/firepit/rocks, coals, hot_coals; the vanilla campfire_fire) through AssetPipeline and give the lit and unlit appearances `model:` on the daylight_detector carrier (rotated y 90 for axis z), as ForgeDevices does for the charcoal forge."
  - "FirepitTest: assert the file written at LOADED_OUTPUT is the one CraftEngine loads (the CeYamlCheck section) and that the literal devices/firepit.yml outside configuration/ is not loaded, so the block id is not registered twice."
  - "Self-test `firepit.cook` (FirepitCookCase + its SelfTestSuite line): place tfp:firepit, put an oak log in and light it through FireStarting's lightable, put raw beef in, advance the calendar 1000 ticks, catch up and assert cooked beef in output slot 5 and the input empty. Run on Paper and Folia 1.21.11."
tests:
  - "itest: `firepit.cook` cooks raw meat on Paper and Folia"
done_criteria:
  - "Lit firepit cooks raw beef in the expected TFC time (self-test + screenshot of lit visual)"
---

# PAR-HEAT-19: Firepit loaded in game

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
