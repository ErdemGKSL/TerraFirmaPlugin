# M1: Skeleton

Goal: a buildable, installable plugin with all infrastructure that features plug into. There's no gameplay yet besides rock/soil/ore blocks existing and loading. Exit: the server starts on Paper + Folia (1.21.11, 26.3) with the three client plugins. Our CE pack installs and is built and delivered. Registries load from datagen output. CI runs unit tests and the self-test matrix.

## Build & project

- [x] **M1-BUILD-01 Gradle multi-module setup** · deps: M0 done · skills: plugin-testing
  - Do: Gradle Kotlin DSL with `build-logic` conventions:
    - Java 25 toolchain;
    - modules `api`, `core`, `worldgen`, `paper`, `integration/{craftengine,bettermodel,betterhud}`, `content`, `tools/datagen`, `itest`, `dist`;
    - Shadow 9.x with relocations for Caffeine/JCTools (or the PluginLoader approach chosen in M0);
    - `compileOnly` Paper API at the lowest supported version, plus CE 26.9.1 / BetterModel / BetterHud APIs;
    - foojay resolver.
  - Done when: `./gradlew build` produces one plugin jar, and `core`/`worldgen` have no Bukkit on their classpath (enforced by a dependency check).

- [x] **M1-BUILD-02 License headers & NOTICE automation** · deps: M1-BUILD-01
  - Do: EUPL header check (Spotless or similar). A ported-file header template with a TFC path + commit field. A NOTICE consistency check (e.g. every asset with a non-EUPL license is listed).
  - Done when: CI fails on missing headers.

- [ ] **M1-BUILD-03 CI pipeline** · deps: M1-BUILD-01 · skills: plugin-testing
  - Do: GitHub Actions: `check` (unit tests, data-load test, header check), then the self-test matrix {Paper, Folia} × {1.21.11, 26.3} on Java 25 with pinned plugin versions. Upload artifacts and logs.
  - Done when: a green run on main, with a failing self-test making the job red.

## Plugin bootstrap & lifecycle

- [ ] **M1-BOOT-01 paper-plugin.yml, bootstrapper, loader** · deps: M1-BUILD-01 · skills: dependency-bootstrap
  - Do:
    - `load: STARTUP`, `folia-supported: true`;
    - dependencies on CE/BM/BH with `load: BEFORE`, `join-classpath: true`;
    - the bootstrapper registers the datapack (initially empty) and Brigadier `/tfp` commands;
    - the loader handles libraries;
    - the plugin class runs the `onLoad`/`onEnable` phases as specified in `dependency-bootstrap` §2.
  - Done when: boots on all 4 targets; `/tfp version` works.

- [ ] **M1-BOOT-02 Compatibility gate** · deps: M1-BOOT-01 · skills: dependency-bootstrap
  - Do: `content/compat.yml` (MC range, plugin minimums, Java 25). Refuse to enable on unsupported versions, with clear messages. Warn on untested newer versions.
  - Done when: unit tests cover the gate; a self-test with a too-old dependency fails cleanly.

- [ ] **M1-BOOT-03 Config system** · deps: M1-BOOT-01
  - Do: our `config.yml`, with a typed config in `core` (records), comment-preserving defaults merge and a reload path. It includes `visuals.profile` (`1.21: level-3a`, `26: level-2`), `visuals.solid-reserve-warning: 500`, `visuals.use-nether-end-blocks: auto` and `auto-install.*`. Port TFC's `config/ServerConfig`/`CommonConfig` keys as their features land.
  - Done when: config round-trip tests pass.

- [ ] **M1-BOOT-04 Ready gate** · deps: M1-BOOT-01
  - Do: a single readiness flag: CE reloaded ∧ BetterModel models ready ∧ registries loaded. Features and self-tests wait on it.
  - Done when: a self-test asserts ready within N seconds of startup.

## Threading primitives

- [x] **M1-THR-01 Scheduler abstraction** · deps: M1-BUILD-01 · skills: paper-folia-threading
  - Do: the `Scheduler` interface (region / entity with retired / global / async CPU pool / io on virtual threads) + Folia-scheduler implementation + `ThreadAsserts` (`-Dtfp.assertThreads`).
  - Done when: unit + self-tests on Paper and Folia; no `BukkitScheduler` usage (enforced by an ArchUnit-style check).

