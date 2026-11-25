# 200-animal load test (PAR-REL-06)

The self-test case `husbandry.load_200` (`itest/src/main/java/dev/tfp/itest/cases/HusbandryRunCase.java`, nested class
`Load`) spawns 200 TFC animals and measures the plugin's share of the region thread for 60 s. Budgets from
`docs/perf/budgets.md`: PERF-TICK-01 (plugin share 2 ms per tick, hard cap per tick) and PERF-COMMIT-01 (1.5 ms per tick,
checked on the 99th percentile).

**Update (2026-11-25, PAR-FAUNA-26): goals 13.2 -> 0.65 ms/tick (Paper) and 13.1 -> 0.42 (Folia), no Folia crash; still
over budget** because the staggered check phase (about 1.2 to 1.4 ms on every tick, outside the AI code) and single-tick
spikes remain. Numbers in "After PAR-FAUNA-26" below; the rest is tracked in PAR-FAUNA-28.

**Verdict (2026-11-25, PAR-REL-06): over budget on Paper and Folia.** The plugin's animal goals take about 13 ms per tick for 200
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

## After PAR-FAUNA-26

What changed in the animal AI (`paper/livestockai`, `paper/prey`), keeping TFC's choices:

- A sensor reading (`prey/Reading`) holds the entities in range and tests line of sight lazily, once per entity per
  reading, as vanilla's `NearestVisibleLivingEntities` does; every goal filters by kind first (avoidable kind, same
  species, player, land predator) and asks for the line of sight only for entities it would pick.
- An entity's kind (species from its record, `prey/SpeciesCache`; tag bits per species, `prey/SpeciesTags`) is read once
  per entity, not once per entity per observer per period; a sighting is built only for an avoidable candidate, at most
  once per entity and tick (`prey/Sightings`).
- One nearby-entity scan per chunk section, range and tick, shared by the animals of that section (`prey/NearbyScan`).
- The first sensor reading is dated back by 0 to 19 ticks by entity id (vanilla's sensor starts at a random tick), so a
  herd's readings spread over the 20-tick period.
- `PaperPreyBody.moveTo(Mob, Entity, double)` uses `Pathfinder.moveTo(LivingEntity, double)` (Folia 1.21.11-14 has no
  `moveTo(Entity, double)`). Every other `Pathfinder` / `MobGoals` call in both packages (`moveTo(Location, double)`,
  `stopPathfinding`, `hasPath`, `setCanFloat`, `MobGoals.removeGoal(Mob, GoalKey)`) is in the Folia 1.21.11-14 API.
- Async decision: not used. Paper's `Goal.shouldActivate` needs its answer in the same call and the decisions left are
  the pure `core` rules on a handful of candidates (microseconds); a hand-off would cost more than it saves.

Same setup and commands as above, plugin at 780784b plus PAR-FAUNA-26, 2026-11-25. Times in ms per tick.

| Platform | Slice mean | p50 | p99 | max | Goals mean | Goals p99 | Goals max | Worst check phase | Check per animal mean / max (µs) | Ticks > 1.5 ms | Region tick interval p99 / max | Server MSPT mean / p99 / max |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Paper 1.21.11-132 | 2.013 | 1.950 | 2.917 | 39.665 | 0.649 | 1.554 | 38.301 | 1.364 | 44.1 / 962.8 | 1199 of 1199 | 50.467 / 71.166 | 4.747 / 9.898 / 47.680 |
| Folia 1.21.11-14 | 1.602 | 1.541 | 2.323 | 5.245 | 0.420 | 1.142 | 4.063 | 1.182 | 41.1 / 857.0 | 764 of 1199 | 50.444 / 65.678 | n/a |

Goal time by class after the change (ms per tick, the six largest):

| Paper | | Folia | |
|---|---|---|---|
| `livestockai.LivestockGoals$Breed` | 0.140 | `prey.SmallPreyGoals$AvoidPredator` | 0.130 |
| `livestockai.LivestockGoals$Tempt` | 0.137 | `livestockai.LivestockGoals$FollowAdult` | 0.105 |
| `prey.SmallPreyGoals$AvoidPredator` | 0.114 | `livestockai.LivestockGoals$Tempt` | 0.072 |
| `livestockai.LivestockGoals$FollowAdult` | 0.097 | `livestockai.LivestockGoals$Breed` | 0.062 |
| `prey.PreyIdleGoals$FollowAdult` | 0.060 | `livestockai.LivestockGoals$Idle` | 0.034 |
| `livestockai.LivestockGoals$Swim` | 0.049 | `livestockai.LivestockGoals$Avoid` | 0.016 |

`LivestockGoals$Avoid` fell from 12.55 to under 0.05 ms per tick and `PreyFleeGoal` from 1.64 to under 0.05 (neither is
in the Paper top six). Folia: 200 of 200 animals alive at the end, no `NoSuchMethodError`, no "Entity threw exception".
The Folia server's first start logged CraftEngine's "Cannot allocate enough real block states" for some ore blocks; the
case ran in that start (no animal block is involved).

What is left (PAR-FAUNA-28): the case adds the heaviest staggered-check phase (1.18 to 1.36 ms, `AnimalService` age step
and the `onCheck` of livestock, products, sounds and aquatic, outside the AI packages) to every tick, so the slice p50 is
already at or above the 1.5 ms p99 budget with the goals at 0.36 to 0.59 ms; and single-tick spikes in the goals (Paper
max 38.3 ms, Folia 4.1 ms, likely pathfinding bursts or a GC pause inside a timed call).

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
