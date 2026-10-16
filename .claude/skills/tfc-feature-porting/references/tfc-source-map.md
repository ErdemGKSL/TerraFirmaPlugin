# TFC source map

Where each gameplay area lives in the TerraFirmaCraft repository (1.21.1 branch, commit `c3b4d95`). All Java paths are relative to `src/main/java/net/dries007/tfc/`. "Gen data" means `src/generated/resources/data/tfc/`, "Main data" means `src/main/resources/data/tfc/`, and "Assets" means `src/main/resources/assets/tfc/`.

Re-verify paths if the pinned commit changes. TFC refactors packages between versions.

## Contents
1. Scale at a glance
2. Area → source table
3. Cross-cutting infrastructure
4. TFC's own tests
5. Datagen (Python)

## 1. Scale at a glance

| Thing | Count | Where |
|---|---|---|
| Java files | ~1700 (common 783, world 297, client 249, util 126, compat 109, mixin 75) | `src/main/java` |
| Blockstate files | ~4500 (wood 1223, ore 1125, rock 812, plant 325, metal 186) | `Assets/blockstates/**` |
| Item models | ~5600 | `Assets/models/item/**` |
| Recipes | ~6200 (crafting 2912, heating 884, collapse 525, chisel 513, barrel 331, anvil 281, pot 214, casting 111, knapping 101, quern 89, welding 87, glassworking 60, sewing 25, landslide 21, alloy 9, scraping 7, loom 5, bloomery 1, blast_furnace 1) | `Gen data/recipe/**` |
| Rocks | 21: granite diorite gabbro shale claystone limestone conglomerate dolomite chert chalk tuff rhyolite basalt andesite dacite quartzite slate phyllite schist gneiss marble | `common/blocks/rock/Rock.java` |
| Rock block variants | raw hardened smooth cobble bricks gravel spike cracked_bricks mossy_bricks mossy_cobble chiseled loose mossy_loose pressure_plate button aqueduct rope_anchor (+ slab/stairs/wall) | `Rock.BlockType` |
| Woods | 20: acacia ash aspen birch blackwood chestnut douglas_fir hickory kapok mangrove maple oak palm pine rosewood sequoia spruce sycamore white_cedar willow | `common/blocks/wood/Wood.java` |
| Wood block variants | ~40 per wood (log, stripped, planks, leaves, sapling, fallen leaves, twig, supports, workbench, chest, loom, sluice, barrel, lectern, shelf, axle, gearbox, windmill, water wheel, crate, ...) | `Wood.BlockType` |
| Metals | 27 incl. weak/high-carbon steels; ~60 item/block types each (ingot, sheets, rod, tool heads, tools, armor, anvil, lamp, chain, bars, trapdoor, ...) | `util/Metal.java` |
| Ores | 33 (graded poor/normal/rich for metal ores) × 21 rocks | `common/blocks/rock/Ore.java` |
| Crops | 29 | `common/blocks/crop/Crop.java` |
| Plants | 214 | `common/blocks/plant/Plant.java` |
| Entity classes | ~72 (+44 Blockbench models in `resources/entity_models/*.bbmodel`) | `common/entities/**` |
| Locales | en_us, tr_tr, de_de, es_es, ja_jp, ko_kr, pl_pl, pt_br, ru_ru, ... | `Assets/lang/` and `resources/lang/` |

## 2. Area → source table

### Calendar & climate
- Calendar: `util/calendar/` (`ICalendar` holds the tick constants: `CALENDAR_TICKS_IN_HOUR = 1000`, days per month configurable; `ServerCalendar`, `CalendarTransaction`, `Season`, `Month`, `ICalendarTickable`).
- Climate: `util/climate/` (`OverworldClimateModel`, `ClimateModel`, `ClimateRange(s)`, `KoppenClimateClassification`). Climate ranges for crops/fauna: `Gen data/tfc/climate_range/**`.
- Weather and snow: `util/tracker/WeatherHelpers.java`, `util/EnvironmentHelpers.java`.
- Client sky/colours: `client/overworld/`, `Assets/textures/colormap/` (divergence: see ledger).

### World generation
- Entry: `world/TFCChunkGenerator.java`, `world/ChunkNoiseFiller.java`, `world/ChunkHeightFiller.java`, `world/TFCAquifer.java`, `world/FastConcurrentCache.java`.
- Regions (continents, climate, biome choice, rocks): `world/region/` (`RegionGenerator` with its ordered tasks `Init`, `AddContinents...`, `AnnotateClimate`, `ChooseBiomes`, `ChooseRocks`, `AddRiversAndLakes`, ...).
- Layers: `world/layer/` + `world/layer/framework/`.
- Rivers: `world/river/` (`MidpointFractal`, `Flow`, `RiverNoise`).
- Noise: `world/noise/` (includes MIT-licensed `FastNoiseLite`).
- Biomes: `world/biome/` + `Main data/worldgen/biome/*.json`.
- Surface: `world/surface/` + `world/surface/builder/`.
- Chunk data (rock layers, rainfall, temperature, forest): `world/chunkdata/`.
- Features: `world/feature/{tree,plant,vein,cave,coral}`, volcanoes `world/volcano/`, shores `world/shore/`, carvers `world/carver/`, placement `world/placement/`.
- Tree structures: `Main data/structure/<wood>{,_dead,_large}/*.nbt` (56 folders).
- Rock settings data: `Gen data/tfc/worldgen/rock_settings/`.