- [x] **M1-THR-02 RegionCommitQueue** · deps: M1-THR-01 · skills: paper-folia-threading
  - Do: per-region budgeted queue, `BlockChange(pos, expectedOld, new)` validation, stale re-queue callback, configurable budget (default 1.5 ms/tick/region), metrics.
  - Done when: a stress self-test keeps the budget, and the stale-change race test passes.

- [x] **M1-THR-03 AsyncDecisionPipeline** · deps: M1-THR-02 · skills: paper-folia-threading
  - Do: generic pipeline (snapshot supplier → pure decide → per-chunk commit), per-position coalescing, `NeighbourSnapshot` helper (radius-N capture, immutable), metrics.
  - Done when: the connected-block toy from M0-ASYNC-01 is rebuilt on it, with tests.

- [ ] **M1-THR-04 Atomic registry & calendar snapshot primitives** · deps: M1-THR-01
  - Do: `AtomicReference`-based snapshot holders; a volatile global clock holder (filled by M2-CAL-01).
  - Done when: jcstress tests for publication pass.
  - Status: `dev.tfp.core.concurrent.Snapshot<T>` (versioned `AtomicReference`, `publishIfNewer`) added by M1-REG-01 with latch-based JUnit publication tests; jcstress and the clock holder are still open.

## Data & registries

- [x] **M1-REG-01 Registry framework in core** · deps: M1-THR-04 · skills: tfc-feature-porting
  - Do: typed registries, a codec layer (JSON, strict: unknown keys/types fail), `RegistrySnapshot`, off-thread load + atomic swap, `/tfp reload data`.
  - Done when: unit tests and a reload-during-use test pass.
  - Status: core done (`json/`, `registry/`, `RegistriesReloadTest`). The `/tfp reload data` command is Paper glue: it calls `Registries.reload(executor, () -> DataLoader.standard(ids).load(roots))`.

- [x] **M1-REG-02 Ingredients & output modifiers** · deps: M1-REG-01 · skills: tfc-feature-porting
  - TFC: `common/recipes/ingredients/`, `common/recipes/outputs/`
  - Do: port all ingredient types (`tfc:and`, `tfc:not_rotten`, `tfc:lacks_trait`, `tfc:has_trait`, `tfc:heat`, `tfc:fluid_content`, ...) and output modifiers (`tfc:copy_input`, `tfc:add_trait`, `tfc:copy_food`, `tfc:meal`, `tfc:add_heat`, ...) as sealed hierarchies with codecs. Their behaviour hooks call into later systems through interfaces (food/heat land in M3).
  - Done when: every type found in TFC data parses (the data-load test), and unit tests cover the matching logic.
  - Status: `SampleDataTest` (fixed TFC sample in `core/src/test/resources/tfc-sample`) covers every type in TFC data; `TfcDataCoverageTest` loads all ~8.5k upstream files (needs `TFC_ROOT`); `ContentDataLoadTest` parses all datagen output.

- [x] **M1-REG-03 Tags** · deps: M1-REG-01
  - TFC: `src/generated/resources/data/tfc/tags/**`
  - Do: our tag registry for blocks/items/fluids/entities (CE ids + vanilla), loaded from the datagen output.
  - Done when: TFC tag files load, and lookups are tested.

- [ ] **M1-REG-04 Data-load test** · deps: M1-REG-02, M1-DGN-02
  - Do: a test loading all of `content/data/**`. It fails on unknown types, unresolved ids/tags or duplicates.
  - Done when: it runs in CI.
  - Status: `core/.../registry/ContentDataLoadTest` loads `content/generated/content/data` with `id_map.json` in `:core:test` (part of `check`; the folder is a test input). Everything parses and every `tfp:` item/block id resolves, but 99 references to NeoForge `c:` common tags that datagen doesn't export (`c:cobblestones`, `c:feathers`, ...) remain, so the test aborts (skipped) on exactly that error kind and fails on any other. Open until M1-DGN-02 exports those tags. The id map should also list fluids, entity types, food traits and the vanilla ids used, so that those are checked too.

## Datagen v1 (tools/datagen)

