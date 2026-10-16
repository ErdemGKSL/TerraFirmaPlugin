---
name: plugin-testing
description: Testing strategy and concrete setup for this Paper + Folia TFC plugin. Pure-Java unit/property/golden-value tests for core and worldgen (incl. porting TFC's own tests), data-load validation of all ported TFC JSON, worldgen determinism and concurrency tests (jcstress), JMH performance budgets, MockBukkit for listeners/commands/PDC, an in-server self-test harness run through run-paper on a Paper/Folia × 1.21.11/26.x matrix with CraftEngine/BetterModel/BetterHud installed, thread-ownership assertions, spark profiling, and manual visual checklists. Use this whenever writing, running, fixing or planning tests, CI, benchmarks or profiling, when finishing a ported feature ("is it done?"), or when a bug report needs a regression test, even if the user only says "make sure it works".
---

# Testing this plugin

A port is "done" when TFC's numbers are reproduced, it can't break threading rules, and it stays inside its performance budget. Use the pyramid below. Lower layers are cheap and catch most bugs; only go up a layer for what the lower one can't see.

Versions and tools (looked up 2026-10, verify before pinning): JUnit Jupiter 6.1.x, jqwik 1.10.x, MockBukkit `org.mockbukkit.mockbukkit:mockbukkit-v26.2:4.117.0` (also `mockbukkit-v1.21`; no 26.3 artifact yet), run-paper 3.1.0, JMH 1.37 via `me.champeau.jmh` 0.7.3, jcstress 0.16, Awaitility 4.3.0. Source: `paper-folia-threading/references/tooling-report.md` §2, §5. The toolchain is **Java 25 for everything**, because BetterModel and BetterHud require Java 25 at runtime and compile time.

## Layer 1: pure Java (`core/`, `worldgen/`). Most tests live here.
- **Golden values from TFC.** For every ported rule, pin at least one number taken from TFC behaviour or code: decay date of a food at day N, item temperature after T ticks at heat capacity C, forging rule outcomes, alloy percentages, crop growth at given climate. When the number came from running TFC, note it in the test (`// from TFC c3b4d95 in-game: ...`).
- **Port TFC's own tests** together with the code they cover: `ForgeRuleTest`, `KnappingPatternTest`, `RotationNetworkTest`, `FlowTest`, `SolarCalculatorTest`, `HeatingItemTest`, `MoldTest` (paths in `tfc-feature-porting/references/tfc-source-map.md` §4). Adapt the setup, keep the assertions.
- **Property tests (jqwik)** for invariants: temperature never exceeds the source, nutrition stays in [0,1], climate is continuous, rock layers are complete for every column.
- **Data-load test:** load the entire `content/data/**` tree through the real registry loader. Fail on any unknown ingredient/output/recipe type, unresolved item id or tag, or duplicate id. This catches datagen regressions immediately.
- **Lazy-evaluation equivalence:** for each calendar-based system, assert that "evaluate once at t=N" equals "step through t=0..N" (step only in the test). This proves the no-ticking design matches TFC's ticking behaviour.

## Layer 2: worldgen determinism and concurrency
- **Determinism:** same seed + chunk → identical block/biome hash, across runs and across thread counts. Generate a grid of chunks sequentially and in parallel (e.g. a 16-thread pool) and compare hashes. This catches non-thread-safe caches.
- **Visual check:** port TFC's drawing tests (`test/drawing/RegionGeneratorTests`, `BiomeNoiseTest`, `Artist`) to render region, biome and rock maps to PNGs under `build/worldgen-images/`. They're for human review, not assertions. Attach them when reviewing a worldgen change.
- **jcstress** for anything lock-free: the calendar/season snapshot publication, the commit queues, atomic registry swaps.

## Layer 3: performance budgets (JMH)
Benchmark hot paths with `@Threads(N)`. Start with these budgets and tighten them once real numbers exist:
- worldgen: full chunk pipeline per chunk (noise → surface → rocks → features)
- chunk biome-palette rewrite per packet
- item lore build per stack
- collapse computation for a typical cave-in

Commit the baseline results (`docs/perf/`). A >20% regression needs a written reason in the PR.

## Layer 4: MockBukkit (Paper glue without a server)
Good for: event listeners, commands, PDC codecs on ItemStacks/entities/chunks, GUI click handling, scheduler wiring.

Its limits, so you don't trust it for the wrong thing:
- no worldgen pipeline (`WorldMock#getGenerator` throws), but `ChunkGenerator.generateNoise` can be called directly with `server.createChunkData(world)`;
- no packets/NMS;
- **`isOwnedByCurrentRegion` always returns `isPrimaryThread()`**, so it can't detect wrong-region access;
- the bootstrapper/loader don't run, so keep a test seam around `PluginBootstrap` logic.

The CraftEngine/BetterModel/BetterHud adapters are interfaces (see `CLAUDE.md`). Use fakes of *our* adapter interfaces in MockBukkit tests; never mock the third-party plugins' internals.

## Layer 5: in-server self-test harness (the real thing)
A `itest` plugin (or the main plugin with `-Dtfp.selftest=true`) runs scripted cases after the server and all three client plugins have loaded (after BetterModel's `PluginEndReloadEvent` and CraftEngine's pack build). Then it writes a JSON/JUnit report and shuts the server down. Typical cases:
- generate chunks with `getChunkAtAsync(x, z, true)` and assert block/biome distributions and that CraftEngine blocks are present;
- place a device, advance the calendar, unload/reload the chunk, assert the lazily evaluated state;
- spawn an animal, assert a BetterModel tracker exists and our visual state was re-applied after a chunk reload;
- register BetterHud placeholders and evaluate them for a fake player snapshot.

Run it through run-paper with `downloadPlugins` for CraftEngine, BetterModel, BetterHud (and PacketEvents if adopted). Use the matrix **Paper 26.3, Paper 1.21.11, Folia 26.3 (alpha), Folia 1.21.11**, all on Java 25. **Folia is the strongest thread-bug detector**: it throws on wrong-thread access. A feature isn't done until its self-tests pass on Folia. Gradle and CI snippets: `references/harness-and-ci.md`.

Turn on our own assertions in all test servers (`-Dtfp.assertThreads=true`): `assertOwned(location)` before every block/entity mutation, and `assertNotTickThread()` in code that must never run on a tick thread (I/O, heavy compute).

## Layer 6: manual visual checks (what automation can't judge)
Textures, models, animations, HUD alignment and GUI art need a human with a real client. Make the check fast and repeatable:
- Every feature adds a showcase to `/tfp debug showcase <feature>`. It places all variants and states in a grid (all ore grades × rocks, every crop stage, every animal variant/age), so a reviewer can look at them in one place.
- Use the checklist in `references/visual-checklist.md`. Record the result (and screenshots, if the reviewer provides them) in the PR.

## Profiling
spark is bundled with Paper. Use `/spark profiler start --timeout 300` with chunk-worker and netty threads included, under a scripted load: a pre-generated area plus N bots or players walking. Timings is deprecated; don't use it. Report MSPT per region on Folia.

## Definition of done for a ported feature
- [ ] Golden-value + ported TFC tests green (layer 1)
- [ ] Data-load test green with the feature's data
- [ ] Lazy-vs-stepped equivalence test, if time-based
- [ ] Benchmarks added for any new hot path, within budget
- [ ] Self-test cases pass on Paper **and Folia** (26.3 and 1.21.11)
- [ ] Showcase + visual checklist done
- [ ] Ledger row updated (`docs/porting/ledger.md`)
