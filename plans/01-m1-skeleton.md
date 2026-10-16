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
  - Status: first GitHub run is green on main (run 18, 9ebbdce): check, content-up-to-date and the four self-test jobs all passed. Still open: a failing self-test turning the job red has been shown only locally (rc=1), not in a CI run.
  - Earlier status: workflow written, no run yet. `.github/workflows/ci.yml`: `check` (fetch TFC @ pinned commit, `generateContent`, `check`, `verifyContent` as determinism gate), `content` (verifies a committed `content/generated/` is up to date; skipped while it is gitignored), `selftest` matrix {paper, folia} × {1.21.11, 26.3} → `:itest:runSelfTest` (added in `itest/build.gradle.kts`, run-paper, Modrinth ids from `libs.versions.toml`; fails fast without the M1-TEST-01 plugin, fails unless `selftest-report.xml` has no failures) + log upload. Verified locally: YAML parses, tasks exist, the `check` job's commands pass on a clean HEAD worktree + these changes. Fixed `.gitignore`: `**/build/` was hiding `build-logic/src/main/kotlin/dev/tfp/build/` (CheckLicenseHeaders/CheckNotice), so a fresh checkout did not compile. Unverified: an Actions run, the shallow fetch of TFC by SHA, the matrix (red until M1-TEST-01).

## Plugin bootstrap & lifecycle

- [x] **M1-BOOT-01 paper-plugin.yml, bootstrapper, loader** · deps: M1-BUILD-01 · skills: dependency-bootstrap
  - Do:
    - `load: STARTUP`, `folia-supported: true`;
    - dependencies on CE/BM/BH with `load: BEFORE`, `join-classpath: true`;
    - the bootstrapper registers the datapack (initially empty) and Brigadier `/tfp` commands;
    - the loader handles libraries;
    - the plugin class runs the `onLoad`/`onEnable` phases as specified in `dependency-bootstrap` §2.
  - Done when: boots on all 4 targets; `/tfp version` works.
  - Status: code done (`paper-plugin.yml`, `boot/TfpBootstrap` datapack + `/tfp [version|reload config]`, `boot/TfpLoader`, `TfpPlugin` phases), `BootstrapTest` + `:dist:shadowJar` pass. Client plugins are `required: false` (degraded mode; the compat gate refuses too-old versions). Libraries: Caffeine/JCTools stay bundled + relocated, so the loader resolves nothing. Not run on a server yet: boot on the 4 targets, datapack discovery (pack.mcmeta format range) and `/tfp version` in game are unverified.
  - Verified in the server: boots on the four targets and `/tfp version` answers (self-test, matrix rerun acd145a + paper-26.3-fix).

- [x] **M1-BOOT-02 Compatibility gate** · deps: M1-BOOT-01 · skills: dependency-bootstrap
  - Do: `content/compat.yml` (MC range, plugin minimums, Java 25). Refuse to enable on unsupported versions, with clear messages. Warn on untested newer versions.
  - Done when: unit tests cover the gate; a self-test with a too-old dependency fails cleanly.
  - Status: pure decision `core.compat.CompatGate` (`CompatGateTest`, `VersionTest`); Bukkit side `paper.boot.CompatCheck` (`CompatCheckTest` runs the too-old-dependency case against faked server/plugins). Minimums = spike-pinned versions (CE 26.9.2, BM 3.5.1, BH 2.2.0-SNAPSHOT-449). The in-server self-test is left to M1-TEST-01.

- [x] **M1-BOOT-03 Config system** · deps: M1-BOOT-01
  - Do: our `config.yml`, with a typed config in `core` (records), comment-preserving defaults merge and a reload path. It includes `visuals.profile` (`1.21: level-3a`, `26: level-2`), `visuals.solid-reserve-warning: 500`, `visuals.use-nether-end-blocks: auto` and `auto-install.*`. Port TFC's `config/ServerConfig`/`CommonConfig` keys as their features land.
  - Done when: config round-trip tests pass.
  - Status: `core.config` (`TfpConfig`, `ConfigCodec`, text-based `YamlDefaultsMerge`), `paper.config.ConfigService` + `/tfp reload config`. Tests: `ConfigCodecTest`, `YamlDefaultsMergeTest`, `ConfigServiceTest`.

