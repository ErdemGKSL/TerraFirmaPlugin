# 200-animal load test (PAR-REL-06)

The self-test case `husbandry.load_200` (`itest/src/main/java/dev/tfp/itest/cases/HusbandryRunCase.java`, nested class
`Load`) spawns 200 TFC animals and measures the plugin's share of the region thread for 60 s. Budgets from
`docs/perf/budgets.md`: PERF-TICK-01 (plugin share 2 ms per tick, hard cap per tick) and PERF-COMMIT-01 (1.5 ms per tick,
checked on the 99th percentile).

**Update (2026-11-25, PAR-FAUNA-28): worst check phase 1.36 -> 0.90 ms (Paper) and 1.18 -> 0.91 (Folia), slice mean
2.01 -> 1.41 and 1.60 -> 1.45, p99 2.92 -> 2.00 and 2.32 -> 1.88; still over budget** (p99 above 1.5 ms, max 9.7 / 4.7 ms)
because of single-call goal spikes in the AI packages (pathfinding inside `shouldActivate`, up to 13 ms in one call), not
GC. Numbers in "After PAR-FAUNA-28" below; the goal spikes are tracked in PAR-FAUNA-33.

**Update (2026-11-25, PAR-FAUNA-33): the case now runs the calendar and puts the herd on land; before, the calendar
was frozen (no player online) and the Paper area was ocean, so the earlier numbers measured a herd that did not move.
With a living herd: goals mean 0.70 / 0.56 ms (Paper / Folia), p99 1.99 / 1.52, max 3.66 / 3.46; slice mean 2.01 /
2.15, p99 3.30 / 3.11, max 4.97 / 5.05; still over budget. Numbers and causes in "After PAR-FAUNA-33".**

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

## After PAR-FAUNA-28

What changed in the check (`AnimalService`, `SpeciesBehaviours`, the livestock, products and sounds hooks):

- One record per check: the service reads the record once, every hook gets it (`SpeciesBehaviours.current`), a hook that
  changes the carrier's own record calls `SpeciesBehaviours.update`, and the service writes once after the last hook,
  only when the age step or a hook changed it (test `AnimalCheckOnceTest`: two changing hooks give one read and one
  write; no change gives no write). Before, the age step, the mating, the birth and the egg each wrote the PDC, and a hen
  mated and laying in the same check had her fertilization overwritten by the laying hook's stale record.
- Hooks with nothing due return before any lookup: a male in the livestock hook; milk animals in the products hook
  (milk is taken on interaction); a hen below the laying familiarity before her product state is read; a wool animal only
  re-applies its model when it was shorn, the wool is back and the model on the carrier is not the one it should wear
  (before: a model refresh, with attribute and model writes, on every check of every sheep shorn once).
- The sounds hook takes the species from the check (no second record read) and caches the TFC event name per species
  (no regex per ambient sound); mating skips neighbours of another carrier type before decoding their record.

Same setup and commands as above, plugin at d6ac357 plus PAR-FAUNA-28, 2026-11-25. Times in ms per tick.

| Platform | Slice mean | p50 | p99 | max | Goals mean | Goals p99 | Goals max | Worst check phase | Check per animal mean / max (µs) | Ticks > 1.5 ms | Region tick interval p99 / max | Server MSPT mean / p99 / max |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Paper 1.21.11-132 | 1.411 | 1.370 | 2.002 | 9.678 | 0.514 | 1.105 | 8.781 | 0.897 | 39.4 / 702.9 | 289 of 1199 | 50.268 / 52.971 | 4.257 / 7.552 / 31.786 |
| Folia 1.21.11-14 | 1.445 | 1.410 | 1.884 | 4.701 | 0.536 | 0.974 | 3.792 | 0.910 | 39.4 / 771.6 | 267 of 1199 | 50.100 / 65.171 | n/a |

Before (same machine, same day, PAR-FAUNA-26 table above): Paper slice 2.013 / p99 2.917 / max 39.665, worst phase
1.364; Folia 1.602 / 2.323 / 5.245, worst phase 1.182. A re-run of the Paper baseline at d6ac357 with a local diagnostic
patch (below) gave slice mean 3.401, p99 4.024, max 6.804, worst phase 2.759, check per animal 110 µs: the check phase is
a one-shot sample (worst of three runs per animal), so one pause inside one animal's check moves it by milliseconds.

Verdict: **still over budget on Paper and Folia** (p99 2.00 / 1.88 > 1.5 ms; max 9.7 / 4.7 > 2 ms). The p99 is goals p99
(about 1.0 to 1.1 ms) plus the worst check phase (0.9 ms); the max is a goal spike.

