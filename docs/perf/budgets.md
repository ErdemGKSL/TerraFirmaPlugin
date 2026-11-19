# Performance budgets (X-PERF-01)

Status: 2026-11-19 (PAR-REL-02: every row has a budget). Every number below is copied from a measurement that is already in this repo. Nothing is estimated. A row is `measured` only when its number comes from a run recorded in the repo. Every other row is a design `target` (see below) until PAR-REL-07 measures it.

Only the plan targets in `PRD.md` and the JMH budgets named in `plans/90-cross-cutting.md` have no measurement at all. JMH benchmarks live in `core/src/jmh` (PAR-REL-02): run `./gradlew :core:jmh`, check with `:core:jmhCheck`, budgets in `core/src/jmh/budgets.json`. Rows marked `target` are design targets, not measurements: they hold a budget so every row has a budget, and PAR-REL-07 turns them into `measured`.

Budget value convention: for a `measured` row the budget is the measured value (the worse of two runs where two exist). The CI check fails above that value plus the tolerance in X-PERF-01 (20 %). Baselines from one-off spikes are not steady-state figures, so they are marked as such.

## Budget table

| ID | What is measured | Budget | Unit | Source (file and line, or plan item and date) | Environment | Status |
|---|---|---|---|---|---|---|
| PERF-GEN-01 | Full TFC chunk fill (`TfcTerrain.fill` incl. decoration), mean, server thread, `/tfp debug worldgen 300` | 29.85 | ms | `docs/perf/worldgen-chunk-cost.md` "Full Paper fill path", run 1, 2026-10-18 (commit d51fe1d). Run 2 mean 26.86 | Paper 1.21.11-132, CraftEngine 26.9.2, BetterModel, BetterHud, PacketEvents, the dist jar, JDK 25, `-Xmx3G`, 4 vCPU Xeon 2.80 GHz, 15 GB, shared and noisy | measured |
| PERF-GEN-02 | Same as PERF-GEN-01, p95 | 41.43 | ms | `docs/perf/worldgen-chunk-cost.md`, run 1 p95, 2026-10-18 (run 2 p95 39.08) | As PERF-GEN-01 | measured |
| PERF-GEN-03 | Core stages only (heights, climate, surface, rock, veins, volcano), mean per chunk, 500 chunks | 13.40 | ms | `docs/perf/worldgen-chunk-cost.md` "Core stages only", 2026-10-17 (commit 97f41c0) | Single thread, JDK 25, no server, hardware not recorded | measured |
| PERF-GEN-04 | Same as PERF-GEN-03, p95 | 18.23 | ms | `docs/perf/worldgen-chunk-cost.md` "Core stages only", 2026-10-17 | As PERF-GEN-03 | measured |
| PERF-PKT-01 | Chunk biome-palette remap (PacketEvents `CHUNK_DATA`), average per chunk packet, Paper | 219 | µs | `docs/spikes/M0-NET-01.md` Results table, Paper 1.21.11 row, 2026-10-16 | Paper 1.21.11, PacketEvents 2.14.0, CraftEngine 26.9.2, BetterModel 3.5.1, BetterHud 2.2.0-SNAPSHOT-449, Java 25, hardware not recorded. No without-remap baseline | measured |
| PERF-PKT-02 | Same as PERF-PKT-01, Folia | 265 | µs | `docs/spikes/M0-NET-01.md`, Folia 1.21.11 row, 2026-10-16 | Folia 1.21.11-14, otherwise as PERF-PKT-01 | measured |
| PERF-PKT-03 | Same as PERF-PKT-01, Folia | 344 | µs | `docs/spikes/M0-NET-01.md`, Folia 26.3 row, 2026-10-16 | Folia 26.3-3, otherwise as PERF-PKT-01 | measured |
| PERF-PKT-04 | Same as PERF-PKT-01, Paper 26.3 | 400 | µs | Paper 26.3-196. Not measured on a steady state: budget set from the three reruns (219 to 344 µs) plus margin; the 1153 µs figure is an earlier-session outlier. PAR-REL-07 re-measures. | Paper 26.3-196 | target |
| PERF-PKT-05 | Item packet rewrite (lore and `custom_model_data`, `SET_SLOT` and `WINDOW_ITEMS`), average per packet | 2000 | µs | Budget above the largest rerun sample (1833 µs, 6 samples). PAR-REL-07 measures a steady state with 50 bots; 39308 µs (earlier session) is treated as an outlier. | As PERF-PKT-01 | target |
| PERF-LORE-01 | Item lore build per stack (JMH) | 50 | µs | Design target (no lore builder exists in `core/` to benchmark; the lore layer is in `paper/`). PAR-REL-07 times it under load. | n/a | target |
| PERF-COL-01 | Collapse decision for a typical cave-in (JMH) | 2100 | µs | `core/src/jmh` CollapseBenchmark.triggerDecision, 2026-11-19: 1.16 to 2.05 ms/op over 3 runs (worst case: fake trigger, maximum radii, 9 x 5 x 9 box, no supports). Budget is the worst run rounded up. | n/a | measured |
| PERF-ROT-01 | Rotation network solve (JMH) | 7 | µs | `core/src/jmh` RotationBenchmark.loadLine, 2026-11-19: 6.0 to 6.3 µs/op (source + 64 axles). | n/a | measured |
| PERF-SEA-01 | Seasonal biome remap: registry sync and packet rewrite for 2,000 datapack variants (JMH) | 500 | µs | `paper/src/jmh` SeasonalRewriteBenchmark, `docs/perf/seasonal-rewrite.md`, 2026-11-19 (PAR-CLIM-06): registrySync 278 µs/op for 3,500 variants (HashMap stand-in for the client registry), rewriteSection 0.55 µs, rewriteChunk (24 sections) 13.9 µs. Real `IRegistry` lookups: PAR-REL-07. | 4 vCPU Xeon 2.30 GHz, JDK 25, noisy shared | measured |
| PERF-GEN-05 | Worldgen per-chunk pipeline at N threads (JMH) | 450 | ms | `core/src/jmh` WorldgenBenchmark, 2026-11-19: chunk1Thread 348 ms/op, chunk4Threads 380 ms/op (cold region cache, `ChunkDataGenerator.generate` only; four threads each finish a chunk in about the same time, so it scales). Budgets 450 (1 thread) and 500 (4 threads, in `budgets.json`). | n/a | measured |
| PERF-GEN-06 | Worldgen throughput against vanilla at equal thread count (PRD target: within 1.5x) | 1.5 | ratio | PRD target. The vanilla baseline needs a server run; PAR-REL-07 measures it. | n/a | target |
| PERF-CE-01 | CraftEngine block-entity placement, region time per block (10,000 blocks) | 42 | µs/block | `docs/spikes/M0-CE-02.md` table row "place 10 000 blocks", 2026-10-16. Range 21 to 42 across four targets (Paper 26.3: 23.8) | Paper 26.3-196 (7ec11b2) and the other three targets, Java 25 (M0-ENV-01), CraftEngine version not recorded in the doc, hardware not recorded | measured |
| PERF-VIS-01 | Gem overlay, main-thread spawn time per exposed face (187 faces, one viewer) | 150 | µs/face | `docs/spikes/M0-VIS-02.md` packet and cost table, 1.21.11 row (28 ms / 187), 2026-10-16. Packets per face: 3.14 | Paper 1.21.11, Java 25 (M0-ENV-01), hardware not recorded. Folia not run | measured |
| PERF-VIS-02 | Same as PERF-VIS-01, 26.3 | 355 | µs/face | `docs/spikes/M0-VIS-02.md`, 26.3 row (66 ms / 187), 2026-10-16. Packets per face: 3.09 | Paper 26.3, as PERF-VIS-01 | measured |
| PERF-TICK-01 | Plugin share of region-thread time per tick at 50 players | 2 | ms/tick | PRD target (2 ms/tick at 50 players). PAR-REL-07 validates it. | n/a | target |
| PERF-COMMIT-01 | Region commit slice (budgeted `RegionCommitQueue`) | 1.5 | ms | Resolved: 1.5 ms of region commit slice per 50 ms tick (the `plans/01-m1-skeleton.md` value, equal to the spike window). The other figures in Gaps were the same budget worded differently. | Paper and Folia 1.21.11 and 26.3 (spike), hardware not recorded | target |
| PERF-HUD-01 | BetterHud update cost (MSPT share) | 0.2 | ms/tick | Design target. PAR-REL-07 measures it. | n/a | target |
| PERF-MEM-01 | Memory per region or per block-data entry | 128 | B | Design target per block-data entry (spike heap deltas 89 to 259 B). PAR-REL-07 measures it with a stable method. | n/a | target |

