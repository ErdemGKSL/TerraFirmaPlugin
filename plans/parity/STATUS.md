# Parity status

Generated-style table: one row per item in `plans/parity/items/`. The loop updates the `status` column here and in the item file in the same commit (see `PROTOCOL.md`). Keep rows sorted by wave, then id.

Totals: 236 items; open 211, done 22, blocked 3; waves 1..56 (wave 0 = already done).

| id | title | area | wave | status |
|---|---|---|---|---|
| PAR-HUD-01 | Two-layer HUD emitted by datagen (HudArt, offset-left layouts, trim compensation) | HUD | 0 | done |
| PAR-HUD-02 | Two-layer HUD art: one master per bar, bg and fill derived by hud_layers.py | HUD | 0 | done |
| PAR-HUD-03 | `/tfp debug hunger <name> <0..20>` for exact low-food HUD states | HUD | 0 | done |
| PAR-METAL-07 | Charcoal forge visuals: TFC heat-level models on a snow-layer carrier | METAL | 0 | done |
| PAR-INFRA-01 | Feature module SPI: register listeners, services and debug subcommands without editing TfpPlugin | INFRA | 1 | done |
| PAR-INFRA-04 | Item data components on PDC: fluid contents and item lists (vessels, jugs, buckets, molds) | INFRA | 1 | done |
| PAR-INFRA-07 | Tag runtime: TFC item/block/fluid/entity tags resolved for CE and vanilla ids | INFRA | 1 | done |
| PAR-REL-01 | Test infrastructure fixes: headless client pack URL, snow-free test site, CE shutdown warning | REL | 1 | done |
| PAR-INFRA-02 | Device block framework: CraftEngine block behaviour routing, per-block state and lazy catch-up hooks | INFRA | 2 | done |
| PAR-INFRA-03 | GUI framework: textured chest GUIs, title glyphs, buttons, progress bars, alignment | INFRA | 2 | done |
| PAR-INFRA-05 | Item tooltip pipeline: several decorations per item, TFC styles and colours | INFRA | 2 | done |
| PAR-INFRA-12 | Self-test case: item data PDC round trip on a real server (nested food/heat inside tfp:contents) | INFRA | 2 | done |
| PAR-INFRA-08 | Device datagen: shared CE templates for device blocks (states, loot self, hitbox carriers, entity_renderer) | INFRA | 2 | done |
| PAR-INFRA-14 | NeoForge common tags: datagen emits the vanilla members of the c: tags (neoforge_common_tags.json) | INFRA | 2 | done |
| PAR-HUD-04 | Two-layer HUD in-game verification at GUI scale 2 and 3 (full, low, submerged) | HUD | 3 | done |
| PAR-INFRA-06 | Recipe runtime on Paper: ItemStack ingredient matching, indexed lookups, output modifiers | INFRA | 3 | done |
| PAR-INFRA-09 | Block and entity loot runtime: TFC loot tables evaluated on Paper | INFRA | 3 | done |
| PAR-METAL-10 | Charcoal forge visual verification: night, neighbouring full blocks, animation frames, lit-display flicker | METAL | 3 | done |
| PAR-INFRA-13 | Hitbox carrier selector: every CE block gets the vanilla state whose collision and selection shape best match TFC's | INFRA | 3 | done |
| PAR-INFRA-18 | Device framework wiring: register tfp:device with CraftEngine, self-test device.roundtrip, SAVE removal | INFRA | 3 | done |
| PAR-CLIM-10 | Plugin enables on Paper 26.x: SnowService builds the icicle tip without casting to PointedDripstone | CLIMATE | 4 | done |
| PAR-HUD-05 | HUD vs vanilla armour row: no overlap with armour worn, health text inside its bar | HUD | 4 | open |
| PAR-HUD-08 | Held item temperature popup with real values | HUD | 4 | open |
| PAR-INFRA-10 | Neighbour-update runner: block shape updates decided async, applied sync | INFRA | 4 | open |
| PAR-INFRA-11 | Catch-up trigger service: chunk load, interaction and visible-area sweep for lazy blocks | INFRA | 4 | open |
| PAR-INFRA-19 | Device generators on the shared spec: Firepit, Grill, Pot and ForgeDevices write through DeviceSpecs + DeviceYaml | INFRA | 4 | open |
| PAR-INFRA-20 | Datagen exports TFC's overrides of vanilla recipes (data/minecraft/recipe, 66 files) | INFRA | 4 | open |
| PAR-INFRA-21 | Loot delegation: datagen ships TFC loot tables, CE blocks that need TFC conditions drop through the Paper loot runtime | INFRA | 4 | open |
| PAR-INFRA-22 | Correct-tool gate for delegated block loot: CustomBlocks.isCorrectTool over CraftEngine's tool check | INFRA | 4 | open |
| PAR-INFRA-24 | Packaging: :dist:shadowJar always ships the generated content (content/data/id_map.json) without share-build.sh | INFRA | 4 | done |
| PAR-WORLD-17 | Ore overlay spike S1: measure exposed ore faces per chunk and the overlay entity budget | WORLD | 4 | open |
| PAR-HUD-06 | HUD polish: health frame asymmetry, ambient temperature bar and info plate, pulse animation | HUD | 5 | open |
| PAR-PROC-01 | Knapping GUI: 5x5 grid for stone, clay, fire clay, leather and goat horn | PROC | 5 | open |
| PAR-PROC-19 | Misc materials as CE items: ores, gems, powders, fibres, cloth, paper, glue, straw, mortar, spawn eggs | PROC | 5 | open |
| PAR-SURV-01 | All TFC food items generated as CE items with food_stamp and food data | SURV | 5 | open |
| PAR-INFRA-17 | Renderer-cube feel (spike S2): destroy_stages, particles, sounds and mining for display-drawn full blocks | INFRA | 5 | open |
| PAR-METAL-20 | Charcoal forge lit tops turned 180°: the heat_N item display must match TFC's block-model orientation | METAL | 5 | open |
| PAR-HUD-07 | HUD per MC version and TFC display styles: 26.x sprite list, XP bar style, health display style | HUD | 6 | open |
| PAR-PROC-02 | Workbench crafting: the plain 2,255 shaped/shapeless recipes as CE recipe YAML | PROC | 6 | open |
| PAR-SURV-02 | Food tooltip: expiry/rotten, traits, nutrition lines | SURV | 6 | open |
| PAR-SURV-03 | Eating completion: undefined foods, peaceful, rotten advancement hook, in-game check | SURV | 6 | open |
| PAR-INFRA-15 | Ore overlay datagen: ores take the host rock visual, overlay item models, always-on allocator rule, profiles level-0..2 only | INFRA | 6 | open |
| PAR-PROC-03 | Advanced crafting: TFC advanced recipes, food combining, casting crafting, vanilla recipe leakage | PROC | 7 | open |
| PAR-PROC-04 | Quern: block, handstone, grinding, GUI-less interaction | PROC | 7 | open |
| PAR-PROC-05 | Scraping hides on a log | PROC | 7 | open |
| PAR-SURV-04 | Thirst completion: thirst effects, rain drinking, salt water, open-water drinking decision | SURV | 7 | open |
| PAR-INFRA-16 | Ore overlay runtime: packet-only face overlays, neighbour-driven refresh, Folia-safe, feel checklist | INFRA | 7 | open |
| PAR-FLUID-01 | Fluids as container contents: registry of TFC fluids, colours, debug fill | FLUID | 8 | open |
| PAR-SURV-05 | Health and damage: TFC health scale, physical damage types, entity and armour resistances | SURV | 8 | open |
| PAR-SURV-06 | Item size and weight on Paper: container limits, overburdened and exhausted, tooltip | SURV | 8 | open |
| PAR-SURV-07 | Player tab screens: calendar, nutrition and climate pages opened from outside the inventory | SURV | 8 | open |
| PAR-REL-12 | Visual and overlay budget guards: profile-vs-pool unit test, overlay entities-per-chunk itest budget | REL | 8 | open |
| PAR-FLUID-02 | Buckets: wooden bucket, red and blue steel buckets, fluid placement policy | FLUID | 9 | open |
| PAR-FLUID-03 | Salt water, spring water and river water in the world | FLUID | 9 | open |
| PAR-PROC-08 | Barrel block: fluids, sealing, sealed and instant recipes, preservation | PROC | 9 | open |
| PAR-SURV-08 | Alcohol and intoxication | SURV | 9 | open |
| PAR-FLUID-04 | Hot spring healing and bubble columns | FLUID | 10 | open |
| PAR-HEAT-01 | Item heat glow: hot items glow by heat level on vanilla clients | HEAT | 10 | open |
| PAR-HEAT-02 | Heat tooltip in TFC colours and metal info lines (melting point, workable, weldable) | HEAT | 10 | open |
| PAR-SURV-09 | Death, respawn and regeneration rules | SURV | 10 | open |
| PAR-CLIM-01 | Calendar config, sleep skipping and doDaylightCycle sync | CLIMATE | 11 | open |
| PAR-CLIM-02 | Precipitation per position on Paper and the in-game snow-vs-rain check | CLIMATE | 11 | open |
| PAR-HEAT-03 | Hot items: burn damage while held, item entities cool in water and snow | HEAT | 11 | open |
| PAR-PROC-11 | Composter | PROC | 11 | open |
| PAR-CLIM-03 | Snow and ice accumulation: lazy-vs-stepped equivalence and server check | CLIMATE | 12 | open |
| PAR-HEAT-04 | Heating recipes on devices: the 884 heating recipes as a shared service | HEAT | 12 | open |
| PAR-HEAT-05 | Fire starting: firestarter use-duration, flint and pyrite, StartFireEvent world rules | HEAT | 12 | open |
| PAR-PROC-09 | Barrel GUI, fluid level visual, rain fill, barrel rack | PROC | 12 | open |
| PAR-CLIM-04 | Snow piles, ice piles, icicles and sea ice as CraftEngine blocks | CLIMATE | 13 | open |
| PAR-HEAT-06 | Firepit block: placement, fuel, lazy temperature, ash, extinguish by rain and water | HEAT | 13 | open |
| PAR-PROC-10 | Log pile and charcoal pit | PROC | 13 | open |
| PAR-PROC-14 | Salads | PROC | 13 | open |
| PAR-CLIM-05 | Seasonal grass tint: decide band resolution and make the colour change visible | CLIMATE | 14 | blocked |
| PAR-HEAT-07 | Firepit GUI: fuel slots, input, outputs, temperature bar | HEAT | 14 | open |
| PAR-HEAT-10 | Torches: burnout to dead torch, relight, wall torches | HEAT | 14 | open |
| PAR-INFRA-23 | Hitbox carrier in-game verification: outlines, stand heights and client prediction of every carrier class | INFRA | 14 | open |
| PAR-PROC-18 | Powderkeg | PROC | 14 | open |
| PAR-CLIM-06 | Seasonal packet rewrite JMH budget and visual checklist | CLIMATE | 15 | open |
| PAR-HEAT-08 | Grill on firepit: placement, five slots, cooking, GUI | HEAT | 15 | open |
| PAR-HEAT-09 | Cooking pot on firepit: boiling, soups and jams, pot fluid, GUI | HEAT | 15 | open |
| PAR-HEAT-11 | Candles, candle cakes and jack-o'-lanterns: burnout, lighting, carving | HEAT | 15 | open |
| PAR-HEAT-12 | Firebox, stove and stove pot | HEAT | 16 | open |
| PAR-PHYS-01 | TFC falling blocks: canFallThrough, landing, damage | PHYS | 16 | open |
| PAR-WORLD-01 | Rendered region and river maps compared with TFC | WORLD | 16 | open |
| PAR-WORLD-02 | Bukkit biome binding and climate call sites on the real chunk data | WORLD | 16 | open |
| PAR-PHYS-02 | Collapse runner: mining triggers collapses decided async, applied as falling blocks | PHYS | 17 | open |
| PAR-PHYS-03 | Landslides and gravity soils | PHYS | 17 | open |
| PAR-WORLD-03 | Worldgen performance: JMH chunk budget, vanilla baseline and exposed-gem decision | WORLD | 17 | open |
| PAR-WORLD-04 | TFC soil and gravel block set in generation: grass, dirt, coarse dirt, clay, peat, kaolin, mud, rock gravel | WORLD | 17 | open |
| PAR-GATHER-05 | Soil interactions: hoeing, paths, mud, rooted dirt, grass spread and rock spawning | GATHER | 18 | open |
| PAR-GATHER-06 | Raw rock drops and mossy rock spreading | GATHER | 18 | open |
| PAR-PHYS-05 | Explosion collapses | PHYS | 18 | open |
| PAR-WORLD-05 | Ice sheets, sea ice and icebergs at generation; lava flows; remaining biome surface builders | WORLD | 18 | open |
| PAR-TOOL-01 | Stone tools: rock tool heads and tools as CE items, crafting from knapped heads | TOOL | 19 | open |
| PAR-TOOL-03 | In-game verification of metal tools and armour | TOOL | 19 | open |
| PAR-WORLD-06 | Cave tunnels and canyons (vanilla carvers with TFC carveBlock) | WORLD | 19 | open |
| PAR-WORLD-08 | Disc, pipe and kaolin veins; montane second pass; ore distribution statistics | WORLD | 19 | open |
| PAR-TOOL-02 | Tool mechanics on Paper: TFC mining speed, durability rules, forging efficiency | TOOL | 20 | open |
| PAR-TOOL-04 | Swords, maces, macuahuitl: attack damage per TFC tier | TOOL | 20 | open |
| PAR-WORLD-07 | Aquifer barrier noise and the grass rule under carved surfaces | WORLD | 20 | open |
| PAR-WORLD-09 | Groundcover, loose rocks, deposits and boulders as CE blocks in generation | WORLD | 20 | open |
| PAR-GATHER-01 | Pick up loose rocks and groundcover with a right-click | GATHER | 21 | open |
| PAR-POT-01 | Placed items: put up to four items on the ground (and the straw/log stages of a pit kiln) | POTTERY | 21 | open |
| PAR-PROC-12 | Panning | PROC | 21 | open |
| PAR-WORLD-10 | Wood blocks: logs, wood, stripped, leaves, saplings as CE blocks | WORLD | 21 | open |
| PAR-GATHER-03 | Tree felling: the whole tree falls when the trunk is cut with an axe | GATHER | 22 | open |
| PAR-GATHER-04 | Leaves: slow decay, movement slowdown, fallen leaves; plant movement slowdown | GATHER | 22 | open |
| PAR-PHYS-04 | Support beams: blocks, connections, collapse prevention | PHYS | 22 | open |
| PAR-PROC-13 | Sluice | PROC | 22 | open |
| PAR-POT-02 | Unfired and fired pottery items, clay knapping outputs | POTTERY | 23 | open |
| PAR-TOOL-05 | Javelins: throwing, damage, pickup, javelin AI for skeletons | TOOL | 23 | open |
| PAR-TOOL-06 | Knife, scythe and hoe area behaviours; remaining tool items (saw, chisel, propick, tuyere, shears heads) | TOOL | 23 | open |
| PAR-WORLD-11 | Tree generation: forest placement on every chunk, dead forests, mangroves | WORLD | 23 | open |
| PAR-POT-03 | Pit kiln: straw and log stages, lighting, lazy firing | POTTERY | 24 | open |
| PAR-POT-04 | Small vessel: inventory, melting contents into metal, pouring | POTTERY | 24 | open |
| PAR-TOOL-07 | Shields per metal | TOOL | 24 | open |
| PAR-WORLD-12 | Plant blocks: all TFC plant shapes as CE blocks in generation | WORLD | 24 | open |
| PAR-GATHER-02 | Harvest plants, leaves, twigs and fallen leaves by TFC loot tables | GATHER | 25 | open |
| PAR-POT-05 | Molds: filling from a vessel/crucible, solidifying, breaking out the casting | POTTERY | 25 | open |
| PAR-WORLD-13 | Remaining plant features: vines, kelp, epiphytes, creeping plants, cacti, algae, atoll corals | WORLD | 25 | open |
| PAR-WORLD-14 | Volcanic features: springs, rivulets, flood-fill lakes, calderas of other cones | WORLD | 25 | open |
| PAR-POT-06 | Large vessel (and glazed): nine slots, sealing, preservation, automation | POTTERY | 26 | open |
| PAR-POT-07 | Jugs and glass bottles: fill, drink, pour, break | POTTERY | 26 | open |
| PAR-WORLD-15 | Volcanic and spring CE blocks: hardened rock, magma blocks, spring water look | WORLD | 26 | open |
| PAR-WORLD-16 | Spawn and world settings: spawn offset, forced TFC gamerules, structures off, dimension policy | WORLD | 26 | open |
| PAR-FARM-01 | Farmland block: hydration from water (async), nutrients, hoe overlay readout | FARM | 27 | open |
| PAR-METAL-01 | Alloy display and fluid alloy glue for vessels and crucibles | METAL | 27 | open |
| PAR-POT-08 | Drying bricks, mud bricks and thatch blocks | POTTERY | 27 | open |
| PAR-POT-09 | Bowls and powder bowls (wooden and ceramic bowl blocks) | POTTERY | 27 | open |
| PAR-FARM-02 | Fertilizers | FARM | 28 | open |
| PAR-FARM-03 | Crop blocks: all 29 crops in every shape, seeds, dead and wild crops | FARM | 28 | open |
| PAR-METAL-02 | Fire ingot mold and bell casting; casting crafting | METAL | 28 | open |
| PAR-METAL-03 | Anvil blocks: rock anvils and metal anvils with items shown on top | METAL | 28 | open |
| PAR-FARM-04 | Crop growth glue: lazy calendar growth, climate checks, death, harvest yields | FARM | 29 | open |
| PAR-FARM-06 | Stationary berry bushes: lifecycle, picking, propagation | FARM | 29 | open |
| PAR-METAL-04 | Anvil plan selection GUI | METAL | 29 | open |
| PAR-METAL-08 | Charcoal forge block entity: creation from a charcoal pile, lighting, continuous heating of held items | METAL | 29 | open |
| PAR-FARM-05 | Wild crops and spreading crops (pumpkin, melon), rotten pumpkins | FARM | 30 | open |
| PAR-FARM-07 | Spreading bushes, canes and cranberries | FARM | 30 | open |
| PAR-METAL-05 | Anvil forging GUI: step buttons, target and progress markers, hammering, forging bonus | METAL | 30 | open |
| PAR-PROC-16 | Glassworking: blowpipe operations (blow, roll, stretch, pinch, flatten, saw) | PROC | 30 | open |
| PAR-FARM-08 | Fruit trees: saplings, growing branches, seasonal leaves, fruit | FARM | 31 | open |
| PAR-METAL-06 | Welding on the anvil (flux) | METAL | 31 | open |
| PAR-METAL-09 | Charcoal forge GUI and insulation/chimney multiblock scan | METAL | 31 | open |
| PAR-PROC-17 | Glass basin and hot poured glass; poured glass blocks and glass items | PROC | 31 | open |
| PAR-DATA-01 | Commands under /tfp: player, count block, clear world, add trim, weather hooks | DATA | 32 | open |
| PAR-FARM-09 | Bananas | FARM | 32 | open |
| PAR-FARM-10 | Sapling growth into trees | FARM | 32 | open |
| PAR-METAL-11 | Bellows: push, air boost, crank, animation | METAL | 32 | open |
| PAR-CLIM-07 | Weather command and TFC weather state (`/tfp weather`) | CLIMATE | 33 | open |
| PAR-FARM-11 | Plant regrowth, plant growth/spread, flower cuttings | FARM | 33 | open |
| PAR-METAL-12 | Crucible block and GUI | METAL | 33 | open |
| PAR-TOOL-08 | Prospector's pick: scan, result message, `/tfp propick` | TOOL | 33 | open |
| PAR-CLIM-08 | Lightning rules: enableLightning, log stripping, fire start | CLIMATE | 34 | open |
| PAR-CLIM-09 | Climate instruments: thermometer, anemometer, vane, calendar clock | CLIMATE | 34 | open |
| PAR-DATA-02 | Advancements: triggers, awards and presentation | DATA | 34 | open |
| PAR-METAL-13 | Channels and mold tables: pouring flow | METAL | 34 | open |
| PAR-DATA-03 | Advancement criteria wiring across features | DATA | 35 | open |
| PAR-DATA-04 | Field guide: decide the form and convert the book | DATA | 35 | blocked |
| PAR-HEAT-13 | Fire behaviour: fire arrows, dousing, fire spread limits | HEAT | 35 | open |
| PAR-METAL-14 | Bloomery multiblock and bloom | METAL | 35 | open |
| PAR-DATA-05 | Localisation: Paper lang loading, all TFC locales, plugin strings | DATA | 36 | open |
| PAR-METAL-15 | Blast furnace, tuyere and steel path | METAL | 36 | open |
| PAR-METAL-16 | Ingot and double-ingot piles | METAL | 36 | open |
| PAR-TOOL-10 | Bows and glow arrows | TOOL | 36 | open |
| PAR-DATA-06 | Configuration parity: every TFC ServerConfig/CommonConfig key in config.yml | DATA | 37 | open |
| PAR-DATA-07 | Damage types and mob effects | DATA | 37 | open |
| PAR-DATA-08 | Sounds: TFC sound events into the pack and played by features | DATA | 37 | open |
| PAR-METAL-18 | Bells (bronze, brass) | METAL | 37 | open |
| PAR-DATA-09 | Particles: action-driven TFC particles with vanilla equivalents | DATA | 38 | open |
| PAR-DATA-10 | Dispenser behaviours, bonemeal, vanilla rule overrides | DATA | 38 | open |
| PAR-DATA-11 | Paintings, armour trims, misc data registries | DATA | 38 | open |
| PAR-FAUNA-01 | Entity model import: TFC bbmodels and Java animations converted to BetterModel | FAUNA | 38 | open |
| PAR-DATA-12 | Block info readout (Jade-like tooltips) on the actionbar | DATA | 39 | open |
| PAR-DATA-13 | TFC pin drift check in CI | DATA | 39 | open |
| PAR-FAUNA-02 | Entity models for Java-only TFC models via the rig generator | FAUNA | 39 | open |
| PAR-FAUNA-21 | Animal framework completion: species behaviour SPI, goal replacement, model re-apply checks | FAUNA | 39 | open |
| PAR-DECO-01 | Wood families loaded in CraftEngine: planks, stairs, slabs, fences, walls with our block_state templates | DECO | 40 | open |
| PAR-DECO-10 | Ropes, rope anchors and rope knots | DECO | 40 | open |
| PAR-FAUNA-03 | Entity texture variants, gendered and aged textures, render review | FAUNA | 40 | open |
| PAR-FAUNA-04 | Spawning by climate on Paper | FAUNA | 40 | open |
| PAR-FAUNA-05 | Livestock: feeding, familiarity and breeding interactions | FAUNA | 41 | open |
| PAR-PROC-06 | Loom: weaving cloth, burlap, wool, silk | PROC | 41 | open |
| PAR-PROC-07 | Sewing table GUI | PROC | 41 | open |
| PAR-PROC-15 | Scribing table | PROC | 41 | open |
| PAR-FAUNA-06 | Animal products: milk, wool, eggs; sheared look | FAUNA | 42 | open |
| PAR-FAUNA-08 | Livestock AI and roster: graze, follow food, herd; all 18 domestic species | FAUNA | 42 | open |
| PAR-FAUNA-09 | Prey AI and wild animals | FAUNA | 42 | open |
| PAR-FAUNA-11 | Aquatic animals: fish, squid, jellyfish, dolphins, orcas, manatees; mob buckets | FAUNA | 42 | open |
| PAR-FAUNA-07 | Nest box: laying, hatching, GUI | FAUNA | 43 | open |
| PAR-FAUNA-10 | Predator AI: hunting, home range, sleep schedule, packs | FAUNA | 43 | open |
| PAR-FAUNA-12 | Amphibians: turtles, penguins, seals, crocodiles, frogs (play dead, land/water) | FAUNA | 43 | open |
| PAR-FAUNA-13 | Pests and critters: rats raiding containers, lobsters, crayfish, isopods | FAUNA | 43 | open |
| PAR-FAUNA-14 | Pets: dogs and cats with commands | FAUNA | 44 | open |
| PAR-FAUNA-15 | Horses, donkeys and mules: riding, breeding, chests | FAUNA | 44 | open |
| PAR-FAUNA-17 | Hunting drops, butchering, hides | FAUNA | 44 | open |
| PAR-FAUNA-18 | Fishing: rod, hook, bait | FAUNA | 44 | open |
| PAR-DECO-02 | Wood logic: doors, trapdoors, fence gates, buttons, pressure plates | DECO | 45 | open |
| PAR-FAUNA-16 | Camels (dromedary, bactrian) | FAUNA | 45 | open |
| PAR-FAUNA-19 | Lobster traps | FAUNA | 45 | open |
| PAR-FAUNA-20 | Pluckable feathers, animal sounds | FAUNA | 45 | open |
| PAR-DECO-03 | Signs and hanging signs (per wood, per metal hanger) | DECO | 46 | open |
| PAR-DECO-04 | Chests, trapped chests and barrel racks per wood | DECO | 46 | open |
| PAR-DECO-05 | Tool racks, shelves, bookshelves, lecterns | DECO | 46 | open |
| PAR-DECO-06 | Rock families: bricks, cobble, smooth, stairs, slabs, walls, aqueducts, spikes, pressure plates, buttons | DECO | 46 | open |
| PAR-DECO-07 | Mud bricks, alabaster, aggregate, fire bricks, wattle and daub, thatch | DECO | 47 | open |
| PAR-DECO-08 | Sand, sandstone families, hardened clay, halite, coal, calcite, misc natural blocks | DECO | 47 | open |
| PAR-DECO-09 | Metal decoration blocks: metal blocks (and weathered), bars, chains, grates, trapdoors | DECO | 47 | open |
| PAR-TOOL-09 | Chisel: smoothing, slabs and stairs with mode cycling | TOOL | 47 | open |
| PAR-DECO-11 | Boats, chest boats and minecarts | DECO | 48 | open |
| PAR-DECO-12 | Misc blocks: crates, buoys, cakes, golden bamboo, palm mosaic, tree roots, light block | DECO | 48 | open |
| PAR-METAL-17 | Metal weathering of copper-family blocks | METAL | 48 | open |
| PAR-METAL-19 | Metal lamps: fuel, light, lit state | METAL | 48 | open |
| PAR-DECO-13 | Potted plants and krummholz | DECO | 49 | open |
| PAR-DECO-14 | Thatch bed: sleeping and spawn setting | DECO | 49 | open |
| PAR-DECO-15 | Visual budget re-check after all block families | DECO | 49 | open |
| PAR-MECH-01 | Rotation network blocks: axle, encased axle, gearbox, clutch, creative motor | MECH | 49 | open |
| PAR-MECH-02 | Rotation visuals: spinning axles and gears | MECH | 50 | open |
| PAR-MECH-05 | Crankshaft and powered bellows | MECH | 50 | open |
| PAR-MECH-06 | Quern automation | MECH | 50 | open |
| PAR-MECH-07 | Trip hammer | MECH | 50 | open |
| PAR-MECH-03 | Water wheel | MECH | 51 | open |
| PAR-MECH-04 | Windmill and blades | MECH | 51 | open |
| PAR-MECH-08 | Power loom | MECH | 51 | open |
| PAR-MECH-09 | Pump and pipes | MECH | 51 | open |
| PAR-MECH-10 | Bladed axle | MECH | 52 | open |
| PAR-REL-02 | Performance budgets in CI (JMH) and threading gate | REL | 52 | open |
| PAR-REL-03 | Dependency and version updates (MC, Paper, Folia, CE, BetterModel, BetterHud) | REL | 52 | open |
| PAR-REL-04 | Stone-age run self-test | REL | 52 | open |
| PAR-REL-05 | Metal run self-test | REL | 53 | open |
| PAR-REL-06 | Husbandry run self-test and 200-animal load test | REL | 53 | open |
| PAR-REL-10 | Art backlog: replace every placeholder texture | REL | 53 | open |
| PAR-REL-11 | Design docs and skills kept current | REL | 53 | open |
| PAR-REL-07 | Performance audit with 50 bots | REL | 54 | open |
| PAR-REL-08 | Full playthrough and visual QA | REL | 55 | open |
| PAR-REL-09 | Release packaging | REL | 56 | blocked |
