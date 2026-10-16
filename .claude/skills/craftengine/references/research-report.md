# CraftEngine: research report (primary sources)

> Note for this repo: examples use namespace `tfc` and pack folder `terrafirmacraft`. Our convention is the provisional namespace from `CLAUDE.md`: pack folder and namespace `tfp`. "local clone at scratchpad/..." refers to the original research environment and is not kept.

Researched 2026-10-16. Source: `github.com/Xiao-MoMi/craft-engine` at commit `b3079c5ac170fff2cb2a4c4dad184771a236cd1b` (2026-09-29, `project_version=26.9.2`, `config_version=114`). All repo paths below are relative to the repo root (local clone at `scratchpad/src/ce`).
Wiki: `https://xiao-momi.github.io/craft-engine-wiki/<page>/`. The canonical host `momirealms.net/craft-engine-wiki` was unreachable from this sandbox, but GitHub Pages serves the same pages.
Closed-source NMS helper: `net.momirealms:craft-engine-nms-helper-mojmap:1.0.240` (from repo.momirealms.net). I inspected it with `javap` only, to confirm the worldgen hooks.

Conventions: **[SRC]** = read in source, **[CFG]** = shipped config/example, **[WIKI]** = wiki page, **[INF]** = my inference from source (reasoned, not tested), **UNVERIFIED** = could not confirm.

---

