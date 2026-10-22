---
id: PAR-FAUNA-04
title: "Spawning by climate on Paper"
area: FAUNA
status: open
wave: 40
deps: [PAR-FAUNA-01, PAR-FAUNA-21]
size: M
aliases: ["M5-ENT-03"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/entities/Fauna.java"
  - "src/main/java/net/dries007/tfc/common/entities/Faunas.java"
  - "src/generated/resources/data/tfc/tfc/fauna"
files_owned:
  - "core/src/main/java/dev/tfp/core/spawn/**"
  - "core/src/test/java/dev/tfp/core/spawn/**"
  - "core/src/test/resources/dev/tfp/core/spawn/**"
  - "paper/src/main/java/dev/tfp/paper/spawn/**"
  - "paper/src/test/java/dev/tfp/paper/spawn/**"
ui: true
in_game_steps:
  - "Fresh world; tfp debug animal census 128 (new)"
  - "screenshot herds"
plan:
  - "Per chunk generation/load and a natural spawn pass: decide async (SpawnRules with chunk climate), position search on the region thread, spawn carriers via AnimalService; per-chunk cap (vanilla-like) as a documented divergence."
tests:
  - "core: SpawnRulesTest exists"
  - "paper: spawn pass budget"
done_criteria:
  - "Fresh world has climate-correct animals (debug count per species near spawn)"
---

# PAR-FAUNA-04: Spawning by climate on Paper

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
