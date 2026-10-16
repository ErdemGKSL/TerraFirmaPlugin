# M3: Stone age

Goal: the full TFC early game. A player spawns, gathers loose rocks and sticks, knaps stone tools, survives on food and water with nutrition and decay, makes fire, cooks, makes pottery in a pit kiln, farms, makes charcoal, and finally melts native copper in a vessel and casts the first copper tool. Exit: that whole path is playable on Paper and Folia, with HUD and GUIs.

Paths are relative to `src/main/java/net/dries007/tfc/` unless noted. Every task follows the Definition of Done in `plans/README.md`.

## Player survival

- [ ] **M3-SURV-01 Food component & decay** · deps: M1-REG-01, M2-CAL-01 · skills: tfc-feature-porting
  - TFC: `common/component/food/` (`FoodComponent`, `FoodCapability`, `FoodData`, `FoodTrait(s)`), data `food/`
  - Do: item PDC codec (creation date + traits), lazy rot evaluation, decay modifiers, trait multipliers (salted, brined, pickled, smoked, preserved, ...), stack merging with rounded creation dates (TFC rounding), the `tfp:food_stamp` CE item processor.
  - Done when: golden tests (rot date for given foods/traits), stacking tests, lazy-vs-stepped test.

- [ ] **M3-SURV-02 Nutrition & eating** · deps: M3-SURV-01, M1-SVC-02
  - TFC: `common/component/food/NutritionData.java`, `Nutrient.java`, `common/player/PlayerInfo.java`
  - Do: 5 nutrient groups, eating rules (the TFC hunger/saturation model replaces vanilla food), the max-health bonus from nutrition.
  - Done when: golden tests vs TFC eating sequences.

- [ ] **M3-SURV-03 Thirst & drinking** · deps: M3-SURV-02
  - TFC: `common/player/PlayerInfo.java` (thirst), data `drinkable/`, `network/PlayerDrinkPacket.java`, `common/effect/ThirstEffect.java`
  - Do: thirst decay by temperature/activity; drinking from water source blocks by right-clicking with an empty hand (the vanilla trigger replacing the packet), salt water effects, jugs; thirst effects.
  - Done when: golden tests; self-test drinking.

- [ ] **M3-SURV-04 TFC health scale & damage types** · deps: M1-SVC-02
  - TFC: `util/PhysicalDamage(Type).java`, data `entity_damage_resistance/`, `item_damage_resistance/`
  - Do: the TFC max health model (displayed "45/50" via HUD), crush/pierce/slash resistances, vanilla damage mapping.
  - Done when: golden damage tests.

- [ ] **M3-SURV-05 Item size & weight** · deps: M1-REG-01
  - TFC: `common/component/size/`, data `item_size/`
  - Do: inventory/container restrictions (e.g. large items not in small vessels), overburdened effect from heavy items, tooltip lines.
  - Done when: rules tested; self-test inventory restrictions.

- [ ] **M3-SURV-06 Survival HUD** · deps: M3-SURV-02, M3-SURV-03, M3-SURV-04, M1-INT-04 · skills: betterhud, image-generation
  - Do: BetterHud layouts: health bar + "45/50", hunger, thirst, held-item temperature popup (M3-HEAT-01). Hide vanilla hearts/food/air. Art from TFC `textures/gui/icons*`; extra art via `image-generation`.
  - Done when: visual checklist at GUI scales 1–4 on 1.21.11 and 26.3.

## Heat & fire

- [ ] **M3-HEAT-01 Item heat** · deps: M1-REG-01, M2-CAL-01, M1-SVC-03 · skills: tfc-feature-porting, craftengine-visuals-pack
  - TFC: `common/component/heat/` (`HeatComponent`, `HeatCapability`), data `item_heat/`; test `HeatingItemTest`
  - Do: lazy heat (`lastTemperature` + `lastTick`), heat transfer to containers. Tooltip line + heat glow via `custom_model_data` floats in outgoing packets (`range_dispatch` item models). Burn damage on holding very hot items.
  - Done when: ported `HeatingItemTest` + golden cooling curves; glow visual check.

- [ ] **M3-HEAT-02 Fuels & fire starting** · deps: M3-HEAT-01
  - TFC: data `fuel/`, `common/items/FirestarterItem.java`, `FlintAndPyriteItem.java`, `util/events/StartFireEvent.java`
  - Do: firestarter use-duration via Paper events (CE item behaviours have no use-duration hook); fire starting rules.
  - Done when: self-test lighting a firepit.

