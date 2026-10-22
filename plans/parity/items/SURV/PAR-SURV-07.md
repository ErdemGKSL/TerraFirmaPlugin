---
id: PAR-SURV-07
title: "Player tab screens: calendar, nutrition and climate pages opened from outside the inventory"
area: SURV
status: open
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
  - "Click the tab button drawn left of the window"
  - "Switch to Nutrition and Climate; screenshot each"
plan:
  - "Outside-click (slot -999) in the player inventory opens our tab GUI (locked decision); the pack draws the tab button next to the inventory."
  - "Pages: calendar (date, season, day, time, as TFC CalendarScreen text), nutrition (5 bars as title glyph overlays from PlayerNutrition), climate (avg/actual temperature, rainfall, Koppen as TFC ClimateScreen)."
  - "Tab buttons switch pages; Escape returns to the inventory."
tests:
  - "paper: MockBukkit outside click opens the tab"
  - "tools/datagen: tab art present"
done_criteria:
  - "Screenshots of the three pages at GUI scale 2 and 3 aligned"
---

# PAR-SURV-07: Player tab screens: calendar, nutrition and climate pages opened from outside the inventory

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
