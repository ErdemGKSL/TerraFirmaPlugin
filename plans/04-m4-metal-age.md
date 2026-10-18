# M4: Metal age

Goal: the TFC metallurgy chain up to red/blue steel. Exit: the scripted "metal run" self-test passes (copper → bronze alloys → wrought iron via bloomery → steel → black steel → red/blue steel), with GUIs and visuals.

Paths are relative to `src/main/java/net/dries007/tfc/` unless noted.

- [~] **M4-MET-01 Metals registry & items** · deps: M1-REG-01, M1-DGN-03 · skills: craftengine-items, tfc-asset-migration
  - TFC: `util/Metal.java`, `util/MetalItem.java`, `util/registry/RegistryMetal.java`, data `fluid_heat/`, `item_heat/`
  - Do: 27 metals with tiers; generated CE items for ingots, double ingots, sheets, double sheets, rods, tool heads, tools, armor parts and armor (~60 per metal). Metal blocks use `entity_renderer` (decided).
  - Done when: all metal items load; tooltips show heat; showcase.
  - Status (@claude): in progress. Done (data, `core/metal/`): `Metal` (the 27 metals, TFC order, with fluid heat, ingot heat and weathering resistance) and `MetalTier` (the 9 tool and armor tiers). Test: `MetalTableTest`, golden on the 27 metals (`metals.tsv` exported from TFC's `util/Metal.java`, `TFCTiers`, `TFCArmorMaterials`, `fluid_heat/` and `item_heat/` at c3b4d9536; verified 27 metals). Open: the CraftEngine items per metal, the registry glue, the item heat tooltips, the showcase.

- [~] **M4-MET-02 Alloying** · deps: M4-MET-01, M3-POT-02
  - Status (@claude): in progress. Done (rules, `core/alloy/`): `AlloyRange`, `AlloyRecipe`, `FluidAlloy` (immutable fill with capacity, proportional drain, epsilon-filtered contents, result by first matching recipe or unknown, matching with the result-removal rule). Test: `FluidAlloyTest`, golden on all 9 TFC alloys (`alloy-recipes.tsv` exported from TFC's `recipe/alloy/` at c3b4d9536). Open: the Paper fluid glue, the alloy recipes as CE/Paper data and the alloy GUI or display.
  - TFC: `util/AlloyRange.java`, `util/FluidAlloy.java`, recipes `alloy/`
  - Done when: golden alloy-ratio tests for all 9 alloys.

- [~] **M4-MET-03 Casting** · deps: M4-MET-02
  - TFC: `common/recipes/CastingRecipe.java`, `CastingCraftingRecipe.java`, recipes `casting/`
  - Done when: all 111 casting recipes; mold break chance as in TFC.
  - Status (@claude): in progress. Done (rules, `core/casting/`): `CastingRecipe` (record: mold, fluid, amount, result, count, break chance with TFC's default 1), `CastingRules` (match, first-match lookup, output, use-break and craft-keep decisions with the roll injected). Tests: `CastingRulesTest` (12 tests), golden on all 111 recipes (`casting-recipes.tsv` exported from TFC's `recipe/casting/` at c3b4d9536; verified 111 JSON files, 55 at chance 1, 28 at 0.01, 28 at 0.1). Open: the Paper casting block and mold glue, the CE items, the mold break visuals, the GUI.

- [~] **M4-ANV-01 Forging rules** · deps: M1-REG-02 · skills: tfc-feature-porting
  - TFC: `common/component/forge/` (`ForgeRule`, `ForgeStep`, `ForgeSteps`, `Forging`, `ForgingBonus`); test `ForgeRuleTest`
  - Done when: `ForgeRuleTest` ported and green.
  - Status: core `dev.tfp.core.forge` ports `ForgeStep`, `ForgeRule`, `ForgeSteps` (incl. matching from `Forging`) and `ForgingBonus` (thresholds and multipliers as parameters); test `ForgeRuleTest` (3 tests) green. Open: anvil block, forging GUI, golden forging sequences, welding, Paper glue for config values.

- [~] **M4-ANV-02 Anvil block & GUI** · deps: M4-ANV-01, M3-HEAT-01 · skills: craftengine-gui-furniture, craftengine-java-api
  - TFC: `common/blockentities/AnvilBlockEntity.java`, `common/container/{Anvil,AnvilPlan}Container.java`, `network/SelectAnvilPlanPacket.java`, `common/blocks/devices/AnvilBlock.java`, `common/blocks/rock/RockAnvilBlock.java`, textures `gui/anvil*.png`
  - Do: rock anvil + metal anvils (tiered); plan selection GUI; forging GUI with step buttons (slot items), target/progress markers drawn as title glyph overlays; hammer use; items visible on the anvil top (dynamic renderer); welding; forging bonus.
  - Done when: all 281 anvil + 87 welding recipes; GUI checklist; golden forging sequences.
  - Status: core `dev.tfp.core.anvil` ports the anvil recipe (`AnvilRecipe`, `AnvilRecipes`: tier gating, candidates per item, default plan choice), `ForgingProgress` (step history plus work offset, overwork guard, destroy range, `checkComplete` with leeway, forging bonus ratio), and welding (`WeldingRecipe`, `WeldingRecipes`, the copy-best/copy-worst bonus rule). Verified counts from TFC at c3b4d9536: 281 anvil recipe files (271 under `metal/` plus 10 top level) and 87 welding files. Tests: `AnvilGoldenTest` (6), `WeldingGoldenTest` (6), `ForgingSequenceTest` (3: four TFC step plans accepted with their outputs, reversed plans rejected, leeway), `AnvilLogicTest` (11). Golden data: `core/src/test/resources/dev/tfp/core/anvil/` (TSV, scratch exporter outside the repo). Open: the anvil block and CE visuals, plan selection and forging GUI with the glyph overlays, the hammer use, item visuals on the anvil, the welding glue (flux slot), and the forging bonus config values (the work target is supplied by the caller; TFC's seeded target is not ported yet).

- [ ] **M4-FORGE-01 Charcoal forge & bellows** · deps: M3-HEAT-03 · skills: paper-folia-threading
  - TFC: `common/blockentities/{CharcoalForge,Bellows}BlockEntity.java`, `common/blocks/devices/{CharcoalForge,Bellows}Block.java`, tag `charcoal_forge_insulation`
  - Do: multiblock insulation check (async), temperature curve (lazy while active), bellows boost.
  - Done when: golden heating curves.

- [ ] **M4-CRUC-01 Crucible, channels & mold table** · deps: M4-MET-02, M4-FORGE-01
  - TFC: `common/blockentities/{Crucible,Channel,MoldTable}BlockEntity.java`, `util/ChannelFlow.java`
  - Do: crucible GUI, pouring into channels, flow simulation decided async, mold table filling; molten metal visuals with an emissive animated texture.
  - Done when: flow golden tests; self-test pour.

- [~] **M4-BLOOM-01 Bloomery** · deps: M4-FORGE-01
  - TFC: `common/blockentities/{Bloomery,Bloom}BlockEntity.java`, recipe `bloomery/`, tag `bloomery_insulation`
  - Done when: wrought iron path works; golden timing.
  - Status (@claude): in progress. Done (rules, `core/bloomery/`): `BloomeryRecipe` (fluid, catalyst, result, duration), `BloomeryItem` (per-unit melt), `BloomeryRules` (chimney levels and capacity, recipe selection, dump rule, alternating intake, bloom output with the 1-degree start temperature, bloom layers, lazy `phaseAt` with the halt tick). Tests: `BloomeryGoldenTest` (5 tests: 1 row verified against 1 JSON file in `recipe/bloomery/` at c3b4d9536, 14400 ticks, 100 mB cast iron + 2 charcoal, 1535 to 1534 degrees; `bloomery.tsv`), `BloomeryRulesTest` (17 tests). Open: the multiblock scan glue (base, chimney, gate checks), the bloomery block and its CE visuals, the GUI or display; the catalyst intake is verified against TFC's `Helpers.alternatingConsumeItemsFromEntitiesIndividually`, which splits one unit per step, so single-unit intake matches, the Paper tick or lazy trigger, the `bloomery_insulation` tag, the wrought iron anvil step (`refined_iron_bloom`, M4-FORGE-01).

- [ ] **M4-BLAST-01 Blast furnace** · deps: M4-BLOOM-01, M4-CRUC-01
  - TFC: `common/blockentities/BlastFurnaceBlockEntity.java`, recipe `blast_furnace/`, tag `blast_furnace_insulation`
  - Done when: pig iron → steel path works.

- [ ] **M4-INGOT-01 Ingot & sheet piles** · deps: M4-MET-01 · skills: craftengine-gui-furniture
  - TFC: `common/blockentities/IngotPileBlockEntity.java`, `common/blocks/devices/{IngotPile,DoubleIngotPile}Block.java`
  - Do: stacking piles with the `tint_source` renderer (count + metal → item model select); collision layer.
  - Done when: visual checklist; place/break preserves items.

- [~] **M4-TOOL-02 Metal tools, armor & tiers** · deps: M4-MET-01, M3-TOOL-01 · skills: craftengine-items
  - TFC: `common/items/*` (tools, armor, shields, maces, javelins), `common/component/forge/ForgingBonus*`
  - Do: tier `break_power`, mining speeds, durability with the forging bonus, armor damage resistances, equipment assets.
  - Done when: tier progression tests; armor golden values.
  - Status (@claude): in progress. Done (rules, `core/tool/`): `ToolKind` (17 metal tool kinds with head part, attack factor, attack speed, digger and damageable flags), `ToolStats` and `MetalToolRules` (durability = tier uses, mining speed = tier speed for diggers else 1, attack damage = factor x tier damage, break power = tier level, heat-to-fluid scaling `fluidAfterDamage`), `ArmorSlot` and `ArmorRules` (per-piece durability and defense, horse body uses chest, total defense), `ForgingBonus` and `ForgingConfig` (ratio thresholds, efficiency, durability chance, damage multipliers, unbreaking). Counts verified against TFC at c3b4d9536: 153 tool rows (9 tiers x 17 kinds), 9 armor rows, 5 forging rows (`tools.tsv`, `armor.tsv`, `forging.tsv` exported by a scratch script). Tests: `MetalToolGoldenTest` (6: row counts, per-row tool, armor and forging numbers), `MetalToolRulesTest` (10: tier progression by level, red and blue steel, copper pickaxe values, non-diggers, heating fluid, armor slots, forge ratio bounds, unbreaking bounds). Open: the CraftEngine item definitions per metal (heads, tools, armor), the registry glue, the tooltips, the tool and armor recipes, the Paper damage and break-speed hooks (forging efficiency and damage multipliers), and the shears' special mining speed.

- [ ] **M4-PROS-01 Prospecting** · deps: M2-GEN-10, M4-TOOL-02
  - TFC: `common/items/PropickItem.java`, `ProspectResult.java`, `network/ProspectedPacket.java`, `common/commands/PropickCommand.java`
  - Do: propick scan decided async (snapshot of the radius), result message; this replaces visual ore-grade info on `level-3a`.
  - Done when: golden scan results on fixed worlds.

- [~] **M4-CHIS-01 Chisel** · deps: M4-TOOL-02
  - TFC: `common/items/ChiselItem.java`, `common/player/ChiselMode.java`, `network/CycleChiselModePacket.java`, recipes `chisel/`
  - Do: modes cycled via a vanilla trigger (sneak + swap-hand key), smoothing/stairs/slabs conversions.
  - Done when: all 513 chisel recipes.
  - Status (@claude): in progress. Done (rules, `core/chisel/`): `ChiselMode` (smooth/slab/stair, TFC priorities, cycle with wrap), `ChiselRecipe` (record: any-of ingredients, mode, result, optional item output), `ChiselRules` (first-match lookup, use decision with the tool and placement inputs, slab half from the hit face, damage 1 and cooldown 10 as constants). Tests: `ChiselGoldenTest` (6 tests: 513 rows verified against 513 JSON files at c3b4d9536, 57 smooth / 228 slab / 228 stair, 0 duplicate input+mode pairs among 534 claims, every recipe found by its own input and mode, slab and stair names match their mode), `ChiselRulesTest` (9 tests). Open: the CE chisel item and block glue, the mode cycle trigger (sneak + swap-hand), the smoothing and stairs visuals (TFC's stair placement and fluid fill), the Paper glue (collapse check on `CAN_TRIGGER_COLLAPSE`, the item drop and damage).

- [~] **M4-WEATH-01 Metal weathering & lamps** · deps: M4-MET-01, M2-CAL-01
  - TFC: `common/blocks/Weathering*.java`, `common/blockentities/LampBlockEntity.java`, data `lamp_fuel/`
  - Done when: lazy weathering test; lamp fuel burn golden test.
  - Status (@claude): in progress. Done (rules, `core/weathering/WeatheringRules`): the gate, age modifier and stage chance, the random tick rate, and `catchUp` (lazy stage walk with exponential waits over the elapsed calendar ticks, the drip and climate chance held as `extraChance`). Tests: `WeatheringLazyTest` (10 tests: TFC formulas, all metals via `Metal`, golden median for copper, monotone and absorbing properties), `WeatheringLazyVsSteppedTest` (TFC's per-random-tick draw order against `catchUp` over 3 configs, 20,000 samples each). Lamps: the burn rule was already in `core/light/LampRules` (M3-HEAT-04); added `LampFuelTsvGoldenTest` on `light/lamp-fuel.tsv` (8 rows exported from TFC's `lamp_fuel/` at c3b4d9536: 4 fuels x 1 and 250 mB). Open: the Paper weathering glue and the lazy catch-up trigger (chunk load or interaction, the neighbour and climate samples), the CE weathering block states, the lamp block and visuals, the lamp GUI if any.

## Exit criteria
The metal-run self-test passes on all targets. Anvil/forge/crucible GUIs pass the visual checklist. Ledger rows `playable`.