- [x] **M1-BOOT-04 Ready gate** · deps: M1-BOOT-01
  - Do: a single readiness flag: CE reloaded ∧ BetterModel models ready ∧ registries loaded. Features and self-tests wait on it.
  - Done when: a self-test asserts ready within N seconds of startup.
  - Status: `paper.ready.ReadyGate` (signal / waive for a missing plugin / fail), `ReadyGateTest`. Signals: CE → M1-INT-02 and BetterModel → M1-INT-03 adapters (wired, not runtime-verified), registries → `paper/data/RegistryService` at enable (wired by M1-SVC-05, not runtime-verified); the in-server self-test is M1-TEST-01.

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

- [x] **M1-THR-04 Atomic registry & calendar snapshot primitives** · deps: M1-THR-01
  - Do: `AtomicReference`-based snapshot holders; a volatile global clock holder (filled by M2-CAL-01).
  - Done when: jcstress tests for publication pass.
  - Status: done with **repeated JUnit stress tests instead of jcstress** (no jcstress Gradle plugin in the catalog). `core/concurrent/Snapshot` (+ CAS `update`), `core/clock/GlobalClockHolder` (volatile immutable `ClockSnapshot`; calendar fills it in M2-CAL-01). `SnapshotStressTest` (×10: concurrent `update`, no lost update, readers never see a torn version/value pair or a backwards version) and `GlobalClockHolderTest` (×10: one writer, 4 readers, every snapshot internally consistent, sequence monotonic; ×5 concurrent writers keep `sequence` unique). Wiring real jcstress stays a CI improvement.

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

- [x] **M1-REG-04 Data-load test** · deps: M1-REG-02, M1-DGN-02
  - Do: a test loading all of `content/data/**`. It fails on unknown types, unresolved ids/tags or duplicates.
  - Done when: it runs in CI.
  - Status: done. `core/.../registry/ContentDataLoadTest` loads `content/generated/content/data` with `id_map.json` + `external_tags.json` (`KnownIds.fromDataRoot`) in `:core:test`, part of `check` (the folder is a test input), and passes with no skip. It fails on any unknown folder/kind/type/key, duplicate, tag cycle, or unresolved item/block/fluid/entity type/food trait id or tag. Ids/tags outside `tfp:` are accepted only when listed (see M1-DGN-02).

## Datagen v1 (tools/datagen)

- [x] **M1-DGN-01 TFC checkout pin & manifest** · deps: M1-BUILD-01 · skills: tfc-asset-migration
  - Do: CLI that clones/reads TFC at the commit pinned in the ledger, hashes its inputs and writes `content/.datagen-manifest.json`. The output must be deterministic (byte-identical).
  - Done when: two runs produce identical output; CI verifies `content/` is up to date.
  - Status: done. Output dir `content/generated/content` (jar `/content`). Determinism: unit test + two real runs diffed. CI gate: `./gradlew :tools:datagen:verifyContent` (wire into the workflow in M1-BUILD-03).

- [x] **M1-DGN-02 Data export** · deps: M1-DGN-01 · skills: tfc-feature-porting
  - TFC: `src/generated/resources/data/tfc/{tfc/**,recipe/**,tags/**}`
  - Do: rename `tfc:` → `tfp:`, maintain the item/block id mapping table, write `content/data/**` in TFC schemas.
  - Done when: M1-REG-04 passes.
  - Status: done. Export + `data/id_map.json` (blocks, items, fluids 133, entity types 131, food traits 10, `external_ids` for the vanilla/NeoForge ids used) + `data/external_tags.json` (16 NeoForge `c:` and 12 vanilla tags referenced but not defined in the checkout; 440 `c:` tag files TFC does define are exported). External lists come from running core's loader over the export. ContentDataLoadTest runs and passes; `verifyContent` passes, two runs byte-identical. External ids/tags are not verified against vanilla/NeoForge registries (not in the checkout).

- [x] **M1-DGN-03 Asset pipeline v1: rocks, soils, ores** · deps: M1-DGN-01 · skills: tfc-asset-migration, craftengine-config-generation, craftengine-blocks
  - TFC: `assets/tfc/{blockstates,models,textures}/{rock,ore,dirt,grass,clay,sand,...}`
  - Do:
    1. Copy textures and models with refs rewritten.
    2. **Flatten ore base+overlay into one PNG per ore×rock.**
    3. Emit literal CE YAML per id calling `tfp:` templates (author `tfp:settings/*`, `tfp:block_state/*`, `tfp:loot_table/*`).
    4. Generate lang for all locales.
  - Done when: the CE pack for these families loads with zero `Found N issue(s)` lines (checked by the self-test).
  - Status: 1353 cube blocks + templates + lang (13 locales) generated and statically checked; CE load not verified yet (self-test). Not emitted yet (listed in `reports/datagen-summary.json`): connected grass (multipart), slabs/stairs/walls, loose rocks, spikes, buttons, plates, anvils, aqueducts, rope anchors, small ores.
  - Code ready, server run pending: `ce.pack_issues` passes (0 `Found N issue(s)` lines for `tfp`/`tfpitest` from the live Log4j tap, and rock/soil/ore samples loaded) on the second start (the first creates `plugins/CraftEngine/resources`, so our pack is not installed then and the case skips).

  - Verified in the server (checks-1.21.11, 2026-10-16): the generated rock, soil and ore families install and load (1353 tfp blocks) with zero CraftEngine issue lines on four starts
