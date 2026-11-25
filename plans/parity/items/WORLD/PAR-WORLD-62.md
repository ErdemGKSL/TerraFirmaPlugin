---
id: PAR-WORLD-62
title: "Ore overlay default radius from the PAR-WORLD-19 census"
area: WORLD
status: done
wave: 29
deps: [PAR-WORLD-19]
size: S
aliases: ["S1 owner question (docs/spikes/S1-ore-overlay.md §4), option 1"]
tfc_refs: []
files_owned:
  - "core/src/main/java/dev/tfp/core/oreoverlay/OverlayGate.java"
  - "core/src/main/java/dev/tfp/core/config/TfpConfig.java"
  - "paper/src/main/resources/config.yml"
  - "core/src/test/java/dev/tfp/core/oreoverlay/ViewerSetTest.java"
  - "core/src/test/java/dev/tfp/core/oreoverlay/budget/OreOverlayBudgetTest.java"
  - "core/src/test/java/dev/tfp/core/config/ConfigCodecTest.java"
  - "docs/perf/ore-overlay-budget.md"
  - "docs/research/ore-entity-renderer-design.md"
  - "docs/spikes/S1-ore-overlay.md"
ui: false
plan:
  - "The PAR-WORLD-19 census (docs/spikes/S1-ore-overlay.md §2.3-§4, ee73a31) fails the 8 k overlays-per-player gate at 48 m (4-chunk radius) on 2 of 3 seeds (p95 8.9 k, 11.3 k, 4.6 k) and passes at 40 m (3-chunk radius: 7.0 k, 7.6 k, 3.1 k). Owner rule: worker decides; decided option 1."
  - "The runtime radius was `ceil(cutoff/16) + 1`, so 40 m still gave 4 chunks (docs/perf/ore-overlay-budget.md had flagged this): the census's 40 m option is the 3-chunk square. `OverlayGate.radiusChunks` is now `ceil(cutoff/16)`, the smallest radius covering the client sphere from anywhere in the viewer's chunk (the viewer set is reconciled on every chunk change, OreOverlayService), so 40 m sends 49 chunks. `max-per-player` stays 8000 (S1 suggested 6000 with option 1; left for the GPU measurement)."
  - "Done: default `ore-overlay.cutoff-blocks` 40 (TfpConfig.OreOverlay.DEFAULTS, config.yml), radius formula, view_range 0.625; tests ViewerSetTest (radius/view range pins, new tight-coverage check over cutoffs 1-160), OreOverlayBudgetTest (default 3-chunk square within the gate; S1's published 4-chunk numbers pinned through an explicit 4-chunk gate), ConfigCodecTest; ARCHITECTURE.md §9.1, design doc, perf doc, S1 doc decision; ledger divergence (40 m instead of 48 m). Pre-existing, not from this item: `OreOverlayBudgetTest.fixtureIsTheExportOfTheSpikeReports` fails because PAR-WORLD-19 rewrote spikes/ore-overlay/out while the BudgetFixture still holds the PAR-WORLD-17 export."
tests:
  - "core: radius covers the cutoff sphere from every viewer position and is tight; default 40 m -> 3 chunks, view_range 0.625"
  - "core: the census fixture at the default radius stays within the 8 k gate at p95"
done_criteria:
  - "Default cutoff 40 m with a 3-chunk send radius; docs, tests and ledger agree"
---

# PAR-WORLD-62: Ore overlay default radius from the PAR-WORLD-19 census

Follow-up of PAR-WORLD-19. Follow `plans/parity/PROTOCOL.md`.
