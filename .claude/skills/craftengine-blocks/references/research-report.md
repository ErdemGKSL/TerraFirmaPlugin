# CraftEngine custom BLOCKS: deep reference (settings, properties, states, models, every behavior)

> Note for this repo: researched at CraftEngine commit b3079c5 (v26.9.2), 2026-10-16. Examples say `tfc:`; our provisional namespace is `tfp:` (see `CLAUDE.md`). Shipped `default:` templates are cited as reference designs only; we re-author them as `tfp:` templates (see `craftengine-config-generation`).

Researched 2026-10-16 against `Xiao-MoMi/craft-engine` commit `b3079c5` (v26.9.2), local clone `scratchpad/src/ce`.
This report goes deeper than `scratchpad/research/craftengine.md` (§4.5, §4.6, §5, §6 there) and does not repeat its visual-state-pool or worldgen material.

Path abbreviations used below:
- `CORE/` = `core/src/main/java/net/momirealms/craftengine/core/`
- `BB/` = `bukkit/src/main/java/net/momirealms/craftengine/bukkit/block/behavior/`
- `BK/` = `bukkit/src/main/java/net/momirealms/craftengine/bukkit/`
- `RES/` = `common-files/src/main/resources/resources/`
- WIKI = `https://xiao-momi.github.io/craft-engine-wiki/configuration/block/...`

Tags: **[SRC]** read in source, **[CFG]** shipped config, **[WIKI]**, **[INF]** my inference from source, **UNVERIFIED**.

---

