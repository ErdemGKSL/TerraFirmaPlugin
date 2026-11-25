---
id: PAR-DATA-20
title: "Field guide BOOK tab: the fifth player tab opens the field guide (follow-up of PAR-DATA-04)"
area: DATA
status: done
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
  - "Done: BOOK is the fifth Tab (icon u 64, chest slot 31, tab column y 96, lang key tabs.title.book); TabListener.press closes the page and calls the field guide supplied by PlayerTabsModule (services().get(FieldGuideService.class) per click, so it works whichever module enabled first); datagen PlayerTabArt draws the fifth button and the taller tab column from TFC's icons (the TAB_ENUM check now covers BOOK). Tests: paper tabs 23 pass (2 new in PlayerTabsMockBukkitTest), datagen PlayerTabArtTest. Pending in-game check (container cannot run client)."
tests:
  - "paper: the BOOK tab click opens the contents book (fake FieldGuideService)"
done_criteria:
  - "The BOOK tab is drawn with the other four and opens the field guide"
---

# PAR-DATA-20: Field guide BOOK tab

Follow-up created by PAR-DATA-04. Follow `plans/parity/PROTOCOL.md`.
