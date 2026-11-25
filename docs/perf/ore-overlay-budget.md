# Ore overlay and visual budget guards (PAR-REL-12)

Status: 2026-11-07. Three automated guards keep the visual-state budget and the ore overlay budget from regressing. PAR-REL-02
links this page into `docs/perf/budgets.md` when it lands.

## Gate values

| Guard | Gate | Where it comes from |
|---|---|---|
| Visual pools, every shipped allocation (level-0..2 × 1.21/26 × Nether/End on/off) | used ≤ capacity per pool; CE `solid` pool keeps ≥ `Profile.SOLID_RESERVE_WARNING` (500) free; no `tfp:ore/**` appearance owns a visual; no allocation folder for a profile outside `Profile.values()`; one model list per vanilla state | ARCHITECTURE.md §9.1 (always-on ore rule, PAR-INFRA-15) |
| Overlays per player within the gated radius (4 chunks, 81 chunks, for the 48 m cutoff) | ≤ 8 000 (S1 gate, = default `ore-overlay.max-per-player`) | `docs/spikes/S1-ore-overlay.md` §4, design §4.3 |
| Table build CPU per chunk (`ExposedFaces.compute`) | target 0.5 ms; the unit test fails above 4 × the target (2 ms), median of 20 runs after 50 warm-up runs | `docs/research/ore-entity-renderer-design.md` §4.3 |

## The guards and how to rerun them

1. **`ProfilePoolGuardTest`** (`tools/datagen`, part of `./gradlew check`). Reads `visuals/index.json` and each allocation's
   `visual_templates.yml` from the in-memory output of the pinned checkout and from the committed `content/generated`.
   It counts the pools from the templates, so the index's own numbers are checked too. Each violation names the allocation, the pool and the
   block ids. Seeded failures are committed tests: an overflow, a full-cube ore, a leftover `level-3a` folder, two models
   on one state, and a solid pool below the reserve.
   `./gradlew :tools:datagen:test --tests dev.tfp.datagen.ProfilePoolGuardTest`
   - Shown with temporary local changes (not committed): `oreoverlay/mode.properties` set to `full_cube` fails with
     "1113 ore appearance(s) own a visual instead of the host rock's" in all 12 allocations. `SOLID_RESERVE_WARNING = 1300`
     fails with "pool solid: 1289 slots left < reserve 1300" for every 1.21 allocation.
2. **`OreOverlayBudgetTest`** (`core`, deterministic). The fixture
   `core/src/test/resources/dev/tfp/core/oreoverlay/budget/s1-census-tfc.txt` holds the per-chunk ore blocks, surface faces and cave faces
   of the S1 census re-run with TFC's caves (PAR-WORLD-19; seeds 1-3, 32 × 32 chunks each). `BudgetFixture.main` exports it from
   `spikes/ore-overlay/out/ore-census-*-paper.json`, and a test checks that the resource still equals that export. Each chunk becomes an
   `OreSnapshot` with exactly that many ores and exposed faces (outcrops included; ore layers 3 blocks apart so a dense cave-world
   chunk can open up to 2 faces per ore). The test then runs the real `ExposedFaces.compute`, checks p95 over every window of the
   area (S1 §2.3) against the gate, and reconciles every chunk of the 20 S1 sample squares into a `ViewerSet` with the default cap.
   `./gradlew :core:test --tests 'dev.tfp.core.oreoverlay.budget.*'`
3. **itest `ore.overlay.budget`** (`OreOverlayBudgetCase`, in the default self-test suite, 10 × 10 chunks by default). It
   generates the area plus a ring and holds it with force-load tickets set on the global region after generation. For each of
   the 20 S1 sample positions, a fake viewer joins the real `OreOverlayService` (`viewerWorld`, `center`, `chunkSent` for the 81 chunks). The case waits until every
   table is published and the rate limiter has nothing deferred. It records the faces, the entities the viewer holds,
   the packets (2 per overlay) and the bytes. The bytes are estimated at S1's measured 125 B per overlay, because the fake
   viewer has no connection. It fails when a viewer holds more than `min(cap, 8 000)`, when the entities differ from `min(faces, cap)`, or when p95 faces
   exceed 8 000. It writes `ore-overlay-budget.json` in the server folder. To run it on the S1 area, use the
   `spikes/ore-overlay/run-census.sh` recipe (priming start, then a fresh world) with
   `EXTRA_JVM="-Dtfp.selftest=true -Dtfp.oreOverlayBudget.chunks=32"`, `level-seed=1`, Paper 1.21.11.

