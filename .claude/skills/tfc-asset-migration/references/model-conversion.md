# Converting TFC-only model constructs for vanilla clients

Vanilla clients only understand vanilla model JSON, blockstates (`variants`/`multipart`) and 1.21.4+ item model definitions. Every NeoForge/TFC model *loader* must be turned into one of those, or moved server-side. Counts are from TFC @ `c3b4d95`.

## Contents
1. `tfc:plant` (154): seasonal stage models
2. `neoforge:separate_transforms` (42)
3. `neoforge:fluid_container` (66), `tfc:fluid_container` (24), `tfc:mold` (16)
4. `tfc:leaves` (24)
5. `tfc:trim` (36)
6. Pile and table loaders (`tfc:ingot_pile`, `tfc:double_ingot_pile`, `tfc:mold_table`, `tfc:scraping`)
7. Item `overrides` with TFC predicates (73)
8. Multipart blockstates (519)
9. Two-layer models (`tfc:block/ore` with `overlay`)

## 1. `tfc:plant`
Example (`models/block/plant/morning_glory_dynamic_down_a.json`):
```json
{ "loader": "tfc:plant",
  "blooming": { "parent": "tfc:block/plant/morning_glory_0a" },
  "seeding":  { "parent": "tfc:block/plant/morning_glory_2" },
  "dying":    { "parent": "tfc:block/plant/morning_glory_1" },
  "dormant":  { "parent": "tfc:block/plant/morning_glory_1" },
  "sprouting":{ "parent": "tfc:block/plant/morning_glory_2" },
  "budding":  { "parent": "tfc:block/plant/morning_glory_2" } }
```
The client picks a stage from the calendar. We do that server-side:
- Add a block property `stage` (sprouting, budding, blooming, seeding, dying, dormant). Deduplicate to the distinct parents: here only 3 distinct models, so map stages to 3 appearance groups.
- Our plant behaviour updates `stage` lazily (chunk load, season change sweep, random check) from the calendar. Stage changes are block state changes and go through the budgeted commit queue.

## 2. `neoforge:separate_transforms`
A different model in GUI vs in hand. Emit an item model definition:
```yaml
model:
  type: minecraft:select
  property: minecraft:display_context
  cases:
    - when: [gui, ground, fixed]
      model: { type: minecraft:model, path: tfp:item/<id>_gui }
  fallback: { type: minecraft:model, path: tfp:item/<id>_held }
```

## 3. Fluid containers and molds
TFC renders the contained fluid with a tinted layer. Options, in order of preference:
1. **Baked variants:** for containers with few meaningful fills (e.g. bucket-like, mold filled/empty per metal), emit one model per state and select with `range_dispatch`/`select` on a custom model data value that we set from PDC (on the stack, or in outgoing packets).
2. **Tinted layer:** one model with a fluid layer + `tints: [{type: minecraft:custom_model_data, ...}]`. We set the fluid colour as custom-model-data colour.
3. If neither fits, show the contents only in the tooltip, and record a divergence.

## 4. `tfc:leaves`
TFC leaves pick seasonal textures and handle "fancy" rendering. Map them to CE leaves pools: `tintable_leaves` for tint-driven seasonal colour (biome foliage colour, via our biome remap), or explicit seasonal texture variants as a `season` property if the change is a texture swap rather than a tint. Decide per wood, from what the TFC textures do.

## 5. `tfc:trim`
Armor trim item variants. Use 1.21.4 item model definitions on the `trim` component (`select` on `minecraft:trim_material`), or vanilla trim handling for armor items.

## 6. Pile and table loaders
`tfc:ingot_pile`, `tfc:double_ingot_pile`, `tfc:mold_table`, `tfc:scraping` render contents that depend on block data (count, metal, progress).
- Few states (e.g. ingot count 1–64 × metal): use CE **dynamic entity_renderer** elements driven by our block-entity controller (one item_display per visible layer, not per ingot), or baked models per (count bucket, metal) if the count is small.
- Scraping progress (16 cells): a baked model per progress mask is feasible if the cells combine into ≤ a few hundred models. Otherwise use a renderer.

## 7. Item `overrides` → item model definitions
```json
"overrides": [ { "predicate": { "tfc:heat": 0.3 }, "model": "tfp:item/blowpipe/ceramic_1_st" }, ... ]
```
becomes
```yaml
model:
  type: minecraft:range_dispatch
  property: minecraft:custom_model_data
  index: 0                   # float slot 0 = heat fraction
  entries:
    - { threshold: 0.0, model: { type: minecraft:model, path: tfp:item/blowpipe/ceramic_0_st } }
    - { threshold: 0.3, model: { type: minecraft:model, path: tfp:item/blowpipe/ceramic_1_st } }
  fallback: { type: minecraft:model, path: tfp:item/blowpipe/ceramic_0 }
```
We write the float (e.g. heat fraction from the lazily computed temperature) into the item's custom model data **in outgoing packets**, bucketed, so the server stack doesn't churn. The same mechanism serves the pan stages (`deposit` `model_stages`).

## 8. Multipart blockstates
- Rock walls (189), wood fences/gates (121), plants (74), alabaster/wattle/mud bricks, connected grass sides (`grass`, `clay_grass`, `duff`).
- **Flatten** when feasible: enumerate the property combinations, take the union of matching `when` parts, and merge their elements into one baked model per combination (apply each part's x/y rotation to its elements; skip combinations that are impossible in game). Good for connected grass (≤16 combos per block).
- **Renderer** when not: CE templates `default:block_state/fence`/`wall`-style. A real vanilla base state provides the shape, and `entity_renderer` draws post + sides.
- Overriding a single state of a vanilla multipart block works (the client merges blockstates per state; verified on 26.1.2). The practical limit is the number of states you'd have to cover, so fences and walls stay renderer-based.

## 9. Two-layer models
TFC's `tfc:block/ore` stacks a base cube (`#all`) and an overlay cube (`#overlay`, cutout). Copy the parent model as `tfp:block/ore` and keep using it via CE `generation.parent`. Vanilla clients render two-layer cubes fine. Check render type: vanilla chooses cutout from the texture alpha, and the TFC `render_type` field (14 models) is ignored by vanilla. Verify those 14 visually.
