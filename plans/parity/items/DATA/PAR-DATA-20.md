---
id: PAR-DATA-20
title: "Field guide BOOK tab: the fifth player tab opens the field guide (follow-up of PAR-DATA-04)"
area: DATA
status: open
wave: 36
deps: [PAR-DATA-04]
size: S
aliases: ["follow-up of PAR-DATA-04"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/client/screen/button/PlayerInventoryTabButton.java"
  - "src/main/java/net/dries007/tfc/network/OpenFieldGuidePacket.java"
  - "src/main/java/net/dries007/tfc/compat/patchouli/PatchouliIntegration.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/tabs/**"
  - "paper/src/test/java/dev/tfp/paper/tabs/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/tabs/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/tabs/**"
ui: true
in_game_steps:
  - "Open the inventory as Bot, click the BOOK tab button outside the window; screenshot the opened contents book at GUI scale 2"
plan:
  - "TFC shows a fifth BOOK tab (when Patchouli is installed) that opens the field guide. PAR-SURV-07 left it out because the guide's form was undecided; PAR-DATA-04 made the guide vanilla written books opened by dev.tfp.paper.guide.FieldGuideService (a module service: services().find(FieldGuideService.class)). Add the BOOK tab to TabLayout/TabTitles (TFC's order and icon), and on its click close the tab page and call FieldGuideService.openIndex on the player's thread. Record the ledger divergence row of PAR-SURV-07 (no BOOK tab) as resolved."
tests:
  - "paper: the BOOK tab click opens the contents book (fake FieldGuideService)"
done_criteria:
  - "The BOOK tab is drawn with the other four and opens the field guide"
---

# PAR-DATA-20: Field guide BOOK tab

> Claimed: parity worker session_011osUp9Lr5PbNofhx4XEc3S, 2026-11-25

Follow-up created by PAR-DATA-04. Follow `plans/parity/PROTOCOL.md`.