## Table of contents
1. [Mental model in 10 lines](#1-mental-model-in-10-lines)
2. [Block config structure](#2-block-config-structure)
   - 2.1 Top-level keys · 2.2 `state` vs `states` · 2.3 Properties · 2.4 Appearances · 2.5 Variants (matching rules) · 2.6 `id` pinning · 2.7 Inline blocks in items · 2.8 Loot · 2.9 Events
3. [Block settings (every key)](#3-block-settings)
   - 3.1 Table · 3.2 Visual-derived defaults · 3.3 Shipped settings templates · 3.4 Other shipped block templates
4. [Model forms for appearances](#4-model-forms-for-appearances)
5. [Behavior composition, ordering, hardcoded behaviors](#5-behavior-composition)
6. [Built-in behaviors: overview table](#6-built-in-behaviors-overview)
7. [Built-in behaviors: details](#7-built-in-behaviors-details)
8. [TFC families → behaviors or gaps](#8-tfc-families)
9. [Pitfalls](#9-pitfalls)
10. [UNVERIFIED / open questions](#10-unverified--open-questions)

---

## 1. Mental model in 10 lines
- Every **combination of property values** (Cartesian product) is one CE `ImmutableBlockState`. Each one is bound to its **own** server-side NMS block `craftengine:custom_N`, which has exactly one state. Settings like friction, sound type and explosion resistance are written onto that per-state NMS block. [SRC `BK/block/BukkitBlockManager.java` L273–390 `applyPlatformSettings`; `CORE/block/AbstractBlockManager.java` L544–555 one internal id per state string]
  - Consequence: `custom_N` budget (`block.serverside-blocks`, default 2000) is consumed **per state**. A 4-facing × 2-half × 5-shape × 2-waterlogged stairs block uses 80 of them.
  - Consequence [INF]: any vanilla `state.is(block)` comparison against a CE block (for example `spreading_block`/`surface_spreading_block` targets) matches **one exact CE state**, not the whole CE block.
- Each state is sent to clients as a **visual vanilla state** (appearance). Hitbox, client prediction, skylight occlusion and other "visual-derived" defaults come from that vanilla state.
- **Behaviors** are Java objects bound to the block. Several behaviors are wrapped in `DualBlockBehavior` (2) or `CompositeBlockBehavior` (3+), and **hardcoded** behaviors are added automatically for the special property names `axis`, `facing`, `facing_clockwise`, `rotation` and `waterlogged`. [SRC `BK/block/BukkitBlockManager.java` L168–206]

---

## 2. Block config structure

### 2.1 Top-level keys of a block entry [SRC `CORE/block/AbstractBlockManager.java` L473–486, L487–855]
| Key (aliases) | Meaning |
|---|---|
| `settings` | `BlockSettings` (§3). Template-able (`settings::template:`). Applied to **all** states, then variant overrides. |
| `state` / `states` | Same parser (`ConfigKeys.of("state(s)")`). See 2.2. **Required** (`getNonNullSection`). |
| `behavior` / `behaviors` | One map or a list of maps, each `{type: ..., ...}`. `type` without namespace → `craftengine:`. |
| `loot` | Inline loot table map, or an id string (loot table reference). See 2.8. |
| `event` / `events` | List of `{on: <trigger>, conditions: [...], functions: [...]}`. Block-level alias `break` → `block_break`. See 2.9. |
| `entity_culling` | `false` or `{aabb, view_distance, aabb_expansion (0.5), ray_tracing (true)}`; block-level, applied to all appearances. Culling itself is Premium (see main report). |

Vanilla ids (e.g. `minecraft:stone:`) under `blocks:` are parsed by `parseVanillaBlock` and only honour `settings.client_bound_tags` (re-tag a vanilla block client-side). [SRC L462–471]

### 2.2 `state` vs `states` [SRC L497–627; WIKI `states/`]
- No `properties` (or empty) → **single-state block**. The appearance keys sit directly under `state:` (or under a single-entry `appearances:` map; more than one entry is an error `appearance_without_properties`). [SRC L609–619]
- With `properties` → multi-state. `appearances` is then **required** (`getNonNullSection`), `variants` optional.

```yaml
# single state, RES/default_assets/configuration/blocks/topaz_ore.yml L44-47
state:
  auto_state: solid
  texture: minecraft:block/custom/topaz_ore     # 1 texture → cube_all, model path = texture path
```
```yaml
# multi state skeleton
states:
  id: 0                    # optional fixed first custom_N index (2.6)
  properties: { age: { type: int, range: 0~7, default: 0 } }
  appearances:
    young: { auto_state: sapling, model: ns:block/x_young }
    old:   { auto_state: sapling, model: ns:block/x_old }
  variants:
    age=7: { appearance: old, settings: { luminance: 3 } }
```

### 2.3 Properties (every type) [SRC `CORE/block/property/Properties.java` L16–31 and factories]
Property map key = property name. Each entry needs `type`. Unknown `type` → error `resource.block.state.property.unknown_type`.

| `type` | Values | Options | Default if `default` omitted |
|---|---|---|---|
| `boolean` | `true`, `false` | `default` (bool) | `false` (`getBoolean`) [SRC `BooleanProperty` L83–88] |
| `int` | `min..max` | `range: "a~b"` **or** `min` + `max` (both required); `default` clamped to range | `min` [SRC `IntegerProperty` L98–116] |
| `string` | listed strings | `values: [..]` (non-empty, required), `default` | first value [SRC `StringProperty` L93–108] |
| `axis` | `x, y, z` | optional `values` subset, `default` | first value (`x`) |
| `horizontal_direction` / `4-direction` | `north, east, south, west` | `values`, `default` | `north` |
| `direction` / `6-direction` | `down, up, north, south, west, east` | `values`, `default` | `down` |
| `single_block_half` | `bottom, top` | `values`, `default` | `bottom` |
| `double_block_half` | `upper, lower` | `values`, `default` | **`upper`** (enum order) |
| `hinge` | `left, right` | | `left` |
| `stairs_shape` | `straight, inner_left, inner_right, outer_left, outer_right` | | `straight` |
| `slab_type` | `top, bottom, double` | | **`top`** |
| `sofa_shape` | `straight, inner_left, inner_right` | | `straight` |
| `anchor_type` | `floor, wall, ceiling` | | `floor` |
| `bed_part` | `head, foot` | | `head` |

Enum factory: `values` (list, de-duplicated) restricts allowed values; `default` is matched against the **lowercase** enum name; no match → first value. [SRC `EnumProperty.Factory` L120–149] Enum names are from `CORE/block/property/type/*.java` and `CORE/util/Direction.java` L13–18.

Special property names that add hardcoded behaviors: `axis`, `facing`, `facing_clockwise`, `rotation`, `waterlogged` (§5.3). [SRC; WIKI states]

### 2.4 Appearances [SRC L606–747]
Each appearance (or the `state:` section of a single-state block) accepts:

| Key | Meaning |
|---|---|
| `auto_state: <group>` or `auto_state: {type: <group>, id: <share-key>}` | Take a free vanilla state from a pool (groups listed below). Same `id` within the same group = same vanilla state. Without `id`, cache key is `blockId` or `blockId[appearance=name]`. [SRC L632–651] |
| `state: <vanilla state string>` | Explicit vanilla state (no pool). Changing its model changes it globally. [SRC L653–660] |
| `transparent: true` | Writes `minecraft:block/empty` as that vanilla state's model. Combine with `entity_renderer`. [SRC L694–697] |
| `texture` / `textures` | Simplified model generation (§4). |
| `model` / `models` | Model path string, a model map, or a list of model maps (weighted). (§4) |
| `blueprint` | `.bbmodel` source converted to a block model (§4). |
| `x`, `y`, `z`, `uvlock`, `weight` | Blockstate-variant fields; used when the appearance itself defines `texture` or `blueprint`; with `model:` maps put them **inside** the model map. [SRC L703–731, L1003–1038] |
| `entity_renderer` / `entity_render` | One element or a list (see main report §4.7). |

`auto_state` groups [SRC `CORE/block/AutoStateGroup.java`]: `solid`, `note_block`, `mushroom`, `brown_mushroom_block`, `red_mushroom_block`, `mushroom_stem`, `leaves`, `waterlogged_leaves`, `tintable_leaves`, `waterlogged_tintable_leaves`, `non_tintable_leaves` (`no_tint_leaves`, `leaves_no_tint`), `waterlogged_non_tintable_leaves` (+aliases), `tripwire`, `lower_tripwire` (attached=true), `higher_tripwire` (attached=false), `sugar_cane`, `cactus`, `kelp`, `chorus`, `cave_vines`/`cave_vine`, `weeping_vines`/`weeping_vine`, `twisting_vines`/`twisting_vine`, `pressure_plate`, `sapling`. Pool sizes: main report §5.1–5.2.

The **first appearance in YAML order** is the fallback for every state that no variant bound. [SRC L683–746, L820–826]

An appearance without any model key keeps the vanilla look of its vanilla state (hami melon reuses `pumpkin_stem[age=N]`, `RES/default_assets/configuration/blocks/hami_melon.yml` L112–128; amethyst torch reuses `redstone_torch[lit=false]` under an item_display, `amethyst_torch.yml` L47–51).

### 2.5 Variants: matching rules (precise) [SRC L754–792; `CORE/block/parser/BlockNbtParser.java` L17–43; `CORE/block/BlockStateVariantProvider.java` L64–83]
- Key syntax: `prop=value,prop2=value` (unquoted strings, comma-separated, order irrelevant). One value per property. No OR, no ranges, no negation.
- Properties not in the key are wildcards (all values).
- **Unknown property names and invalid values are silently dropped** (`propertyProvider.apply(name) == null` → skipped; `createOptionalTag` empty → skipped). A typo such as `ag=3` or `age=9` therefore turns that entry into a **match-all wildcard**. Only a syntactically broken key raises `invalid_variant_nbt`.
- `settings:` in a variant: for each matching state, `BlockSettings.ofFullCopyAndApply(current, override)`. Variants are processed in YAML order, so **later matching entries override earlier ones key by key**. [SRC L769–774]
- `appearance:` in a variant: for each matching state, the visual state, culling data and constant renderers are **overwritten**. The loop runs in YAML order, so **the LAST matching variant wins**. The wiki says "the first matching entry sets it. Later entries do not override". The source (L777–789) has no "already set" check, so the wiki is wrong for this commit. **Write non-overlapping keys to be safe.**
- Unknown `appearance` name → error `undefined_appearance`; the variant is skipped.

### 2.6 `id` pinning [SRC L515–555; WIKI states]
`states.id: N` allocates `custom_N .. custom_{N+stateCount-1}` contiguously in property-product order. Range must be within `[0, serverside-blocks)`; overlap with another pinned block → `real_state_conflict`. Without `id`, ids are auto-allocated per state string and cached in `plugins/CraftEngine/cache/custom_block_states.json`. Pin only when an external datapack/tool needs `craftengine:custom_N`.

### 2.7 Inline blocks in items [SRC `BK/item/behavior/BlockItemBehavior.java` Factory]
Item behaviors `block_item`, `wall_block_item`, `ground_block_item`, `ceiling_block_item`, `double_high_block_item`, `multi_high_block_item`, `liquid_collision_block_item` take `block:` as either an id or a full block section. A section is queued as a block config **with the item's id**:
```yaml
# RES/default_assets/configuration/blocks/flame_cane.yml L1-17
items:
  default:flame_cane:
    texture: minecraft:item/custom/flame_cane
    behavior:
      type: block_item
      block:
        settings: { template: [default:hardness/none, default:sound/grass], overrides: { push_reaction: popped } }
        behaviors: [ { type: vertical_crop_block, max_height: 4, grow_speed: 1, direction: up } ]
        loot: { template: default:loot_table/self }
        states: { ... }
```
- `settings.item` defaults to the **block id** (`BlockSettings.of().itemId(id)`, SRC L489). For a standalone block whose item has a different id (seeds, torches), set `settings.item` (pick-block, and `default:loot_table/self` resolve through it [INF]).
- Multi-block placement (`double_high_block`, `multi_high_block`, doors) needs the matching item behavior (`double_high_block_item`, `multi_high_block_item`; `tree.yml` L277 uses `double_high_block_item` for doors). [CFG]
- Placement runs `updateStateForPlacement` through all behaviors and then checks `canSurvive` (`BlockItemBehavior` L268). Fires `CustomBlockAttemptPlaceEvent`, then runs the block's `place` event functions (L178).

### 2.8 Loot [SRC `CORE/plugin/config/ConfigValue.java` L768–774; `BK/plugin/injector/BlockStateGenerator.java` L120–150]
- `loot: {pools: [...]}` inline, `loot: {template: default:loot_table/..., arguments: {...}}`, or `loot: ns:id` (reference; may resolve to a datapack loot table).
- Drops are produced by the injected NMS `getDrops`. **No drops if the breaking entity's tool fails `isCorrectTool`** (`require_correct_tools`/`correct_tools`/`required_break_power`, §3), only when an entity is present.
- Shipped loot templates (`RES/default_templates/configuration/templates/loot_tables.yml`) and their arguments: `default:loot_table/self` (none), `basic` (`item`, `count:-1`), `silk_touch` (`item`, `count`), `furniture` (`item`), `door`, `slab`, `seed_crop` (`crop_item`, `ripe_age`, `crop_item_count:-1`, `crop_seed`, `extra_seed_count:-3`), `crop` (`crop_item`, `ripe_age`, `extra_crop_count:-3`), `ore` (`ore_block`, `ore_drop`, `ore_drop_count:-1`), `leaves` (`leaves`, `sapling`).
- Per-state loot: use conditions `match_block_property` in pools/functions (pebble example `pebble.yml` L26–47).

### 2.9 Events on blocks [SRC `BK/item/listener/ItemEventListener.java` ~L218–275; `BK/block/listener/BlockEventListener.java` L200–275; `BK/item/behavior/BlockItemBehavior.java` L178]
| Trigger | When | Context |
|---|---|---|
| `right_click` / `left_click` | `PlayerInteractEvent` on a custom block, after `CustomBlockInteractEvent`, **before** behavior `useOnBlock`/`useWithoutItem` | player, hand, item_in_hand, block, custom_block_state, position |
| `block_break` (`break`) | Player breaks the block, after `CustomBlockBreakEvent` | player, block, state, item_in_hand |
| `step` | Player `GameEvent.STEP` on the block (players only) | |
| `place` | After placement by a `block_item` | |
- In click events, `cancel_event` cancels the Bukkit event **and skips the behavior interaction**. If a function marks interaction (`update_interaction_tick`), behaviors are skipped this tick (`hasInteractionInThisTick`).
- Real example, harvest-and-replant on right click (`ender_pearl_flower.yml` L57–78): conditions `match_block_property {age: 2}` + `match_item`, functions `break_block`, `place_block {block_state: default:ender_pearl_flower[age=0]}`, `set_count {add: true, count: -1}`, `swing_hand`, `update_interaction_tick`.
- `break_block` calls `player.breakBlock(x,y,z)` (simulated player mining → normal loot with the held tool). [SRC `CORE/plugin/context/function/BreakBlockFunction.java` L33]
- Shipped event template `default:rotatable_block` (`events.yml`): sneak + empty hand → `cycle_block_property facing` N→E→S→W.

---

## 3. Block settings

### 3.1 Every key [SRC `CORE/block/setting/BlockSettingsModifiers.java` L16–188; defaults `CORE/block/setting/BlockSettings.java` L18–53; application `BK/block/BukkitBlockManager.java` L273–390]
Key normalisation: `-` → `_`, and anything after `#` is stripped (`hardness#a`). **Unknown keys are silently ignored** (`Optional.ofNullable(...).ifPresent`, BlockSettings L75–90). External plugins can add keys via `BlockSettingsModifiers.register(Key, factory)` and store values in `customData`.

| Key | Type | Default | Effect / notes |
|---|---|---|---|
| `item` | item id | block id | Item for pick-block and related logic. |
| `name` | string | null | Stored. **No reader found** in core/bukkit (grep). The block translation key is always `block.<ns>.<path>` (`BlockDefinition.translationKey`). |
| `hardness` | float | **2.0** | Destroy speed. `0` = instant break. |
| `resistance` | float | **2.0** | Explosion resistance (per state NMS block). |
| `incorrect_tool_dig_speed` | float | 0.3 | Mining progress multiplier when `isCorrectTool` fails (`BukkitServerPlayer.getDestroyProgress` L948–958). |
| `require_correct_tools` | bool | false | If true, drops/speed need a correct tool. **Implicitly true when `correct_tools` is non-empty** (`requireCorrectTool()` L217–219). |
| `correct_tools` | list of item ids or `#item_tag` | [] | Correct tool set (tags expanded lazily through CE item tags). |
| `respect_tool_component` | bool | false | Also accept vanilla `minecraft:tool` `correct_for_drops` against the custom state. |
| `required_break_power` | int | 0 | Tool must have `break_power` ≥ this (vanilla tiers: wood/gold 1, stone/copper 2, iron 3, diamond 4, netherite 5 [WIKI settings]). |
| `tags` | list of block tags (`#` optional) | [] | Server-side tags on that state's `custom_N` holder (`mineable/*`, `logs`, `dirt`, …). Behaviors such as leaves (`minecraft:logs`) and bush (`bottom_block_tags`) read these. |
| `sounds` | `{break, step, place, hit, fall}` | **all empty** | Each: sound id or `{id, volume, pitch}` (volume range `a~b` allowed). Default volume/pitch per slot in `CORE/block/BlockSounds.java` L37–41. Without `sounds`, the block is silent. |
| `luminance` | int 0–15 | 0 | Light emission (per state, so it can be set per variant). |
| `block_light` / `light_block` / `light_dampening` | int | -1 = take from visual | Light dampening. |
| `propagate_skylight` | bool | from visual | Server light only; the client decides skylight occlusion itself [WIKI]. |
| `can_occlude` | bool | from visual | Occlusion. |
| `use_shape_for_light_occlusion` | bool | from visual | |
| `is_suffocating` | bool | `canBlockView(visual)` | |
| `is_view_blocking` | bool | = suffocating | |
| `is_redstone_conductor` | bool | **false** (UNDEFINED → `asBoolean()` false) | Not derived from the visual. Full blocks need `true` (the `solid_1x1x1` template sets it). |
| `replaceable` | bool | false | Like grass: placing a block on it replaces it (`BlockBehavior.canBeReplaced` L449–467; the block's own item never replaces it). |
| `push_reaction` | enum | `push_pull` | `push_pull`, `push`, `popped`, `immoveable`, `ignore_entity`; legacy `normal`, `destroy`, `block`, `ignore`, `push_only` (`PushReaction.java`). |
| `map_color` | int id, name (e.g. `stone`, `wood`, `plant`, `sand`, `deepslate`), or color | `CLEAR` | Map colour. A colour value maps to the nearest map colour. |
| `instrument` | note-block instrument name | `harp` | Instrument when under a note block. An invalid name logs a warning and falls back to harp. |
| `burnable` | bool | false | Ignited by lava, registered with fire. |
| `burn_chance` | int 0–100 | 0 | Vanilla igniteOdds. |
| `fire_spread_chance` | int 0–100 | 0 | Vanilla burnOdds. |
| `fluid_state` | string | empty | Only `"water"` is recognised (`state.equals("water")`) → source water fluid state. Set it per variant for `waterlogged=true`. |
| `is_randomly_ticking` | bool | false | **Only used when the block's behavior is not a `RandomTickBlock`**. Dual/Composite behaviors always implement `RandomTickBlock`, so with 2+ behaviors (including hardcoded ones) this setting is ignored (§5.2). |
| `support_shape` | vanilla state string | visual | Overrides only the support shape (what attaches on top). |
| `friction` | float | 0.6 | "Unstable": the client predicts movement from the visual block [WIKI]. |
| `speed_factor` | float | 1.0 | Unstable, as above. |
| `jump_factor` | float | 1.0 | Unstable, as above. |
| `bounce_restitution` | float | 0.0 | Only on MC ≥ 26.2 (`setBounceRestitution`). |
| `block_raytrace` | bool | false (→ `canBlockView(visual)`) | Marks state as view-blocking for CE's internal raytrace table (entity culling). |
| `destroy_stages` | `{items: [..] (required), position, translation, scale, pitch, yaw, rotation, display_transform, billboard, view_range, brightness{block_light,sky_light}}` | none | Replaces the crack overlay with item_display stages (`DestroyStageDisplayEntitySetting.fromConfig` L61–81). |
| `client_bound_tags` | list | — | **Vanilla block entries only.** |

Per-state overrides: any of these under `variants.<key>.settings` (§2.5).

### 3.2 Defaults derived from the visual state [SRC `BukkitBlockManager.applyPlatformSettings` L294–352, L381–386]
Derived when unset: `can_occlude`, `use_shape_for_light_occlusion`, `is_suffocating` (from `canBlockView(visual)`), `is_view_blocking` (= suffocating), `block_light` (light dampening), `propagate_skylight`, `block_raytrace`. **Shape / collision / support shape always come from the visual state** (only `support_shape` is overridable). Pathfinding defaults to the visual's `isPathfindable` (`BB/BukkitBlockBehavior.java` L31–42). Fall damage defaults to full vanilla fall damage (L44–52).
**Not** derived (fixed defaults): hardness 2, resistance 2, redstone conductor false, sounds empty, map colour clear, push reaction push_pull, instrument harp, randomly ticking false.

### 3.3 Shipped settings templates [CFG `RES/default_templates/configuration/templates/block_settings.yml`]
| Template | Contents |
|---|---|
| `default:settings/solid_1x1x1` | is_suffocating, is_view_blocking, is_redstone_conductor, can_occlude true; replaceable false |
| `default:settings/transparent_1x1x1` | all of the above false |
| `default:settings/sapling` | hardness/none + sound/grass; push_reaction popped, map_color plant, tags mineable/axe, saplings, sword_efficient |
| `default:settings/leaves` | sound/grass, burn_data/leaves, hardness 0.2, transparent_1x1x1, popped, map_color plant, tags mineable/hoe, sword_efficient, leaves, replaceable_by_trees |
| `default:settings/wood` | sound/wood, burn_data/wood, hardness/wood, instrument bass, tags mineable/axe, logs_that_burn, logs, completes_find_tree_tutorial, map_color wood |
| `default:settings/planks` | sound/wood, burn_data/planks, hardness/planks, solid_1x1x1, bass, mineable/axe, map_color wood |
| `default:settings/ore` | sound/stone, solid_1x1x1, hardness `${hardness:-3.0}`, resistance `${resistance:-3.0}`, basedrum, map_color stone, mineable/pickaxe, `correct_tools: ["#minecraft:pickaxes"]`, `required_break_power: ${break_power}` (**argument required, no default**) |
| `default:settings/deepslate_ore` | same with sound/deepslate, hardness `${hardness:-4.5}` |
| `default:hardness/{none 0/0, leaves 0.2, glass 0.3, stone 1.5/6, deepslate 3/6, obsidian 50/1200, wool 0.8, wood 2/2, planks 2/3, button 0.5, melon 1}` | hardness/resistance pairs |
| `default:burn_data/{leaves 30/60, wood 5/5, planks 5/20, wool 30/60, vine 15/100, carpet 60/20, coal 5/5, grass 60/100}` | burnable + burn_chance/fire_spread_chance |
| `default:sound/{grass, wood, stone, deepslate, glass, sand, gravel, metal, snow, bamboo, vine, lantern, amethyst_block}` | via `default:sound/block_template` (`minecraft:block.${block_type}.{break,step,place,hit,fall}`); plus `default:sound/crop`, `default:sound/stem_crop` |

Usage pattern (template list + overrides) [CFG `amethyst_torch.yml` L37–46]:
```yaml
settings:
  template: [default:sound/wood, default:hardness/none]
  overrides: { push_reaction: popped, luminance: 15, item: default:amethyst_torch }
```

### 3.4 Other shipped block-state templates [CFG `RES/default_templates/configuration/templates/block_states.yml`]
| Template | Arguments | Visual approach |
|---|---|---|
| `default:block_state/pillar` (L2) | `pillar_model`, `pillar_texture_top`, `pillar_texture_side` | 3 `auto_state: solid` appearances (cube_column, cube_column_horizontal x90/y90) |
| `default:block_state/leaves` (L80) → `tintable_leaves` / `non_tintable_leaves` / `__leaves__` (L45) | `leaves_tintable:-false`, `leaves_texture` / `leaves_model`, `leaves_max_distance:-7` | properties waterlogged, persistent (**default true**), distance 1..max; auto_state `(waterlogged_)(non_)tintable_leaves`; waterlogged variant sets `resistance 1200, burnable false, fluid_state water` |
| `default:block_state/trapdoor` (L98) | `trapdoor_base_block`, `trapdoor_model`, `trapdoor_texture` | explicit `<base>_trapdoor[...,powered=true]` states (freed by mappings) |
| `default:block_state/door` (L415) | `door_base_block`, `door_model`, `door_texture_top/bottom` | explicit `<base>_door[...,powered=true]` |
| `default:block_state/fence_gate` (L688) | `fence_gate_base_block`, `fence_gate_model`, `fence_gate_texture` | explicit freed fence gate states |
| `default:block_state/slab` (L875) | `slab_base_block`, `slab_model`, `slab_texture(_top/_side/_bottom)`, `planks_model` | explicit `<base>_slab[type=..,waterlogged=..]` |
| `default:block_state/stairs` (L945) | `stairs_base_block`, `stairs_model`, `stairs_texture(_top/_side/_bottom)` | explicit `<base>_stairs[...]` (80 states) |
| `default:block_state/pressure_plate` (L1631) | `pressure_plate_model`, `pressure_plate_texture` | `auto_state: pressure_plate` |
| `default:block_state/fence` (L1658) | `fence_base_block`, `fence_post_item`, `fence_side_item` | vanilla fence state as hitbox + item_display entity renderers |
| `default:block_state/button` (L2098) | `button_base_block`, `button_pressed_item`, `button_not_pressed_item` | vanilla button state + item_display |

Freed vanilla stairs/slabs in default `mappings.yml`: stairs only `cut_copper`, `exposed_cut_copper`, `weathered_cut_copper`, `oxidized_cut_copper` (80 each); slabs only those 4 copper + `petrified_oak_slab` (6 each). [CFG `RES/internal/configuration/mappings.yml`, grep counts] So **only 4 stairs and 5 slab blocks can have real models out of the box**. More need extra `block_state_mappings` (§8, §9).

---

## 4. Model forms for appearances [SRC `CORE/block/AbstractBlockManager.java` L109–115, L693–736, L857–877, L914–1038; `CORE/pack/model/simplified/block/*`; `CORE/pack/model/generation/ModelGeneration.java` L78–115]

| Form | Example | Result |
|---|---|---|
| `texture: tex` (1 texture) | `texture: minecraft:block/custom/topaz_ore` | generates `cube_all` model **at the texture's path** |
| `texture(s): [t1..tn]` + `model: path` (appearance level) | `textures: [a_top, a_side]`, `model: ns:block/a` | n=1 `cube_all` (`all`), 2 `cube_column` (`end, side`), 3 `cube_bottom_top` (`bottom, side, top`), 4 `orientable` (`bottom, front, side, top`), 5 `cube` (east/west share), ≥6 `cube` (`down, up, north, south, west, east`). n ≥ 2 without `model` → error. |
| `model: path` | `model: minecraft:block/custom/reed` | uses an existing model file in the pack |
| `model: {path, generation: {parent, textures, display, gui_light, ambient_occlusion}, x, y, z, uvlock, weight}` | pillar template | generates the model JSON at `path`. `textures` values may be `#ref` |
| `model: {path?, texture(s): [...], x, y, ...}` | `tree.yml` L159–161 planks | simplified generation inside the map. `path` optional if exactly 1 texture. If both `generation` and `textures` are present, `generation` wins (the wiki says they are mutually exclusive) |
| `models: [ {path, weight, x, y, ...}, ... ]` | `pebble.yml` L58–69 | weighted random variant list (same as vanilla blockstate arrays) |
| `blueprint: file.bbmodel` (+ optional `model`/`path` as output path) | | converted by `BBModelConverter` |
| `transparent: true` | | `minecraft:block/empty` |

**Texture-to-slot assignment is name-based, not purely positional** (`TextureSlotAssigner.assign`): (1) the file name equals a slot or ends with `_<slot>` (e.g. `granite_top` → `top`); (2) the file name *contains* a slot name; (3) the remaining textures fill the remaining slots in order. Example with 2 textures (slots `end, side`): `[log_top, log]` matches no slot in passes 1–2, so pass 3 assigns by order: `log_top` → `end`, `log` → `side`. With 3 textures (`bottom, side, top`), `[granite_bottom, granite_side, granite_top]` is matched by suffix regardless of order. A name like `raw_granite_side_mossy` contains `side`, so it is put in `side` regardless of position. **Name generated textures predictably or use `generation.textures` explicitly.**
- `^` prefix marks the particle texture: `textures: ["^ns:block/x_particle", ...]`.
- Rotations `x`/`y`/`z` must be multiples of 90 (else `invalid_rotation`); `0` is omitted. `uvlock` and `weight` are copied as given.
- Single vanilla state → only one model JSON. A second, different model on the same vanilla state → `model_conflict` (L940–955). Identical JSON is allowed (shared states).
- Model paths are asset paths (`ns:block/...` resolves to `assets/ns/models/block/...`). Shipped examples keep custom models under `minecraft:block/custom/...`.

---

## 5. Behavior composition

### 5.1 Build order [SRC `BK/block/BukkitBlockManager.java` L168–206]
1. Hardcoded property behaviors, in **property declaration order** (`axis`, `facing`, `facing_clockwise`, `rotation`).
2. User behaviors in YAML list order.
3. `WaterloggedBlockBehavior` last, if a boolean `waterlogged` property exists.
Count 0 → plain `BukkitBlockBehavior`; 1 → that behavior; 2 → `DualBlockBehavior`; ≥3 → `CompositeBlockBehavior`.

### 5.2 Combination semantics [SRC `BB/CompositeBlockBehavior.java` L30–569; `BB/DualBlockBehavior.java`]
| Hook | Combination |
|---|---|
| `getFirst(Class)` | First behavior in build order that `instanceof` the class (null if none). `let(Class, consumer)` calls the consumer for every match. Plain behaviors return `this` if it matches. Use `state.behavior().getFirst(MyBehavior.class)`, never cast. |
| `updateStateForPlacement` | Chained in order; any `null` aborts placement. Hardcoded `facing` runs first, so a later behavior can override `facing`. |
| `updateShape` | Chained: each gets the previous result in `args[0]`. |
| `canSurvive` | **AND** of all. |
| `useOnBlock` | First result that is not `PASS`/`TRY_EMPTY_HAND` wins; else `PASS` if any passed; else default `TRY_EMPTY_HAND` (then `useWithoutItem`). |
| `useWithoutItem` | First non-`PASS` wins. |
| `tick`, `randomTick`, `onPlace`, `neighborChanged`, `entityInside`, `stepOn`, explosions, `spawnAfterBreak`, `attack`, `handlePrecipitation`, `placeMultiState` | all called in order |
| `canRandomlyTick` | OR over behaviors that implement `RandomTickBlock` |
| bone meal | valid if any is valid; `performBonemeal` runs on every `BonemealableBlock` whose `isBonemealSuccess` passes |
| `canBeReplaced` | OR (the source comment says AND, but the code is OR) |
| `canPlaceMultiState` | AND; `hasMultiState` OR |
| signals | `getSignal`/`getDirectSignal` max; analog output = average of non-zero |
| `fallOn` | first `PrioritizedFallOnHandler` (`bouncing_block`) else default |
| fluids, container, pickup | first behavior implementing `LiquidBlockContainer` / `WorldlyContainerHolder` / `BucketPickup` |
| block entity | `createBlockEntityController` gathers controllers of every `EntityBlock` behavior; `initControllerId(i)` numbers them 0.. in order. Any `EntityBlock` → every state `hasBlockEntity`. |

**Random ticking rule** (`applyPlatformSettings` L354–358): if the bound behavior object is a `RandomTickBlock`, `canRandomlyTick(state)` decides per state, else `settings.is_randomly_ticking`. Dual/Composite **always** implement `RandomTickBlock`. So a block with `facing` plus one non-ticking behavior, or two non-ticking behaviors, **never random-ticks**, whatever `is_randomly_ticking` says. A custom Java behavior that needs random ticks must implement `RandomTickBlock`.

### 5.3 Hardcoded behaviors [SRC `BB/HardcodedPropertyBlockBehavior.java` L1–189; `BB/WaterloggedBlockBehavior.java`]
| Property name | Requirement | Placement | Rotate/mirror |
|---|---|---|---|
| `axis` | `axis` type | axis of the clicked face | rotate swaps x↔z |
| `facing` | Direction enum | 6 values → opposite of nearest looking dir; 4 horizontal → opposite of horizontal look (faces the player); exactly `{up,down}` → opposite of vertical look; other subsets → unchanged | yes |
| `facing_clockwise` | Direction enum | 6 values → nearest looking dir rotated clockwise; 4 horizontal → horizontal dir clockwise | yes |
| `rotation` | `int` with `min: 0`, `max > 0` | `SegmentedAngle(max+1).fromDegrees(yaw+180)` (sign-like, e.g. 0..15) | yes |
| `waterlogged` | `boolean` | true if placed in water | bucket pickup / placing water toggles it; schedules water ticks. **Fluid rendering/physics also needs `settings.fluid_state: water` on the `waterlogged=true` variant.** |
Names like `custom_axis` get no hardcoded logic [WIKI states].

---

## 6. Built-in behaviors overview
Registered in `BB/BukkitBlockBehaviors.java` L10–64 (53 ids incl. alias `drop_exp_block`). Columns: **Req props** = properties the factory requires (missing → `missing_property`; wrong type → `property_type_mismatch`). **RT** = random ticks, **ST** = scheduled ticks, **BE** = creates block entity, **Int** = player interaction.

| type | Req props | Opt props | RT | ST | BE | Int | One-line purpose |
|---|---|---|---|---|---|---|---|
| `empty` | – | – | | | | | no-op |
| `bush_block` | – | – | | yes (delay) | | | survive only on whitelisted/blacklisted block **below** |
| `hanging_block` | – | – | | yes | | | same, block **above** |
| `falling_block` | – | – | | yes (2t) | | | gravity like sand |
| `leaves_block` | `distance` int, `persistent` bool | `waterlogged` | when !persistent && distance==max | yes | | | vanilla-style decay, logs via `minecraft:logs` tag |
| `strippable_block` | – | – | | | | axe | right-click with tool → other block |
| `sapling_block` | `stage` int | – | always | | | bone meal | stages then configured feature / structure |
| `on_liquid_block` | – | – | | yes | | | survive on water/lava surface below |
| `near_liquid_block` | – | – | | yes | | | survive if liquid at given offsets |
| `concrete_powder_block` | – | – | | | | | turns into `solid_block` when touching water |
| `vertical_crop_block` | `age` int | – | always | | | | sugar-cane growth up/down |
| `crop_block` | `age` int | – | while age<max | | | bone meal | age growth by light; survive = light only |
| `grass_block` | – | – | | | | bone meal | bone meal places a placed_feature around |
| `lamp_block` | `lit` bool | – | | yes | | | redstone lamp |
| `trapdoor_block` | `half` single, `facing`, `powered`, `open` | `waterlogged` | | | | open | vanilla trapdoor |
| `door_block` | `half` double, `facing`, `hinge`, `powered`, `open` | – | | | | open | vanilla door (2 blocks) |
| `stackable_block` | int named by `property` (default `amount`) | – | | | | place | right-click same item adds 1 |
| `sturdy_base_block` | – | – | | yes | | | survive if a face/rigid/center support in `direction` |
| `fence_gate_block` | `facing`, `in_wall`, `open`, `powered` | – | | | | open | vanilla fence gate |
| `slab_block` | `type` slab_type | `waterlogged` | | | | place | vanilla slab merge |
| `stairs_block` | `facing`, `half` single, `shape` | `waterlogged` | | | | | vanilla stairs shape |
| `pressure_plate_block` | `powered` bool | – | | yes | | step | vanilla wooden/stone plate |
| `double_high_block` | `half` double | – | | | | | 2-tall (upper/lower) |
| `change_over_time_block` | – | – | always | | | | random-tick chance → `next_block` |
| `simple_storage_block` | – | `open` bool | | yes | **yes** | open GUI | chest-like inventory |
| `toggleable_lamp_block` | `lit`; `powered` (opt. if `can_open_with_hand`) | | | | | toggle | hand/redstone toggle |
| `sofa_block` | `facing`, `shape` sofa_shape | `waterlogged` | | | | | sofa corner shapes |
| `bouncing_block` | – | – | | | | | slime-like bounce |
| `directional_attached_block` | `facing` | – | | | | | wall-attached (torch-like) |
| `liquid_flowable_block` | – | – | | | | | water/lava wash it away (drops) |
| `simple_particle_block` | – | – | | | **yes (async ticker)** | | particles every N ticks |
| `wall_torch_particle_block` | `facing` | – | | | **yes** | | particles offset by facing |
| `fence_block` | `north/east/south/west` bool | – | | | | leash | fence connections |
| `button_block` | `powered` | – | | yes | | press | vanilla button |
| `face_attached_horizontal_directional_block` | `face` anchor_type, `facing` | – | | | | | floor/wall/ceiling attach (lever/button) |
| `stem_block` | `age` int | – | always | | | bone meal | melon/pumpkin stem |
| `attached_stem_block` | `facing` | – | | | | | attached stem reverts when fruit gone |
| `chime_block` | – | – | | | | | sound on projectile hit |
| `budding_block` | – | – | always | | | | amethyst-like bud growth chain |
| `seat_block` | – | `facing` | | | **yes** | sit | sit on right click |
| `surface_spreading_block` | – | `snowy` | always | | | | grass spread/decay to base block |
| `snowy_block` | `snowy` bool | – | | | | | snowy=true when snow above |
| `hangable_block` | `hanging` bool | `waterlogged` | | | | | lantern-like hang/stand |
| `drop_experience_block` / `drop_exp_block` | – | – | | | | | XP on break |
| `multi_high_block` | int named by `property` (required key) | – | | | | | N-tall column |
| `spreading_block` | – | – | always | | | | spread into `target_block` |
| `item_frame_block` | `facing` | – | | | **yes** | put/take/rotate | item-frame-like block |
| `display_item_block` | – | `facing` | | | **yes** | put/take | shows one item |
| `drawer_block` | – | `facing` | | | **yes** | put/take | single-item mass storage |
| `tint_source_block` | – | – | | | **yes** | | stores item for renderer tint |
| `vine_crop_head_block` | `age` int | `berries` | always | yes (delay) | | bone meal | growing vine tip |
| `vine_crop_body_block` | – | `berries` | | yes | | bone meal | vine segment |
| `decay_block` | `age` int | – | if no `delay` | if `delay` | | | age up then turn into block |
| `seagrass_like_block` | – | – | | | | | placeable only in full water, keeps water |

---

## 7. Built-in behaviors: details
Every factory is read from source (`BB/<Class>.java`, Factory line given). Default values are the literal defaults in `section.getX(key, default)`. "AbstractCanSurvive" = `BB/AbstractCanSurviveBlockBehavior.java` (L1–131): `onPlace` schedules a tick in 2t; with `delay: 0` the block becomes air immediately in `updateShape` when unsupported (vanilla `updateOrDestroy` drops loot [INF]); with `delay > 0` a tick is scheduled and `tick` plays the break sound and `destroyBlock(drop=true)`. Its `*_blocks` lists accept vanilla ids (whole block), vanilla states (`minecraft:x[a=b]`), custom block ids (all states) and custom state strings; `*_block_tags` accept block tags. Both also accept a dashed alias (`bottom-blocks`).

### 7.1 `bush_block` (BushBlockBehavior, Factory L41)
| key | type | default |
|---|---|---|
| `bottom_blocks` | list | [] |
| `bottom_block_tags` | list | [] |
| `blacklist` | bool | false |
| `stackable` | bool | false |
| `max_height` | int | 0 (unlimited when stackable) |
| `delay` | int ticks | 0 |
Logic (`mayPlaceOn`): a tag hit → `!blacklist`; same block below → allowed only if `stackable` (and `max_height` 0 = unlimited, 1 = never, >1 = count limit); listed state → `!blacklist`; else → `blacklist`. **Whitelist mode with empty lists means the block cannot survive anywhere.**
```yaml
# tree.yml L174-183 (sapling)
behaviors:
  - type: liquid_flowable_block
  - type: bush_block
    bottom_blocks: [minecraft:farmland]
    bottom_block_tags: [minecraft:dirt, minecraft:sand, minecraft:supports_vegetation]
```

### 7.2 `hanging_block` (Factory L54)
Same keys but `above_blocks` / `above_block_tags`; checks the block **above**. [INF] Its `mayStackOn` starts counting from `below(abovePos)` (the block's own position), so `max_height` limits may be off by one; test it.

### 7.3 `falling_block` (Factory L125)
| key | default |
|---|---|
| `hurt_amount` | -1 (no damage) |
| `max_hurt` | -1 |
| `sounds.land` | none |
| `sounds.destroy` | none |
Ticks 2t after placement or any neighbour update. If `FallingBlock.isFree(below)` → spawns a falling block entity carrying the custom state (custom `FallingBlockEntityGenerator`). Only vertical fall, no sideways slide. [CFG `gunpowder_block.yml` L9–12, `netherite_anvil.yml` L12]

### 7.4 `leaves_block` (Factory L164)
No keys. Requires `distance` (int; its max is the decay distance) and `persistent` (bool); optional `waterlogged`. Distance 0 source = any state tagged `minecraft:logs` (vanilla or custom via `settings.tags`). Neighbour vanilla leaves pass their `distance`; custom blocks without `leaves_block` count as max. Random tick: if `!persistent && distance == max` → fires `LeavesDecayEvent`, removes block, drops loot. Placement does **not** set `persistent`; it uses the property default (`true` in the shipped template), so world generation must place `persistent=false` states. [CFG `tree.yml` L218–231 + template `__leaves__`]

### 7.5 `strippable_block` (Factory L158)
| key | type | default |
|---|---|---|
| `stripped` | block state string | **required** |
| `tools` / `tool` | ids or `#tags` | `["#minecraft:axes"]` |
| `excluded_properties` | list | [] (all current properties copied by name to the new state) |
| `sound` | sound | `minecraft:item.axe.strip` |
Right-click (not adventure mode, not shield-blocking) → `EntityChangeBlockEvent`, sets the new state, damages the tool by 1. Works while sneaking (`canUseOnBlockIfSecondaryUseActive`). [CFG `tree.yml` L58–60]

### 7.6 `sapling_block` (Factory L302)
| key | default |
|---|---|
| `feature` / `configured_feature` | required unless `structure` |
| `structure` | – (structure id; placed only if all chunks of its bounding box are loaded) |
| `bone_meal_success_chance` | 0.45 |
| `grow_speed` | 1/7 (chance per random tick) |
| `light_requirement` | 9 |
| `max_light_requirement` | 15 |
Requires int `stage`. Random tick + light (block above, max local raw brightness) → `stage+1` until max, then places the feature. Feature placement failure → rollback. **No time-based growth.** [CFG `tree.yml` L183–193; note `default_value: 0` there is not a real key]

### 7.7 `on_liquid_block` (Factory L34)
| key | default |
|---|---|
| `liquid_type` | `[water]` (`water`, `lava`) |
| `stackable` | false |
| `delay` | 0 |
Survives if the block below has water (or is vanilla `ice`) / lava fluid **and** the block itself is not in fluid. Use with item behavior `liquid_collision_block_item` (lily-pad placement). The `positions` key in shipped `reed.yml` L19 is ignored by this behavior.

### 7.8 `near_liquid_block` (Factory L45)
| key | default |
|---|---|
| `liquid_type` | `[water]` |
| `positions` | **[]** → never survives unless stackable on itself |
| `stackable` | false |
| `delay` | 0 |
`positions` are `"x,y,z"` offsets; survives if any offset has the liquid (source or flowing) with no fluid above it. [CFG `flame_cane.yml` L32–40]

### 7.9 `concrete_powder_block` (Factory L90)
`solid_block` (state string, required). Becomes that state when placed into or touching water (`BlockFormEvent`). Combine with `falling_block`. [CFG `gunpowder_block.yml`]

### 7.10 `vertical_crop_block` (Factory L97)
| key | default |
|---|---|
| `max_height` | 3 |
| `grow_speed` | 1.0 (integer part = age added per random tick; fraction = chance of +1) |
| `direction` | `up` (`down` for hanging) |
Requires int `age`. When the target position is air and the column is shorter than `max_height`, age increases. At max it places the block's **default state** in the next cell (BlockGrowEvent) and resets its own age. Pair with `bush_block {stackable: true}` so the column survives. [CFG `flame_cane.yml` L16–40]

### 7.11 `crop_block` (Factory L243)
| key | default |
|---|---|
| `grow_speed` | 0.125 |
| `light_requirement` | 0 |
| `max_light_requirement` | 15 |
| `spawn_light_requirement` | = light_requirement |
| `max_spawn_light_requirement` | = max_light_requirement |
| `is_bone_meal_target` | true |
| `bone_meal_age_bonus` | 1 (NumberProvider, e.g. `{type: uniform, min: 1, max: 2}`) |
Requires int `age`. Random tick when `age < max` and raw brightness in range → age + growth (BlockGrowEvent). **`canSurvive` only checks light** (spawn light in worldgen regions, `min-1..max` otherwise); soil must come from a `bush_block`. Ripe = `age == max` (random ticking stops). [CFG `ender_pearl_flower.yml` L19–28; `magma_plant.yml` L46–56]

### 7.12 `grass_block` (Factory L184)
`feature` / `placed_feature` (required): a **placed** feature placed in the 128-try vanilla pattern on bone meal over blocks of the same custom state. No spreading (see `surface_spreading_block`).

### 7.13 `lamp_block` (Factory L86)
Requires `lit`. Placement lit = has neighbour signal; neighbour change toggles (turning off is delayed 4t). `BlockRedstoneEvent`. [CFG `copper_coil.yml`]

### 7.14 `trapdoor_block` / `door_block` / `fence_gate_block` (Factories L249 / L369 / L282)
Common keys: `can_open_with_hand` (true), `can_open_by_wind_charge` (true), `sounds.open`, `sounds.close` (default none). Required properties in the table. Doors: 2 blocks via `placeMultiState`. Creative or wrong-tool break of the upper half suppresses lower drops. Use `double_high_block_item` for the item [CFG `tree.yml` L277]. Visuals: `default:block_state/{trapdoor,door,fence_gate}`.

### 7.15 `stackable_block` (Factory L66)
| key | default |
|---|---|
| `property` | `amount` (int property name, must exist) |
| `items` / `item` | [] ids that increment |
Right-click with a listed item (not sneaking) while below max → `canBeReplaced` true → the placement returns the clicked state with property+1. [CFG `pebble.yml` L22–25]

### 7.16 `sturdy_base_block` (Factory L105)
| key | default |
|---|---|
| `direction` | `down` (support side: down=floor, up=ceiling, a horizontal dir = wall) |
| `support_types` | `[full]` (also `rigid`, `center`; any match passes) |
| `stackable` | false |
| `max_height` | 0 (stacking needs >1) |
| `delay` | 0 |
[CFG `pebble.yml` L18–21, `amethyst_torch.yml` L53–56 (center), `table_lamp.yml` L26–30]

### 7.17 `slab_block` (L120) / `stairs_block` (L123)
No keys. Slab: `type` slab_type + optional `waterlogged`; clicking a bottom/top slab with the same item merges to `double`. Stairs: `facing`, `half` (single_block_half), `shape` (computed from neighbours) + optional `waterlogged`. Visual templates: §3.4. [CFG `tree.yml` L634, L679]

### 7.18 `pressure_plate_block` (Factory L237)
| key | default |
|---|---|
| `sensitivity` | `everything` (`all`) or `mobs` (`mob`; living entities) |
| `pressed_time` | 20 ticks |
| `sounds.on` / `sounds.off` | none |
Survives on rigid or center support below. Needs a visual without collision so `entityInside` fires (`auto_state: pressure_plate`).

### 7.19 `double_high_block` (Factory L162)
Requires `half` (double_block_half). Placement needs the cell above replaceable; places the upper half (`placeMultiState`). Breaking one half removes the other (updateShape → air or water). Upper half survives only on its own lower half. Lower half has **no ground check**; add `bush_block` with `stackable: true, max_height: 2` [INF: then the upper half's "same block below" passes and the lower half checks the soil]. Item: `double_high_block_item`.

### 7.20 `change_over_time_block` (Factory L66)
| key | default |
|---|---|
| `next_block` | required (state string, custom or vanilla) |
| `change_speed` | 0.057 (chance per random tick) |
| `excluded_properties` | [] |
Random tick → `BlockFormEvent` → next state with same-named properties copied (minus excluded). Always random-ticks. Good for simple oxidation/aging chains.

### 7.21 `simple_storage_block` (Factory L173)
| key | default |
|---|---|
| `title` | `<lang:container.chest>` (MiniMessage, player context) |
| `rows` | 1 |
| `sounds.open` / `sounds.close` | none |
| `has_signal` | true (comparator) |
| `allow_input` | true |
| `allow_output` | true |
| `data_key` | `craftengine:simple_storage` (NBT key in block-entity data) |
Optional `open` bool property is set while viewed. Block entity stores items; items drop on removal (`SimpleStorageBlockEntityController.onRemove` L184–190). Hoppers via `WorldlyContainerHolder`. [CFG `safe_block.yml` L27–35]

### 7.22 `toggleable_lamp_block` (Factory L107)
`can_open_with_hand` / `can_toggle_with_hand` (false). Requires `lit`. `powered` required unless hand-toggle is on. Right click cycles `lit`; a redstone rising edge flips `lit`. [CFG `table_lamp.yml` L23–25]

### 7.23 `sofa_block` (Factory L101)
Requires `facing`, `shape` (sofa_shape), optional `waterlogged`. Computes inner corners from neighbouring sofas. [CFG `sofa.yml`]

### 7.24 `bouncing_block` (Factory L86)
| key | default |
|---|---|
| `bounce_height` | 0.66 (**forced 0 on ≥26.2**; use `settings.bounce_restitution` there) |
| `sync_player_position` | true (marks the player hurt-dirty 1t later to resync) |
| `fall_damage_multiplier` | 0.5 |
[CFG `sofa.yml` L46–49 with `$$<26.2` guard]

### 7.25 `directional_attached_block` (Factory L106)
| key | default |
|---|---|
| `attached_blocks` / `attached_block_tags` | [] |
| `blacklist` | **true** |
Requires `facing` (4 or 6 values). Survives if the face it hangs on is sturdy (FULL) and passes the list. Placement tries the nearest looking directions. [CFG `amethyst_torch.yml` L86]

### 7.26 `liquid_flowable_block` (Factory L49)
`drop_item` (true). Lets water/lava flow into the block. Water: drop loot (unless worldgen) and replace by fluid; lava: fizz without drops. Add to all plants, torches and small decorations. [CFG many]

### 7.27 `simple_particle_block` (L35) / `wall_torch_particle_block` (L40)
| key | default |
|---|---|
| `particles` / `particle` | list of `{particle, x, y, z, count (1), offset_x/y/z (0), speed (0), + particle data keys (e.g. dust color)}`; numbers are NumberProviders |
| `tick_interval` | 10 |
Block entity with an **async** ticker (`createAsyncBlockEntityTicker`). The wall variant requires `facing` and offsets by facing. [CFG `amethyst_torch.yml` L58–69, L88–98]

### 7.28 `fence_block` (Factory L168)
`connectable_block_tag` (`minecraft:wooden_fences`), `can_leash` (false). Requires `north/east/south/west` booleans. Connects to same-tag blocks and sturdy faces. Visual template `default:block_state/fence` (entity renderers on a vanilla fence state).

### 7.29 `button_block` (Factory L220)
`ticks_to_stay_pressed` (30), `can_be_activated_by_arrows` (true), `sounds.on/off`. Requires `powered`. Usually with `face_attached_horizontal_directional_block` (`tree.yml` L471–473).

### 7.30 `face_attached_horizontal_directional_block` (Factory L120)
`attached_blocks/attached_block_tags`, `blacklist` (true). Requires `face` (anchor_type) + `facing`. Floor/wall/ceiling placement, needs a sturdy FULL face.

### 7.31 `stem_block` (L154) / `attached_stem_block` (L76)
stem: `fruit` (required, CE id or `minecraft:` block), `attached_stem` (required CE id with `facing`), `fruit_bottom_blocks/fruit_bottom_block_tags` (where the fruit may spawn), `light_requirement` (0), `max_light_requirement` (15). Requires int `age`. Random tick: age+1 to max, then places the fruit on a random horizontal side and converts to the attached stem. attached: `fruit`, `stem` (both required), requires `facing`; reverts to stem (age max) when the fruit is gone. [CFG `hami_melon.yml` L94–104, L159–167]. Bone meal bug: result clamped to hardcoded 7 (`Math.min(7, ...)`).

### 7.32 `chime_block` (Factory L38)
`sounds.chime` / `sounds.projectile_hit`: sound played when a projectile hits.

### 7.33 `budding_block` (Factory L106)
`growth_chance` (0.2), `blocks` (ordered list of ids; first is placed into air/full water, each next replaces the previous). Sets `facing` and `waterlogged` on the placed block if present.

### 7.34 `seat_block` (Factory L66)
`seats`: list of `"x,y,z [yaw [force]]"` strings or `{position, yaw, limit_player_rotation, force_player_rotation}` maps. Optional `facing` rotates seats. Right click (not sneaking) spawns a seat entity. [CFG `sofa.yml` L50–52]

### 7.35 `surface_spreading_block` (Factory L117)
| key | default |
|---|---|
| `base_block` | `minecraft:dirt` (state string) |
| `light_requirement` / `required_light` | 0 |
| `max_light_requirement` | 15 |
Random tick: if it cannot stay grass (opaque block above, or full fluid above; one snow layer is OK) → turns into `base_block`. Else, if light above is in range, tries 4 random positions (±1, −3..+1, ±1); a position whose block equals `base_block`'s block (exact state for CE, see §1) with space above becomes this block's **default state** (`snowy` set from snow above if the property exists). Pair with `snowy_block`.

### 7.36 `snowy_block` (Factory L45)
Requires `snowy`. Sets `snowy` from whether the block above has the `minecraft:snow` tag (placement + update from above).

### 7.37 `hangable_block` (Factory L82)
Requires `hanging`, optional `waterlogged`. Placement picks hanging (look up) or standing. Needs center support above or below. Not pathfindable. Lantern-like.

### 7.38 `drop_experience_block` / `drop_exp_block` (Factory L78)
`amount` / `count` (NumberProvider, default 0, e.g. `3~7`), `condition(s)` (condition list; context has position and item_in_hand). Drops XP on break only with the correct tool (unless the vanilla flag already drops). [CFG `topaz_ore.yml` L28–33]

### 7.39 `multi_high_block` (Factory L246)
`property` (**required key**, name of an int property, e.g. `height` 0..N-1). Places `min+1..max` above with the matching value. Every segment checks its neighbours; breaking any segment collapses the column. Item: `multi_high_block_item`.

### 7.40 `spreading_block` (Factory L45)
`target_block` (required state string). Random tick: one random nearby block equal to the target (exact block) becomes this block's default state. No light or survival checks.

### 7.41 `item_frame_block` (Factory L195)
`position` (0,0,0), `glow` (false), `invisible` (false), `render_map_item` (true), `sounds.put/take/rotate`, `data_key` (`craftengine:item_frame`). Requires `facing`. Item frame semantics with comparator output, dynamic renderer, drops the item on removal.

### 7.42 `display_item_block` (Factory L145)
`position` (0.5,0.5,0.5), `sounds.put/take`, `has_signal` (true), `tint_source` (false), `data_key` (`craftengine:display_item`). Optional `facing`. Right click with an item while empty → stores 1; empty hand while filled → item goes **into the player's hand**; else `TRY_EMPTY_HAND`. Renders the item with a dynamic element and drops it on removal. Closest built-in to "right-click to pick up".

### 7.43 `drawer_block` (Factory L274)
`item_position`, `text_position`, `item_scale`, `text_scale` (all four default to `CENTER_VECTOR3` = 0.5,0.5,0.5 in code), `max_stacks` (32), `allow_input`/`allow_output` (true), `has_signal` (true), `compatible_mode` (false), `sounds.put/take`, `data_key` (`craftengine:drawer`). Optional `facing`. Single item type, right-click put, left-click (`attack`) take. [CFG `drawer_block.yml` L18–27]

### 7.44 `tint_source_block` (Factory L47)
`drop_item` (true), `data_key` (`craftengine:tint_source`). Copies the placed item (1x) into the block entity; renderers with `tint_source:` copy its components (dye colour). [CFG `sofa.yml` L53–54]

### 7.45 `vine_crop_head_block` (Factory L277)
| key | default |
|---|---|
| `body` | required (CE block id of the body segment) |
| `max_height` | **-1 → never grows** (`currentHeight < -1` is false); set it |
| `grow_speed` | 1.0 |
| `direction` | `up` |
| `delay` | 1 (survival recheck) |
| `bone_meal.behavior` | `berries` (sets `berries=true`) or `grow` |
| `bone_meal.grow_blocks` | 1 (parsed, but `getBlocksToGrowWhenBoneMealed` ignores it and always returns 1, L245–250) |
Requires int `age`, optional `berries`. Survives on a sturdy face, the body, or itself. When it grows, the old head turns into a body (`berries` copied) and the new head is the default state.

### 7.46 `vine_crop_body_block` (Factory L219)
`head` (required), `direction` (`up`), `delay` (1), `bone_meal.*` as above, optional `berries`. Turns back into the head when the segment beyond it is not the same block. No harvesting logic: do berries with events.

### 7.47 `decay_block` (Factory L123)
| key | default |
|---|---|
| `decay_into` | `air` (state string) |
| `delay` | none → random-tick mode; NumberProvider (`100~200`) > 0 → scheduled-tick mode |
| `chance` | 1 (NumberProvider) |
| `required_light` | 0 (max raw brightness at or adjacent to the block must be ≥) |
Requires int `age`. Each tick/random tick passing light and chance → age+1; at max → `decay_into`. [WIKI decay_block example `default:ice` → water]

### 7.48 `seagrass_like_block` (Factory L25)
No keys. Placement only in full water. Refuses other liquids, keeps scheduling water ticks. Combine with `settings.fluid_state: water` [INF] and a kelp/seagrass-like visual.

### 7.49 Not behaviors but relevant
- `HardcodedPropertyBlockBehavior`, `WaterloggedBlockBehavior` (§5.3) are added automatically.
- `EmptyBlockBehavior` is what unbound `custom_N` blocks get after unload.
- Item-side counterparts: `block_item`, `wall_block_item`, `ground_block_item`, `ceiling_block_item`, `double_high_block_item`, `multi_high_block_item`, `liquid_collision_block_item` (`BK/item/behavior/`).

---

## 8. TFC families
Legend: **Built-in** = config only; **Hybrid** = built-in plus events or our listener; **Custom** = our own `BlockBehavior` registered as `tfc:*` (see the main report §8.4 for the API: `BlockBehaviors.register`, implement `RandomTickBlock`/`EntityBlock`, controllers + tickers).

| TFC family | Best CE fit | Gap / custom work |
|---|---|---|
| Raw rock (21) with collapse | single-state block, `default:settings/solid_1x1x1`, hardness/tags; `auto_state: solid` or explicit freed full blocks | **Custom.** CE has no collapse logic. A TFC collapse (mining raw rock triggers an unsupported-area check, support beams radius) needs a `tfc:collapsible` behavior or a break listener that spawns falling blocks. `falling_block` only does vertical sand-style fall on neighbour update. |
| Hardened/smooth/bricks/cobble etc. | single-state + templates; stairs/slabs/walls | Stairs/slabs: built-in `stairs_block`/`slab_block`, but **real models need freed vanilla stair/slab sets** (only 4 stairs / 5 slabs freed by default, §3.4). Add `block_state_mappings` mapping unused vanilla stairs/slabs onto one look-alike (e.g. all `*_stairs[...]` → `stone_stairs[...]`) to free sets, or render via `transparent` + `entity_renderer`. **No wall behavior exists** (walls are multipart, which CE strips): custom `tfc:wall` + entity renderers, or skip. Each stairs block = 80 `custom_N`. |
| Gravity sand/gravel/dirt (landslide) | `falling_block` (+ `concrete_powder_block` not needed) | Vertical fall built-in. TFC landslides also slide sideways off edges → **Custom** (or Hybrid: `falling_block` + our listener deciding sideways moves). |
| Ores (graded × rock) | single-state blocks, `default:settings/ore` (pass `break_power`!), `drop_experience_block` optional, `loot` per grade | Built-in. Budget: ~800+ single-state ore blocks exceed the `solid` pool (≈1.3–1.5k shared with everything else). Plan extra `block_state_mappings` or explicit unused vanilla full-block states. Small/surface ore pieces: `sturdy_base_block` + `liquid_flowable_block` on a tripwire/button visual. |
| Soil (dirt/clay per soil type) | single-state | Built-in. |
| Grass spreading | `surface_spreading_block {base_block: tfc:dirt/<soil>}` + `snowy_block` (needs `snowy` property) | Mostly built-in. Each grass block reverts to its own base block. TFC's connected-grass sides and spreading onto clay/peat variants → Custom if needed. Bone meal → `grass_block {feature}` optional. |
| Farmland | – | **Custom.** No farmland behavior. Moisture/nutrients/hydration need a block entity (`EntityBlock` controller). Visual: vanilla `farmland[moisture=N]` states via mappings (hitbox 15/16). |
| Crops with stages | `crop_block` + `bush_block` (soil) + `liquid_flowable_block`; int `age`; `loot: default:loot_table/crop|seed_crop` | `crop_block` grows by random tick + light only. TFC growth is calendar/temperature/hydration-driven with block entity data, and **dead** and **wild** crops are separate blocks → **Custom** `tfc:crop` (EntityBlock + lazy calendar catch-up). Reuse CE visuals (appearances per age) and loot. Double-tall crops → custom (`double_high_block` has no growth). |
| Berry bushes | appearances per lifecycle via `string`/`int` properties; harvest via `events: right_click` + `update_block_property` + `drop_loot` | Lifecycle (dormant/healthy/flowering/fruiting by season) + spreading bushes → **Custom**. `change_over_time_block` is too simple (random chance only). |
| Fruit trees (branches, leaves with seasons) | `leaves_block`-like visuals | **Custom** (growth structure, seasonal leaves, harvest). |
| Saplings (timed growth) | `sapling_block` grows by random-tick chance; `feature`/`structure` | Timed growth (days) → **Custom** (block entity storing the planting tick); tree generation is our own Java. Reuse `bush_block` for soil checks. |
| Leaves decay | `leaves_block` + template `default:block_state/leaves`; our logs must carry tag `minecraft:logs` | Built-in for decay. Worldgen must place `persistent=false`. Seasonal colours = extra property → more states. Fallen leaves → custom. |
| Logs (axis, strippable) | `default:block_state/pillar` (axis hardcoded) + `strippable_block {stripped: tfc:wood/stripped_log/<wood>}` + `default:settings/wood` | Built-in. Tree felling (break whole tree) → custom listener. `natural` flag (TFC) = extra boolean property (doubles states). |
| Plants (single) | `bush_block` (soil tags) + `liquid_flowable_block`; `replaceable: true` for grass-like; visual `sapling`/`sugar_cane`/`tripwire` pools or `transparent` + renderer | Built-in. 214 plants × stages may exhaust pools: prefer shared `auto_state {type: sugar_cane, id: transparent}` + item_display renderers for cross models (performance cost per block, §10). |
| Tall plants | `double_high_block` + `bush_block {stackable: true, max_height: 2}` + `double_high_block_item` | Built-in [INF]. Water plants: `seagrass_like_block` / `on_liquid_block` (+ `liquid_collision_block_item`). Kelp-like columns: `vine_crop_head/body_block` (set `max_height`!) or `vertical_crop_block`. |
| Groundcover: loose rocks, sticks/twigs, shells, seashells | `liquid_flowable_block` + `sturdy_base_block {support_types: [full]}`; tiny visual (`auto_state: lower_tripwire` like `pebble.yml`, or button states); `stackable_block` for piles | Pickup on right-click: **Hybrid** with `events: right_click` → `break_block` (simulated mining; drops via loot) + `swing_hand` + `cancel_event`, or **Custom** `tfc:pickupable` (`useWithoutItem` gives the item into the hand, like `display_item_block` L102–110). Tripwire visuals keep client-side string quirks; no collision. |
| Snow piles / ice piles | – | **Custom** (snow pile remembers the covered block → block entity). Ice melting: `decay_block` (`required_light`, `decay_into: water`) is close, but TFC melts by temperature → custom. |
| Devices with inventories (firepit, quern, barrel, crucible, anvil, pit kiln) | `simple_storage_block` only gives a chest GUI; `display_item_block`/`drawer_block` show items | **Custom** `EntityBlock` behaviors + `BlockEntityController` (save/load NBT, tickers, sleeping tickers) + our own GUIs. Use `entity_renderer` / dynamic elements for displayed contents. `luminance` per `lit` variant for firepits. `simple_particle_block` (async) for smoke can be combined. |
| Torches that burn out | standing: `sturdy_base_block {support_types: [center]}` + `liquid_flowable_block` + `simple_particle_block`; wall: `directional_attached_block` + `wall_torch_particle_block` (pattern `amethyst_torch.yml`); burn-out: `decay_block {delay: ..., decay_into: tfc:dead_torch}` | Hybrid. `decay_block` with `delay` uses scheduled ticks: no catch-up for unloaded time (persistence of `custom_N` scheduled ticks UNVERIFIED, §10). TFC's calendar-based burn time → custom (store placement tick). |
| Candles (1–4, lit) | `stackable_block {property: candles, items: [...]}` + `luminance` per variant + lighting via `events` (`match_item flint_and_steel` → `update_block_property lit`) | Hybrid. Visual: explicit vanilla `*_candle[...]` states (16 per colour) if mapped, else renderers. |
| Rope / climbable | visual from `cave_vines`/`weeping_vines`/`twisting_vines` pools (client treats them as climbable; mappings.yml comment "we can repurpose those extra states to make custom blocks like ropes"); `hanging_block {stackable: true}` | Built-in visuals + survival. Growth/placement down → custom if needed. |
| Support beams (vertical/horizontal) | vertical: `sturdy_base_block` or none; visuals via fence-like entity renderers (`default:block_state/fence` pattern) | Horizontal beams needing two supports, connection shapes and collapse prevention → **Custom** (`tfc:support`). |
| Chests/barrels (simple storage only) | `simple_storage_block {rows, title}` + `open` property | Built-in for plain storage. TFC barrels (fluids, sealing, recipes) → Custom. |
| Doors, trapdoors, fence gates, fences, buttons, pressure plates, signs | built-ins + templates (`tree.yml` config_factory pattern) | Built-in. Each door type needs a freed vanilla door set (`replaced_door_type` in `tree.yml` L15–18; ~22 types freed by default). |

---

## 9. Pitfalls
1. **Variant appearance resolution: last match wins in code, first in wiki.** Use non-overlapping variant keys. [SRC L777–789 vs WIKI states]
2. **Typos in variant keys become wildcards** (unknown property or invalid value dropped silently). Validate generated keys against declared properties in our datagen. [SRC BlockNbtParser L30–35]
3. **Unknown settings keys and unknown behavior keys are ignored silently** (only unknown `type` errors). Shipped configs contain dead keys (`default_value` in `tree.yml` sapling; `positions` in `reed.yml`). [SRC BlockSettings L75–90]
4. **`is_randomly_ticking` is ignored when the block has ≥2 behaviors** (hardcoded `facing`/`axis`/`waterlogged` count). Random ticks come only from `RandomTickBlock` behaviors. [SRC BukkitBlockManager L354–358]
5. **No sounds by default**: always apply a `default:sound/*` template. [SRC BlockSettings `sounds = BlockSounds.EMPTY`]
6. **`is_redstone_conductor` defaults to false** even for full cubes. [SRC applyPlatformSettings L299]
7. **`fluid_state: water` must be set per `waterlogged=true` variant**. The hardcoded waterlogged behavior only flips the property and schedules ticks. [CFG templates]
8. **`default:settings/ore` needs `arguments: {break_power: N}`** (no default). [CFG block_settings.yml L77]
9. **`crop_block` doesn't check soil**: add `bush_block`. [SRC CropBlockBehavior.canSurvive]
10. **`bush_block` in whitelist mode with no lists never survives**, and `near_liquid_block` without `positions` never survives. [SRC]
11. **`vine_crop_head_block.max_height` defaults to -1 = never grows**; `bone_meal.grow_blocks` is ignored (always 1). [SRC L101, L245–250]
12. **`stem_block` bone meal clamps age to 7** regardless of the property max. [SRC performBonemeal]
13. **`bouncing_block.bounce_height` is forced to 0 on MC ≥ 26.2**; use `settings.bounce_restitution`. [SRC Factory L96]
14. **Each CE state = one `custom_N`**; stairs = 80, leaves template = 2×2×7 = 28. Estimate the total and raise `block.serverside-blocks` (restart required). [SRC]
15. **Spreading targets compare exact NMS blocks**, which for CE blocks means one exact state. Give soil blocks a single state, or list the exact state string. [INF from per-state NMS blocks]
16. **Leaves**: our logs must have tag `minecraft:logs`; worldgen must place `persistent=false`. Default template property default is `persistent=true`. [SRC/CFG]
17. **Model texture slots are guessed from file names** (`_top`, `_side`, `end`, …) before order. [SRC TextureSlotAssigner]
18. **Freed stairs/slabs/doors are scarce**; multipart blocks (walls, fences, panes) cannot get explicit models (multipart stripped). [CFG mappings.yml; main report §5.3]
19. **Hardcoded `facing` with 4 values places the block facing the player** (opposite of look). Use `facing_clockwise` for "rotated" semantics. [SRC]
20. **`double_block_half` default value is `upper`** (enum order) and `slab_type` defaults to `top`; set `default:` explicitly. [SRC enums]
21. **Events run before behaviors**; `cancel_event` or `update_interaction_tick` in a `right_click` event suppresses the behavior's interaction (e.g. strippable, storage). [SRC ItemEventListener]
22. **Drops require the correct tool when `correct_tools`/`required_break_power` is set**, and wrong-tool mining is slowed by `incorrect_tool_dig_speed` (0.3). [SRC]
23. **`hanging_block` stack counting** may be off by one (cursor starts at self). [SRC L39–55, INF]
24. **Client-predicted physics** (friction, speed/jump factor, climbability, bounce) follow the visual block, not the settings ("unstable" in the wiki).

---

## 10. UNVERIFIED / open questions
1. **Scheduled-tick persistence** for `custom_N` blocks across chunk unload/reload (`decay_block {delay}`, falling, can-survive delays). On unload CE restores vanilla states before saving; whether the vanilla `block_ticks` entries for `craftengine:custom_N` survive and fire after CE re-injects on load is untested.
2. Wiki vs source disagreement on variant appearance precedence (§2.5). Behavior is derived from the loop at `AbstractBlockManager` L777–789; not runtime-tested.
3. `double_high_block` + `bush_block {stackable: true, max_height: 2}` compatibility (§7.19) is inferred from `mayPlaceOn`/`mayStackOn`; test placement and breaking.
4. Whether `liquid_flowable_block`'s water replacement drops loot when the breaking context has no entity (it calls `dropResources`, which bypasses the `isCorrectTool` entity check).
5. `display_item_block` as a pickup mechanic: put/take semantics confirmed in source, but rendering cost per block (dynamic element per tracked player) not measured.
6. `settings.name`: no consumer found by grep; may be dead.
7. Exact behavior when `fluid_state: water` is set on a state whose visual is not waterloggable (client shows no water; server has water) — expect desync.
8. Performance of thousands of `simple_particle_block` async tickers or renderer-based plants with many players (not measured).
9. Whether adding many `block_state_mappings` (freeing vanilla stairs/slabs/full blocks) has client-side side effects beyond appearance (sounds are replayed server-side; tags remain those of the real vanilla block).
10. Wiki pages for individual behaviors other than `bush_block` and `decay_block` were not fetched; defaults here come from the factories at this commit.
