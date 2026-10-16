# CraftEngine deep dive: visual block states and resource-pack generation and delivery

> Note for this repo: researched at CraftEngine commit b3079c5 (v26.9.2) plus vanilla 1.21.4/1.21.11/26.1.2 client jars, 2026-10-16. Examples say `tfc:`; our provisional namespace is `tfp:` (see `CLAUDE.md`). `scratchpad/...` paths refer to the original research environment.

Researched 2026-10-16 against `Xiao-MoMi/craft-engine` at commit `b3079c5` (`project_version=26.9.2`). The local clone is `scratchpad/src/ce`. All repo paths below are relative to that clone.
Wiki: `https://xiao-momi.github.io/craft-engine-wiki/<page>/`. I fetched the pages listed in §11 on 2026-10-16.
Vanilla facts were checked against the official client jars for **1.21.4, 1.21.11 and 26.1.2**, downloaded from Mojang's piston-meta. I extracted only `assets/minecraft/blockstates/*`, and for 26.1.2 (which is unobfuscated) I used `javap` on a few classes.
This report extends `scratchpad/research/craftengine.md`. Read that first for the general model: real `craftengine:custom_N` server blocks, a visual vanilla state per CE state, and the hitbox taken from the visual state.

**Tags:**
- **[SRC]**: read in CE source (file + lines).
- **[CFG]**: a shipped config file.
- **[WIKI]**: wiki page.
- **[MC-JAR]**: checked in the vanilla client jar or assets.
- **[INF]**: my inference from source; reasoned, not run.
- **UNVERIFIED**: not confirmed.

Nothing was built or run.

---

