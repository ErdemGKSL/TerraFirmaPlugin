# PAR-CLIM-23 verification: vanilla /time and the night skip move the calendar on Paper 26.3 and 1.21.11

- Date: 2026-11-17. Jar: `dist/build/libs/TerraFirmaPlugin-0.1.0-SNAPSHOT.jar` built from `65f0c7d` (PAR-CLIM-22's
  `TimeSkipReasons` and `ClockTimeSkipEvent` listener included); `itest-0.1.0-SNAPSHOT.jar` from the same tree.
- Servers: fresh run dirs from `spikes/client/server.sh` (TFC world, `TFP_WORLD=1`, priming start, world deleted, second
  start), Paper **1.21.11-132** and Paper **26.3-253** (stable, 2026-11-15; the item asks for build 244 or later),
  CraftEngine 26.9.2, BetterModel 3.5.1, BetterHud 2.2.0-SNAPSHOT-449, PacketEvents 2.14.0.
- Player: the vanilla 1.21.11 / 26.3 client as `Bot` under Xvfb (`spikes/client/session.sh`, pack applied). The 26.3
  client needs Mesa's Vulkan driver (`mesa-vulkan-drivers`, lavapipe); it was missing on this machine and was installed.
- Who ran it: this session had no subagent tool, so the CLAUDE.md delegation to a Haiku runner could not be done; the item
  worker drove the servers and judged the results.

## Steps (RCON, scratch `drive.py`; transcripts in `runs.txt`)

Peaceful, clear weather, Bot in survival on a stone platform at y 200 two blocks south of a red bed. A readout is vanilla
`time query daytime` (1.21.11) or `time query day` (26.3, the day timeline) plus `/tfp time add ticks 1`, whose reply prints
the calendar date and hour. Sequence: readout, `/tfp time set hour 21`, readout, vanilla `/time set day`, readout, vanilla
`/time add 1000`, readout, `/tfp time set hour 21`, readout, Bot right-clicks the bed (`xdotool click 3`), poll `SleepTimer`
until it drops to 0, readout. On 26.3 the run was repeated after setting `time.affects-all-worlds: true` in
`config/paper-global.yml` and restarting.

## Results

| step | 1.21.11 | 26.3, affects-all-worlds false | 26.3, affects-all-worlds true |
|---|---|---|---|
| `/time set day` at calendar 21:01 | "Set the time to 1000"; vanilla 1043; calendar June 3 21:01 -> **June 4 07:02** | "Clock minecraft:overworld is already at time marker minecraft:day" (see below); day timeline 15041 -> 1035; calendar June 3 21:02 -> **June 4 07:02** | same message; 15027 -> 1043; calendar July 2 21:01 -> **July 3 07:01** |
| `/time add 1000` | "Set the time to 2034"; vanilla 2078; calendar 07:02 -> **08:04** (+1 h, 2 s of running between readouts) | "Set minecraft:overworld to ... tick(s)"; 2070; calendar 07:02 -> **08:04** | same; 2077; calendar 07:01 -> **08:04** |
| night skip from calendar 21:01 (bed) | slept 5 s; vanilla **23001**; calendar June 4 21:01 -> **June 5 05:00** | slept 5.5 s; day timeline **23019**; calendar June 7 21:02 -> **June 8 05:08** read 7 s later (05:00 at wake; a second run read **June 7 05:00** right after waking) | slept 5.5 s; **23021** 0.5 s after waking; calendar July 3 21:02 -> **July 4 05:07** read 7 s later |

- Every `/time` moved the calendar forward to the same time of day vanilla moved to (calendar = vanilla day time - 18000),
  and the night skip moved it to 5 am of the next day, on both families and with both `affects-all-worlds` settings. With
  `affects-all-worlds: true` Paper fires the world-less `ClockTimeSkipEvent` for `/time` (PAR-CLIM-22 javap), which a
  `TimeSkipEvent` listener does not receive: the calendar moving there shows `CalendarModule.onClockTimeSkip` at work.
- On 26.3 the first `time query day` sampled right as `SleepTimer` read 0 still showed the pre-skip value (15185/15187)
  in two of the five sampled sleeps; the sample 0.5 s later read 23021, so the wake-up is seen a tick before the clock jump. Not a fault.
- Bed sleeps on 26.3 in the first pass did not start because the bed was placed a block off (the scratch script truncated
  negative coordinates) and later because Bot looked at the floor; both were script errors, fixed before the runs above.

## Logs (`log-scan.txt`)

No `NoSuchMethodError`, `NoClassDefFoundError`, `Could not pass event` or `Calendar:` line in any of the three manual runs.
The only WARN lines are the empty PacketEvents version line (GitHub API 403, known harmless), BetterHud's dev-build notice
and CraftEngine's `intentionally_empty` sound template notice.

## Self-test `calendar.sleep_skip` (item `tests`)

Run with `-Dtfp.selftest=true` and the itest jar on the same run dirs:
- 1.21.11: passes ("9 pm at calendar 1077000 (world 1095000); NIGHT_SKIP slept 8000 calendar / 9600 player ticks to 1085000
  (5 am), vanilla lands at 1103000; console /gamerule minecraft:advance_time false cancelled; empty server: calendar stopped").
- 26.3: **error** `java.lang.NoClassDefFoundError: org/bukkit/event/world/TimeSkipEvent$SkipReason` at
  `CalendarSleepSkipCase.sleepSkip` line 136: the test case itself builds a 1.21.11 `TimeSkipEvent` with the removed enum.
  The plugin is not involved; the case file is outside this item's `files_owned`: follow-up **PAR-CLIM-24**. (The other
  failing cases of these ad-hoc runs, dehydration, loot overrides and terrain, are unrelated to the calendar and are not
  judged here.)

## Finding: 26.x `/time set <marker>` answers "already at time marker"

Paper 26.3's `TimeCommand.moveToTimeMarker` fires the skip event with `target - current` and returns `NOT_MOVED` (the
command fails with `ERROR_ALREADY_AT_TIME_MARKER`) when the event's amount is 0 afterwards. `CalendarModule.skip` moves the
calendar inside the event, and its `DaylightSync` listener sets the world's full time to the new calendar time at once, so
the amount it then sets (`vanillaDayTime(new) - world.getFullTime()`) is 0. The time and the calendar did move, but the
sender sees a red error and the command result is a failure (command blocks, functions). 1.21.11 and `/time add` are not
affected. Follow-up **PAR-CLIM-25** (`CalendarModule` is outside this item's `files_owned`).

## Verdict

| criterion | result |
|---|---|
| vanilla `/time set`, `/time add` and the night skip on Paper 26.3 and 1.21.11 log no error and move the calendar (same time of day; night skip to 5 am), `time.affects-all-worlds` false and true on 26.3 | **PASS** |
| itest `calendar.sleep_skip` on 26.3 as on 1.21.11 | passes on 1.21.11; on 26.3 the case's own code fails to link: follow-up PAR-CLIM-24 |
