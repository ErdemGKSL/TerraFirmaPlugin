---
id: PAR-INFRA-48
title: "NeighbourRunnerTest flake: chainOfTenPlacementsConvergesWithOneCommitPerChangedBlock sees 14 provisional writes instead of 10"
area: INFRA
status: done
wave: 13
deps: [PAR-INFRA-10]
size: S
aliases: []
tfc_refs: []
files_owned:
  - "paper/src/test/java/dev/tfp/paper/neighbour/NeighbourRunnerTest.java"
ui: false
in_game_steps: []
plan:
  - "Found by the PAR-INFRA-45 gate run (2026-11-13, commit 7735978): a full `:core:test :paper:test :tools:datagen:test` failed once with `NeighbourRunnerTest > chainOfTenPlacementsConvergesWithOneCommitPerChangedBlock()` at NeighbourRunnerTest.java:166, `[provisional writes at once] expected: 10 but was: 14`. Two isolated reruns and a full rerun passed."
  - "Likely cause (from reading the test, not reproduced): `regions.await(0, placements)` only waits for the placement task. The worker pool can already decide and the region thread commit a few changes before the test thread reads `writes.get()`, so the count includes real commits, not only the 10 provisional writes."
  - "Fix inside the test only: read the write count inside the awaited placement task, or hold the workers / the commit queue until the provisional-write check is done. Do not change NeighbourRunner unless a loop shows a real product race; then add a follow-up for its owner."
  - "Done: the placing task itself records the write count and the provisional state of row[3] (region 0 is one thread, so no commit can interleave); the test asserts those captured values. Product code unchanged. 50 isolated reruns of the test passed, full :core:test :paper:test :tools:datagen:test green."
tests:
  - "paper: NeighbourRunnerTest passes 50 repeated runs of chainOfTenPlacementsConvergesWithOneCommitPerChangedBlock and the full `:paper:test` stays green"
done_criteria:
  - "The provisional-write assertion counts only the placing task's writes and cannot race the decide/commit pipeline"
---

# PAR-INFRA-48: NeighbourRunnerTest flake: chainOfTenPlacementsConvergesWithOneCommitPerChangedBlock sees 14 provisional writes instead of 10

All fields live in the front matter above; the loop parses them. Follow `plans/parity/PROTOCOL.md`.