- [ ] **M3-HEAT-03 Firepit, grill, pot** · deps: M3-HEAT-02, M1-SVC-01 · skills: craftengine-java-api, craftengine-gui-furniture
  - TFC: `common/blockentities/{AbstractFirepit,Firepit,Grill,Pot}BlockEntity.java`, `common/blocks/devices/FirepitBlock.java`, recipes `pot/`, `heating/`
  - Do: CE block + controller (fuel, temperature as a lazy curve, items), GUIs (TFC textures `gui/fire_pit*.png`), cooking via heating recipes, pot soups/jams (`tfc:pot`, `tfc:pot_soup`, `tfc:pot_jam`). Visible logs/flame via properties + an animated texture; smoke particles only while lit and interacted with (particle policy).
  - Done when: golden cooking times; self-tests; visual checklist.

- [ ] **M3-HEAT-04 Torches, candles, lamps (basic)** · deps: M3-HEAT-02
  - TFC: `common/blocks/TFCTorchBlock.java`, `DeadTorchBlock.java`, `TFCCandleBlock.java`
  - Do: torch burnout from the placement timestamp (lazy, checked on chunk load/interaction via the async pipeline) → dead torch; relighting.
  - Done when: lazy-vs-stepped test.

## Gathering & knapping

- [ ] **M3-GATH-01 Groundcover & loose rocks pickup** · deps: M2-GEN-11 · skills: craftengine-blocks
  - TFC: `common/blocks/GroundcoverBlock.java`, `common/blocks/rock/LooseRockBlock.java`
  - Do: a `tfp:pickupable` behaviour (right-click → item into hand), button-state visuals.
  - Done when: self-test pickup; no drop issues with the wrong tool.

- [ ] **M3-GATH-02 Sticks, twigs, fallen leaves, plants harvesting** · deps: M2-GEN-12, M2-GEN-13
  - TFC: `common/blocks/wood/FallenLeavesBlock.java`, `common/blocks/plant/*`
  - Done when: TFC drop tables reproduced (loot golden tests).

- [ ] **M3-KNAP-01 Knapping GUI & recipes** · deps: M1-REG-02, M0-GUI-01 · skills: craftengine-gui-furniture, tfc-feature-porting
  - TFC: `common/container/KnappingContainer.java`, `util/data/KnappingPattern.java`, `KnappingType.java`, data `knapping_type/`, recipes `knapping/`; test `KnappingPatternTest`
  - Do: textured 6-row chest GUI (5×5 grid in columns 2–6), types stone/clay/fire clay/leather/goat horn, consumption rules.
  - Done when: ported `KnappingPatternTest`; all 101 knapping recipes resolvable; GUI aligned.

- [ ] **M3-TOOL-01 Stone tools & tool mechanics** · deps: M3-KNAP-01 · skills: craftengine-items
  - TFC: `common/items/ToolItem.java`, `JavelinItem.java`, `HammerItem.java`, `TFCHoeItem.java`, `ScytheItem.java`, `util/Metal.java` (tiers)
  - Do: CE items with `break_power` tiers + correct-tool tags, the vanilla `tool` component speeds, javelin throw (trident material), knife/scythe area behaviours.
  - Done when: mining tiers match TFC; self-test.

## Rock & soil physics

- [ ] **M3-PHYS-01 Collapse** · deps: M1-THR-03, M2-GEN-07 · skills: paper-folia-threading, tfc-feature-porting
  - TFC: `util/tracker/Collapse.java`, `WorldTracker.java`, `common/recipes/CollapseRecipe.java`, data `support/`, tags `can_collapse`, `can_start_collapse`, `can_trigger_collapse`
  - Do: mining triggers an async collapse decision (support beams radius, chance rules) → budgeted commits spawning falling blocks; collapse recipes (raw → cobble).
  - Done when: golden tests on scripted scenarios; Folia stress self-test.

- [ ] **M3-PHYS-02 Landslide & gravity soils** · deps: M3-PHYS-01
  - TFC: `common/recipes/LandslideRecipe.java`, tag `can_landslide`
  - Do: vertical falling (CE `falling_block` or ours) + sideways sliding decided async.
  - Done when: golden scenarios.

- [ ] **M3-PHYS-03 Support beams** · deps: M3-PHYS-01 · skills: craftengine-blocks
  - TFC: `common/blocks/wood/{Vertical,Horizontal}SupportBlock.java`
  - Do: connection states via the async decision pipeline; renderer visuals.
  - Done when: connection self-test (place/break chains), collapse prevention.

## Pottery & containers

- [ ] **M3-POT-01 Pit kiln** · deps: M3-KNAP-01, M3-HEAT-02 · skills: craftengine-java-api
  - TFC: `common/blockentities/PitKilnBlockEntity.java`, `common/blocks/devices/PitKilnBlock.java`
  - Do: placed items + straw + logs stages, firing lazily from the calendar, results.
  - Done when: golden firing time; self-test.

