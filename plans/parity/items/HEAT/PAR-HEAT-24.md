---
id: PAR-HEAT-24
title: "Grill loaded in game: configuration/devices/grill.yml, TFC grill models, the grill screen art and the in-game grill check"
area: HEAT
status: done
wave: 17
deps: [PAR-HEAT-08, PAR-HEAT-19, PAR-HEAT-23]
size: S
aliases: ["follow-up of PAR-HEAT-08 (loading the grill in CraftEngine changes DatagenTest's fixture expectation, the model look needs hitbox demands of its own, the screen art needs a datagen generator; its in-game check moved here)"]
tfc_refs:
  - "src/main/resources/assets/tfc/blockstates/grill.json"
  - "src/main/resources/assets/tfc/models/block/firepit_grill.json"
  - "src/main/resources/assets/tfc/textures/gui/fire_pit_grill.png"
  - "src/main/java/net/dries007/tfc/client/screen/GrillScreen.java"
  - "src/main/java/net/dries007/tfc/client/render/blockentity/GrillBlockEntityRenderer.java"
files_owned:
  - "tools/datagen/src/test/java/dev/tfp/datagen/coverage/BlockCoverageTest.java"
  - "itest/src/test/java/dev/tfp/itest/SelfTestSuiteTest.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/HitboxAllocationTest.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/HitboxCarrierTest.java"
  - "itest/src/main/java/dev/tfp/itest/SelfTestSuite.java"
  - "docs/verification/PAR-HEAT-24/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/Grill.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/GrillTest.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/DatagenTest.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/grillgui/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/grillgui/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxDemands.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/device/DeviceSpecs.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/GeneratedCarrierTest.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/device/DeviceSpecParityTest.java"
  - "content/generated/content/craftengine/tfp/configuration/devices/grill.yml"
  - "itest/src/main/java/dev/tfp/itest/cases/GrillCookCase.java"
  - "docs/verification/PAR-HEAT-08/**"
ui: true
in_game_steps:
  - "op Bot; tfp debug place tfp:firepit[lit=false] on a solid block, fuel it with stick bundles and light it with a sneaking strong fire start (PAR-HEAT-23 steps); `/tfp debug device` shows lit=true"
  - "ce item give Bot tfp:wrought_iron_grill; right-click the lit firepit with it (not sneaking): the block turns into tfp:grill[lit=true], `/tfp debug device` shows kind tfp:grill with the firepit's fuel slots, burn ticks and temperature"
  - "right-click the top of the grill with `ce item give Bot tfp:food/beef` over two different slots: each raw beef shows on top at its slot (item display, scale 0.3, lying flat); screenshot at GUI scale 2 and 3"
  - "tfp time add hours 1; right-click the grill with an empty hand (not sneaking): the grill screen opens (fuel column, five grill slots, thermometer); screenshot at GUI scale 2 and 3; hover the cooked beef: its tooltip shows the Wood Grilled trait"
  - "sneak-click with an empty hand on a cooked beef: it comes off; sneak-click elsewhere on the lit grill: the wrought iron grill comes back, the block is a firepit again (still lit, same fuel) and the player takes 1 grill damage"
