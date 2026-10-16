# M0: Spikes

Goal: turn every "UNVERIFIED" that later work depends on into a measured answer. Each spike writes a short result note in `docs/spikes/<id>.md` (setup, result, numbers, decision) and updates `ARCHITECTURE.md` §14 and the affected skills. Spike code is throwaway: keep it in `spikes/<id>/` and don't let it leak into production modules.

Test servers: run-paper with CraftEngine 26.9.x, BetterModel 3.5.x and BetterHud 2.x, on Paper 26.3, Paper 1.21.11, Folia 26.3 (alpha) and Folia 1.21.11, all on Java 25 (`plugin-testing` → `references/harness-and-ci.md`).

## Tasks

- [x] **M0-ENV-01 Spike harness** · deps: none · skills: plugin-testing
  - Do: a minimal Gradle project under `spikes/harness/` with run-paper tasks for the 4 server targets, `downloadPlugins` for CE/BM/BH (+ PacketEvents), and the EULA property. A tiny test plugin with `paper-plugin.yml` (`load: STARTUP`, `folia-supported: true`, dependencies on the three plugins).
  - Done when: each target boots with all plugins enabled and no errors, documented in `docs/spikes/M0-ENV-01.md`.

- [x] **M0-CE-01 CraftEngine blocks from our ChunkGenerator** · deps: M0-ENV-01 · skills: craftengine-java-api
  - Do: follow the test plan in `craftengine-java-api` → report §9.6. A Bukkit `ChunkGenerator` on the default world writes `CraftEngineBlocks.getBukkitBlockData(state)` in `generateNoise` and in a `BlockPopulator` (including neighbour-chunk border writes). Use one marker block without a block entity and one with. CE `misc.delay-configuration-load: false`, `cache-system: true`.
  - Done when: markers are custom after generation, after unload/reload and after restart, on all 4 targets. The neighbour-write result with `cache-system: false` is recorded. The thread name recorded by the BE marker's controller is noted.

- [x] **M0-CE-02 Per-block data store decision** · deps: M0-CE-01 · skills: craftengine-java-api, paper-folia-threading
  - Do: implement a toy "crop" two ways: (a) CE `BlockEntityController` with a planted timestamp, no ticker; (b) our chunk-PDC store. Measure for 10k blocks/chunk area: load/save time, memory, behaviour across `/ce reload`, Folia region access, block replaced by vanilla means (piston, explosion, WorldEdit).
  - Done when: a decision is recorded in `ARCHITECTURE.md` §7 (expected: CE controllers for devices/player crops, properties-only for wild blocks) with the numbers.

- [x] **M0-VIS-01 Visual allocator prototype + in-game checks** · deps: M0-ENV-01 · skills: craftengine-visuals-pack, craftengine-config-generation
  - Do:
    1. Script the allocation for profiles `level-2` and `level-3a` from `docs/research/vanilla-fullcube-supply.csv` + the TFC demand script.
    2. Emit CE YAML for 1 rock × all variants, 3 ores × grades (flattened textures), and a button-state small ore. Include the `block_state_mappings` that hide the repurposed vanilla blocks.
    3. Check in a client: textures, hitboxes, sounds; button `powered` prediction flash; `redstone_ore` lit prediction; render layer on 1.21.11 vs 26.3.
    4. Read the CE pool capacities at runtime after `CraftEngineReloadEvent`.
  - Done when: the client checklist passes on 1.21.11 and 26.3, the runtime pool-count read works, and problem blocks are removed from the CSV allowlist.

- [ ] **M0-VIS-02 Exposed-only gem overlay** · deps: M0-VIS-01 · skills: craftengine-gui-furniture
  - Do: a gem ore whose visual equals the host rock. On exposure, place one `item_display` per exposed face in the air cell, translated back 0.01. Check lighting, z-fighting and packet count. Count exposed faces/chunk with vanilla caves as a proxy (re-measure with TFC worldgen in M2-GEN-15).
  - Done when: screenshots are OK at day, night and in caves, and the cost per exposed gem is documented.