Where the goal spikes come from (local diagnostic patch on `HusbandryRunCase`, not committed: the slowest timed goal call
per tick with its goal class and method, and the JVM's total GC time at each sample; and the check body timed per hook).
Ticks with goals above 1.5 ms, three runs (Paper before, Paper after, Folia after):

| Run | Slowest single call in the tick (ms) | GC time changed in that tick? |
|---|---|---|
| Paper | `PreyIdleGoals$FollowAdult#shouldActivate` 2.25, `LivestockGoals$Breed#shouldActivate` 1.18, `LivestockGoals$FollowAdult#shouldActivate` 0.92, `SmallPreyGoals$AvoidPredator#shouldActivate` 0.82 | in one of the five ticks (the `Breed` one, +75 ms of GC since the previous logged tick), not in the others |
| Paper | `LivestockGoals$FollowAdult#shouldActivate` 4.44, `LivestockGoals$Panic#shouldActivate` 2.29, `LivestockGoals$Tempt#shouldActivate` 2.10 / 1.05, `SmallPreyGoals$AvoidPredator#shouldActivate` 1.19 | no |
| Folia | `LivestockGoals$Breed#shouldActivate` 13.19, `LivestockGoals$Avoid#shouldActivate` 2.29, `LivestockGoals$Tempt#shouldActivate` 1.40, `LivestockGoals$FollowAdult#shouldActivate` 1.24, `LivestockGoals$Swim#shouldStayActive` 1.08 | in one of the eight ticks (the `Avoid` one, +43 ms), not in the others |

So the spikes are single `shouldActivate` calls of the goals that start a path (`Breed`, `FollowAdult`, `Tempt`, `Panic`
call `moveTo`, which computes the vanilla path in the same call) or scan (`AvoidPredator`, `Avoid`), mostly not GC pauses
(the GC total moved in two of the 18 logged ticks; GC pauses are recorded here only, for PAR-REL-07). Spreading the path starts over ticks, as the plan asked, is code in
`paper/livestockai` and `paper/prey` (PAR-FAUNA-26's files, outside PAR-FAUNA-28's): tracked in PAR-FAUNA-33. The same
patch put the check body at about 7 µs for the record read and 1 to 15 µs per hook (cold caches; the warm cost is lower),
with single outliers of 0.4 to 38 ms in one call of a hook that does nothing but compare two fields: host pauses, which
the worst-of-three sample adds to the phase.

## After PAR-FAUNA-33

**Verdict (2026-11-25): still over budget on Paper and Folia, and the earlier numbers were not measuring a living herd.**

What the committed diagnostic (`HusbandryRunCase.Load`: the five slowest timed calls of the window with goal class,
method, sampler row and whether the JVM's GC total moved in that row; GC time and count of the window; path starts and
deferrals; goal starts per class; at the end the animals in water and the running goals) showed on the first run:

- The self-test server has no player, so TFC's time stop arrests the calendar: the player ticks stood still for the whole
  window. Every goal timed on them held its first answer: idle waits never ended, so no idle stroll and no path started
  (0 path starts in the window), and the sensor readings, the shared section scans and the sightings, stamped with the same
  frozen tick, were never refreshed. The calendar also advances in 20-tick steps when it runs, so it is no per-tick stamp.
- On the Paper world the old area (40 chunks west and south of spawn) was ocean: 183 of 200 animals were in water at the
  end, and only `Swim` goals ever started.

So the PAR-FAUNA-26 / PAR-FAUNA-28 numbers above measured a frozen herd. The case now (same slice, same budgets, same
60 s window):

- forces the calendar to run as with a player online (`CalendarService.setRunWhenEmpty`, restored after), so the goals
  live;
- takes the first 3x3 area at least 40 chunks from spawn whose surface is mostly dry (9 sampled columns, at most one
  liquid) and retries up to 20 columns per animal for a dry spawn; force-loads 7x7 chunks around it so strollers stay
  loaded;
- records why a herd animal was removed (`EntityRemoveEvent` cause, position, damage cause) in the failure.

Changes in `paper/livestockai` and `paper/prey` (TFC targets, speeds and ranges unchanged; FAUNA-08 / FAUNA-09 goldens
and the MockBukkit goal tests pass):

- `prey/PathBudget`: at most 3 deferrable path starts per region thread and server tick (idle stroll and walk to the look
  target, baby follows adult, tempt, breed walk). A refused first start answers false (idle, retried next period) or
  retries on the next tick (followers); a refused refresh of an animal already walking is skipped and may use all but the
  last token, so refreshes cannot starve first starts. Panic, flight, avoid-predator and ram starts are never refused but
  use the tokens. Test `PathBudgetMockBukkitTest` (3): a herd of 50 calves following in one tick computes at most 3 paths
  per tick and every calf starts (within 34 ticks); 50 idle goals all start with at most 3 paths per tick; urgent starts.
- `prey/Ticks`: the sensor period, the section scans, the sightings and the path budget count server ticks
  (`Bukkit.getCurrentTick`), as vanilla's sensors do; TFC's memories (flight, panic, cooldowns, breeding) stay on the
  calendar.
- `prey/NearbyScan`: one scan per chunk section shared for up to 10 ticks and by every range up to 16 (the rabbit's 8-block
  predator scan shares the 16-block one), entity boxes read once per scan, a per-filter cache of the section (land
  predators; anything an avoidance could pick).
- `LivestockGoals$Avoid` and `PreyFleeGoal` ask the filtered scan first and skip the reading when nothing a player,
  predator or rammer is in range; `SmallPreyGoals$AvoidPredator` filters the shared scan by land predator once per section.
- `Tempt` reads the world's player list (no entity-section query when no player is near); `Breed` caches the sex; the
  follow-adult goals remember adults (`SpeciesCache.knownGrown`: an age only grows), so adults are read once.

Paper 1.21.11-132 and Folia 1.21.11-14, plugin at 1048e3a plus PAR-FAUNA-33, 2026-11-25, same machine. Times in ms per
tick.

| Platform | Slice mean | p50 | p99 | max | Goals mean | Goals p50 | Goals p99 | Goals max | Worst check phase | Ticks > 1.5 ms | Path starts / deferred | GC in window | Server MSPT mean / p99 / max |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Paper | 2.011 | 1.919 | 3.304 | 4.974 | 0.696 | 0.604 | 1.988 | 3.659 | 1.315 | 1199 of 1199 | 726 / 8 | 0 ms | 3.670 / 7.368 / 32.444 |
| Folia | 2.149 | 2.099 | 3.112 | 5.046 | 0.561 | 0.511 | 1.524 | 3.458 | 1.588 | 1199 of 1199 | 521 / 3 | 0 ms | n/a |

Goal time by class (ms per tick, the six largest): Paper `LivestockGoals$Avoid` 0.159, `$FollowAdult` 0.147, `$Idle`
0.094, `$Look` 0.077, `SmallPreyGoals$AvoidPredator` 0.068, `PreyFleeGoal` 0.058; Folia `$Avoid` 0.156, `$Idle` 0.104,
`$Look` 0.060, `$FollowAdult` 0.058, `AvoidPredator` 0.054, `PreyFleeGoal` 0.051. Slowest single calls: Paper
`PreyIdleGoals$FollowAdult#shouldActivate` 3.11, `LivestockGoals$Look#shouldActivate` 2.94, `$FollowAdult#tick` 2.62,
`PreyFleeGoal#shouldActivate` 2.14, `$Avoid#shouldActivate` 2.04; Folia `LivestockGoals$Idle#shouldActivate` 2.88, 2.81,
2.29, 2.24 (stroll path starts), `$Look#shouldActivate` 2.18; no GC in those rows. 200 of 200 animals alive on both.

Other runs of the same code on Paper (the spread on this shared machine): goals mean 0.59 to 0.75, p99 1.74 to 2.61, max
3.1 to 6.9 (one run 53.1 with a 52 ms young GC inside an `Avoid` call); worst check phase 1.05 to 1.48. Two runs lost one
animal each during the window with no entity exception logged (the cause is now recorded by the case). The first run of
this item, with the calendar still frozen and the herd in the ocean, read goals mean 0.25, p99 0.47: not comparable.

Where the budget is lost now:

- The worst check phase (1.0 to 1.6 ms, the `AnimalService` age step and the behaviour hooks, PAR-FAUNA-28's files; a
  one-shot worst-of-three sample per animal) alone takes most of PERF-COMMIT-01's 1.5 ms, so the slice p50 is already at
  or above the p99 budget whatever the goals do.
- Goals p99 1.5 to 2.6 ms and max 3 to 7 ms come from single calls of 2 to 4 ms: vanilla path computations inside
  `Pathfinder.moveTo` (idle strolls, follow re-paths; at most 3 per tick now, but one path alone can take 2 to 4 ms) and
  calls with a trivial body (`Look#shouldActivate`, `Avoid#shouldActivate` with nothing in range) that took 2 to 3 ms with
  no GC in the row: host preemption on the shared 4 vCPU container or safepoints. A count budget cannot bound one path's
  cost; meeting a 1 ms goal max needs the path search off the tick (or a time-sliced search) and a quieter host.

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
