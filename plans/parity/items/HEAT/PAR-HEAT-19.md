---
id: PAR-HEAT-19
title: "Firepit loaded in game: configuration/devices/firepit.yml, TFC firepit models, self-test firepit.cook and the in-game check"
area: HEAT
status: done
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
ui: false
in_game_steps:
  - "Moved to PAR-HEAT-23 (PROTOCOL 6.5): the look the steps check needs the model carriers of that item"
plan:
  - "Current: PAR-HEAT-06 ships the firepit device kind (paper/firepit), its catch-up (core/firepit FirepitSim) and `Firepit.loadedYaml` (template tfp:block/device with the tfp:device behaviour), but does not write it: a firepit in configuration/ makes DatagenTest#writesOreOverlayItemsTemplatesAndRuntimeData's `non_occluding` list non-empty (`tfp:firepit`, which is right: TFC's firepit is noOcclusion)."
  - "Write `Firepit.LOADED_OUTPUT` in `Firepit.run` (`out.put(LOADED_OUTPUT, loadedYaml(tfc))`), flip FirepitTest's `isNull` check, and change the DatagenTest expectation to `containsExactly(\"tfp:firepit\")` (the fixture now has a device)."
  - "Copy TFC's firepit_lit / firepit_unlit models and their textures (devices/firepit/rocks, coals, hot_coals; the vanilla campfire_fire) through AssetPipeline and give the lit and unlit appearances `model:` on the daylight_detector carrier (rotated y 90 for axis z), as ForgeDevices does for the charcoal forge. (The `model:` part moved to PAR-HEAT-23: the shared carrier state can hold one model only.)"
  - "FirepitTest: assert the file written at LOADED_OUTPUT is the one CraftEngine loads (the CeYamlCheck section) and that the literal devices/firepit.yml outside configuration/ is not loaded, so the block id is not registered twice."
  - "Self-test `firepit.cook` (FirepitCookCase + its SelfTestSuite line): place tfp:firepit, put an oak log in and light it through FireStarting's lightable, put raw beef in, advance the calendar 1000 ticks, catch up and assert cooked beef in output slot 5 and the input empty. Run on Paper and Folia 1.21.11."
  - "Done: datagen writes configuration/devices/firepit.yml (Firepit.run, LOADED_OUTPUT) and copies TFC firepit_lit/firepit_unlit with their textures (Firepit.models via AssetPipeline); FirepitTest +3 (14), DatagenTest non_occluding = tfp:firepit; self-test firepit.cook (FirepitCookCase, stick bundle as fuel: TFC logs are no CE items yet) passed on Paper and Folia 1.21.11 (cooked beef in slot 5, input empty; CE pack 0 issues). The model look needs four MODEL hitbox demands (HitboxDemands/DeviceSpecs outside files_owned): follow-up PAR-HEAT-23, which also holds the in-game steps and screenshot (PROTOCOL 6.5, ui false here). Divergence row in the ledger."
  - "In-game run 2026-11 (deferred check, PAR-HEAT-23) stopped at lighting: not a FireStarting defect. As in TFC (FirepitBlock.useItemOn consumes every plain click), a plain right click on the firepit is its own use (FirepitDevice; the client predicts the daylight_detector carrier's use too), so the firestarter is never raised and FireStarterListener skips flint and pyrite; lighting needs a sneak click, and only a strong start lights it (FirepitModule lightable). The 458 C / lastMaxBurnTicks 600 readout is one stick bundle (TFC duration 600) lit and burnt out. PAR-HEAT-23 step 2 now says sneak, fuel with several bundles and check lit=true at once."
tests:
  - "itest: `firepit.cook` cooks raw meat on Paper and Folia"
done_criteria:
  - "Lit firepit cooks raw beef in the expected TFC time (self-test firepit.cook); the screenshot of the lit visual: deferred check tracked in PAR-HEAT-23"
---

# PAR-HEAT-19: Firepit loaded in game

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
