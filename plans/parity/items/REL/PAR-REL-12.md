---
id: PAR-REL-12
title: "Visual and overlay budget guards: profile-vs-pool unit test, overlay entities-per-chunk itest budget"
area: REL
status: open
wave: 8
deps: [PAR-INFRA-15, PAR-INFRA-16]
size: S
aliases: ["owner decision 2026-10-22: regression/perf guard for the ore overlay", "X-VIS-01", "X-PERF-01 (overlay part)"]
tfc_refs:
  - "src/main/resources/data/tfc/worldgen/configured_feature/vein"
  - "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java"
files_owned:
  - "tools/datagen/src/test/java/dev/tfp/datagen/ProfilePoolGuardTest.java"
  - "core/src/test/java/dev/tfp/core/oreoverlay/budget/**"
  - "core/src/test/resources/dev/tfp/core/oreoverlay/budget/**"
  - "itest/src/main/java/dev/tfp/itest/cases/OreOverlayBudgetCase.java"
  - "docs/perf/ore-overlay-budget.md"
ui: false
in_game_steps: []
plan:
  - "Current: VisualBudgetTest (X-VIS-01) checks only the default profiles against a budget table printed from the index; nothing fails when a non-default profile overflows a pool or when a family generator adds a full-cube ore look again; the overlay runtime (PAR-INFRA-16) has no automated budget."
  - "ProfilePoolGuardTest (datagen, real checkout): for every shipped allocation (level-0, level-1, level-2 x 1.21/26 x Nether/End on/off) fail if any pool's used count exceeds its capacity, if the solid pool leaves fewer than `Profile.SOLID_RESERVE_WARNING` slots, if any `tfp:ore/**` appearance owns a visual unit of its own (the always-on ore rule regressed), if an allocation folder exists for a profile that is not in `Profile.values()` (no level-3a/3b leftovers), or if two different models share one vanilla state. The message names the allocation, the pool and the culprit block ids."
  - "Overlay count budget (core, deterministic): a fixture of chunk snapshots (exported once by PAR-WORLD-17's census from 3 seeds, stored as compact resources under core/src/test/resources/dev/tfp/core/oreoverlay/budget/) run through `ExposedFaces.compute`; assert p95 faces per chunk and the total within the 4-chunk radius of the 20 sample positions stay within the S1 gate (default 8 k per player) and that the table build stays under the documented CPU budget with a generous margin (timed JUnit, warm-up, median of 20 runs; a JMH source set is PAR-REL-02's)."
  - "itest `ore.overlay.budget`: pregenerate the S1 area on Paper 1.21.11, connect the self-test's fake viewer (or count the packets the display sender would send for a viewer at the 20 positions), record entities per player, packets and bytes on join, and table-build ms p95; fail above the gate; write the numbers to the report."
  - "docs/perf/ore-overlay-budget.md: the gate values, how to rerun, the latest numbers; PAR-REL-02 links it into docs/perf/budgets.md when it lands (wave 52)."
tests:
  - "ProfilePoolGuardTest (datagen), OreOverlayBudgetTest (core), itest `ore.overlay.budget` on Paper 1.21.11 (run delegated per PROTOCOL 5.3)"
done_criteria:
  - "ProfilePoolGuardTest is part of `./gradlew check` and fails on a seeded overflow and on a seeded full-cube ore appearance (shown by a temporary local change, not committed)"
  - "OreOverlayBudgetTest and `ore.overlay.budget` green with the S1 gate; docs/perf/ore-overlay-budget.md has the numbers"
---

# PAR-REL-12: Visual and overlay budget guards: profile-vs-pool unit test, overlay entities-per-chunk itest budget

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.

Wave note: added after the owner decision of 2026-10-22 (ores as exposed-face overlays). Wave 8 already holds four items;
this is a fifth there on purpose (files disjoint from PAR-FLUID-01, PAR-SURV-05, PAR-SURV-06 and PAR-SURV-07). PAR-DECO-15
(final budget re-check) depends on it.