## Table of contents
0. [TL;DR for the TFC project](#0-tldr-for-the-tfc-project)
1. [Versions, platforms, licensing, editions](#1-versions-platforms-licensing-editions)
2. [How it works internally (the mental model)](#2-how-it-works-internally-the-mental-model)
3. [Data folder layout, packs, load order, reload](#3-data-folder-layout-packs-load-order-reload)
4. [Config schema](#4-config-schema)
   - 4.1 Top-level sections · 4.2 YAML extensions (`$$`, `::`, `#`) · 4.3 Templates & config_factory
   - 4.4 Items · 4.5 Blocks (settings, properties, appearances, variants, models) · 4.6 Built-in block behaviors
   - 4.7 entity_renderer · 4.8 Furniture · 4.9 Recipes · 4.10 Loot · 4.11 Events/functions/conditions
   - 4.12 Images/fonts/emoji/GUI · 4.13 Lang vs translations · 4.14 Equipment · 4.15 Worldgen features · 4.16 client_bound_* (premium)
5. [The visual-state system: block_state_mappings, auto_state pools, explicit states](#5-the-visual-state-system)
6. [Hitbox / shape semantics, light, sounds](#6-hitbox--shape-semantics-light-sounds)
7. [Resource pack generation & delivery](#7-resource-pack-generation--delivery)
8. [Java API](#8-java-api)
9. [Storage, chunk lifecycle & world generation](#9-storage-chunk-lifecycle--world-generation)
10. [Performance notes](#10-performance-notes)
11. [Gotchas / pitfalls](#11-gotchas--pitfalls)
12. [Recommended integration recipe for our plugin](#12-recommended-integration-recipe-for-our-plugin)
13. [UNVERIFIED / open questions](#13-unverified--open-questions)

---

## 0. TL;DR for the TFC project

1. **Custom blocks are real NMS blocks.** At bootstrap, CE registers `block.serverside-blocks` (default 2000) blocks named `craftengine:custom_0..N` into the vanilla block registry. Each custom *state* (every combination of properties) uses one of them. The client is sent a "visual" vanilla state instead. [SRC `BukkitBlockManager.registerServerSideCustomBlocks`, `AbstractBlockManager.BlockParser`]
2. **The visual appearance is either an `auto_state` (taken from a pool of freed vanilla states) or an explicit `state:` (any vanilla state).** Pools exist only because `block_state_mappings` (internal pack `mappings.yml`) remap real vanilla states (for example `note_block[...]` and many others) to a single look-alike, which frees the originals. An explicit `state:` with a `model` **overrides that vanilla state's blockstate variant in the resource pack globally**, so any real vanilla block in that state looks the same. Two different models on one vanilla state is a load error. [SRC `AbstractBlockManager.arrangeModelForStateAndVerify`]
3. **The server-side hitbox (outline and collision) is always the visual vanilla state's shape.** You cannot define arbitrary shapes. Only `support_shape` can be overridden, by pointing at another vanilla state. Light, occlusion and suffocation default from the visual state. [SRC `BukkitBlockShape`, `BukkitBlockManager.applyPlatformSettings`]
4. **Worldgen works with real states.** CE wraps the world's `ChunkGenerator` (Paper only) and injects every chunk section before the NOISE/SURFACE/CARVER/STRUCTURE/FEATURE stages, so any `setBlockState` of a CE state during generation is recorded in CE storage. So **a Bukkit `ChunkGenerator`/`BlockPopulator` can place CE blocks with `CraftEngineBlocks.getBukkitBlockData(state)`** (inferred from source plus nms-helper bytecode; test it). Caveat: with the default `misc.delay-configuration-load: true`, chunks generated during startup are generated before CE has loaded configs or hooked worldgen. See §9.
5. **External plugins can register block behaviors, item behaviors, entity-renderer elements, functions, conditions, template args, config parsers, and more** through static `register(Key, Factory)` methods. You must register before CE parses configs. Config `type:` values without a namespace default to `craftengine:`. [SRC `BlockBehaviors.register`, wiki `api/block_behavior`]
6. **There is no API to register a pack folder.** Content packs must live in `plugins/CraftEngine/resources/<pack>/` (`pack.yml`, `configuration/`, `resourcepack/`). Symlinks are followed. Raw resource-pack folders and zips *can* be added via `AsyncResourcePackCacheEvent.registerExternalResourcePack(Path)`. [SRC `AbstractPackManager.loadPacks`, `AsyncResourcePackCacheEvent`]
7. **`client_bound_data` / `client_bound_model` / `client_bound_material`, resource pack protection/obfuscation and entity culling are Premium-only.** The source we read has `VersionHelper.PREMIUM = false`. [SRC `VersionHelper`, `AbstractItemManager`, wiki `intro/exclusive_feature`]
8. **License:** CraftEngine is **GPL-3.0** (repo `LICENSE`, Modrinth). Our plugin is EUPL-1.2. EUPL's compatibility clause lists GPLv3, but linking and distribution need a legal check. This is not legal advice.

---

## 1. Versions, platforms, licensing, editions

| Item | Value | Source |
|---|---|---|
| Plugin version | 26.9.2 (latest on Modrinth, 2026-09-28) | `gradle.properties`, Modrinth API |
| MC versions | **1.20 – 26.3** (`VersionHelper` flags `isOrAbove1_20` … `isOrAbove26_3`; `latest_supported_version=26.3`). Modrinth lists 1.20…26.3 for paper/folia/purpur. The bukkit/spigot jar lists 26.1+ only. | `core/.../util/VersionHelper.java`, `gradle.properties`, Modrinth |
| Platforms | Paper forks (Paper, Purpur, Pufferfish, Leaf, Leaves, UniverseSpigot) and Folia forks (Folia, Canvas, Luminol). `foliaSupported = true`. | wiki `getting_start/installation`, `bukkit/paper-loader/build.gradle.kts` |
| Java | JDK 21+. Built with toolchain 25 and `release 21`. MC 26.x itself needs Java 25. | `build.gradle.kts`, wiki installation |
| Loader | Two jars: a Bukkit `plugin.yml` loader and a **Paper plugin** with `PaperCraftEngineBootstrap` (`paper-plugin.yml`). Both use `load = STARTUP`. | `bukkit/loader/build.gradle.kts`, `bukkit/paper-loader/build.gradle.kts` |
| Injection | **ByteBuddy** generates a `CraftEngineBlock` subclass of NMS `Block`, overriding getShape/getCollisionShape/getSupportShape/tick/randomTick/onPlace/canSurvive/updateShape/rotate/mirror/bonemeal/... and delegating to a `BlockBehavior`. NMS access goes through a "proxy" reflection layer (`craft-engine-bukkit-proxy`) plus the closed-source `FastNMS` (nms-helper) for injected classes: `InjectedLevelChunkSection`, `InjectedPalettedContainer`, `InjectedCustomChunkGenerator`, `InjectedWorldBorder`. A runtime agent (`RuntimePatcher`) patches the server at bootstrap. | `bukkit/src/main/java/net/momirealms/craftengine/bukkit/plugin/injector/BlockGenerator.java`, `PaperCraftEngineBootstrap.java`, `WorldStorageInjector.java` |
| Packet interception | Netty pipeline. Block-state ids in chunk and block-update packets are rewritten through `blockStateMappings` (custom id → visual id, mapped vanilla → kept vanilla). Item, chat, title and other packets are rewritten for `<image>`, `<shift>` and similar tags. `network.performance-mode` handles packets at the NMS packet stage. | `BukkitNetworkManager.registerBlockStatePacketListeners`, `config.yml` `network.*` |
| License | GPL-3.0 | `LICENSE`, Modrinth |
| Editions | Community (free, Modrinth) and Premium (VoxelShop, BuiltByBit, NMCrate, Afdian). README: Premium has "official support, exclusive features, dev builds". | `README.md` |

**Premium-only features that matter to us** (source shows `if (VersionHelper.PREMIUM ...)` or config says "[Premium Exclusive]"):
- `client_bound_data`, `client_bound_model`, `client_bound_material` on items (`AbstractItemManager` ~L537–673). In Community these keys are **silently ignored**. Note that the default `tree.yml` uses `client_bound_data` for leaves.
- `item.client-bound-model` global (config.yml L294–307).
- Resource pack protection and obfuscation (`resource-pack.protection`, config.yml L147–213).
- Entity culling of block-entity renderers and furniture (`client-optimization.entity-culling`, config.yml L812–842; wiki entity_renderer "Entity Culling – Premium Exclusive").
- Everything else (blocks, behaviors, renderers, furniture, worldgen, API) is in the open source.

---

## 2. How it works internally (the mental model)

```
server world chunk section  ── holds NMS DelegatingBlockState "craftengine:custom_N"  (real block, own registry id)
        │   (InjectedLevelChunkSection / InjectedPalettedContainer mirrors every setBlockState into …)
        ▼
CE storage (CESection palette of ImmutableBlockState: {id: "tfc:rock/raw/granite", properties:{…}})
        │   saved to <world>/craftengine/ (linear|mca region files) or PDC
        ▼
network layer: custom_N state id  ──blockStateMappings──▶  visual vanilla state id (e.g. note_block[instrument=hat,note=3])
resource pack: assets/minecraft/blockstates/note_block.json variant "instrument=hat,note=3,powered=false" → your model
```

- `ImmutableBlockState` (core) is the CE state. It holds `customBlockState` (the NMS `custom_N` state), `visualBlockState` (the vanilla look) and `restoreBlockState` (the vanilla state written to disk on chunk unload, equal to the visual, or glass if the visual is a barrier). [SRC `core/.../block/ImmutableBlockState.java`, `BukkitBlockManager` L373–380]
- Each `custom_N` NMS block is a ByteBuddy `CraftEngineBlock` whose `behaviorHolder` and `shapeHolder` are rebound on every reload. [SRC `BlockGenerator.init`, `BukkitBlockManager.applyPlatformSettings`, `unload()`]
- Internal ids (`custom_N`) are allocated per **state string** (for example `ns:block[prop=val]`) by `IdAllocator`. The allocator is cached in `plugins/CraftEngine/cache/custom_block_states.json`. Visual auto-state allocations are cached in `cache/visual_block_states.json`. You can pin ids with `states: { id: <start> }`. [SRC `AbstractBlockManager` constructor and `parseCustomBlock`]

---

## 3. Data folder layout, packs, load order, reload

### 3.1 Layout
After first start (wiki `getting_start/installation`, `project_structure`):
```
plugins/CraftEngine/
  config.yml  commands.yml  translations/  libs/  generated/  cache/  data.db
  resources/
    <pack_folder>/            # one "pack"; folder name = default namespace
      pack.yml                # optional metadata
      configuration/          # *.yml and *.json, recursive, any file names
      resourcepack/           # vanilla-format assets (assets/<ns>/models|textures|sounds|...)
      blueprint/              # Blockbench .bbmodel sources (optional)
      script/                 # JS scripts (optional, scripting.js.enable)
      subpacks/<id>/{configuration,resourcepack,blueprint,script}   # enabled via pack.yml subpacks
```
[SRC `core/.../pack/Pack.java` `expandFolders`; `AbstractPackManager.loadPacks` L485–557]

- Folders starting with `.` are ignored. If the folder name is not a valid namespace, the namespace becomes `minecraft`. [SRC L502–508]
- `pack.yml` keys: `enable` (default true), `namespace`, `description`, `version`, `author`, `subpacks: {id: bool}`. Subpack keys may use version conditionals. [SRC L513–548]

```yaml
# common-files/src/main/resources/resources/default_feature_populator/pack.yml
author: XiaoMoMi
version: 1.0
description: Default Feature Populator for CraftEngine
namespace: default
enable: false
subpacks:
  $$>=26.3:
    26_3: true
  $$26.1~26.2:
    26_1: true
  $$fallback:
    fallback: true
```
- All `.yml`/`.yaml`/`.json` under `configuration/` are parsed, and **symlinks are followed** (`FileVisitOption.FOLLOW_LINKS`). Files are cached by (lastModified, size). [SRC `updateCachedConfigFiles` L577–631]
- Shipped packs: `default_assets` (examples, namespace `default`), `default_templates` (enabled, namespace `default`; we rely on its templates), `internal` (GUI images, `mappings.yml`, destroy stages; namespace `internal`), `legacy_armor` (disabled), `remove_shulker_head`, `default_feature_populator` (disabled). These are extracted only when `resources/` does not exist. [SRC `loadPacks` L486–494]

### 3.2 How a third-party plugin ships content
- **No API registers a configuration pack from another location.** `loadPacks()` scans only `dataFolder/resources`. [SRC]
- Options:
  1. Copy (or symlink) our generated pack into `plugins/CraftEngine/resources/tfc/` **before CE loads configs** (see timing in 3.3), then trigger `/ce reload all` (or the API equivalent) if CE has already loaded.
  2. Raw assets (textures and models outside a CE pack) can be injected at pack build time: listen to `AsyncResourcePackCacheEvent` and call `registerExternalResourcePack(Path)` with a folder or `.zip`. It must be called **every** time the event fires. The cache only refreshes when file size or mtime changes. [SRC `bukkit/.../api/event/AsyncResourcePackCacheEvent.java`]
  3. `config.yml` → `resource-pack.merge-external-folders` / `merge-external-zip-files`. By default this already includes `"BetterModel/build.zip"` and `"ModelEngine/resource pack"`. Add BetterHud's output here. [CFG config.yml L88–94]
- `AsyncResourcePackGenerateEvent(generatedPackPath, zipFilePath)` lets you post-process the generated pack. [SRC api/event]
- Commands to manage packs: `/ce resource enable|disable|list|create|save-default|search`. [CFG commands.yml]

### 3.3 Startup and load order [SRC `CraftEngine.onPluginEnable` L420–556, `BukkitCraftEngine`, `PaperCraftEngineBootstrap`]
1. **Bootstrap (Paper plugin):** dependencies, proxy, config and locale, `RuntimePatcher.patch`. On `LifecycleEvents.DATAPACK_DISCOVERY`, `injectRegistries()` registers the `custom_N` blocks, so **datapacks can reference CE blocks**.
2. **onLoad (`onPluginLoad`):** registers built-in behavior, element and hitbox types (`BukkitBlockBehaviors.init()` and others), then creates the managers.
3. **onEnable:** listeners and commands. If `misc.delay-configuration-load: false`, it also runs worldManager.delayedInit (world and worldgen hooks) and **loads all configs immediately** (recipes are deferred to the next tick).
4. **First tick (`runDelayed`)**, with the default `delay-configuration-load: true`: full `reloadPlugin(...)` (configs and recipes), then `worldManager.delayedInit()` (world listeners plus injection into already-loaded worlds), furniture/projectile init, async asset caching. **One more tick later** it fires `CraftEngineReloadEvent` (`isFirstReload() == true`) and sets `isFullyLoaded`.
   - Config comment: "Defers CraftEngine configuration loading until after all plugins have initialized … Only disable if you require datapack-generated spawn chunks." [CFG config.yml L860–863]
5. **Registration timing for us:** register behaviors and other types in our `onLoad()` or `onEnable()` (both happen before the first-tick reload under the default setting). If we switch `delay-configuration-load` to false, we must register in **`onLoad()`**, because CE loads configs in its own onEnable (STARTUP). The API doc says `loadedBlocks()`/`loadedItems()` are empty during onEnable, so listen for `CraftEngineReloadEvent`. [SRC javadoc `CraftEngineBlocks.loadedBlocks`]

### 3.4 Reload [SRC `ReloadCommand`, `CraftEngine.reloadPlugin`; wiki project_structure]
| Command | Does |
|---|---|
| `/ce reload` = `/ce reload config` | Re-reads config.yml, translations, **re-scans packs**, reparses all configs except recipes, rebinds block behaviors and shapes, re-registers packet mappings. Does not rebuild the resource pack. |
| `/ce reload recipe` | config + recipes |
| `/ce reload pack` | runs workflows with trigger `reload_pack` (generate → validate → zip → upload → send_pack) |
| `/ce reload host` | reloads hosting settings |
| `/ce reload all` | config + recipes, then pack workflows |
| `/ce workflow <name>` | run a named workflow |

**Restart required** (config comments): `block.serverside-blocks`, `deceive-bukkit-material`, `inject-bukkit-material`, storage backend, `chunk-system.*` (cache-mode, async-write/read, injection target), `furniture.collision-entity-type`, `network.performance-mode`, `item-crypto`, `attribute.enable`, `recipe.inject-block-entities`, `equipment.lod`, `client-optimization.entity-culling`, `scripting.js.enable`. commands.yml also needs a restart. Plugin reload or disable at runtime is refused, and the server is shut down. [SRC `BukkitCraftEngine.onPluginEnable/Disable`]

Async: config parsing is multi-threaded (`misc.multi-threaded-configuration-load: true`). Sync tasks (recipes, sounds, paintings, listeners) run on the main thread afterwards. [SRC `reloadPlugin`]

---

## 4. Config schema

### 4.1 Top-level sections (keys accepted in any configuration file)
From `ConfigKeys.of(...)` section ids [SRC grep over all parsers]. A `#suffix` is stripped from the key (`config_factory#basic`, `lang#items`) so one file can hold several sections of the same type [SRC `AbstractPackManager.processConfigEntry` L757–766]:

`item(s)`, `block(s)`, `block_state_mapping(s)`, `furniture`, `entit(y|ies)`, `equipment(s)`, `equipment_set(s)`, `recipe(s)`, `loot(s)`, `loot_source(s)|vanilla_loot(s)`, `template(s)`, `config_factor(y|ies)`, `categor(y|ies)`, `image(s)`, `emoji(s)`, `sound(s)`, `jukebox_song(s)`, `painting(s)`, `lang|language(s)`, `translation(s)|l10n|i18n|localization|internationalization`, `advancement(s)`, `attribute(s)`, `attribute_operation(s)`, `damage_rule(s)`, `configured_feature(s)`, `placed_feature(s)`, `global_variable(s)`, `atlas(es)`, `skip_optimization`.

Entries are keyed by namespaced id (`tfc:rock/raw/granite`). Item behaviors can define a block **inline** (`block_item` → `block: {…}`); the block then gets the item's id. [CFG `pebble.yml`, `netherite_anvil.yml`]

### 4.2 YAML extensions [SRC `core/.../plugin/config/yaml/StringKeyConstructor.java`]
- **Version-conditional keys**: `$$>=1.21.4:`, `$$<=1.20.4:`, `$$26.1~26.2:` (inclusive range), `$$1.21.4:` (exact). A matching mapping is merged into the parent. If *all* keys of a mapping are `$$…`, the node becomes a **value selector**, and `$$fallback` is used when nothing matches. A `#tag` suffix keeps keys unique (`$$>=1.21.4#sapling:`).
  ```yaml
  # default_assets/configuration/items/hami_melon.yml
  data:
    item_name: <!i><lang:item.default.hami_melon_slice>
    $$>=1.20.5:
      food: { nutrition: 2, saturation: 1.0, can_always_eat: false }
  $$<=1.20.4:
    settings:
      food: { nutrition: 2, saturation: 1.0 }
  ```
- **Deep keys**: `data::item_name: …` is equivalent to `data: { item_name: … }`. Used for `loot::template: default:loot_table/self` and `settings::template: …`. [CFG tree.yml]
- Duplicate keys merge maps (a warning is logged). TAB indentation is auto-fixed and the file rewritten. [SRC `AbstractPackManager.parseYamlContent`]
- Extra YAML tags: `!!byte !!short !!long !!double !!IntArray !!LongArray …`, `UUID`.

### 4.3 Templates and config_factory [WIKI `reference/template`; SRC `TemplateManagerImpl`, `TemplateArguments`]
- Define under `templates:`. Use `template: id` or `template: [id1, id2]` (maps deep-merge; lists concatenate), together with `arguments:`, `overrides:` (full replace) and `merges:` (deep merge).
- Placeholders: `${arg}`, default `${arg:-default}` (SNBT; nestable: `${a:-'${b:-x}'}`), case modifiers `${arg^}` / `${arg^^}`, escape `\${`. Built-ins: `${__NAMESPACE__}`, `${__ID__}`.
- Argument types (`type:`): `plain`, `self_increase_int`, `map`, `list`, `null`, `expression` (with `value_type`), `condition` (`condition`/`on_true`/`on_false`), `when` (`source`/`when`/`fallback`), `to_upper_case`, `to_lower_case`, `capitalize`, `object`. Use `__skip_template_argument__: {}` when a map argument contains a literal `type` key.
- `config_factory`: `instances:` (a list of argument maps) and `blueprint:` (a full config tree with `${}` placeholders). This is how `default_assets/configuration/blocks/tree.yml` generates log, wood, planks, door, fence, button, leaves, sapling and other blocks per tree type. It is the natural fit for TFC's rock × block-type matrix.
  ```yaml
  # tree.yml (excerpt)
  config_factory#basic:
    instances:
      - namespace: default
        tree_type: palm
        asset_path_prefix: minecraft:block/custom/
        replaced_door_type: oak
        leaves_tintable: true
    blueprint:
      items:
        ${namespace}:${tree_type}_log:
          settings:
            fuel_time: ${wood_fuel_time:-300}
  ```
- Default templates we can reuse (`default_templates/configuration/templates/*.yml`): `default:block_state/{pillar,leaves,tintable_leaves,non_tintable_leaves,trapdoor,door,fence_gate,slab,stairs,pressure_plate,fence,button}`, `default:settings/{solid_1x1x1,transparent_1x1x1,sapling,leaves,wood,planks,ore,deepslate_ore}`, `default:hardness/*`, `default:burn_data/*`, `default:sound/{stone,wood,grass,gravel,sand,…}`, `default:loot_table/{self,basic,furniture,ore,leaves,slab,…}`, `default:rotatable_block`, `default:rotatable_furniture_4|8`.

### 4.4 Items [SRC `AbstractItemManager` L502–720; WIKI item pages]
Keys: `material` (server item, default `item.default-material: nether_brick`), `model`/`models`/`texture(s)`/`blueprint`/`legacy_model`, `item_model`, `custom_model_data`, `data` (item processors = components), `settings`, `behavior(s)`, `event(s)`, `category`, `updater`, `oversized_in_gui`, `hand_animation_on_swap`, `swap_animation_scale`, `override_data`, plus Premium `client_bound_material`/`client_bound_model`/`client_bound_data`.

- Identification: the id is written as `craftengine:id` via `IdProcessor`. Check it with `CraftEngineItems.getCustomItemId(stack)`. [SRC `core/.../item/processor/IdProcessor.java`]
- Model generation: on 1.21.4+ an `item_model` equal to the item id is generated automatically. CMD is auto-allocated only if the pack must support ≤1.21.1 clients (config `item.custom-model-data-starting-value`, `always-use-item-model`). [SRC L566–640]
- `model:` uses 1.21.4 item-model-definition syntax: `type: minecraft:model|condition|select|range_dispatch|composite|special|empty|bundle_selected_item`, with `path`, `generation` (`parent`, `textures`, `display`), and `tints` (`minecraft:dye|constant|…`).
  ```yaml
  # default_assets/configuration/items/cap.yml
  default:cap:
    material: leather_helmet
    settings: { dyeable: true }
    data:
      item_name: <!i><white><lang:item.default.cap>
      unbreakable: true
      remove_components: [attribute_modifiers]
      $$>=1.21.2:
        equippable: { slot: head }
    model:
      type: minecraft:model
      path: minecraft:item/custom/cap
      tints:
        - type: minecraft:dye
          default: -6265536
  ```
- `data:` processors (complete list) [SRC `core/.../item/processor`]: `item_name, custom_name, display_name, lore, dynamic_lore, insert_lore, remove_lore, unbreakable, max_damage, food, equippable, enchantment(s), trim, dyed_color, item_model, custom_model_data, tooltip_style, hide_tooltip, jukebox_playable, painting_variant, profile, written_book_content, process_written_book_tags, use_remainder, attribute_modifiers|attributes, component(s), remove_component(s), nbt, pdc, tags, block_state|blockstate, arguments, set_arguments, get_arguments, random_values|randoms, condition|conditional, function(s), external, id, overwritable_*`.
- `settings:` (complete) [SRC `core/.../item/setting`]: `allowed_projectiles, anvil_repair_item, attribute_modifiers, break_power, can_place, compost_probability, consume_replacement, craft_remainder, craft_remaining_item, destroy_on_death_chance, disable_vanilla_behavior, drag_repair_item, drop_display, dye_color, dyeable, enchantable, equipment, equipment_lod, equipment_potion_effects, equipment_set_part, equippable, firework_color, food, fuel_remainder, fuel_time, glow_color, ingredient_substitute, invulnerable, keep_on_death_chance, prevent_break, projectile, renameable, repairable, respect_repairable_component, tags, trigger_advancement`.
- Item behaviors (complete) [SRC `bukkit/.../item/behavior`]: `block_item, wall_block_item, ground_block_item, ceiling_block_item, double_high_block_item, multi_high_block_item, liquid_collision_block_item, furniture_item, liquid_collision_furniture_item, range_mining_item, compostable_item, flint_and_steel_item`.
- `break_power` / `required_break_power`: a tool can break a block only if its power is ≥ the required value. `item.break-power` overrides vanilla tools. [CFG config.yml L326–330]

### 4.5 Blocks [SRC `AbstractBlockManager.BlockParser` L366–990; WIKI `configuration/block/states`]

```yaml
blocks:
  ns:id:
    settings: {...}                 # BlockSettings, see below; can use template/overrides
    behavior: {type: …} | [ … ]     # alias behaviors
    loot: {...} | {template: …}
    events: [ {on: right_click, conditions: [...], functions: [...]} ]
    state:                          # single-state block
      auto_state: solid             # OR  state: minecraft:anvil[facing=east]
      model: {...} | texture: … | models: [...] | transparent: true | blueprint: …
      entity_renderer: {...} | [...]
    states:                         # multi-state block
      id: 0                         # optional fixed first internal id (custom_N)
      properties: { name: {type: …, default: …} }
      appearances: { name: { auto_state|state, model…, entity_renderer…, transparent } }
      variants: { "prop=val,prop2=val": { appearance: name, settings: {...overrides} } }
    entity_culling: false | {aabb, view_distance, aabb_expansion, ray_tracing}   # premium effect
```
- **Property types** [SRC `core/.../block/property`]: `boolean, int (min/max or range: 1~7), string, axis, horizontal_direction (=4-direction), direction (=6-direction), single_block_half, double_block_half, hinge, stairs_shape, slab_type, sofa_shape, anchor_type, bed_part`. Special names give hardcoded placement and rotation behavior: `axis`, `facing`, `facing_clockwise`, `rotation`; `waterlogged` gets automatic waterlogging behavior. [SRC `BukkitBlockManager.createBlockBehavior`, `HardcodedPropertyBlockBehavior`; WIKI states]
- **Variant matching**: unlisted properties act as wildcards. The *first* matching variant gives `appearance`. All matching variants' `settings` are merged in order. States without an appearance fall back to the **first** appearance. [WIKI states; SRC L756–791]
- **Model forms** under an appearance: `model: path`, `model: {path, generation: {parent, textures}}`, `model: {path, textures: [..]}` (1 tex → cube_all, 2 → cube_column, 3 → cube_bottom_top, 4 → orientable, 5+ → six faces; prefix `^` marks the particle texture), `texture: x`, `models: [{path, weight, x, y, z, uvlock}]` (weighted random), `transparent: true` (empty model), `blueprint: file.bbmodel`. Rotations must be multiples of 90. [SRC L695–736, 952–990]
- **Block settings** (complete list) [SRC `core/.../block/setting/BlockSettingsModifiers.java`]: `item, name, support_shape, luminance, map_color, burn_chance, fire_spread_chance, block_light|light_block|light_dampening, hardness, friction, speed_factor, jump_factor, resistance, incorrect_tool_dig_speed, replaceable, is_redstone_conductor, is_suffocating, is_randomly_ticking, is_view_blocking, propagate_skylight, burnable, push_reaction, instrument, sounds{break,step,place,hit,fall}, fluid_state, can_occlude, require_correct_tools, respect_tool_component, required_break_power, use_shape_for_light_occlusion, tags, correct_tools, block_raytrace, bounce_restitution (26.2+), destroy_stages`. Vanilla blocks accept `settings.client_bound_tags` (re-tag a vanilla block client-side) [SRC `parseVanillaBlock`].

Real example: the explicit vanilla state plus custom model (`default_assets/configuration/blocks/netherite_anvil.yml`):
```yaml
states:
  properties:
    facing_clockwise: { type: 4-direction, default: north }
  appearances:
    axisX:
      state: minecraft:anvil[facing=east]     # freed by mappings.yml (east→west)
      model:
        path: minecraft:block/custom/netherite_anvil
        y: 90
        generation:
          parent: minecraft:block/anvil
          textures:
            top: minecraft:block/custom/netherite_anvil_top
            body: minecraft:block/custom/netherite_anvil
            particle: minecraft:block/custom/netherite_anvil
    axisZ:
      state: minecraft:anvil[facing=north]    # freed (north→south)
      model: { path: minecraft:block/custom/netherite_anvil }
  variants:
    facing_clockwise=east:  { appearance: axisX }
    facing_clockwise=west:  { appearance: axisX }
    facing_clockwise=north: { appearance: axisZ }
    facing_clockwise=south: { appearance: axisZ }
```
Explicit state **without a model**, reusing the vanilla look (`hami_melon.yml`): `appearances: { age=0: { state: pumpkin_stem[age=0] } … }`.

Small-hitbox, multi-appearance auto_state (`pebble.yml`): `appearances: one: {auto_state: lower_tripwire, models: [...4 rotations]}`, `three: {auto_state: higher_tripwire, …}`.

### 4.6 Built-in block behaviors (complete) [SRC `bukkit/.../block/behavior/BukkitBlockBehaviors.java`]
`empty, bush_block, hanging_block, falling_block, leaves_block, strippable_block, sapling_block, on_liquid_block, near_liquid_block, concrete_powder_block, vertical_crop_block, crop_block, grass_block, lamp_block, trapdoor_block, door_block, stackable_block, sturdy_base_block, fence_gate_block, slab_block, stairs_block, pressure_plate_block, double_high_block, change_over_time_block, simple_storage_block, toggleable_lamp_block, sofa_block, bouncing_block, directional_attached_block, liquid_flowable_block, simple_particle_block, wall_torch_particle_block, fence_block, button_block, face_attached_horizontal_directional_block, stem_block, attached_stem_block, chime_block, budding_block, seat_block, surface_spreading_block, snowy_block, hangable_block, drop_experience_block (alias drop_exp_block), multi_high_block, spreading_block, item_frame_block, display_item_block, drawer_block, tint_source_block, vine_crop_head_block, vine_crop_body_block, decay_block, seagrass_like_block`.
Automatically added: `HardcodedPropertyBlockBehavior` (axis/facing/facing_clockwise/rotation) and `WaterloggedBlockBehavior` (if a boolean `waterlogged` property exists). Per-behavior options: wiki `configuration/block/behaviors/<name>`. Example `crop_block` keys [SRC `CropBlockBehavior.Factory`]: requires an int property `age`; `grow_speed` (0.125), `light_requirement`, `max_light_requirement` (15), `spawn_light_requirement`, `max_spawn_light_requirement`, `is_bone_meal_target` (true), `bone_meal_age_bonus`.

Combined behaviors example (`pebble.yml`):
```yaml
behaviors:
  - type: liquid_flowable_block
  - type: sturdy_base_block
    direction: down
    support_types: [full]
  - type: stackable_block
    property: pebble
    items: [default:pebble]
```

### 4.7 entity_renderer (block entity renderers) [SRC `bukkit/.../block/entity/renderer/constant/*`; WIKI `configuration/block/states/entity_renderer`]
- Element types: `item_display`, `text_display`, `block_display`, `item` (spinning dropped item), `armor_stand`. Compatibility module adds `better_model` and `model_engine`. `type` is inferred from `item`/`text`/`block`.
- Display params (item_display): `item`, `scale` (number or x,y,z), `position` (default 0.5,0.5,0.5), `translation`, `pitch`, `yaw`, `rotation` (number = Y degrees, x,y,z Euler, or x,y,z,w quaternion), `display_transform|display_context`, `billboard`, `shadow_radius`, `shadow_strength`, `glow_color`, `brightness{block_light,sky_light}`, `view_range`, `tint_source`, `condition(s)` (per-player visibility), `offset {type: xz|xyz, horizontal, vertical}` (random per-position offset like flowers). [SRC `ItemDisplayBlockEntityElementConfig.Factory` L221–251]
- Wiki tip: prefer `translation`/`rotation` over `position`/`yaw`/`pitch` to avoid position-sync packets.
- **Constant vs dynamic**: constant renderers come from `entity_renderer` in the appearance, are stored per chunk, and are spawned as packet-only virtual entities per tracking player (`ItemDisplayBlockEntityElement` caches spawn and despawn packets). Dynamic renderers are created by block entities (`DynamicDisplayItemBlockEntityElement`, `DynamicDrawerBlockEntityElement`, `DynamicItemFrameBlockEntityElement`) for `display_item_block`, `drawer_block` and `item_frame_block`. [SRC]
- `tint_source`: copies item components (for example `minecraft:dyed_color`) from a block entity's `TintSource` (provided by the `tint_source_block` or `display_item_block` behaviors) onto the rendered item. Shorthand `tint_source: minecraft:dyed_color`; full form `{type: default, components: [...], index: 0}`. [SRC `DefaultBlockEntityTintSourceConfig`; CFG `sofa.yml`]
- **"Transparent" convention** for nearly unlimited blocks: share one cleared vanilla state:
  ```yaml
  state:
    auto_state: { type: sugar_cane, id: transparent }   # same id → same vanilla state
    transparent: true
    entity_renderer: { type: item_display, item: default:my_decoration }
  ```
  [WIKI states "Entity Renderer"]. Without `transparent`, the vanilla model *and* the entity both render. The button and fence templates rely on this: the real `oak_button[...]` state is the hitbox and an item_display at scale 1.01 covers it (`default_templates/.../block_states.yml` `default:block_state/button`, `fence`).
  ```yaml
  # block_states.yml  default:block_state/button (excerpt)
  face=floor,facing=east,powered=true:
    state: ${button_base_block}[face=floor,facing=east,powered=true]
    entity_renderer:
      item: ${button_pressed_item}
      rotation: 0,90,0
      scale: 1.01
      translation: 0,0.005,0
  ```
- `state: barrier` plus an entity_renderer gives a full-cube invisible hitbox (sofa). On chunk unload, a barrier is restored as **glass** [SRC `BukkitBlockManager` L376]. `state: white_bed[...]` gives a bed hitbox (`sofa.yml`).
- Warning [WIKI]: blocks with hardcoded renderers and non-full models can cause X-ray artifacts.

### 4.8 Furniture [CFG `furniture/*.yml`; SRC `bukkit/.../entity/furniture`]
Defined under `furniture:` or inline in `furniture_item`. Keys: `settings{item, hit_times, sounds}`, `events`, `loot`, `variants: {ground|wall|ceiling|<name>: {loot_spawn_offset, elements: [...], hitboxes: [...]}}`. The item behavior's `rules: {ground: {rotation: four|eight|any, alignment: center|any}}`.
- Element types: `item_display, text_display, block_display, item, armor_stand` (+ `better_model`, `model_engine` with those plugins).
- Hitbox types: `interaction` (width, height, interactive, invisible, blocks_building, can_use_item_on, can_be_hit_by_projectile), `shulker` (direction, peek, scale, interaction_entity, blocks_building, seats), `happy_ghast`, `custom`. Seats are given as `"x,y,z yaw [limitPlayerRotation]"`.
- Furniture behaviors: `simple_storage_furniture`, `display_item_furniture`, `glowing_furniture`.
- `furniture.collision-entity-type: interaction|boat`, `hide-base-entity`. [CFG config.yml L423–440]
```yaml
# default_assets/configuration/furniture/bench.yml (excerpt)
hitboxes:
  - position: 0,0,0
    type: shulker
    direction: east
    peek: 100
    blocks_building: true
    interactive: true
    interaction_entity: true
    seats:
      - 0,0,-0.1 0 true
      - 1,0,-0.1 0 true
```

### 4.9 Recipes [SRC `core/.../item/recipe/RecipeSerializers.java`]
Types: `shaped` (`crafting_shaped`), `shaped_transform`, `shapeless` (`crafting_shapeless`), `shapeless_transform`, `dye` (`crafting_dye`, ≥1.21.2), `smelting`, `blasting`, `smoking`, `campfire_cooking`, `stonecutting`, `smithing_transform`, `smithing_trim`, `brewing`. Global options include `recipe.disable-vanilla-recipes`, `unlock-on-ingredient-obtained`, and `inject-block-entities` (furnace injection for custom conditions). [CFG config.yml L592–623] The shape is the vanilla format with `pattern`, `ingredients`, and `result {id,count}`, as in `topaz_ore.yml`. TFC's knapping, anvil, quern and barrel recipes are not vanilla types, so we will implement them ourselves.

### 4.10 Loot tables [SRC `core/.../loot`; CFG templates/loot_tables.yml]
`pools: [{rolls, conditions, entries: [{type, item, functions, conditions}]}]`. Registered ids (entries, functions and number providers combined): `item, alternatives, loot_table, furniture_item, exp, empty, if_else, set_count, apply_bonus (formula ore_drops|binomial_with_bonus_count), limit_count, explosion_decay, drop_exp, apply_data, function`. The vanilla-loot sources (`loot_source`) are `block_break, block_shear, entity_death, entity_drop, entity_shear, fishing, archaeology, piglin_barter, vault, container, harvest, advancement`. Datapacks can reference CE items via entry `type: craftengine:item` [WIKI compatibility/datapack].

### 4.11 Events, functions, conditions [SRC `core/.../plugin/context/{EventTrigger,CommonFunctions,CommonConditions}.java`]
- Triggers: `left_click`, `right_click` (aliases `use_on`, `use`, `use_item_on`), `attack` (`hit`), `consume` (`eat`, `drink`), `block_break` (`dig`; blocks also accept `break`), `item_break`, `furniture_break`, `place` (`build`), `pick_up` (`pick`), `step`, `fall`, `shoot`. New triggers can be registered with `EventTrigger.register(Key, aliases…)`.
- Functions: `command, message, actionbar, title, open_window, cancel_event, run, place_block, update_block_property, transform_block, break_block, update_interaction_tick, set_count, drop_loot, swing_hand, set_food, set_saturation, play_sound, particle, potion_effect, remove_potion_effect, leveler_exp, set_cooldown, set_item_cooldown, remove_cooldown, spawn_furniture, remove_furniture, replace_furniture, rotate_furniture, set_furniture_variant, teleport, transfer, set_variable, toast, damage, heal, merchant_trade, remove_entity, if_else, alternatives, when, damage_item, cycle_block_property, set_exp, set_level, play_totem_animation, close_inventory, discover_recipe, clear_item, js`.
- Conditions: `has_player, has_item, match_item, equipment, equipment_set, match_entity, match_block, match_block_property, table_bonus, survives_explosion, any_of, all_of, enchantment, inverted, falling_block, random, distance, permission, has_discovered_recipe, equals, regex, string_equals, string_contains, expression, is_null, hand, on_cooldown, on_item_cooldown, inventory_has_item, match_furniture_variant, is_bedrock_player, js, open_water, biome, world`. Prefix `!` negates (`type: "!has_item"`).
- External plugins can register their own: `CommonFunctions.register(Key, FunctionFactory)` and `CommonConditions.register(Key, ConditionFactory)` (BukkitCraftEngine itself registers `ce:test_flag`).
```yaml
# netherite_anvil.yml
events:
  - on: right_click
    functions:
      - type: open_window
        gui_type: anvil
      - type: cancel_event
    conditions:
      - type: expression
        expression: '!<arg:player.is_sneaking>'
```

### 4.12 Images, fonts, emoji, GUI
- `images:` register glyphs in a font: `{height, ascent, font, file, grid_size: cols,rows}`. Reference a glyph as `<image:ns:id>` or `ns:id:row:col` for a grid. Codepoints are auto-assigned (`image.codepoint-starting-value`, `minecraft:default` uses U+E000–U+F8FF). [CFG internal/gui.yml, emoji.yml, config.yml L465–527]
- `<shift:N>` uses offset characters in `minecraft:default` (config.yml `image.offset-characters`).
- GUI backgrounds: CE's own browser uses titles like `"<white><shift:-11><image:internal:item_browser>"`. The `network.intercept-packets.container: true` option makes CE parse these tags in **any plugin's** container titles (and chat, titles, bossbar, scoreboard, item names and lore and more), so our plugin can write `<image:tfc:anvil_gui>` into inventory titles. [CFG config.yml L553–571, L672–728] **UNVERIFIED**: exact tag syntax accepted inside third-party titles (MiniMessage-like string as plain text?).
- `emoji:` maps keywords to images with permission and per-context templates. [CFG emoji.yml]
- `CraftEngineImages.byId(Key)` returns an `Image` for building components in code. [SRC api]
- `categor(y|ies)`: item browser categories (`/ce` opens the browser).

### 4.13 Lang vs translations
- `lang:` → client resource-pack lang files (`<lang:item.ns.id>` = vanilla translatable). [CFG default_assets/langs/en_us.yml]
- `translations:` → server-side per-player-locale strings (`<l10n:key>`). [CFG internal/translations/en.yml]

### 4.14 Equipment / armor [CFG `items/topaz_armor.yml`, config.yml L360–376]
```yaml
equipments:
  $$>=1.21.2:
    default:topaz:
      type: component
      humanoid: minecraft:entity/equipment/humanoid/topaz
      humanoid_leggings: minecraft:entity/equipment/humanoid_leggings/topaz
  $$<1.21.2:
    default:topaz:
      type: trim          # sacrifices vanilla chainmail trim slot (equipment.sacrificed-vanilla-armor)
```
Items reference them via `settings.equipment` / `equippable`.

### 4.15 Worldgen features [WIKI `configuration/worldgen_feature`, `compatibility/datapack`; SRC `BukkitWorldManager` L800–1000]
- `configured_feature(s)` and `placed_feature(s)` use **vanilla datapack JSON as YAML**, parsed with the vanilla `ConfiguredFeature`/`PlacedFeature` codecs. CE block ids are accepted where vanilla ids are. Extra fields on placed features: `world(s)`, `dimension(s)`, `dimension_type(s)|environment(s)`, `biome(s)` (needs a `minecraft:biome` placement).
- CE providers for custom blocks: `craftengine:simple_state_provider`, `weighted_state_provider`, `rotated_block_provider`, `randomized_int_state_provider`, and feature `craftengine:simple_block` (handles multi-block such as doors).
- Datapacks (Iris, Terra and others) can use CE blocks because they are real registry blocks. In contexts that don't resolve CE ids, use the internal id `craftengine:custom_N` (pin it with `states.id`). [WIKI datapack caution]
- `/ce feature place-feature …` command; the `sapling_block` behavior takes `feature: ns:tree`.

### 4.16 client_bound_* (Premium only)
`client_bound_data` is item processors applied only to the client-side copy. The source only honors it when `VersionHelper.PREMIUM` is true. **Do not rely on it.** [SRC `AbstractItemManager` L667–673]

---

## 5. The visual-state system

### 5.1 block_state_mappings (`resources/internal/configuration/mappings.yml`, 4554 lines) [CFG, SRC `BlockStateMappingParser`]
`before: after`. Every real vanilla `before` state is **sent to clients as `after`**. The freed `before` becomes a **candidate** in every `AutoStateGroup` whose block set and predicate match. Only mapped states can be auto-allocated. Conflicting mappings are errors. Users and other packs can add their own `block_state_mappings` in any configuration file.

Default mapping counts (per block; from mappings.yml, comments say "feel free to add or remove"):

| Section | What is freed | Count |
|---|---|---|
| Anvil | east≡west, north≡south (×3 anvils) | 6 |
| Sapling | stage 1→0 (8 types incl. pale_oak ≥1.21.4) | 8 |
| Mushroom | brown/red/stem: 63 each (all sides → all-true) | 189 |
| Kelp | top-block ages | 25 |
| Vines | cave_vines 48, weeping 25, twisting 25 | 98 |
| Sugar cane / Cactus | ages 1–15 → 0 | 15 / 15 |
| Leaves | distance/persistent variants per leaf type (26 each; spruce/birch fixed color) | ~26 × 13 |
| Tripwire | 126 of 128 (keeps 2 vanilla looks) | 126 |
| Note block | 1100 (+200 on ≥26.1) | 1100/1300 |
| Trapdoor / Door | powered≡unpowered, 32 each × ~22 types | ~700 each |
| Fence gate | 16 × 12 wood types | 192 |
| Slab / Stairs | petrified_oak & copper slabs (6 each), cut copper stairs (80 each) | ~30 / 320 |
| Grate, pressure plate (weighted, 14 each), lightning rod (24 each ≥1.21.9), copper bars/chain/lantern (≥1.21.9) | | |
| Corals, chorus plant | **commented out** (chorus group empty by default) | 0 |

### 5.2 AutoStateGroup (exact) [SRC `core/.../block/AutoStateGroup.java`]
| id(s) | blocks | predicate |
|---|---|---|
| `solid` | brown/red mushroom block, mushroom stem, note_block | any (≈1100–1300 + 189) |
| `note_block` | note_block | any |
| `mushroom` / `brown_mushroom_block` / `red_mushroom_block` / `mushroom_stem` | mushroom blocks | any |
| `leaves` / `waterlogged_leaves` | all 11 leaves | waterlogged false/true |
| `tintable_leaves` / `waterlogged_tintable_leaves` | oak, jungle, acacia, dark_oak, mangrove | waterlogged false/true |
| `non_tintable_leaves` (`no_tint_leaves`, `leaves_no_tint`) / `waterlogged_non_tintable_leaves` | azalea, flowering_azalea, cherry, pale_oak, spruce, birch | |
| `tripwire` / `lower_tripwire` (attached=true) / `higher_tripwire` (attached=false) | tripwire | |
| `sugar_cane`, `cactus`, `kelp`, `chorus`, `cave_vines` (`cave_vine`), `weeping_vines`, `twisting_vines` | resp. | any |
| `pressure_plate` | light/heavy weighted pressure plate | any |
| `sapling` | 8 saplings | any |

- Form: `auto_state: solid` or `auto_state: {type: solid, id: shared_key}`. The same `id` shares one vanilla state. Allocation is **stable**, cached by name in `cache/visual_block_states.json`. [SRC `VisualBlockStateAllocator`]
- When a group runs out, you get a `resource.block.state.visual_state_exhausted` error. [SRC]
- Debug commands: `/ce debug auto-state-usage <group>` (lists candidates and usage), `/ce debug visual-state-usage`, `/ce debug real-state-usage` (internal ids), `/ce debug get-block-internal-id`, `/ce debug target-block`, `/ce debug is-section-injected`, `/ce debug export-block-state-mappings`. [CFG commands.yml; SRC `DebugAutoStateUsageCommand`]

### 5.3 Explicit `state:` and coexistence [SRC `VisualBlockStateAllocator.assignFixedBlockState`, `AbstractBlockManager.arrangeModelForStateAndVerify`]
- Any vanilla state string works (`minecraft:stone`, `oak_button[face=floor,facing=east,powered=false]`, `note_block:0` shorthand for mapped states).
- If the state is also an auto candidate, it is marked used, so auto allocation skips it.
- If a `model` is given, a variant for that exact property string is written into `assets/<ns>/blockstates/<block>.json`. **This changes how every real instance of that vanilla state looks.** So `state: minecraft:stone` plus a granite model makes all vanilla stone look like TFC granite. That is fine in a TFC world, but stone placed by players or found in other worlds is affected too. To avoid it, also add `block_state_mappings: { stone: <something> }` so real stone is sent as something else. [INF]
- Two custom blocks may share one explicit state only if they use the **identical** model JSON. Otherwise `resource.block.state.model_conflict` is raised. [SRC]
- Blockstate generation adds and replaces variants only for overridden property strings and **drops `multipart`** from an existing file. [SRC `generateBlockOverrides` L3405–3449] This means multipart blocks (fences, walls, glass panes, chorus, …) are tricky as explicit visual states. The fence and button templates avoid model overrides and use entity renderers instead.
- **For TFC**, single-state vanilla blocks that never appear in a TFC world (stone variants, ores, deepslate ores, wool ×16, concrete ×16, terracotta ×17, glazed terracotta ×16 (4 facings each), concrete powder, planks, …) give **one visual per state** with full-cube collision and their own sound type. Combined with the `solid` pool (~1.3–1.5k), this covers rock and ore blocks. Small ore pieces and pebbles: tripwire (lower/higher), button states (very small hitbox, explicit state), carpets (1/16 high), pressure plates.

---

## 6. Hitbox / shape semantics, light, sounds

- **Outline and collision shape = the visual vanilla state's shape** (server-side, `BukkitBlockShape.getShape/getCollisionShape` delegate to `rawBlockState = nmsVisualState`). `support_shape: <vanilla state>` overrides only the support shape (for what can attach or stand on top). [SRC `bukkit/.../block/BukkitBlockShape.java`, `BukkitBlockManager` L312]
  ```java
  public Object getShape(Object thisObj, Object[] args) {
      return BlockBehaviourProxy.BlockStateBaseProxy.INSTANCE.getShape(this.rawBlockState, args[1], args[2], args[3]);
  }
  ```
  So the server and client agree on hitboxes. Choose the visual state for the hitbox you want (button = tiny, carpet = 1/16 slab, tripwire low/high, cactus 14×15×14, sugar cane = no collision, barrier = full and invisible, bed foot = 9/16).
- **Client-predicted behaviors come from the visual block.** Climbability (vines and similar keep the `climbable` client tag, per the mappings.yml comment), note-block interaction prediction (CE tracks `isClientSideNoteBlock`/`isClientSideTripwire` flags to compensate), bounce on bed or slime, slowdown from cobweb or honey (client-side movement), and leaves particles. [CFG mappings.yml comments; SRC `BlockGenerator` fields] Server-side `friction`, `speed_factor` and `jump_factor` are settable, but the client predicts from the visual block. **UNVERIFIED**: how CE reconciles these (it may resync player position; see `bouncing_block.sync_player_position`).
- Derived defaults from the visual state when unset: `can_occlude`, `use_shape_for_light_occlusion`, light dampening (`block_light`), `propagate_skylight`, `is_suffocating` (from `canBlockView(visual)`), `is_view_blocking`. Settable: `luminance`, `map_color`, `instrument`, `push_reaction`, `fluid_state` (water), `is_randomly_ticking`, `tags` (server tags on the `custom_N` holder). [SRC `applyPlatformSettings`]
- Light: the custom light system (`block.light-system.enable/async`) updates light where server and client states disagree. Skylight occlusion is decided purely client-side. [WIKI block settings; SRC `WorldStorageInjector.updateLight`]
- **Sound system**: for every *visual* vanilla state in use, CE renames that vanilla block's SoundType events (place/break/step/hit) to `replaced.*` in the pack (silenced on the client) and plays the correct sounds server-side for both vanilla and custom blocks. [SRC `BukkitBlockManager.processSounds` L571–611; CFG config.yml L383–392] Consequence: using `minecraft:stone` as a visual affects the shared `stone` SoundType, which many vanilla blocks use. CE handles the replay, but the logic runs server-side.

---

## 7. Resource pack generation & delivery [CFG config.yml L21–292; SRC `AbstractPackManager`]
- **Workflows**: `resource-pack.workflows.<name>: {trigger: reload_pack, steps: [generate, validate, optimize, zip{path, store-png, protection}, upload{pack}, send_pack{pack}]}`.
- Generation runs in an in-memory FS (Jimfs). It merges all packs' `resourcepack/` folders, external folders and zips, and cache-event paths, then generates models, item definitions (1.21.2, 1.21.4+ and legacy overrides), sounds, client lang, equipment, particles, atlases and blockstate overrides. `pack.mcmeta` with overlays (`overlay-format: ce_overlay_{version}`) covers `supported-version.min/max`. [SRC L769–830]
- Conflicts are handled by `duplicated-files-handler` rules (`merge_json`, `merge_legacy_model`, `merge_pack_mcmeta`, `merge_atlas`, `merge_font`, `retain_matching`). [CFG L236–292]
- Validation: `fix-atlas`, `fix-missing-texture`, `fallback-models` (26.1 texture format, rotation angles), UV clamping.
- Optimization: PNG (optional Zopfli) and JSON minify, with cache.
- Hosting: `packs.<id>.type: self` (built-in HTTP server: `self-host.ip/port/protocol`, one-time token, rate limiting) or `external`, `lobfile`, `mcpacks`, `s3` (Cloudflare R2, COS), `openlist`, `onedrive`, `gitlab`, `dropbox`, or a zip-only export. Presets let players choose packs (`/pack`). [WIKI `getting_start/set_up_host`]
- Delivery: `send-on-join`, `kick-if-declined`, `strict-player-uuid-validation`, prompt. [CFG L215–234]
- Obfuscation and protection: Premium only.
- Limits: the client supports ≥1.16 resource packs (comment L71). Item CMD values ≤ 16,777,216.

---

## 8. Java API

### 8.1 Coordinates [README; `core/build.gradle.kts`, `bukkit/build.gradle.kts`, `bukkit/proxy/build.gradle.kts`]
```kotlin
repositories { maven("https://repo.momirealms.net/releases/") }
dependencies {
    compileOnly("net.momirealms:craft-engine-core:26.9")     // README uses 26.9; repo metadata <release> = 26.9.1
    compileOnly("net.momirealms:craft-engine-bukkit:26.9")
    // optional, unstable NMS proxies:
    compileOnly("net.momirealms:craft-engine-bukkit-proxy:26.9")
}
```
At research time, `craft-engine-bukkit:26.9.2` gave **404** in the repo; the latest published release in `maven-metadata.xml` is 26.9.1. **Relocations** apply to the published jars: `net.kyori` → `net.momirealms.craftengine.libraries` (CE's Adventure is **not** Paper's Adventure), `net.momirealms.sparrow.nbt` → `net.momirealms.craftengine.libraries.nbt` (so `CompoundTag` in API signatures is the relocated class), and bytebuddy, caffeine, snakeyaml and others. [SRC `buildSrc/.../RelocationExtension.kt`] Declare `CraftEngine` as a dependency in our `paper-plugin.yml` with `load: BEFORE` for us, so we load after it.

### 8.2 Entry classes (`bukkit/src/main/java/net/momirealms/craftengine/bukkit/api/`)
**`CraftEngineBlocks`**
```java
static Map<Key, BlockDefinition> loadedBlocks();            // empty during onEnable → use CraftEngineReloadEvent
static @Nullable BlockDefinition byId(Key id);
static boolean place(Location, ImmutableBlockState, boolean playSound);
static boolean place(Location, ImmutableBlockState, int flags /*UpdateFlags*/, boolean playSound, boolean checkCanSurvive);
static boolean place(Location, Key blockId, CompoundTag properties, int flags, boolean playSound, boolean checkCanSurvive);
static boolean remove(Block); static boolean remove(Block, @Nullable Player, boolean movedByPiston, boolean dropLoot, boolean sendLevelEvent);
static boolean isCustomBlock(Block);
static @Nullable ImmutableBlockState getCustomBlockState(Block);
static @Nullable ImmutableBlockState getCustomBlockState(BlockData);
static BlockData getBukkitBlockData(ImmutableBlockState);   // real NMS custom_N state as CraftBlockData
static boolean isVanillaBlockState(int id);
```
`place` is a plain NMS `LevelWriter.setBlock` plus `onPlace`. There is no thread hop, so call it on the main thread or the owning Folia region thread. [SRC]
`BlockDefinition`: `defaultState()`, `getBlockState(CompoundTag props)`, `getProperty(name)`, `variantProvider().states()`. `ImmutableBlockState`: `with(Property, value)`, `cycle(Property)`, `get(Property)`, `owner()`, `settings()`, `behavior()`, `customBlockState()`, `visualBlockState()`. [SRC core]

**`CraftEngineItems`**: `loadedItems()`, `byId(Key|String)` → `BukkitItemDefinition` (`buildBukkitItem()`, `buildBukkitItem(Player)`, `buildBukkitItem(ItemBuildContext,int)`), `byItemStack(ItemStack)`, `isCustomItem(ItemStack)`, `getCustomItemId(ItemStack)`.
**`CraftEngineFurniture`**: `loadedFurniture()`, `byId`, `place(Location, Key id[, String variant[, boolean playSound]])`, `place(Location, FurnitureDefinition, CompoundTag data, boolean)`, `rayTrace(...)`, `isFurniture(Entity)`, `isCollisionEntity`, `isSeat`, `getLoadedFurnitureByMetaEntity/BySeat/ByCollider`, `remove(...)`.
**`CraftEngineImages`**: `loadedImages()`, `byId(Key)`.
**`BukkitAdaptor`**: `adapt(Player)` → `BukkitServerPlayer`, `adapt(World)` → `BukkitWorld` (CE world wrapper; storage via `storageWorld()`), `adapt(Entity)`, `adapt(Block)` → `BukkitExistingBlock`, `adapt(ItemStack)` → `BukkitItem`.

### 8.3 Events (`bukkit/.../api/event`)
`CraftEngineReloadEvent` (`isFirstReload()`), `AsyncResourcePackCacheEvent` (`registerExternalResourcePack(Path)`), `AsyncResourcePackGenerateEvent` (`resourcePackFolder()`, `zipFilePath()`), `AsyncResourcePackPrepareEvent` (per player pack ids), `CustomBlockAttemptPlaceEvent` / `CustomBlockPlaceEvent` / `CustomBlockBreakEvent` (`setDropItems`) / `CustomBlockInteractEvent` (action, hand, face, interactionPoint, item) (all cancellable, player events), `FurnitureAttemptPlaceEvent`, `FurniturePlaceEvent`, `FurnitureBreakEvent`, `FurnitureHitEvent`, `FurnitureInteractEvent`, `BlockDispenseProjectileEvent`, `ServerPreShutdownEvent`.

### 8.4 Registering custom behaviors from an external plugin
Registry pattern [SRC `core/.../block/behavior/BlockBehaviors.java`]:
```java
public static <T extends BlockBehavior> BlockBehaviorType<T> register(Key key, BlockBehaviorFactory<T> factory) {
    BlockBehaviorType<T> type = new BlockBehaviorType<>(key, factory);
    ((WritableRegistry<BlockBehaviorType<? extends BlockBehavior>>) BuiltInRegistries.BLOCK_BEHAVIOR_TYPE)
            .register(ResourceKey.create(Registries.BLOCK_BEHAVIOR_TYPE.location(), key), type);
    return type;
}
public static BlockBehavior fromConfig(BlockDefinition block, ConfigSection section) {
    Key key = Key.ce(section.getNonEmptyString("type"));   // no namespace → "craftengine:"
    ...
}
```
Factory: `interface BlockBehaviorFactory<T> { T create(BlockDefinition block, ConfigSection section); }` with helpers `BlockBehaviorFactory.getProperty(path, block, "age", Integer.class)` and `getOptionalProperty`.
Wiki example [WIKI `api/block_behavior`]:
```java
// Register once, before CE parses block configurations that use this ID.
BlockBehaviors.register(Key.of("myplugin:particles"), SimpleParticleBlockBehavior.FACTORY);
```
```yaml
blocks:
  myplugin:smoke_block:
    behavior:
      type: myplugin:particles
```
Implementation: extend `net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior`. NMS-level callbacks receive `(Object thisBlock, Object[] args)` with NMS objects; the documented arg layouts are in javadoc in `core/.../block/behavior/BlockBehavior.java`, for example `randomTick: args[0]=BlockState, args[1]=ServerLevel, args[2]=BlockPos, args[3]=RandomSource`. Higher-level callbacks: `updateStateForPlacement(BlockPlaceContext, ImmutableBlockState)`, `useOnBlock(UseOnContext, state)`, `useWithoutItem(UseOnContext, state)`, `canBeReplaced`, `canPlaceMultiState`, `itemToPickup`. Convert an NMS state to a CE state with `BlockStateUtils.getOptionalCustomBlockState(Object nmsState)`. Real example `CropBlockBehavior.randomTick` uses `customState.with(ageProperty, after).customBlockState().minecraftState()` and `CraftEventFactoryProxy.handleBlockGrowEvent` (proxy artifact). Composite blocks: use `state.behavior().getFirst(MyBehavior.class)`. Do not cast.
- **Block entities** (per-block data, tickers): the behavior implements `EntityBlock` → `createBlockEntityController(BlockEntity)` and `initControllerId(int)`. A controller extends `BlockEntityController` with `saveCustomData/loadCustomData(CompoundTag)`, `onLoad/onUnload/onRemove/preBlockStateChange`, `createBlockEntityTicker` (sync: main or Folia region thread) and `createAsyncBlockEntityTicker`, `SleepingBlockEntityTicker` (sleep/wakeUp), and `subscribeChunkLoad(pos, cb)`. Call `world.blockEntityChanged(pos)` after mutation. Look up the controller with `context.getLevel().storageWorld().getBlockEntityAtIfLoaded(pos)` then `entity.controller.let(MyController.class, controllerId, c -> …)`. [WIKI `api/block_entity`, `api/block_entity_ticker`] This is well suited to TFC's per-block data (crop growth, firepit or forge temperature, barrels).
- Other registries with static `register(Key, Factory)`: `ItemBehaviors.register` (factory `create(Pack, Path, Key id, ConfigSection)`), `BlockEntityElementConfigs.register`, `BlockEntityTintSources.register`, `CommonFunctions.register`, `CommonConditions.register`, `EventTrigger.register`, `TemplateArguments.register`, `BlockSettingsModifiers` (`BLOCK_SETTINGS_TYPE`), and furniture behavior/hitbox/element registries. Full list: `core/.../registry/BuiltInRegistries.java` L113–175. Custom top-level config sections: `CraftEngine.instance().packManager().registerConfigSectionParser(ConfigParser)`. [SRC `AbstractPackManager` L464–475]
- **Timing**: `BukkitBlockBehaviors.init()` runs in CE's `onLoad`. Configs parse on the first tick (default) or in CE's onEnable (`delay-configuration-load: false`). Register in our `onLoad()` to be safe in both modes, with CE as a load-before dependency. [SRC §3.3] **UNVERIFIED**: whether registries ever freeze. No freeze call was found in `core/.../registry`, which suggests late registration works but is pointless after parsing.

### 8.5 Thread safety / Folia
- API `place` and `remove` call NMS directly with no scheduling, so they need the owning thread (Paper main, Folia region). [SRC `CraftEngineBlocks`]
- CE has Folia-specific classes (`FoliaCEWorld`, `FoliaCEChunk`). On Folia, chunk-load handling for existing chunks is scheduled per region. [SRC `BukkitWorldManager.handleWorldLoad`]
- Async tickers run during the world's async tick and must not mutate the world. [WIKI block_entity_ticker]
- CE fires its events from its own listeners (main or region thread); the Async* events are async.

---

## 9. Storage, chunk lifecycle & world generation

### 9.1 Storage [SRC `BukkitStorageAdaptor`, `CEWorld.REGION_DIRECTORY = "craftengine"`, `DefaultSectionSerializer`]
- Per world: `<worldFolder>/craftengine/` region files in `linear` (default) or `mca` format, or `pdc` (chunk PersistentDataContainer), or `none`. Slime worlds use the Slime format automatically. Compression defaults to LZ4. Async write and read, plus a decoded-chunk cache (`cache-mode: lifecycle` uses Moonrise hooks on Paper/Folia 1.21.4+). [CFG config.yml L730–810]
- Section format: a palette of `{id: "<ce block id>", properties: {...}}` plus packed data. It is **keyed by CE id, not internal id**, so reallocating `custom_N` doesn't corrupt saved CE data. Block entities and constant renderer positions are saved per chunk.
- Unknown ids on load become `InactiveBlockDefinition`. `process-invalid-blocks.convert/remove` remap them. [SRC `DefaultSectionSerializer.deserialize`]
- Migration: `/ce feature migrate-world-storage`, `clear-world-storage`; 26.1+ auto-migrates the old world-folder layout with a backup. [SRC `PaperCraftEngineBootstrap.backupWorldData`]

### 9.2 Chunk lifecycle [SRC `BukkitWorldManager.handleChunkLoad` L633–778, `handleChunkUnload` L525–590]
- **Unload** (`restore-vanilla-blocks-on-chunk-unload: true`): every CE state in the section is replaced by its `restoreBlockState` (= visual state; barrier → glass) **in the vanilla chunk** before Minecraft saves it. The vanilla region files therefore contain only vanilla states, and removing CE leaves the visual blocks behind.
- **Load** (`restore-custom-blocks-on-chunk-load: true`): CE data is read and each stored state is written back into the NMS section as the real `custom_N` state. Light is re-checked, constant renderers are re-registered, the section is re-injected, block entities are activated.
- `sync-custom-blocks-on-chunk-load: false` (default): if true, NMS palettes are scanned for `custom_N` states that are *not* in CE storage and added. This exists for edits made through the CraftEngine Fabric mod (Axiom/WorldEdit). [CFG L789–791] **[INF]** It is also the safety net for any CE states that reached vanilla chunk data without passing through an injected section.
- Section injection: `chunk-system.injection.target: section` (wraps `LevelChunkSection`) or `palette` (wraps `PalettedContainer`). Every `setBlockState` calls `WorldStorageInjector.compareAndUpdateBlockState`, which mirrors the change into CE storage and handles block entities, renderers, destroy stages and light. [SRC `WorldStorageInjector.java`]

### 9.3 World generation [SRC `BukkitWorldManager.injectWorldGeneration` L472–514, `handleChunkGenerate` L592–631; nms-helper `InjectedCustomChunkGenerator` (javap)]
- On world init (Paper only: `if (previous == null && VersionHelper.hasPaperPatch) injectWorldGeneration(...)`), CE replaces the generator in `ChunkMap.worldGenContext` (1.21+) or `ChunkMap.generator` (1.20) with `FastNMS.createInjectedChunkGenerator(world, previousGenerator)`. This is a delegating `ChunkGenerator` that wraps **whatever generator the world has**: vanilla `NoiseBasedChunkGenerator`, or CraftBukkit's `CustomChunkGenerator` for plugin `ChunkGenerator`s.
- Bytecode of `v1_21_5/v1_21_9` `InjectedCustomChunkGenerator`: `fillFromNoise` → NOISE, `buildSurface` → SURFACE, `applyCarvers` → CARVER, `createStructures` → STRUCTURE, `applyBiomeDecoration` (3-arg) → FEATURE. In `v26_3`, `buildTerrain` covers NOISE, SURFACE and CARVER together; `applyBiomeDecoration` covers FEATURE. Before delegating to the target, each one calls `BukkitWorldManager.handleChunkGenerate(world, pos, chunkAccess, stage)`. That reads or creates the CE chunk, **injects all sections**, and returns a Runnable (run after the stage) that writes CE data if anything changed. Stages can be disabled with `chunk-system.generation.{noise,surface,structure,carver,feature}` (all true by default).
- **Consequence [INF, high confidence]:** a Bukkit `ChunkGenerator` (`generateNoise/Surface/Bedrock/Caves` writing `ChunkData.setBlock(x,y,z, blockData)`) or a `BlockPopulator` (`LimitedRegion.setBlockData`) that writes `CraftEngineBlocks.getBukkitBlockData(state)` (a `CraftBlockData` wrapping the NMS `custom_N` state) goes through the injected sections, so CE records it. These run off the main thread in Paper's worldgen workers; CE's injection is designed for those threads (`synchronized (sections)`). Datapack features (`configured_feature` with CE ids or `craftengine:*` providers) work the same way.
  - Requirements: (a) **Paper** (the injection is skipped on Spigot), (b) CE configs loaded before generation (so `ImmutableBlockState`s exist and `custom_N` → behavior/visual bindings are set), (c) the world generator is installed and storage is ready, (d) the chunk-system.generation stages are enabled.
  - **Startup caveat:** with `misc.delay-configuration-load: true` (default), the world listeners and `worldManager.delayedInit()` are registered **only after the first-tick config reload**. Spawn chunks generated during server startup are therefore generated (i) before CE's block definitions exist and (ii) without the injected generator (unless something called `BukkitAdaptor.adapt(world)` earlier, which calls `ensureStorageWorld` → `installStorageWorld` → `injectWorldGeneration`). For a custom-generator TFC world, **set `misc.delay-configuration-load: false`** (the config comment says to do this when datapack-generated spawn chunks are needed), make sure our pack files exist before CE's onEnable (write them in our `onLoad`), and register behaviors in our `onLoad`. Alternatively make the TFC world non-default and create it after `CraftEngineReloadEvent(firstReload)`. [INF; SRC `CraftEngine.onPluginEnable` L452–527]
  - Getting `ImmutableBlockState` inside a generator: cache `BlockDefinition`/state objects after `CraftEngineReloadEvent`. They are swapped on every reload, so refresh the cache on each reload event.
- Placing during generation with `CraftEngineBlocks.place(...)` is wrong: it needs a live `Level`/`Location` and calls `onPlace` neighbor updates. Use BlockData writes. [INF]

---

## 10. Performance notes
- **Block states**: one `custom_N` NMS block per CE state. The global palette grows by `serverside-blocks`. Raise it in steps of ~1000 ("It is recommended to increase it by 1000 each time", restart needed). [CFG L379–381] [INF] Total registry states going past 2^15 adds one bit per entry to direct-palette sections. Minor, but avoid a huge excess.
- **entity_renderer** = virtual packet entities, sent to every player tracking the chunk (spawn + metadata on track, despawn on untrack). Cost scales with renderers per chunk × players. Use real block models where you can and renderers only where needed. Culling is Premium. `view_range` and conditions reduce load. Prefer `translation`/`rotation` over `position`. [SRC `ItemDisplayBlockEntityElement`; WIKI]
- Chunk data: linear + LZ4 + lifecycle cache + async read/write are the defaults. `async-read` pre-reads CE data in `SerializableChunkData#read` before ChunkLoadEvent (Paper 1.21.4+). [CFG L744–766; SRC `BukkitWorldManager.onChunkDataRead`]
- Light system async (`block.light-system.async: true`).
- Packet handlers: disable unused `network.intercept-packets.*`. `performance-mode` processes at the NMS packet stage; plugins that write raw buffers may bypass it. `minimize-item-packets`. [CFG L529–590]
- `network.mod-channel.visual-block-states-max-per-packet: 5000` and `creative-tab-max-items-per-packet` only affect the **CraftEngine client mod** channel (Fabric mod for Axiom and WorldEdit visuals), not vanilla clients. [CFG L573–578; SRC `AbstractBlockManager.delayedLoad` sends `ClientboundVisualBlockStatesPacket` only to `clientCustomBlockEnabled()` players]
- Entity tracking (`entity.tracking`) defaults to players only. Item updater triggers are expensive (off by default).
- Block entity tickers: use `SleepingBlockEntityTicker` to avoid ticking idle blocks. [WIKI]

---

## 11. Gotchas / pitfalls
1. **Explicit `state:` with a model changes that vanilla state everywhere.** Map the vanilla state away with `block_state_mappings` if real instances can exist. One model per vanilla state, or you get a conflict error. (§5.3)
2. **Hitbox comes from the vanilla state.** No custom VoxelShapes. (§6)
3. **`multipart` is stripped** when CE writes a blockstate override, so avoid explicit visual overrides on multipart blocks. Use entity renderers instead. [SRC `generateBlockOverrides`]
4. **Premium-only `client_bound_*`** keys are silently ignored in Community.
5. **Config ids for behavior `type` default to the `craftengine:` namespace.** Always namespace our types (`tfc:…`).
6. **API timing**: `loadedBlocks()` and the others are empty before the first `CraftEngineReloadEvent`. Definitions are re-created on each reload, so do not hold stale references.
7. **Do not `/reload` or plugman CE.** It shuts the server down. [SRC]
8. **Worldgen at startup** with the default delayed config load (§9.3).
9. **Datapacks / external tools may need `craftengine:custom_N` ids.** These are only stable if the allocation cache is kept or `states.id` is pinned. [WIKI datapack caution; SRC IdAllocator]
10. **Sound replacement** applies to the whole SoundType of each visual block used. (§6)
11. `deceive-bukkit-material: bricks`: `Block#getType()` returns BRICKS for CE blocks unless `inject-bukkit-material`. Third-party plugins that check Material see bricks. Our code must use `CraftEngineBlocks.getCustomBlockState(block)`. [CFG L402–415]
12. Barrier visuals restore to **glass** on unload. [SRC]
13. Adventure in CE's API is **relocated** (`net.momirealms.craftengine.libraries.*`). Do not pass Paper Components into CE API methods that expect CE's Component. [SRC RelocationExtension]
14. Light: skylight occlusion is client-decided. `block_light`/`can_occlude` only affect block light. [WIKI settings]
15. Hardcoded vanilla renderers plus non-full models can cause X-ray. [WIKI entity_renderer]
16. Tripwire and note block visuals: the client predicts note-block interaction and tripwire string behavior. CE has special flags to fix this, but there are known visual glitches (mappings.yml comments: "the client always thinks it's interactive… visual glitches when the client try predicting instrument changes").
17. Vines used as visuals are always climbable client-side ("Unless you tweak the vine's block tag"). Use `settings.client_bound_tags` on vanilla blocks to change client tags. [CFG mappings.yml, SRC parseVanillaBlock]
18. Removing CE: blocks revert to their visual vanilla look, because of restore-on-unload, and that only happens for chunks that were unloaded or saved while CE ran.
19. Folder names starting with `.` are ignored. An invalid-namespace folder name makes the pack namespace `minecraft`.
20. GitHub issues were **not reviewed**: the API is session-restricted and momirealms.net was blocked. See §13.

---

## 12. Recommended integration recipe for our plugin
1. Ship a CE pack inside our jar and, in **`onLoad()`**, write or update `plugins/CraftEngine/resources/terrafirmacraft/` (`pack.yml` with `namespace: tfc`, `configuration/**/*.yml`, `resourcepack/assets/tfc/...`). Only rewrite changed files, because CE caches by mtime and size.
2. Generate YAML with `config_factory` and templates (rock × block type, ore × rock × grade) rather than thousands of literal entries. Use `default_templates` where they fit (pillar logs, leaves, slab, stairs, door, button, fence) or our own templates.
3. Register `tfc:*` block behaviors, item behaviors, functions and conditions in `onLoad()` (`BlockBehaviors.register(Key.of("tfc","…"), factory)`), with CE loaded first (`paper-plugin.yml` dependency).
4. Set or ask admins to set `block.serverside-blocks` large enough (estimate states first) and `misc.delay-configuration-load: false` if the TFC world is generated at startup. Add BetterHud's pack to `merge-external-*`; BetterModel's `BetterModel/build.zip` is already merged by default.
5. Visual budget: full cubes on unused single-state vanilla blocks (explicit states plus `block_state_mappings` to hide real ones) and the `solid` pool. Small items on button, tripwire or carpet states, or `transparent` + `entity_renderer` (shared `id: transparent`).
6. Worldgen: in our `ChunkGenerator`/populators, write `CraftEngineBlocks.getBukkitBlockData(state)`. Cache states per `CraftEngineReloadEvent`. Verify with `/ce debug is-section-injected` and `/ce debug target-block` that generated blocks are tracked.
7. After changing our pack at runtime: `/ce reload all` (or trigger the workflow via `packManager().triggerWorkflows("reload_pack")`, an internal API).

---

## 13. UNVERIFIED / open questions
1. **Bukkit `ChunkGenerator`/`BlockPopulator` placing CE BlockData**: inferred from the injected generator bytecode and `WorldStorageInjector`. Not runtime-tested. In particular: does CraftBukkit's `CustomChunkGenerator` write `ChunkData` into the same ProtoChunk section objects that CE injected (Paper's `CraftChunkData` writes directly to ProtoChunk sections, which should be the case)? Test on 1.21.x and 26.x, Paper and Folia.
2. Whether spawn-chunk generation before the delayed CE load leaves untracked `custom_N` states in vanilla region files (would need `sync-custom-blocks-on-chunk-load: true` to recover), or whether those chunks only contain blocks with unbound behaviors. Needs testing.
3. Blockstate files that override only some variants: the report assumes the Minecraft client stacks blockstate definitions across packs, so unoverridden states keep their vanilla model. CE does not ship vanilla blockstates, so this must be true for CE to work, but I did not verify it in CE source.
4. Exact Maven version to use: README says `26.9`. The repo metadata `<release>` is 26.9.1; 26.9.2 (Modrinth) was not on Maven at research time.
5. Registry freezing: no freeze found. Late registration after the first parse is untested.
6. Client-predicted movement properties (friction, speed, jump, bounce) when the visual block differs: how CE reconciles them (`bouncing_block.sync_player_position` hints at position resync).
7. `<image>`/`<shift>` tags in third-party container titles: whether they need MiniMessage-formatted plain-text titles and how Paper Component titles are handled.
8. Whether live reload (`/ce reload`) of block appearances updates already-sent chunks for online players, or whether they need a rejoin or chunk resend.
9. GitHub issues, DeepWiki and the wiki FAQ were not consulted (API restricted, host blocked). Known community pitfalls are therefore not included.
10. Exact note-block mapping count per MC version (1100 base + 200 on ≥26.1, counted from mappings.yml) and leaf-type counts on older versions (pale_oak ≥1.21.4, poplar leaves ≥26.3).
11. The GPL-3.0 vs EUPL-1.2 interaction for a plugin compiled against CE's API (compileOnly, not shaded) needs legal review.
