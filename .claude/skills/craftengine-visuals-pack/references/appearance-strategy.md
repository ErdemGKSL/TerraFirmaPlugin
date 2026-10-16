# Appearance and hitbox strategy for TFC blocks

The principle: **the visual vanilla state decides the hitbox, outline, client movement prediction and sound type**. Pick it per TFC shape. Each TFC block family gets one row here. When you add a new family, add a row so later ports stay consistent.

## Contents
1. Budget sources
2. TFC family → visual base table
3. Freeing vanilla states (`block_state_mappings`)
4. Example: graded ores via config_factory
5. Example: small ore piece on a button state
6. Example: device with entity_renderer
7. Allocation report (datagen)

## 1. Budget sources

Verified counts (default `mappings.yml`, fresh install; `research-report.md` §0, §10):

| Source | 1.21.4–1.21.11 | 26.1+ | Shape | How |
|---|---|---|---|---|
| `auto_state: solid` (note_block 1100/1300 + mushroom 189, **same objects**, not additive) | 1289 | 1489 | full cube | auto |
| Explicit verified full cubes: stone family 44, ores 24 states (redstone ores ×2 `lit`), wool 16, concrete 16, concrete powder 16, terracotta 17, glazed terracotta 64 | **197** | 197 | full cube, own SoundType | `state:` + model, plus mappings to hide real ones |
| More explicit full cubes (vanilla planks, logs/wood × axis, bricks, quartz, prismarine, copper blocks, coral blocks, mud bricks, ...) | not yet audited | | full cube | Datagen keeps a **curated, audited list**. Check each one for client prediction and render layer first |
| Leaves pools (dry + waterlogged) | 117 + 117 (tintable 65 + 65) | same | full, cutout | auto |
| Tripwire (lower + higher) | 63 + 63 | same | no collision | auto |
| Plant pools: cave_vines 48, kelp 25, weeping 25, twisting 25, sugar_cane 15, cactus 15, pressure_plate 28, sapling 8; chorus **0** | | | plant shapes | auto |
| Buttons (explicit) | 336 states, but **pair `powered=false/true` with the same model** → 168 looks | same | tiny, no collision | explicit |
| Carpets 17 (not pale_moss), wood/stone pressure plates 28 | | | 1/16, flat | explicit |
| Vanilla crop ages (wheat, carrots, potatoes, beetroots, stems, nether wart, torchflower, pitcher, cocoa; **not** sweet_berry_bush) | ~80 | same | crop outlines, no collision | explicit; map the vanilla crops away |
| Freed states in no auto group (doors, trapdoors, gates, copper slabs/stairs/grates/bars/chains/lanterns, anvils...) | 1900 | 2164 (26.3: 2242) | per block | explicit full state string |
| `transparent` + `entity_renderer` | 1 state for unlimited blocks | | of the chosen visual | `auto_state: {type: sugar_cane, id: transparent}`, or `barrier` for full collision |

**Budget warning:** TFC's full cubes are ~1125 graded ores + ~210 rock variants + soils/sand + wood. That exceeds solid (1289) + the verified explicit cubes (197) on 1.21.x. The datagen allocator must:
1. reuse identical models (hardened = raw);
2. audit additional vanilla full-cube blocks;
3. report the shortfall per MC version.

Only if it still doesn't fit, raise it with the user (e.g. merge ore grades visually on 1.21.x only). Never decide silently.

The installed `mappings.yml` is frozen at first extraction, so pool sizes vary per server. Read `AutoStateGroup` candidate counts at runtime after `CraftEngineReloadEvent` and warn on shortfall.

## 2. TFC family → visual base

| TFC family | Visual base | Why / notes |
|---|---|---|
| Raw/hardened rock, cobble, bricks, smooth, chiseled, gravel (21 rocks) | `solid` pool or explicit unused vanilla full cubes | Terrain, so it needs real block models. Hardened shares raw's model (one visual slot if the model is identical). Gravel: pick a falling-looking base only for sounds; falling is server logic. |
| Ores: poor/normal/rich × metal ore × rock; non-graded ores × rock | `solid` pool + explicit vanilla full cubes | Separate texture per grade (decision). Parent model = TFC `ore.json` (base + overlay). |
| Soils: dirt, grass, clay, clay grass, rooted dirt, mud, peat, farmland, path | full cubes as above; farmland/path: explicit `farmland` / `dirt_path` states | Grass top tint: decide per season-tint plan. Vanilla grass block tint is biome-driven. |
| Sand (colours), sandstone variants | full cubes | |
| Logs/wood/stripped (20 woods × 3 axes) | explicit vanilla log/wood states (each axis is its own state) or `solid` | Use the `default:block_state/pillar` template. |
| Planks | full cubes | |
| Leaves (20 woods) | `tintable_leaves` / `leaves` pools | Seasonal colour: tintable leaves follow biome foliage colour, which matches our biome-remap plan. |
| Stairs/slabs/walls (rock, wood) | `default:block_state/slab`, `stairs` templates (copper/petrified-oak states freed by mappings) or real base + `entity_renderer` | Walls are multipart: base + renderer. |
| Fences, fence gates | `default:block_state/fence` (renderer), `fence_gate` template | Fence is multipart. |
| Doors, trapdoors | `default:block_state/door`, `trapdoor` templates (powered≡unpowered freed) | |
| Small ore pieces, loose rocks, twigs, shells, groundcover | **button** states (explicit) or `lower_tripwire` | Tiny outline, no collision. |
| Fallen leaves, thin layers | carpet states (explicit) | 1/16 high. |
| Snow/ice piles on plants | snow layer states, or plant visual + `entity_renderer` | |
| Crops (29 × stages), wild/dead crops | `sapling`, `sugar_cane`, `tripwire` pools, or explicit unused crop states (vanilla wheat/carrots/potatoes/beetroots ages are unused in TFC) | Vanilla crops don't exist in a TFC world, so their age states are free. |
| Tall plants, flowers, grasses (214 plants) | tripwire pools, sapling pool, explicit unused flower states, `transparent` + renderer for the rest | Many plants: budget carefully. Prefer real models. |
| Ropes, climbing plants | ladder / vine states | Climbable client-side. Vine-based visuals are always climbable. |
| Support beams | fence-like base + renderer | |
| Devices (firepit, quern, anvil, crucible, barrel, pot, forge, bloomery...) | barrier/bed/anvil states + `entity_renderer` | Low counts per chunk, dynamic contents. |
| Ingot/sheet piles, placed items, tool racks, shelves | slab/pressure-plate-height base + dynamic renderer | |
| Metal blocks, lamps, chains, bars, grates | explicit unused vanilla states of matching shape (vanilla chains/bars/lanterns), or renderer | |

