---
id: PAR-CLIM-25
title: "Vanilla /time set <marker> on 26.x answers \"already at time marker\" although the time and the calendar moved (skip amount 0 after DaylightSync)"
area: CLIMATE
status: done
wave: 17
deps: [PAR-CLIM-23]
size: S
aliases: ["follow-up of PAR-CLIM-23 (2026-11-17): in-game run on Paper 26.3-253; docs/verification/PAR-CLIM-23/verdict.md"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/calendar/CalendarEventHandler.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/calendar/CalendarModule.java"
  - "paper/src/test/java/dev/tfp/paper/calendar/CalendarModuleMockBukkitTest.java"
  - "paper/src/test/java/dev/tfp/paper/calendar/TimeSkipReasonsTest.java"
ui: false
plan:
  - "Found in PAR-CLIM-23 on Paper 26.3-253 (both time.affects-all-worlds settings): vanilla `/time set day` at calendar 21:00 moves the day timeline to ~1000 and the calendar to 07:00 the next day, but the command answers \"Clock minecraft:overworld is already at time marker minecraft:day\" (a command failure). Cause (javap of paper-26.3.jar `TimeCommand.moveToTimeMarker`): it fires the skip event with `target - current`, then returns `NOT_MOVED` (ERROR_ALREADY_AT_TIME_MARKER) when the event's amount is 0, else `setTotalTicks(current + amount)` with `current` read before the event. `CalendarModule.skip` moves the calendar inside the event and `CalendarService`'s listener (`DaylightSync.apply`) sets the world's full time to the new calendar time at once, so `vanillaDayTime(new) - world.getFullTime()` is 0. 1.21.11's `/time set` (reads the time after the event) and 26.x `/time add` are not affected."
  - "Fix: compute the amount against the world time read before the calendar skip (capture `world.getFullTime()` first, then `cal.skip`, then set `vanillaDayTime(new) - before`), so vanilla's own `current + amount` lands on the calendar's day time and the command reports success; check that 1.21.11 (`setDayTime(getDayTime() + amount)` after the event, where the world is already moved) still lands right, e.g. by suppressing the immediate DaylightSync write during a vanilla skip and letting vanilla apply the amount, or by version-specific handling. Cover both paths in MockBukkit (an event whose amount is applied to the pre-event time and one applied to the post-event time)."
  - "Done: CalendarModule.skip reads the world time before the calendar skip, puts the event's world back there after the skip's DaylightSync and sets the amount from it, so vanilla lands on the calendar whether it adds the amount to the time read before (26.x) or after (1.21.11) the event; test CalendarModuleMockBukkitTest.timeSetMarkerKeepsARealAmountForBothVanillaFamilies (+ night-skip hold assert), ./gradlew check green; in game on Paper 26.3-254 (affects-all-worlds false/true) and 1.21.11-132 all four markers answer success and move the calendar: docs/verification/PAR-CLIM-25/verdict.md"
tests:
  - "paper: CalendarModuleMockBukkitTest: a COMMAND skip leaves a non-zero amount that lands vanilla on the calendar day time from the pre-event world time; the 1.21.11 landing is unchanged"
  - "delegated in-game run on Paper 26.3 and 1.21.11: `/time set day` answers success and moves calendar and clock"
done_criteria:
  - "On Paper 26.3 a vanilla `/time set day|noon|night|midnight` answers success and moves the calendar to that time of day; 1.21.11 unchanged; evidence in docs/verification/PAR-CLIM-25/"
---

# PAR-CLIM-25: Vanilla /time set <marker> on 26.x answers "already at time marker" although the time and the calendar moved (skip amount 0 after DaylightSync)

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
