---
id: PAR-CLIM-22
title: "Vanilla /time and the night skip on 26.x: CalendarModule.onTimeSkip throws NoSuchMethodError on TimeSkipEvent.getSkipReason()"
area: CLIMATE
status: open
wave: 15
deps: []
size: S
aliases: ["follow-up of PAR-INFRA-23 (in-game runs 2026-11-14 on Paper 26.3-244: every vanilla `time set` logs the error; docs/verification/PAR-INFRA-13/verdict.md)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/calendar/CalendarEventHandler.java"
  - "src/main/java/net/dries007/tfc/ForgeEventHandler.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/calendar/CalendarModule.java"
  - "paper/src/main/java/dev/tfp/paper/calendar/TimeSkipReasons.java"
  - "paper/src/test/java/dev/tfp/paper/calendar/TimeSkipReasonsTest.java"
ui: false
plan:
  - "Found in the PAR-INFRA-23 runs on Paper 26.3-244: `CalendarModule.onTimeSkip` (paper/src/main/java/dev/tfp/paper/calendar/CalendarModule.java, the `event.getSkipReason()` call near line 213) throws `NoSuchMethodError: TimeSkipEvent.getSkipReason()` at every vanilla `/time set`, so on 26.x vanilla's `/time` and the night skip never become calendar skips (TFC `skipForwardBy`) and the world time drifts from the calendar until the next `DaylightSync`. Cause: the plugin compiles against Paper 1.21.11, where `org.bukkit.event.world.TimeSkipEvent` declares `getSkipReason()` returning `TimeSkipEvent$SkipReason`; in the 26.3 API `TimeSkipEvent` extends the new `ClockTimeSkipEvent`, `getSkipReason()` lives there and returns `ClockTimeSkipEvent$SkipReason` (COMMAND, CUSTOM, NIGHT_SKIP), and `TimeSkipEvent$SkipReason` no longer exists (`javap` of paper-api 26.3.build.244)."
  - "Fix: a version-safe reader `TimeSkipReasons.of(Event)` that resolves `getSkipReason` once by reflection (a `MethodHandle` on the event's runtime class, cached) and returns the enum constant's `name()` mapped to our own enum (COMMAND, CUSTOM, NIGHT_SKIP, UNKNOWN); `onTimeSkip` switches on that instead of comparing with `TimeSkipEvent.SkipReason`. `getSkipAmount()` and `getWorld()` keep their descriptors on both APIs (check with javap on both jars and note it in the class comment). An unknown or missing reason is treated as CUSTOM (left alone) and logged once."
  - "Check that the 26.x server still fires `TimeSkipEvent` (not only `ClockTimeSkipEvent`) for `/time set` and the night skip; if it fires `ClockTimeSkipEvent` without a world, decide how the world is found (the first normal world, as DaylightSync) and record it in the ledger calendar row."
tests:
  - "paper: TimeSkipReasonsTest (the 1.21.11 TimeSkipEvent with each reason maps to COMMAND, CUSTOM and NIGHT_SKIP; a stand-in event class whose getSkipReason() returns another enum type with the same constant names maps the same way, as on 26.x; an event without the method maps to UNKNOWN without throwing)"
  - "CalendarModuleMockBukkitTest keeps passing"
done_criteria:
  - "A vanilla `/time set` and the night skip on Paper 26.3 log no error and move the calendar as on 1.21.11 (server log of one run on each family in the commit message or docs/verification/PAR-CLIM-22/)"
  - "TimeSkipReasonsTest green; the ledger calendar row names the 26.x API difference"
---

# PAR-CLIM-22: Vanilla /time and the night skip on 26.x: CalendarModule.onTimeSkip throws NoSuchMethodError on TimeSkipEvent.getSkipReason()

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
