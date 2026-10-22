# TFC feature inventory and parity status

Derived from the pinned TFC source (`/opt/tfc` @ `c3b4d9536b82e5a9746dd793c586720a74108057`: `common/blocks`, `common/items/TFCItems`, `common/blockentities`, `common/container` + `client/screen`, `common/entities/TFCEntities`, `common/recipes/TFCRecipeTypes`, `util/data/DataManagers`, `world/**`, `config/ServerConfig`, `common/commands`, `data/tfc/advancement`, `client/TFCSounds`, `client/particle/TFCParticles`, `ForgeEventHandler`, `mixin/`) and compared with this repo (core/, worldgen/, paper/, integration/, tools/datagen output in content/generated, docs/porting/ledger.md, plans/*.md, plans/gaps.md) on 2026-10-22.

Status legend: **done** (rules, glue and visuals in game), **partial** (some layers exist: usually core rules golden-tested, glue or visuals missing), **placeholder** (generated or written with stand-in blocks/visuals), **not started**, **impossible-on-vanilla** (needs a client mod; the closest planned equivalent is named).

## Features with parity work items

| area | TFC feature | status now | item |
|---|---|---|---|
| INFRA | Plugin wiring for feature code (no TFC counterpart) | not started | PAR-INFRA-01 |
| INFRA | Block entities as CE block-entity controllers (TFCBlockEntity, TickableBlockEntity, ICalendarTickable) | partial | PAR-INFRA-02 |
| INFRA | Container screens (TFCContainerScreen, BlockEntityContainer, ButtonHandlerContainer) | not started | PAR-INFRA-03 |
| INFRA | FluidComponent / ItemListComponent item components | partial | PAR-INFRA-04 |
| INFRA | Item tooltips (util/tooltip/Tooltips, ClientInventoryTooltip, tooltip style configs) | partial | PAR-INFRA-05 |
| INFRA | Recipe matching on real items (RecipeHelpers, ingredients, output modifiers) | partial | PAR-INFRA-06 |
| INFRA | TFC tags (TFCTags + data/tfc/tags) | partial | PAR-INFRA-07 |
| INFRA | Datagen of device blocks (blockstates + models of common/blocks/devices) | partial | PAR-INFRA-08 |
| INFRA | Loot tables and TFC loot functions/conditions (util/loot) | partial | PAR-INFRA-09 |
| INFRA | updateShape/neighborChanged connections (support beams, wattle, connected grass, fences) | partial | PAR-INFRA-10 |
| INFRA | Calendar catch-up of block entities (CalendarEventHandler, onCalendarUpdate) | not started | PAR-INFRA-11 |
| CLIMATE | Calendar (ServerCalendar, CalendarTransaction, sleeping, time stop when empty) | partial | PAR-CLIM-01 |
| CLIMATE | Rain vs snow by temperature (WeatherHelpers.getPrecipitationAt) | partial | PAR-CLIM-02 |
| CLIMATE | Snow & ice accumulation/melting + catch-up (WeatherHelpers.onTickChunk) | partial | PAR-CLIM-03 |
| CLIMATE | Snow pile / ice pile / icicle / sea ice blocks | placeholder | PAR-CLIM-04 |
| CLIMATE | Seasonal grass/foliage/water colours (TFCColors climate colormaps) | partial | PAR-CLIM-05 |
| CLIMATE | Seasonal biome packet rewrite cost | partial | PAR-CLIM-06 |
| CLIMATE | WeatherCommand | partial | PAR-CLIM-07 |
| CLIMATE | Lightning (ForgeEventHandler.onEntityJoinLevel lightning branch) | not started | PAR-CLIM-08 |
| CLIMATE | Thermometer / anemometer / vane / calendar clock blocks | not started | PAR-CLIM-09 |
| WORLD | Region generator maps (RegionGeneratorTests drawing) | partial | PAR-WORLD-01 |
| WORLD | ChunkGenerator/BiomeProvider glue | partial | PAR-WORLD-02 |
| WORLD | Worldgen cost | partial | PAR-WORLD-03 |
| WORLD | Soil block families (SoilBlockType, connected grass, clay grass, peat, kaolin) | partial | PAR-WORLD-04 |
| WORLD | Surface builders: ice sheets, volcanic, badlands etc. | partial | PAR-WORLD-05 |
| WORLD | Cave and canyon carvers (TFCCaveCarver, TFCCanyonCarver) | not started | PAR-WORLD-06 |
| WORLD | TFCAquifer barrier noise | partial | PAR-WORLD-07 |
| WORLD | Ore veins (disc/pipe/kaolin) | partial | PAR-WORLD-08 |
| WORLD | Groundcover / loose rock / ore deposit / small ore blocks | placeholder | PAR-WORLD-09 |
| WORLD | Wood base blocks (Wood.BlockType log/wood/leaves/sapling) | placeholder | PAR-WORLD-10 |
| WORLD | Tree features (ForestFeature, TreeHelpers, structure templates) | partial | PAR-WORLD-11 |
| WORLD | Plant blocks (Plant enum: grasses, flowers, shrubs, tall, water, floating, epiphytes, creeping) | placeholder | PAR-WORLD-12 |
| WORLD | Plant/coral features without a rule (96 ids in flora/open_gaps.txt) | partial | PAR-WORLD-13 |
| WORLD | Volcano/hot spring/spring features | partial | PAR-WORLD-14 |
| WORLD | Magma blocks, hardened rock, hot spring visuals | placeholder | PAR-WORLD-15 |
| WORLD | World settings, spawn, enableForcedTFCGameRules, enableNetherPortals | partial | PAR-WORLD-16 |
| SURV | Food items (Food enum, soups, salads, jams, preserves) | partial | PAR-SURV-01 |
| SURV | Food tooltip (FoodCapability.addTooltipInfo) | partial | PAR-SURV-02 |
| SURV | Eating (PlayerInfo.eat) | partial | PAR-SURV-03 |
| SURV | Thirst (ThirstEffect, PlayerInfo thirst, Drinkable) | partial | PAR-SURV-04 |
| SURV | Health scale, PhysicalDamage, entity/item damage resistance | partial | PAR-SURV-05 |
| SURV | Item size/weight (ItemSizeManager, Size, Weight, OVERBURDENED, EXHAUSTED) | partial | PAR-SURV-06 |
| SURV | Player inventory tabs (CalendarScreen, NutritionScreen, ClimateScreen, SwitchInventoryTabPacket) | not started | PAR-SURV-07 |
| SURV | Alcohol drinkables, intoxication (maxIntoxicationTicks) | not started | PAR-SURV-08 |
| SURV | keepNutritionAfterDeath, peaceful regeneration, natural regeneration modifier | not started | PAR-SURV-09 |
| HUD | Survival HUD bars (IngameOverlays health/food/thirst) | done | PAR-HUD-01 |
| HUD | HUD bar art | done | PAR-HUD-02 |
| HUD | Debug aid | done | PAR-HUD-03 |
| HUD | HUD screenshot checks | partial | PAR-HUD-04 |
| HUD | HUD layout vs vanilla armour/XP rows | partial | PAR-HUD-05 |
| HUD | HUD polish | partial | PAR-HUD-06 |
| HUD | HealthDisplayStyle, DisabledExperienceBarStyle, ClimateTemperatureDisplayStyle | not started | PAR-HUD-07 |
| HUD | Item temperature overlay (IngameOverlays held temperature) | partial | PAR-HUD-08 |
| HEAT | Heated item rendering (client item colour by temperature) | not started | PAR-HEAT-01 |
| HEAT | Heat and metal tooltips (HeatCapability.addTooltipInfo, Metal tooltip) | partial | PAR-HEAT-02 |
| HEAT | Hot item burn and cooling (coolHotItemEntities, ticksBeforeItemCool) | not started | PAR-HEAT-03 |
| HEAT | HeatingRecipe (item conversion at temperature) | partial | PAR-HEAT-04 |
| HEAT | Firestarter, flint & pyrite, StartFireEvent | partial | PAR-HEAT-05 |
| HEAT | Firepit (FirepitBlock, AbstractFirepitBlockEntity, FirepitBlockEntity) | partial | PAR-HEAT-06 |
| HEAT | FirepitScreen/FirepitContainer | not started | PAR-HEAT-07 |
| HEAT | Grill (GrillBlock, GrillBlockEntity, GrillScreen) | partial | PAR-HEAT-08 |
| HEAT | Pot (PotBlock, PotBlockEntity, PotScreen, pot recipes) | partial | PAR-HEAT-09 |
| HEAT | TFCTorchBlock / DeadTorchBlock (torchTicks) | partial | PAR-HEAT-10 |
| HEAT | TFCCandleBlock, TFCCandleCakeBlock, JackOLanternBlock (candleTicks, jackOLanternTicks, enablePumpkinCarving) | partial | PAR-HEAT-11 |
| HEAT | FireboxBlock, StoveBlock, StovePotBlock, FireboxScreen | not started | PAR-HEAT-12 |
| HEAT | enableFireArrowSpreading, DouseFireEvent, onFireStart/onFireStop | not started | PAR-HEAT-13 |
| GATHER | GroundcoverBlock / LooseRockBlock pickup | partial | PAR-GATHER-01 |
| GATHER | Plant/leaves/twig harvesting loot | partial | PAR-GATHER-02 |
| GATHER | AxeLoggingHelper / LoggingEvent | not started | PAR-GATHER-03 |
| GATHER | TFCLeavesBlock decay, leavesMovementModifier, plantsMovementModifier, enableLeavesDecaySlowly | not started | PAR-GATHER-04 |
| GATHER | Farmland/path/mud creation, grass spreading, grassSpawningRocksChance | not started | PAR-GATHER-05 |
| GATHER | Rock block drops, MossSpreadingBlock (enableMossyRockSpreading, mossyRockSpreadRate) | partial | PAR-GATHER-06 |
| TOOL | Stone tools (ROCK_TOOLS, RockCategory tools) | placeholder | PAR-TOOL-01 |
| TOOL | ToolItem mining rules, willConsumeDurability, forging bonus efficiency/durability | partial | PAR-TOOL-02 |
| TOOL | Metal tools and armour verification | partial | PAR-TOOL-03 |
| TOOL | Weapon attack damage (ToolItem.productAttributes, TFCMaceItem) | partial | PAR-TOOL-04 |
| TOOL | JavelinItem, ThrownJavelin, JavelinAttackGoal | partial | PAR-TOOL-05 |
| TOOL | ScytheItem/CreativeMiningTool area cut, knife uses, TFCHoeItem | partial | PAR-TOOL-06 |
| TOOL | TFCShieldItem | not started | PAR-TOOL-07 |
| TOOL | PropickItem, ProspectResult, PropickCommand | partial | PAR-TOOL-08 |
| TOOL | ChiselItem, ChiselMode, chisel recipes (513) | partial | PAR-TOOL-09 |
| TOOL | GlowArrowItem, GlowArrow | not started | PAR-TOOL-10 |
| POTTERY | PlacedItemBlock, PlacedItemBlockEntity, PlaceBlockSpecialPacket (enablePlacingItems, maxPlacedItemSize) | not started | PAR-POT-01 |
| POTTERY | Ceramic items (UNFIRED_* and fired: vessel, jug, pot, bowl, crucible, molds, bricks, large vessel, glazed variants) | not started | PAR-POT-02 |
| POTTERY | PitKilnBlock, PitKilnBlockEntity (pitKilnTicks, pitKilnTemperature) | partial | PAR-POT-03 |
| POTTERY | VesselItem, SmallVesselInventoryContainer (smallVesselCapacity, smallVesselMaximumItemSize) | partial | PAR-POT-04 |
| POTTERY | MoldItem, Mold, MoldLikeAlloyContainer, casting recipes (111) | partial | PAR-POT-05 |
| POTTERY | LargeVesselBlock, LargeVesselBlockEntity (largeVesselEnableAutomation, redstone seal) | partial | PAR-POT-06 |
| POTTERY | JugItem, GlassBottleItem, FluidContainerItem | partial | PAR-POT-07 |
| POTTERY | DryingBricksBlock (mudBricksTicks), ThatchBlock (thatchMovementMultiplier) | partial | PAR-POT-08 |
| POTTERY | BowlBlock, BowlBlockEntity (powderBowlEnableAutomation) | not started | PAR-POT-09 |
| PROC | KnappingContainer/KnappingScreen, knapping recipes (101), knapping types (5) | partial | PAR-PROC-01 |
| PROC | Crafting recipes (minecraft:crafting_shaped/shapeless in data/tfc/recipe/crafting) | not started | PAR-PROC-02 |
| PROC | AdvancedShaped/ShapelessRecipe, FoodCombiningCraftingRecipe, CastingCraftingRecipe, TFCWorkbenchContainer | partial | PAR-PROC-03 |
| PROC | QuernBlock, QuernBlockEntity, quern recipes (89) | partial | PAR-PROC-04 |
| PROC | ScrapingBlock, ScrapingBlockEntity (7 recipes) | partial | PAR-PROC-05 |
| PROC | TFCLoomBlock, LoomBlockEntity (5 recipes) | partial | PAR-PROC-06 |
| PROC | SewingTableBlock, SewingTableContainer, SewingTableScreen (25 recipes) | partial | PAR-PROC-07 |
| PROC | BarrelBlock, BarrelBlockEntity, barrel recipes (309 sealed + instant + instant fluid) | partial | PAR-PROC-08 |
| PROC | BarrelScreen/BarrelContainer, barrel fluid rendering, BarrelRackBlock, rain filling | not started | PAR-PROC-09 |
| PROC | LogPileBlock, BurningLogPileBlock, CharcoalPileBlock (charcoalTicks) | partial | PAR-PROC-10 |
| PROC | TFCComposterBlock, ComposterBlockEntity (composterTicks) | partial | PAR-PROC-11 |
| PROC | EmptyPanItem, PanItem, deposits loot | partial | PAR-PROC-12 |
| PROC | SluiceBlock, SluiceBlockEntity (sluiceTicks) | partial | PAR-PROC-13 |
| PROC | SaladContainer/SaladScreen | not started | PAR-PROC-14 |
| PROC | ScribingTableBlock, ScribingTableContainer/Screen, ScribingTablePacket | not started | PAR-PROC-15 |
| PROC | BlowpipeItem, GlassBlowpipeItem, GlassworkingItem, GlassWorking, glassworking recipes (60) | partial | PAR-PROC-16 |
| PROC | GlassBasinBlock, HotPouredGlassBlock, PouredGlassBlock, glass batch/bottle/lens items | partial | PAR-PROC-17 |
| PROC | PowderkegBlock, PowderkegBlockEntity, PowderkegScreen, PowderKegExplosion | not started | PAR-PROC-18 |
| PROC | Material items (ORES, GRADED_ORES, GEMS, POWDERS, ORE_POWDERS, jute/papyrus/cloth/glue/soot/mortar/..., spawn eggs, GemSawItem) | partial | PAR-PROC-19 |
| METAL | FluidAlloy, AlloyRange, alloy recipes (9) | partial | PAR-METAL-01 |
| METAL | Fire ingot mold, bell mold, CastingCraftingRecipe | partial | PAR-METAL-02 |
| METAL | AnvilBlock, RockAnvilBlock, AnvilBlockEntity (inventory, tiers) | placeholder | PAR-METAL-03 |
| METAL | AnvilPlanContainer/AnvilPlanScreen, SelectAnvilPlanPacket | not started | PAR-METAL-04 |
| METAL | AnvilContainer/AnvilScreen, Forging, ForgingBonus | partial | PAR-METAL-05 |
| METAL | WeldingRecipe (87) | partial | PAR-METAL-06 |
| METAL | Charcoal forge visuals | done | PAR-METAL-07 |
| METAL | CharcoalForgeBlock, CharcoalForgeBlockEntity | partial | PAR-METAL-08 |
| METAL | CharcoalForgeScreen/Container, charcoal_forge_insulation tag, sky check | not started | PAR-METAL-09 |
| METAL | Charcoal forge visual check | partial | PAR-METAL-10 |
| METAL | BellowsBlock, BellowsBlockEntity | partial | PAR-METAL-11 |
| METAL | CrucibleBlock, CrucibleBlockEntity, CrucibleScreen (crucibleCapacity, pouring rates) | partial | PAR-METAL-12 |
| METAL | ChannelBlock, MoldTableBlock, ChannelFlow, PourFasterPacket | partial | PAR-METAL-13 |
| METAL | BloomeryBlock, BloomeryBlockEntity, BloomBlock, BloomBlockEntity (bloomeryCapacity, bloomeryMaxChimneyHeight) | partial | PAR-METAL-14 |
| METAL | BlastFurnaceBlock, BlastFurnaceBlockEntity, BlastFurnaceScreen, tuyere | partial | PAR-METAL-15 |
| METAL | IngotPileBlock, DoubleIngotPileBlock, IngotPileBlockEntity | partial | PAR-METAL-16 |
| METAL | WeatheringBlock, WeatheringGrateBlock, IWeatheringBlock | partial | PAR-METAL-17 |
| METAL | TFCBellBlock, TFCBellBlockEntity | not started | PAR-METAL-18 |
| METAL | LampBlock, LampBlockEntity, LampBlockItem, lamp_fuel (lampCapacity) | partial | PAR-METAL-19 |
| PHYS | TFCFallingBlockEntity, IFallableBlock, FallingBlockMixin | not started | PAR-PHYS-01 |
| PHYS | Collapse, WorldTracker, CollapseRecipe, support data (enableBlockCollapsing) | partial | PAR-PHYS-02 |
| PHYS | LandslideRecipe, can_landslide (enableBlockLandslides) | partial | PAR-PHYS-03 |
| PHYS | VerticalSupportBlock, HorizontalSupportBlock (support_beams) | partial | PAR-PHYS-04 |
| PHYS | enableExplosionCollapsing, collapseExplosionPropagateChance | not started | PAR-PHYS-05 |
| FAUNA | Entity models (44 bbmodels, 128 AnimationDefinitions) | not started | PAR-FAUNA-01 |
| FAUNA | Java-only entity models (createBodyLayer) | not started | PAR-FAUNA-02 |
| FAUNA | Entity texture variants (GenderedRenderAnimal, old textures) | not started | PAR-FAUNA-03 |
| FAUNA | Fauna, Faunas, spawn placements (SpawnPlacementsMixin) | partial | PAR-FAUNA-04 |
| FAUNA | TFCAnimal, Mammal, OviparousAnimal (familiarity, feeding, mating, gestation) | partial | PAR-FAUNA-05 |
| FAUNA | DairyAnimal, WoolyAnimal, ProducingAnimal, EggComponent, AnimalProductEvent | partial | PAR-FAUNA-06 |
| FAUNA | NestBoxBlock, NestBoxBlockEntity, NestBoxScreen | partial | PAR-FAUNA-07 |
| FAUNA | Livestock AI (ai/livestock), livestock roster | partial | PAR-FAUNA-08 |
| FAUNA | Prey, WildAnimal, WingedPrey, RammingPrey, TFCRabbit/Fox/Armadillo/Panda/Frog, ai/prey | partial | PAR-FAUNA-09 |
| FAUNA | Predator, FelinePredator, AmphibiousPredator, ai/predator (PackPredatorAi) | partial | PAR-FAUNA-10 |
| FAUNA | aquatic (Fish, FreshwaterFish, TFCSquid, Jellyfish, Octopoteuthis...), fish buckets | partial | PAR-FAUNA-11 |
| FAUNA | AmphibiousAnimal, TrueAmphibiousMoveControl, ai/amphibian | partial | PAR-FAUNA-12 |
| FAUNA | Pest, PestContainer, AquaticCritter (enableInfestations) | partial | PAR-FAUNA-13 |
| FAUNA | Dog, TFCCat, TamableMammal, PetCommandScreen, PetCommandPacket | partial | PAR-FAUNA-14 |
| FAUNA | TFCHorse, TFCDonkey, TFCMule, TFCChestedHorse, HorseProperties | partial | PAR-FAUNA-15 |
| FAUNA | AbstractCamel, BactrianCamel, DromedaryCamel | partial | PAR-FAUNA-16 |
| FAUNA | AnimalYieldProvider, HideItemType, meat items | partial | PAR-FAUNA-17 |
| FAUNA | TFCFishingRodItem, TFCFishingHook, Bait, GetHookedGoal | partial | PAR-FAUNA-18 |
| FAUNA | LobsterTrapBlock, LobsterTrapBlockEntity, EnterLobsterTrapGoal | partial | PAR-FAUNA-19 |
| FAUNA | Pluckable, TFCSounds entity sounds | not started | PAR-FAUNA-20 |
| FAUNA | TFCAnimal common data, EntityHelpers, BrainAnimalBehavior | partial | PAR-FAUNA-21 |
| DECO | Wood decoration blocks (Wood.BlockType: planks, stairs, slab, fence, fence gate, door, trapdoor, button, pressure plate...) | placeholder | PAR-DECO-01 |
| DECO | TFCDoorBlock, TFCTrapDoorBlock, TFCFenceGateBlock, TFCWoodButtonBlock, TFCPressurePlateBlock | not started | PAR-DECO-02 |
| DECO | TFCStandingSignBlock, TFCWallSignBlock, TFCCeilingHangingSignBlock, TFCWallHangingSignBlock | not started | PAR-DECO-03 |
| DECO | TFCChestBlock, TFCTrappedChestBlock, RestrictedChestContainer (chestMaximumItemSize) | not started | PAR-DECO-04 |
| DECO | ToolRackBlock, ShelfBlock, BookshelfBlock, TFCLecternBlock (+ block entities) | not started | PAR-DECO-05 |
| DECO | Rock.BlockType decorations, AqueductBlock, RockSpikeBlock | placeholder | PAR-DECO-06 |
| DECO | Mud brick families, alabaster (raw/bricks/polished, 16 dyes), aggregate, fire bricks, WattleBlock, StainedWattleBlock | placeholder | PAR-DECO-07 |
| DECO | SANDSTONE, SANDSTONE_DECORATIONS, HARDENED_CLAY, HALITE, LIGNITE, BITUMINOUS_COAL, CALCITE | partial | PAR-DECO-08 |
| DECO | Metal.BlockType blocks (block, slab, stairs, bars, chain, grate, trapdoor, weathered ages) | placeholder | PAR-DECO-09 |
| DECO | AbstractRopeBlock, HangingRopeBlock, GroundedRopeBlock, RopeAnchorBlock, RopeItem, RopeKnot | not started | PAR-DECO-10 |
| DECO | TFCBoat, TFCChestBoat, TFCBoatItem, TFCMinecartChest, HoldingMinecart, TFCMinecartItem | not started | PAR-DECO-11 |
| DECO | CrateBlock, BuoyBlock, TFCCakeBlock, GOLDEN_BAMBOO, PALM_MOSAIC, TreeRootsBlock, TFCLightBlock | not started | PAR-DECO-12 |
| DECO | POTTED_PLANTS, KrummholzBlock (5 species) | not started | PAR-DECO-13 |
| DECO | ThatchBedBlock, ThatchBedBlockEntity (enableThatchBedSleeping, enableThatchBedSpawnSetting) | not started | PAR-DECO-14 |
| DECO | Visual state budget | partial | PAR-DECO-15 |
| FLUID | TFCFluids (metal, simple, colored fluids, salt/spring/river water), FluidHeat | partial | PAR-FLUID-01 |
| FLUID | WOODEN_BUCKET, RED_STEEL_BUCKET, BLUE_STEEL_BUCKET, FluidContainerItem, woodenBucketCanPlaceSources, metalBucketCanPlaceSources | not started | PAR-FLUID-02 |
| FLUID | Salt water, spring water, river water fluids (RiverWaterBlock flow push) | impossible-on-vanilla | PAR-FLUID-03 |
| FLUID | HotWaterBlock (hotWaterHealAmount), TFCBubbleColumnBlock (bubbleColumnProvidesAir) | not started | PAR-FLUID-04 |
| MECH | AxleBlock, EncasedAxleBlock, GearBoxBlock, ClutchBlock, CreativeRotationBlock, RotationNetworkManager | partial | PAR-MECH-01 |
| MECH | Rotating block renderers | not started | PAR-MECH-02 |
| MECH | WaterWheelBlock, WaterWheelBlockEntity | partial | PAR-MECH-03 |
| MECH | WindmillBlock, WindmillBlockEntity, WindmillBladeItem | partial | PAR-MECH-04 |
| MECH | CrankshaftBlock, CrankshaftBlockEntity | partial | PAR-MECH-05 |
| MECH | RotationSinkBlockEntity on quern (quernEnableAutomation) | partial | PAR-MECH-06 |
| MECH | TripHammerBlock, TripHammerBlockEntity | partial | PAR-MECH-07 |
| MECH | PowerLoomBlock, PowerLoomBlockEntity | partial | PAR-MECH-08 |
| MECH | FluidPumpBlock, FluidPipeBlock, PumpBlockEntity, HorizontalPipeBlock | partial | PAR-MECH-09 |
| MECH | BladedAxleBlock, BladedAxleBlockEntity | not started | PAR-MECH-10 |
| FARM | FarmlandBlock, FarmlandBlockEntity, IFarmland, HoeOverlays | partial | PAR-FARM-01 |
| FARM | Fertilizer data (pure N/P/K, compost, guano...) | partial | PAR-FARM-02 |
| FARM | Crop blocks (Default/Double/Climbing/Flooded/Spreading/Pickable, dead, wild), SeedItem | not started | PAR-FARM-03 |
| FARM | CropBlockEntity, CropHelpers.growthTick, IPickableCrop, cropGrowthModifier, cropExpiryModifier | partial | PAR-FARM-04 |
| FARM | WildCropBlock, SpreadingCropBlock, TFCPumpkinBlock, ROTTEN_PUMPKIN (wildCropMaturity*) | partial | PAR-FARM-05 |
| FARM | StationaryBerryBushBlock, BerryBushBlockEntity, Lifecycle (berryBushGrowthTicks) | partial | PAR-FARM-06 |
| FARM | SpreadingBushBlock, SpreadingCaneBlock, WaterloggedBerryBushBlock, DeadCaneBlock | partial | PAR-FARM-07 |
| FARM | FruitTreeSaplingBlock, GrowingFruitTreeBranchBlock, FruitTreeBranchBlock, FruitTreeLeavesBlock (fruitBranchGrowthTicks) | partial | PAR-FARM-08 |
| FARM | BananaPlantBlock, BananaSaplingBlock, DeadBananaPlantBlock (banana*GrowthTicks) | not started | PAR-FARM-09 |
| FARM | TFCSaplingBlock growth (saplingGrowthModifier), tree structures | not started | PAR-FARM-10 |
| FARM | PlantRegrowth, plantGrowthChance, plantSpreadChance, FlowerCuttingItem | not started | PAR-FARM-11 |
| DATA | TFCCommands (PlayerCommand, CountBlockCommand, ClearWorldCommand, AddTrimCommand) | partial | PAR-DATA-01 |
| DATA | TFC advancements (115) and triggers (BlockActionTrigger, EntityActionTrigger, GenericTrigger) | partial | PAR-DATA-02 |
| DATA | Advancement criteria mapping | not started | PAR-DATA-03 |
| DATA | Patchouli field guide (7 categories, 81 entries) | partial | PAR-DATA-04 |
| DATA | Lang files (13 locales) | partial | PAR-DATA-05 |
| DATA | TFCConfig (ServerConfig ~150 keys, CommonConfig, animal configs) | partial | PAR-DATA-06 |
| DATA | TFCDamageTypes, TFCEffects (thirst, exhausted, overburdened, ink, glow ink) | partial | PAR-DATA-07 |
| DATA | TFCSounds (block, item, entity, music, random) | not started | PAR-DATA-08 |
| DATA | TFCParticles (23) | not started | PAR-DATA-09 |
| DATA | DispenserBehaviors, enableVanilla* configs, onBoneMeal, onLivingSpawnCheck, MilkBucketItemMixin | not started | PAR-DATA-10 |
| DATA | painting_variant, trim_material | not started | PAR-DATA-11 |
| DATA | BlockEntityTooltips, EntityTooltips (Jade/TOP compat) | impossible-on-vanilla | PAR-DATA-12 |
| DATA | TFC update tracking | partial | PAR-DATA-13 |
| REL | Test harness | partial | PAR-REL-01 |
| REL | Performance and threading gates | partial | PAR-REL-02 |
| REL | Compatibility matrix | partial | PAR-REL-03 |
| REL | Stone-age playthrough | not started | PAR-REL-04 |
| REL | Metal-age playthrough | partial | PAR-REL-05 |
| REL | Husbandry playthrough | not started | PAR-REL-06 |
| REL | Performance audit | not started | PAR-REL-07 |
| REL | QA | not started | PAR-REL-08 |
| REL | Release | not started | PAR-REL-09 |
| REL | 2D art not in TFC (176 assets in docs/art/image-list.md) | partial | PAR-REL-10 |
| REL | Docs | partial | PAR-REL-11 |

## Features already at parity (no open item)

| area | TFC feature | status | evidence |
|---|---|---|---|
| CLIMATE | Calendar core, `/tfp time`, persistence, HUD date | done | ledger 'Calendar' playable; `calendar.persist` self-test (follow-ups in PAR-CLIM-01) |
| CLIMATE | Climate model (OverworldClimateModel, Koppen, solar) | done (rules)  | ClimateGoldenTest, SolarGoldenTest; HUD readout = `/tfp debug chunk` |
| CLIMATE | Climate ranges (49 files) | done (rules) | ClimateRangeTest; consumers wire it in their items |
| WORLD | Noise library, layer framework, region generator rules, rivers, chunk data, biome heights and density, surface rules, aquifer decision, cluster ore veins, groundcover/deposit rules, tree/plant/coral rules, calderas and hot springs, spawn search | done (rules + glue, golden vs TFC) | ledger 'World generation' rows; open parts are PAR-WORLD-* |
| WORLD | Rock, ore (graded), soil, sand full-cube CE blocks | done | content/generated/.../configuration/{rock,ore,soil}, ledger 'Rocks, ores, soils' playable |
| INFRA | Plugin skeleton, installer of CE/BM/BH content, scheduler abstraction, AsyncDecisionPipeline, chunk data store, packet layer, registries and datagen v1, self-test harness | done | plans/00-m0-spikes.md and 01-m1-skeleton.md (all ticked) |
| SURV | Food decay rules, item PDC codec, food_stamp processor, stack merging | done (rules + glue) | FoodDecayGoldenTest, FoodMergeListenerMockBukkitTest |
| SURV | Nutrition rules and eating record, rotten effects, vanilla food sync | done (rules + glue) | NutritionGoldenTest, NutritionEatingTest; in-game eat of tfp:food/mutton |
| SURV | Thirst decay (lazy) and drinking from water with a bed behind | done (rules + glue) | ThirstGoldenTest, WaterDrinkTest; in-game lake2 run |
| HUD | Health/thirst/hunger/air bars, date and temperature readouts, vanilla hearts/food/air hidden | done (first pass) | plans/gaps.md M3-SURV-06 entries; two-layer redesign = PAR-HUD-01..08 |
| HEAT | Item heat rules, PDC codec, tooltip line, lazy cooling, `tfp debug heat` and `warm`, forge click heating | done (rules + glue) | HeatGoldenTest, in-game runs heat/cool/tip/warm/catch4 |
| TOOL | Metal pickaxe/axe/shovel/hoe/knife/scythe/hammer items, armour and horse armour items | done (data), verification open | metal.yml, ArmorGenerationTest; verification PAR-TOOL-03 |
| METAL | 27 metals table, 230 plain metal items with names | done (data) | MetalTableTest, metal.yml, PinnedCheckoutTest names |
| METAL | Rules of alloys, casting, anvil/forging/welding, forge, crucible, channel flow, bloomery, blast furnace, ingot piles, weathering, tools/armour stats | done (rules) | core goldens listed in plans/04-m4-metal-age.md |
| FAUNA | Rules of livestock, products, nest lay, spawn, prey flee, predators, aquatic, pests, pets, mounts, hunt drops, fishing | done (rules) | core goldens listed in plans/05-m5-husbandry.md |
| MECH | Rotation network solver, water wheel/windmill/wind, machine rules | done (rules) | RotationGoldenTest, RotationNetworkManagerPortTest, machine goldens |
| DATA | Advancement tree data (115), field guide data (81 entries), locales (13, 10075 keys), TFC pin drift tool | done (data) | AdvancementsTest, FieldGuideTest, LocalesTest, TfcPinDriftTest |

## Impossible on vanilla clients (closest planned equivalent)

| TFC feature | why impossible | closest equivalent | item |
|---|---|---|---|
| Custom world fluids (salt, spring, river water, molten metal, other TFC fluids as blocks) | vanilla clients only render vanilla fluids | vanilla water/lava + biome-based salt-ness + server-side river current; non-vanilla fluids only in containers | PAR-FLUID-03 |
| Continuous client-side climate colours (TFCColors per block per frame) | biome colour is client-side | datapack seasonal biome variants + async chunk biome remap (locked decision) | PAR-CLIM-05 |
| Sky, stars and solar rendering (`client/overworld`, stars.json) | client rendering | none (vanilla sky); recorded divergence | PAR-CLIM-05 (ledger row) |
| Inventory tab buttons inside the player screen | client screen | outside-click trigger + pack-drawn button (locked decision) | PAR-SURV-07 |
| Key bindings: place item (V), chisel mode cycle, stack food, pour faster | no client key events | sneak+right-click, sneak+swap-hand, automatic stacking, sneak while pouring | PAR-POT-01, PAR-TOOL-09, PAR-METAL-13 |
| Glassblowing overlays, hoe overlays, crate overlays, ghost block previews | client HUD/world overlays | actionbar text on use or sneak-look | PAR-PROC-16, PAR-FARM-01, PAR-DECO-12, PAR-DATA-12 |
| Jade / The One Probe / JEI / EMI / Patchouli / Vivecraft compat | client mods | actionbar look-info; field guide as books (decision pending) | PAR-DATA-12, PAR-DATA-04 |
| Ambient particles (butterflies, moths, wind, falling leaves, snowflakes) | would need constant packets; policy forbids ambient particles | dropped; action particles only | PAR-DATA-09 |
| Per-frame item heat colour and tooltip refresh | client-side tooltip/item rendering | level resolved when the item packet is sent (glow + tooltip) | PAR-HEAT-01, PAR-HEAT-02 |
| Custom mob effect icons (thirst, exhausted, overburdened) | client effect registry | vanilla effect carriers + HUD indicators | PAR-DATA-07 |
| Client config styles (health display, temperature display, food expiry tooltip style, XP bar style) | per-player client config | server-wide config equivalents | PAR-HUD-07, PAR-INFRA-05 |
| Create TFC world screen (CreateTFCWorldScreen) | client screen | bukkit.yml generator + config | PAR-WORLD-16 |

Counts over the item rows: done 4, impossible-on-vanilla 2, not started 64, partial 138, placeholder 11.
