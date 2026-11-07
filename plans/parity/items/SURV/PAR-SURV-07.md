---
id: PAR-SURV-07
title: "Player tab screens: calendar, nutrition and climate pages opened from outside the inventory"
area: SURV
status: done
wave: 8
deps: [PAR-INFRA-03]
size: M
aliases: ["M3-GUI-01", "gaps: inventory tab button"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/client/screen/CalendarScreen.java"
  - "src/main/java/net/dries007/tfc/client/screen/NutritionScreen.java"
  - "src/main/java/net/dries007/tfc/client/screen/ClimateScreen.java"
  - "src/main/java/net/dries007/tfc/network/SwitchInventoryTabPacket.java"
  - "src/main/resources/assets/tfc/textures/gui/player_calendar.png"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/tabs/**"
  - "paper/src/test/java/dev/tfp/paper/tabs/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/tabs/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/tabs/**"
ui: true
in_game_steps:
  - "Open the inventory (E)"
  - "Click the tab column drawn right of the window (any click outside the window opens the calendar page)"
  - "Switch to Nutrition and Climate; screenshot each"
plan:
  - "Outside-click (slot -999) in the player inventory opens our tab GUI (locked decision); the pack draws the tab button next to the inventory."
  - "Pages: calendar (date, season, day, time, as TFC CalendarScreen text), nutrition (5 bars as title glyph overlays from PlayerNutrition), climate (avg/actual temperature, rainfall, Koppen as TFC ClimateScreen)."
  - "Tab buttons switch pages; Escape returns to the inventory."
  - "Done: paper/tabs (PlayerTabsModule, TabListener outside-click trigger + page clicks + crafting table title, TabService capture/worker/apply, TabPage TFC text positions, TabTitles per-line fonts, TabLayout 4-row chest with tab buttons in slots 27-30, TabFont, Koppen); datagen tabs/PlayerTabArt (page art re-laid out, tab buttons from icons.png, 94-frame nutrient bars, night sprite, inventory tab column via lang container.crafting, 13 line fonts, frame advance markers). Tests: paper TabPageTest 9, PlayerTabsMockBukkitTest 7, PlayerTabLayoutConsistencyTest 2; datagen PlayerTabArtTest 7. Escape and the INVENTORY button close the page (the server cannot open the client inventory); 7 divergence rows in the ledger. In-game check passed at GUI scale 2 and 3: docs/verification/PAR-SURV-07/."
tests:
  - "paper: MockBukkit outside click opens the tab"
  - "tools/datagen: tab art present"
done_criteria:
  - "Screenshots of the three pages at GUI scale 2 and 3 aligned"
---

# PAR-SURV-07: Player tab screens: calendar, nutrition and climate pages opened from outside the inventory

> see_not_parity: NP-HUD-06, NP-UI-02 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
