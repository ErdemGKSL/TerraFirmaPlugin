---
id: PAR-CLIM-24
title: "Self-test calendar.sleep_skip on 26.x: build the NIGHT_SKIP event without TimeSkipEvent$SkipReason (NoClassDefFoundError on Paper 26.3)"
area: CLIMATE
status: done
wave: 17
deps: [PAR-CLIM-23]
size: S
aliases: ["follow-up of PAR-CLIM-23 (2026-11-17): the self-test run on Paper 26.3-253 errors in the test case itself; docs/verification/PAR-CLIM-23/verdict.md"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/calendar/CalendarEventHandler.java"
files_owned:
  - "itest/src/main/java/dev/tfp/itest/cases/CalendarSleepSkipCase.java"
  - "itest/src/test/java/dev/tfp/itest/cases/CalendarSleepSkipCaseTest.java"
ui: false
plan:
  - "Found in the PAR-CLIM-23 self-test run on Paper 26.3-253: `calendar.sleep_skip` errors with `java.lang.NoClassDefFoundError: org/bukkit/event/world/TimeSkipEvent$SkipReason` at `CalendarSleepSkipCase.sleepSkip` (line 136, `new TimeSkipEvent(world, TimeSkipEvent.SkipReason.NIGHT_SKIP, amount)`). On the 26.x API that enum is gone: `TimeSkipEvent` extends `ClockTimeSkipEvent` and its constructor takes `ClockTimeSkipEvent$SkipReason` (javap in docs/verification/PAR-CLIM-22/verdict.md). The plugin itself is fine there (the in-game night skip moves the calendar to 5 am, PAR-CLIM-23)."
  - "Fix: build the event version-safely in the case: find the `TimeSkipEvent` constructor `(World, <enum>, long)` by reflection and pass the enum constant named `NIGHT_SKIP` of its parameter type (`Enum.valueOf`), cached once; skip the case with a clear reason if no such constructor exists. Keep the rest of the case unchanged."
  - "Done: CalendarSleepSkipCase builds the NIGHT_SKIP event through a reflectively found TimeSkipEvent constructor (World first, enum with NIGHT_SKIP, long last; extra parameters from the world's no-argument getter of that type, since Paper 26.3 build 254 takes (World, WorldClock, ClockTimeSkipEvent$SkipReason, long) and gets getDefaultClock()); CalendarSleepSkipCaseTest 3; calendar.sleep_skip PASSED on Paper 26.3-254 and 1.21.11-132; docs/verification/PAR-CLIM-24/verdict.md."
  - "Review 2026-11-18: one full check reported CaseRunnerTest.aSkipThrownAtRunTimeIsASkipWithItsReason failing (outside files_owned, untouched by this item: a 200 ms timeout on a common-pool supplyAsync under load); it passes on rerun and in a second full ./gradlew check, so it is tracked as follow-up PAR-INFRA-62."
tests:
  - "itest: SelfTestSuiteTest keeps passing; a unit test for the reflective builder on the 1.21.11 API (a NIGHT_SKIP TimeSkipEvent with the given world and amount)"
  - "itest calendar.sleep_skip passes on Paper 26.3 and 1.21.11 (delegated server run)"
done_criteria:
  - "calendar.sleep_skip passes in the self-test on Paper 26.3 and 1.21.11; report excerpts in docs/verification/PAR-CLIM-24/"
---

# PAR-CLIM-24: Self-test calendar.sleep_skip on 26.x: build the NIGHT_SKIP event without TimeSkipEvent$SkipReason (NoClassDefFoundError on Paper 26.3)

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
