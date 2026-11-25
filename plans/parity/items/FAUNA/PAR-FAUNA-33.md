---
id: PAR-FAUNA-33
title: "200-animal load: spread the path starts of the livestock and prey goals (single-call spikes) (follow-up of PAR-FAUNA-28)"
area: FAUNA
status: open
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
