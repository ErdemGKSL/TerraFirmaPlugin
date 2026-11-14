---
id: PAR-CLIM-18
title: "Snow catch-up after a calendar skip for chunks that stay loaded (/tfp time add, sleep skip)"
area: CLIMATE
status: done
wave: 13
deps: [PAR-CLIM-03]
size: S
aliases: ["follow-up of PAR-CLIM-03 (ledger: snow catch-up row, Revisit 'Yes (skip trigger)')"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/tracker/WeatherHelpers.java"
  - "src/main/java/net/dries007/tfc/util/calendar/ServerCalendar.java"
  - "src/main/java/net/dries007/tfc/common/commands/TimeCommand.java"
  - "src/main/java/net/dries007/tfc/util/calendar/CalendarEventHandler.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/weather/SnowSkipCatchUp*.java"
  - "paper/src/test/java/dev/tfp/paper/weather/SnowSkipCatchUp*.java"
  - "paper/src/main/java/dev/tfp/paper/weather/SnowService.java"
  - "paper/src/main/java/dev/tfp/paper/calendar/CalendarService.java"
  - "paper/src/main/java/dev/tfp/paper/calendar/CalendarModule.java"
ui: false
in_game_steps: []
plan:
  - "Found in the PAR-CLIM-03 review (2026-11-13): TFC's `ServerCalendar.skipForwardBy` (called by `/time add`, TimeCommand:133, and the sleep skip, CalendarEventHandler:116) advances player ticks by `calendarTicks / calendarTickRate` (1.2x at the default rate), so every chunk that stays loaded and ticked sees more than 4,000 ticks since its `lastRandomTick` on its next tick and runs the `onTickChunk` catch-up (WeatherHelpers:232-275). Ours runs `SnowCatchUp.plan` only on chunk load, so loaded chunks keep their old snow across a skip (ledger, snow catch-up row)."
  - "Build: when `CalendarService.skip` (and the sleep skip) moves the calendar, hand each loaded overworld chunk to its region and run the same path as `SnowService.WorldSnow.onLoad` (stamp check with `SnowCatchUp.isDue`, plan on the worker pool, forced steps in hops of `STEPS_PER_HOP`, budgeted pipeline), then stamp the chunk. Skips shorter than the 4,000-tick threshold do nothing, as in TFC. No ticking: one pass per skip."
  - "Keep `core/` unchanged unless a pure helper is needed (then add it under `core/.../weather/Snow*`, owned by PAR-CLIM-03's glob, in this item's commit)."
  - "Ledger: close the 'Yes (skip trigger)' revisit of the snow catch-up row."
  - "Done: SnowSkipCatchUp (the sampling round watches the published clock; a jump of more than 4,000 calendar ticks gives each loaded overworld chunk one region pass over the window from the later of its stamp and the tick before the jump, same plan, hops and budget as a load, then stamps it at the jump's end; no CalendarService hook was needed, so /tfp time, the sleep skip and vanilla /time are all covered). Tests: SnowSkipCatchUpMockBukkitTest (5). Server check: docs/verification/PAR-CLIM-18/verdict.md (56 -> 1 layers across a 6-month skip, chunk kept loaded)."
tests:
  - "paper: MockBukkit: a loaded chunk stamped at tick 1000, then `skip` by one month, gets a catch-up plan and its stamp moves to the new tick; a skip under 4,000 ticks plans nothing"
done_criteria:
  - "After `/tfp time add months 6` with the test chunk force-loaded across the skip, the January snow of PAR-CLIM-03's site melts without an unload (count before and after, as in docs/verification/PAR-CLIM-03/verdict.md)"
---

# PAR-CLIM-18: Snow catch-up after a calendar skip for chunks that stay loaded

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
