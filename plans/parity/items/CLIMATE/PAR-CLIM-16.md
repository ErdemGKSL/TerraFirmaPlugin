---
id: PAR-CLIM-16
title: "Calendar sleep-skip self-test: calendar.sleep_skip on Paper and Folia"
area: CLIMATE
status: open
wave: 12
deps: [PAR-CLIM-01]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/calendar/CalendarEventHandler.java"
  - "src/main/java/net/dries007/tfc/util/calendar/ServerCalendar.java"
files_owned:
  - "itest/src/main/java/dev/tfp/itest/cases/CalendarSleepSkipCase.java"
ui: false
in_game_steps: []
plan:
  - "Follow-up of PAR-CLIM-01, whose files_owned did not include an itest case file."
  - "Case `calendar.sleep_skip` (SelfTestSuite hub line): on the running server, with the calendar forced to run (`CalendarService.setRunWhenEmpty`), set the calendar to 9 pm with `CalendarService.skip`, check the overworld's full time is `calendarTicks + 18000` and the daylight rule is on; fire a `TimeSkipEvent` NIGHT_SKIP on the global thread (`OnThread.global`) and check the calendar is at 5 am the next day, the player ticks advanced by `slept * dayLength / 20`, the published clock moved, and `world.getFullTime() + event.getSkipAmount()` equals the calendar's new day time."
  - "Also check a `/gamerule` change of the daylight rule from the console is cancelled (WorldGameRuleChangeEvent) and an empty server with the time stop on turns the rule off."
tests:
  - "itest: `calendar.sleep_skip` passes on Paper 1.21.11 and Folia (:itest:runSelfTest)"
done_criteria:
  - "`calendar.sleep_skip` PASSED in a :itest:runSelfTest run on Paper and on Folia"
---

# PAR-CLIM-16: Calendar sleep-skip self-test: calendar.sleep_skip on Paper and Folia

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
