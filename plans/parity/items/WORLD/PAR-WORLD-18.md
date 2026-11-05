---
id: PAR-WORLD-18
title: "Ore vein weighted pick throws at a draw of 1.0: chunk refused (\"no element above 100.0\")"
area: WORLD
status: done
wave: 5
deps: []
size: S
aliases: ["found by PAR-WORLD-17 census, 2026-11-04"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/collections/Weighted.java"
  - "src/main/java/net/dries007/tfc/util/collections/IWeighted.java"
  - "src/main/java/net/dries007/tfc/world/feature/vein/VeinFeature.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/worldgen/ore/Weighted.java"
  - "core/src/main/java/dev/tfp/core/worldgen/ore/OreVeins.java"
  - "core/src/test/java/dev/tfp/core/worldgen/ore/**"
ui: false
in_game_steps: []
plan:
  - "Current: on Paper 1.21.11, TFC world seed 1, the PAR-WORLD-17 census saw chunk -62,-166 refused: `TFC worldgen refused chunk -62,-166 in world: generation failed: java.lang.IllegalStateException: no element above 100.0`. `Weighted.get(random)` computes `random * total` and finds no cumulative total strictly above it, so the draw was exactly 1.0 (the 1.21 `nextDouble` goes through a float, which can round up to 1.0). The refused chunk stays empty, a hole in the world."
  - "Check what TFC's `Weighted.get` does at the pin with the same draw (its NavigableMap `higherEntry`/`ceiling` and the float `nextDouble`) and port that exactly; if TFC can crash too, pick the last element and record a divergence."
  - "Keep the golden values of OreVeinGoldenTest unchanged."
  - "Done: Weighted.get returns the last element when no running total is above random * total (a draw of 1.0; TFC's higherEntry is null and throws at the pin, so it is a ledger divergence). A draw below 1.0 cannot reach the total (correctly rounded r * total stays below it), so only 1.0 hits it. Tests: core WeightedEdgeTest (6: draw 1.0, just below 1.0, float nextDouble of the top 53 bits rounds to 1.0, ordinary draws, all-zero weights still throw, seed 1 chunk -62,-166 decides every position and rock, which threw `no element above 100.0` before). OreVeinGoldenTest unchanged and green. The census rerun for seeds 1-3 (done_criteria) belongs to PAR-WORLD-19's server run."
tests:
  - "core: Weighted.get(1.0) and a draw that rounds to 1.0 through the float nextDouble; a regression for the seed-1 chunk -62,-166 decision"
done_criteria:
  - "No refused chunk in the PAR-WORLD-17 census (`empty_chunks` empty) for seeds 1, 2, 3"
---

# PAR-WORLD-18: Ore vein weighted pick throws at a draw of 1.0

Follow-up of PAR-WORLD-17 (its census reports refused chunks in `empty_chunks` and treats them as solid). Paths in
`tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow
`plans/parity/PROTOCOL.md`.