- [x] **M1-DGN-01 TFC checkout pin & manifest** · deps: M1-BUILD-01 · skills: tfc-asset-migration
  - Do: CLI that clones/reads TFC at the commit pinned in the ledger, hashes its inputs and writes `content/.datagen-manifest.json`. The output must be deterministic (byte-identical).
  - Done when: two runs produce identical output; CI verifies `content/` is up to date.
  - Status: done. Output dir `content/generated/content` (jar `/content`). Determinism: unit test + two real runs diffed. CI gate: `./gradlew :tools:datagen:verifyContent` (wire into the workflow in M1-BUILD-03).

- [ ] **M1-DGN-02 Data export** · deps: M1-DGN-01 · skills: tfc-feature-porting
  - TFC: `src/generated/resources/data/tfc/{tfc/**,recipe/**,tags/**}`
  - Do: rename `tfc:` → `tfp:`, maintain the item/block id mapping table, write `content/data/**` in TFC schemas.
  - Done when: M1-REG-04 passes.
  - Status: export + `data/id_map.json` generated (generated `data/tfc/{tfc,recipe,tags}`, `c:`/`minecraft:` tags, hand-written worldgen tags). Open until M1-REG-04 exists.

- [ ] **M1-DGN-03 Asset pipeline v1: rocks, soils, ores** · deps: M1-DGN-01 · skills: tfc-asset-migration, craftengine-config-generation, craftengine-blocks
  - TFC: `assets/tfc/{blockstates,models,textures}/{rock,ore,dirt,grass,clay,sand,...}`
  - Do:
    1. Copy textures and models with refs rewritten.
    2. **Flatten ore base+overlay into one PNG per ore×rock.**
    3. Emit literal CE YAML per id calling `tfp:` templates (author `tfp:settings/*`, `tfp:block_state/*`, `tfp:loot_table/*`).
    4. Generate lang for all locales.
  - Done when: the CE pack for these families loads with zero `Found N issue(s)` lines (checked by the self-test).
  - Status: 1353 cube blocks + templates + lang (13 locales) generated and statically checked; CE load not verified yet (self-test). Not emitted yet (listed in `reports/datagen-summary.json`): connected grass (multipart), slabs/stairs/walls, loose rocks, spikes, buttons, plates, anvils, aqueducts, rope anchors, small ores.

- [ ] **M1-DGN-04 Visual allocator with profiles** · deps: M1-DGN-03, M0-VIS-01 · skills: craftengine-visuals-pack
  - Do: implement profiles `level-0…level-3b` × MC version families. Sources, in order: CE `solid` → identity duplicates → (Nether/End) → absent-in-TFC (CSV allowlist). Also:
    - emit `block_state_mappings` that hide the repurposed blocks;
    - write allocation reports (used/free per pool and version);
    - CI fails on overflow.
  - Done when: reports exist for every profile; defaults (`1.21: level-3a`, `26: level-2`) leave ≥ the target reserve.

- [ ] **M1-DGN-05 Vanilla-block suppression data** · deps: M1-DGN-04
  - Do: list the repurposed vanilla blocks per profile so that our runtime (M1-INST-04) blocks their creation paths. Generate the datapack tag edits (ambient sounds, `#crystal_sound_blocks`, `#impermeable`).
  - Done when: the lists ship in the jar; the datapack loads.

## Installer & integrations

- [ ] **M1-INST-01 Install manifest engine** · deps: M1-BOOT-01 · skills: dependency-bootstrap
  - Do: write to the CE `resources/tfp/` and BetterHud `packs/tfp/` folders. Hash manifest (untouched → overwrite; edited → `.tfp-new`; deleted → respect), atomic writes, opt-outs.
  - Done when: unit tests for all three cases pass.

- [ ] **M1-INST-02 Config edits in CE/BH/BM** · deps: M1-INST-01 · skills: dependency-bootstrap
  - Do: comment-preserving single-key edits from the `dependency-bootstrap` §6 table:
    - CE: `delay-configuration-load: false`, `serverside-blocks` sizing, `supported-version.min`, `exclude-core-shaders: false`, disable `remove_shulker_head`;
    - BetterHud: remove `test_hud`, merge settings.
    Show a restart banner and degraded mode when needed.
  - Done when: the fresh-server self-test (first start → restart → ready) passes.

- [ ] **M1-INST-03 Visual profile selection & pack rebuild** · deps: M1-DGN-04, M1-INST-01
  - Do: pick the allocation for `visuals.profile` and the running version. Trigger a CE pack rebuild when content or profile changed. Check pool capacity at runtime (fail and name the higher profile, or warn below the reserve).
  - Done when: a self-test switches profiles across restarts without world changes.