- [x] **M1-DGN-04 Visual allocator with profiles** · deps: M1-DGN-03, M0-VIS-01 · skills: craftengine-visuals-pack
  - Do: implement profiles `level-0…level-3b` × MC version families. Sources, in order: CE `solid` → identity duplicates → (Nether/End) → absent-in-TFC (CSV allowlist). Also:
    - emit `block_state_mappings` that hide the repurposed blocks;
    - write allocation reports (used/free per pool and version);
    - CI fails on overflow.
  - Done when: reports exist for every profile; defaults (`1.21: level-3a`, `26: level-2`) leave ≥ the target reserve.
  - Status: done (datagen level). `tools/datagen` `dev.tfp.datagen.visuals` (`VisualAllocator`, `Profile`, `VersionFamily`, `VanillaSupply` over the per-state `visuals/fullcube-states.csv` exported by `tools/datagen/scripts/export_fullcube_states.py`, `HideTargets`, `VisualOutputs`, `AllocationReport`); 20 allocations (5 profiles × {1.21, 26} × Nether/End off/on) under `visuals/`, reports `reports/visual-allocation-<profile>-<family>.md`, `visuals/index.json`. Order: identity → Nether/End → absent clean → absent minor → solid (§9.1: non-pool first). Block entries now call `tfp:visual/<id>` templates (the old `tfp:block_state/cube|random_pillar` are gone). Free solid slots with the current demand (1353 blocks, 1437 appearances), N/E off (on): 1.21 level-0 509 (576), level-2 698 (765), **level-3a 950 (1017)**, level-3b 1202 (1269); 26 level-0 713 (780), **level-2 902 (969)**, level-3a 1154 (1221), level-3b 1406 (1473). `PinnedCheckoutTest.defaultProfilesLeaveTheSolidReserveFree` asserts ≥ 500 for both defaults; `VisualAllocatorTest` covers order, sharing, profiles, mappings, overflow. **Not verified in game:** CE expanding a template inside `appearances`, and the profile switch (M1-INST-03/M1-TEST-01). Gem-ore overlay renderer (level-2+) is not built yet, so gem ores currently look like raw rock.

- [x] **M1-DGN-05 Vanilla-block suppression data** · deps: M1-DGN-04
  - Do: list the repurposed vanilla blocks per profile so that our runtime (M1-INST-04) blocks their creation paths. Generate the datapack tag edits (ambient sounds, `#crystal_sound_blocks`, `#impermeable`).
  - Done when: the lists ship in the jar; the datapack loads.
  - Status: done (static). Per allocation `visuals/<family>/<profile>/nether-end-<off|on>/suppressed_blocks.json` (`suppress`: absent/Nether blocks with their states and `creation_paths` such as `lava_water`, `concrete_powder_water`, `copper_weathering_scraping_waxing`; `identity_duplicates`: need no suppression). Datapack `datapack/tfp_visuals/` (edits `#crystal_sound_blocks`, `#triggers_ambient_desert_dry_vegetation_block_sounds`, `#triggers_ambient_dried_ghast_block_sounds`; `#impermeable` needs no edit because no waterlogged state is claimed). Both are in the `:content` jar under `/content` (checked with `unzip -l`). `VisualContentCheck` (fixture + full checkout) parses every list and datapack file. **Not verified:** the datapack loading on a server (it is not registered yet: M1-INST-04 must discover `/content/datapack/tfp_visuals`), and that clients honour the synced tags.

## Installer & integrations

