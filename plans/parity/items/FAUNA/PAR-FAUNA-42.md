---
id: PAR-FAUNA-42
title: "200-animal load: the rabbit garden raid and fox berry searches within the tick budget (follow-up of PAR-FAUNA-33)"
area: FAUNA
status: open
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
tests:
  - "paper: a rabbit far from any farmland finds nothing without scanning every block (count of block reads); the found farmland and the search order equal the full scan's on random gardens"
  - "itest husbandry.load_200 (exists): no RabbitRaidGoal or FoxBerryGoal call among the slowest goal calls"
done_criteria:
  - "RabbitRaidGoal and FoxBerryGoal single calls under 0.5 ms in husbandry.load_200 on Paper and Folia 1.21.11; TFC behaviour unchanged"
---

# PAR-FAUNA-42: 200-animal load: the rabbit garden raid and fox berry searches within the tick budget

Follow `plans/parity/PROTOCOL.md`.