- [ ] **M1-INST-04 Vanilla-block suppression runtime** · deps: M1-DGN-05, M1-THR-01
  - Do: block/convert the creation of repurposed vanilla blocks in TFC worlds: `BlockFormEvent` (lava+water → stone/cobble/obsidian/basalt), concrete powder hardening, copper oxidation/waxing, vanilla recipes/loot producing them (via CE `disable-vanilla-recipes` list + our loot hooks), and dispensers/commands (best effort).
  - Done when: self-tests confirm no repurposed vanilla block can appear through the covered paths.

- [ ] **M1-INT-01 Integration adapter interfaces** · deps: M1-BUILD-01
  - Do: interfaces in `paper` (`BlockVisuals`, `CustomBlocks`, `CustomItems`, `EntityModels`, `HudBridge`) + implementations in `integration/*` + fakes for tests.
  - Done when: feature code compiles against interfaces only (enforced by an architecture test).

- [ ] **M1-INT-02 CraftEngine adapter base** · deps: M1-INT-01 · skills: craftengine-java-api
  - Do: behaviour registration in `onLoad`, state cache refreshed on every `CraftEngineReloadEvent`, region-thread-only wrappers, listener priorities (≥ LOW on load, ≤ HIGH on unload), log appender detecting CE config issues for our pack.
  - Done when: self-test: our test block places, breaks, persists; zero CE issues logged.

- [ ] **M1-INT-03 BetterModel adapter base** · deps: M1-INT-01 · skills: bettermodel
  - Do: `ModelAssetsEvent` registration from the jar, a guarded subscriber wrapper (try/catch), `PluginEndReloadEvent` → ready, `CreateEntityTrackerEvent` re-apply hook.
  - Done when: the bovine example model spawns in the self-test.

- [ ] **M1-INT-04 BetterHud adapter base** · deps: M1-INT-01 · skills: betterhud
  - Do: placeholder/listener registration in `onEnable`, the per-player `HudSnapshot` cache (published from the player's thread), pack folder install.
  - Done when: a placeholder evaluates from the snapshot in the self-test with no Folia errors.

## Core services

- [ ] **M1-SVC-01 Block-data store** · deps: M0-CE-02, M1-INT-02
  - Do: implement the decision from M0-CE-02 (CE controller base class + our chunk-PDC store for chunk-scale data), with versioned codecs and migration tests.
  - Done when: persistence self-tests (unload/reload/restart) pass.

- [ ] **M1-SVC-02 Player data store** · deps: M1-THR-01
  - Do: async load at pre-login and async save; a versioned record (thirst, nutrition, intoxication, chisel mode, ... added by features).
  - Done when: a join/quit/crash-safety self-test passes.

- [ ] **M1-SVC-03 Packet layer base** · deps: M0-NET-01
  - Do: the chosen packet library integration, a handler registry with netty-thread rules, creative-return stripping, metrics.
  - Done when: a no-op handler is installed and measured.

- [ ] **M1-SVC-04 i18n** · deps: M1-DGN-02
  - Do: server-side translations (our messages) + client lang via CE `lang:` (generated). Ship all TFC locales including `tr_tr`.
  - Done when: a self-test shows a translated message per locale.

- [ ] **M1-SVC-05 Commands & debug** · deps: M1-BOOT-01
  - Do: `/tfp reload [data|content]`, `/tfp debug showcase <family>` (places all variants in a grid), `/tfp debug block|player|chunk`, `/tfp version`.
  - Done when: the showcase works for rocks/soils/ores.

## Self-test harness

- [ ] **M1-TEST-01 itest plugin** · deps: M1-BOOT-04 · skills: plugin-testing
  - Do: a case runner that waits on the ready gate, writes a JUnit XML report and shuts down. Fails on thread-assert exceptions in logs and on CE issues for our pack.
  - Done when: it runs in the CI matrix.

- [ ] **M1-TEST-02 MockBukkit & unit test base** · deps: M1-BUILD-01
  - Do: test fixtures for adapters (fakes), MockBukkit setup for listeners/commands/PDC codecs.
  - Done when: sample tests pass in CI.

## Exit criteria
Fresh server → our jar + CE/BM/BH → one automatic restart → ready. Rock/soil/ore blocks are placeable and showcase-visible with the default visual profile. CI matrix is green.