## Table of contents
0. [TL;DR: budgets and hard rules](#0-tldr-budgets-and-hard-rules)
1. [block_state_mappings](#1-block_state_mappings)
   - 1.1 Semantics · 1.2 Syntax and version conditionals · 1.3 Conflict rules · 1.4 Effect on auto_state candidacy · 1.5 Per-block table of default mappings · 1.6 The installed file is never updated
2. [AutoStateGroup pools](#2-autostategroup-pools)
   - 2.1 Groups and candidate counts · 2.2 Allocation algorithm, order and stability · 2.3 Cache files and cleanup · 2.4 Exhaustion · 2.5 `{type, id}` sharing and the `transparent` convention
3. [Explicit `state:`](#3-explicit-state)
   - 3.1 Accepted syntax · 3.2 How the blockstate override JSON is generated · 3.3 Model-conflict rule · 3.4 Interaction with the sound-replacement system · 3.5 Variants-only vs multipart vanilla blockstates · 3.6 Client-side behaviour of the visual block (what to avoid) · 3.7 Render layer / transparency
4. [Item models (1.21.4+), legacy CMD, state-dependent item visuals](#4-item-models)
5. [Resource-pack pipeline](#5-resource-pack-pipeline)
   - 5.1 When the pack is built · 5.2 Generation order · 5.3 Merge order and sources · 5.4 Duplicated-file handlers · 5.5 pack.mcmeta, overlays, supported-version · 5.6 Validation · 5.7 Optimization · 5.8 Workflows (all step types) · 5.9 Reload commands · 5.10 API hooks
6. [Hosting and delivery](#6-hosting-and-delivery)
7. [Sounds, lang vs translations, atlases, particles, global variables](#7-sounds-lang-atlases-particles-global-variables)
8. [BetterModel, BetterHud and core shaders](#8-bettermodel-betterhud-and-core-shaders)
9. [Pitfalls](#9-pitfalls)
10. [TFC budget worksheet and recommended recipe](#10-tfc-budget-worksheet-and-recommended-recipe)
11. [Sources consulted](#11-sources-consulted)
12. [UNVERIFIED / open questions](#12-unverified--open-questions)

---

## 0. TL;DR: budgets and hard rules

**Full-cube, opaque visual pools** (default `mappings.yml`, fresh extraction):

| Pool | Size on 1.21.4–1.21.11 | Size on 26.1+ |
|---|---|---|
| `solid` | **1289** | **1489** |
| `note_block` | 1100 | 1300 |
| `mushroom` | 189 | 189 |

- `solid` draws from the same state objects as `note_block` and `mushroom`; it is not additive.
- Other pool sizes: leaves 117 (non-waterlogged) + 117 (waterlogged) on 1.21.4+, tripwire 126 (63 lower + 63 higher), cave_vines 48, kelp 25, weeping 25, twisting 25, sugar_cane 15, cactus 15, pressure_plate 28, sapling 8, chorus **0**. See §2.1.
- **Total default mappings:** 3738 (1.21.4), 4002 (1.21.11), 4202 (26.1–26.2), 4280 (26.3). Of these, **1900 / 2164 / 2164 / 2242** free states that belong to **no** auto group. You can use them only through an explicit `state:` (trapdoors, doors, fence gates, copper slabs/stairs/grates/bars/chains/lanterns/lightning rods, anvils, poplar leaves). See §1.5.

**Hard rules:**
1. A mapping you add yourself (`block_state_mappings`) creates an auto candidate **only** if the block belongs to an `AutoStateGroup` block set. Mapping `stone`, wool, ores and similar blocks frees those states for explicit `state:` and `block:N` shorthand, but never for `auto_state`. [SRC `AbstractBlockManager` L324–362]
2. Mappings are **one hop**. A real vanilla state X mapped to Y is sent to clients as Y. If Y itself carries a custom model (because Y is some custom block's visual), real X looks like that custom block. [SRC `BukkitNetworkManager.registerBlockStatePacketListeners` L379–401]
3. An explicit `state:` with a model writes `variants["<full property string>"]` into `assets/<ns>/blockstates/<block>.json` of the generated pack. That changes how **every** client-visible instance of that exact vanilla state looks. Vanilla's own blockstate file stays below it in the client's resource stack. The client merges per state, so states you did not override keep their vanilla look, **including multipart blocks**. [SRC `AbstractPackManager.generateBlockOverrides` L3405–3449; MC-JAR `BlockStateModelLoader.loadBlockStateDefinitionStack` in 26.1.2]
4. One vanilla state can carry exactly one variant JSON. A second, *different* JSON throws `resource.block.state.model_conflict`, and **that whole custom block fails to load**. The wiki says it is only "a conflict warning"; the code aborts the block. [SRC L940–955 and `ResourceConfigUtils.runCatching`]
5. Every vanilla state used as a visual silences its whole SoundType (place/break/step/hit) on the client. Door/trapdoor/fence-gate/button blocks also get their open/close/click sounds silenced. CE replays the sounds server-side. [SRC `BukkitBlockManager.processSounds` L571–640, `SoundSet`]
6. **CE does not build the pack at startup.** A pack is built only by `/ce reload pack`, `/ce reload all` or `/ce workflow <name>`. On join, players get the last uploaded zip. [SRC: `triggerWorkflows` is called only from `ReloadCommand` L58/L95; `runWorkflow` only from `PackWorkflowCommand`]
7. Merge precedence: **the first file written wins.** Order is CE pack `resourcepack/` folders (directory-listing order), then `merge-external-folders`, then zips (merge-external-zip-files plus cache-event zips; set order is undefined). Conflicting files without a matching handler are reported and the later file is dropped. CE then *generates* its own assets on top. [SRC L3762–3898, L769–850]
8. CE ships **no** `rendertype_text`/`text` core shader. Its only shader is `resources/remove_shulker_head/.../rendertype_entity_solid.fsh`, in an old pre-1.21.2 format, inside a pack that is **enabled** by default. Keep `exclude-core-shaders: false`, because `true` would delete BetterHud's shaders too. [SRC/CFG; §8]

---

## 1. block_state_mappings

### 1.1 Semantics
- Section key: `block_state_mapping` or `block_state_mappings` (`ConfigKeys.of("block_state_mapping(s)")`). It is valid in **any** configuration file of any enabled pack. [SRC `AbstractBlockManager` L294–296]
- Form: `before: after`. Both are vanilla block-state strings parsed with the NMS `BlockStateParser`, the same syntax as `/setblock`, with namespace optional.
  - An unparsable `before` or `after` gives the error `resource.argument.parser.blockstate` and that entry is skipped. [SRC L324–336; `BukkitBlockManager.createVanillaBlockState` L230–255]
- For each mapping [SRC L337–347]:
  1. `blockStateMappings[before.registryId] = after.registryId`. This int array drives the chunk, section-update and block-update packet rewriting. [SRC `BukkitNetworkManager` L379–420]
     - Unmapped vanilla ids map to themselves.
     - Custom `custom_N` ids map to their visual state. A custom id with no definition falls back to `minecraft:stone`.
     - Clients running the CE client mod get the raw ids (`newMappingsMOD`).
  2. `before` is appended to `blockStateArranger[ownerBlock]`. This list backs the `block:N` shorthand (§3.1), so **the index is the order in which mappings were parsed**.
  3. `autoVisualBlockStateCandidates[before] = createVisualBlockCandidate(before)`. This adds the state to every `AutoStateGroup` whose block set contains the block and whose predicate matches (§1.4).
- **Loading stage:** `LoadingStages.BLOCK_STATE_MAPPING`.
  - The block parser declares `dependencies() = [BLOCK_STATE_MAPPING, ITEM]`, so all mappings are known before any block is parsed. [SRC L383–386]
  - The mapping parser is **synchronous**, because `ConfigParser.async()` defaults to false, and it runs in config-storage order (pack order, then file order). [SRC `ConfigParser` L44, `SectionConfigParser.loadAll` L15–40]
- **Reload:** `unload()` clears the mapping array, the arranger, the candidates and the groups. `/ce reload` (config) therefore re-evaluates mappings, and the packet remapper is rebuilt in `delayedLoad()`. [SRC L150–167; `BukkitBlockManager.delayedLoad` L148–151]
  - Changed models still need a pack rebuild (`/ce reload all`).
  - Whether chunks already sent to online players get resent: **UNVERIFIED** (see `craftengine.md` §13.8).

### 1.2 Syntax and version conditionals
The whole file is one `block_state_mappings:` map. A version-conditional block is a key that starts with `$$`. Its children are **merged into the parent map** only when the server version matches. [SRC `StringKeyConstructor.processVersionedBlock` L104–112, `isVersionMatch` L177–195]

```yaml
# common-files/src/main/resources/resources/internal/configuration/mappings.yml L13–23
block_state_mappings:
  oak_sapling[stage=1]: oak_sapling[stage=0]
  # ...
  $$>=1.21.4#sapling:
    pale_oak_sapling[stage=1]: pale_oak_sapling[stage=0]
```
- **Accepted specs:**
  - `>=X`, `>X`, `<=X`, `<X`, an exact `X`, and `A~B` (inclusive).
  - `$$fallback` exists only for *value selectors*, that is maps whose keys are all `$$...`. [SRC L197–220]
- **The version is the server's MC version.** `VersionHelper.version` is `10000*major + 100*minor + patch`, so 26.1 is 260100 and 1.21.11 is 12111. [SRC `VersionHelper` L85–110, L131–163]
- **The `#suffix` only makes keys unique.** `parseVersionToInteger` skips non-digit characters except `.`. **Pitfall:** digits inside the suffix are *not* ignored. For example, `$$>=1.21.4#leaves2` parses as `1.21.42`. Use letter-only suffixes. [SRC `VersionHelper` L131–163]
- **Duplicate keys** (including keys produced by merging `$$` blocks) only log the warning `yaml.duplicated_key`, and the later value wins. [SRC `StringKeyConstructor.mergeMap`/`setValueWithDuplicationCheck` L131–175]
- **Conditional blocks used in the shipped file** [CFG `mappings.yml` L22, 599, 626, 1995, 2584, 2845, 3266, 3524, 3736, 4110, 4152, 4251, 4382, 4409]:
  - `>=1.20.3` for copper trapdoors, doors and grates.
  - `>=1.21.4` for pale_oak.
  - `>=1.21.9` for copper lightning rods, bars, chains and lanterns.
  - `>=26.1` for note_block trumpet instruments.
  - `>=26.3` for poplar leaves.

### 1.3 Conflict rules
- **Same `before`, different `after`:** the error `resource.mapping.conflict` ("Unable to map block state … because the state has already been mapped to …"). The **first** parsed mapping wins and the later one is skipped. [SRC L337–341; CFG `translations/en.yml` L285]
- **Same `before`, same `after`, parsed twice** (for example, your pack repeats an internal line): there is **no error**, but [INF from SRC L342–346]:
  - the state is appended to the arranger a second time, which shifts the `block:N` indices;
  - a **second `BlockStateCandidate` object** for the same state is added to the groups. The array keeps only the second one, so `assignFixedBlockState` marks only that one as used.
  - Result: the first candidate can still be auto-allocated, so two blocks can end up on one visual state, and the second one then hits `model_conflict`.
  - **Never duplicate mappings that the internal pack already has.**
- A mapping and an explicit `state:` on the same `before` state are compatible. That is the intended pattern: hide real instances, reuse the state as a visual.
- A mapping target (`after`) gets no special treatment. If you also give `after` a model (as some custom block's visual), real `before` blocks show that model (one-hop rule, §0.2).

### 1.4 Effect on auto_state candidacy
`createVisualBlockCandidate` calls `AutoStateGroup.findGroups(state)`, which uses `BY_BLOCKS` plus each group's predicate. If the result is empty, **no candidate is created**. [SRC L351–362; `AutoStateGroup` L126–157]

| You add a mapping for… | Becomes an auto candidate in | Usable via |
|---|---|---|
| `note_block[instrument=harp,…]`: the 50 harp states the default keeps | `note_block`, `solid` | auto + explicit |
| spruce/birch leaves (shipped **commented out**, L705–758) | `non_tintable_leaves`/`leaves` (+ waterlogged variants) | auto + explicit |
| `chorus_plant` (shipped commented out, L4490–4554) | `chorus` (default 0 → up to 63) | auto + explicit |
| the 2 tripwire states kept by default | `tripwire` + `lower/higher_tripwire` | auto + explicit |
| corals (commented out) | **none** (no coral group) | explicit only |
| `stone`, `granite`, ores, wool, concrete, terracotta, buttons, carpets, crops | **none** | explicit only (`state: minecraft:stone` or `stone:0`) |

The mapping's real job for plan (b) is **hiding real instances**. It is not needed to *use* a state: an explicit `state:` works on any vanilla state, mapped or not. [SRC `ConfigValue.getAsVanillaBlockState` L719–760]

### 1.5 Per-block table of every default mapping
How the table was produced:
- The counts come from evaluating every line of `common-files/src/main/resources/resources/internal/configuration/mappings.yml` (4554 lines; commented-out lines excluded) with CE's own version-matching logic, which I re-implemented in `scratchpad/scripts/mapcount.py` and `maptable.py`.
- Each "count" is the number of **freed** `before` states for that block.
- 1.21.4 and 1.21.11 differ only in the `>=1.21.9` copper blocks. 26.1 adds 200 note_block states. 26.3 adds poplar leaves.

| # | Block (`before`) | 1.21.4 | 1.21.11 | 26.1–26.2 | 26.3 | Sent to client as | Condition | mappings.yml lines |
|---|---|---|---|---|---|---|---|---|
| 1 | `anvil` | 2 | 2 | 2 | 2 | same block | — | 6–7 |
| 2 | `chipped_anvil` | 2 | 2 | 2 | 2 | same block | — | 8–9 |
| 3 | `damaged_anvil` | 2 | 2 | 2 | 2 | same block | — | 10–11 |
| 4 | `oak_sapling` | 1 | 1 | 1 | 1 | same block | — | 15–15 |
| 5 | `spruce_sapling` | 1 | 1 | 1 | 1 | same block | — | 16–16 |
| 6 | `birch_sapling` | 1 | 1 | 1 | 1 | same block | — | 17–17 |
| 7 | `jungle_sapling` | 1 | 1 | 1 | 1 | same block | — | 18–18 |
| 8 | `acacia_sapling` | 1 | 1 | 1 | 1 | same block | — | 19–19 |
| 9 | `cherry_sapling` | 1 | 1 | 1 | 1 | same block | — | 20–20 |
| 10 | `dark_oak_sapling` | 1 | 1 | 1 | 1 | same block | — | 21–21 |
| 11 | `pale_oak_sapling` | 1 | 1 | 1 | 1 | same block | >=1.21.4 | 23–23 |
| 12 | `brown_mushroom_block` | 63 | 63 | 63 | 63 | same block | — | 27–89 |
| 13 | `red_mushroom_block` | 63 | 63 | 63 | 63 | same block | — | 90–152 |
| 14 | `mushroom_stem` | 63 | 63 | 63 | 63 | same block | — | 153–215 |
| 15 | `kelp` | 25 | 25 | 25 | 25 | same block | — | 219–243 |
| 16 | `weeping_vines` | 25 | 25 | 25 | 25 | same block | — | 249–273 |
| 17 | `twisting_vines` | 25 | 25 | 25 | 25 | same block | — | 274–298 |
| 18 | `cave_vines` | 48 | 48 | 48 | 48 | same block | — | 299–347 |
| 19 | `sugar_cane` | 15 | 15 | 15 | 15 | same block | — | 352–366 |
| 20 | `cactus` | 15 | 15 | 15 | 15 | same block | — | 370–384 |
| 21 | `oak_leaves` | 26 | 26 | 26 | 26 | same block | — | 390–415 |
| 22 | `jungle_leaves` | 26 | 26 | 26 | 26 | same block | — | 416–441 |
| 23 | `acacia_leaves` | 26 | 26 | 26 | 26 | same block | — | 442–467 |
| 24 | `dark_oak_leaves` | 26 | 26 | 26 | 26 | same block | — | 468–493 |
| 25 | `mangrove_leaves` | 26 | 26 | 26 | 26 | same block | — | 494–519 |
| 26 | `azalea_leaves` | 26 | 26 | 26 | 26 | same block | — | 521–546 |
| 27 | `flowering_azalea_leaves` | 26 | 26 | 26 | 26 | same block | — | 547–572 |
| 28 | `cherry_leaves` | 26 | 26 | 26 | 26 | same block | — | 573–598 |
| 29 | `pale_oak_leaves` | 26 | 26 | 26 | 26 | same block | >=1.21.4 | 600–625 |
| 30 | `red_poplar_leaves` | 0 | 0 | 0 | 26 | same block | >=26.3 | 627–652 |
| 31 | `orange_poplar_leaves` | 0 | 0 | 0 | 26 | same block | >=26.3 | 653–678 |
| 32 | `yellow_poplar_leaves` | 0 | 0 | 0 | 26 | same block | >=26.3 | 679–704 |
| 33 | `tripwire` | 126 | 126 | 126 | 126 | same block | — | 764–889 |
| 34 | `note_block` | 1100 | 1100 | 1300 | 1300 | same block | >=26.1 | 895–2195 |
| 35 | `iron_trapdoor` | 32 | 32 | 32 | 32 | same block | — | 2199–2230 |
| 36 | `acacia_trapdoor` | 32 | 32 | 32 | 32 | same block | — | 2231–2262 |
| 37 | `oak_trapdoor` | 32 | 32 | 32 | 32 | same block | — | 2263–2294 |
| 38 | `spruce_trapdoor` | 32 | 32 | 32 | 32 | same block | — | 2295–2326 |
| 39 | `birch_trapdoor` | 32 | 32 | 32 | 32 | same block | — | 2327–2358 |
| 40 | `jungle_trapdoor` | 32 | 32 | 32 | 32 | same block | — | 2359–2390 |
| 41 | `dark_oak_trapdoor` | 32 | 32 | 32 | 32 | same block | — | 2391–2422 |
| 42 | `mangrove_trapdoor` | 32 | 32 | 32 | 32 | same block | — | 2423–2454 |
| 43 | `cherry_trapdoor` | 32 | 32 | 32 | 32 | same block | — | 2455–2486 |
| 44 | `bamboo_trapdoor` | 32 | 32 | 32 | 32 | same block | — | 2487–2518 |
| 45 | `crimson_trapdoor` | 32 | 32 | 32 | 32 | same block | — | 2519–2550 |
| 46 | `warped_trapdoor` | 32 | 32 | 32 | 32 | same block | — | 2551–2582 |
| 47 | `copper_trapdoor` | 32 | 32 | 32 | 32 | same block | >=1.20.3 | 2585–2616 |
| 48 | `exposed_copper_trapdoor` | 32 | 32 | 32 | 32 | same block | >=1.20.3 | 2617–2648 |
| 49 | `weathered_copper_trapdoor` | 32 | 32 | 32 | 32 | same block | >=1.20.3 | 2649–2680 |
| 50 | `oxidized_copper_trapdoor` | 32 | 32 | 32 | 32 | same block | >=1.20.3 | 2681–2712 |
| 51 | `waxed_copper_trapdoor` | 32 | 32 | 32 | 32 | same block | >=1.20.3 | 2716–2747 |
| 52 | `waxed_exposed_copper_trapdoor` | 32 | 32 | 32 | 32 | same block | >=1.20.3 | 2748–2779 |
| 53 | `waxed_weathered_copper_trapdoor` | 32 | 32 | 32 | 32 | same block | >=1.20.3 | 2780–2811 |
| 54 | `waxed_oxidized_copper_trapdoor` | 32 | 32 | 32 | 32 | same block | >=1.20.3 | 2812–2843 |
| 55 | `pale_oak_trapdoor` | 32 | 32 | 32 | 32 | same block | >=1.21.4 | 2846–2877 |
| 56 | `oak_door` | 32 | 32 | 32 | 32 | same block | — | 2881–2912 |
| 57 | `spruce_door` | 32 | 32 | 32 | 32 | same block | — | 2913–2944 |
| 58 | `birch_door` | 32 | 32 | 32 | 32 | same block | — | 2945–2976 |
| 59 | `jungle_door` | 32 | 32 | 32 | 32 | same block | — | 2977–3008 |
| 60 | `acacia_door` | 32 | 32 | 32 | 32 | same block | — | 3009–3040 |
| 61 | `dark_oak_door` | 32 | 32 | 32 | 32 | same block | — | 3041–3072 |
| 62 | `mangrove_door` | 32 | 32 | 32 | 32 | same block | — | 3073–3104 |
| 63 | `cherry_door` | 32 | 32 | 32 | 32 | same block | — | 3105–3136 |
| 64 | `bamboo_door` | 32 | 32 | 32 | 32 | same block | — | 3137–3168 |
| 65 | `crimson_door` | 32 | 32 | 32 | 32 | same block | — | 3169–3200 |
| 66 | `warped_door` | 32 | 32 | 32 | 32 | same block | — | 3201–3232 |
| 67 | `iron_door` | 32 | 32 | 32 | 32 | same block | — | 3233–3264 |
| 68 | `copper_door` | 32 | 32 | 32 | 32 | same block | >=1.20.3 | 3267–3298 |
| 69 | `exposed_copper_door` | 32 | 32 | 32 | 32 | same block | >=1.20.3 | 3299–3330 |
| 70 | `weathered_copper_door` | 32 | 32 | 32 | 32 | same block | >=1.20.3 | 3331–3362 |
| 71 | `oxidized_copper_door` | 32 | 32 | 32 | 32 | same block | >=1.20.3 | 3363–3394 |
| 72 | `waxed_copper_door` | 32 | 32 | 32 | 32 | same block | >=1.20.3 | 3395–3426 |
| 73 | `waxed_exposed_copper_door` | 32 | 32 | 32 | 32 | same block | >=1.20.3 | 3427–3458 |
| 74 | `waxed_weathered_copper_door` | 32 | 32 | 32 | 32 | same block | >=1.20.3 | 3459–3490 |
| 75 | `waxed_oxidized_copper_door` | 32 | 32 | 32 | 32 | same block | >=1.20.3 | 3491–3522 |
| 76 | `pale_oak_door` | 32 | 32 | 32 | 32 | same block | >=1.21.4 | 3525–3556 |
| 77 | `oak_fence_gate` | 16 | 16 | 16 | 16 | same block | — | 3559–3574 |
| 78 | `spruce_fence_gate` | 16 | 16 | 16 | 16 | same block | — | 3575–3590 |
| 79 | `birch_fence_gate` | 16 | 16 | 16 | 16 | same block | — | 3591–3606 |
| 80 | `jungle_fence_gate` | 16 | 16 | 16 | 16 | same block | — | 3607–3622 |
| 81 | `acacia_fence_gate` | 16 | 16 | 16 | 16 | same block | — | 3623–3638 |
| 82 | `dark_oak_fence_gate` | 16 | 16 | 16 | 16 | same block | — | 3639–3654 |
| 83 | `mangrove_fence_gate` | 16 | 16 | 16 | 16 | same block | — | 3655–3670 |
| 84 | `cherry_fence_gate` | 16 | 16 | 16 | 16 | same block | — | 3671–3686 |
| 85 | `bamboo_fence_gate` | 16 | 16 | 16 | 16 | same block | — | 3687–3702 |
| 86 | `crimson_fence_gate` | 16 | 16 | 16 | 16 | same block | — | 3703–3718 |
| 87 | `warped_fence_gate` | 16 | 16 | 16 | 16 | same block | — | 3719–3734 |
| 88 | `pale_oak_fence_gate` | 16 | 16 | 16 | 16 | same block | >=1.21.4 | 3737–3752 |
| 89 | `petrified_oak_slab` | 6 | 6 | 6 | 6 | `oak_slab` | — | 3755–3760 |
| 90 | `cut_copper_slab` | 6 | 6 | 6 | 6 | `waxed_cut_copper_slab` | — | 3761–3766 |
| 91 | `exposed_cut_copper_slab` | 6 | 6 | 6 | 6 | `waxed_exposed_cut_copper_slab` | — | 3767–3772 |
| 92 | `weathered_cut_copper_slab` | 6 | 6 | 6 | 6 | `waxed_weathered_cut_copper_slab` | — | 3773–3778 |
| 93 | `oxidized_cut_copper_slab` | 6 | 6 | 6 | 6 | `waxed_oxidized_cut_copper_slab` | — | 3779–3784 |
| 94 | `cut_copper_stairs` | 80 | 80 | 80 | 80 | `waxed_cut_copper_stairs` | — | 3787–3866 |
| 95 | `exposed_cut_copper_stairs` | 80 | 80 | 80 | 80 | `waxed_exposed_cut_copper_stairs` | — | 3867–3946 |
| 96 | `weathered_cut_copper_stairs` | 80 | 80 | 80 | 80 | `waxed_weathered_cut_copper_stairs` | — | 3947–4026 |
| 97 | `oxidized_cut_copper_stairs` | 80 | 80 | 80 | 80 | `waxed_oxidized_cut_copper_stairs` | — | 4027–4106 |
| 98 | `copper_grate` | 2 | 2 | 2 | 2 | `waxed_copper_grate` | >=1.20.3 | 4111–4112 |
| 99 | `weathered_copper_grate` | 2 | 2 | 2 | 2 | `waxed_weathered_copper_grate` | >=1.20.3 | 4113–4114 |
| 100 | `exposed_copper_grate` | 2 | 2 | 2 | 2 | `waxed_exposed_copper_grate` | >=1.20.3 | 4115–4116 |
| 101 | `oxidized_copper_grate` | 2 | 2 | 2 | 2 | `waxed_oxidized_copper_grate` | >=1.20.3 | 4117–4118 |
| 102 | `light_weighted_pressure_plate` | 14 | 14 | 14 | 14 | same block | — | 4122–4135 |
| 103 | `heavy_weighted_pressure_plate` | 14 | 14 | 14 | 14 | same block | — | 4136–4149 |
| 104 | `exposed_lightning_rod` | 0 | 24 | 24 | 24 | `waxed_exposed_lightning_rod` | >=1.21.9 | 4153–4176 |
| 105 | `lightning_rod` | 0 | 24 | 24 | 24 | `waxed_lightning_rod` | >=1.21.9 | 4177–4200 |
| 106 | `oxidized_lightning_rod` | 0 | 24 | 24 | 24 | `waxed_oxidized_lightning_rod` | >=1.21.9 | 4201–4224 |
| 107 | `weathered_lightning_rod` | 0 | 24 | 24 | 24 | `waxed_weathered_lightning_rod` | >=1.21.9 | 4225–4248 |
| 108 | `copper_bars` | 0 | 32 | 32 | 32 | `waxed_copper_bars` | >=1.21.9 | 4252–4283 |
| 109 | `exposed_copper_bars` | 0 | 32 | 32 | 32 | `waxed_exposed_copper_bars` | >=1.21.9 | 4284–4315 |
| 110 | `oxidized_copper_bars` | 0 | 32 | 32 | 32 | `waxed_oxidized_copper_bars` | >=1.21.9 | 4316–4347 |
| 111 | `weathered_copper_bars` | 0 | 32 | 32 | 32 | `waxed_weathered_copper_bars` | >=1.21.9 | 4348–4379 |
| 112 | `copper_chain` | 0 | 6 | 6 | 6 | `waxed_copper_chain` | >=1.21.9 | 4383–4388 |
| 113 | `exposed_copper_chain` | 0 | 6 | 6 | 6 | `waxed_exposed_copper_chain` | >=1.21.9 | 4389–4394 |
| 114 | `oxidized_copper_chain` | 0 | 6 | 6 | 6 | `waxed_oxidized_copper_chain` | >=1.21.9 | 4395–4400 |
| 115 | `weathered_copper_chain` | 0 | 6 | 6 | 6 | `waxed_weathered_copper_chain` | >=1.21.9 | 4401–4406 |
| 116 | `copper_lantern` | 0 | 4 | 4 | 4 | `waxed_copper_lantern` | >=1.21.9 | 4410–4413 |
| 117 | `exposed_copper_lantern` | 0 | 4 | 4 | 4 | `waxed_exposed_copper_lantern` | >=1.21.9 | 4414–4417 |
| 118 | `oxidized_copper_lantern` | 0 | 4 | 4 | 4 | `waxed_oxidized_copper_lantern` | >=1.21.9 | 4418–4421 |
| 119 | `weathered_copper_lantern` | 0 | 4 | 4 | 4 | `waxed_weathered_copper_lantern` | >=1.21.9 | 4422–4425 |
| | **TOTAL** | **3738** | **4002** | **4202** | **4280** | | | |

**Totals by family (fresh extraction):**

| Family | 1.21.4 | 1.21.11 | 26.1–26.2 | 26.3 | Auto group? |
|---|---|---|---|---|---|
| note_block (22 instruments × 25 notes × 2 powered; +4 trumpet instruments on 26.1) | 1100 | 1100 | 1300 | 1300 | note_block, solid |
| mushroom blocks (63 × 3; all six faces become all-true) | 189 | 189 | 189 | 189 | mushroom, solid, per-block |
| leaves (26 per type; distance/persistent → `distance=7,persistent=true`, waterlogged kept) | 234 (9 types) | 234 | 234 | 312 (+3 poplar) | leaves groups (poplar: **no group**) |
| tripwire (126 of 128; keeps `disarmed=true,…,all true` for attached true/false) | 126 | 126 | 126 | 126 | tripwire, lower/higher |
| plants: kelp 25, weeping 25, twisting 25, cave_vines 48 (age 25 commented out), sugar_cane 15, cactus 15 | 153 | 153 | 153 | 153 | own groups |
| saplings (stage 1 → 0) | 8 | 8 | 8 | 8 | sapling |
| weighted pressure plates (power 2–15 → 1) | 28 | 28 | 28 | 28 | pressure_plate |
| anvils (N≡S, E≡W) | 6 | 6 | 6 | 6 | none |
| trapdoors (powered≡unpowered, 32 each × 21 types) | 672 | 672 | 672 | 672 | none |
| doors (32 each × 21 types) | 672 | 672 | 672 | 672 | none |
| fence gates (16 × 12) | 192 | 192 | 192 | 192 | none |
| slabs (petrified_oak→oak 6, cut copper→waxed 4×6) | 30 | 30 | 30 | 30 | none |
| cut copper stairs → waxed (4×80) | 320 | 320 | 320 | 320 | none |
| copper grates → waxed (4×2) | 8 | 8 | 8 | 8 | none |
| copper lightning rods/bars/chains/lanterns → waxed (≥1.21.9) | 0 | 264 | 264 | 264 | none |
| **Total** | **3738** | **4002** | **4202** | **4280** | |

The vanilla looks that stay visible: note_block `instrument=harp` (all notes), 2 tripwire looks, `distance=7,persistent=true` leaves, the waxed copper variants, `oak_slab`, `stage=0` saplings, `power=1` plates, and `age=0` for the plants. [CFG mappings.yml comments L1–2, 4–5, 13–14, 25–26, 217–218, 245–248, 350–351, 368–369, 386–389, 760–763, 891–894, 2197–2198, 2879–2880, 2713–2715, 4108–4109, 4120–4121, 4427–4428, 4490–4491]

### 1.6 The installed `mappings.yml` is never updated
- Default packs are extracted only when `plugins/CraftEngine/resources/` does not exist. [SRC `AbstractPackManager` ctor L167–175, `loadPacks` L486–494]
- `saveResource` never overwrites an existing file, so `/ce resource save-default` is a no-op for existing files. [SRC `BukkitCraftEngine.saveResource` L493–500]
- **Consequence:** a server that first ran an older CE keeps the older `mappings.yml`. For example, it may lack the `>=26.1#note_block` block, in which case `solid` is 1289, not 1489, on 26.1.
- **Never hard-code pool sizes.** Read them at runtime: `AutoStateGroup.X.candidateCount()` after `CraftEngineReloadEvent`, or `/ce debug auto-state-usage <group>`. [SRC `AutoStateGroup.candidateCount` L87–89; `DebugAutoStateUsageCommand`]

---

## 2. AutoStateGroup pools

### 2.1 Groups and candidate counts
Source: `core/src/main/java/net/momirealms/craftengine/core/block/AutoStateGroup.java` L11–58, in **enum order**, which is also the allocation order (§2.2). The counts are computed from the default mappings (`scratchpad/scripts/groups.py`).

| # | Group id(s) | Vanilla blocks | Predicate | 1.21.4 / 1.21.11 | 26.1 / 26.3 | Hitbox / notes |
|---|---|---|---|---|---|---|
| 1 | `non_tintable_leaves`, `no_tint_leaves`, `leaves_no_tint` | azalea, flowering_azalea, cherry, pale_oak, spruce, birch leaves | waterlogged=false | 52 | 52 | spruce/birch not mapped by default, so the count is 39 before 1.21.4 |
| 2 | `waterlogged_non_tintable_leaves` (+ 2 aliases) | same | waterlogged=true | 52 | 52 | |
| 3 | `tintable_leaves` | oak, jungle, acacia, dark_oak, mangrove leaves | waterlogged=false | 65 | 65 | foliage-tinted by the client |
| 4 | `waterlogged_tintable_leaves` | same | waterlogged=true | 65 | 65 | |
| 5 | `leaves` | 11 leaf types (no poplar) | waterlogged=false | 117 | 117 | |
| 6 | `waterlogged_leaves` | same | waterlogged=true | 117 | 117 | |
| 7 | `lower_tripwire` | tripwire | attached=true | 63 | 63 | lower outline |
| 8 | `higher_tripwire` | tripwire | attached=false | 63 | 63 | |
| 9 | `note_block` | note_block | any | 1100 | 1300 | full cube |
| 10 | `brown_mushroom_block` | brown_mushroom_block | any | 63 | 63 | full cube |
| 11 | `red_mushroom_block` | red_mushroom_block | any | 63 | 63 | |
| 12 | `mushroom_stem` | mushroom_stem | any | 63 | 63 | |
| 13 | `tripwire` | tripwire | any | 126 | 126 | |
| 14 | `sugar_cane` | sugar_cane | any | 15 | 15 | no collision |
| 15 | `cactus` | cactus | any | 15 | 15 | 14×15×14 collision |
| 16 | `cave_vines`, `cave_vine` | cave_vines | any | 48 | 48 | climbable |
| 17 | `weeping_vines`, `weeping_vine` | weeping_vines | any | 25 | 25 | climbable |
| 18 | `twisting_vines`, `twisting_vine` | twisting_vines | any | 25 | 25 | climbable |
| 19 | `kelp` | kelp | any | 25 | 25 | waterlogged by nature |
| 20 | `chorus` | chorus_plant | any | **0** | **0** | default mappings commented out |
| 21 | `pressure_plate` | light/heavy weighted plates | any | 28 | 28 | |
| 22 | `sapling` | 8 saplings | any | 8 | 8 | |
| 23 | `mushroom` | 3 mushroom blocks | any | 189 | 189 | |
| 24 | `solid` | 3 mushroom blocks + note_block | any | **1289** | **1489** | |

- **Groups share candidate objects.** A state allocated through `note_block` is no longer available to `solid` or `mushroom`, and the reverse also holds.
- **Real distinct budgets:** full cube 1289/1489; leaves 234 (117 dry + 117 waterlogged); tripwire 126; plus the individual plant pools.
- **Wiki vs code:** the wiki table says non_tintable covers "Spruce/Birch/Cherry/Pale Oak/Azalea". The default mappings do **not** free spruce/birch, so those contribute 0 until you un-comment L705–758. [WIKI `configuration/block/states`; CFG]

### 2.2 Allocation algorithm, order and stability
[SRC `VisualBlockStateAllocator` L82–155; `AbstractBlockManager.BlockParser` L388–439, L629–662]

1. **Cache key per appearance:**
   - `auto_state: <group>` uses `ns:id` for a single-state block, or `ns:id[appearance=<name>]`.
   - `auto_state: {type: <group>, id: X}` uses `<groupPrimaryId>[id=X]` (for example `solid[id=transparent]`).
   - `{type}` without `id` behaves like the short form.
   - `type` defaults to `solid` in the map form. [SRC L632–647]
2. During parsing, each appearance calls `requestAutoState(cacheKey, group)`. The same key returns the same future, which is how sharing works. The request is queued per group (`pendingAllocationFutures[group.ordinal()]`).
3. **When the requests happen.** Appearance parsing runs inside the `whenCompleteAsync` continuation of the internal-id allocation, on the async executor. `postProcess` joins those futures first, so every `requestAutoState` and `assignFixedBlockState` happens before `processPendingAllocations()`. [SRC L388–422, L558–664]
4. `processPendingAllocations()`:
   - **(a) Cached entries first.** For each `name → state` in `visual_block_states.json`:
     - If the candidate exists, is not used, and the pending request with that name satisfies its group, it is reused.
     - If **nobody requested that name this load**, the state is still marked **used**, so the reservation is kept.
     - Used or missing candidates are ignored.
   - **(b) The rest.** In `AutoStateGroup` enum order (table above), and within each group in request order, the next unused candidate is taken. The group pointer walks candidates in mapping-parse order.
5. Explicit `state:` calls `assignFixedBlockState`, which marks the candidate used immediately. Explicit states therefore always beat auto allocation, even beat cached auto entries.
   - In that case the cached owner is reallocated: (a) sees the candidate as used, so the request falls through to (b).

**Stability:**
- Allocations are stable *through the cache*.
- The **first** allocation for new blocks is **not reproducible across installs**. Request order depends on async task completion when `misc.multi-threaded-configuration-load: true`.
- **[INF]** For reproducible visuals, ship or seed `plugins/CraftEngine/cache/visual_block_states.json` ourselves. Format: a JSON object `{ "<cacheKey>": "<block state string>" }`, sorted by registry id. CE re-reads it on the next load only if its mtime changed. [SRC L191–246]

### 2.3 Cache files and cleanup
- `plugins/CraftEngine/cache/visual_block_states.json`: auto visual allocations. [SRC `AbstractBlockManager` ctor L130]
  - Written only when dirty.
  - Deleted when empty.
- `plugins/CraftEngine/cache/custom_block_states.json`: internal `custom_N` ids (IdAllocator). [SRC L129]
- **Stale entries keep reserving their state forever** (step 4a). Removing blocks does not free pool slots. Free them with:
  - `/ce clean-cache visual_block_states`, which removes cache entries whose state is not the visual of any loaded block. [SRC `CleanCacheCommand` L73–92]
  - Other cache types: `custom_block_states`, `custom_model_data`, `font`/`images`, `all` (default). [SRC L44–66]
- Edge case:
  - An appearance switched from auto to explicit removes the cache entry `ns:id:appearance`, not `ns:id[appearance=…]` (different separators). [SRC L657 vs L642]
  - **[INF]** The old auto reservation therefore survives until `clean-cache`. Single-state blocks use `ns:id` for both forms, so they are unaffected.

### 2.4 Exhaustion
- If a group has no unused candidate, the future fails with `StateExhaustedException`.
- The block reports `resource.block.state.visual_state_exhausted`: "The visual state group '<group>' has reached its maximum capacity of '<candidateCount>' slots and cannot allocate a state for '<cacheKey>'". [SRC `VisualBlockStateAllocator` L139–153, `AbstractBlockManager` L664–677; CFG `translations/en.yml` L311]
- **The whole block (all appearances) is not registered.** The holder stays an `InactiveBlockDefinition`.
- Real-state exhaustion is a different error: `real_state_exhausted`. Fix it by raising `block.serverside-blocks` (restart required). [SRC L558–566; CFG `config.yml` L377–380]

### 2.5 `{type, id}` sharing and the `transparent` convention
- Appearances (in any block) with the same `{type, id}` get the **same** vanilla state. The key includes the group's *primary* id, so `{type: solid, id: x}` and `{type: note_block, id: x}` are different keys. [SRC L638–641]
- If shared appearances specify models, the models must be byte-identical JSON (§3.3). The intended use is `transparent: true` plus `entity_renderer`:

```yaml
# WIKI configuration/block/states ("The transparent convention")
state:
  auto_state: { type: sugar_cane, id: transparent }   # one state backs unlimited blocks
  transparent: true                                     # variant -> minecraft:block/empty
  entity_renderer: { type: item_display, item: tfc:anvil_display }
```
- `transparent: true` writes the variant `{"model":"minecraft:block/empty"}`. CE generates `assets/minecraft/models/block/empty.json` (zero-size element) and a 16×16 empty texture. [SRC `AbstractBlockManager` L62, L694–697; `AbstractPackManager.generateEmptyBlockModel` L3199–3225]
- **The hitbox is still the visual state's** (§0, `craftengine.md` §6). With `sugar_cane` you get **no collision**.
- **[INF] Full-collision rare blocks:** use an explicit `state: minecraft:barrier` (2 states, by `waterlogged`). Barrier has a full collision box, is invisible and does not occlude, so neighbours are not culled into x-ray holes.
  - CE special-cases barrier visuals: they restore to `minecraft:glass` on unload. [SRC `BukkitBlockManager` L374–380]
  - Do **not** use a `solid` or note_block state with `transparent: true`. Those visuals `canOcclude`, so neighbour faces are culled and you see through the world.
- **CE also offers `type: better_model`** entity_renderer elements when BetterModel is installed:
  - keys: `model`, `position`, `yaw`, `pitch`, `sight_trace`, `conditions`. [SRC `bukkit/compatibility/.../bettermodel/BetterModelBlockEntityElementConfig.java` L75–90; `BukkitCompatibilityManager` L212–217; WIKI `configuration/block/states/entity_renderer`]
  - Entity culling for renderers is **Premium only**. [WIKI same page]

---

## 3. Explicit `state:`

### 3.1 Accepted syntax
[SRC `ConfigValue.getAsVanillaBlockState` L715–763; WIKI `configuration/block/states`]

| Form | Example | Notes |
|---|---|---|
| Full state | `minecraft:note_block[instrument=hat,note=0,powered=false]` | Any vanilla state, mapped or not. Unspecified properties take the block default (vanilla parser). |
| Namespace omitted | `anvil[facing=east]`, `stone` | |
| Shorthand `block:N` / `ns:block:N` | `note_block:0`, `minecraft:stone:3` | The N-th **mapped** `before` state of that block, in mapping-parse order. Errors with `resource.block.state.unreleased_state` when the block has no mappings or N is too large. N must be ≥ 0. |
| Custom state | `ns:block[prop=val]` resolves through `BlockStateParser` first | It returns the custom NMS state, but appearance code calls `getAsVanillaBlockState` → `createBlockState` (custom first). **Do not** use custom states as visuals. [SRC `BukkitBlockManager.createBlockState` L221–228] |

- **Recommendation:** in generated configs, always emit **full state strings**. `block:N` indices shift whenever mappings are added, removed, reordered or duplicated (§1.3) or when packs load in a different order (§5.3).
- A state with no `model`/`texture`/`blueprint`/`transparent` writes **no** override. The visual keeps the current look (vanilla, or another block's model). Example: `hami_melon.yml` uses `state: pumpkin_stem[age=N]` without a model. [CFG `default_assets/configuration/...`; `craftengine.md` §4.5]
- Model forms per appearance [SRC L694–736, L915–1038]:
  - `model: path`
  - `model: {path, generation:{parent, textures, display, gui_light}}`
  - `model: {path, textures:[…]}` / `texture: x` / `textures: [...]`. Texture count picks the parent: 1 = cube_all, 2 = cube_column, 3 = cube_bottom_top, 4 = orientable, 5+ = six faces; a `^` prefix marks the particle texture.
  - `models: [{path, weight, x, y, z, uvlock}]`: weighted list → variant array.
  - `blueprint: file.bbmodel`
  - `transparent: true`
- Rotations must be multiples of 90, otherwise `invalid_rotation`.
- `z` is accepted and written into the variant. No vanilla 1.21.4/1.21.11/26.1.2 blockstate uses `"z"` [MC-JAR grep]. **UNVERIFIED** that clients honour it; avoid it.

### 3.2 How the blockstate override JSON is generated
1. **At parse time**, `arrangeModelForStateAndVerify(state, variantJson)`:
   - splits `state.getAsString()` (for example `minecraft:note_block[instrument=hat,note=0,powered=false]`) into block id and the **full** property string (`instrument=hat,note=0,powered=false`; `""` for property-less blocks);
   - stores it in `blockStateOverrides[blockId][propertyString]`. [SRC L940–955]
2. **At generation time**, `generateBlockOverrides` runs for each block id. [SRC `AbstractPackManager` L3405–3449]
   - It reads the merged pack's existing `assets/<ns>/blockstates/<block>.json`, if a CE pack, external folder or zip supplied one.
   - It **removes `multipart`** and keeps the existing `variants`.
   - It appends or overwrites CE's variants (new keys sorted; existing keys replaced in place).
   - It writes `{"variants": {...}}`.
   - It writes only to the root `assets/`, never to overlays.
3. With `map_compatibility: true` on the `generate` step, vanilla overrides are skipped and only `assets/craftengine/blockstates/custom_N.json` is written, for map renderers and mods. `resource-pack.generate-mod-assets: true` writes both. [SRC L814–819, L3451–3471]

**Why partial variant files work** [MC-JAR 26.1.2 `net.minecraft.client.resources.model.BlockStateModelLoader`, javap]:
- `loadBlockStates` uses `FileToIdConverter.listMatchingResourceStacks`, which returns all packs' copies of `blockstates/<block>.json`.
- `loadBlockStateDefinitionStack` iterates the stack and calls `BlockStateModelDispatcher.instantiate(...)` for each copy, then `putAll` into one `IdentityHashMap<BlockState, …>`.
- Higher packs therefore override **per state**. Vanilla's `multipart` (lower in the stack) still defines every state you did not override. 1.21.4–1.21.11 behaviour is assumed identical (obfuscated jar, not disassembled): **UNVERIFIED but very likely**, since CE's default `solid` pool already overrides multipart mushroom blocks this way.
- **Consequence:** the "multipart dropping" only destroys multipart definitions that **another merged pack** shipped for the same block (for example a texture pack's custom fence). Vanilla's multipart is unaffected.

### 3.3 Model-conflict rule
- Each `(block, propertyString)` slot holds one variant `JsonElement`. A second registration that is not `equals()` (Gson structural equality: model path + x/y/z/uvlock/weight, or the same weighted array) throws `KnownResourceException("resource.block.state.model_conflict", …)`. [SRC L948–953]
- It is thrown inside `ResourceConfigUtils.runCatching`, so **the rest of that block's registration is skipped**: no behaviour, no `byId` entry. [SRC `ResourceConfigUtils` L14–36; `AbstractBlockManager` L664, L683–851]
  - Appearances processed earlier in that block may already have written overrides.
- Identical JSON is allowed. Many CE blocks can share one explicit vanilla state *and* model, for example every TFC "raw granite" variant that looks the same.
- A model generated with `generation:` when the target model file already exists in the merged pack: CE logs `resource_pack.model_generation.conflict` and **keeps the existing file**. [SRC `generateModels` L3368–3380; CFG en.yml L225]

### 3.4 Interaction with the sound-replacement system
[SRC `BukkitBlockManager.processSounds` L571–640; `AbstractPackManager.generateOverrideSounds` L3292–3365; `SoundSet` L13–43; `SoundListener` L55–80; `BlockEventListener` L100–125, L188, L289, L324; CFG `config.yml` L382–391]

- **Input set:** `tempVisualBlockStatesInUse`, i.e. every visual state of every loaded custom block (explicit or auto). Mapping targets are **not** included.
- **Per visual state:**
  - Its `SoundType`'s place/break/step/hit event ids are mapped to `replaced.<id>`.
  - If the visual block belongs to a `SoundSet` (wooden/nether/bamboo/cherry/copper trapdoors and doors, wooden/nether/bamboo/cherry fence gates, all buttons), its open/close (click on/off) events are mapped too.
- **Pack (when `block.sound-system.enable: true`):**
  - `assets/minecraft/sounds.json` gets `"<original>": {"replace": true, "sounds": ["internal/silence"]}`.
  - The original definitions are copied from CE's internal `internal/sounds.json` to `"replaced.<original>"`.
  - `silent-sound-path` (default `internal/silence.ogg`) is written to `assets/minecraft/sounds/`.
- **Runtime:**
  - Server-sent sound packets with a replaced id are rewritten to `replaced.*` (`SoundListener`).
  - Client-predicted place/break/step/hit sounds for **vanilla** blocks whose SoundType is silenced are replayed by the server (`BlockEventListener`). `process-cancelled-events.step/break` controls whether that also happens for cancelled events.
  - Custom blocks play their `settings.sounds`.
- **TFC impact:**
  - Using `minecraft:stone` (SoundType `STONE`), wool (`WOOL`), concrete or terracotta (`STONE`) as visuals silences the client-side sounds of **every** vanilla block with that SoundType. CE replays them server-side, adding latency.
  - That is acceptable, but expect a much larger replay load than with note_block (`WOOD`).
  - Pick visual families that share few SoundTypes, and remember that a *single* use is enough to silence a whole SoundType.

### 3.5 Variants-only vs multipart vanilla blockstates
**Checked in the client jars** (1.21.4, 1.21.11, 26.1.2) [MC-JAR; script `scratchpad/scripts/bs.py`]: 96 files are multipart (on 1.21.11/26.1.2), and everything else is variants-only.

Per §3.2, overriding a single state is technically fine for **both** kinds. The difference that matters is what the **unoverridden states** look like and how many states a block has.

Multipart in vanilla (complete list, 26.1.2):
- all `*_fence` (12 wood + nether_brick);
- all `*_wall` (21);
- `glass_pane` + 16 `*_stained_glass_pane`;
- `iron_bars`;
- all copper bars (8, ≥1.21.9);
- all `*_shelf` (12, ≥1.21.9);
- `brown_mushroom_block`, `red_mushroom_block`, `mushroom_stem`, `chorus_plant`, `vine`, `glow_lichen`, `sculk_vein`, `resin_clump`;
- `fire`, `soul_fire`, `redstone_wire`, `brewing_stand`, `composter`, `chiseled_bookshelf`, `bamboo`;
- `pale_moss_carpet`, `pink_petals`;
- `leaf_litter`, `wildflowers` (≥1.21.5).

Variants-only, verified for the TFC repurpose candidates (variant-key counts are equal on all three versions):

| Family | Blocks | Client states (= keys unless noted) |
|---|---|---|
| Stone family | stone, granite, polished_granite, diorite, polished_diorite, andesite, polished_andesite, cobbled_deepslate, polished_deepslate, tuff, calcite, dripstone_block, smooth_stone, stone_bricks, cracked/chiseled/mossy_stone_bricks, cobblestone, mossy_cobblestone, deepslate_bricks/tiles, cracked_deepslate_bricks/tiles, chiseled_deepslate, infested_stone/cobblestone/stone_bricks, smooth_basalt, blackstone, polished_blackstone, sandstone, red_sandstone (1 each); deepslate, infested_deepslate, basalt, polished_basalt (3, axis) | 44 |
| Ores | coal, iron, copper, gold, emerald, lapis, diamond ores, their deepslate variants, nether_gold, nether_quartz, ancient_debris, raw_iron/copper/gold_block (1 each); redstone_ore and deepslate_redstone_ore (1 key `""` but **2 states**, `lit`) | 22 keys (24 states) |
| Wool / concrete / concrete powder | 16 each | 48 |
| Terracotta | 17 | 17 |
| Glazed terracotta | 16 × 4 facing | 64 |
| Stained glass | 16 (translucent layer, see §3.7) | 16 |
| Carpets | 16 dyed + moss_carpet (1 each). **pale_moss_carpet is multipart**, avoid it. | 17 |
| Buttons | 12 wood + stone + polished_blackstone, 24 each (face×facing×powered) | 336 |
| Plates | 12 wood + stone + polished_blackstone, 2 each (`powered`) | 28 |
| Crops | wheat 8, carrots 8, potatoes 8, beetroots 4, melon_stem 8, pumpkin_stem 8, attached_melon_stem 4, attached_pumpkin_stem 4, nether_wart 4, torchflower_crop 2, pitcher_crop 10, sweet_berry_bush 4, cocoa 12 | 84 |
| Misc | note_block (1 key `""` for 1150/1350 states), tripwire (32 partial keys for 128 states), barrier (1 key, 2 states), structure_void (1), light (16 keys, 32 states) | |

- **Partial keys** (redstone_ore, tripwire, barrier, light, note_block): CE always writes **full** property keys, so overriding one state leaves the sibling states vanilla.

### 3.6 Client-side behaviour of the visual block (what to avoid)
The client treats a CE block exactly as its visual vanilla state: physics, prediction, particles and the occlusion flag. CE patches only note_block and tripwire prediction (`isClientSideNoteBlock`/`isClientSideTripwire` flags) and the interact sounds (§3.4). [SRC `BukkitBlockManager.applyPlatformSettings` L374–375; `BlockGenerator` L68–82, L214; CFG mappings.yml comments L246–248, L891–894]

The following is **[INF, vanilla knowledge, UNVERIFIED in game]**:

- **Avoid as visuals:**
  - ice, packed_ice, blue_ice (client friction);
  - slime and honey (bounce, stickiness);
  - soul_sand (client speed factor);
  - cobweb and sweet_berry_bush (slowdown);
  - powder_snow, scaffolding, ladders;
  - vines/kelp-like climbables, unless intended (CE comment L246);
  - cherry and pale_oak leaves: they spawn petal/leaf particles client-side. Tinted-leaf particles on 1.21.5+ are removed by `remove-tinted-leaves-particle`.
- **`redstone_ore` / `deepslate_redstone_ore`:** the client runs `stepOn`/`attack`/`use` → `interact`, which spawns particles and *predicts* `lit=true`. If you override only `lit=false`, stepping on it shows vanilla lit redstone ore until a block update arrives. Override both states with the same model, or skip these blocks.
- **Buttons:** `ButtonBlock.useWithoutItem` → `press()` runs on the client too (prediction). Right-clicking locally switches to `powered=true` and plays the click sound. CE silences click sounds (SoundSet), but the **powered=true variant** may flash.
  - Safe pattern: give `powered=false` and `powered=true` of the same face/facing the **same model**. That halves the button budget to 168 distinct looks.
- **Concrete powder:** client neighbour-shape updates next to water can locally predict concrete. Edge case; prefer concrete or wool.
- **Crops:** a bone-meal particle prediction is harmless. `sweet_berry_bush` slows players client-side: avoid.
- **Hitbox and shape** come from the visual on both sides:
  - button: tiny;
  - carpet / moss_carpet: 1/16;
  - pressure plate: none (outline flat);
  - crops: no collision, crop outline by age;
  - full cubes for the stone, ore, wool, concrete and terracotta families.
  - `craftengine.md` §6 has the full discussion.
- **Light:** client light comes from the client state. `light[level=N]` *emits* light client-side; the CE light system handles server/client mismatches (`block.light-system`). **UNVERIFIED** how they combine.

### 3.7 Render layer / transparency
- **26.1+:**
  - The chunk render layer is chosen **per quad from the texture's transparency** (`BakedQuad$MaterialInfo.of → ChunkSectionLayer.byTransparency(Transparency)`). [MC-JAR 26.1.2 javap]
  - Cutout and translucent textures therefore render correctly on any visual block.
  - Occlusion still follows the vanilla block (`canOcclude`), so a see-through texture on a `stone` visual exposes culled neighbour faces (x-ray).
- **≤1.21.11:**
  - The layer is per *block* (vanilla `ItemBlockRenderTypes`): stone/note_block/mushroom/wool = solid; leaves = cutout_mipped; glass/grate = cutout; stained glass = translucent; plants = cutout.
  - CE's own comments agree: leaves "are some of the few block types that actually support transparent textures" and the copper grate is "Suitable for making glass because it is completely transparent". [CFG mappings.yml L388, L4109]
  - For transparent TFC blocks on 1.21.x, use leaves/grate/glass-like visuals, or `transparent` + entity_renderer.
  - Not disassembled (the jar is obfuscated): **UNVERIFIED** for 1.21.11 specifically.

---

## 4. Item models

### 4.1 Which ids CE writes
[SRC `AbstractItemManager` L345–366, L540–660, L760–890; WIKI `configuration/item/models`]

- **`item_model`** (assets/<ns>/items/<id>.json):
  - Written for custom items when the server is ≥1.21.2 **and** `supported-version.max` ≥1.21.2.
  - The value is the item id (or the explicit `item_model:`) when CMD is 0 or `item.always-use-item-model: true` (default true).
- **`custom_model_data`** (int) is auto-allocated only if `supported-version.min` < 1.21.2 or `item.always-use-custom-model-data: true` (default false).
  - Allocation range: from `item.custom-model-data-starting-value` (default 10000, per-material overrides) up to 16,777,216.
- When CMD > 0, CE also writes a vanilla-item override `assets/minecraft/items/<material>.json`:
  - `range_dispatch` on `custom_model_data` **index 0** (floats[0]), falling back to the vanilla model. [SRC `generateModernItemOverrides` L3583–3648]
  - **CE then owns floats[0]** on that material.
- **Legacy `overrides`** (pre-1.21.4): generated when `min` < 1.21.4 or `always-generate-model-overrides: true`, by automatically down-converting the modern tree. [SRC L840–870]
  - Only properties implementing `LegacyModelPredicate` convert: range `damage`, `custom_model_data`, `crossbow/pull`, `use_duration`; select `trim_material`, `main_hand`, `charge_type`; condition `broken`, `damaged`, `fishing_rod/cast`, `using_item`.
  - Others fail with `resource.item.model_definition.downgrade_failure` unless you supply `legacy_model:`. [SRC `pack/model/definition/**`, grep `LegacyModelPredicate`]
- **Existing file wins:** an existing `assets/<ns>/items/<id>.json` in the merged pack produces `resource_pack.item_model.conflict`, and CE skips generation. [SRC L3548–3561]
- **For us:** set `supported-version.min` ≥ 1.21.4. Then there is no CMD, no legacy overrides, and the CMD component is free for our own per-stack data.

### 4.2 Model tree types and properties (CE YAML mirrors vanilla)
[SRC `ItemModels`, `RangeDispatchProperties`, `SelectProperties`, `ConditionProperties`, `Tints` registries; WIKI pages under `configuration/item/models/*`]

- **types:**
  - `model` (`path`, `generation`, `blueprint`, `tints`, `transformation` (26.1+))
  - `composite` (`models: [...]`, rendered last-to-first)
  - `condition` (`property`, `on_true`, `on_false`)
  - `select` (`property`, `cases: [{when, model}]`, `fallback`)
  - `range_dispatch` (`property`, `scale`, `entries: [{threshold, model}]`, `fallback`)
  - `special`, `empty`, `bundle/selected_item`
- **range_dispatch properties:** `bundle/fullness`, `compass`, `cooldown`, `count`, `crossbow/pull`, `damage`, `time`, `use_cycle`, `use_duration`, `custom_model_data` (floats[index]).
- **select properties:** `block_state`, `charge_type`, `component`, `context_dimension`, `context_entity_type`, `display_context`, `local_time`, `main_hand`, `trim_material`, `custom_model_data` (strings[index]).
- **condition properties:** `broken`, `bundle/has_selected_item`, `carried`, `component`, `damaged`, `extended_view`, `fishing_rod/cast`, `has_component`, `keybind_down`, `selected`, `using_item`, `view_entity`, `custom_model_data` (flags[index]).
- **tints:** `constant`, `dye`, `firework`, `grass`, `map_color`, `potion`, `team`, `custom_model_data` (colors[index]). The list position is the `tintindex`. Colour formats: decimal, `r,g,b` 0–255, or floats.
- **Root extras:** `hand_animation_on_swap`, `oversized_in_gui`, `swap_animation_scale`.
- **Simplified forms:** `texture:`, `textures: [...]` (material-specific slot order), `models: [...]`, `blueprint:`.

### 4.3 State-dependent item visuals (e.g. TFC heat glow)
Our plugin writes the CMD component on the stack using Paper's data-component API, for example floats[0] = heat 0..1 and colors[0] = an RGB glow. The CE model tree reads it:

```yaml
# Pattern built from WIKI configuration/item/models/{range_dispatch,composite,model}; syntax per SRC registries
items:
  tfc:metal/ingot/copper:
    material: paper
    model:
      type: minecraft:range_dispatch
      property: minecraft:custom_model_data
      index: 0                 # floats[0] = normalized temperature written by our plugin
      entries:
        - threshold: 0.0
          model: { path: tfc:item/metal/ingot/copper }
        - threshold: 0.25      # "warm" and above: base + tinted glow overlay
          model:
            type: minecraft:composite
            models:
              - path: tfc:item/heat_glow           # top layer (earlier = on top)
                tints:
                  - type: minecraft:custom_model_data
                    index: 0                        # colors[0] set by our plugin
                    default: 0xFF4000
              - path: tfc:item/metal/ingot/copper
      fallback: { path: tfc:item/metal/ingot/copper }
```

Rules for this pattern:
- Keep CE from allocating CMD for these items (min ≥ 1.21.2, `always-use-custom-model-data: false`). Otherwise CE puts its int in floats[0] and adds a range_dispatch on the *material's* item model.
- `client_bound_data`/`client-bound-model` (packet-side component injection) is **Premium only**, so server-side stack data is the community path. [SRC `AbstractItemManager` L630–647; CFG config.yml L293–306]
- Changing floats re-sends the item. That is fine for inventory updates, but keep update frequency low.
- **UNVERIFIED:** whether model elements' `light_emission` gives a true "glow" in GUI and hand on 1.21.x/26.x. Tinting works regardless.

---

## 5. Resource-pack pipeline

### 5.1 When the pack is built
- **Only** by `/ce reload pack`, `/ce reload all` or `/ce workflow <name>`. [SRC `ReloadCommand` L50–105, `PackWorkflowCommand` L24–40]
  - `reload pack` and `reload all` run every workflow whose `trigger` contains `reload_pack`; `PackWorkflowSequence.RELOAD_PACK` is the only trigger CE fires.
  - `/ce workflow <name>` runs one workflow regardless of trigger.
- No build at server start.
  - `initCachedAssets()` at startup only fires `AsyncResourcePackCacheEvent` and pre-reads files into the memory cache. [SRC `CraftEngine` L532, `AbstractPackManager.initCachedAssets` L448–457]
  - Self-hosted packs reload the zip from `storage_path` at startup. [WIKI `set_up_host/self`; SRC `SelfHostHttpServer.load` L224–231]
- Workflows hold the `resourceOperations` lock. A concurrent reload returns "busy". [SRC L962–979; `ReloadCommand` L37–40]

### 5.2 Generation order (`generate` step)
[SRC `generatePackAssets` L769–850] All work happens in an **in-memory Jimfs** at `resource_pack/`:
1. `AsyncResourcePackCacheEvent` is fired synchronously on the calling (async) thread. Listeners add external paths. [SRC `BukkitPackManager` L42–44, `EventUtils.fireAndForget`]
2. **Merge** (`updateCachedAssets`), §5.3. Duplicates are logged.
3. `generateFonts` (`images:`, emoji, offset chars; `override-uniform-font`).
4. `generateModels` for items, then blocks. An existing model or texture is kept and a warning is logged.
5. `generateEmptyBlockModel`, only if a `transparent` appearance or a model-less visual is used.
6. Item definitions: `generateModernItemModels1_21_2` (only when the pack spans 1.21.2–1.21.3), `generateModernItemModels1_21_4`, `generateLegacyItemOverrides`, `generateModernItemOverrides`.
7. `generateOverrideSounds` (§3.4), then `generateCustomSounds` (`sounds:` → `assets/<ns>/sounds.json`, merged into the existing file; CE keys win). [SRC L3259–3290]
8. `generateClientLang` (`lang:` → `assets/minecraft/lang/<locale>.json`; existing keys kept, CE keys override, sorted). [SRC L3227–3257]
9. `generateEquipments`, then `generateParticle` (empties `minecraft:tinted_leaves` particle textures when `remove-tinted-leaves-particle: true` and max ≥ 1.21.5) [SRC L2748–2779], then `generateAtlases` (`atlases:` config merged into existing atlas JSON) [SRC L2781–2801].
10. `generateBlockOverrides` (§3.2).
11. Read `pack.mcmeta` (merged), add revision overlays, optionally `removeAllShaders` (`exclude-core-shaders`), rewrite the `pack` section (`validatePackMetadata`).

### 5.3 Merge order and sources
[SRC `updateCachedAssets` L3762–3796, `processRegularFile` L3798–3821, `processZipFile` L3823–3868, `updateConflictChecker` L3870–3898; `PackCacheData`]

| Order | Source | Resolution |
|---|---|---|
| 1 | Every **enabled** CE pack's `resourcepack/` plus enabled subpacks' `subpacks/<id>/resourcepack/`, in the order of `loadedPacks` | Directory-stream order of `plugins/CraftEngine/resources/`. **Filesystem-dependent, not sorted.** [SRC `loadPacks` L496–553] |
| 2 | `resource-pack.merge-external-folders` plus cache-event folders | Paths relative to **`plugins/`** (`dataFolder.getParent()`). Missing paths are silently skipped. Collected into a `Set`, so order among them is undefined. |
| 3 | `resource-pack.merge-external-zip-files` plus cache-event zips | Must be regular files ending in `.zip`. Same `Set` caveat. |

- **Defaults** [CFG config.yml L87–95]:
  - `merge-external-folders: ["ModelEngine/resource pack"]`
  - `merge-external-zip-files: ["CustomNameplates/resourcepack.zip", "BetterModel/build.zip"]`
  - `exclude-file-extensions: [md, psd, bbmodel, db, ini, DS_Store, zip, gz, tar, jar]`
  - `cache-resource-files: true` (in-memory cache keyed by mtime+size; zip entries keyed by the zip's mtime+size)
- **The first writer of a relative path wins.** Each later file with the same path is offered to the first matching `duplicated-files-handler` rule (§5.4). With no match, it is recorded and later printed under `resource_pack.duplicated_files`, and **the later file is dropped**.
- CE-generated assets (§5.2) are written **after** merging and usually win or merge, except for models, textures and item definitions, where an existing file wins.
- Symlinks are followed. Folders whose names start with `.` are skipped as packs.

### 5.4 Duplicated-file handlers
[SRC `pack/conflict/**`; CFG config.yml L235–292; WIKI `reference/file_conflict`]

- **Matching:** each rule is `{term: <matcher>, resolution: <resolution>}`, and the first match wins.
  - The matcher tests the **relative** pack path (for example `assets/minecraft/items/x.json`).
  - The resolution receives `(existing file in Jimfs, conflicting source)`. For zip entries, the source path is the path *inside the zip*, so `retain_matching` + `contains` cannot see the zip's file name.
- **Matchers:**
  - `any_of{terms}`, `all_of{terms}`, `inverted{term}`
  - `exact{path}`, `filename{name}`, `contains{path}`, `pattern{pattern}` (regex)
  - `parent_path_prefix{prefix}` and `parent_path_suffix{suffix}`: the code reads `prefix`/`suffix`. The wiki examples show `path`, which is wrong. [SRC `ParentPrefixPathMatcher` L25, `ParentSuffixPathMatcher` Factory]
- **Resolutions:**
  - `merge_json{deeply}`: deep merge; the later file wins on scalar or array conflicts, nested objects merge.
  - `merge_legacy_model`
  - `merge_pack_mcmeta`: combines `overlays.entries` (by directory), `filter.block`, `features.enabled`, `language` and unknown keys. `pack` is regenerated later.
  - `merge_atlas`: union of `sources`, de-duplicated.
  - `merge_font`
  - `retain_matching{term}`: replaces the existing file if the *conflicting* file matches.
  - `conditional{term, resolution}`
- **Default rules:** `minecraft/models/item` → merge_legacy_model; `minecraft/items` → deep merge_json; `pack.mcmeta` → merge_pack_mcmeta; `pack.png` → keep CE default_assets' copy; any `sounds.json` → shallow merge (later wins per event key); `minecraft/atlases` → merge_atlas; `minecraft/lang` → shallow merge; `font/default.json` + `font/uniform.json` → merge_font.
- **Not covered by default:**
  - **blockstates**: first wins; CE then merges its variants and drops multipart;
  - shaders: the wiki says shader merging is unsupported;
  - textures, models, other fonts.

### 5.5 pack.mcmeta, overlays, supported-version
[SRC `validatePackMetadata` L1075–1123; `Overlays`, `Overlay.getAsOverlayEntry` L44–62; CFG config.yml L70–85]

- **`supported-version.min`/`max`:** a version string, `latest`, or `server` (defaults: `min: server`, `max: latest`). CE writes `pack_format` + `supported_formats` when min is below 1.21.9 (format change, 25w31a), and `min_format`/`max_format` when max ≥ 1.21.9.
  - **Pitfall:** with `min: server` on a 1.21.11 or 26.x server, ViaVersion clients older than the server version are outside the declared range.
- **Overlay folder name:** `overlay-format: "ce_overlay_{version}"` (the code default is `overlay_{version}`; it must contain `{version}`). CE adds revision overlays (item models, equipment) and validation fix overlays (atlases, model fallbacks).
- **Overlays from merged packs survive:** `merge_pack_mcmeta` combines entries, and validation reads them. Ship our own version-specific assets as an overlay directory plus an `overlays.entries` item in our `resourcepack/pack.mcmeta`. [INF from SRC]
- **Subpacks are not overlays.** `pack.yml` `subpacks:` (with `$$` version keys) select *which folders are merged at all*, based on the **server** version. [`craftengine.md` §3.1]

### 5.6 Validation (`validate` step, `resource-pack.validation.*`)
[SRC `validateResourcePack` L1520–1656, `validateResourcePackWithOverlays` L1672–2350; CFG L104–121; en.yml L228–238, L355]

- Runs per version segment: overlay combinations across min/max. Packs spanning 1.21.11 are split into a legacy and a modern segment.
- **Logs (does not fail):**
  - missing font, equipment, model or parent textures/models;
  - missing `.ogg` for sound events;
  - `missing_block_model` for blockstate variants;
  - `missing_item_model`;
  - `texture_not_in_atlas`;
  - `multiple_atlases` (a model mixing block- and item-atlas textures; matters on 1.21.11+, which has a separate items atlas);
  - `duplicated_sprite`, `bad_sprite`;
  - UV out of bounds (26.1+ can fail to bake).
- `fix-atlas: true`: adds out-of-atlas textures to `blocks.json`/`items.json` through generated overlays (written into root `assets/` when the segment includes ≤1.20.1). Disabled when obfuscation is on.
- `fix-missing-texture`, `fallback-models.fix-textures-format` (the 26.1 textures-map format for older clients), `fix-element-rotation-angle` (22.5° steps before 1.21.6, ±45° before 1.21.11), `fix-model-uv-out-of-bounds` (default false).
- **Advice (WIKI `getting_start/item_models`):** keep textures under `textures/block/` and `textures/item/`, power-of-two sizes, lowercase paths. Block models should use only block-atlas textures, and item models only item-atlas textures.

### 5.7 Optimization (`optimize` step)
[SRC `optimizeResourcePack` L1201–1463; CFG L123–144]
- PNG re-encode (`texture.enable`, `zopfli-iterations` 0 = off, `exclude` prefixes).
- JSON/mcmeta minify (`json.enable`, `exclude`).
- Result cache `cache-size` MiB in memory plus `cache/pack-optimization.bin`.
- Per-pack exclusions through a config section `skip_optimization: {texture: [...], json: [...]}` in any CE config file. [SRC `SkipOptimizationParser` L3974–4030]

### 5.8 Workflows (all step types)
[SRC `pack/workflow/**`; CFG L21–39; WIKI `getting_start/workflows`, `workflows/multi_pack`]

```yaml
resource-pack:
  workflows:
    <name>:                 # [A-Za-z0-9_-]+, non-empty steps
      trigger: reload_pack  # string or list; omit = manual only (/ce workflow <name>)
      steps:                # each item: map with `type`, or a bare string = {type: <string>}
        - type: generate          # once per workflow; options: description, map_compatibility
        - type: validate          # needs generated/loaded, unzipped pack
        - type: optimize          # same
        - type: export            # path: dir (wiped & rewritten; cannot be the plugin dir or a parent)
        - type: packsquash        # executable (default "packsquash" on PATH), config (TOML, required), timeout (s, default 1800)
        - type: load_zip          # path: replaces the current pack; allows validate/optimize/zip again
        - type: zip               # path, store-png (false), protection (Premium only; ignored otherwise)
        - type: upload            # pack|host: <pack id>, path: zip; host must canUpload (not external/self_forward/none)
        - type: send_pack         # pack|host: <pack id>; resends to online players who have that pack selected
```

- Ordering is validated at load (`PackWorkflowValidation`):
  - `generate` at most once;
  - `validate`/`optimize`/`zip`/`export` need a generated or loaded, not-yet-zipped pack;
  - `upload` needs a host that supports uploads.
- Paths are relative to `plugins/CraftEngine`.
- `zip` writes to a temp file and atomically moves it, then fires `AsyncResourcePackGenerateEvent(jimfsPackPath, zipPath)`. [SRC L1046–1051, L866–881]
- **Note:** the legacy `resource-pack.pack-squash.*` keys are still read by `Config` (L466–468) but unused by the workflow system. Use the `packsquash` step instead.

### 5.9 Reload commands
[SRC `ReloadCommand`; `CraftEngine.reloadPlugin` L230–340; WIKI `getting_start/item_models` "Run /ce reload all"]

| Command | Effect |
|---|---|
| `/ce reload` = `/ce reload config` | Re-reads config.yml/translations, re-scans packs, reparses all configs except recipes, rebuilds mappings, allocations and the sound map, and re-registers packet remappers. **No pack build.** |
| `/ce reload recipe` | Config plus recipes |
| `/ce reload pack` | Runs `reload_pack` workflows with the **currently loaded** configs |
| `/ce reload host` | Config reload plus reloading `resource-pack.packs` and `self-host` |
| `/ce reload all` | Config + recipes, then `reload_pack` workflows |
| `/ce workflow <name>` | One workflow |
| `/ce feature send-pack` | Resend packs |

- After our plugin rewrites YAML or assets, use **`/ce reload all`**. With the API: `CraftEngine.instance().reloadPlugin(...)`, then `packManager().triggerWorkflows("reload_pack")` (internal API, as `ReloadCommand` does).

### 5.10 API hooks
[SRC `bukkit/.../api/event/AsyncResourcePackCacheEvent.java`, `AsyncResourcePackGenerateEvent`]
- **`AsyncResourcePackCacheEvent#registerExternalResourcePack(Path)`:**
  - takes a directory or a `.zip`, and throws `IllegalArgumentException` otherwise;
  - must be called **on every firing**: startup cache warm-up and every `generate`;
  - the cache only refreshes when size or mtime change.
- **`AsyncResourcePackGenerateEvent`** fires after the zip is written. The pack path is the Jimfs path, so edits there do not change the already written zip.

---

## 6. Hosting and delivery

### 6.1 Packs (`resource-pack.packs.<id>`)
[SRC `AbstractPackManager.loadHosts` L883–917, `ResourcePackHosts`, `host/impl/*`; WIKI `set_up_host/*`]

- `<id>` must match `[A-Za-z0-9_-]{1,64}`.
- Keys: `type`, `default` (true = enabled when the player has no saved preference). Configuration order is send order.

| `type` | Keys (code) | Notes |
|---|---|---|
| `self` | `storage_path` (default `./cache/hosted/<id>/resource_pack.zip`) | Served by the built-in HTTP server (`self-host`). Upload copies the zip there. Reloaded on restart. |
| `external` | `url` (required), `sha1`, `uuid` (default: name-UUID of the url) | No upload. Manual updates. |
| `lobfile` | `api_key` or `use_environment_variables` (`CE_LOBFILE_API_KEY`), `cache_file_name` | |
| `mcpacks` | `cache_file_name` | Free, no account (uploads to `mcpacks.dev`). |
| `s3` | `endpoint`, `bucket`, `access_key_id`, `access_key_secret`, `use_environment_variables` (`CE_S3_ACCESS_KEY_ID/SECRET`), `protocol`, `path_style`, `region`, `upload_path`, `disable_calculate_sha256`, `validity` (s), `rate_map{max_requests,reset_interval}`, `timeout{connect,socket,api_call,api_call_attempt,connection_acquisition}`, `cdn{domain,protocol}` | Implementation is in the separate artifact `craft-engine-s3` (`core/build.gradle.kts` L26). The wiki marks it **"Paid / Advanced User"**: UNVERIFIED whether the Community build includes it. |
| `openlist` / `alist` | `api_url`, `username`, `password`, `file_password`, `otp_code`, `jwt_token_expiration` (h, 48), `upload_path`, env (`CE_OPENLIST_*`/`CE_ALIST_*`), `cache_file_name` | |
| `onedrive` | `client_id`, `client_secret`, `refresh_token` (or env `CE_ONEDRIVE_*`), `upload_path` (`resource_pack.zip`), `cache_file_name` | |
| `gitlab` | `gitlab_url`, `access_token` (or `CE_GITLAB_ACCESS_TOKEN`), `project_id`, `cache_file_name` | |
| `dropbox` | `app_key`, `app_secret`, `refresh_token` (or `CE_DROPBOX_*`), `upload_path`, `cache_file_name` | |
| `self_forward` | `server`, `secret`, `pack` | Asks another CE server's self-host (`GET <server>/forward/<pack>`, header `secret`) for a per-player link; pairs with `self-host.forward_secret`. No upload. |
| `none` | none | |

- The wiki also documents per-pack `dependencies: [...]` / `conflicts: [...]` (`workflows/multi_pack`). These keys are **not read anywhere** in this commit's source (grep): **UNVERIFIED / newer than b3079c5**.

### 6.2 `resource-pack.self-host`
[SRC `SelfHostHttpServer.load` L164–240; CFG L57–68; WIKI `set_up_host/self`]

| Key | Default | Meaning |
|---|---|---|
| `ip` | `auto` | Advertised IP. `auto` uses a Cloudflare trace lookup and falls back to localhost. Not the bind address. |
| `port` | `auto` | `auto` = **share the Minecraft port** through the Netty pipeline. A number = dedicated port (code default 8163). |
| `url` | `""` | Overrides the advertised base URL (reverse proxy, NAT). `http://` is added if missing; a trailing `/` is ensured. |
| `protocol` | `http` | Ignored when `url` is set. The built-in server is HTTP only; HTTPS needs a front proxy. |
| `deny_non_minecraft_request` | true | User-Agent must start with `Minecraft Java/`. |
| `one_time_token` | true | `?token=` per player; single-use; expires in 1 min (wiki). |
| `strict_validation` | false | Binds the token to the `X-Minecraft-UUID` header. Do not enable on offline-mode servers. |
| `rate_limiting.qps_per_ip` | `10/60` | requests/seconds (token bucket) |
| `rate_limiting.max_bandwidth_per_second` | 5,000,000 (config) / 0 (code) | |
| `rate_limiting.min_download_speed_per_player` | 50,000 | |
| `forward_secret` | none | Enables `/forward/<pack>` for `self_forward` hosts. |

### 6.3 Delivery (`resource-pack.delivery`)
[SRC `Config` L448–465, `NMSFinishConfigurationListener` L27–80, `BukkitPackManager` L57–60; CFG L214–233]

- `send-on-join` (true): on 1.20.2+ the pack is pushed **during the configuration phase**. CE holds the "finish configuration" packet, queues resource-pack tasks, then joins the world. Below 1.20.2 it is sent on `PlayerJoinEvent`.
- `kick-if-declined` (true), `kick-if-failed-to-apply` (config file false; **code default true** if the key is missing).
- `prompt` (MiniMessage).
- `strict-player-uuid-validation` (true): kicks unverified UUIDs before sending. Check proxy/offline setups.
- `proxy{enable, host, port, username, password, scheme}`: outbound HTTP proxy for host API calls.
- A failed link request kicks with `multiplayer.requiredTexturePrompt.disconnect`.

### 6.4 Multiple packs, presets, preferences
[SRC L318–411, `PackPreset`; CFG L41–55; WIKI `workflows` (dev/prod example)]

- Each player's per-pack tri-state preference is stored in the CE database (`storage.type`, default sqlite).
- Selected packs = defaults overridden by preferences, sent in configured order.
- `presets: {name: [pack ids]}`. `/pack preset <name>` (permission `ce.resourcepack.preset.<name>`) sets the listed packs TRUE and every other managed pack FALSE.
- `/pack enable|disable <id>`. Admin commands: `/ce feature pack-preference`, `/ce feature pack-preset`. Debug: `/ce debug pack-states [player]`. [CFG commands.yml L64–89, L405–410]
- Dev/prod split: separate workflows and packs, with `trigger: reload_pack` only on the dev workflow. [WIKI `getting_start/workflows`]

---

## 7. Sounds, lang, atlases, particles, global variables

- **Custom sounds:** `sounds: { ns:event: {replace, subtitle, sounds: [name | {name, volume, weight, stream, attenuation_distance, preload, type}]} }` uses vanilla `sounds.json` semantics. The result is merged into `assets/<ns>/sounds.json`, and validation warns about missing `.ogg` files. [WIKI `configuration/sound`; SRC L3259–3290]
- **Replacement system:** see §3.4. Keys: `block.sound-system.enable`, `process-cancelled-events.{step,break}`, `silent-sound-path`. [CFG L382–391]
- **Lang (client) vs translations (server):**
  - `lang: {en_us: {key: text}}` is written into `assets/minecraft/lang/*.json` and needs a pack rebuild.
    - Locale `all` applies to every language.
    - `block_name:<ns:id>` keys are converted to the real `block.craftengine.custom_N` translation key.
    - Use with `<lang:key>`.
  - `translations: {en: {...}}` is resolved server-side: `<i18n:key>` uses the server locale; `<l10n:key>` uses each player's locale and is resolved per packet, so it works in other plugins' text too.
  - [WIKI `configuration/translations`; SRC `generateClientLang` L3227–3257]
- **Atlases:** `atlases: {minecraft:blocks: {sources: [...]}}` with source types `directory{source,prefix}`, `single{resource,sprite}`, `filter{pattern{namespace,path}}`, `unstitch{resource,divisor_x,divisor_y,regions}`, `paletted_permutations{textures,palette_key,permutations,separator}`.
  - The same id in several files is merged in load order, then merged into any existing atlas JSON and optimized.
  - Usually unnecessary thanks to `fix-atlas`. [WIKI `configuration/atlas`; SRC `AtlasConfigParser` L4040–4086, `generateAtlases` L2781–2801]
- **Particles:** the only pack-side particle handling is `remove-tinted-leaves-particle` (§5.2). `reference/particle_effect` is about function and behaviour particles (runtime), not pack assets.
- **Global variables:** `global_variables: {id: "<minimessage>"}`, used as `<global:id[:args]>`. Text-level, resolved by packet interception (`network.intercept-packets`). Not pack-related. [WIKI `configuration/global_variable`]
- **Fonts and images:** `images:` and emoji generate `minecraft:default` font entries. `override-uniform-font: true` mirrors them to `uniform`. Fonts merge with `merge_font`. [CFG L99–100; `craftengine.md` §4.12]

---

## 8. BetterModel, BetterHud and core shaders

**BetterModel (`BetterModel/build.zip`)**
- Already in CE's default `merge-external-zip-files` (path relative to `plugins/`). [CFG config.yml L90–92]
- BetterModel writes `assets/<namespace (default bettermodel)>/{items,models,textures/item}`, `pack.mcmeta` (with overlays) and `pack.png`. It needs no core shaders. [`research/bettermodel.md` §2]
- Merge path: zips merge after CE folders. Its `pack.mcmeta` overlays are combined (`merge_pack_mcmeta`), and `pack.png` is replaced only by CE default_assets' copy.
- BetterModel builds at `ServerLoadEvent`. **CE only re-merges on the next CE pack build**: run `/ce reload pack` after BetterModel rebuilds. CE never builds at startup, so the order only matters for manual rebuilds.

**BetterHud**
- BetterHud's built-in CraftEngine hook (`CraftEngineCompatibility.kt`, `merge-with-external-resources: true`) runs a BetterHud reload inside `AsyncResourcePackCacheEvent` and registers its zip or folder output. [`research/betterhud.md` §7.2]
- Requires BetterHud `pack-type` zip or folder, not `none`.
- **Do not also** list `BetterHud/build.zip` in `merge-external-zip-files`. Two different `Path` spellings would merge it twice: duplicate warnings, plus `merge_pack_mcmeta` combining entries. [INF from SRC `PackCacheData` (a `Set` of `Path`)]
- BetterHud's files go into `assets/minecraft/textures/gui/...` (boss bar sprites, `bars.png`). If our CE pack also ships those, **ours wins** (CE folders first) and the later file is reported as a duplicate.
- `/hud reload` alone does not update the CE pack; follow it with `/ce reload pack`.

**Core shaders**
- `grep` over the whole CE repo finds exactly one shader: `common-files/src/main/resources/resources/remove_shulker_head/resourcepack/assets/minecraft/shaders/core/rendertype_entity_solid.fsh`.
  - It lives in pack `remove_shulker_head`: `namespace: minecraft`, **`enable: true`**, description "Remove Shulker Head for 1.20~1.21.1".
- **No `rendertype_text` and no `text` shader**, so there is no collision with BetterHud's text shader (`<overlay>/assets/minecraft/shaders/core/rendertype_text.*` or `text.*` on 26.2+).
- The shulker shader uses pre-1.21.6 fog uniforms (`FogStart`, `FogEnd`, `FogColor`, `linear_fog`) and sits in the **root** `assets`, so 1.21.4+ and 26.x clients also load it.
  - **[INF] UNVERIFIED in game:** it may fail to compile on 1.21.6+ (fog moved to a uniform block, the same "FogColor redefinition" failure class as BetterHud issue #344), breaking resource loading.
  - **Recommendation:** disable that pack on 1.21.4+ servers (`/ce resource disable remove_shulker_head`, or `enable: false` in its `pack.yml`).
- `exclude-core-shaders: true` deletes `assets/minecraft/shaders` from root **and all overlays** after merging. That would strip BetterHud's shaders. Keep it **false**. [SRC `removeAllShaders` L1157–1173, L837–840]
- CE has no shader merge resolution; the wiki says shader merging is unsupported. So the first shader file wins. [WIKI `reference/file_conflict`]

---

## 9. Pitfalls
1. **Duplicate mappings** (the same line in our pack and in internal) create duplicate candidates and shifted `block:N` indices. Only add mappings for blocks the internal pack never maps (stone family, ores, wool, …), or parse the installed internal `mappings.yml` first. (§1.3)
2. **The installed `mappings.yml` is frozen** at first extraction. Pool sizes differ between servers, so read them at runtime. (§1.6)
3. **One-hop mappings:** never map real stone to a state that is itself a custom visual. Map to a pure vanilla state that no custom block overrides (for example `smooth_stone` if unused). (§0.2)
4. **Model conflict aborts the whole block**, not just a warning. Shared explicit states need byte-identical variant JSON (same path, rotation, uvlock, weight). (§3.3)
5. **Stale cache reservations** consume pool slots. Run `/ce clean-cache visual_block_states` after removing blocks. (§2.3)
6. **First-time auto allocation is non-deterministic** with multi-threaded loading. Seed `cache/visual_block_states.json` if reproducibility matters. (§2.2)
7. **No pack build at startup.** Automation must call `/ce reload all` or trigger workflows after writing our pack. Third-party rebuilds (BetterModel, BetterHud) also require a CE pack build. (§5.1)
8. **Pack-folder merge order is filesystem order.** Do not rely on our CE pack beating another CE pack for the same file. Use unique namespaces and avoid shipping `minecraft:` blockstates or models that other packs also ship. (§5.3)
9. **Blockstate files from other packs:** CE drops their `multipart` when it writes variants for that block. (§3.2)
10. **Existing model, texture or item-definition files beat `generation:`** (warning, not error). Do not ship a file at a path CE should generate. (§3.3, §4.1)
11. **Sound replacement is per SoundType.** One `stone` visual silences STONE sounds for all vanilla stone-type blocks client-side, replayed server-side. (§3.4)
12. **Client prediction:** `redstone_ore` lit state, button presses, climbables, friction, speed factor and particle-spawning leaves. (§3.6)
13. **Transparency on ≤1.21.11 depends on the visual block**, and occlusion depends on the visual block on all versions. `transparent: true` on a solid visual gives x-ray; use `barrier` or `light`/`structure_void`/sugar_cane-type visuals. (§2.5, §3.7)
14. **`supported-version.min: server`** excludes older ViaVersion clients. Setting min < 1.21.2 makes CE allocate CMD and take floats[0]. (§4.1, §5.5)
15. **`exclude-core-shaders: true`** removes BetterHud's shaders. The enabled `remove_shulker_head` shader is a risk on 1.21.6+. (§8)
16. **Digits in `$$…#suffix` keys** corrupt the version. (§1.2)
17. **`kick-if-failed-to-apply`** defaults to **true** if the key is absent from config.yml. (§6.3)
18. **`/hud reload`** and BetterModel reloads do not reach players until a CE pack build. (§8)
19. **`AsyncResourcePackCacheEvent` listeners must re-register on every firing**, including the startup warm-up. (§5.10)

---

## 10. TFC budget worksheet and recommended recipe

**Per-version budget table (default `mappings.yml`, fresh extraction)**

| Budget | 1.21.4–1.21.11 | 26.1+ | Hitbox | Use for |
|---|---|---|---|---|
| auto `solid` | 1289 | 1489 | full | rock variants, soil, planks, misc cubes |
| auto leaves (dry + waterlogged) | 117 + 117 | 117 + 117 | full, cutout | TFC leaves (tinted subsets 65 + 65) |
| auto tripwire | 63 lower + 63 higher | same | tiny | loose items, low decorations |
| auto plants | sugar_cane 15, cactus 15, kelp 25, cave_vines 48, weeping 25, twisting 25, sapling 8, pressure_plate 28 | same | by plant | crops, saplings |
| explicit full cubes (map real ones away) | stone family 44 + ores 24 + wool 16 + concrete 16 + concrete powder 16 + terracotta 17 + glazed 64 = **197** | same | full, own SoundType | high-volume rock and ore blocks |
| explicit small/flat | buttons 336 (≈168 if powered pairs share a model), carpets 17, wood/stone plates 28 | same | button / carpet / plate | ore pieces, pebbles, flat items |
| explicit crop ages | 84 (minus sweet_berry_bush 4) | same | crop outlines | TFC crops (needs mapping away vanilla crops) |
| freed but no group (explicit `state:` with full string) | 1900 | 2164 (26.3: 2242) | per block | doors, trapdoors, fence gates, copper |
| transparent + entity_renderer (`id: transparent`) | 1 state (unlimited blocks) | same | of the chosen visual | rare or animated blocks; `barrier` for full collision |

**Recipe**
1. Emit **full state strings** for every explicit `state:`. Never use `block:N` in generated config.
2. Hide repurposed real states: add `block_state_mappings` for every repurposed vanilla state that could exist in any world, targeting pure-vanilla states that no custom block uses.
   - Put them in our own pack file and never repeat internal lines.
3. Set `supported-version.min` ≥ 1.21.4 and `max: latest`. Keep `always-use-custom-model-data: false`.
4. Leave `merge-external-zip-files` containing `BetterModel/build.zip`. Let BetterHud's auto-merge register itself; do not list it twice.
5. Keep `exclude-core-shaders: false`. Disable `remove_shulker_head` on 1.21.4+.
6. After writing our pack, call `/ce reload all` (or the API equivalent). On first install, also seed `cache/visual_block_states.json` if deterministic visuals are needed.
7. At runtime, after `CraftEngineReloadEvent`, verify `AutoStateGroup.SOLID.candidateCount()` (and the other pools) ≥ our needs. Warn if the installed internal mappings are older.

---

## 11. Sources consulted

**CE source (b3079c5):**
- `common-files/src/main/resources/resources/internal/configuration/mappings.yml` (all 4554 lines)
- `common-files/src/main/resources/config.yml` L1–400
- `commands.yml`
- `translations/en.yml`
- `resources/*/pack.yml`, `remove_shulker_head/**`
- `core/.../block/{AutoStateGroup,AbstractBlockManager}.java`
- `core/.../pack/allocator/VisualBlockStateAllocator.java`
- `core/.../pack/{AbstractPackManager,PackCacheData,Pack,PackGenerationOptions,PackPreset,GeneratedPack}.java`
- `core/.../pack/{workflow,host,conflict,mcmeta}/**`
- `core/.../plugin/config/{Config,ConfigValue,ConfigParser,SectionConfigParser,IdSectionConfigParser}.java`, `yaml/StringKeyConstructor.java`
- `core/.../util/{VersionHelper,GsonHelper,ResourceConfigUtils}.java`
- `core/.../item/AbstractItemManager.java`
- `core/.../sound/SoundSet.java`
- `bukkit/.../block/BukkitBlockManager.java`
- `bukkit/.../plugin/network/BukkitNetworkManager.java`, `listener/game/SoundListener.java`, `listener/configuration/NMSFinishConfigurationListener.java`
- `bukkit/.../pack/BukkitPackManager.java`
- `bukkit/.../api/event/AsyncResourcePackCacheEvent.java`
- `bukkit/.../plugin/command/{feature,debug}/*`
- `bukkit/compatibility/.../bettermodel/*`

**Wiki pages:** `getting_start/{set_up_host,set_up_host/self,set_up_host/s3,set_up_host/external,workflows,workflows/multi_pack,item_models,project_structure}`, `reference/{file_conflict,particle_effect}`, `configuration/{block/states,block/states/entity_renderer,sound,atlas,global_variable,translations,item/models,item/models/{model,range_dispatch,select,condition,composite}}`, `advanced/{item_model_definition,model_tinting}`, sitemap.

**Vanilla:** client jars 1.21.4, 1.21.11 and 26.1.2 from piston-meta. `assets/minecraft/blockstates/*` were classified by `scratchpad/scripts/bs.py` (summary in `scratchpad/dl/bs_summary.json`). For 26.1.2, `javap` on `BlockStateModelLoader` and `BakedQuad$MaterialInfo`.

**Scripts:** `scratchpad/scripts/{mapcount,maptable,groups,fam,bs,h2t}.py`.

---

## 12. UNVERIFIED / open questions
1. The per-state blockstate stack merge is confirmed only on 26.1.2 (javap). 1.21.4–1.21.11 are assumed identical, which CE's own `solid` pool over multipart mushroom blocks depends on.
2. The per-block render layer on ≤1.21.11 (`ItemBlockRenderTypes`) is from vanilla knowledge; the obfuscated jars were not disassembled. The per-texture layer on 26.1+ is confirmed.
3. Whether `remove_shulker_head`'s old-format `rendertype_entity_solid.fsh` breaks 1.21.6+ and 26.x clients. It probably fails to compile; test it, or disable the pack.
4. Client-prediction glitches for button, redstone_ore, concrete_powder and crop visuals (§3.6), and whether CE resyncs them. Only note_block and tripwire have explicit CE flags.
5. Whether the variant `"z"` rotation key is honoured by any client.
6. S3 host availability in the Community build (separate `craft-engine-s3` artifact, wiki "Paid").
7. Per-pack `dependencies`/`conflicts` (wiki `workflows/multi_pack`) are not present in b3079c5.
8. Whether seeding `cache/visual_block_states.json` externally is safe across CE upgrades. The format is simple JSON (SRC L191–246), but it is not documented.
9. Whether `/ce reload` (config) updates already-loaded chunks for online players when visuals change. Rejoin may be needed.
10. Element `light_emission` for "glowing" items and blocks on 1.21.x/26.x.
11. BetterHud plus CE double registration if the user also lists `BetterHud/build.zip` (depends on `Path.equals` of the two spellings).
12. Behaviour of light-block visuals (`light[level=N]` emits client-side light) together with the CE light system.