## Latest numbers

### Overlay count (core fixture, PAR-WORLD-19 census with TFC caves, 2026-11-25)

The fixture recounts S1 exactly: the per-chunk and 20-sample radius p50/p95/max equal the published census values, and the
all-window figures equal S1 §2.3.

| Seed | 40 m (default, 3-chunk radius), all 676 windows p50 / p95 / max | 48 m (4-chunk radius), all 576 windows p50 / p95 / max | 40 m, 20 samples: raw max, sent max (cap 8 000) | 48 m, 20 samples: raw p95 / max, sent p95 / max |
|---|---|---|---|---|
| 1 | 2 192 / 6 969 / 11 171 | 3 786 / **8 901** / 13 000 | 8 858, 8 000 (1 of 20 capped) | 6 693 / 7 006, 6 693 / 7 006 |
| 2 | 2 807 / 7 615 / 10 528 | 4 971 / **11 293** / 13 108 | 7 705, 7 705 | 11 199 / 13 070, 8 000 / 8 000 (5 of 20 capped) |
| 3 | 1 246 / 3 136 / 3 919 | 2 197 / 4 613 / 5 761 | 3 671, 3 671 | 4 242 / 4 693, 4 242 / 4 693 |

- Gate status:
  - At the default 40 m, p95 is within 8 000 on every seed (asserted). Windows above it (max 11.2 k) are where the cap binds.
  - At the former 48 m, p95 is above the gate on seeds 1 and 2. The test pins this documented fact (the reason for
    PAR-WORLD-62); it does not claim a pass.
  - A player is never sent more than 8 000 overlays at either radius: the cap keeps the nearest faces.
  - The PAR-WORLD-17 combined estimate (vanilla cave proxy as a per-chunk floor) is superseded and no longer gated; at 40 m
    its seed 2 all-window p95 is 8 241.
- Radius formula. `OverlayGate.radiusChunks` was `ceil(cutoff / 16) + 1`, so a 40 m cutoff gave **4** chunks, not the 3 the
  S1 doc assumes for its "40 m" option. PAR-WORLD-62 changed it to `ceil(cutoff / 16)` (the smallest radius covering the
  sphere from anywhere in the viewer's chunk) and the default cutoff to 40 m: 3 chunks, 49 chunks. `OreOverlayBudgetTest`
  pins S1's 4-chunk figures with an explicit 4-chunk gate and checks the default against the 3-chunk square.

### Table build CPU (core, `ExposedFaces.compute` only, this 4-core container, 2026-11-25)

| Chunk (all 3 seeds, by ore count) | Ores | Faces | Median of 20 runs |
|---|---|---|---|
| p50 | 135 | 9 | 0.002-0.004 ms |
| p95 | 2 557 | 168 | 0.042-0.073 ms |
| max | 7 547 | 497 | 0.14-0.23 ms |

### itest `ore.overlay.budget`, S1 area (Paper 1.21.11-132, seed 1, 32 × 32 chunks at −93, −171, 2026-11-07)

| Measure | Value |
|---|---|
| Faces within the 4-chunk radius, 20 samples p50 / p95 / max | 0 / 0 / 0 (no tunnels yet, as S1 measured) |
| Overlay entities per player p95 / max | 0 / 0 |
| Join packets / bytes p95 | 0 / 0 |
| Table builds | 1 620 builds. CPU avg 4.15 ms, max 478 ms. The max is the server-wide maximum since start, taken under generation load on a shared 4-core machine. The service keeps no per-build p95. |
| Join settle time per sample (all 81 tables published, nothing deferred) | p95 555 ms, max 703 ms |
| Area generation | 171.5 s |
| Result | PASSED |

- The runtime's average build (4.15 ms) is far above the core `compute` figure (≤ 0.17 ms). The runtime build also counts
  `ExposedFaces.borderNeeds`, which uses a boxed `TreeSet` over every ore's out-of-chunk neighbours, and it runs while the
  server is generating terrain. The 0.5 ms target in design §4.3 is met by `compute` but not by the full runtime build.
  PAR-REL-02's JMH set should measure `borderNeeds + compute` separately.
- The same run had an unrelated failure, `inst.suppression` (M1-INST-04: lava next to water formed obsidian with no suppression log line).
- Rerun on a world with caves: PAR-WORLD-19. Until then the itest shows the lower bound and the core fixture carries
  the estimate.
