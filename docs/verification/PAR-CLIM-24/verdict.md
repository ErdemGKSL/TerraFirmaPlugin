# PAR-CLIM-24 verdict

Date: 2026-11-18. Base HEAD: 9df30b0 plus the PAR-CLIM-24 change to `itest/.../CalendarSleepSkipCase.java`.
Runs: `./gradlew :itest:runSelfTest -Pplatform=paper -Pmc=<version>` (CraftEngine, BetterModel, BetterHud installed).

## Finding while fixing

The item text assumed a 26.x constructor `TimeSkipEvent(World, ClockTimeSkipEvent$SkipReason, long)`. Paper 26.3 build 254
(`paper-api-26.3.build.254-stable`, javap) has only

```
public org.bukkit.event.world.TimeSkipEvent(org.bukkit.World, io.papermc.paper.world.WorldClock,
        org.bukkit.event.world.ClockTimeSkipEvent$SkipReason, long);
```

A first run with only the three-argument shape skipped the case on 26.3 ("no TimeSkipEvent(World, <reason with
NIGHT_SKIP>, long) constructor"). The builder now accepts `(World, <extra>..., <enum with NIGHT_SKIP>, long)` and fills each
extra parameter from the public no-argument `World` getter of that exact type (`getDefaultClock()` for `WorldClock`).

## Report excerpts

Paper 26.3-254-main@f7153ca (API 26.3.build.254-stable):

```
[selftest] PASSED calendar.sleep_skip: night skip moves the calendar to 5 am, daylight rule held (PAR-CLIM-16): 9 pm at
calendar 8661000 (world 8679000); NIGHT_SKIP slept 8000 calendar / 9600 player ticks to 8669000 (5 am), vanilla lands at
8687000; console /gamerule minecraft:advance_time false cancelled; empty server: calendar stopped, minecraft:advance_time off
```

Paper 1.21.11-132 (API 1.21.11-R0.1-SNAPSHOT):

```
[selftest] PASSED calendar.sleep_skip: night skip moves the calendar to 5 am, daylight rule held (PAR-CLIM-16): 9 pm at
calendar 981000 (world 999000); NIGHT_SKIP slept 8000 calendar / 9600 player ticks to 989000 (5 am), vanilla lands at
1007000; console /gamerule minecraft:advance_time false cancelled; empty server: calendar stopped, minecraft:advance_time off
```

Other cases failing in the same runs (`ce.pack_issues`, `firepit.cook`, `loot.vanilla_overrides`,
`worldgen.terrain_generates` on 26.3) are outside this item and unchanged by it.

## Verdict

| criterion | result |
|---|---|
| calendar.sleep_skip passes in the self-test on Paper 26.3 and 1.21.11 | pass |
| unit test of the reflective builder on the 1.21.11 API (`CalendarSleepSkipCaseTest`, 3) | pass |
| `./gradlew check` | pass |

## Review round (2026-11-18)

The tester's full `./gradlew check` on 8165e7f printed `:itest:test FAILED` once:
`CaseRunnerTest > aSkipThrownAtRunTimeIsASkipWithItsReason()` at `CaseRunnerTest.java:102`. That test and `CaseRunner` are
not touched by this item. Its "async skip" case throws from `CompletableFuture.supplyAsync` under a 200 ms case timeout, so
when the common pool starts late on a loaded machine the runner reports a timeout instead of the skip. It passed in the
tester's forced rerun and in a second full `./gradlew check` on this branch (below). The race is tracked as PAR-INFRA-62
(owns `CaseRunnerTest.java`).
Second full `./gradlew check` (JAVA_HOME=/opt/jdk/25) on 4ecdae7 with this round's plan files: BUILD SUCCESSFUL in 8m 22s, no failed task, CaseRunnerTest 0 failures.
