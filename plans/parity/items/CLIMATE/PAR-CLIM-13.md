---
id: PAR-CLIM-13
title: "TFC time command completion: `/tfp time set` dayLength, monthLength, rain and clear, and the calendar tick rate"
area: CLIMATE
status: blocked
wave: 14
deps: [PAR-CLIM-01, PAR-CLIM-12]
size: S
aliases: ["parity audit 2026-11-08: TimeCommand literals dayLength, monthLength, rain, clear and ServerCalendar.setCalendarTickRate named by no item (ledger row '/time set dayLength ...' not implemented)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/commands/TimeCommand.java"
  - "src/main/java/net/dries007/tfc/util/calendar/ServerCalendar.java"
  - "src/main/java/net/dries007/tfc/util/calendar/Calendar.java"
  - "src/main/java/net/dries007/tfc/util/tracker/WeatherHelpers.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/calendar/TimeCommand*.java"
  - "core/src/test/java/dev/tfp/core/calendar/TimeCommand*"
  - "core/src/main/java/dev/tfp/core/calendar/CalendarRate*.java"
  - "core/src/test/java/dev/tfp/core/calendar/CalendarRate*"
  - "paper/src/main/java/dev/tfp/paper/calendar/TimeRate*.java"
  - "paper/src/test/java/dev/tfp/paper/calendar/TimeRate*"
ui: false
in_game_steps: []
plan:
  - "BLOCKED (2026-11-14) on files outside files_owned: the rate and month-length changes need (1) core/src/main/java/dev/tfp/core/calendar/CalendarState.java (no way to change dayLengthMinutes, and the invariant dayLengthMinutes >= 1 cannot hold TFC's disabled rate 0), (2) core/src/main/java/dev/tfp/core/calendar/CalendarCodec.java (persist the disabled rate; new layout version), (3) paper/src/main/java/dev/tfp/paper/calendar/CalendarService.java (state is private and only skip(Delta) moves it forward; set dayLength/monthLength need an update(UnaryOperator<CalendarState>) that publishes and saves, and monthLength can move the tick backward), (4) the /tfp time handler: TfpCommands.Handler gets new methods implemented in paper/src/main/java/dev/tfp/paper/TfpPlugin.java (or paper/src/main/java/dev/tfp/paper/calendar/CalendarModule.java), and (5) paper/src/test/java/dev/tfp/paper/boot/BootstrapTest.java /tfp time tree (its hub line covers only /tfp debug children). Unblock: add these five paths to files_owned (edits limited to the rate/month-length API and the new literals)."
  - "Current (audit 2026-11-08): `/tfp time` has set day/noon/night/midnight/hour and add years/months/days/hours/ticks; TFC's `set dayLength <minutes>|default|realtime|disabled`, `set monthLength <days>|default|realtime`, `set rain` and `set clear` are missing (ledger row says not implemented), and the calendar has no tick rate (TFC ServerCalendar.setCalendarTickRate: calendar ticks per player tick = 20 / minutes, 0 when disabled)."
  - "core/calendar: port the calendar tick rate (persisted with the calendar, applied to calendar-tick advance only, player ticks unchanged, as TFC) and the four literals with TFC's arguments, limits (monthLength 1..1000) and reply keys (tfc.commands.time.set_day_length, set_day_length_disabled, set_month_length, add_time)."
  - "`set rain|clear`: TFC's search: from the current calendar tick, step 1,000 ticks up to 400,000 and skip forward to the first tick where `isPrecipitating(getRain(t), instantRainfall(pos))` equals the wanted state, using PAR-CLIM-12's cycle and the sender's position; nothing found = no change, as TFC."
  - "Wire the literals into the existing `/tfp time` tree (shared hub `boot/TfpCommands.java` only by adding lines, or the calendar FeatureModule); update the BootstrapTest command tree only through its listed hub line."
  - "Ledger: close the '/time set dayLength ...' divergence row; feature row update for the calendar."
tests:
  - "core: TimeCommandTest cases for every new literal (rate arithmetic 20 / minutes, disabled = 0, month length clamp) and a CalendarRate golden against TFC ServerCalendar"
  - "core: rain/clear search golden on a fixed seed and rainfall vs a TFC scratch export"
  - "paper: MockBukkit execution of the four literals, replies and the persisted rate after reload"
done_criteria:
  - "All TFC TimeCommand literals behave as TFC with passing tests; the ledger row is closed"
---

# PAR-CLIM-13: TFC time command completion

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
