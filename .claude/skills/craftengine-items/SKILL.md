---
name: craftengine-items
description: Defining TFC items in CraftEngine and wiring their mechanics. Item entry keys, data processors and raw vanilla components, settings, choosing the base material, how CE identifies items and why our PDC is safe, the item updater, CE's item packet rewriting vs our tooltip layer, tool tiers/types via break_power and correct_tools, item behaviours (only 4 hooks) and custom Java ones, equipment, recipes (YAML only, no Java API, Paper-registered recipes ignored), loot tables and loot_sources, and the events/functions/conditions layer. Use this whenever generating or editing CE item YAML, adding tools/armor/food/metal items, recipes or loot, writing a tfp: item behaviour/processor, or debugging items that don't stack, mine, drop, craft or show the right tooltip, even if the user just says "add bronze tools".
---

# CraftEngine items, recipes, loot, events

Source: `references/research-report.md` (CE @ `b3079c5`). §-numbers refer to it. Item *visual* model definitions are covered in `craftengine-visuals-pack`. Java registration timing is covered in `craftengine-java-api`.

## Identity and our data (§1.6–1.8)
- A CE item = a vanilla `material` ItemStack + components. The CE id is stored at `minecraft:custom_data` → `craftengine:id`. **Our PDC lives next to it (`PublicBukkitValues`) and is safe**, except for:
  - CE's `data.pdc` processor, which replaces the whole PDC at build time. Never use it on our items.
  - updater `reset` without `keep_components: [minecraft:custom_data]`.
  - recipe results, which are fresh builds. Carry PDC over with transform recipes (`keep_custom_data`).
- Stamp creation data (e.g. TFC food creation tick) with a **non-constant item processor** registered in Java (`ItemProcessors.register`, used as `data: { tfp:food_stamp: {} }`). It runs on every CE build (give command, loot, recipe result, API). Vanilla copies/splits keep PDC.
- Items with different PDC don't stack. Design TFC food timestamps to round (TFC itself rounds creation dates) so stacks merge.
- **CE rewrites every outgoing item packet** (tag parsing, component rebase). Our lore/model-data layer must:
  - strip its own additions on the creative-mode return path, using a marker;
  - avoid `<...>` text that CE would parse as a tag.
  Pipeline order between our handler and CE's is unverified, so test it.
- **CE's `custom_model_data` processor replaces the whole CMD component.** On items whose visuals we drive from Java (heat glow, pan stages), don't set a root `custom_model_data` in CE. Keep the pack minimum ≥ 1.21.4 so CE uses `item_model` instead of CMD.

## Entry essentials (§1.1–1.5)
- **Always set `material` explicitly.** The default `nether_brick` is a vanilla crafting ingredient. Pick per family:
  - neutral material (`paper`, `stick`, `iron_nugget`) for plain items;
  - matching vanilla tool for tools, which gives enchantability and hand rendering. **Axe/shovel/hoe materials keep their vanilla right-click actions** (strip/path/till) unless we cancel them;
  - vanilla food material, or a `consumable` component, for food. `food` alone isn't edible on 1.21.2+.
- `data:` accepts CE processors **and any vanilla component by bare name** (`tool`, `max_stack_size`, `consumable`, `weapon`, `repairable`...). **Unknown keys are silently ignored**, in `settings:` too, so validate in datagen against report §1.3/§1.4.
- `settings.food` / `settings.equipment` run **after** `data:` and override it.
- Use `category` (singular) for the item browser. `categories` is ignored on items.
- Don't use the updater for gameplay data (its triggers are off and costly). Migrate our PDC ourselves.