- [x] **M1-INST-01 Install manifest engine** · deps: M1-BOOT-01 · skills: dependency-bootstrap
  - Do: write to the CE `resources/tfp/` and BetterHud `packs/tfp/` folders. Hash manifest (untouched → overwrite; edited → `.tfp-new`; deleted → respect), atomic writes, opt-outs.
  - Done when: unit tests for all three cases pass.
  - Status: done at unit level. Pure rule `core/install/FileDecision` + `InstallManifest` (`<target>/.tfp-manifest.json`, sha256 + tombstones); `paper/install/ContentInstaller` (writes only changed files, temp + atomic move, deletes no-longer-shipped files only if untouched, cleans crash temps), `AtomicFiles`, `Installer` (called from `TfpPlugin.onLoad`; never creates `CraftEngine/resources/`; opt-outs `auto-install.craftengine|betterhud`, BetterHud `packs/-tfp`). Tests: `FileDecisionTest`, `ContentInstallerTest` (temp dir, incl. jar file system, failed write keeps the old file), `InstallerTest`. The jar ships no `/content/betterhud/tfp` yet (skipped with a log line). Triggering the CE pack rebuild after a change is M1-INST-03 (`InstallStatus.packRebuild` is computed). `/tfp reload content` still answers "not available" (needs INST-03's CE/BM reload). In-server run: M1-TEST-01.

- [x] **M1-INST-02 Config edits in CE/BH/BM** · deps: M1-INST-01 · skills: dependency-bootstrap
  - Do: comment-preserving single-key edits from the `dependency-bootstrap` §6 table:
    - CE: `delay-configuration-load: false`, `serverside-blocks` sizing, `supported-version.min`, `exclude-core-shaders: false`, disable `remove_shulker_head`;
    - BetterHud: remove `test_hud`, merge settings.
    Show a restart banner and degraded mode when needed.
  - Done when: the fresh-server self-test (first start → restart → ready) passes.
  - Status: unit level done (the fresh-server self-test moves to M1-TEST-01). `core/install/YamlKeyEditor` (line-based, comment-preserving single-key/list-item edits; refuses what it can't parse), `ThirdPartyEdits` (the §6 table: CE `delay-configuration-load`, `cache-system` (M0-CE-01), `storage-type` ≠ none, `serverside-blocks` sized from the datagen report, `supported-version.min` ≥ 1.21.4 (`server`/`latest` accepted), `exclude-core-shaders`, `merge-external-zip-files` keeps BetterModel, `remove_shulker_head` disabled; BetterHud `test_hud` removed, merge/pack-type/self-host; BetterModel `pack-type: zip`), `ConfigEdit` (owner's third value wins, recorded in `plugins/TerraFirmaPlugin/install/edits.json`), `InstallStatus.decide` (pure restart banner / degraded / pack rebuild). `paper/install/ThirdPartyConfigEditor` writes atomically and only if the result parses. Tests: `ThirdPartyEditsTest` (every key on fixtures copied from `spikes/run/paper-26.3/plugins/...`, trimmed, origin named), `YamlKeyEditorTest`, `InstallStatusTest`, `InstallerTest` (first start → restart banner → second start normal). Open: datagen reports no CE state count yet (`counts.ce_states`), so `serverside-blocks` is sized from `ce_blocks` (a lower bound); `chunk-system.blacklisted-worlds` is not checked; degraded mode has no consumer until the M2 generator.

- [x] **M1-INST-03 Visual profile selection & pack rebuild** · deps: M1-DGN-04, M1-INST-01
  - Do: pick the allocation for `visuals.profile` and the running version. Trigger a CE pack rebuild when content or profile changed. Check pool capacity at runtime (fail and name the higher profile, or warn below the reserve).
  - Done when: a self-test switches profiles across restarts without world changes.
  - Status: unit level done; the in-server self-test is open (M1-TEST-01), so this stays [ ]. `core/visuals`: `VisualSelection` (family from the running version via `Visuals.family`, `visuals.profile`, `use-nether-end-blocks` auto = neither vanilla dimension), `VisualIndex` (`visuals/index.json`), `ProfileCheck` (pure: fits / FAIL naming the next higher profile, preferring the first that fits / WARN below `solid-reserve-warning`), `PackBuild` (fingerprint of the installed CE pack + selection vs the last *successful* rebuild in `install/pack-build.json`; a profile switch → rebuild, `migrateWorldData` always false). `Installer` installs the selected allocation's `visual_templates.yml` + `visual_mappings.yml` as `CraftEngine/resources/tfp/configuration/visuals/` (missing allocation → failure → degraded); `status().packRebuild()` now comes from the fingerprint plan. `paper/install/PackRebuildTrigger` (on every `BlockVisuals.onReload`): pool check, one rebuild per run through `paper.integration.PackRebuilder`, fingerprint recorded after success. CE adapter `CraftEnginePackAdapter`: `PackRebuilder` = `/ce reload all` equivalent (`reloadPlugin(async, platform, true,true,true,false)` + `triggerWorkflows("reload_pack")`, read from CE 26.9.2 `ReloadCommand` bytecode, deferred until CE is not reloading/busy), `VisualPools` = `AutoStateGroup.SOLID` used/ours. Tests: `ProfileCheckTest` (9), `PackBuildTest` (7, incl. switch across restarts → rebuild, no migration), `VisualSelectionTest` (6, every shipped allocation exists), `InstallerTest` (+5: profile changed → rebuild requested once, failed rebuild retried, world file untouched, capacity check per reload). **Not verified in a server:** the CE reload/pack API calls, the pool counts, CE expanding the installed templates.

  - Verified in the server (checks2-1.21.11, Paper 1.21.11): fresh level-3a, then level-0, then level-3a: the level-3a figures match exactly (915 free, 35 used by other packs, 339 needed); level-0 shows 474 free and 780 needed; "Released 441" after the switch back; the world regions were not touched. Done by a manual run; not yet an automated self-test case.
- [x] **M1-INST-04 Vanilla-block suppression runtime** · deps: M1-DGN-05, M1-THR-01
  - Do: block/convert the creation of repurposed vanilla blocks in TFC worlds: `BlockFormEvent` (lava+water → stone/cobble/obsidian/basalt), concrete powder hardening, copper oxidation/waxing, vanilla recipes/loot producing them (via CE `disable-vanilla-recipes` list + our loot hooks), and dispensers/commands (best effort).
  - Done when: self-tests confirm no repurposed vanilla block can appear through the covered paths.
  - Status: unit level done; stays [ ] until the in-server self-test (M1-TEST-01) confirms it with real fluid/oxidation ticks. Pure rules `core/visuals/Suppression` + `SuppressionSet` (shipped `suppressed_blocks.json` + `visual_mappings.yml`): lava/water results → the state's look-alike (`stone`), never a repurposed block (fallback `stone`, else cancel); concrete hardening, copper weathering, scraping/waxing/entity changes, other forms (mud → clay), coral death, spread, bone meal, placement (incl. wet sponge in the Nether) → cancel; dispensers and `/setblock`/`/fill` (also after `execute … run`) best effort; `recipesToDisable` (the CE `disable-vanilla-recipes` list, computed and tested; **the config write itself is not wired**, the installer still has to apply it) and `suppressedItems`. `paper/suppression/SuppressionListener` (thin; registered in `TfpPlugin.onEnable` for the installed allocation): BlockForm/Spread/Fade/Fertilize, EntityChangeBlock, BlockPlace, BlockDispense, BlockDropItem + LootGenerate (strip), PrepareItemCraft + FurnaceSmelt, player/console commands. Every world counts as a TFC world until M2 marks them. Tests: `SuppressionTest` (19: one per rule, commands, recipes, and over all 20 shipped allocations every creation path is covered, every lava result becomes a non-repurposed block), `SuppressionListenerTest` (10, MockBukkit: lava→cobblestone/obsidian becomes stone in the TFC world, vanilla world untouched, powder/copper/coral/grass/bone meal/place/scrape/console setblock/loot). MockBukkit has no fluid physics: the test fires the `BlockFormEvent` the server would fire and applies it like `handleBlockFormEvent`. **Not covered:** `/clone`, `/place`, functions, structure blocks, other plugins; dispensed honeycomb waxing; lightning de-oxidation.

  - Verified in the server (checks2-1.21.11, Paper 1.21.11): lava source and flowing lava next to water become stone; two "suppress: BlockFormEvent ... lava -> stone" lines logged. Done by a manual run; not yet an automated self-test case.
- [x] **M1-INT-01 Integration adapter interfaces** · deps: M1-BUILD-01
  - Do: interfaces in `paper` (`BlockVisuals`, `CustomBlocks`, `CustomItems`, `EntityModels`, `HudBridge`) + implementations in `integration/*` + fakes for tests.
  - Done when: feature code compiles against interfaces only (enforced by an architecture test).
  - Status: `paper.integration.*` (interfaces, `BlockStateRef`, `HudSnapshot`/`HudSnapshotCache`, `Integration`/`IntegrationProvider` SPI, `Integrations` with degraded fallbacks, wired in `TfpPlugin`); adapters found by `ServiceLoader` (`:dist:verifyIntegrationServices` checks the merged service file). Fakes in `paper/src/test/.../integration/Fake*` (a `java-test-fixtures` source set broke `BootstrapTest`, which reads the datapack as a directory). `ArchitectureTest`: only `dev.tfp.integration..` imports `net.momirealms..`/`kr.toxicity..`, nothing outside it depends on an adapter, each adapter imports only its own plugin.

- [x] **M1-INT-02 CraftEngine adapter base** · deps: M1-INT-01 · skills: craftengine-java-api
  - Do: behaviour registration in `onLoad`, state cache refreshed on every `CraftEngineReloadEvent`, region-thread-only wrappers, listener priorities (≥ LOW on load, ≤ HIGH on unload), log appender detecting CE config issues for our pack.
  - Done when: self-test: our test block places, breaks, persists; zero CE issues logged.
  - Status: compiled, not runtime-verified. `integration/craftengine`: `CraftEngineIntegration` (behaviours via `CraftEngineBehaviours` in onLoad, state cache rebuilt on every `CraftEngineReloadEvent` + `CRAFTENGINE_RELOADED` signal, owner-thread-checked `stateAt/place/remove`, chunk hooks LOW/HIGH), `CraftEngineItemAdapter`, `CraftEngineIssueLog` over the pure `core.diagnostics.CraftEngineIssueMatcher` (unit-tested). Appender wiring deferred to M1-TEST-01: on Paper CE logs through SLF4J/Log4j (`Slf4jPluginLogger`, javap), so a JUL handler sees nothing and a Log4j appender needs log4j-core. Now wired in the self-test: `itest` `Log4jTap` (root Log4j appender, log4j-core compileOnly) feeds `LogFindings` with the same matcher (unit-tested, not run in a server yet). Open: the self-test (needs a test block in the CE pack).
  - Verified in the server: self-test: block place/break/persist passes; zero CE issues for our pack (matrix).

- [x] **M1-INT-03 BetterModel adapter base** · deps: M1-INT-01 · skills: bettermodel
  - Do: `ModelAssetsEvent` registration from the jar, a guarded subscriber wrapper (try/catch), `PluginEndReloadEvent` → ready, `CreateEntityTrackerEvent` re-apply hook.
  - Done when: the bovine example model spawns in the self-test.
  - Status: compiled, not runtime-verified. `integration/bettermodel`: `BetterModelIntegration` (guarded `ModelAssetsEvent`/`PluginEndReloadEvent`/`CreateEntityTrackerEvent` subscribers, Success → `BETTERMODEL_MODELS_READY`, Failure → waived), jar models from `bettermodel/models/index.txt` (placeholder `tfp_example_bovine` from the BM-01 spike). Unit tests: guard, jar index, service file. Open: the spawn self-test (M1-TEST-01).
  - Verified in the server: self-test: the bovine example spawns and its tint re-applies (matrix).

- [x] **M1-INT-04 BetterHud adapter base** · deps: M1-INT-01 · skills: betterhud
  - Do: placeholder/listener registration in `onEnable`, the per-player `HudSnapshot` cache (published from the player's thread), pack folder install.
  - Done when: a placeholder evaluates from the snapshot in the self-test with no Folia errors.
  - Status: compiled, not runtime-verified. `integration/betterhud`: `BetterHudIntegration` registers `tfp_health/max_health/thirst/hunger/held_temp` placeholders and `tfp_health/thirst/hunger` listeners in onEnable, all reading `HudSnapshotCache`; `BetterHudPackTarget` computes `plugins/BetterHud/packs/tfp/` (and the `-tfp` opt-out); writing is M1-INST-01. Not a ready-gate signal (§12 gate = CE ∧ BM ∧ registries). Open: the Folia self-test (M1-TEST-01).
  - Verified in the server: self-test: the BetterHud placeholder evaluates from the snapshot, no Folia thread errors (matrix).

## Core services

- [x] **M1-SVC-01 Block-data store** · deps: M0-CE-02, M1-INT-02
  - Do: implement the decision from M0-CE-02 (CE controller base class + our chunk-PDC store for chunk-scale data), with versioned codecs and migration tests.
  - Done when: persistence self-tests (unload/reload/restart) pass.
  - Status: done at unit level (as scoped for M1): `core/persist` (`VersionedCodec`, `DataMap`, `DataMapBinary`; `VersionedCodecTest`), `integration/craftengine/TfpBlockEntityController<T>` + `NbtDataMaps` (`TfpBlockEntityControllerTest`: save → CE NBT bytes → load, v1→v2 migration, newer/corrupt data kept verbatim), `paper/blockdata/ChunkDataStore` + `PdcChunkDataStore` (`ChunkDataStoreTest`: PDC round trip across a simulated restart, migration, in-memory fake). **The in-server unload/reload/restart self-test moves to M1-TEST-01.** No feature controller exists yet (the first is M3 crops/devices).

- [x] **M1-SVC-02 Player data store** · deps: M1-THR-01
  - Do: async load at pre-login and async save; a versioned record (thirst, nutrition, intoxication, chisel mode, ... added by features).
  - Done when: a join/quit/crash-safety self-test passes.
  - Status: done as a unit test with a temp dir (`PlayerDataStoreTest`: join/quit/rejoin/restart, quick rejoin waits for the pending save, crash mid-write leaves the old file, torn file → `.bak`, newer data refused and untouched, concurrent updates). `paper/player`: `PlayerData` (TFC `PlayerInfo` fields), `PlayerDataCodec` (v1), `PlayerDataFiles` (CRC32 frame, temp + atomic move, `.bak`), `PlayerDataStore`, `PlayerDataListener` (pre-login HIGHEST, quit MONITOR), autosave every 5 min in `TfpPlugin`. A live join/quit on a server is for M1-TEST-01.

- [x] **M1-SVC-03 Packet layer base** · deps: M0-NET-01
  - Do: the chosen packet library integration, a handler registry with netty-thread rules, creative-return stripping, metrics.
  - Done when: a no-op handler is installed and measured.
  - Status: code done, in-server proof missing. `paper/packet`: `PacketHandler` (Netty-thread rules in its doc), `PacketHandlerRegistry` (lock-free dispatch, per-handler metrics), `PacketEventsLayer` (one listener), `PacketLayers` (reflective, degrades without PacketEvents), `CreativeReturnHandler` + `ClientItemDecoration` hook, `NoopPacketHandler` (keep-alive) installed in `onEnable`; `/tfp debug packets` prints metrics. PacketEvents is now an optional dependency (`paper-plugin.yml`, `compat.yml`). `PacketHandlerRegistryTest` covers routing, decisions, metrics, errors, concurrent dispatch with fake contexts. **Missing:** a server run showing the no-op handler's metrics (M1-TEST-01; no servers were started here).
  - Verified in the server: self-test: a no-op PacketEvents handler is installed and measured (matrix).

- [x] **M1-SVC-04 i18n** · deps: M1-DGN-02
  - Do: server-side translations (our messages) + client lang via CE `lang:` (generated). Ship all TFC locales including `tr_tr`.
  - Done when: a self-test shows a translated message per locale.
  - Status: server side done: `paper/i18n/Messages` (`lang/<locale>.properties`, `lang/index.txt`, fallback same language → `en_us` → key, literal `{n}` arguments) with `en_us` and `tr_tr` for all our messages; `MessagesTest` shows a translated message per shipped locale and checks every bundle has every key with the same arguments; command replies are translated per sender (`BootstrapTest`). The client lang (block/item names from TFC's locales) is datagen's job (M1-DGN; CE `lang/*.json` already in the generated pack).

- [x] **M1-SVC-05 Commands & debug** · deps: M1-BOOT-01
  - Do: `/tfp reload [data|content]`, `/tfp debug showcase <family>` (places all variants in a grid), `/tfp debug block|player|chunk`, `/tfp version`.
  - Done when: the showcase works for rocks/soils/ores.
  - Status: `TfpCommands`: `/tfp [version]`, `/tfp reload [config|data|content]` (bare = data), `/tfp debug block|chunk|player [name]|packets`, translated replies; `TfpPlugin` implements them (`reload data` via `paper/data/RegistryService`, which also loads the registries at enable and signals `REGISTRIES_LOADED`; `reload content` answers "not available" until M1-INST). `BootstrapTest` covers the Brigadier tree, permissions, version output, every subcommand with a fake handler, and Turkish replies. **Missing:** `/tfp debug showcase <family>` (the Done-when), and in-server runs of the debug commands.
  - Code ready, server run pending: a player runs `/tfp debug showcase rocks|soils|ores` and sees every loaded variant in a grid (one row per type/ore grade, one column per rock or soil), checked against the plugin-testing visual checklist. Code: `core.showcase.Showcase` (family selection from the shipped `id_map.json`, grid layout; `ShowcaseTest`), `paper.debug.ShowcasePlacer` (one region task per chunk; `ShowcasePlacerMockBukkitTest`), Brigadier `debug showcase <family>` with suggestions (`BootstrapTest.debugShowcase`), en_us/tr_tr messages.

## Self-test harness

  - Verified in the server (checks-1.21.11, 2026-10-16): showcase rocks 217/217, soils 23/23, ores 1113/1113 placed (chat messages; screenshot of rocks viewed)
- [x] **M1-TEST-01 itest plugin** · deps: M1-BOOT-04 · skills: plugin-testing
  - Do: a case runner that waits on the ready gate, writes a JUnit XML report and shuts down. Fails on thread-assert exceptions in logs and on CE issues for our pack.
  - Done when: it runs in the CI matrix.
  - Status: plugin written and unit-tested, **no in-server run yet** (no servers were started here), so not done. `itest` (`dev.tfp.itest`, `paper-plugin.yml` `TerraFirmaPluginSelfTest`, inactive unless `-Dtfp.selftest=true`): `SelfTestPlugin` attaches `Log4jTap` (a Log4j appender on the server's root logger, `compileOnly log4j-core` 2.25.2; CE logs via SLF4J→Log4j so a JUL handler would miss it) in `onLoad`, falls back to scanning `logs/latest.log` if attaching fails; on `ServerLoadEvent(STARTUP)` a `tfp-selftest` thread runs `SelfTestSuite` through `CaseRunner` (per-case timeout + cancel, AssertionError = failure, other = error, a failed gate case skips later cases), adds two log cases from `LogFindings` (`ThreadAssertMatcher`: our new `ThreadAssertionException`, Folia/Paper "failed main thread check", AsyncCatcher; `core.diagnostics.CraftEngineIssueMatcher("tfp")`), writes `selftest-report.xml` (`JUnitXmlReport`, atomic) and shuts down from the global region thread. Cases: `ReadyCase` (gate opens within `-Dtfp.selftest.readySeconds`, default 180, of JVM start; M1-BOOT-04), `ConfigCase` (config.yml parses without warnings, every §6 edit present via read-only `ConfigEditsCheck`; a pending first-start restart is reported, not failed), `VersionCommandCase` (`/tfp version` via `Bukkit.createCommandSender` on the global thread), `PlayerDataCase` (fake UUID load → change → unload → reload, files removed), `RegionSchedulerCase` (region task on the owner thread; the self-test thread is not owner and `ThreadAsserts.owned` throws there). Skipped with a reason until verified in a server: CE test block place/break/persist (M1-INT-02, no test block in the pack yet), BetterModel bovine (M1-INT-03). Tests (21): `CaseRunnerTest` (outcomes, timeout, gate/skips), `JUnitXmlReportTest` (parse, counts, escaping, the `runSelfTest` failures/errors regex), `LogFindingsTest`, `Log4jTapTest`, `ReadyCaseTest`, `ConfigEditsCheckTest`, `SelfTestSuiteTest`. Open: run `:itest:runSelfTest` on the 4 targets; first start → restart → ready needs two server starts (runSelfTest does one); log lines written after the report (shutdown) are not checked.
  - Verified in the server: runs on the four targets; JUnit report, skip reasons and exit status verified by the local matrix (GitHub CI run still open in BUILD-03).

- [x] **M1-TEST-02 MockBukkit & unit test base** · deps: M1-BUILD-01
  - Do: test fixtures for adapters (fakes), MockBukkit setup for listeners/commands/PDC codecs.
  - Done when: sample tests pass in CI.
  - Status: done locally (`./gradlew check`; no Actions run). `org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.116.3` (jar manifest `Paper-Version: 1.21.11-R0.1-SNAPSHOT`, resolved through repo.papermc.io) as `paper` testImplementation; it pulls JUnit 6.0.3, so `:paper` tests run on the JUnit 6 platform (BOM-aligned). Base `paper/src/test/.../testing/MockServerTest` (fresh `ServerMock` + `PluginMock`, `offMainThread`), limits documented there. Samples: `PdcChunkDataStoreMockBukkitTest` (real PDC; off-main access throws `ThreadAssertionException`), `PlayerDataListenerMockBukkitTest` (async pre-login loads, `PlayerMock.disconnect` saves), `FakeCustomBlocksMockBukkitTest` (adapter fake on MockBukkit blocks). The adapter fakes (`Fake*` in `paper/src/test/.../integration`) are from M1-INT-01. Brigadier lifecycle commands are not covered by MockBukkit (`BootstrapTest` keeps its own seam).

## Exit criteria
Fresh server → our jar + CE/BM/BH → one automatic restart → ready. Rock/soil/ore blocks are placeable and showcase-visible with the default visual profile. CI matrix is green.
