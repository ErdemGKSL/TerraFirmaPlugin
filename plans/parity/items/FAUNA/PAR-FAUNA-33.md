---
id: PAR-FAUNA-33
title: "200-animal load: spread the path starts of the livestock and prey goals (single-call spikes) (follow-up of PAR-FAUNA-28)"
area: FAUNA
status: blocked
wave: 56
deps: [PAR-FAUNA-08, PAR-FAUNA-09]
size: M
aliases: ["follow-up of PAR-FAUNA-28 (2026-11-25): the goal spikes are in paper/livestockai and paper/prey, outside its files_owned"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/entities/ai/livestock/LivestockAi.java"
  - "src/main/java/net/dries007/tfc/common/entities/ai/prey/PreyAi.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/livestockai/**"
  - "paper/src/test/java/dev/tfp/paper/livestockai/**"
  - "paper/src/main/java/dev/tfp/paper/prey/**"
  - "paper/src/test/java/dev/tfp/paper/prey/**"
  - "itest/src/main/java/dev/tfp/itest/cases/HusbandryRunCase.java"
ui: false
in_game_steps: []
plan:
  - "Still blocked after the revisit (2026-11-25, second pass): the path budget got a time side (prey/PathBudget charges the measured time of every Pathfinder.moveTo from PaperPreyBody / PaperLivestockBody to the tick; after 0.3 ms of search deferrable starts and refreshes wait for the next tick; targets, speeds, ranges unchanged; FAUNA-08/09 goldens untouched; PathBudgetMockBukkitTest 5 (+2), LivestockGoalsMockBukkitTest 11 and PreyGoalsMockBukkitTest 14 pass). husbandry.load_200, Paper 1.21.11 (HEAD 071e8c7 + this change, shared 4 vCPU container): goals mean 0.597 / p99 2.717 / max 6.098 ms (same-session baseline without the time side: 0.747 / 3.402 / 4.309), slice mean 1.787 / p99 3.906 / max 7.287, worst check phase 1.189, path starts 655 / deferred 55, no GC in the window. Folia 1.21.11 (first, priming run of the run dir): goals 0.769 / 3.592 / 6.181, slice 1.922 / 4.745 / 7.334, worst check phase 1.153, deferred 217; one horse suffocated (spawned at y 89 into a block, a case issue). Remaining causes: (1) single calls of 3 to 6 ms that no start budget can bound: one vanilla path search (LivestockGoals$Idle#shouldActivate 4.6 ms on Folia), the shared section scan paid by whichever goal refreshes it (LivestockGoals$Avoid#shouldActivate 5.8, SmallPreyGoals$AvoidPredator 4.1, no GC), host preemption on the shared container; (2) wildraid.RabbitRaidGoal#shouldActivate 2.9 to 3.2 ms per search (its 33x33x3 block scan), outside files_owned: follow-up PAR-FAUNA-42; (3) the worst check phase 1.15 to 1.19 ms (PAR-FAUNA-28 files) alone leaves the slice p50 at 1.65 to 1.71 ms, above PERF-COMMIT-01 1.5 whatever the goals do. Meeting a 1 ms goal max needs the path search and the section scans off the region tick (or time-sliced) and a quiet host; budgets and the case unchanged."
  - "Blocked (2026-11-25): done criterion not met. husbandry.load_200 with a living herd (the case now runs the calendar and spawns on land): goals max per tick 3.66 ms on Paper 1.21.11 and 3.46 on Folia 1.21.11 (criterion < 1 ms; mean 0.70 / 0.56, p99 1.99 / 1.52); plugin slice mean 2.01 / 2.15, p99 3.30 / 3.11, max 4.97 / 5.05 vs PERF-COMMIT-01 1.5 / PERF-TICK-01 2. Remaining cause: (1) single goal calls of 2 to 4 ms: one vanilla path search inside Pathfinder.moveTo (idle stroll, follow re-path; the count budget caps them at 3 per tick but cannot bound one search) and trivial calls (Look/Avoid shouldActivate) of 2 to 3 ms with no GC in the row (host preemption or safepoints on the shared 4 vCPU container; one run had a 52 ms young GC inside a call); needs the path search off the tick or time-sliced, and a quiet host for the measurement. (2) The worst check phase 1.0 to 1.6 ms (AnimalService age step and hooks, PAR-FAUNA-28 files, one-shot worst-of-three sample) alone exceeds what PERF-COMMIT-01 leaves for the goals. Numbers: docs/perf/animals-200.md, After PAR-FAUNA-33."
  - "Done so far: diagnostic committed in HusbandryRunCase.Load (5 slowest calls with goal class, method, row and GC in that row; GC ms/count of the window, not asserted; path starts/deferrals; goal starts per class; animals in water and running goals at the end; removal causes). It found the earlier runs measured a frozen herd (calendar arrested with no player: idle waits never ended, readings and scans never refreshed, 0 path starts; the Paper area was ocean, 183 of 200 in water). The case now forces the calendar to run (setRunWhenEmpty, restored), picks a dry 3x3 area and dry spawn columns, force-loads 7x7. Code: prey/PathBudget (3 deferrable starts per region thread and tick; urgent starts never refused), prey/Ticks (sensing, scans, sightings and budget on server ticks), NearbyScan shared 10 ticks across ranges up to 16 with cached boxes and per-filter section lists, Avoid/PreyFlee skip the reading when nothing avoidable is in range, AvoidPredator on the predator-filtered scan, Tempt over the world player list, Breed sex memo, adult memo (SpeciesCache.knownGrown). Test PathBudgetMockBukkitTest (3); LivestockGoalsMockBukkitTest (11) and PreyGoalsMockBukkitTest (14) pass; core goldens untouched. Ledger: 3 divergence rows."
  - "Current (2026-11-25, after PAR-FAUNA-28, docs/perf/animals-200.md): husbandry.load_200 slice mean 1.41 / 1.45 ms (Paper / Folia 1.21.11), p99 2.00 / 1.88, max 9.68 / 4.70; goals mean 0.51 / 0.54, p99 1.11 / 0.97, max 8.78 / 3.79; worst check phase 0.90 / 0.91."
  - "The max is a single goal call: a local diagnostic (slowest timed call per tick with its goal class) found LivestockGoals$Breed#shouldActivate 13.2 ms, LivestockGoals$FollowAdult 4.4, Panic 2.3, Tempt 2.1, PreyIdleGoals$FollowAdult 2.3, SmallPreyGoals$AvoidPredator 1.2, LivestockGoals$Avoid 2.3, with no GC in 16 of the 18 spike ticks. The goals that start a path call Pathfinder.moveTo inside shouldActivate, which computes the vanilla path in that call."
  - "Spread the path starts: at most N path computations per region tick for our goals (a per-region token bucket or a stagger by entity id), a goal whose start is deferred answers false and retries on its next period, as vanilla's goal selector already retries; keep TFC's targets, speeds and ranges (FAUNA-08 / FAUNA-09 goldens). Cap the per-call scan of AvoidPredator / Avoid the same way if they still show up."
  - "Commit the diagnostic into HusbandryRunCase.Load: report the slowest timed call of the window with its goal class and method, and the GC time during the window (reported, not asserted). Do not change the budgets or the slice definition."
  - "The p99 needs more than the spikes: goals p99 about 1.0 ms plus the worst check phase 0.9 ms (a one-shot worst-of-three sample per animal, 39 us mean, single outliers of 0.7 ms from host pauses) is above 1.5 ms. Lower the goals' steady cost too (Tempt, Breed, AvoidPredator, FollowAdult are 0.08 to 0.12 ms/tick each)."
  - "Then re-run husbandry.load_200 on Paper and Folia 1.21.11; if it passes, PAR-FAUNA-28 and PAR-REL-06 can be closed with the numbers."
tests:
  - "paper: a herd of 50 starting paths in one tick computes at most N paths per tick and every goal eventually starts"
  - "itest husbandry.load_200 (exists)"
done_criteria:
  - "Goal max per tick in husbandry.load_200 under 1 ms on Paper and Folia 1.21.11 with the PAR-FAUNA-08 / 09 goldens unchanged"
---

# PAR-FAUNA-33: 200-animal load: spread the path starts of the livestock and prey goals (single-call spikes) (follow-up of PAR-FAUNA-28)

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