## Tools and tiers (§2)
- Item `settings.break_power: <tier>` vs block `required_break_power`. Tool type: item `settings.tags: [tfp:tools/pickaxe, ...]` + block `require_correct_tools: true`, `correct_tools: ["#tfp:tools/pickaxe"]`. Mining speed comes from the item's vanilla `tool` component (`rules`, `default_mining_speed`, `damage_per_block`) against the block's `settings.tags`.
- **Applies to CE blocks only.** With a wrong tool there are **no drops** and mining is slowed ×0.3. Never put `required_break_power` on blocks that must always drop (loose rocks, groundcover).
- TFC tier numbers: take them from TFC `Metal`/tier code, not memory.
- Non-mining tools (chisel, hammer, saw, knife, scythe, propick, javelin) are our item behaviours. Simple area mining can use the built-in `range_mining_item`. Crafting-grid durability: `craft_remainder: {type: hurt_and_break, damage: 1}`. Javelin: `material: trident`.

## Item behaviours (§4)
- Built-ins: `block_item` (+ wall/ground/ceiling/double_high/multi_high/liquid_collision variants), `furniture_item`, `range_mining_item`, `compostable_item`, `flint_and_steel_item`.
- Custom: `ItemBehaviors.register(Key.of("tfp","chisel"), factory)` in `onLoad`. **Only 4 hooks**: `useOnBlock`, `useOnEntity`, `use` (air), `onBreakBlock`. Everything else (left click, consume, use duration/release, inventory tick, attack, drop, pickup, durability) needs our own Paper listeners.
- On a CE block, the **block** behaviour and block events get the click first. On a vanilla-interactable block, the item behaviour runs only while sneaking.
- Return `SUCCESS_AND_CANCEL` to cancel the Bukkit event.

## Recipes (§6)
- CE recipes are YAML only: `shaped`, `shapeless`, `*_transform`, `smelting`/`blasting`/`smoking`/`campfire_cooking` (**`time` defaults to 80**, so always set it), `stonecutting`, `smithing_*`, `brewing`. There is **no Java API** to add CE recipes.
- Ingredients: CE id, vanilla id, or `#tag` (vanilla tags + CE `settings.tags`). A CE item doesn't match a plain vanilla id unless it declares `settings.ingredient_substitute`.
- Recipe `functions` and `visual_result` are **Premium-only**. `conditions` appear to work in Community (verify).
- **Recipes we register through Paper are invisible to CE's crafting logic.** Whether a CE item made of material X leaks into vanilla recipes for X is **unverified**: test it, and guard with our own `PrepareItemCraftEvent` checks.
- TFC split: plain grid crafting with CE items → generated CE YAML. Knapping, anvil, welding, quern, barrel, pot, casting, heating, loom, scraping, sewing, glassworking → our own recipe engine (`core`) + GUIs. TFC "advanced" crafting with ingredient predicates/modifiers → our crafting interception.
- After `/minecraft:reload`, run `/ce reload recipe`.

## Loot (§7)
- Block drops come from the block's `loot:` (inline, template, or id string referencing a CE table or a datapack table). `loot_sources:` injects into vanilla events. In datapacks, use the `craftengine:item` entry type.
- From Java: `BlockBreakEvent#setDropItems(false)` (CE mirrors it into `CustomBlockBreakEvent`). Custom loot functions/entries: `LootFunctions.register` etc.

## Events / functions / conditions (§8)
A small scripting layer: `events: [{on: right_click, conditions: [...], functions: [...]}]`. Use it for simple glue (sounds, swing, toggling a property). Write real gameplay in Java. Custom ones: `CommonFunctions.register`, `CommonConditions.register`, `EventTrigger.register`. For items, `events: break:` means *item* break.

## Pitfalls checklist (§11)
Silent ignores (unknown keys, Premium keys); `category` vs `categories`; smelting time 80; settings-derived processors overriding `data:`; the CMD processor replacing the whole CMD component; the `pdc` processor wiping PDC; vanilla tool right-click actions; CE item tags not being vanilla tags; break power on CE blocks only; no drops with a wrong tool; no recipe API; recipes needing a reload after `/minecraft:reload`; CE rewriting item packets.