- [ ] **M3-POT-02 Molds & small vessel** · deps: M3-POT-01, M3-HEAT-01
  - TFC: `common/component/mold/`, `common/items/MoldItem.java`, `VesselItem.java`, `common/container/SmallVesselInventoryContainer.java`; test `MoldTest`
  - Do: vessel inventory, melting contents in fire to molten metal (fluid component), pouring into molds (casting recipes), mold fill visuals (item model `range_dispatch`).
  - Done when: ported `MoldTest`; native copper → copper tool head path works.

- [ ] **M3-POT-03 Large vessel & jugs** · deps: M3-POT-01
  - TFC: `common/blockentities/LargeVesselBlockEntity.java`, `common/items/JugItem.java`
  - Done when: self-test sealing and preservation.

- [ ] **M3-POT-04 Drying bricks, mud bricks, thatch** · deps: M2-CAL-01
  - TFC: `common/blocks/devices/DryingBricksBlock.java`, `ThatchBlock.java`
  - Done when: lazy drying test.

## Processing

- [ ] **M3-PROC-01 Quern** · deps: M1-SVC-01 · skills: craftengine-gui-furniture
  - TFC: `common/blockentities/QuernBlockEntity.java`, recipes `quern/`
  - Do: handstone interaction; spinning visual (`better_model` element or interpolated element per M0); grinding recipes.
  - Done when: all 89 quern recipes work; visual checklist.

- [ ] **M3-PROC-02 Scraping, sewing, loom (manual)** · deps: M1-SVC-01
  - TFC: `common/blockentities/ScrapingBlockEntity.java`, `LoomBlockEntity.java`, `common/container/SewingTableContainer.java`, recipes `scraping/`, `sewing/`, `loom/`
  - Done when: recipes work; GUIs aligned.

- [ ] **M3-PROC-03 Barrel** · deps: M3-SURV-01, M1-SVC-01
  - TFC: `common/blockentities/BarrelBlockEntity.java`, `common/component/block/Barrel*.java`, recipes `barrel/` (`barrel_sealed`, `barrel_instant`, `barrel_instant_fluid`)
  - Do: fluid storage, sealing with a lazy recipe completion timestamp, preservation traits, alcohol; fluid level visual via tint_source.
  - Done when: golden sealed-recipe durations; all 331 barrel recipes load.

- [ ] **M3-PROC-04 Log pile & charcoal pit** · deps: M3-HEAT-02, M3-PHYS-01
  - TFC: `common/blockentities/{LogPile,BurningLogPile}BlockEntity.java`, `common/blocks/CharcoalPileBlock.java`
  - Do: multiblock validation (insulation check) via the async pipeline, lazy burn, charcoal pile layers.
  - Done when: golden yields; self-test.

- [ ] **M3-PROC-05 Composter** · deps: M3-FARM-01
  - TFC: `common/blockentities/ComposterBlockEntity.java`
  - Done when: lazy composting test.

## Farming

- [ ] **M3-FARM-01 Farmland, hydration & nutrients** · deps: M1-SVC-01, M2-CLIM-01
  - TFC: `common/blocks/soil/FarmlandBlock.java`, `common/blockentities/FarmlandBlockEntity.java`, data `fertilizer/`
  - Do: NPK storage, hydration from nearby water (async decided), fertilizers.
  - Done when: golden nutrient tests.

- [ ] **M3-FARM-02 Crops** · deps: M3-FARM-01, M2-CLIM-02
  - TFC: `common/blocks/crop/` (`Crop`, `CropBlock`, `DefaultCropBlock`, `DoubleCropBlock`, `ClimbingCropBlock`, `FloodedCropBlock`, `SpreadingCropBlock`, dead/wild), `common/blockentities/CropBlockEntity.java`, `CropHelpers.java`
  - Do: calendar-driven growth with climate checks, catch-up on load/interaction (no tickers), death conditions, yields by nutrients, all 29 crops, seeds via `block_item`.
  - Done when: golden growth tests per crop type; lazy-vs-stepped tests.

- [ ] **M3-FARM-03 Berry bushes & seasonal plants** · deps: M2-GEN-13, M2-CAL-01
  - TFC: `common/blocks/plant/fruit/`, `common/blockentities/BerryBushBlockEntity.java`, `PlantRegrowth.java`
  - Do: lifecycle stages by season (dormant/healthy/flowering/fruiting) as properties updated lazily (async sweep on season change + load), harvest.
  - Done when: lifecycle golden tests.

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

- [ ] **M3-PAN-01 Panning & sluice** · deps: M2-GEN-11
  - TFC: `common/items/PanItem.java`, `EmptyPanItem.java`, `common/blocks/devices/SluiceBlock.java`, data `deposit/`
  - Do: pan stages via item model data, loot tables per deposit, sluice processing.
  - Done when: loot golden tests; visual stage check.

## Exit criteria
The scripted "stone-age run" self-test passes (spawn → loose rocks → knapped tools → fire → cooked food → pottery → copper casting). Ledger rows `playable`; HUD/GUI visual checklists done.
