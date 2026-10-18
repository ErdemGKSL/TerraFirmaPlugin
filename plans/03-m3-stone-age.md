# M3: Stone age

Goal: the full TFC early game. A player spawns, gathers loose rocks and sticks, knaps stone tools, survives on food and water with nutrition and decay, makes fire, cooks, makes pottery in a pit kiln, farms, makes charcoal, and finally melts native copper in a vessel and casts the first copper tool. Exit: that whole path is playable on Paper and Folia, with HUD and GUIs.

Paths are relative to `src/main/java/net/dries007/tfc/` unless noted. Every task follows the Definition of Done in `plans/README.md`.

## Player survival

- [~] **M3-SURV-01 Food component & decay** · deps: M1-REG-01, M2-CAL-01 · skills: tfc-feature-porting
  - TFC: `common/component/food/` (`FoodComponent`, `FoodCapability`, `FoodData`, `FoodTrait(s)`), data `food/`
  - Do: item PDC codec (creation date + traits), lazy rot evaluation, decay modifiers, trait multipliers (salted, brined, pickled, smoked, preserved, ...), stack merging with rounded creation dates (TFC rounding), the `tfp:food_stamp` CE item processor.
  - Done when: golden tests (rot date for given foods/traits), stacking tests, lazy-vs-stepped test.
  - Status (@claude): in progress. Done: the pure rules in `core/food/` (`FoodDecay`: flags, decay date modifier, lifetime, rotten check, stage, creation-date rounding, trait creation-date shift, stack rule; `FoodState`: creation date + traits, sanitize, apply/remove trait, stacking, merge; `FoodTrait` with TFC's default modifiers; `FoodConfig`), with `FoodDecayGoldenTest` (254 rows from TFC's own arithmetic), `FoodStackingTest` and `FoodLazyVsSteppedTest`. Done (Paper): the item PDC codec `paper/food/FoodPdc` (nested container `tfp:food` = {`tfp:v` int version, `tfp:created` long, `tfp:traits` string list}; absent/newer reads empty, other PDC kept) and the stack helper `paper/food/FoodStacks` (`canMerge` by the core `FoodState.stacksWith` rule, `mergeInto` keeps the earlier date), with `FoodPdcMockBukkitTest` (round trip, no key, merge). Done (CE + events): the `tfp:food_stamp` item data processor in Java (`integration/craftengine/FoodStampProcessor`, non-constant, registered in `onLoad` through `CraftEngineBehaviours.itemProcessor`; YAML `data: { tfp:food_stamp: {} }` or `{ traits: [tfp:wild] }`) dates every CE build via `paper/food/FoodStamp` (rounded current player tick from the published clock, starting traits applied as TFC `applyTrait`; an existing date is kept; undated while the calendar is unset); `paper/food/ItemFoodHooks` implements `FoodHooks<ItemStack>` (`isRotten`, `hasTrait` accepting `tfc:`/`tfp:` ids) plus the stage and the tooltip line (`food.stage.fresh|rotten|never_rots`, en_us + tr_tr); `paper/food/FoodMergeListener` (registered in `TfpPlugin`) applies TFC's stack rule to inventory clicks (cursor-on-slot swaps), player pickups and hopper moves through `FoodStacks.canMerge`/`mergeInto`, earlier date wins; tests `FoodMergeListenerMockBukkitTest` (stamp, tooltip stages, stamped vs unstamped no merge, close dates merge via click and hopper events) and `FoodStampProcessorTest`. Open: (1) no datagen emits `tfp:food_stamp` yet (no CE food items are generated); (2) the tooltip line is produced but not yet drawn: the packet lore decorator (`ClientItemDecoration` + strip) for item tooltips does not exist; (3) done in M3-SURV-02: the food-definition `decay_modifier` comes from `FoodDefinitions` by item id (1 for items without a definition). Note: TFC dates food in player ticks (`Calendars.get().getTicks()` = `CalendarState.playerTicks`), not calendar ticks, and has no "stale" stage (fresh or rotten only).

- [~] **M3-SURV-02 Nutrition & eating** · deps: M3-SURV-01, M1-SVC-02
  - TFC: `common/component/food/NutritionData.java`, `Nutrient.java`, `common/player/PlayerInfo.java`
  - Do: 5 nutrient groups, eating rules (the TFC hunger/saturation model replaces vanilla food), the max-health bonus from nutrition.
  - Done when: golden tests vs TFC eating sequences.
  - Status (@claude): in progress. Done (rules, `core/food/`): `Nutrient` (TFC order), `NutritionConfig` (TFC defaults: window 80, defaults 0.5/dairy 0, health modifiers 0.2/0.85/3.0, passive exhaustion 1), `NutritionState` (TFC `NutritionData`: records newest first, the hunger-window average with the zero-gap for lost hunger, default fill, cap 1, record truncation, the milk rule; values derived from the current hunger, so nutrition falls lazily as hunger is spent), `HungerState` (passive exhaustion from the player tick in integer units, 3000 ticks per point, saturation spent before hunger, vanilla eat caps, action exhaustion), `PlayerNutrition` (TFC `PlayerInfo.eat` order: record at the hunger before eating, add hunger/saturation, recompute), `NutritionLevels.healthModifier` (TFC `getHealthModifier`). Tests: `NutritionGoldenTest` (197 rows from TFC's own arithmetic: 21 health-modifier values, 130 eating-sequence steps, 46 timeline samples of the full per-tick simulation), `NutritionLazyVsSteppedTest` (one jump equals tick-by-tick and uneven steps; point count within 1 of TFC's float stepping over 200 days). Done (decay modifier, M3-SURV-01 leftover): `core/food/FoodDefinitions` (food data by item id from the registry snapshot; 1 for items without a definition), `paper/food/FoodDefinitionsCache` (rebuilt per snapshot), `ItemFoodHooks.DecayModifiers.byId` (CE id via `CustomItems.idOf`) wired in `TfpPlugin`, and `FoodStampProcessor` (through `IntegrationContext.foods`). Open: the eating action in Paper (consume event → `PlayerNutrition.eat`, rotten-food effects, thirst/intoxication hand-off to M3-SURV-03, peaceful handling, vanilla food level/saturation sync, persistence through `PlayerData.nutrition`), the nutrient HUD/GUI (M3-SURV-06, M3-GUI-01), the health-modifier effect on damage and displayed health (M3-SURV-04); "done when" golden tests vs TFC eating sequences are met for the rules.

