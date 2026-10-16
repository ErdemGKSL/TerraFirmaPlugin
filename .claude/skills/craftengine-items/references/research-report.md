# CraftEngine deep dive: items, tools, models, behaviors, equipment, recipes, loot, events

> Note for this repo: researched at CraftEngine commit b3079c5 (v26.9.2), 2026-10-16. Examples say `tfc:`; our provisional namespace is `tfp:` (see `CLAUDE.md`).

Researched 2026-10-16 against `github.com/Xiao-MoMi/craft-engine` commit `b3079c5` (v26.9.2, local clone `scratchpad/src/ce`), plus the wiki at `https://xiao-momi.github.io/craft-engine-wiki/` (downloaded copies converted to text in `scratchpad/wiki/*.txt`). This report extends `scratchpad/research/craftengine.md` (the general CE report). Where the two disagree, this report is the more specific one; the corrections are listed in §11.

**Path abbreviations**
- `core:` = `core/src/main/java/net/momirealms/craftengine/core/`
- `bukkit:` = `bukkit/src/main/java/net/momirealms/craftengine/bukkit/`
- `assets:` = `common-files/src/main/resources/resources/default_assets/configuration/`
- `templates:` = `common-files/src/main/resources/resources/default_templates/configuration/templates/`
- `config.yml` = `common-files/src/main/resources/config.yml`
- `wiki:<page>` = `https://xiao-momi.github.io/craft-engine-wiki/<page>/`

**Evidence tags:** **[SRC]** read in source · **[CFG]** shipped config · **[WIKI]** wiki page · **[INF]** my inference from source, not run · **UNVERIFIED** not confirmed.

**Edition caveat.** The open-source build has `VersionHelper.PREMIUM = false`. Every feature marked Premium below either does nothing or is silently ignored in Community.

---

