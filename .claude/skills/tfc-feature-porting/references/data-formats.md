# TFC data formats (what the datagen tool ingests)

All examples below are copied verbatim from TFC `src/generated/resources/data/tfc/` at commit `c3b4d95`. Keep these schemas as our on-disk format, so TFC's data (and TFC addon datapacks) can be imported mechanically.

## Contents
1. Common building blocks: ingredients and results
2. Definition folders (`data/tfc/tfc/<kind>/`)
3. Recipe types (`data/tfc/recipe/<type>/`)
4. Tags
5. Porting rules for data

## 1. Ingredients and results

TFC uses vanilla 1.21 ingredient syntax plus custom ingredient types:

```json
{ "item": "tfc:food/cooked_calamari" }
{ "tag": "c:ingots/weak_steel" }
[ { "item": "a" }, { "item": "b" } ]          // any-of
{ "type": "tfc:and", "children": [ {"item": "tfc:jar/red_apple"}, {"type": "tfc:lacks_trait", "trait": "tfc:canned"}, {"type": "tfc:not_rotten"} ] }
```

Custom ingredient types seen in data, by frequency: `tfc:and`, `tfc:not_rotten` (1418 each), `tfc:lacks_trait`, `tfc:has_trait`, `tfc:heat`, `tfc:fluid_content`. Sources: `common/recipes/ingredients/`.

Results use `{"id": "...", "count": n}`. Output modifiers (`common/recipes/outputs/`) appear in a `modifiers` list: `tfc:copy_input`, `tfc:add_trait`, `tfc:remove_trait`, `tfc:copy_food`, `tfc:copy_oldest_food`, `tfc:meal`, `tfc:copy_forging_bonus`, `tfc:add_heat`, `tfc:add_glass`, `tfc:add_powder`, `tfc:dye_leather`, `tfc:remove_dye`, `tfc:empty_bowl`, `tfc:extra_products`, `tfc:chance`, `tfc:round_creation_date`, `tfc:add_bait_to_rod`, `tfc:damage_crafting_remainder`.

Port ingredients and modifiers as a small sealed-interface hierarchy in `core/`, with a JSON codec that is keyed by `type`. Unknown types should fail loudly at load time, never be skipped silently.

## 2. Definition folders

| Folder | Files | Meaning |
|---|---|---|
| `item_heat` | 343 | heat capacity, forging/welding temperature per ingredient |
| `food` | 195 | hunger, saturation, nutrients, decay modifier |
| `item_size` | 69 | size + weight (inventory/encumbrance rules) |
| `fuel` | 50 | burn duration, temperature, purity |
| `fluid_heat` | 28 | melt temperature, specific heat of molten metals |
| `climate_range/crop`, `climate_range/...` | 49+ | temperature/hydration windows for crops and plants |
| `deposit` | 84 | panning/sluice deposits with loot table and model stages |
| `fertilizer` | 10 | N/P/K amounts |
| `drinkable` | 5 | water, intoxication, effects |
| `fauna` | many | spawn climate windows, months |
| `support` | 1+ | support beam ranges |
| `knapping_type`, `lamp_fuel`, `entity_damage_resistance`, `item_damage_resistance` | | as named |
| `worldgen/rock_settings` | | per-rock worldgen settings |

Examples:

```json
// item_heat/weak_steel/ingot.json
{ "forging_temperature": 924.00006, "heat_capacity": 2.857143,
  "ingredient": { "tag": "c:ingots/weak_steel" }, "welding_temperature": 1232.0 }

// food/cooked_calamari.json
{ "decay_modifier": 2.25, "hunger": 4, "ingredient": { "item": "tfc:food/cooked_calamari" },
  "protein": 1.5, "saturation": 1.0 }

// fuel/white_cedar_logs.json
{ "duration": 1500, "ingredient": { "tag": "tfc:white_cedar_logs" }, "purity": 0.95, "temperature": 625.0 }

// item_size/hanging_signs.json
{ "ingredient": { "tag": "minecraft:hanging_signs" }, "size": "very_small", "weight": "medium" }

// fluid_heat/high_carbon_blue_steel.json
{ "fluid": "tfc:metal/high_carbon_blue_steel", "melt_temperature": 1540.0, "specific_heat_capacity": 0.008571429 }

// drinkable/alcohol.json
{ "ingredient": { "tag": "tfc:alcohols" }, "intoxication": 4000, "may_drink_when_full": true, "water": 5.0 }

// climate_range/crop/pumpkin.json
{ "max_hydration": 85, "max_temperature": 27.0, "min_hydration": 20, "min_temperature": -7.0 }

// fertilizer/pure_potassium.json
{ "ingredient": { "item": "tfc:pure_potassium" }, "potassium": 0.1 }

// deposit/native_copper/tuff.json
{ "ingredient": { "item": "tfc:deposit/native_copper/tuff" }, "loot_table": "tfc:deposit/native_copper_tuff",
  "model_stages": [ "tfc:item/pan/native_copper/tuff_full", "tfc:item/pan/native_copper/tuff_half", "tfc:item/pan/native_copper/result" ] }

// fauna/alpaca.json
{ "ignore_rivers": true, "max_groundwater": 500.0, "max_temperature": 35.0, "min_groundwater": 300.0,
  "min_temperature": 22.0, "months": ["may","june","july","august","september","october"] }

// support/horizontal_support_beam.json  (ingredient list shortened)
{ "ingredient": ["tfc:wood/horizontal_support/acacia", "..."], "support_down": 2, "support_horizontal": 4, "support_up": 2 }
```

