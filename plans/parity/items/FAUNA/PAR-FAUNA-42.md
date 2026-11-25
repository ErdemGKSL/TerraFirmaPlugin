---
id: PAR-FAUNA-42
title: "200-animal load: the rabbit garden raid and fox berry searches within the tick budget (follow-up of PAR-FAUNA-33)"
area: FAUNA
status: done
wave: 57
deps: [PAR-FAUNA-24]
size: S
aliases: ["follow-up of PAR-FAUNA-33 (2026-11-25): the raid search goals are in paper/wildraid, outside its files_owned"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/entities/prey/TFCRabbit.java"
  - "src/main/java/net/dries007/tfc/common/entities/prey/TFCFox.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/wildraid/**"
  - "paper/src/test/java/dev/tfp/paper/wildraid/**"
ui: false
in_game_steps: []
plan:
  - "Found in PAR-FAUNA-33 (2026-11-25, husbandry.load_200 on Paper 1.21.11): `wildraid.RabbitRaidGoal#shouldActivate` is the steadiest single-call spike of the herd's goals: 2.9 to 3.2 ms per call (the five slowest calls of a run were all this goal, no GC), 0.12 to 0.15 ms per tick on average. Each search walks TFC's `MoveToBlockGoal.findNearestBlock` box (range 16, vertical 1: 33 x 33 x 3 blocks) through RaidFinder with a `RaidSites.farmland` lookup per block, once per rabbit every 200 to 399 ticks."
  - "Keep TFC's search order, range, delays and result. Make the search cheap: skip chunk sections with no farmland (a per-section or per-chunk farmland presence check, e.g. the chunk's palette / a cached flag refreshed on farmland placement), test the raidable crop only where farmland is, cache the chunk lookups per search; or spread one search over a few ticks (the goal answers false until it is complete). Same for FoxBerryGoal's bush search."
  - "Done (2026-11-25): wildraid/RaidScan runs findNearestBlock in TFC's order in time slices (WildRaidModule.SEARCH_SLICE_NANOS 0.3 ms, clock read every 16 cells), one per ask, from the block where the search started; the goal answers false until it is done. Exact savings: a sated rabbit skips the search (isValidTarget needs wantsToRaid), one farmland lookup per cell, chunk usability once per chunk and slice. Search order, range, delays and result unchanged (RaidScanTest: 200 random gardens equal the full scan). Tests: RaidScanTest 4, WildRaidGoalsTest 8 unchanged. itest husbandry.load_200 (Paper and Folia 1.21.11, 2026-11-25): no RabbitRaidGoal or FoxBerryGoal call among the five slowest goal calls (Paper: the fifth slowest is 0.89 ms, Folia 2.99 ms, all livestock goals); RabbitRaidGoal 0.066 / 0.079 ms per tick on average. A single call is bounded by the 0.3 ms slice plus one cell; the case itself still fails on the livestock goals (PAR-FAUNA-33). Ledger divergence row added. Also fixed WildRaidModule's ported-file header (one path per @ line)."
tests:
  - "paper: a rabbit far from any farmland finds nothing without scanning every block (count of block reads); the found farmland and the search order equal the full scan's on random gardens"
  - "itest husbandry.load_200 (exists): no RabbitRaidGoal or FoxBerryGoal call among the slowest goal calls"
done_criteria:
  - "RabbitRaidGoal and FoxBerryGoal single calls under 0.5 ms in husbandry.load_200 on Paper and Folia 1.21.11; TFC behaviour unchanged"
---

# PAR-FAUNA-42: 200-animal load: the rabbit garden raid and fox berry searches within the tick budget

Follow `plans/parity/PROTOCOL.md`.