## Table of contents
0. [TL;DR for TFC](#0-tldr-for-tfc)
1. [Item config](#1-item-config)
   - 1.1 Top-level keys · 1.2 Processing order · 1.3 `data:` processors · 1.4 `settings:` · 1.5 `material` · 1.6 Identity and PDC · 1.7 Updater · 1.8 Network rewriting and our packet layer
2. [Tools and mining](#2-tools-and-mining)
3. [Item models from the item side](#3-item-models-from-the-item-side)
4. [Item behaviors and custom Java behaviors](#4-item-behaviors)
5. [Equipment and armor](#5-equipment--armor)
6. [Recipes](#6-recipes)
7. [Loot tables and loot sources](#7-loot)
8. [Events, functions, conditions, context](#8-events-functions-conditions)
9. [Categories, item browser, creative](#9-categories--item-browser--creative)
10. [Java extension points (all registries)](#10-java-extension-points-summary)
11. [Pitfalls](#11-pitfalls)
12. [UNVERIFIED / open questions](#12-unverified--open-questions)

---

## 0. TL;DR for TFC

1. **A CE item is a vanilla `material` ItemStack plus components.** The CE id is stored as the string `craftengine:id` at the root of `minecraft:custom_data`. Bukkit PDC lives in the same component under `PublicBukkitValues`, so our PDC coexists safely. [SRC `bukkit:item/factory/ComponentItemFactory1_20_5.java` L54-78; `core:item/processor/PDCProcessor.java`]
2. **The material defines vanilla server behavior** (food/consumable, tool speed, base attributes, block placement, default stack size, equippability, vanilla tags and enchantability). Any vanilla component can be overridden under `data:` by its bare name (`tool:`, `max_stack_size:`, `consumable:`, `weapon:`). An unknown `data:` key that is a known component id becomes a raw component. [SRC `core:item/processor/ItemProcessors.java` `collectProcessors` L80-104]
3. **Tool tiers.** Item `settings.break_power` (int, default inherited from the vanilla material) is compared with block `required_break_power`. Tool types: item `settings.tags` (CE-internal tags) plus block `correct_tools: ["#tag"]` and `require_correct_tools: true`. Mining speed comes from the NMS destroy progress (block `hardness` and the item's vanilla `tool` component), multiplied by `incorrect_tool_dig_speed` (default 0.3) when the tool is wrong. All of this applies **only to CE blocks**. [SRC `bukkit:util/BlockStateUtils.isCorrectTool` L52-68; `bukkit:plugin/user/BukkitServerPlayer.getDestroyProgress` ~L945]
4. **Item behaviors have only 4 hooks:** `useOnBlock`, `useOnEntity`, `use` (right-click air), `onBreakBlock`. There is no inventory tick, no left-click, no consume, no use-duration and no release hook. Those need our own Bukkit listeners. Register with `ItemBehaviors.register(Key, ItemBehaviorFactory)`; the factory is `create(Pack, Path, Key itemId, ConfigSection)`. [SRC `core:item/behavior/*`]
5. **Recipes.** CE turns its YAML recipes into **real NMS recipes** in the server RecipeManager. It also re-reads all datapack recipes so that item tags containing CE items work. There is **no public API to add CE recipes from Java**; ship them as YAML. Recipes another plugin registers through Paper are **ignored** by CE's crafting listener. Recipe `functions` and `visual_result` are Premium-only. Recipe `conditions` are checked in the Community code path for crafting (the wiki says Premium). [SRC `bukkit:item/recipe/RecipeEventListener.java` L818-852, L856-858]
6. **Loot.** A CE block's drops come from its `loot:`, which can be inline, a template, or a **string id** (a CE `loots:` table or a datapack loot table). Drops are skipped when the tool is not correct. Use `loot_sources:` to inject into vanilla events, and the datapack entry `"type": "craftengine:item"` for datapack tables. Drop control from Java: `BlockBreakEvent#setDropItems(false)`, which CE copies into its own `CustomBlockBreakEvent`. [SRC `bukkit:block/listener/BlockEventListener.java` L144-216; `bukkit:plugin/injector/BlockStateGenerator.java` L120-150]
7. **Events, functions and conditions** are a small scripting layer. Custom functions, conditions, triggers, item processors, settings, loot functions, number providers and so on can all be registered from Java through static `register(Key, Factory)` methods (§10).
8. **Tooltips.** `client_bound_data` is Premium. CE still rewrites **every** outgoing item packet when `network.intercept-packets.item: true` (tag parsing, `rebase` of components) and restores items from a `craftengine:network_data` tag on the way back. Our own lore rewriting must strip itself on the creative-mode return path. See §1.8.

---

## 1. Item config

### 1.1 Top-level keys of an item entry
Parsed in `core:item/AbstractItemManager.java` `ItemParser.parseSection` L520-890 [SRC]. Keys accept kebab or snake case, and `(s)` means an optional plural (`core:plugin/config/ConfigKeys.java`).

| Key | Type | Effect | Notes |
|---|---|---|---|
| `material` | id | Server-side item type. Default `item.default-material` = `nether_brick` (config.yml L322). Ignored for vanilla ids. | L536 |
| `client_bound_material` | id | Item type shown to the client. | **Premium**; ignored otherwise (L538-539). |
| `custom_model_data` | int 1..16777216 | Forces a CMD value and, with a model, binds it in the allocator cache `cache/custom_model_data/<material>.json`. | L545-561. Without it, CMD is auto-allocated only when the pack min version is <1.21.2 or `always-use-custom-model-data` is on (L360-362). |
| `item_model` | id | Forces the `minecraft:item_model` component. When omitted on 1.21.2+, CE uses the **item id** (L618-629). | Pointing at a *vanilla* item model while using CMD → error `occupied_vanilla_item_model` (L859). |
| `model` / `models` / `texture(s)` / `blueprint` / `legacy_model` | | Model definition (§3). Without any of these, CE generates no model. | L733-737 |
| `hand_animation_on_swap` (true), `oversized_in_gui` (true), `swap_animation_scale` (1.0) | | Root fields of the generated 1.21.4 item definition. | L838-842 |
| `data` | map | Item processors, i.e. components (§1.3). | L660 |
| `client_bound_data` | map | Processors applied only to the client copy. | **Premium** (L666-670) |
| `settings` | map | Plugin-side mechanics (§1.4). | L687-693 |
| `behavior` / `behaviors` | map or list | Item behaviors (§4). Two behaviors become a `DualItemBehavior`, more become a `CompositeItemBehavior`. | L696-709 |
| `event` / `events` | map or list | Event functions (§8). For items, `break` is an alias of `item_break`. | L49, L676-684 |
| `category` | id or list | Adds the item to item-browser categories. | Only the literal key `category` is detected (L747). See pitfall §11. |
| `updater` | map of `int: op or [ops]` | Versioned migration (§1.7). | L712-731 |
| `override_data` | map | **Vanilla ids only**: changes the vanilla item's default prototype components on the server. Not synced to clients. | L523-533; wiki:configuration/item/data |
| `template`, `arguments`, `overrides`, `merges` | | Generic template system (see the general report §4.3). | |

Vanilla items can have entries too (`minecraft:carrot:` …). They get `settings`, `behavior`, `events` and `override_data`, but no `IdProcessor`. For vanilla ids, `ItemSettings.forItem(true)` sets `disable_vanilla_behavior=false`, `trigger_advancement=true` and `respect_repairable_component=true` [SRC `core:item/setting/ItemSettings.java` `forItem`].

Minimal real example [CFG `assets:items/topaz_tool_weapon.yml`]:
```yaml
items:
  default:topaz_pickaxe:
    material: golden_pickaxe
    settings:
      break_power: 3
      tags: [default:topaz_tools, minecraft:pickaxes]
    data:
      item_name: <!i><#FF8C00><lang:item.default.topaz_pickaxe>
      tooltip_style: minecraft:topaz
      max_damage: 64
    texture: minecraft:item/custom/topaz_pickaxe
```

### 1.2 Build pipeline and processor order [SRC]
`BukkitItemDefinition.build0` (`bukkit:item/BukkitItemDefinition.java` L62-71) creates `new ItemStack(material, count)` and applies the processors **in this order** (AbstractItemManager L640-675, `BukkitItemDefinition.Builder.build` L211-216):
1. Auto `CustomModelDataProcessor` / `ItemModelProcessor`, from the root `custom_model_data` / `item_model`.
2. `data:` processors **in YAML order**.
3. `IdProcessor` (custom items only).
4. `ItemVersionProcessor` (writes `craftengine:version` int into custom_data), if an `updater` exists.
5. Processors derived from **settings**: `equipment` → `equippable` and the asset id; `food` → food component. These run **after** `data:`, so they override it.

If every processor `isConstant()`, the built item is cached once and copied (`initConstantItem` L44-51). Non-constant processors (text with `<arg:...>`, random values, functions) rebuild on every call.

Duplicated keys: append `#suffix` (`insert_lore#1`, `attribute_modifiers#bonus`); the suffix is stripped, and `-` becomes `_` (`core:util/StringUtils.normalizeSettingsType` L118-170) [SRC, WIKI data].

### 1.3 `data:` processors (complete for this commit)
Registry: `core:item/processor/ItemProcessors.java` L18-70. Lookup is `Key.ce(key)`, so `tfc:foo` keys resolve to our own registered processors. If no processor matches but the key is a known vanilla data component (`DataComponentKeys.contains`, which covers `tool`, `weapon`, `consumable`, `max_stack_size`, `repairable`, `enchantable`, `damage_resistant`, `tooltip_display`, …), the key is treated as a raw component (L94-101). Any other unknown key is **silently ignored**.

| Key (aliases) | MC | YAML shape | Notes [SRC file] |
|---|---|---|---|
| `item_name` (`display_name`) | all | MiniMessage string; legacy `&` converted | `ItemNameProcessor`; constant unless it has `<arg>` or similar |
| `custom_name` | all | string; auto `<!i>` if `item.non-italic-tag` | `CustomNameProcessor` |
| `lore` | all | list of strings, **or** list of `{content: [..], operation: APPEND\|PREPEND, priority: int, split_lines: bool, conditions: [...]}` | `lore/LoreProcessor.java` |
| `dynamic_lore` | all | `{<context_name>: <lore list>}`; picks the context from the item tag `craftengine:display_context`, else the first entry | `lore/DynamicLoreProcessor.java` |
| `insert_lore` | all | `{position: HEAD\|TAIL\|BEFORE\|AFTER, pattern: regex, lore: [..], fallback: {...}}` or a plain list (TAIL) | `lore/InsertLoreProcessor.java` |
| `remove_lore` | all | regex string or `{pattern: regex}` | matches plain text |
| `overwritable_lore` / `overwritable_item_name` / `overwritable_custom_model_data` / `overwritable_item_model` / `overwritable_dyed_color` / `overwritable_equippable_asset_id` | | value | only applied when the component is absent |
| `unbreakable` | all | bool | |
| `max_damage` | 1.20.5+ | number (NumberProvider) | |
| `food` | 1.20.5+ | `{nutrition:int, saturation:float, can_always_eat:bool}` | writes only `minecraft:food`. On 1.21.2+ the item is edible only if it also has `consumable` (from the material or `data.consumable`) [WIKI data] |
| `equippable` | 1.21.2+ | `{slot, asset_id, camera_overlay, dispensable=true, swappable=true, damage_on_hurt=true, equip_on_interact=false, can_be_sheared, equip_sound, shearing_sound}` | `core:item/setting/value/EquipmentData.fromConfig` L67-79 |
| `enchantment(s)` | all | `{minecraft:sharpness: 3}` or `{merge: true, enchantments: {...}}` | level 1..255; on enchanted_book it writes stored enchantments |
| `trim` | all | `{material: id, pattern: id}` | |
| `dyed_color` | all | `r,g,b` or int | |
| `item_model` | 1.21.2+ | id | component only, **no** model generation (use the root key for that) |
| `custom_model_data` | all | NumberProvider | on 1.21.4+ writes `{floats:[v]}` and **replaces** the whole component (`bukkit:item/factory/ComponentItemFactory1_21_4.java` L30-36) |
| `tooltip_style` | 1.21.2+ | id | |
| `hide_tooltip` | all | list of component ids | 1.21.5+ uses `tooltip_display` |
| `jukebox_playable` | 1.21+ | song id | |
| `painting_variant` | | id | |
| `profile` | | name, URL, base64, texture path, or `{name\|url\|base64\|texture}` | |
| `written_book_content`, `process_written_book_tags` | 1.20.5+ | see wiki | |
| `use_remainder` | 1.21.2+ | `{id, count=1}` | |
| `attribute_modifiers` (`attributes`) | all | list of `{type, amount (NumberProvider), operation=add_value, slot=any, id?, display?: {type: default\|hidden\|override, value}}` | `display` 1.21.6+ |
| `component(s)` | 1.20.5+ | `{minecraft:x: <yaml value> \| "(json) {...}" \| "(snbt) {...}"}`. `custom_data` here is **merged** into the existing custom_data | `ComponentsProcessor.java`. Strings may contain `<arg:...>` (then non-constant) |
| `remove_component(s)` | 1.20.5+ | list of ids | |
| `nbt` / `tags` | all | map; nested maps become dotted paths; `@a.b` keys are literal paths | on 1.20.5+ writes inside custom_data |
| `pdc` | all | map → **replaces** `custom_data.PublicBukkitValues` with this compound | `PDCProcessor.java`. Keys must be `ns:key` and the values must match Bukkit PDC encodings to be readable through the PDC API [INF] |
| `block_state` (`blockstate`) | all | map of props, or `ns:block[props]` (copies the *visual* state's props) | improves client placement prediction |
| `arguments` / `set_arguments` | all | `{name: text}` → stored at custom_data `craftengine:arguments` | |
| `get_arguments` | | anything | loads the stored arguments into the build context |
| `random_values` (`randoms`) | | `{name: NumberProvider}` → custom_data `craftengine:random_values`; never re-rolled | readable as `<arg:item.random.name>` |
| `function(s)` | | function or list; runs during the build (e.g. `set_variable`) | `FunctionProcessor.java` |
| `external` | | `{plugin\|source, id, replace=false}`; builds from another plugin's item source | |
| `conditional` (`condition`) | | `{conditions: [...], data: {...}}` | **Premium** (factory registered as null in Community → ignored) |
| `id` | | id | internal |

Example with raw components (the pattern for TFC tools; key names follow vanilla component SNBT/JSON) [SRC `ComponentsProcessor`, WIKI data "Custom Components"]:
```yaml
data:
  max_stack_size: 1
  max_damage: 1410
  tool:
    default_mining_speed: 1.0
    damage_per_block: 1
    rules:
      - blocks: "#minecraft:mineable/pickaxe"
        speed: 9.0
        correct_for_drops: true
  attribute_modifiers:
    - type: attack_damage
      amount: 4.5
      operation: add_value
      slot: mainhand
```

### 1.4 `settings:` (complete for this commit)
Registry: `core:item/setting/ItemSettingsModifiers.java` L29-230. Defaults come from `core:item/setting/ItemSettings.java` L20-60. **Unknown keys are silently ignored** (`applyModifiers` L177-190).

| Key | Type / default | Effect |
|---|---|---|
| `fuel_time` | int ticks / 0 | furnace fuel (CE listener `RecipeEventListener.onFurnaceBurn`) |
| `fuel_remainder` | id | returned after burning (stack size 1 only, per wiki) |
| `break_power` | int / −1 (= vanilla table) | §2 |
| `tags` | list of ids (leading `#` optional) | **CE-internal** item tags: recipe ingredients, `correct_tools`, conditions, anvil targets. Not vanilla registry tags. On 26.1+, `dye_color` also adds `minecraft:dyes`. |
| `repairable` | bool, or `{crafting_table, anvil_repair, anvil_combine, grindstone_repair}` / UNDEFINED | |
| `anvil_repair_item` | list `{target: id/#tag/list, amount: NP=0, percent: NP=0}` | set on the *material*; last match wins |
| `drag_repair_item` | list `{target, amount, percent, sound?}` | cursor-drag repair; first match wins |
| `renameable` | bool / true | anvil rename |
| `prevent_break` | bool / false | keeps 1 durability; the item becomes unusable while broken (`bukkit:item/listener/PreventBreakListener.java`) |
| `disable_vanilla_behavior` | bool / **true for custom**, false for vanilla | Blocks placing the material's **vanilla block** when right-clicking with a block-item material (`bukkit:item/listener/ItemEventListener.java` L363-376). |
| `can_place` | bool | inverse alias of `disable_vanilla_behavior` (L163-166) |
| `trigger_advancement` | bool / false for custom | |
| `projectile` | `{display: {item, translation, rotation, display_transform, scale}, sounds: {throw, hit_entity, hit_block}, ignore_infinity_enchantment, pickupable, remove_on_hit, gravity, velocity, damage, pierce_level}` | custom projectile visuals for trident, arrow, snowball and similar [WIKI settings, CFG topaz_trident] |
| `allowed_projectiles` | list ids | extra ammo for bows and crossbows |
| `dyeable` | bool / undefined | crafting-table dyeing |
| `dye_color`, `firework_color` | color | colors this item provides as a dye or firework ingredient |
| `food` | `{nutrition, saturation}` | adds a FoodProcessor on 1.20.5+ (`can_always_eat=false`); on older versions CE handles `FoodLevelChangeEvent` |
| `consume_replacement` | id | item given back after eating (non-creative) (`ItemEventListener.onConsumeItem` L645-700) |
| `craft_remainder` (`craft_remaining_item`) | id, or `{type: fixed\|hurt_and_break\|recipe_based, ...}` | crafting remainder. `hurt_and_break` `{damage}` is how to make **tools that survive crafting** (TFC knife/saw/hammer in crafting grids). |
| `invulnerable` | list of damage causes (`lava`, `fire`, `fire_tick`, `block_explosion`, `entity_explosion`, `lightning`, `contact`) | the dropped item entity ignores these |
| `enchantable` | bool / true | false blocks the enchanting table |
| `compost_probability` | float / 0.5 | |
| `respect_repairable_component` | bool / false for custom | |
| `ingredient_substitute` | list of vanilla ids | lets this CE item satisfy vanilla ingredients in CE-managed recipes |
| `equipment` | `{asset_id (required), client_bound_model?, slot?, camera_overlay?, dispensable?, damage_on_hurt?, swappable?, equip_on_interact?}` | §5 |
| `equippable` | legacy form (equippable data and asset_id together) | registers a component equipment and binds it |
| `equipment_lod` | `{asset_id, distance}` | needs `equipment.lod.enable` |
| `equipment_set_part` | list `{slots: [...], sets: [...]}` | §5 |
| `attribute_modifiers` | list `{type, id, amount, operation, scope: entity\|weapon, slot, conditions?, update_interval=20}` | CE **custom** attribute system (`attribute.enable`, restart). Not the vanilla component. |
| `equipment_potion_effects` | list `{slot, type, amplifier, ambient, particles, show_icon, conditions, update_interval}` | needs `entity.tracking` |
| `keep_on_death_chance`, `destroy_on_death_chance` | float 0..1 / 0 | |
| `drop_display` | bool or format string (`"<arg:count>x <name>"`) | name tag on dropped items |
| `glow_color` | legacy color name | glowing dropped item |
| `hat_height` | double | only with the CustomNameplates hook |

Wiki-only (**not in this commit**, so silently ignored): `anvil_repair_material`, `drag_repair_material` [WIKI settings vs SRC]. They are probably newer than this commit.

Custom settings from Java: `ItemSettingsModifiers.register(Key, value -> settings -> settings.addCustomData(MY_TYPE, parsed))`, with `CustomItemSettingType<T>` created by `simple()` or `newType(dataProcessor, clientBoundDataProcessor)`. The data processor variant can inject **item processors** for the build. Read it back with `itemDefinition.settings().getCustomData(MY_TYPE)`. Real example: `bukkit/compatibility/.../nameplates/CustomNameplateHatSettings.java` L29-38 [SRC].

### 1.5 Choosing `material`
[INF from SRC unless noted.] CE does not create new item types; `material` is the real server item.
- **Inherited vanilla behavior**: default components (stack size, `food`/`consumable`, `tool`, attribute modifiers, `equippable`, `max_damage`, `repairable`, `enchantable`), vanilla **item tags** (enchantment applicability, piglin bartering and so on, which cannot be changed per stack), right-click logic (`Item.use`/`useOn` still run: hoes till, axes strip, buckets fill, block items place unless `disable_vanilla_behavior`), and vanilla recipe membership.
- **Override** most components in `data:`. Remove them with `remove_components`. Tags and the hard-coded `Item` class logic (e.g. `HoeItem.useOn`) cannot be removed except by cancelling events or returning `SUCCESS_AND_CANCEL` from our behavior.
- **Durability**: `max_damage` plus the material's damage handling. Vanilla damages tools via the `tool` component's `damage_per_block` and weapons via `weapon` (1.21.5+). Use `unbreakable` or `prevent_break` as needed.
- **Stack size**: set `max_stack_size` (1..99) explicitly. Items that differ in PDC never stack, which matters for TFC food creation dates.
- **The default material `nether_brick`** is a vanilla crafting ingredient. Pick a neutral material for TFC (e.g. `paper`, `stick`, `iron_nugget`) per family, and test vanilla-recipe leakage (§6.6).
- **Block-item materials** (`note_block`, `mushroom_stem`) improve the client's placement prediction with `data.block_state` [WIKI data]. Placement of the vanilla block is disabled by default for custom items.
- **Food**: on 1.21.2+, using a vanilla food material (e.g. `bread`, `apple`) gives the `consumable` component. Alternatively add `data.consumable: {consume_seconds: 1.6, animation: eat, ...}` (raw component) [INF].
- Armor materials set the default `equippable` asset. See §5 for 3D helmets.

### 1.6 Item identity and our PDC
- The id is a string at `minecraft:custom_data` → root key `craftengine:id` (`IdProcessor.CRAFT_ENGINE_ID`). Reads go through `item.customId()` (`ComponentItemFactory1_20_5.java` L54-78) [SRC]. API: `CraftEngineItems.getCustomItemId(ItemStack)`, `isCustomItem`, `byItemStack`, `byId(Key|String)` → `BukkitItemDefinition.buildBukkitItem(...)` (`bukkit:api/CraftEngineItems.java` L29-88).
- Other CE keys in custom_data: `craftengine:version` (updater), `craftengine:arguments`, `craftengine:random_values`, `craftengine:display_context` (dynamic lore), and the client copy only: `craftengine:network_data` (encrypted with `item-crypto`) [SRC].
- **Bukkit PDC** = `custom_data.PublicBukkitValues`. Adding our keys through `ItemMeta#getPersistentDataContainer()` or Paper's `editPersistentDataContainer` does not touch `craftengine:id`. **Safe** [INF from both encodings].
- Things that destroy our PDC: (a) the `data.pdc` processor when an item is built (fresh items only); (b) `updater` `reset` unless `keep_components: [minecraft:custom_data]`. If you keep the whole custom_data, the version tag is re-set after the step anyway (`ItemUpdateConfig.update` sets the tag at the end); (c) recipe results are fresh builds, so use transform recipes with `keep_custom_data` or `keep_components` to carry PDC across [SRC `core:item/updater/impl/ResetOperation.java`, `core:item/recipe/transform/*`].
- **Stamping PDC at creation** (e.g. TFC food creation tick): register a **non-constant item processor** `tfc:food_stamp` (§10) and put it under `data:`. It then runs every time CE builds the item (`/ce item get`, loot, recipe result, API build). Items created by vanilla paths (e.g. `ItemStack#clone`, splitting stacks) are not "built" and keep their PDC [INF].
- Paper item obfuscation: add `minecraft:custom_data` to `dont-obfuscate` [WIKI data, client_bound section].

### 1.7 Item updater
[SRC `core:item/updater/*`, `AbstractItemManager.updateItem` L171-181; WIKI updater]
```yaml
updater:
  1: {type: apply_data, data: {item_name: "New"}}
  2:
    - {type: transmute, material: stone_sword}
    - {type: reset, keep_components: [minecraft:custom_data, minecraft:damage]}
```
- `apply_data {data}`, `transmute {material}` (keeps components), `reset {keep_components: [...], keep_tags: [...] (legacy)}`. The item version is in `custom_data.craftengine:version`; new items are stamped with the max version, so **keep `data:` equal to the final state**.
- Triggers: `config.yml item.update-triggers.{click-in-inventory, drop, pick-up, attack}`, all false by default and "performance-intensive". The config comment says the updater is for correcting faults, not names or lore.
- From Java: `CraftEngine.instance().itemManager().updateItem(item, () -> ItemBuildContext.of(player))` returns `ItemUpdateResult(finalItem, replaced, updated)` (internal API, not in the `api` package).
- Registering our own updater op: `ItemUpdaters.register(Key, (itemId, section) -> ctx -> {...})`.
- Separate: `item.data-fixer-upper` upgrades item data when the server's MC version changes (config.yml L355-358).

### 1.8 Network rewriting and our tooltip packet layer
[SRC `bukkit:item/ModernNetworkItemHandler.java`, `core:item/network/NetworkItemHandler.java`]
- On **every** outgoing item (s2c), CE recurses into bundle and container contents, and when `network.intercept-packets.item: true` (config L568) parses CE text tags (`<image>`, `<l10n>`, …) inside `item_name`, `custom_name` and `lore` of *all* items (`OtherItem` path). On Premium it also applies `client_bound_data`. It also **rebases** the component patch against the client item's original prototype, which matters when `override_data` changed vanilla prototypes.
- When something was changed in a slot the client can send back, CE stores the originals in `custom_data.craftengine:network_data` (encrypted). `c2s` restores them for creative-mode slot packets.
- **For our lore layer** [INF]: (1) In creative mode the client sends the full stack back, so our injected lore would be written into the server item unless our c2s handler strips it. Use our own marker, like CE's network tag. (2) Pipeline ordering between our handler and CE's is UNVERIFIED. If ours runs after CE's s2c, our lore is not tag-parsed, which is fine. If ours runs before, CE may parse our text for `<...>` tags. Do not emit MiniMessage-like `<...>` text unless intended. (3) `minimize-item-packets` (`Config.minimizeItems`) can strip names and lore for packet sources where the client doesn't render them.

---

## 2. Tools and mining

### 2.1 Mechanics [SRC]
- **Item break power** = `settings.break_power` if ≥ 0, otherwise `VanillaBreakPowers`: wood/gold 1, stone/copper 2, iron 3, diamond 4, netherite 5 (pickaxe/axe/shovel/hoe only), anything else 0. The vanilla table can be overridden in config.yml `item.break-power: {minecraft:golden_pickaxe: 3}` (`core:util/ItemUtils.breakPower` L24-33, `core:item/VanillaBreakPowers.java`).
- **Block side** (CE blocks only), settings: `hardness` (default 2.0), `required_break_power` (int, 0), `require_correct_tools` (bool, false), `correct_tools` (list of ids and `#tags`, resolved through **CE item tags plus vanilla item tags**), `respect_tool_component` (bool; also accept tools whose vanilla `tool` component says `isCorrectToolForDrops` for this block), `incorrect_tool_dig_speed` (float, 0.3), `tags` (block tags bound to the `custom_N` holder, including **new** tag ids like `tfc:mineable/chisel`) (`core:block/setting/BlockSettings.java` L20-50, `BlockSettingsModifiers.java` L66-175; `bukkit:block/BukkitBlockManager.java` L361-366).
- **Correct tool** = (not `require_correct_tools` or (id in `correct_tools`) or (`respect_tool_component` and the vanilla tool says correct)) **and** (`required_break_power` ≤ 0 or itemBreakPower ≥ required) (`bukkit:util/BlockStateUtils.isCorrectTool` L52-68).
- **Effects of an incorrect tool**: destroy progress × `incorrect_tool_dig_speed` (`BukkitServerPlayer.getDestroyProgress`); **no drops** when a player or entity broke it (`BlockStateGenerator` L133-136); no XP for `drop_experience_block`; double-high blocks and doors do not drop either.
- **Base speed** = vanilla NMS `getDestroyProgress` on the real `custom_N` state: hardness from settings, and the item's vanilla `tool` component rules evaluated against the custom block's holder tags. The client is told its `block_break_speed` is 0 (or gets mining fatigue on older versions) and CE drives cracking and breaking server-side (`BukkitServerPlayer.setClientSideCanBreakBlock` L1016-1035). Client-side prediction from the visual block is therefore neutralized.
- **Vanilla blocks are unaffected** by break_power ("This only works for custom blocks", config.yml L325-329). If TFC uses vanilla blocks anywhere, tiering must come from vanilla `tool` components or our own listener.

### 2.2 Recipe for TFC tiers and tool types [INF]
- **Tiers** (TFC: stone 1, copper 2, bronze 3 (bronze/bismuth bronze/black bronze), wrought iron 4, steel 5, black steel 6, red/blue steel 7; confirm against the TFC source) → `settings.break_power: <tier>`. Rock and ore blocks get `required_break_power: <tier>`.
- **Types** → item `settings.tags: [tfc:tools/pickaxe, tfc:tools]` (also add the vanilla-style `minecraft:pickaxes` if CE recipes or conditions should treat it as a pickaxe). Block `require_correct_tools: true` and `correct_tools: ["#tfc:tools/pickaxe"]`.
- **Mining speed per metal** → a raw `tool` component per item: `rules: [{blocks: "#minecraft:mineable/pickaxe", speed: <tfc speed>, correct_for_drops: true}]`, plus `damage_per_block` and `default_mining_speed`. CE blocks carry whatever tags their `settings.tags` list, so give rock blocks `minecraft:mineable/pickaxe`, or a TFC tag (whether a component can decode a **non-registry** tag id is UNVERIFIED; §12).
- **Durability** → `max_damage`. **Damage and speed** → `attribute_modifiers` (vanilla) or `weapon` (1.21.5+).
- **Base material**: any non-tool material works once `tool` is set, but a matching vanilla tool (`iron_pickaxe`, …) also gives vanilla tags (enchantability, `#minecraft:pickaxes` for vanilla mechanics) and handheld rendering for simplified models. Note that axe, shovel and hoe materials keep their **vanilla right-click actions** (strip, path, till) unless cancelled.
- Non-mining TFC tools: **chisel, hammer, saw, knife, scythe, propick, javelin**. Implement them with **our item behaviors** (`useOnBlock` for chisel and propick), `onBreakBlock` (scythe/knife area harvest; or the built-in `range_mining_item` for simple box mining), crafting-grid durability via `craft_remainder: {type: hurt_and_break, damage: 1}`, and the javelin via `material: trident` (vanilla throw) optionally with `settings.projectile` for visuals. Melee damage via attributes.
- `range_mining_item` (built-in): `range: ["x,y,z" ...]` offsets relative to the facing (x = right, y = up, z = depth), `conditions: [...]`. Only breaks targets whose destroy progress ≥ the origin's (`bukkit:item/behavior/RangeMiningItemBehavior.java`).

---

## 3. Item models from the item side

[SRC `core:pack/model/definition/**`, AbstractItemManager L752-890; WIKI configuration/item/models and subpages]
- **Generation rules**: on 1.21.2+ servers with pack max ≥1.21.2, the `item_model` component defaults to the **item id** and CE writes `assets/<ns>/items/<path>.json` from `model:`. CMD is only allocated when the pack min is <1.21.2. The pack min defaults to `server` (config.yml L75-77), so on a 1.21.4+ server no CMD is used unless forced. Legacy overrides are generated when the pack min is <1.21.4. If the model tree cannot be downgraded, the result is `resource.item.model_definition.downgrade_failure`.
- **`model:` types** (`type` defaults to `minecraft:model`; the prefix is optional): `model {path, generation: {parent, textures, display, gui_light}, tints: [...], transformation (26.1+)}`, `composite {models: [...]}`, `condition {property, on_true, on_false}`, `select {property, cases: [{when: v|[..], model}], fallback}`, `range_dispatch {property, scale=1, entries: [{threshold, model}], fallback}`, `special {model: {type: head|chest|shield|bed|banner|...}, base}`, `empty`, `bundle/selected_item`. A string `model: path` is shorthand.
- **Properties** (registered ids in source): range_dispatch: `bundle/fullness, compass, cooldown, count, crossbow/pull, damage, time, use_cycle, use_duration, custom_model_data{index}`. select: `block_state{block_state_property}, charge_type, component{component} (1.21.5+), context_dimension, context_entity_type, display_context, local_time, main_hand, trim_material, custom_model_data{index}`. condition: `broken, bundle/has_selected_item, carried, component, damaged, extended_view, fishing_rod/cast, has_component, keybind_down, selected, using_item, view_entity, custom_model_data{index}`.
- **Tints**: `constant {value}`, `dye {default}`, `firework`, `map_color`, `potion`, `team`, `grass {temperature, downfall}`, **`custom_model_data {index, default}`** (reads `custom_model_data.colors[index]`).
- **Simplified forms**: `texture`/`textures` (the slot order depends on the material: bow 4, crossbow 6, fishing_rod 2, shield 2, elytra 2, spear 2; otherwise 1 with `item/generated` or `item/handheld` for tool materials), `models` (existing JSON), `textures` plus `models`, and `blueprint` (.bbmodel) [WIKI models].

**State visuals for TFC** (molds filled, vessel contents, food states, heated items) [INF]:
- Set data from Java with Paper's `DataComponentTypes.CUSTOM_MODEL_DATA` (`CustomModelData.customModelData().addFloat(..).addFlag(..).addString(..).addColor(..)`). Use `select` on `custom_model_data` strings (e.g. `"filled"`, a metal id), `range_dispatch` on floats (fill fraction, temperature), `condition` on flags, and a `custom_model_data` **tint** on `colors` (fluid or metal colour) on a layer such as `layer1`.
- **Conflict risk:** CE's root `custom_model_data` or auto-CMD writes `{floats:[N]}` and *replaces* the component on every build. On a 1.21.4+ pack (no auto-CMD) this does not happen. Never set root `custom_model_data` on such items, and keep the pack min ≥1.21.4 so no legacy downgrade is attempted (custom CMD-driven trees may not downgrade).
- `dyeable` armor: `settings.dyeable: true` plus a `tints: [{type: minecraft:dye, default: -6265536}]` model (`assets:items/cap.yml`) [CFG].

```yaml
# Example: ceramic mold showing the filled state and the metal colour (INF; property names per vanilla 1.21.4)
model:
  type: minecraft:select
  property: minecraft:custom_model_data
  index: 0
  cases:
    - when: filled
      model:
        type: minecraft:model
        path: tfc:item/ceramic/ingot_mold_filled
        tints:
          - {type: minecraft:constant, value: -1}
          - {type: minecraft:custom_model_data, index: 0, default: -1}
  fallback: {type: minecraft:model, path: tfc:item/ceramic/ingot_mold}
```

---

## 4. Item behaviors

### 4.1 Built-in item behaviors (all, with options) [SRC `bukkit:item/behavior/*`, `BukkitItemBehaviors.java`]
| type | Options |
|---|---|
| `block_item` | `block: id` or an inline block section (the block takes the item id) |
| `wall_block_item`, `ground_block_item`, `ceiling_block_item`, `double_high_block_item`, `multi_high_block_item` | `block` (same) |
| `liquid_collision_block_item` | `block`, `y_offset` (1) |
| `furniture_item` | `furniture: id \| inline`, `rules: {<variant>: {alignment: center\|any…, rotation: four\|eight\|any…}}`, `ignore_placer`, `ignore_entities`, `against_blocks: [...]`, `against_block_tags: [...]`, `blacklist` (true) |
| `liquid_collision_furniture_item` | the furniture options plus `source_only` (true) and the liquid type list |
| `range_mining_item` | `range: ["x,y,z", ...]`, `conditions` |
| `compostable_item` | `chance` (0.55) |
| `flint_and_steel_item` | none; also auto-attached to vanilla `flint_and_steel` (`BukkitItemManager` L70) |
| `empty` | |

`PLACE` event functions for block and furniture items fire from these behaviors (`BlockItemBehavior` L178, `FurnitureItemBehavior` L224).

### 4.2 Custom Java behavior [SRC `core:item/behavior/*`]
```java
// The API surface (core:item/behavior/ItemBehavior.java)
public abstract class ItemBehavior {
  public InteractionResult useOnBlock(UseOnContext ctx)            { return PASS; } // right-click a block
  public InteractionResult useOnEntity(InteractEntityContext ctx)  { return PASS; } // right-click an entity
  public InteractionResult use(World w, @Nullable Player p, InteractionHand h) { return PASS; } // right-click air
  public void onBreakBlock(World w, Player p, BlockPos pos) {}     // after a successful BlockBreakEvent (MONITOR)
}
public interface ItemBehaviorFactory<T extends ItemBehavior> { T create(Pack pack, Path path, Key itemId, ConfigSection section); }
// InteractionResult: FAIL, SUCCESS, PASS, TRY_EMPTY_HAND, SUCCESS_AND_CANCEL
```
Registration (`ItemBehaviors.register(Key, factory)`). An unnamespaced `type` resolves to `craftengine:`, so always write `tfc:...`:
```java
public final class ChiselBehavior extends ItemBehavior {
  public static final ItemBehaviorFactory<ChiselBehavior> FACTORY = (pack, path, id, sec) -> new ChiselBehavior(sec.getInt("radius", 1));
  ...
  @Override public InteractionResult useOnBlock(UseOnContext ctx) {
    var p = (BukkitServerPlayer) ctx.getPlayer();          // p.platformPlayer() -> org.bukkit.entity.Player
    var item = (BukkitItem) ctx.getItem();                 // item.getBukkitItem()
    BlockPos pos = ctx.getClickedPos(); Direction face = ctx.getClickedFace();
    ...; return InteractionResult.SUCCESS_AND_CANCEL;      // cancels the Bukkit event
  }
}
// in onLoad (before CE parses configs):
ItemBehaviors.register(Key.of("tfc", "chisel"), ChiselBehavior.FACTORY);
```
```yaml
items:
  tfc:metal/chisel/bronze:
    material: iron_shovel
    behavior: {type: tfc:chisel, radius: 1}
```
Dispatch semantics [SRC `bukkit:item/listener/ItemEventListener.java`]:
- `useOnBlock`: `PlayerInteractEvent` RIGHT_CLICK_BLOCK at HIGHEST, `ignoreCancelled`. If the target is a **CE block**, `CustomBlockInteractEvent`, the block's events and the **block behavior** run first and can consume the click (L206-284). Then the item behavior runs (L388-406), **but is skipped when the clicked vanilla block is interactable and the player is not sneaking** (L392-396). Item `right_click` event functions run **after** the behavior when it returned PASS (L408-433). `SUCCESS*` marks the tick as interacted (suppresses the off-hand).
- `use`: `PlayerInteractEvent` RIGHT_CLICK_AIR at HIGH (no `ignoreCancelled`). The item's `right_click` functions run **before** the behavior (L604-640).
- `useOnEntity`: `PlayerInteractEntityEvent` at HIGHEST; skipped if the entity is vanilla-interactable with this item. Functions run first, then the behavior (L111-171).
- `onBreakBlock`: `BlockBreakEvent` MONITOR when not cancelled, after the item's `block_break` functions (`bukkit:block/listener/BlockEventListener.java` L157-177).
- Behaviors also work on **vanilla item ids** configured under `items:` (e.g. `minecraft:stick: {behavior: ...}`) because `getBehavior()` resolves vanilla definitions too [SRC L698-701; INF].
- Composition: `behavior: [a, b]` → `DualItemBehavior` / `CompositeItemBehavior`. The first non-PASS result wins (`core:item/behavior/DualItemBehavior.java` L37-53). Find ours with `definition.behavior().getFirst(MyBehavior.class)`.
- **Missing hooks** (we must use Bukkit or Paper events ourselves): left click, consume (`PlayerItemConsumeEvent`), use duration and release (`PlayerStopUsingItemEvent`), held or inventory tick, attack (`EntityDamageByEntityEvent`), drop, pickup, item damage.

---

## 5. Equipment / armor

[SRC `core:item/equipment/*`, `ItemSettingsModifiers` L122-158; CFG `assets:items/topaz_armor.yml`, `templates:` `default:armor/topaz`; WIKI configuration/equipment, configuration/attribute/equipment-set]
- **`equipments:` section**: `ns:asset: {type: component | trim, <layer>: texture | {texture, dyeable: {color_when_undyed}, use_player_texture} | [list]}`. Component layers (1.21.2+): `humanoid, humanoid_leggings, wings, wolf_body, horse_body, llama_body, pig_saddle, strider_saddle, camel_saddle, horse_saddle, donkey_saddle, mule_saddle, skeleton_horse_saddle, zombie_horse_saddle, happy_ghast_body, camel_husk_saddle, nautilus_body`. CE writes `assets/<ns>/equipment/<id>.json`.
- `type: trim` (1.20+, for <1.21.2): sacrifices a vanilla armor appearance (`equipment.sacrificed-vanilla-armor`, default chainmail) and paints custom armor via a hidden trim. Only humanoid layers, and such armor cannot be trimmed. Its processors are `trim` + `overwritable_equippable_asset_id(chainmail)` + `hide_tooltip trim` (`TrimBasedEquipment.modifiers` L40-53).
- **Item binding**: `settings.equipment: {asset_id: ns:asset, slot?: head|chest|legs|feet|body|saddle, camera_overlay?, dispensable?, damage_on_hurt?, swappable?, equip_on_interact?}`.
  - Without `slot`, only `overwritable_equippable_asset_id` is applied, so the **material's own `equippable` (slot)** is reused. That is how the shipped template does it: `material: chainmail_${part}` plus `equipment.asset_id` [CFG].
  - With `slot` (1.21.2+), a full `equippable` component is written.
- The armor values (armor, toughness, knockback resistance) come from the material's default attribute modifiers. Override with `data.attribute_modifiers` (`slot: head` etc.) for TFC per-metal values. The shipped config uses `remove_components: [attribute_modifiers]` on a cosmetic cap [CFG `cap.yml`].
- **3D helmet**: `data.equippable: {slot: head}` with no asset_id, so the item model renders on the head [WIKI equipment].
- **Equipment sets**: `equipment_sets: {ns:set: {pieces: {2: {attribute: [...], potion_effects: [...], events: {activate: [...], deactivate: [...]}}}}}`, with items declaring `settings.equipment_set_part: [{slots: [head], sets: [ns:set]}]`. The highest satisfied tier applies and tiers do not stack. Attributes are CE custom attributes (`attribute.enable`). Condition `equipment_set {set, count|min|max}` [WIKI].
- TFC armor (27 metals × 4 pieces × tiers): one `equipments:` entry per metal (component type), the item template `material: <closest vanilla armor>` plus `settings.equipment.asset_id`, and attributes in `data`. Damage-type resistances (TFC piercing/slashing/crushing) need our own damage listener [INF].

---

## 6. Recipes

### 6.1 Types and exact YAML [SRC `core:item/recipe/RecipeSerializers.java`; WIKI configuration/recipe; CFG]
`type` resolves under `minecraft:`. Registered: `shaped` (`crafting_shaped`), `shaped_transform`, `shapeless` (`crafting_shapeless`), `shapeless_transform`, `smelting`, `blasting`, `smoking`, `campfire_cooking`, `stonecutting`, `smithing_transform`, `smithing_trim`, `brewing`, and `dye` (`crafting_dye`) **on 26.1+ only**.

Common keys (crafting types): `category` (building|redstone|equipment|misc; cooking: food|blocks|misc), `group`, `show_notification` (true), `result`, `visual_result` (Premium), `functions` (Premium in practice), `conditions`, `always_rebuild_result` (true), `unlock_on_ingredient_obtained` (global default `recipe.unlock-on-ingredient-obtained: true`), `unlock_on_join` (false).
```yaml
recipes:
  tfc:crafting/firestarter:
    type: shaped
    category: equipment
    pattern: ["A ", " A"]
    ingredients:
      A: "#tfc:sticks"                 # CE item tag; vanilla tags work too
    result: {id: tfc:firestarter, count: 1}
  tfc:crafting/x:
    type: shapeless
    ingredients:
      - minecraft:stick
      - [tfc:a, tfc:b]                 # nested list = one slot accepting either
      - {items: minecraft:cobblestone, count: 32}   # per-slot stack count
    result: tfc:y                       # string form = count 1
  default:topaz_from_ore:
    type: smelting                      # blasting/smoking/campfire_cooking: same keys
    experience: 1.0                     # alias exp
    time: 200                           # DEFAULT IS 80 in source, not vanilla 200 (CustomSmeltingRecipe L52)
    ingredient: default:topaz_ore       # ingredient(s)
    result: {id: default:topaz, count: 1}
  default:x_stonecut: {type: stonecutting, ingredient: minecraft:stone, result: {id: default:topaz, count: 1}}
  default:topaz_bow:
    type: smithing_transform
    template_type: default:topaz       # optional
    base: minecraft:bow                 # required
    addition: default:topaz             # optional
    merge_components: true
    result: {id: default:topaz_bow, count: 1, post_processors: []}
    transform_processors: []
  default:bolt_tool_trims:
    type: smithing_trim
    template_type: minecraft:bolt_armor_trim_smithing_template
    base: "#minecraft:trimmable_tool"
    addition: "#minecraft:trim_materials"
    pattern: minecraft:bolt             # required 1.21.5+
  tea:tea: {type: brewing, ingredient: tea:leaf, container: tea:cup, result: {id: tea:cup_of_tea, count: 1}}
```
Transform recipes: mark one ingredient `{item(s): ..., source: true}`. `merge_components` (true) copies the source item's components into the result. **`transform_processors`** (alias `post_processors` at recipe level): `apply_data {data}`, `merge_enchantments`, `keep_custom_data {paths: [...]}` (1.20.5+), `keep_components {components: [...]}`, `keep_tags {tags: [...]}` (legacy) [SRC `core:item/recipe/transform/ItemTransformDataProcessors.java` L13-17].

### 6.2 Ingredients [SRC `core:item/recipe/AbstractRecipeSerializer.parseIngredient` L65-133]
- String: a CE id, a vanilla id, or `#tag`. The tag is resolved through **vanilla tag membership plus CE `settings.tags`** (`ItemManager.itemIdsByTag`). An empty or unknown tag → `invalid_tag` error. An unknown item → `item_not_exist` error.
- Map: `{items: <id|#tag|list>, count: n, predicate: [{type: enchantment, enchantments: {id: lvl}} | {type: exact, component: id, value: ...} | {type: all_of ...}]}`.
- Matching is by CE id (a CE item never matches a plain vanilla id unless it declares `settings.ingredient_substitute: [minecraft:x]`) [SRC `getIngredientSubstitutes`; WIKI settings].
- External plugin items via `recipe.ingredient-sources` plus a mapping item with `data.external` [WIKI].

### 6.3 Result and post processors
`result: id` or `{id, count, post_processors: [{type: apply_data, data: {...}}]}` [SRC `parseResult` L36-63, `core:item/recipe/result/PostProcessors.java`]. The result is **built per craft** (`always_rebuild_result: true`), so non-constant data processors (our PDC stamper) run for each craft [INF from `RecipeEventListener` L838-850]. Custom post processor: `PostProcessors.register(Key, section -> (item, ctx) -> item)` [SRC].

### 6.4 Conditions, functions, visual result, unlock
- `conditions` are evaluated in `onPrepareCraftingRecipe` with **no Premium check** (L833-838). Cooking and stonecutting conditions need `recipe.inject-block-entities: true` (furnace injection, restart) for furnaces. The wiki marks conditions Premium, so treat them as UNVERIFIED in Community and test.
- `functions` and `visual_result`: `onCraftingFinish` returns early unless `PREMIUM` (L856-858), so they are **Premium-only**.
- Unlocking: per recipe `unlock_on_ingredient_obtained` / `unlock_on_join`. Global `recipe.unlock-on-join.{all, list}`. Function `discover_recipe`, condition `has_discovered_recipe`.

### 6.5 Global switches (config.yml L591-622)
`recipe.enable`, `disable-vanilla-recipes.{all: false, list: [ids]}` (unregisters those datapack recipes from the server), `ingredient-sources: []`, `unlock-on-ingredient-obtained: true`, `unlock-on-join`, `inject-block-entities: true` (restart). `/ce reload recipe` reloads recipes. After `/minecraft:reload` you must reload CE recipes [WIKI recipe caution].

### 6.6 Interaction with Paper and third-party code [SRC `bukkit:item/recipe/BukkitRecipeManager.java` L176-260, `RecipeRegistry1_21_2.java`, `RecipeEventListener.java`]
- On (re)load, CE copies the **current** NMS recipe map, removes its own previous recipes and the datapack recipes it replaces, adds its recipes (built by the closed-source FastNMS), re-injects the repair and dye special recipes, then swaps the map in and resyncs the recipe book. **Recipes added through `Bukkit.addRecipe` survive** CE reloads because the copy starts from the live map [INF].
- Datapack recipes are re-parsed by CE. If an ingredient's tag contains CE items, CE replaces that vanilla recipe so it accepts them.
- `PrepareItemCraftEvent` (CE listener, default priority): if the current recipe id is unknown to CE (e.g. registered by our plugin through Paper) → **ignored**. If it is a CE recipe → conditions are checked and the result is rebuilt. Special (complex) recipes containing CE items are blanked except repair (`onSpecialRecipe` L737-797).
- **No public Java API to register CE recipes.** `registerRecipeInternal` is protected (`AbstractRecipeManager` L125). Read-only lookups exist: `recipeById`, `recipesByType`, `recipeByResult`, `recipeByIngredient`, `recipeByInput(type, input)` via `CraftEngine.instance().recipeManager()` (internal, unstable).
- For TFC: generate CE YAML for plain crafting that involves CE items, and implement knapping, anvil, quern, barrel, welding, casting and similar in our own systems. If we register Paper recipes that use CE items, use `RecipeChoice.ExactChoice` or our own `PrepareItemCraftEvent` validation. Whether a CE item with material X can satisfy a vanilla or Paper recipe that expects X is **UNVERIFIED** (§12). Assume it can, and guard against it.

---

## 7. Loot

### 7.1 Loot table schema [SRC `core:loot/**`; WIKI reference/loot_table]
```yaml
loot:                       # block.loot / furniture loot / loots:<id> / loot_sources.<id>.loot
  functions: []             # table-level functions
  pools:
    - rolls: 1              # NumberProvider (default 1)
      bonus_rolls: 0        # NumberProvider (multiplied by luck)
      conditions: []
      functions: []
      entries:
        - {type: item, item: tfc:ore/normal_hematite, weight: 1, quality: 0, conditions: [], functions: []}  # item|id
        - {type: alternatives, children: [...], conditions: []}   # alias if_else; first child that expands wins
        - {type: loot_table, id: minecraft:chests/simple_dungeon}  # id|table|name; CE table, else datapack table
        - {type: exp, count: 3}                                    # count|amount|exp
        - {type: furniture_item, item: fallback:id}
        - {type: function, run: [<common functions>]}              # runs functions as an "entry"
        - {type: empty, weight: 5}
```
- **Functions**: `set_count {count|amount, add=false}`, `apply_bonus {enchantment, formula: {type: ore_drops} | {type: binomial_with_bonus_count, extra, probability}}`, `explosion_decay`, `limit_count {min, max}`, `drop_exp {count}`, `apply_data {data: {...item processors}}`. All accept `conditions`.
- Stacks above the max stack size are split automatically (`LootTable.createStackSplitter`).
- `loot:` may be a **string**, which creates a `LootTableReference`: a CE `loots:` table or, failing that, a datapack table (`bukkit:loot/BukkitLootManager.createReference` L167-171). A datapack table referenced by a CE block is run with vanilla `LootParams` including the custom block state (`BlockStateGenerator` L140-147).
- Templates: `default:loot_table/{self, furniture, basic(item,count), door, silk_touch, slab, seed_crop, crop, ore, leaves}` [CFG `templates:loot_tables.yml`].
- Number providers (§8.4) work for `rolls` and `count`.

### 7.2 `loot_sources:` (vanilla loot injection) [SRC `core:loot/source/*`; WIKI configuration/loot_source]
```yaml
loot_sources:              # alias vanilla_loot(s)
  tfc:stone_override:
    type: block_break      # block_break, block_shear, entity_death, entity_drop, entity_shear, harvest, container, archaeology, vault, advancement (targeted); fishing, piglin_barter (no target)
    target: minecraft:stone   # target(s); id(s) of block/entity/loot table/advancement; no [state] syntax
    overwrite: items       # none (default) | items | experience | all  (legacy `override: true`)
    conditions: [{type: match_block_property, properties: {age: "7"}}]
    loot: {pools: [...]}   # or a table id string
```
`entity_death` targets from MythicMobs and other plugins need `loot.entity-sources` in config.yml [WIKI].

### 7.3 CE items in vanilla or datapack loot [SRC `bukkit:plugin/injector/LootEntryInjector.java`; WIKI compatibility/datapack]
CE registers the loot pool entry type `craftengine:item` in the vanilla registry:
```json
{"type": "craftengine:item", "name": "tfc:food/red_apple", "weight": 10}
```

### 7.4 Drop control from Java
- CE block drops are produced inside the NMS `getDrops` of the `custom_N` state (ByteBuddy intercept). They return nothing if the tool is incorrect and otherwise run `block.loot` (`BlockStateGenerator` L120-150). They therefore pass through vanilla's drop pipeline, so Bukkit `BlockDropItemEvent` should see them [INF].
- `BlockBreakEvent` → CE (MONITOR) fires `CustomBlockBreakEvent(player, …, event.isDropItems())`, then copies `customEvent.dropItems()` back into `event.setDropItems` (L211-219). So **either** `BlockBreakEvent#setDropItems(false)` at a priority below MONITOR **or** `CustomBlockBreakEvent#setDropItems(false)` suppresses drops. The wiki recommends listening to Bukkit events and converting with `CraftEngineBlocks` [WIKI api "Some Development Conventions"].
- Non-player breaking (`CraftEngineBlocks.remove(block, player, movedByPiston, dropLoot, sendLevelEvent)`) has a `dropLoot` flag (general report §8.2).
- Running a CE loot table from Java: `CraftEngine.instance().lootManager().getLoot(Key)` → `Loot.getRandomItems(ContextHolder, World, Player)` (internal API) [SRC `core:loot/AbstractLootManager.getLoot` L42, `core:loot/Loot.java`].

---

## 8. Events, functions, conditions

### 8.1 Syntax [SRC `core:plugin/context/CommonFunctions.parseEvents`; WIKI reference/events]
```yaml
events:
  - on: right_click            # or a list: [attack, left_click]
    conditions: [...]          # gate the whole group (RUN function)
    functions: [...]
  - on: consume
    type: command              # single function inline
    command: "say hi"
# map form:
events:
  right_click:
    - {type: swing_hand}
```
Every function accepts `conditions:` (`AbstractConditionalFunction` L36). A condition type prefixed with `!` is negated.

### 8.2 Triggers [SRC `core:plugin/context/EventTrigger.java`; dispatch sites grep]
| Trigger (aliases) | Items | Blocks | Furniture | Context params provided |
|---|---|---|---|---|
| `right_click` (`use_on`, `use`, `use_item_on`) | yes (block/air/entity) | yes | yes | player, hand, item_in_hand, block/entity, position, event |
| `left_click` | yes | yes | yes (attack packet) | same |
| `attack` (`hit`) | yes (`EntityDamageByEntityEvent`) | | | player, item_in_hand, entity, position, event |
| `consume` (`eat`, `drink`) | yes (`PlayerItemConsumeEvent`, cancellable) | | | player, item_in_hand, hand, event |
| `block_break` (`dig`) | yes (item used to break) | yes (`break` alias on blocks) | | player, block, position, item_in_hand, custom_block_state, event |
| `item_break` (`break` on items) | yes (`PlayerItemBreakEvent`, MONITOR) | | | player, item |
| `furniture_break` | | | yes | |
| `place` (`build`) | via block_item/furniture_item behaviors | yes | yes | |
| `pick_up` (`pick`) | yes (cancellable) | | | player?, entity (item), position, event |
| `step` | | yes | | |
| `fall` | registered; no dispatch site found in bukkit (UNVERIFIED) | | | |
| `shoot` | yes (`EntityShootBowEvent`) | | | player?, entity, item_in_hand, event |

Custom trigger: `EventTrigger.register(Key.of("tfc","heat_changed"), "alias")`. We must then fire it ourselves with `Function.execute(ctx, definition.eventFunctions(MY_TRIGGER))` [SRC `ItemDefinition.eventFunctions`].

### 8.3 Functions (all registered in this commit) [SRC `CommonFunctions.java` L1-55; keys per WIKI reference/functions]
`command {command, target, as_player, as_op, as_event}`, `message {message, target, overlay}`, `actionbar {actionbar, target}`, `title {title, subtitle, fade_in, stay, fade_out}`, `open_window {gui_type: anvil|enchantment|grindstone|loom|smithing|crafting|cartography, title}`, `cancel_event`, `run {delay, functions}`, `place_block {block_state, x, y, z}`, `update_block_property {properties, x,y,z}`, `transform_block {block, properties}`, `break_block {x,y,z}`, `cycle_block_property {property, inverse, rules}`, `update_interaction_tick`, `set_count {count, add}` (the context item), `drop_loot {x,y,z, to_inventory, loot}`, `swing_hand {hand}`, `set_food`/`set_saturation {food|saturation, add}`, `play_sound {sound, x,y,z, target, pitch, volume, source}`, `particle {particle, x,y,z, count, offset_*, speed, ...}`, `potion_effect {potion_effect, duration, amplifier, ambient, particles, show_icon}`, `remove_potion_effect {potion_effect | all}`, `leveler_exp {plugin, leveler, count}`, `set_cooldown {id, time, add}`, `remove_cooldown {id|all}`, `set_item_cooldown {id, time, add}` (1.21.2+), `spawn_furniture`, `remove_furniture`, `replace_furniture`, `rotate_furniture {degree, on_success, on_failure}`, `set_furniture_variant {variant}`, `teleport {x,y,z,pitch,yaw,world}`, `transfer {server | host, port}`, `set_variable {name, value_type: int|double|string, value}`, `toast {toast, advancement_type, icon}`, `damage {target, amount, damage_type}`, `heal {amount}`, `merchant_trade {title, offers}`, `remove_entity`, `if_else`/`alternatives {rules: [{conditions, functions}]}`, `when {source, cases: [{when, functions}], fallback}`, `damage_item {amount, slot}`, `set_exp`/`set_level {count, add}`, `play_totem_animation {item, sound, ...}`, `close_inventory`, `discover_recipe {recipe}`, `clear_item {id, count}`, `js {script, function, args}` (needs `scripting.js.enable`). Compatibility-module functions (only when those plugins are present): `cast_mythic_skill`, `spawn_mythic_mob`, `take_money`, `give_money` [WIKI].

### 8.4 Conditions [SRC `CommonConditions.java` L16-50; WIKI reference/conditions]
`has_player`, `has_item`, `match_item {id(s), regex}`, `equipment {slot, id, tag, regex}`, `equipment_set {set, count|min|max}`, `match_entity {id, regex}`, `match_block {x,y,z, id, regex}`, `match_block_property {properties}`, `table_bonus {enchantment, chances}`, `survives_explosion`, `any_of {terms}`, `all_of {terms}`, `inverted {term}`, `enchantment {predicate: "minecraft:silk_touch>=1"}`, `falling_block`, `random {value, id?}`, `distance {min, max}`, `permission {permission}`, `has_discovered_recipe {recipe}`, `equals`/`string_equals {value1, value2}`, `regex {value, regex}`, `string_contains`, `expression {expression}`, `is_null {argument}`, `hand {hand}`, `on_cooldown {id}`, `on_item_cooldown {id}`, `inventory_has_item {id, count}`, `match_furniture_variant {variants}`, `is_bedrock_player`, `js`, `open_water`, `biome {biome(s)}`, `world {world(s)}`. Compatibility: `worldguard:region`, `has_money`, `test_flag` (registered by BukkitCraftEngine as `ce:test_flag`) [WIKI].

**Number providers** (`core:plugin/context/number/NumberProviders.java`): `constant`/`fixed` (shorthand `3`), `uniform {min,max}` (shorthand `1~3`), `expression` (shorthand string, sparrow-expression), `gaussian`/`normal`, `log_normal`, `skew_normal`, `binomial {extra, probability}`, `weighted`, `triangle {min,max,mode}`, `exponential`, `beta` [SRC; WIKI reference/number_format].

### 8.5 Context variables in text and expressions [SRC `core:plugin/context/parameter/*`, `text/StringTags.java`]
- `<arg:path>` chains through context parameters: `player.{name, uuid, x,y,z, yaw, pitch, block_x.., world, health, max_health, food, saturation, gamemode, is_sneaking, is_swimming, is_climbing, is_gliding, is_flying, main_hand_item, off_hand_item, fire_ticks, speed, game_edition, attr...}`, `item`/`item_in_hand.{id, count, custom_model_data, is_custom, is_block_item, is_empty, random.<name>}`, `block.{block_x,y,z, x,y,z, custom_block, custom_block_state, position, world}`, `position.{x,y,z, yaw, pitch, block_x.., biome, block, world}`, `entity.{...}`, `furniture.{id, variant, x,y,z, yaw, pitch, uuid}`, `world.{name, uuid, time}`, plus direct `hand`, `event`, `explosion_radius`, `falling_block`, and damage fields (`damage`, `final_damage`, `is_critical`, …) where provided.
- Other tags (names from the `super("...")` calls in `core:plugin/text/minimessage/*Tag.java` [SRC]): `<var:name>` (set_variable), `<global:...>` (`global_variable(s)` section), `<papi:...>`, `<viewer_papi:...>`, `<rel_papi:...>`, `<viewer_arg:...>`, `<random:...>`, `<expr:...>`, `<l10n:...>`, `<i18n:...>`, `<image:...>`, `<shift:N>`, `<head_texture:...>`. Argument syntax per tag is not documented here; read the tag class.
- **There is no `<arg:>` access to item PDC or arbitrary custom_data.** For TFC values, register a custom condition or function in Java.

### 8.6 Registering our own [SRC]
```java
// Function: reads its config once, runs with a Context
CommonFunctions.register(Key.of("tfc","add_heat"), section -> {
    double amount = section.getDouble("amount", 10);
    return ctx -> ctx.getOptionalParameter(DirectContextParameters.ITEM_IN_HAND)
                     .ifPresent(item -> Heat.add(((BukkitItem) item).getBukkitItem(), amount));
});
// Condition
CommonConditions.register(Key.of("tfc","is_rotten"), section ->
    ctx -> ctx.getOptionalParameter(DirectContextParameters.ITEM_IN_HAND).map(i -> Food.isRotten(i)).orElse(false));
```
- Signatures: `FunctionFactory<Context,T>.create(ConfigSection)`, `Function.run(CTX)`, `ConditionFactory<Context,T>.create(ConfigSection)`, `Condition extends Predicate<CTX>`. The registered key is used as `type: tfc:add_heat`. `Context` exposes `getOptionalParameter(ContextKey)`, `getParameterOrThrow`, `setVariable`/`getVariable` (`core:plugin/context/Context.java` L12-28).
- Register in our plugin's `onLoad` (before CE parses configs; general report §3.3).

---

## 9. Categories / item browser / creative

[SRC `core:plugin/gui/category/**`; CFG `assets:categories.yml`; WIKI configuration/category]
```yaml
categories:
  tfc:metals:
    name: "<!i><gold>Metals"
    lore: []
    icon: tfc:metal/ingot/bronze
    priority: 1          # lower first; ties sorted by id
    hidden: false        # true = only reachable as a nested '#tfc:metals'
    conditions: []
    source:              # or a list of sources
      - {type: list, list: [tfc:metal/ingot/copper, "#tfc:metals/bronze"]}
      - {type: regex, regex: ["tfc:metal/ingot/.*"]}
      - {type: all_items, ignore_vanilla: true}
```
- Items can also add themselves with the item key `category: [tfc:metals]`. They are appended after the source results.
- Browser: `/ce item browser` (player and admin variants), `/ce item get|give|clear`, `/ce item search-usage|search-recipe` [CFG commands.yml L30-115]. The GUI uses CE images and container title interception.
- **Creative inventory**: vanilla clients have no custom creative tab. CE creative tabs exist only for the CraftEngine client mod (`ClientboundCreativeModeTabItemsPacket`, config `network.mod-channel.creative-tab-max-items-per-packet`) [SRC `core:plugin/network/mod/protocol/ClientboundCreativeModeTabItemsPacket.java`; CFG config.yml L573-578]. Middle-click pick-block of CE blocks is handled by the block's `item` setting (general report).

---

## 10. Java extension points (summary)
All of these are static `register(Key, Factory)` calls on public classes. Do them in `onLoad`. An unnamespaced YAML `type` defaults to `craftengine:`. [SRC]

| What | Class.method | Factory signature |
|---|---|---|
| Item behavior | `core:item/behavior/ItemBehaviors.register` | `create(Pack, Path, Key itemId, ConfigSection)` |
| Item `data:` processor | `core:item/processor/ItemProcessors.register` | `ItemProcessorFactory.create(ConfigValue)` → `ItemProcessor{apply(ItemBuildContext); isConstant(); prepareNetworkItem(..)}` |
| Item setting | `core:item/setting/ItemSettingsModifiers.register` | `ItemSettingsModifierFactory(ConfigValue) → settings -> {...}` with `CustomItemSettingType` |
| Item updater op | `core:item/updater/ItemUpdaters.register` | `create(Key item, ConfigSection)` |
| Recipe result post processor | `core:item/recipe/result/PostProcessors.register` | `create(ConfigSection)` → `process(Item, ItemBuildContext)` |
| Transform processor | `core:item/recipe/transform/ItemTransformDataProcessors.register` | `create(ConfigSection)` → `TriConsumer<Item,Item,Item>` |
| Craft remainder | `core:item/recipe/remainder/CraftRemainders` (registry pattern) | |
| Ingredient predicate | `DataComponentPredicates` (core) / `BukkitDataComponentPredicates` | |
| Loot entry / function / formula / source | `LootEntryContainers`, `LootFunctions`, `Formulas`, `LootSources` `.register` | `create(ConfigSection)` |
| Number provider | `core:plugin/context/number/NumberProviders.register` | |
| Function / condition / trigger | `CommonFunctions.register`, `CommonConditions.register`, `EventTrigger.register(Key, aliases...)` | §8.6 |
| Category source | `core:plugin/gui/category/source/CategorySources.register` | |
| Config section parser | `CraftEngine.instance().packManager().registerConfigSectionParser(ConfigParser)` | general report |

Item API (stable `bukkit:api` package): `CraftEngineItems.loadedItems() / byId / byItemStack / isCustomItem / getCustomItemId`. `BukkitItemDefinition.buildBukkitItem(Player|ItemBuildContext[,count])`, `.settings()`, `.behavior()`, `.eventFunctions(EventTrigger)`. Everything else (`CraftEngine.instance().itemManager()/recipeManager()/lootManager()`) is internal and unstable [WIKI api].

---

## 11. Pitfalls
1. **Silent ignores**: unknown `settings:` keys, unknown `data:` keys that are not components, Premium-only keys (`client_bound_*`, `conditional`), and wiki-only settings (`anvil_repair_material`, `drag_repair_material`) all produce **no error**. Validate our generated YAML against the lists in §1.3 and §1.4.
2. **`category` vs `categories`**: the item parser checks `section.containsKey("category")` literally (AbstractItemManager L747), so `categories:` on an item is ignored even though the reader accepts that alias.
3. **For items, `events: break:` means `item_break`**, not block break (L49).
4. **Smelting/blasting/smoking/campfire `time` defaults to 80**, not 200. Always set it.
5. **Recipe `functions` and `visual_result` need Premium** (`onCraftingFinish` guard). Recipe conditions work in source; the wiki says Premium.
6. **Settings-derived processors run after `data:`**. `settings.equipment`/`equippable` and `settings.food` override `data.equippable`/`data.food`.
7. **The `custom_model_data` processor replaces the whole CMD component** (`floats:[v]`). Don't combine CE CMD with our Java-written floats, strings or colors.
8. **`pdc` data processor replaces `PublicBukkitValues` wholesale** at build time, and `updater reset` wipes custom_data unless kept.
9. **`disable_vanilla_behavior` defaults to true for custom items** but only stops placing the material's vanilla block. Vanilla right-click behaviors of tool materials (strip, till, path, bucket) still run.
10. **CE item tags (`settings.tags`) are not vanilla tags.** Vanilla systems (enchanting tables, bartering, vanilla `tool` rules referencing item tags, datapack advancements) only see the material's tags.
11. **Break power and correct-tool checks only apply to CE blocks.** Vanilla blocks use vanilla rules.
12. **An incorrect tool means no drops** for CE blocks with `require_correct_tools`/`required_break_power`, even in creative-like contexts with a player. Don't put `required_break_power` on blocks that must always drop (e.g. TFC loose rocks).
13. **Item behaviors on vanilla-interactable blocks** are skipped unless the player sneaks (e.g. our chisel on a vanilla crafting table). On CE blocks, the block behavior gets the click first.
14. **There is no API to add CE recipes**, and Paper-registered recipes are invisible to CE's crafting logic. Custom-item leakage into vanilla or Paper recipes is possible (§12).
15. **`/minecraft:reload` invalidates CE recipes**; run `/ce reload recipe` afterwards [WIKI].
16. **CE rewrites every item packet** (with item interception on). Coordinate with our tooltip layer, strip our lore on creative c2s, and avoid `<...>` text in our lore.
17. **`client_bound_material` / `client_bound_data` / `item.client-bound-model: true`** in config.yml are Premium and inert. Do not rely on per-player item visuals from CE.
18. **The default material `nether_brick`** is a vanilla ingredient and has vanilla tags. Always set `material` explicitly.
19. **`food` alone does not make an item edible on 1.21.2+**; it needs `consumable`.
20. **Equipment without `slot`** keeps the material's slot. With `slot`, a full `equippable` is written. Trim-based armor sacrifices chainmail's look (Premium `legacy_armor` pack fixes it).
21. **Update triggers are off by default and costly.** Don't depend on the updater for gameplay data. Do our own migrations on PDC.
22. **Wiki vs source drift**: the wiki tracks newer builds (e.g. `anvil_repair_material`). Trust this commit's source for what our pinned CE version accepts.

---

## 12. UNVERIFIED / open questions
1. Whether a CE item whose `material` is X satisfies **vanilla or Paper recipes** whose ingredient is X (the FastNMS recipe classes and vanilla `Ingredient` patching are closed source). `ingredient_substitute`'s existence suggests CE recipes do not accept it, but vanilla recipes that CE does not replace may. Test: give `material: iron_ingot` to a CE item and try the vanilla iron block recipe.
2. Whether the vanilla `tool` component can reference a **non-existent block tag** (e.g. `#tfc:mineable/chisel`) attached to CE holders: the codec may reject unknown tags at item build. Test, or stick to vanilla `minecraft:mineable/*` tags.
3. The `fall` trigger: registered, but no dispatch site was found in `bukkit/`.
4. Netty pipeline ordering between our item-lore packet handler and CE's item interception, and CE's `rebase()` effect on our changes.
5. Recipe `conditions` in Community: the source path shows no Premium guard for crafting; the wiki says Premium.
6. Whether `BlockDropItemEvent` fires for CE block drops produced by the intercepted `getDrops` (likely yes, since it is the vanilla path).
7. Argument syntax of `<global:>`, `<random:>` and `<expr:>` (the tag names are confirmed, the argument formats are not).
8. Live-mirror semantics of `UseOnContext.getItem()` (does mutating `getBukkitItem()` change the held stack?). Probably yes (wraps the NMS stack). Verify.
9. Whether `Bukkit.addRecipe` recipes survive `/ce reload recipe` (inferred from the map-copy implementation).
10. TFC tool-tier numbers in §2.2 are from memory; confirm against the TFC source (`Metal.Tier`).
