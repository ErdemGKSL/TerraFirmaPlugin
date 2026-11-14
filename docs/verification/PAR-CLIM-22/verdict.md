# PAR-CLIM-22 verification

Date: 2026-11-14. Base: `bada12d` plus the PAR-CLIM-22 commit.

## API difference (javap)

| | paper-api 1.21.11-R0.1-SNAPSHOT | paper-api 26.3.build.244-stable |
|---|---|---|
| `TimeSkipEvent` superclass | `WorldEvent implements Cancellable` | `ClockTimeSkipEvent` (`extends Event implements Cancellable`) |
| `getSkipReason()` | on `TimeSkipEvent`, returns `TimeSkipEvent$SkipReason` | on `ClockTimeSkipEvent`, returns `ClockTimeSkipEvent$SkipReason` (COMMAND, CUSTOM, NIGHT_SKIP); `TimeSkipEvent$SkipReason` does not exist |
| `getWorld()` | inherited from `WorldEvent` | declared on `TimeSkipEvent` |
| `getSkipAmount()J`, `setSkipAmount(J)V` | on `TimeSkipEvent` | inherited from `ClockTimeSkipEvent` |

Our compiled call sites `TimeSkipEvent.getWorld()`, `getSkipAmount()`, `setSkipAmount(J)` therefore still link on 26.x; only
`getSkipReason()` did not (the `NoSuchMethodError` of the PAR-INFRA-23 run, `docs/verification/PAR-INFRA-13/verdict.md`).

## Which event the 26.3 server fires (javap of paper-26.3.jar, build 244)

- Night skip: `ServerLevel.tick` creates `new TimeSkipEvent(world, ClockTimeSkipEvent$SkipReason.NIGHT_SKIP, amount)`.
- `/time set|add` and time markers: `TimeCommand` calls `CraftEventFactory.createTimeSkipEvent`, which returns
  `new TimeSkipEvent(sourceLevel, COMMAND, amount)`, or a world-less `new ClockTimeSkipEvent(COMMAND, amount)` when
  `paper-global.yml` `time.affects-all-worlds` is true (default false). `TimeSkipEvent` has its own `HandlerList`, so a
  `TimeSkipEvent` listener does not receive the bare `ClockTimeSkipEvent`: `CalendarModule` registers for that class
  where it exists and measures the skip against the first overworld-type world.
- `World.setFullTime` (our own `DaylightSync`): `TimeSkipEvent` `CUSTOM`, left alone.

## Link check on the 26.3 API

Our classes as built (against 1.21.11) on a plain JVM with the 26.3 server's `libraries/` (paper-api 26.3.build.244 only)
on the class path: each 26.3 `TimeSkipEvent` reason is passed through `CalendarModule.onTimeSkip` (no `LinkageError`) and
`TimeSkipReasons`, and a 26.3 `ClockTimeSkipEvent` through `TimeSkipReasons.of/amount/setAmount`:

```
TimeSkipEvent COMMAND -> COMMAND; ClockTimeSkipEvent COMMAND -> COMMAND amount=1000
TimeSkipEvent CUSTOM -> CUSTOM; ClockTimeSkipEvent CUSTOM -> CUSTOM amount=1000
TimeSkipEvent NIGHT_SKIP -> NIGHT_SKIP; ClockTimeSkipEvent NIGHT_SKIP -> NIGHT_SKIP amount=1000
OK: CalendarModule.onTimeSkip and TimeSkipReasons link and map on the 26.3 API
```

## Tests (1.21.11 API, MockBukkit)

`TimeSkipReasonsTest` 7 (real 1.21.11 `TimeSkipEvent` per reason; a stand-in with its own same-named enum as on 26.x;
no method and an unknown constant map to UNKNOWN without throwing; amount accessors; the module on a world-less stand-in
for COMMAND and NIGHT_SKIP, CUSTOM and cancelled left alone, an unknown reason left alone and logged once);
`CalendarModuleMockBukkitTest` 10 unchanged and green.

## Pending

The server-log criterion (a vanilla `/time set` and a night skip on Paper 26.3 and 1.21.11 with no error and the calendar
moved) needs an in-server run, which this repository delegates to a separate test-runner agent; tracked in PAR-CLIM-23.