- [~] **M3-SURV-03 Thirst & drinking** · deps: M3-SURV-02
  - TFC: `common/player/PlayerInfo.java` (thirst), data `drinkable/`, `network/PlayerDrinkPacket.java`, `common/effect/ThirstEffect.java`
  - Do: thirst decay by temperature/activity; drinking from water source blocks by right-clicking with an empty hand (the vanilla trigger replacing the packet), salt water effects, jugs; thirst effects.
  - Done when: golden tests; self-test drinking.
  - Status (@claude): in progress. Done (rules, `core/thirst/`): `ThirstConfig` (TFC defaults: max 100, modifier 5, heat share 0.4 over 22..34, stage thresholds 20/10), `ThirstState` (TFC's per-point loss `modifier x (1 + heat share)`, floored at 0; lazy from the passive points spent, given by the new `HungerState.pointsSpentTo`; drink capped at max; `Drinkable.attemptDrink` gate), `ThirstStage` (none / slowed / severe / dehydrated, with the slowdown amplifier and the damage flag). Tests: `ThirstGoldenTest` (221 rows from TFC's arithmetic: 11 heat/loss, 168 decay grid, 32 drink, 10 stage), `ThirstLazyVsSteppedTest` (one calendar day = 8 points = 60 thirst at 22 degrees; random steps equal one jump, points exactly and thirst within 1e-3). Open: the drinking action (right-click on water with an empty hand, the vanilla trigger replacing `PlayerDrinkPacket`), the water sources and salt water, jugs and glass bottles, PlayerData persistence and the thirst sync, the thirst effects in Paper (slowdown and dehydration damage every 100 ticks, not peaceful), the rain gain (`thirstGainedFromDrinkingInTheRain`), the HUD (M3-SURV-06), and action exhaustion counted in the thirst decay.