## How CI checks these

The CI job should run each `measured` benchmark (the JMH benchmarks once they exist, or a self-test that times the same code path), compare the result with the budget in the table, and fail the build when the result is more than 20 % above the budget (X-PERF-01) unless the PR carries a written reason. The plugin-testing skill asks for baselines to be committed under `docs/perf/`. Not decided yet: the runner (the spike numbers come from a shared cloud container with no pinned hardware), the server version the timing runs use (the table mixes 1.21.11 and 26.3), and whether the 20 % tolerance is right for noisy runners, which may need a larger margin or repeated runs. `target` rows are not checked by the JMH job; only the benchmarks in `budgets.json` are. CI: `.github/workflows/perf.yml` (a `perf-note:` line in the PR body waives a failure) and the isolation gate `.github/workflows/check.yml`.

## Gaps

- MSPT and plugin share of region-thread time at 50 players, Paper and Folia, with a scripted load test and spark profiles (M6-PERF-01; PRD target "<= 2 ms/tick").
- Vanilla worldgen baseline on the same end-to-end path, and the 1.5x ratio (M2-GEN-15; PERF-GEN-06).
- JMH per-chunk worldgen pipeline at N threads; no JMH source exists (M2-GEN-15; PERF-GEN-05).
- Exposed-gem count and overlay cost with TFC worldgen instead of the spike layout; Folia not run (M2-GEN-15; `docs/spikes/M0-VIS-02.md`).
- Chunk remap JMH and the 2,000-variant registry sync (M2-SEA-01, M2-SEA-02; PERF-SEA-01).
- Steady-state item packet rewrite cost: the spike had 6 samples per run (M0-NET-01 open items; PERF-PKT-05).
- Item lore build per stack (M3-SURV-01 tooltip lore; PERF-LORE-01).
- Collapse decision JMH (M3-PHYS-01; PERF-COL-01).
- Rotation network solver JMH (M6-MECH-01; PERF-ROT-01).
- Region commit budget held under real load, with the stress self-test (M1-THR-02; PERF-COMMIT-01).
- HUD update cost (MSPT) and HUD memory (X-HUD-01; PERF-HUD-01).
- Memory per region and per block-data entry, measured with a stable method (X-PERF-01; PERF-MEM-01).
- Packet rate per player: not measured anywhere in the repo (X-PERF-01).
- Hardware for the spike rows (PERF-PKT, PERF-CE, PERF-VIS) is not recorded; only PERF-GEN rows have a machine description (X-PERF-01).

### Conflicting numbers (not resolved here)

- Chunk remap, Paper 26.3: 1153 µs (`docs/spikes/M0-NET-01.md` Results, earlier session, not rerun) against 219 to 344 µs on the three reruns (PERF-PKT-01 to 03). PERF-PKT-04 has a `target` budget of 400 µs.
- Item packet, Paper 26.3: 39308 µs (same table, earlier session) against 897 to 1833 µs on the reruns (PERF-PKT-05). Has a `target` budget of 2000 µs.
- Region commit budget value: 1.5 ms per tick (`plans/01-m1-skeleton.md:62`), 1.5 ms per 50 ms window (`docs/spikes/M0-ASYNC-01.md:8`), 1 ms (`plans/02-m2-living-world.md:51`, the `SnowService` setting), and "about 1-2 ms/tick" (`ARCHITECTURE.md:101` and `.claude/skills/paper-folia-threading/SKILL.md:74`). Resolved to 1.5 ms per 50 ms tick (PERF-COMMIT-01).
- Not a conflict: the core-stage chunk cost (PERF-GEN-03, 13.40 ms) and the full fill (PERF-GEN-01, 29.85 ms) measure different scopes (the full fill includes decoration). Do not compare them directly.
