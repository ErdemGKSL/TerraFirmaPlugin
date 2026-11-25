---
id: PAR-REL-06
title: "Husbandry run self-test and 200-animal load test"
area: REL
status: blocked
wave: 53
deps: [PAR-FAUNA-08, PAR-FAUNA-17, PAR-FAUNA-06]
size: M
aliases: ["M5 exit criteria"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/SelfTests.java"
files_owned:
  - "itest/src/main/java/dev/tfp/itest/cases/HusbandryRunCase.java"
  - "docs/perf/animals-200.md"
ui: false
in_game_steps: []
plan:
  - "Blocked (2026-11-25): done criterion not met. husbandry.load_200 measures the plugin's animal goals at 13.2 ms/tick mean (p99 32.6, max 52.9) on Paper 1.21.11 and 13.1 (p99 26.5, max 53.3) on Folia 1.21.11 for 200 animals, against PERF-TICK-01 2 ms and PERF-COMMIT-01 1.5 ms; 95 % is livestockai.LivestockGoals$Avoid (12.5 ms/tick). On Folia a baby prey also crashes in PaperPreyBody.moveTo (Pathfinder.moveTo(Entity, double) missing) and is removed. Both fixes are in paper/livestockai and paper/prey (not owned here): follow-up PAR-FAUNA-26. Unblock: rerun the self-test on Paper and Folia once PAR-FAUNA-26 is done and add the numbers to docs/perf/animals-200.md."
  - "Tame -> breed -> products -> slaughter scripted; spark profile with 200 animals."
  - "Done: itest HusbandryRunCase (husbandry.run, husbandry.load_200) in SelfTestSuite. husbandry.run passes on Paper and Folia 1.21.11: AI goals present (cow 8, deer 6), 6 daily feeds through LivestockModule.onFeed to familiarity 0.35, the check pairs the cows, birth after 59 days (1-2 calves, familiarity 0.175), milk 1000 mB and 1 wool through ProductsModule.onInteract, egg laid on the check, bull and deer slaughter drop beef/venison, large/medium raw hide and bones with no vanilla loot. husbandry.load_200 fails the budget (numbers, machine, commands: docs/perf/animals-200.md). Interactions use a stand-in Player proxy on the animal's thread (no player can join a self-test server); the chain animals are unaware so the pair stays in mating range. Follow-up PAR-FAUNA-26. No steps skipped."
tests:
  - "itest"
done_criteria:
  - "No region-thread spikes above budget"
---

# PAR-REL-06: Husbandry run self-test and 200-animal load test

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
