---
id: PAR-CLIM-01
title: "Calendar config, sleep skipping and doDaylightCycle sync"
area: CLIMATE
status: open
wave: 11
deps: [PAR-INFRA-01]
size: M
aliases: ["M2-CAL-03", "M2-CAL-01"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/calendar/ServerCalendar.java"
  - "src/main/java/net/dries007/tfc/util/calendar/CalendarTransaction.java"
  - "src/main/java/net/dries007/tfc/util/calendar/CalendarEventHandler.java"
  - "src/main/java/net/dries007/tfc/util/calendar/CalendarWorldData.java"
  - "src/main/java/net/dries007/tfc/config/ServerConfig.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/calendar/**"
  - "core/src/test/java/dev/tfp/core/calendar/**"
  - "core/src/test/resources/dev/tfp/core/calendar/**"
  - "paper/src/main/java/dev/tfp/paper/calendar/**"
  - "paper/src/test/java/dev/tfp/paper/calendar/**"
ui: true
in_game_steps:
  - "tfp time set night"
  - "setblock ~1 ~ ~ minecraft:red_bed; Bot right-clicks the bed and sleeps until morning"
  - "tfp debug player Bot (date before/after)"
plan:
  - "Current: calendar core, `/tfp time`, persistence and the date HUD are done (ledger 'Calendar' playable)."
  - "Config keys (same names as TFC CommonConfig/ServerConfig): defaultMonthLength (daysInMonth), calendarStartDay (reject -1 as TFC), enableTimeStopWhenServerEmpty."
  - "Sleep skipping: when vanilla skips the night (TimeSkipEvent SLEEP), advance the calendar by the same ticks through CalendarTransaction semantics (player ticks vs calendar ticks as TFC)."
  - "doDaylightCycle sync: the gamerule mirrors the calendar 'doDaylightCycle' state; arrested time when the server is empty if enabled."
tests:
  - "core: golden for the transaction arithmetic (TFC ServerCalendar.skipForwardBy)"
  - "paper: MockBukkit TimeSkipEvent advances calendar"
  - "itest: `calendar.sleep_skip` case"
done_criteria:
  - "Sleeping through a night advances the date shown by the HUD by the same amount (in-game check)"
---

# PAR-CLIM-01: Calendar config, sleep skipping and doDaylightCycle sync

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
