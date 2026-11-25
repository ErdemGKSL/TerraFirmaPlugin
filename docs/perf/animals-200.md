# 200-animal load test (PAR-REL-06)

The self-test case `husbandry.load_200` (`itest/src/main/java/dev/tfp/itest/cases/HusbandryRunCase.java`, nested class
`Load`) spawns 200 TFC animals and measures the plugin's share of the region thread for 60 s. Budgets from
`docs/perf/budgets.md`: PERF-TICK-01 (plugin share 2 ms per tick, hard cap per tick) and PERF-COMMIT-01 (1.5 ms per tick,
checked on the 99th percentile).

**Verdict (2026-11-25): over budget on Paper and Folia.** The plugin's animal goals take about 13 ms per tick for 200
animals, every sampled tick is above 1.5 ms, and one goal class, `livestockai.LivestockGoals$Avoid`, is 95 % of it. Fix
tracked in PAR-FAUNA-26. The Folia run also found a crash in the prey follow-adult goal (below).

## Setup

- 200 animals, 20 each of `tfc:cow`, `tfc:sheep`, `tfc:pig`, `tfc:goat`, `tfc:chicken`, `tfc:duck`, `tfc:alpaca`,
  `tfc:horse`, `tfc:deer`, `tfc:rabbit`, random age (`AgeHint.RANDOM`), at random surface positions (fixed seed) in the
  3x3 force-loaded chunks centred 40 chunks west and 40 south of spawn (a vanilla-terrain world: the self-test world). AI on.
- Plugin slice per tick = time spent in our goals in that tick + the heaviest staggered-check phase:
  - Goals: every goal our species behaviours install is removed and re-added at its priority wrapped in a timer
    (`shouldActivate`, `shouldStayActive`, `start`, `stop`, `tick`; this includes the vanilla pathfinding our goals call).
    A region task on the herd's region reads and resets the sum every tick. Vanilla goals and the carriers' own entity
    ticking are not counted.
  - Checks: each animal's staggered check body (age step and every `SpeciesBehaviour.onCheck`) is timed three times on its
    thread; the worst of the three is summed per check phase (`AnimalService.PERIOD` = 20 phases); the heaviest phase is
    added to every tick (an upper bound).
- 5 s warm-up after wrapping, then 60 s of samples (1199 region ticks).
- Also reported, not asserted: the interval between region ticks and, on Paper, `Server.getTickTimes()` (whole-server MSPT,
  vanilla included).

## Results

Times in ms per tick.

| Platform | Slice mean | p50 | p99 | max | Goals mean | Goals p99 | Goals max | Worst check phase | Check per animal mean / max (µs) | Ticks > 1.5 ms | Region tick interval p99 / max | Server MSPT mean / p99 / max |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Paper 1.21.11-132 | 13.979 | 12.587 | 33.371 | 53.738 | 13.163 | 32.555 | 52.922 | 0.817 | 42.2 / 722.0 | 1199 of 1199 | 51.120 / 77.141 | 16.743 / 36.785 / 63.996 |
| Folia 1.21.11-14 | 14.823 | 13.470 | 28.249 | 55.004 | 13.090 | 26.517 | 53.272 | 1.733 | 44.8 / 1297.9 | 1199 of 1199 | 50.607 / 64.159 | n/a |

Goal time by class (ms per tick, mean over the window):

| Goal class | Paper | Folia |
|---|---|---|
| `livestockai.LivestockGoals$Avoid` | 12.552 | 12.422 |
| `prey.PreyFleeGoal` | 1.636 | 1.449 |
| `prey.SmallPreyGoals$AvoidPredator` | 0.496 | 0.357 |
| `livestockai.LivestockGoals$FollowAdult` | 0.301 | 0.324 |
| `livestockai.LivestockGoals$Tempt` | 0.142 | 0.161 |
| `prey.PreyIdleGoals$FollowAdult` (Paper) / `livestockai.LivestockGoals$Breed` (Folia) | 0.082 | 0.082 |

Earlier Paper run on the same machine (stale generated content, same code paths): slice mean 14.749, p99 34.973, max
65.141; goals mean 13.630; server MSPT mean 17.538.

Folia: 2 of the 200 animals were removed during the run. A baby prey's `PreyIdleGoals$FollowAdult` calls
`PaperPreyBody.moveTo(Mob, Entity, double)`, which calls `Pathfinder.moveTo(Entity, double)`; Folia 1.21.11-14 has no such
method, the goal throws `NoSuchMethodError` and Folia removes the entity ("Entity threw exception"). Not seen on Paper
1.21.11-132. Tracked in PAR-FAUNA-26.

Reading: the 200 animals cost the whole Paper server 16.7 ms per tick on average, of which our goals are 13.2 ms. The
staggered checks are small (under 2 ms in the worst phase). The avoid goal's sensing builds a sighting (record and tag
reads) for every living entity in range on each of its periods, so a dense herd scales with the square of its size.

## Machine and command

- 4 vCPU Intel Xeon @ 2.10 GHz, 15 GB RAM, shared cloud container (other agents' builds ran at the same time), JDK
  Temurin 25.0.5.1, server `-Xmx3G`.
- CraftEngine 26.9.2, BetterModel 3.5.1, BetterHud 2.2.0-SNAPSHOT-449, PacketEvents 2.14.0; plugin at commit 9b68001 plus
  this case.
- Commands (the self-test runs every case; the numbers are in the `husbandry.load_200` line of the server log and in
  `itest/build/selftest-run/<platform>-1.21.11/selftest-report.xml`):

  ```
  ./gradlew :itest:runSelfTest -Pmc=1.21.11 -Pplatform=paper
  ./gradlew :itest:runSelfTest -Pmc=1.21.11 -Pplatform=folia
  ```

  With several self-test servers on one machine, set `server-port` in `itest/build/selftest-run/<platform>-1.21.11/server.properties`
  to a free port first (the default 25565 was taken by another worktree's run).
- spark was not used (it ships with Paper but needs a command and a viewer upload; the case measures its own slice).