Watch out for client prediction: vines are always climbable, note blocks/tripwires have interaction quirks (CE compensates), and slime/honey/cobweb/ice change movement client-side. Use them only when you want that behaviour.

## 3. Freeing vanilla states

If a repurposed vanilla block could still really exist (other worlds, creative inventory, commands), map it away so it doesn't render as a TFC block:

```yaml
# configuration/mappings/tfp_vanilla_freed.yml
block_state_mappings:
  minecraft:diorite: minecraft:stone            # real diorite is sent to clients as stone
  minecraft:granite: minecraft:stone
```

Mapped-away states also become `auto_state` candidates **only** if they belong to a CE `AutoStateGroup`. Otherwise reference them with explicit `state:`. Conflicting mappings are load errors.

## 4. Example: graded ores (datagen literal entry + our template)

We don't use `config_factory` for shipped content (see `craftengine-config-generation`). Datagen writes one small entry per id, and a `tfp:` template carries the shared structure.

```yaml
# configuration/ore/hematite.yml (generated): one entry per (grade, rock)
items:
  tfp:ore/rich_hematite/granite:
    template: tfp:item/ore_block
    arguments:
      ore_id: rich_hematite
      ore_rock: granite
      ore_break_power: 1
      ore_visual: { auto_state: solid }        # or { state: "minecraft:diorite" }, chosen by the allocator
```

```yaml
# configuration/templates/ore.yml (shipped once; our template)
templates:
  tfp:item/ore_block:
    data:
      item_name: <!i><lang:block.tfp.ore.${ore_id}.${ore_rock}>
    model: tfp:block/ore/${ore_id}/${ore_rock}
    behavior:
      type: block_item
      block:
        settings:
          template: tfp:settings/ore
          arguments: { ore_break_power: "${ore_break_power}" }
        behavior:
          type: tfp:ore_block                  # our behaviour: collapse trigger, prospecting data
        state:
          template: tfp:block_state/ore_overlay
          arguments:
            ore_state_visual: ${ore_visual}
            ore_overlay_model: tfp:block/ore/${ore_id}/${ore_rock}
            ore_rock_texture: tfp:block/rock/raw/${ore_rock}
            ore_overlay_texture: tfp:block/ore/${ore_id}
```

`tfp:block_state/ore_overlay` merges the visual (`auto_state` or explicit `state:`) with a model whose `generation.parent` is `tfp:block/ore` (copied from TFC `assets/tfc/models/block/ore.json`, base + overlay layers). This is a design sketch; verify the template merge behaviour with `debug: true` on one entry before generating thousands.

Datagen decides between `auto_state` and an explicit `state:` itself, from the allocation plan (§7), so the result is deterministic and doesn't depend on CE's allocation order.

## 5. Example: small ore piece on a button state

```yaml
blocks:
  tfp:ore/small_native_copper:
    behavior:
      type: tfp:small_ore                 # pick-up behaviour, drops the item
    state:
      state: minecraft:oak_button[face=floor,facing=north,powered=false]   # tiny hitbox, no collision
      model:
        path: tfp:block/groundcover/small_native_copper
        generation:
          parent: tfp:block/groundcover/small_ore   # TFC model shape
          textures: { all: tfp:block/ore/small_native_copper }
```

The vanilla oak button state is never seen in a TFC world. Free it with a `block_state_mappings` entry if wooden buttons could exist elsewhere. For rotations, use the other `facing` states.

## 6. Example: device with entity_renderer

```yaml
blocks:
  tfp:device/quern:
    behavior: { type: tfp:quern }          # our behaviour + BlockEntityController (handstone state, recipe)
    state:
      state: minecraft:barrier             # full invisible hitbox; restored as glass on unload if CE is removed
      entity_renderer:
        - item: tfp:render/quern_base
          translation: 0,0,0
        - item: tfp:render/quern_handstone # shown/hidden per state, rotated while grinding
          translation: 0,0.625,0
```

Dynamic contents (an item on the quern, anvil inputs) use dynamic renderers driven by the block-entity controller. See report §4.7 and §8.4.

## 7. Allocation report (datagen)
`tools/datagen` produces `build/datagen/visual-allocation.md` with, per MC version:
- per-pool used/available;
- the explicit vanilla states claimed, and by which TFC block;
- the `block_state_mappings` emitted;
- the total CE state count, which feeds the recommended `block.serverside-blocks`.

A pull request that changes block content must not exceed any pool. CI fails on overflow.
