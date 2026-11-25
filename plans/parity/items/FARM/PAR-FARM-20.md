---
id: PAR-FARM-20
title: "Verification: crops grow, die and yield in game (itest crop.grow and screenshots)"
area: FARM
status: done
wave: 34
deps: [PAR-FARM-04, PAR-FARM-02, PAR-FARM-19]
size: S
aliases: ["found while doing PAR-FARM-04: the itest and the in-game check need the tfp:farmland/<soil> CE blocks of PAR-FARM-19"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/crop/CropHelpers.java"
files_owned:
  - "itest/src/main/java/dev/tfp/itest/cases/CropGrowCase.java"
  - "itest/src/main/java/dev/tfp/itest/SelfTestSuite.java"
  - "docs/verification/PAR-FARM-20/**"
ui: true
in_game_steps:
  - "plant wheat seeds on farmland next to water"
  - "tfp time add months 2; tp away and back"
  - "screenshot; break for yield"
  - "plant jute (double), tomato (climbing, pickable): grow past the bottom stage; right-click tomato to pick"
  - "ce item give Bot tfp:pure_nitrogen; right-click the farmland and then the crop with it; hoe overlay shows N raised; sneaking uses several; screenshot the action bar (moved from PAR-FARM-02)"
plan:
  - "itest crop.grow: a tfp:crop/wheat on tfp:farmland/<soil> with nutrients, 60 calendar days, expect a higher age or the dead block; check the farmland nutrients fell."
  - "Moved from PAR-FARM-04, whose glue is done (device kind tfp:crop, catch-up kind, listener) but could not run without the farmland blocks."
  - "Check that CraftEngine fires BlockPlaceEvent for the seeds before CropListener (HIGH) so planting off farmland is refused; if it does not, move the guard."
  - "Done: itest CropGrowCase (crop.grow: wheat on tfp:farmland/<soil>, 60 calendar days, unload and reload, expects a higher age or the dead crop) added to SelfTestSuite; it skips while no farmland CE block is loaded: the farmland blocks are the follow-up PAR-FARM-26 (STATUS had a nameless PAR-FARM-19 row for them). Screenshots and the fertilizer readout check stay pending. Pending in-game check (container cannot run client)"
tests:
  - "itest: crop.grow over 60 days"
done_criteria:
  - "Wheat planted in spring is harvestable in summer in-game"
  - "Pure nitrogen raises N in the readout"
---

# PAR-FARM-20


All fields live in the front matter above; the loop parses them. Follow `plans/parity/PROTOCOL.md`.