### Geology & terrain physics
- Rocks, ores, loose rocks, spikes, moss: `common/blocks/rock/`.
- Soils (dirt, grass, clay, mud, farmland, paths): `common/blocks/soil/`.
- Collapse and landslide: `util/tracker/Collapse.java`, `util/tracker/WorldTracker.java`, `common/recipes/CollapseRecipe.java`, `LandslideRecipe.java`, data `Gen data/recipe/{collapse,landslide}/`, support beams `Gen data/tfc/support/`.
- Prospecting: `common/items/PropickItem.java`, `ProspectResult.java`, `network/ProspectedPacket.java`.
- Panning/sluice: `common/items/PanItem.java`, `EmptyPanItem.java`, `common/blocks/devices/SluiceBlock.java`, data `Gen data/tfc/deposit/`.
- Chisel: `common/items/ChiselItem.java`, `player/ChiselMode.java`, `recipe/chisel/`.
- Groundcover: `common/blocks/GroundcoverBlock(Type).java`.

### Flora & agriculture
- Crops: `common/blocks/crop/`, `common/blockentities/CropBlockEntity.java`, `FarmlandBlockEntity.java`, fertilizers `Gen data/tfc/fertilizer/`.
- Plants: `common/blocks/plant/` (+ `fruit/`, `coral/`), `PlantRegrowth.java`.
- Trees and leaves: `common/blocks/wood/` (`TFCLeavesBlock`, `TFCSaplingBlock`, `FallenLeavesBlock`), `world/feature/tree/`.
- Berry bushes and fruit trees: `common/blocks/plant/fruit/`, `BerryBushBlockEntity.java`.

### Fauna
- Base classes: `common/entities/livestock/` (`TFCAnimal`, `Mammal`, `OviparousAnimal`, `DairyAnimal`, `WoolyAnimal`, `CommonAnimalData`, `Age`, `Gender`).
- Predators/prey/aquatic/pets: `common/entities/{predator,prey,aquatic,livestock/pet,livestock/horse,livestock/camel}`.
- AI: `common/entities/ai/**` (TFC uses vanilla `Brain`s).
- Spawning rules: `Gen data/tfc/fauna/*.json`, `common/entities/Faunas.java`.
- Config per animal: `config/animals/`.
- Fishing and traps: `entities/misc/TFCFishingHook.java`, `LobsterTrapBlock(Entity)`.
- Models: `resources/entity_models/*.bbmodel` (Blockbench sources), `client/model/entity/` (Java models), `Assets/textures/entity/`.

### Player survival
- Food: `common/component/food/` (`FoodComponent`, `FoodCapability`, `FoodTrait(s)`, `NutritionData`, `Nutrient`), data `Gen data/tfc/food/`.
- Player data (thirst, nutrition, intoxication, chisel mode): `common/player/PlayerInfo.java`, `IPlayerInfo.java`, `PlayerBridge.java`.
- Drinking: `Gen data/tfc/drinkable/`, `network/PlayerDrinkPacket.java`.
- Health scaling and damage types: `util/PhysicalDamage(Type).java`, data `Gen data/tfc/{entity,item}_damage_resistance/`.
- Size and weight: `common/component/size/`, data `Gen data/tfc/item_size/`.
- Effects: `common/effect/` (thirst, exhausted).

### Heat & fire
- Heat: `common/component/heat/` (`HeatComponent` = lazy extrapolation from `lastTemperature` + `lastTick`), data `Gen data/tfc/item_heat/`, `fluid_heat/`, `fuel/`, `lamp_fuel/`.
- Firepit/grill/pot: `common/blockentities/{AbstractFirepit,Firepit,Grill,Pot}BlockEntity.java`, recipes `pot/`.
- Fire starting: `items/FirestarterItem.java`, `util/events/StartFireEvent.java`.
- Torches and lamps: `blocks/TFCTorchBlock.java`, `blockentities/LampBlockEntity.java`.

### Primitive technology
- Knapping: `common/container/KnappingContainer.java`, `util/data/KnappingPattern.java`, `KnappingType.java`, `Gen data/tfc/knapping_type/`, `recipe/knapping/`.
- Pottery and pit kiln: `blockentities/PitKilnBlockEntity.java`, `common/component/mold/`, `items/MoldItem.java`, `VesselItem.java`.
- Log pile and charcoal pit: `blockentities/{LogPile,BurningLogPile}BlockEntity.java`, `blocks/CharcoalPileBlock.java`.
- Quern: `blockentities/QuernBlockEntity.java`, `recipe/quern/`.
- Barrel: `blockentities/BarrelBlockEntity.java`, `common/component/block/Barrel*.java`, `recipe/barrel/`.
- Others: scraping, loom, sewing, drying bricks, composter, large vessel, powderkeg, nest box, crate, shelf, tool rack (`blockentities/*`).

