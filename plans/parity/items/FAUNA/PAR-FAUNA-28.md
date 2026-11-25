---
id: PAR-FAUNA-28
title: "200-animal load: staggered check phase and goal spikes within the tick budget (follow-up of PAR-FAUNA-26)"
area: FAUNA
status: open
wave: 55
deps: [PAR-FAUNA-26]
size: M
aliases: ["follow-up of PAR-FAUNA-26 (2026-11-25): the remaining slice is outside paper/livestockai and paper/prey"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/entities/livestock/TFCAnimal.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/animal/AnimalService.java"
  - "paper/src/main/java/dev/tfp/paper/animal/SpeciesBehaviours.java"
  - "paper/src/main/java/dev/tfp/paper/livestock/LivestockBehaviour.java"
  - "paper/src/main/java/dev/tfp/paper/products/ProductsModule.java"
  - "paper/src/main/java/dev/tfp/paper/animal/sound/AnimalSoundsModule.java"
  - "paper/src/main/java/dev/tfp/paper/aquatic/AquaticModule.java"
  - "paper/src/test/java/dev/tfp/paper/animal/**"
ui: false
in_game_steps: []
plan:
  - "Current (2026-11-25, after PAR-FAUNA-26, docs/perf/animals-200.md): husbandry.load_200 goals are 0.65 ms/tick mean on Paper 1.21.11 (p99 1.55, max 38.3) and 0.42 on Folia 1.21.11 (p99 1.14, max 4.1), but the plugin slice is 2.01 / 1.60 ms mean, p99 2.92 / 2.32, because the case adds the heaviest staggered-check phase (1.36 Paper, 1.18 Folia) to every tick: the AnimalService age step plus every SpeciesBehaviour.onCheck (livestock, products, sounds, aquatic). Check per animal: mean 41-44 us, max 857-963 us."
  - "Profile one check body (record read/write through AnimalPdc, the onCheck hooks) and cut it: read the record once per check and pass it to every behaviour, write once at the end only when something changed, skip hooks that have nothing due (lazy timestamps). Target: worst phase well under 0.5 ms for 200 animals (10 per phase)."
  - "Find the single-tick goal spikes (Paper max 38.3 ms): log the slowest timed call per tick with its goal class; if it is a pathfinding burst, spread repaths (Idle/strolls) per tick; if it is a GC pause, record it and discuss with PAR-REL-07."
  - "Do not loosen PERF-TICK-01 / PERF-COMMIT-01 or the case; do not change TFC behaviour (FAUNA-05/08/09 goldens)."
tests:
  - "itest husbandry.load_200 (exists)"
  - "paper: a test that one check reads and writes the record at most once"
done_criteria:
  - "husbandry.load_200 passes on Paper and Folia 1.21.11 (plugin slice per tick max <= 2 ms, p99 <= 1.5 ms)"
  - "No behaviour change against the PAR-FAUNA-05 / 08 / 09 goldens"
---

# PAR-FAUNA-28: 200-animal load: staggered check phase and goal spikes within the tick budget (follow-up of PAR-FAUNA-26)

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