- [~] **M3-SURV-04 TFC health scale & damage types** · deps: M1-SVC-02
  - TFC: `util/PhysicalDamage(Type).java`, data `entity_damage_resistance/`, `item_damage_resistance/`
  - Do: the TFC max health model (displayed "45/50" via HUD), crush/pierce/slash resistances, vanilla damage mapping.
  - Done when: golden damage tests.
  - Status (@claude): in progress. Done (rules, `core/health/`): `HealthConfig` (TFC defaults: vanilla max 20, 50 display units per HP, regen every 10 ticks at 0.004 HP x multiplier, food gate 4, thirst gate 20), `HealthScale` (shown health `HP x modifier x 50`; player damage `amount x physical / modifier`, `MAX_VALUE` passthrough), `PhysicalResistances` (`e^(-0.01 x resistance)`, typeless = highest, the skeleton and zombie entity tables, 11 armour materials), `PhysicalDamageType` (vanilla tag mapping), `TfpDamageType` (`tfp:grill`, `pot`, `dehydration`, `pluck`, `lobster_trap`: exhaustion and burning), `HealthRegen` (the multiplier 1 to 3 and the step), `HealthState` (lazy: steps counted by `floorDiv`, applied at once, capped at max). Tests: `HealthGoldenTest` (210 rows from TFC's arithmetic: 23 resistance, 120 damage, 18 display, 3 max, 30 regen, 16 stepped regen), `HealthLazyVsSteppedTest` (random segments equal tick-by-tick stepping within 1e-3), `HealthRulesTest`. Open: the Paper damage hook (`LivingIncomingDamageEvent` and the forging bonus), the vanilla damage source to type mapping for weapons and entities, PlayerData persistence of `HealthState`, the displayed health and the HUD (M3-SURV-06), and the open question in the ledger (the pinned display gives 850 max at average nutrition, not 50).

- [~] **M3-SURV-05 Item size & weight** · deps: M1-REG-01
  - TFC: `common/component/size/`, data `item_size/`
  - Do: inventory/container restrictions (e.g. large items not in small vessels), overburdened effect from heavy items, tooltip lines.
  - Done when: rules tested; self-test inventory restrictions.
  - Status (@claude): in progress. Done (rules, `core/item/`): `ItemSize` (TFC order, 7 sizes), `ItemWeight` (stack sizes 64/32/16/4/1), `ItemSizing` (size + weight, TFC default classes tool/armour/block/other), `ItemSizeLimits` (TFC config defaults), `ContainerRules` (chest, small and large vessel, placed-on-ground slot, barrel and bundle predicates), `Burden` (TFC `CarryCount`: exhausted at one huge very heavy item, overburdened at two). Tests: `ItemSizeGoldenTest` (117 rows from TFC's enums, config defaults and predicate expressions, including the 7 x 5 stack-size grid). Open: the item PDC and `item_size` data resolution in Paper (explicit definitions first, then the class default), the container insert checks in Paper (chest, vessels, placed items, barrel, bundle), the overburdened and exhausted effects every 20 ticks, the tooltip line, and the self-test of inventory restrictions. Note: TFC's `TOOL_SIZE` comment says tools stack to 1, but the code keeps weight medium (stack 16); the port follows the code.

- [ ] **M3-SURV-06 Survival HUD** · deps: M3-SURV-02, M3-SURV-03, M3-SURV-04, M1-INT-04 · skills: betterhud, image-generation
  - Do: BetterHud layouts: health bar + "45/50", hunger, thirst, held-item temperature popup (M3-HEAT-01). Hide vanilla hearts/food/air. Art from TFC `textures/gui/icons*`; extra art via `image-generation`.
  - Done when: visual checklist at GUI scales 1–4 on 1.21.11 and 26.3.

## Heat & fire

- [~] **M3-HEAT-01 Item heat** · deps: M1-REG-01, M2-CAL-01, M1-SVC-03 · skills: tfc-feature-porting, craftengine-visuals-pack
  - TFC: `common/component/heat/` (`HeatComponent`, `HeatCapability`), data `item_heat/`; test `HeatingItemTest`
  - Do: lazy heat (`lastTemperature` + `lastTick`), heat transfer to containers. Tooltip line + heat glow via `custom_model_data` floats in outgoing packets (`range_dispatch` item models). Burn damage on holding very hot items.
  - Done when: ported `HeatingItemTest` + golden cooling curves; glow visual check.
  - Status (c3b4d9536): pure rules ported and golden-tested in `core/.../core/heat/` (`HeatState`, `HeatConfig`, `HeatLevel`; 193 golden rows, lazy-vs-stepped, rules). Still open: item PDC for the heat state, the CraftEngine heat visual (`range_dispatch` glow) and the tooltip, transfer to containers, burn damage, and `HeatingItemTest` (TFC's test checks meat heating recipes through registries, so it belongs in the in-server self-test, not core).

- [~] **M3-HEAT-02 Fuels & fire starting** · deps: M3-HEAT-01
  - TFC: data `fuel/`, `common/items/FirestarterItem.java`, `FlintAndPyriteItem.java`, `util/events/StartFireEvent.java`
  - Do: firestarter use-duration via Paper events (CE item behaviours have no use-duration hook); fire starting rules.
  - Done when: self-test lighting a firepit.
  - Status (c3b4d9536): pure rules ported and golden-tested in `core/.../core/fuel/` (`FuelTable` with the 50 `fuel/` files, `Fuel`, `FuelBurn` (`consumeFuelForTicks`, one item per fuel slot), `FireStart` (chances, flint strength, `startFire` outcome with the random draws as inputs); 50 + 108 + 87 golden rows in `FuelTableGoldenTest`, `FuelBurnGoldenTest`, `FireStartGoldenTest`). Still open: the firepit block and its entity (M3-HEAT-03), the item and block data (ingredients as item/tag matchers, the firepit tags), the firestarter use-duration and flint/firestarter Paper glue, `StartFireEvent` world checks (`canSurvive`, `canBePlacedAt`), dirtiness from purity, and the CraftEngine visuals.

- [~] **M3-HEAT-03 Firepit, grill, pot** · deps: M3-HEAT-02, M1-SVC-01 · skills: craftengine-java-api, craftengine-gui-furniture
  - TFC: `common/blockentities/{AbstractFirepit,Firepit,Grill,Pot}BlockEntity.java`, `common/blocks/devices/FirepitBlock.java`, recipes `pot/`, `heating/`
  - Do: CE block + controller (fuel, temperature as a lazy curve, items), GUIs (TFC textures `gui/fire_pit*.png`), cooking via heating recipes, pot soups/jams (`tfc:pot`, `tfc:pot_soup`, `tfc:pot_jam`). Visible logs/flame via properties + an animated texture; smoke particles only while lit and interacted with (particle policy).
  - Done when: golden cooking times; self-tests; visual checklist.
  - Status (c3b4d9536): pure rules ported and golden-tested in `core/.../core/cooking/` (`FirepitState`, `FirepitRules` (`step` = one live tick of TFC `serverTick`, `advance` = lazy: the air phase stepped, the rest closed form per fuel segment), `GrillRules` (lazy item heating against a constant grill temperature, `ticksToCook`), `PotRules` (pre-boil and boil counters, the finish tick); 392 firepit, 252 grill, 81 cook-time and 594 pot golden rows in `FirepitGoldenTest`, `GrillGoldenTest`, `PotGoldenTest`; `CookingLazyVsSteppedTest`: seeded random lazy-vs-stepped and window composition). Divergences recorded in the ledger (calendar catch-up of the firepit, grill and pot; float precision of the grill steps). Still open: the block entities and their controllers, the placed firepit/grill/pot blocks, the CraftEngine visuals and GUIs, the containers and dropped items, dirtiness and ash, the heating recipes and pot outputs as data, the server self-tests and the visual checklist.

- [~] **M3-HEAT-04 Torches, candles, lamps (basic)** · deps: M3-HEAT-02
  - TFC: `common/blocks/TFCTorchBlock.java`, `DeadTorchBlock.java`, `TFCCandleBlock.java`
  - Do: torch burnout from the placement timestamp (lazy, checked on chunk load/interaction via the async pipeline) → dead torch; relighting.
  - Done when: lazy-vs-stepped test.
  - Status: pure rules ported and golden-tested (`core/light/`: torch/candle burnout and remaining time, candle/torch/lamp light levels, lamp fuel burn per check, lamp fuel durations from `lamp_fuel` data; lazy-vs-stepped test). Still open: the placed blocks and dead-torch swap, the light emission in Paper, the CraftEngine visuals, and the lamp cadence question (TFC drops the remainder on each check; see the test `lampOffGridCheckDropsTheRemainderAsTfcDoes`).

## Gathering & knapping

- [~] **M3-GATH-01 Groundcover & loose rocks pickup** · deps: M2-GEN-11 · skills: craftengine-blocks
  - TFC: `common/blocks/GroundcoverBlock.java`, `common/blocks/rock/LooseRockBlock.java`
  - Do: a `tfp:pickupable` behaviour (right-click → item into hand), button-state visuals.
  - Done when: self-test pickup; no drop issues with the wrong tool.
  - Status (@claude): pure pickup and break rules ported in `core/gather/` (`GatherRules`, `GatherPiece`, `GroundcoverType`, `LooseRockType`, `GatherYield`). TFC has no correct-tool requirement on any groundcover or loose rock, so the tool never gates a drop; a loose rock yields its own item (plain or mossy) with its count 1 to 3; creative pickup yields nothing. `GatherGoldenTest` matches TFC's loot tables exactly (143 pieces, both tool states, pickup and creative). Open: the `tfp:pickupable` block hook in Paper (right-click, held-item handling, stacking of loose rocks), the CraftEngine blocks and button-state visuals, the explosion_decay rule of the loose rock loot table, the self-test and the visual checklist.

- [~] **M3-GATH-02 Sticks, twigs, fallen leaves, plants harvesting** · deps: M2-GEN-12, M2-GEN-13
  - TFC: `common/blocks/wood/FallenLeavesBlock.java`, `common/blocks/plant/*`
  - Done when: TFC drop tables reproduced (loot golden tests).
  - Status (@claude): pure harvest rules ported in `core/gather/` (`HarvestRules`, `HarvestTable`, `HarvestTables`, `LootEntry`, `HarvestCondition`, `HeldTool`, `ToolTag`, `HarvestKind`). A small Minecraft-loot interpreter (match_tool, random_chance, block_state_property, any/all/inverted, alternatives, set_count) runs TFC's 277 loot tables (216 plants, 20 leaves, 20 twigs, 20 fallen leaves, groundcover stick); the injected roll source keeps seeded runs deterministic. `HarvestTables` is transcribed from the JSON at c3b4d9536. `HarvestGoldenTest` checks 6504 cells (542 rows, six tool states, roll 0.0 and 0.999, every block state) against a scratch oracle that reads the raw JSON. Open: the Paper harvest hook (break and right-click, creative handling), the CraftEngine blocks and their visuals, the self-test and the visual checklist.

- [~] **M3-KNAP-01 Knapping GUI & recipes** · deps: M1-REG-02, M0-GUI-01 · skills: craftengine-gui-furniture, tfc-feature-porting
  - Status: rules ported and golden-tested in `core/knap` (`KnappingPattern` grid and pattern match with TFC's offset and mirror rule, `KnappingType`, `KnappingRecipe`, `KnappingRules` (type lookup, matching, the first recipe by id), `KnappingSession` (click, take, close consumption)). Golden data: all 101 recipes and 5 types transcribed at c3b4d9536 into `knapping-recipes.txt`, and `knapping-golden.txt` with 1828 MATCH rows, 253 PICK rows and 10 ITEM rows. Tests: `KnappingGoldenTest`, `KnappingPatternTest` (ported `KnappingPatternTest`), `KnappingSessionTest`, `KnappingDeterminismTest`. Open: the Paper knapping container and screen (5x5 buttons at x+5y, the 6-row chest GUI), the CraftEngine recipes and the knapping tools.
  - TFC: `common/container/KnappingContainer.java`, `util/data/KnappingPattern.java`, `KnappingType.java`, data `knapping_type/`, recipes `knapping/`; test `KnappingPatternTest`
  - Do: textured 6-row chest GUI (5×5 grid in columns 2–6), types stone/clay/fire clay/leather/goat horn, consumption rules.
  - Done when: ported `KnappingPatternTest`; all 101 knapping recipes resolvable; GUI aligned.

- [~] **M3-TOOL-01 Stone tools & tool mechanics** · deps: M3-KNAP-01 · skills: craftengine-items
  - Status: rules ported and golden-tested in `core/tool` (`ToolType`, `ToolTier` for the four stone tiers, `IncorrectTag`, `MinedBlock`, `ToolRules`: mineable test, mining speed, correct-for-drops via the incorrect-for tags, durability loss per TFC's `willConsumeDurability` (the stone axe and shovel use vanilla's destroy-speed rule), attack damage and javelin throw damage). Golden tests: `ToolRulesGoldenTest` (4 tiers, 6 tool types, 9 block fixtures, 180 mining rows, 28 damage rows, transcribed from TFC at c3b4d9536) and `ToolRulesDeterminismTest`. Open: the Paper break and use hooks (`mineBlock`, correct-tool drops, the speed rule), the CraftEngine tool items with `break_power` and `correct_tools`, the javelin throw and the stone-tool visuals.
  - TFC: `common/items/ToolItem.java`, `JavelinItem.java`, `HammerItem.java`, `TFCHoeItem.java`, `ScytheItem.java`, `util/Metal.java` (tiers)
  - Do: CE items with `break_power` tiers + correct-tool tags, the vanilla `tool` component speeds, javelin throw (trident material), knife/scythe area behaviours.
  - Done when: mining tiers match TFC; self-test.

## Rock & soil physics

- [~] **M3-PHYS-01 Collapse** · deps: M1-THR-03, M2-GEN-07 · skills: paper-folia-threading, tfc-feature-porting
  - Status: rules ported and golden-tested in `core/collapse` (`CollapseRules`: support test via `SupportTable`, the trigger decision with the real and fake chances and the radius draws, `canStartCollapse`, `collapse` with the rubble yield and break-below; `CollapseKinds` holds the tag, toughness and recipe data). Golden tests: `CollapseGoldenTest` (support sweeps, pairs, unsupported sets, start grid, collapse grid, rubble, 200 trigger scenarios) and `CollapseDeterminismTest`. Open: the Paper collapse runner (async decide, sync apply: falling-block spawn, budgeted commits, the `startCollapse` column pass and the propagation ticks of `WorldTracker`), the block support data and the CraftEngine blocks.
  - TFC: `util/tracker/Collapse.java`, `WorldTracker.java`, `common/recipes/CollapseRecipe.java`, data `support/`, tags `can_collapse`, `can_start_collapse`, `can_trigger_collapse`
  - Do: mining triggers an async collapse decision (support beams radius, chance rules) → budgeted commits spawning falling blocks; collapse recipes (raw → cobble).
  - Done when: golden tests on scripted scenarios; Folia stress self-test.

- [~] **M3-PHYS-02 Landslide & gravity soils** · deps: M3-PHYS-01
  - Status: rules ported and golden-tested in `core/landslide` (`LandslideRules`: the support test via `SupportTable`, the open-side test, `decide` giving none, a fall or one sideways slide with its single draw, `step` giving the source and destination writes; `LandslideSoils` holds the TFC `can_landslide` tag, the `supports_landslide` tag and the 21 landslide recipes, transcribed from the JSON at c3b4d9536). Golden tests: `LandslideGoldenTest` (440 box rows x 8 seeds, every decision seen, table counts) and `LandslideDeterminismTest` and `LandslideStepTest`. TFC has no slope threshold and no slide chance; the rules follow its code. Open: the Paper slide runner (async decide, sync apply: falling-block spawn, the 2-tick delay and the queue of `WorldTracker`, the neighbour-update hooks), the block support data and the CraftEngine blocks.
  - TFC: `common/recipes/LandslideRecipe.java`, `common/entities/misc/TFCFallingBlockEntity.java` (`canFallThrough`), tags `can_landslide`, `supports_landslide`
  - Do: vertical falling (CE `falling_block` or ours) + sideways sliding decided async.
  - Done when: golden scenarios.

- [~] **M3-PHYS-03 Support beams** · deps: M3-PHYS-01 · skills: craftengine-blocks
  - Status: rules ported and golden-tested in `core/beam` (`BeamRules`: the connected faces, the horizontal span to a pole with TFC's maximum of 5, `canSurvive` for both shapes, the support reach of the horizontal beam (4/2/2, the same numbers as the collapse `SupportTable` data), and `update` giving the drop decision and the connections for one neighbour update). Golden tests: `BeamGoldenTest` (2048-entry span table along all four directions, 162-entry connection table, 300 random neighbourhoods, hand-worked cases) and `BeamDeterminismTest`. Open: the Paper connection runner (async decide, sync apply of connection state and drops), the CraftEngine beam blocks and their visuals, and the place/break chain self-test.
  - TFC: `common/blocks/wood/{Vertical,Horizontal}SupportBlock.java`, data `support/horizontal_support_beam.json`, tag `support_beams`
  - Do: connection states via the async decision pipeline; renderer visuals.
  - Done when: connection self-test (place/break chains), collapse prevention.

## Pottery & containers

- [~] **M3-POT-01 Pit kiln** · deps: M3-KNAP-01, M3-HEAT-02 · skills: craftengine-java-api
  - TFC: `common/blockentities/PitKilnBlockEntity.java`, `common/blocks/devices/PitKilnBlock.java`
  - Do: placed items + straw + logs stages, firing lazily from the calendar, results.
  - Done when: golden firing time; self-test.
  - Status (c3b4d9536): pure rules ported and golden-tested in `core/.../core/kiln/` (`PitKilnLayers`: straw 1 or 4 per item up to 7 layers, up to 8 logs from stage 7 to 15, `canLight`, `structureValid`; `PitKilnRules`: `progress` and `temperatureAt` (TFC float arithmetic, 1.125 eager clamp), `convertTick` (first tick at the recipe temperature, exact binary search), `evaluate` (UNLIT, LIT, FIRED, FAILED with `cookedThrough`), `converted`; 17 layer rows, 90 temperature, 72 conversion and 540 outcome golden rows in `PitKilnLayersGoldenTest` and `PitKilnGoldenTest`; `PitKilnLazyVsSteppedTest` against a per-tick copy of `serverTick`; `PitKilnDeterminismTest`). TFC has no layer-dependent burn time: the burn is the config constant `pitKilnTicks` (7200 default), so the duration is not a function of the layers. Failure is lazy: `evaluate` takes the first tick the kiln was seen invalid (or never). Still open: the kiln block, its placement and ignition in Paper (`PlacedItemBlock` straw hook, fire above, diagonal neighbours), the item heat of the contents (M3-HEAT-01), the CraftEngine visuals per stage, the self-test, and the M3-KNAP-01 input.

- [~] **M3-POT-02 Molds & small vessel** · deps: M3-POT-01, M3-HEAT-01
  - TFC: `common/component/mold/`, `common/items/MoldItem.java`, `VesselItem.java`, `common/container/SmallVesselInventoryContainer.java`; test `MoldTest`
  - Do: vessel inventory, melting contents in fire to molten metal (fluid component), pouring into molds (casting recipes), mold fill visuals (item model `range_dispatch`).
  - Done when: ported `MoldTest`; native copper → copper tool head path works.
  - Status (c3b4d9536): pure rules ported and golden-tested in `core/vessel` (`MoldKind` capacities, `MoldRules` fill, solid and molten test, heat capacity, cast break draw, firing at 1399; `MoldState` the lazy mold with fill, drain, heat, cast; `VesselRules` alloy fill, slot insert, content heat capacity, melt test; `VesselState` the lazy vessel with insert, fill, melt on heat). Golden tests: `MoldGoldenTest` and `VesselGoldenTest` (965 rows from a float32 scratch replay of TFC's arithmetic, `scratchpad/vessel/gen_golden.py`), `VesselLazyVsSteppedTest` and `VesselDeterminismTest`. TFC at c3b4d9536 has no `MoldTest` file, so the goldens are TFC's own formulas and constants, not a ported test. TFC has no drying for molds (the clay firing is a heat threshold). Divergences: single-result alloy, no melt residue (ledger). Open: the Paper vessel and mold placement and inventory (the GUI container, the casting click path), the CraftEngine items and visuals (mold fill `range_dispatch`), and the native copper path self-test.

- [~] **M3-POT-03 Large vessel & jugs** · deps: M3-POT-01
  - TFC: `common/blockentities/LargeVesselBlockEntity.java`, `common/items/JugItem.java`
  - Done when: self-test sealing and preservation.
  - Status (c3b4d9536): pure rules ported and golden-tested in `core/vessel` (`JugRules`: capacity 100, fill and drain as `FluidComponent`, drink drains everything, break draw below 0.02; `LargeVesselRules`: 9 slots, item accepted only unsealed and below `LARGE`, seal and unseal apply and remove `preserved` through `FoodState`). TFC has no seal or drying time for vessels, so there is no seal progress to evaluate; the lazy part is the food's decay from the moved creation date. Golden rows: `JugGoldenTest` (144 fill, 45 drain, 7 break) and `LargeVesselSealGoldenTest` (80 seal, 49 unseal) from `scratchpad/m3pot3/gen_jug_vessel.py`; `LargeVesselRulesTest` (acceptance grid), `LargeVesselLazyVsSteppedTest`, `LargeVesselDeterminismTest`. Still open: the Paper vessel and jug placement and inventory, the world pour (bucket amounts) and drink wiring, redstone and hopper automation, the CraftEngine blocks and visuals, and the self-test for sealing and preservation.

- [~] **M3-POT-04 Drying bricks, mud bricks, thatch** · deps: M2-CAL-01
  - TFC: `common/blocks/devices/DryingBricksBlock.java`, `ThatchBlock.java`
  - Done when: lazy drying test.
  - Status (c3b4d9536): pure rules ported and golden-tested in `core/drying` (`DryingRules`: the pile's reset counter, `advance` as the lazy closed form of TFC's tick over the rain ticks since the last check, `reset` for placement and brick adds, `canAdd` (count below 4, not dry), `progress`; `ThatchRules`: light block 15, the slow factor, NO_SLOW when the block holds a fluid). Golden tests: `DryingGoldenTest` (320 rows from a per-tick replay of TFC's tick in `scratchpad/m3pot4/gen_drying.py`, thresholds -1 to 28800, five rain patterns, adds at 20000) and `ThatchGoldenTest` (10 rows, float32). `DryingLazyVsSteppedTest` (2,500 random piles against the per-tick rule, window composition), `DryingPropertyTest` (determinism, four threads, dry is final, rain on the dry tick blocks it, input checks). Open: the placement (the CraftEngine pile and thatch blocks, the count states, the drop of the dried item), the rain feed from the weather tracker, the server check of the dried texture and thatch slowdown, and the ledger rows for the random-tick sampling.

## Processing

- [~] **M3-PROC-01 Quern** · deps: M1-SVC-01 · skills: craftengine-gui-furniture
  - TFC: `common/blockentities/QuernBlockEntity.java`, recipes `quern/`
  - Do: handstone interaction; spinning visual (`better_model` element or interpolated element per M0); grinding recipes.
  - Done when: all 89 quern recipes work; visual checklist.
  - Status (c3b4d9536): pure rules ported and golden-tested in `core/.../core/quern/` (`QuernRecipe`, `QuernRules`: progress as a lazy function of turns, 90 units per turn, manual 1 per tick; `find` (first by id), `canStart`); all 89 `quern/` recipes and the `fluxstone` tag in `core/src/test/resources/dev/tfp/core/quern/quern-recipes.txt`, 380 lookup golden rows in `quern-golden.txt`, `QuernGoldenTest` and `QuernDeterminismTest` (incl. lazy vs stepped TFC timer). Still open: the quern block and its block entity, the handstone interaction and handstone damage per grind, the network rotation glue (`RotationSink`), the spinning visual, the GUI, and the CraftEngine visuals. Note: TFC has no free-output-slot requirement to start a grind; excess output spills into the world.

- [~] **M3-PROC-02 Scraping, sewing, loom (manual)** · deps: M1-SVC-01
  - TFC: `common/blockentities/ScrapingBlockEntity.java`, `LoomBlockEntity.java`, `common/container/SewingTableContainer.java`, recipes `scraping/`, `sewing/`, `loom/`
  - Done when: recipes work; GUIs aligned.
  - Status (c3b4d9536): rules ported and golden-tested in `core/craft/` (`ScrapingRules` 16-cell grid mask, `LoomRules` push cooldown and lazy tick, `SewingRules` squares, stitches, cloth and yarn use and take, `CraftRecipes` tables for the 7 scraping, 5 loom and 25 sewing recipes). `CraftGoldenTest` (23 tests) checks the tables against `tfc-craft-recipes.tsv` (37 rows from the JSON, transcribed by a scratch script) and the step and result constants; `CraftDeterminismTest` (4 tests) covers table order, cell order and lazy-vs-every-tick loom. Divergences in the ledger (edge hit clamp; TFC's sewing cost quirks are kept). Still open: the Paper interaction (knife, loom and sewing right-click paths, the sewing button and stitch clicks), the GUIs and the sewing container, the CraftEngine visuals and textures (scraping and loom in-progress textures), and the server self-test.

- [~] **M3-PROC-03 Barrel** · deps: M3-SURV-01, M1-SVC-01
  - TFC: `common/blockentities/BarrelBlockEntity.java`, `common/component/block/Barrel*.java`, recipes `barrel/` (`barrel_sealed`, `barrel_instant`, `barrel_instant_fluid`)
  - Do: fluid storage, sealing with a lazy recipe completion timestamp, preservation traits, alcohol; fluid level visual via tint_source.
  - Done when: golden sealed-recipe durations; all 331 barrel recipes load.
  - Status (@claude): in progress. Done (rules, `core/barrel/`): `BarrelRules` (TFC's 10,000 mB capacity, the fill and drain rule with the seal gate and the same-fluid rule), `BarrelState` (contents, seal and unseal ticks, fill, drain, remaining ticks), `BarrelRecipe` (matches with the infinite fluid rule, select, assemble: multiplier, capacity trim, retained fluid, remainder), `BarrelProgress` (`tick` = TFC's per-tick serverTick; `settle` = the lazy jump, equal to the per-tick step). Tests: `BarrelGoldenTest` (309-row sealed table from TFC's recipe JSON, 696 progress rows and the fill and drain rows from a scratch oracle that steps TFC's serverTick), `BarrelLazyVsSteppedTest`, `BarrelDeterminismTest`, `BarrelRulesTest`. Divergence: the lazy catch-up completes a chain on the live tick and uses each step's own recipe (ledger). Open: the barrel block and its Paper interaction (seal toggle, fluid by bucket or jug, container slots, excess), the 22 instant recipes and the all-331 load check, the food traits from `on_seal` (M3-SURV-01), the rain fill and pouring, the GUI, the fluid level visual (CraftEngine tint_source), the server check.

- [~] **M3-PROC-04 Log pile & charcoal pit** · deps: M3-HEAT-02, M3-PHYS-01
  - TFC: `common/blockentities/{LogPile,BurningLogPile}BlockEntity.java`, `common/blocks/CharcoalPileBlock.java`
  - Do: multiblock validation (insulation check) via the async pipeline, lazy burn, charcoal pile layers.
  - Done when: golden yields; self-test.
  - Status (c3b4d9536): pure rules ported and golden-tested in `core/.../core/charcoal/` (`LogPile` (count 1..16, layer and row), `CharcoalPit` (`hasConverted`, `convertTick`, `remainingTicks`, `charcoalFromLogs`, `convert` for a column: the charcoal pile below, the bottom-first fill, the drop), `CharcoalSpread` (the cover rule and the neighbour sweep, the 1-in-7 draw passed in). The burn is `charcoalTicks` (18000 default), and the charcoal depends on the log count only, not the wood. 128 amount, 226 lit-state and 300 column golden rows in `CharcoalPitGoldenTest`, plus `CharcoalPitLazyVsSteppedTest`, `CharcoalPitDeterminismTest` and `CharcoalRulesTest`. Divergences in the ledger (the random-tick sweep, the draw moment, the `lastUpdateTick > 0` guard kept). Still open: the log pile and charcoal pile blocks and their placement and stacking, the placement and ignition in Paper (fire above, `canBePlacedAt`, the 30-tick neighbour delay, the insulation check through the async pipeline), the CraftEngine visuals (count and layer states), and the self-test.

- [~] **M3-PROC-05 Composter** · deps: M3-FARM-01
  - TFC: `common/blockentities/ComposterBlockEntity.java`
  - Done when: lazy composting test.
  - Status: pure rules ported to `core/compost/` (`CompostRules`, `CompostAddition`, `CompostBin`) and golden-tested against TFC at c3b4d9536 (544 golden rows: ready ticks, lazy settle, additions, extraction, stage and rotten grids, tag classification). Lazy-vs-stepped and determinism tests pass. Open: the composter block and block entity state, the Paper interaction (use, sneak extraction, the player-on-block check), the hopper automation, the rotten infestation, and the CraftEngine visuals.

## Farming

- [~] **M3-FARM-01 Farmland, hydration & nutrients** · deps: M1-SVC-01, M2-CLIM-01
  - TFC: `common/blocks/soil/FarmlandBlock.java`, `common/blockentities/FarmlandBlockEntity.java`, data `fertilizer/`
  - Do: NPK storage, hydration from nearby water (async decided), fertilizers.
  - Done when: golden nutrient tests.
  - Status: pure rules ported to `core/farm/` and golden-tested against TFC at c3b4d9536 (1857 golden rows, lazy-vs-stepped and determinism tests). Open: the farmland block and its block entity state, the Paper hydration source (async), the CE visuals, and the fertilizer item and data.

- [~] **M3-FARM-02 Crops** · deps: M3-FARM-01, M2-CLIM-02
  - TFC: `common/blocks/crop/` (`Crop`, `CropBlock`, `DefaultCropBlock`, `DoubleCropBlock`, `ClimbingCropBlock`, `FloodedCropBlock`, `SpreadingCropBlock`, dead/wild), `common/blockentities/CropBlockEntity.java`, `CropHelpers.java`
  - Do: calendar-driven growth with climate checks, catch-up on load/interaction (no tickers), death conditions, yields by nutrients, all 29 crops, seeds via `block_item`.
  - Done when: golden growth tests per crop type; lazy-vs-stepped tests.
  - Status: pure rules ported to `core/crop/` and golden-tested against TFC at c3b4d9536 (1,831 golden lines: all 29 crop constants and stage counts, stage ages, growth steps with climate, nutrient share, yield and death, the lazy catch-up loop, harvest counts). Lazy-vs-stepped, split and determinism tests pass. Open: the crop blocks (all shapes), the Paper growth glue and block entity (decide async, apply sync), seeds via `block_item`, the spread fruit, the CraftEngine visuals.

- [~] **M3-FARM-03 Berry bushes & seasonal plants** · deps: M2-GEN-13, M2-CAL-01
  - TFC: `common/blocks/plant/fruit/`, `common/blockentities/BerryBushBlockEntity.java`, `PlantRegrowth.java`
  - Do: lifecycle stages by season (dormant/healthy/flowering/fruiting) as properties updated lazily (async sweep on season change + load), harvest.
  - Done when: lifecycle golden tests.
  - Status (@claude): in progress. Done (rules, `core/berry/`): `BushSpecies` (seven stationary bushes, the cranberry included: lifecycle by hemispheric month, climate range), `Lifecycle`, `BerryRules` (`season`, `place`, `pick`, `randomTick`, `catchUp` = climate sampling, settled lifecycle, closed-form growth), `BerryBush`, `BerryClock`. Spreading bushes, canes, cranberry and fruit tree rules: `SpreadingSpecies`, `SpreadRules` (spread decision with the draws passed in, cane step, survival, distance to ground), `CranberryRules` (waterlogged catch-up), `FruitTree`, `FruitTreeRules` (sapling tick, branch cycles, leaves). Tests: `BerryGoldenTest`, `BerryLazyVsSteppedTest`, `BerryDeterminismTest`, `BerryRulesTest`, `SpreadingGoldenTest` (from TFC at c3b4d9536, rows in `spreading-golden.txt`), `SpreadDeterminismTest`. Open: the spreading-bush catch-up, the growing branch's geometry, the bush blocks, the Paper growth glue (placement, pick, propagation and spread placement, the lazy catch-up trigger) and the CE visuals. The divergences are in the ledger.

## GUIs & player tabs

- [ ] **M3-GUI-01 Player tabs screen** · deps: M0-GUI-01, M2-CAL-01, M3-SURV-02, M2-CLIM-03 · skills: craftengine-gui-furniture
  - TFC: `client/screen/` (player calendar/nutrition/climate screens), textures `gui/player_*.png`
  - Do: the outside-click trigger → textured GUI with calendar, nutrition and climate pages (title glyph overlays for the bars).
  - Done when: GUI alignment checklist; works on both versions.

- [ ] **M3-GUI-02 Workbench & crafting interception** · deps: M1-REG-02
  - TFC: `common/recipes/AdvancedShapedRecipe.java`, `common/recipes/AdvancedShapelessRecipe.java`, `common/container/TFCWorkbenchContainer.java`, recipes `crafting/`
  - Do: plain recipes as generated CE YAML; advanced ones (ingredient predicates, output modifiers, tool damage) via `PrepareItemCraftEvent` interception; block vanilla recipe leakage of CE items.
  - Done when: all 2912 crafting recipes resolvable; leakage test.

## Panning & sluice

- [~] **M3-PAN-01 Panning & sluice** · deps: M2-GEN-11
  - Status (@claude): in progress. Done (rules, `core/pan/`): `PanRules` (start in main hand and infinite water, loot only with a deposit, empty-pan fill, 120-tick use, 16-tick sound), `SluiceRules` (representative state, countdown that moves only when BOTH, first-filled-slot production, first-hole soil intake, 32 slots). Tests: `PanRulesTest`, `SluiceRulesTest`. Open: the deposit loot tables (datagen), the sluice block, pan and sluice CE visuals, the Paper glue (per-tick driver, gather every 20 ticks, loot roll and spawn), the lazy catch-up for a sluice and the pan stage visuals (not yet checked against TFC models).
  - TFC: `common/items/PanItem.java`, `EmptyPanItem.java`, `common/blocks/devices/SluiceBlock.java`, data `deposit/`
  - Do: pan stages via item model data, loot tables per deposit, sluice processing.
  - Done when: loot golden tests; visual stage check.

## Exit criteria
The scripted "stone-age run" self-test passes (spawn → loose rocks → knapped tools → fire → cooked food → pottery → copper casting). Ledger rows `playable`; HUD/GUI visual checklists done.