### Metallurgy & glass
- Metals: `util/Metal.java`, `util/AlloyRange.java`, `util/FluidAlloy.java`, `recipe/alloy/`.
- Crucible, channels, mold table: `blockentities/{Crucible,Channel,MoldTable}BlockEntity.java`, `util/ChannelFlow.java`.
- Charcoal forge and bellows: `blockentities/{CharcoalForge,Bellows}BlockEntity.java`.
- Anvil and forging: `blockentities/AnvilBlockEntity.java`, `common/component/forge/` (`ForgeRule`, `ForgeStep`, `ForgingBonus`), `recipe/{anvil,welding}/`, `network/SelectAnvilPlanPacket.java`.
- Bloomery and blast furnace: `blockentities/{Bloomery,BlastFurnace,Bloom}BlockEntity.java`.
- Casting: `recipe/casting/`, `common/recipes/CastingRecipe.java`.
- Glassworking: `common/component/glass/`, `items/{Blowpipe,GlassBlowpipe,Glassworking}Item.java`, `blockentities/{GlassBasin,HotPouredGlass}BlockEntity.java`.
- Oxidation: `blocks/Weathering*.java`.

### Mechanical power
- Network solver: `util/rotation/` (`RotationNetwork`, `RotationNetworkManager`, nodes). TFC already has a test for it: `test/util/RotationNetworkTest.java`.
- Blocks and block entities: `common/blocks/rotation/`, `common/blockentities/rotation/` (axle, gearbox, clutch, water wheel, windmill, crankshaft, trip hammer, power loom, pump).

### Fluids
- `common/fluids/` (`TFCFluids`, `RiverWaterFluid`, `MoltenFluid`, `Alcohol`, `SimpleFluid`). In-world custom fluids are a divergence. Fluids in containers are item data only.

### Items, tools, combat
- `common/items/` (tools, javelin, scythe, propick, hammer, chisel, shields, maces, boats, fishing rod). Tool stats come from `Metal` tiers.

### GUIs & field guide
- Containers: `common/container/`. Screens: `client/screen/`. Textures: `Assets/textures/gui/` (`anvil.png`, `knapping.png`, `player_calendar.png`, `player_nutrition.png`, `player_climate.png`, ...).
- Field guide (Patchouli): templates `src/main/templates/data/tfc/patchouli_books/field_guide`, generated `src/generated/resources/assets/tfc/field_guide`, generator `resources/generate_book.py`.

### Commands, advancements, loot
- Commands: `common/commands/`. Advancements: `Main data/advancement/` + `util/advancements/`. Loot: `Main data/loot_table/`, `util/loot/` (custom loot conditions and functions).

## 3. Cross-cutting infrastructure
- Data managers (JSON reload listeners): `util/data/DataManager.java`, `DataManagers.java`. One manager per `Gen data/tfc/<folder>`.
- Ingredients: `common/recipes/ingredients/` (`tfc:and`, `tfc:not_rotten`, `tfc:lacks_trait`, `tfc:has_trait`, `tfc:heat`, `tfc:fluid_content`, ...). Outputs: `common/recipes/outputs/` (`tfc:copy_input`, `tfc:add_trait`, `tfc:copy_food`, `tfc:meal`, `tfc:add_heat`, ...).
- Tags: `Gen data/tfc/tags/{block,item,fluid,entity_type,damage_type,worldgen}`. They drive a lot of behaviour (e.g. `can_collapse`, `can_landslide`, `breaks_when_isolated`).
- Config: `config/` (`ServerConfig`, `CommonConfig`, `ClientConfig`).
- Network packets: `network/`. Each packet tells you about a client↔server interaction you must recreate.
- Mixins: `mixin/`. Each mixin is a vanilla behaviour TFC overrides. Check here when "vanilla does the wrong thing" in our port.

## 4. TFC's own tests
`src/test/java/net/dries007/tfc/test/**` (JUnit 5, 26 files): `ForgeRuleTest`, `KnappingPatternTest`, `RotationNetworkTest`, `FlowTest`, `SolarCalculatorTest`, `HeatingItemTest`, `MoldTest`, `ToolsTest`, `BranchDirectionTest`, and drawing tests for `RegionGenerator` and biome noise (they render images, handy for visually checking a worldgen port). Port the pure ones together with the rules they cover.

## 5. Datagen (Python)
`resources/*.py` (mcresources-based) generates most JSON: `assets.py` (blockstates/models), `data.py` (data definitions), `recipes` inside `data.py`, `world_gen.py`, `generate_textures.py`, `generate_book.py`. Our datagen tool should consume the **generated output** (`src/generated/**`, `src/main/resources/**`), not re-run TFC's Python. Read the Python only to understand intent, e.g. why a model has an `overlay` texture.
