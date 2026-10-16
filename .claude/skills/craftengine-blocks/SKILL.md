---
name: craftengine-blocks
description: Writing CraftEngine block definitions for this TFC plugin. Block entry structure (state vs states, every property type, appearances, variants and their exact matching rules, id pinning, inline blocks in items, loot, events), every block setting and its real default, how multiple behaviours combine (random ticks, placement, canSurvive), all 53 built-in block behaviours with required properties and gotchas, and which TFC block families are built-in vs need our own Java behaviour. Use this whenever generating or editing CE block YAML, choosing a built-in behaviour, debugging a block that won't place/survive/tick/drop/make sounds, or deciding whether a TFC block needs a custom tfp: behaviour, even if the user just says "add the crop blocks".
---

# CraftEngine blocks

Source: `references/research-report.md` (CE @ `b3079c5`, read from source; most claims aren't runtime-tested). §-numbers refer to it. This skill covers the **block definition and behaviours**. Appearance/pool choice: `craftengine-visuals-pack`. Java behaviours and block entities: `craftengine-java-api`. Generating the YAML at scale: `craftengine-config-generation`.

## The model in four lines
- **Every property combination is its own server block** (`craftengine:custom_N`). A 4-facing × 2-half × 5-shape × 2-waterlogged stairs block uses **80** slots. Sum the states in datagen, then size `block.serverside-blocks` (restart required).
- Each state gets a visual vanilla state (its appearance), which provides hitbox, client prediction and skylight.
- Behaviours are Java objects. Hardcoded ones are added automatically for the property names `axis`, `facing`, `facing_clockwise`, `rotation` and `waterlogged`.
- Settings (friction, sounds, resistance...) are applied per state.

## Entry structure (§2)
```yaml
blocks:
  tfp:crop/barley:
    settings: { template: tfp:settings/crop }          # applied to all states, then variant overrides
    behaviors: [ { type: tfp:crop }, { type: bush_block, bottom_block_tags: ["#tfp:farmland"] } ]
    loot: { template: tfp:loot_table/crop_by_stage, arguments: { crop_name: barley } }
    states:
      properties:
        age: { type: int, range: 0~7, default: 0 }
      appearances:
        stage_0: { auto_state: sapling, model: tfp:block/crop/barley_0 }
        # ...
      variants:
        age=0: { appearance: stage_0 }
        # ...
```
- Property types: `boolean`, `int` (`range: a~b` or `min`/`max`), `string` (`values`), `axis`, `horizontal_direction`/`4-direction`, `direction`/`6-direction`, `single_block_half`, `double_block_half`, `hinge`, `stairs_shape`, `slab_type`, `sofa_shape`, `anchor_type`, `bed_part`. **Always set `default:` explicitly**: the enum defaults are surprising (`double_block_half` → `upper`, `slab_type` → `top`).
- **Variant matching (verified in source, differs from the wiki):** unlisted properties are wildcards, and when several variants match a state, **the last matching appearance wins** in YAML order. **A typo (`ag=3`, `age=9`) silently becomes a match-all wildcard.** So datagen must emit **non-overlapping, fully specified** variant keys and validate every key against the declared properties.
- The first appearance in YAML order is the fallback for unbound states.
- An appearance without a model keeps its vanilla state's look.
- `states.id: N` pins contiguous internal ids. Only do this if an external datapack needs `craftengine:custom_N`.
- Inline blocks inside `block_item` take the item's id. Set `settings.item` when the block's item id differs (seeds, torches).

## Settings that bite (§3, §9)
- **Blocks are silent by default.** Always apply a sound template.
- `is_redstone_conductor` defaults to **false**, even for full cubes.
- `waterlogged=true` variants need `settings: { fluid_state: water }`. The hardcoded behaviour only flips the property.
- **Unknown setting and behaviour keys are silently ignored.** Only an unknown `type` errors. Validate keys in datagen against the lists in report §3.1 / §7.
- Drops need the correct tool when `correct_tools`/`required_break_power` is set. Wrong-tool mining is slowed by `incorrect_tool_dig_speed` (0.3).
- Client-predicted friction, speed, jump and bounce follow the **visual** block, not these settings.

## Behaviour composition (§5)
- Build order: hardcoded (`axis`/`facing`/... in property order) → YAML list → `waterlogged`.
- `canSurvive` = AND of all behaviours. `updateStateForPlacement` is chained (a `null` aborts placement). `useOnBlock`: the first non-PASS result wins. Ticks and neighbour updates call every behaviour.
- **Random ticks:** once a block has **two or more behaviours, including the hardcoded ones**, `is_randomly_ticking` is ignored. Random ticks then come only from behaviours that implement `RandomTickBlock`. Our Java behaviours that need random ticks must implement it. (We prefer lazy calendar evaluation over random ticks anyway.)
- **Events run before behaviours.** `cancel_event` in a `right_click` event suppresses the behaviour's interaction.
- Find our behaviour with `state.behavior().getFirst(TfpCropBehavior.class)`. Never cast.

## Built-in behaviours: quick picks (full table in §6, details in §7)
| Need | Built-in | Must-know |
|---|---|---|
| Survive only on certain blocks below/above | `bush_block` / `hanging_block` | Whitelist mode with empty lists **never survives** |
| Needs support on a face | `sturdy_base_block` (`direction`, `support_types`) | |
| Washed away by water | `liquid_flowable_block` | |
| Gravity (vertical only) | `falling_block` | TFC landslide sideways sliding needs our code |
| Log axis + stripping | pillar template + `strippable_block` | Logs must carry the `minecraft:logs` tag for leaf decay |
| Leaf decay | `leaves_block` (`distance`, `persistent`) | Worldgen must place `persistent=false` |
| Grass spreading | `surface_spreading_block` + `snowy_block` | Spread targets match **one exact CE state**, so keep soils single-state |
| Light-based crop growth | `crop_block` | **Doesn't check soil**: add `bush_block`. TFC crops need our own `tfp:crop` anyway |
| Vine/kelp column | `vine_crop_head_block` | `max_height` defaults to −1 = never grows |
| Stairs/slabs | `stairs_block`/`slab_block` | Real models need freed vanilla stair/slab sets (only 4/5 by default) |
| Walls | **none** | Walls are multipart, so `tfp:wall` + renderers, or skip |
| Doors/trapdoors/gates | built-ins + templates | Each type consumes a freed vanilla door/trapdoor/gate set (~22 available) |
| Piles by right-clicking the same item | `stackable_block` | |
| Show/hold one item | `display_item_block`, `drawer_block` | Dynamic renderer per viewer |
| Plain chest inventory | `simple_storage_block` | TFC barrels/devices are custom |
| Age then turn into another block | `decay_block` (`delay` or random) | Scheduled-tick persistence across unload is **unverified**, so no calendar catch-up |
| Particles | `simple_particle_block` (async ticker) | Only for action-related effects (our particle policy). Avoid ambient use. |
| Sit | `seat_block` | |
| XP on break | `drop_experience_block` | |

## TFC family → built-in vs custom (§8)
- **Built-in / config only:** soils, ores (with `tfp:settings/ore`; `break_power` is required in CE's ore template), logs, leaves decay, planks, simple plants, tall plants (`double_high_block` + `bush_block`), doors/trapdoors/gates/buttons/pressure plates, plain storage, candles (hybrid: `stackable_block` + events).
- **Hybrid** (built-in + events or our listener): groundcover pickup (`right_click` → our `tfp:pickupable`, or `break_block` + `cancel_event`), torches (visual + support built-ins; burn-out by calendar is ours), gravity soils (vertical built-in, sideways ours).
- **Custom `tfp:` behaviours needed:** collapse (raw rock), landslide sideways, farmland (hydration/nutrients, block entity), crops (calendar/climate growth, dead/wild variants), saplings (timed growth, our tree generation), berry bushes and fruit trees (seasonal lifecycle), snow/ice piles (remember the covered block), all devices (firepit, quern, barrel, crucible, anvil, pit kiln, charcoal forge...), support beams, walls, fallen leaves, tree felling.

## Never do
- Overlapping or partial variant keys. Unvalidated property values in keys.
- Relying on `is_randomly_ticking` for a multi-behaviour block.
- Forgetting sounds, `fluid_state` for waterlogged states, or `bush_block` with `crop_block`.
- `enable: false` (aborts the whole parser; see `craftengine-config-generation`).