plan:
  - "UNBLOCKED (manager, 2026-11-18): files_owned widened to BlockCoverageTest, SelfTestSuiteTest, HitboxAllocationTest and HitboxCarrierTest as the BLOCKED line asks. Apply docs/verification/PAR-HEAT-24/pending.patch (git apply; resolve drift by hand), then do the model look (MODEL demands lit/unlit x axis on their own carriers), drop the PAR-HEAT-34 mention if the model lands here, run ./gradlew check, redo the in-game steps and finish. The hitbox tests are shared: edit only your own pins and keep others on rebase."
  - "Blocked (2026-11-18): the work needs three test files outside files_owned. (1) tools/datagen/src/test/java/dev/tfp/datagen/coverage/BlockCoverageTest.java: everyTfcBlockstateHasAClassAndNoneIsUnowned pins `tfc:grill` as `family:devices/grill.yml`; once Grill.run writes configuration/devices/grill.yml it is `ce:configuration/devices/grill.yml` (one line). (2) itest/src/test/java/dev/tfp/itest/SelfTestSuiteTest.java: the case count 35 -> 36 and a `grill.cook` line (the SelfTestSuite hub line alone fails :itest:test). (3) tools/datagen/src/test/java/dev/tfp/datagen/hitbox/HitboxAllocationTest.java: deviceCarriers pins the grill's two VANILLA_LOOK appearances, so the model plan line (MODEL demands lit/unlit x axis on their own carriers) cannot land. Unblock by adding these three files to files_owned (HitboxCarrierTest.java too if the grill's carriers move). Ready work, `./gradlew check` green with it on 4ecdae7: docs/verification/PAR-HEAT-24/pending.patch (git apply): Grill.run writes LOADED_OUTPUT, GrillTest/DatagenTest flipped, datagen grillgui/GrillScreenArt (tfp:gui/grill in configuration/grill_gui.yml: TFC's fire_pit_grill.png on the 4-row chest, frames lifted 4 px onto chest row 0, the connector stems drawn 4 px longer, thermometer sprite checked equal to the firepit's) + GrillScreenArtTest (6), the Datagen hub line, itest GrillCookCase + SelfTestSuite line, and fixes (1) and (2). Not in it: the model look; the patch's Grill javadoc names a follow-up PAR-HEAT-34 for it, drop that when the model is done here. Ledger rows to update at done: 'Grill on a firepit (PAR-HEAT-08)' (Not in game yet text), the grill screen divergence (art drawn, revisit No), and the two grill carrier rows (473, 476)."
  - "Current: PAR-HEAT-08 ships the grill device kind tfp:grill (paper/grill: GrillDevice, GrillConversion firepit <-> grill, GrillListener for the wrought iron grill on a firepit, the five item displays, GrillScreens) and `Grill.loadedYaml` (template tfp:block/device, pinned by GrillTest), but does not write it: a grill in configuration/ makes DatagenTest#writesOreOverlayItemsTemplatesAndRuntimeData's `non_occluding` list `[tfp:firepit, tfp:grill]` (right: TFC's grill is noOcclusion)."
  - "Write `Grill.LOADED_OUTPUT` in `Grill.run` (`out.put(LOADED_OUTPUT, loadedYaml(tfc))`), flip GrillTest's `isNull` check, change the DatagenTest expectation to `containsExactly(\"tfp:firepit\", \"tfp:grill\")` and regenerate (verifyContent)."
  - "Look: TFC's grill.json is a multipart (firepit_grill per axis + the firepit's lit part). Switch the grill's two VANILLA_LOOK demands to model demands (lit x axis) with `snow[layers=5]` hidden (ledger: held-snow ghost, PAR-INFRA-51), after PAR-HEAT-23 has done the same for the firepit; copy firepit_grill and its textures through AssetPipeline."
  - "Screen art: a datagen generator (grillgui/) writes `tfp:gui/grill` from TFC's fire_pit_grill.png re-laid out on the 4-row chest of `paper/grill/gui/GrillScreenLayout` (grill row moved up 4 px to chest row 0); the thermometer reuses the firepit strip (PAR-HEAT-20)."
  - "Self-test `grill.cook` (GrillCookCase + its SelfTestSuite line): a lit firepit converted by GrillConversion.toGrill keeps its fuel; raw beef in slot 4 cooks to cooked beef carrying tfp:wood_grilled after the calendar moves 1000 ticks. Run on Paper and Folia 1.21.11."
  - "Done: pending.patch applied (Grill.run writes configuration/devices/grill.yml, grillgui/GrillScreenArt tfp:gui/grill + GrillScreenArtTest, itest GrillCookCase + SelfTestSuite line, BlockCoverageTest/SelfTestSuiteTest/DatagenTest flips). Model look: no free state scores 0.5+ for the grill outline with a model (snow[layers=5] has no free hide target), so the grill is hitbox-only on an unlit soul campfire and draws TFC's multipart merged per lit value (tfp:block/grill_lit/_unlit) as entity_renderer item displays turned per axis (DeviceSpecs.firepitRendered); snow[layers=5] stays reserved by a no-appearance grill demand so no other carrier moved. Tests: GrillTest (+mergedModelsAreTheGrillOverTheFire), GeneratedCarrierTest#theGrillCarrierIsPinned, DeviceSpecParityTest, HitboxAllocationTest; ./gradlew check green. Ledger: grill row playable, carrier/model/screen divergences. In-game screenshot check left to the verify stage (docs/verification/PAR-HEAT-24/): check the axis-z turn of the display and the 7 px outline."
tests:
  - "datagen: GrillTest loaded entry written; DatagenTest non_occluding"
  - "itest: `grill.cook` on Paper and Folia"
done_criteria:
  - "Grill cooks with the grilled trait (tooltip) and items show on top"
---

# PAR-HEAT-24: Grill loaded in game: configuration/devices/grill.yml, TFC grill models, the grill screen art and the in-game grill check

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