- [ ] **M0-NET-01 Packet layer: biome remap + item lore** · deps: M0-ENV-01 · skills: paper-folia-threading
  - Do:
    1. Ship a datapack with 2 colour variants of a biome. Return it from a `BiomeProvider`.
    2. With PacketEvents, rewrite chunk biome palettes per "season" and measure µs/packet.
    3. Obtain biome network ids without NMS, or document the minimal NMS needed.
    4. Rewrite item lore and `custom_model_data` floats in outgoing item packets. Strip them on the creative return path.
    5. Find the order relative to CE's item interception.
    6. Record which thread runs the listeners.
  - Done when: a grass colour switch is visible after a chunk resend, the lore never persists into the server stack, and the timings are documented. Decide PacketEvents vs alternative.

- [ ] **M0-GUI-01 Outside-inventory button** · deps: M0-VIS-01 · skills: craftengine-gui-furniture
  - Do: CE `lang` override of `container.crafting` with a `minecraft:default` glyph (the `ui-inventory-tab-button` placeholder, see `image-generation` backlog). Check whether an empty-cursor click outside the player inventory sends a slot −999 click on 1.21.11 and 26.3 clients. Try the alternative trigger (`PlayerRecipeBookSettingsChangeEvent`). Check a textured chest GUI using the alignment math.
  - Done when: the chosen trigger works on both versions, a textured 6-row GUI aligns pixel-perfect at GUI scales 1–4, and the caveats (crafting table title, creative inventory) are documented.

- [ ] **M0-BM-01 BetterModel carrier & lifecycle** · deps: M0-ENV-01 · skills: bettermodel, model-authoring
  - Do: register `tools/models` output (bovine example + converted TFC deer) via `ModelAssetsEvent`. Spawn on a Cow carrier and on a Horse (mount). Check:
    - walk on pathfinding; `eat` override layering;
    - baby scale (`ModelScaler.entity()` vs attribute); hiding the carrier's equipment/name;
    - re-applying tint/part toggles in `CreateEntityTrackerEvent` after chunk reload and `/bm reload`;
    - rider seat height on the horse;
    - Folia tracker creation thread.
  - Done when: everything above is verified or a workaround is documented in the `bettermodel` skill.

- [ ] **M0-BH-01 BetterHud integration** · deps: M0-ENV-01 · skills: betterhud
  - Do: our `packs/tfp/` with health "45/50" + thirst bar fed by async-safe placeholders (from a per-player snapshot). Transparent vanilla heart/food/air sprites via our CE pack. CE pack rebuild after HUD changes.
  - Done when: the HUD shows on 1.21.11 and 26.3 at GUI scales 1–4, vanilla bars are hidden, the hotbar is kept, and there are no Folia thread errors.

- [ ] **M0-JAR-01 One jar for 1.21.11 + 26.x** · deps: M0-ENV-01 · skills: plugin-testing
  - Do: compile against the lowest Paper API with Java 25. Check: the `api-version` string, Adventure 4.x code running on 5.x, `ServerBuildInfo` version switch, and whether any NMS is needed (from M0-NET-01).
  - Done when: the same jar runs on all 4 targets.

- [x] **M0-ASYNC-01 AsyncDecisionPipeline under Folia** · deps: M0-ENV-01 · skills: paper-folia-threading
  - Do: a prototype of `AsyncDecisionPipeline` + `RegionCommitQueue` with a connected-block toy (fence-like). Stress it: 10k placements, explosions, piston chains across region borders. Measure the region-thread time per tick and the commit latency.
  - Done when: no thread violations on Folia, the per-region budget is respected, the stale-change re-queue works, and the numbers are documented.

## Exit criteria
All spikes are resolved, `ARCHITECTURE.md` §14 is updated with the decisions, and the affected skills are corrected (remove "UNVERIFIED" notes that were resolved).