## 3. Recipe types

Counts per folder: crafting 2912 (`tfc:advanced_shaped_crafting`, `tfc:advanced_shapeless_crafting`, plus vanilla), heating 884, collapse 525, chisel 513, barrel 331 (`tfc:barrel_sealed`, `tfc:barrel_instant`, `tfc:barrel_instant_fluid`), anvil 281, pot 214 (`tfc:pot`, `tfc:pot_jam`, `tfc:pot_soup`), casting 111, knapping 101, quern 89, welding 87, glassworking 60, sewing 25, landslide 21, alloy 9, scraping 7, loom 5, bloomery 1, blast_furnace 1.

```json
// recipe/quern/light_blue_dye.json
{ "type": "tfc:quern",
  "ingredient": [ {"item": "tfc:plant/labrador_tea"}, {"item": "tfc:plant/sapphire_tower"}, {"item": "tfc:plant/mountain_hullwort"}, {"item": "tfc:plant/penwortel"} ],
  "result": { "count": 2, "id": "minecraft:light_blue_dye" } }

// recipe/anvil/wrought_iron_grill.json
{ "type": "tfc:anvil", "ingredient": { "tag": "c:double_sheets/wrought_iron" },
  "result": { "count": 1, "id": "tfc:wrought_iron_grill" },
  "rules": ["punch_last", "draw_any", "punch_not_last"], "tier": 3 }

// recipe/knapping/... (pattern rows are 5 chars, '#' = keep)
{ "type": "tfc:knapping", "knapping_type": "tfc:goat_horn",
  "pattern": ["#####", "## ##", "## ##", "#   #", "#####"], "result": { ... } }

// recipe/pot/jam_red_apple_canning_2.json (abridged)
{ "type": "tfc:pot", "duration": 300, "temperature": 300.0, "uses_all_fluid": false,
  "fluid_ingredient": { "amount": 200, "fluid": "minecraft:water" },
  "ingredients": [ { "type": "tfc:and", "children": [ {"item": "tfc:jar/red_apple"}, {"type": "tfc:lacks_trait", "trait": "tfc:canned"}, {"type": "tfc:not_rotten"} ] } ],
  "item_output": [ { "modifiers": [ {"type": "tfc:copy_input"}, {"type": "tfc:add_trait", "trait": "tfc:canned"} ] } ] }
```

Vanilla crafting recipes must be handled through Paper's recipe API only where they are plain shaped/shapeless. Anything using TFC ingredients or modifiers needs our own crafting interception (PrepareItemCraftEvent / CraftItemEvent) backed by the ported recipe classes.

## 4. Tags
`data/tfc/tags/{block,item,fluid,entity_type,damage_type,worldgen}` plus TFC additions to `c:` and `minecraft:` tags. Tags drive behaviour (`can_collapse`, `can_start_collapse`, `can_landslide`, `breaks_when_isolated`, `*_insulation`, `can_be_snow_piled`, ...). Load them into our own tag registry, because our blocks are CraftEngine blocks and vanilla's block tags won't contain them by default.

## 5. Porting rules for data
- **IDs:** keep TFC's path structure (`food/cooked_calamari`, `ore/rich_bismuthinite/andesite`) under our namespace. The namespace is provisional, see `CLAUDE.md`. The datagen tool owns the `tfc:` → our-namespace rewrite. Never hand-edit IDs.
- **Numbers:** TFC values like `924.00006` are float artefacts. Keep them as-is, don't round.
- **Item identity:** an "item" in TFC data maps to a CraftEngine item ID or a vanilla material. The datagen tool resolves this through one mapping table, so the rest of the code doesn't care.
- **Reload:** data loads off-thread into immutable registries, and the swap is atomic (`AtomicReference`). Gameplay code never sees a half-loaded registry.
