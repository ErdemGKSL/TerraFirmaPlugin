# Parity status

Generated-style table: one row per item in `plans/parity/items/`. The loop updates the `status` column here and in the item file in the same commit (see `PROTOCOL.md`). Keep rows sorted by wave, then id.

Totals: 385 items; open 207, done 168, blocked 9; waves 1..56 (wave 0 = already done).

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
| PAR-HUD-05 | HUD vs vanilla armour row: no overlap with armour worn, health text inside its bar | HUD | 4 | done |
| PAR-HUD-08 | Held item temperature placeholders with real values (popup layout and in-game check in PAR-HUD-10) | HUD | 4 | done |
| PAR-INFRA-10 | Neighbour-update runner: block shape updates decided async, applied sync | INFRA | 4 | done |
| PAR-INFRA-11 | Catch-up trigger service: chunk load, interaction and visible-area sweep for lazy blocks | INFRA | 4 | done |
| PAR-INFRA-19 | Device generators on the shared spec: Firepit, Grill, Pot and ForgeDevices write through DeviceSpecs + DeviceYaml | INFRA | 4 | done |
| PAR-INFRA-20 | Datagen exports TFC's overrides of vanilla recipes (data/minecraft/recipe, 66 files) | INFRA | 4 | done |
| PAR-INFRA-21 | Loot delegation: datagen ships TFC loot tables, CE blocks that need TFC conditions drop through the Paper loot runtime | INFRA | 4 | done |
| PAR-INFRA-22 | Correct-tool gate for delegated block loot: CustomBlocks.isCorrectTool over CraftEngine's tool check | INFRA | 4 | done |
| PAR-INFRA-27 | TFC metal tools count as correct tools: CE tag expansion of the blocks' correct_tools includes tfp:metal/* tools | INFRA | 4 | done |
| PAR-INFRA-24 | Packaging: :dist:shadowJar always ships the generated content (content/data/id_map.json) without share-build.sh | INFRA | 4 | done |
| PAR-WORLD-17 | Ore overlay spike S1: measure exposed ore faces per chunk and the overlay entity budget | WORLD | 4 | done |
| PAR-HUD-09 | Mount health bar: the ridden animal's health as a second health bar, as TFC's MOUNT_HEALTH | HUD | 5 | done |
| PAR-HUD-06 | HUD polish: health frame asymmetry, ambient temperature bar and info plate, pulse animation | HUD | 5 | done |
| PAR-HUD-11 | Held item heat evaluated lazily at placeholder time (no popup lag after a heat change) | HUD | 5 | done |
| PAR-HUD-12 | HUD and needs per game mode: bars hidden and needs paused in creative and spectator | HUD | 7 | done |
| PAR-PROC-01 | Knapping GUI: 5x5 grid for stone, clay, fire clay, leather and goat horn | PROC | 5 | done |
| PAR-PROC-19 | Misc materials as CE items: ores, gems, powders, fibres, cloth, paper, glue, straw, mortar, spawn eggs | PROC | 5 | done |
| PAR-SURV-01 | All TFC food items generated as CE items with food_stamp and food data | SURV | 5 | done |
| PAR-INFRA-25 | Catch-up service wiring: self-test catchup.lazyblock, config.yml keys, device indexing on placement | INFRA | 5 | done |
| PAR-INFRA-26 | Neighbour runner self-test: neighbour.converge on Paper and Folia | INFRA | 5 | done |
| PAR-INFRA-28 | Raw and hardened rock loot through the Paper runtime: delegate the 42 tfc:is_isolated tables, remove tfp:loot_table/rock_to_loose | INFRA | 5 | done |
| PAR-INFRA-30 | TFC's 41 overrides of vanilla loot tables (glass, panes, snow, chests, zombie, drowned) ship in a datapack | INFRA | 5 | done |
| PAR-INFRA-17 | Renderer-cube feel (spike S2): destroy_stages, particles, sounds and mining for display-drawn full blocks | INFRA | 5 | done |
| PAR-METAL-20 | Charcoal forge lit tops turned 180°: the heat_N item display must match TFC's block-model orientation | METAL | 5 | done |
| PAR-WORLD-18 | Ore vein weighted pick throws at a draw of 1.0: chunk refused ("no element above 100.0") | WORLD | 5 | done |
| PAR-HUD-07 | HUD per MC version and TFC display styles: 26.x sprite list, XP bar style, health display style | HUD | 6 | done |
| PAR-HUD-10 | Held temperature popup shows TFC's level text, verified in game | HUD | 6 | done |
| PAR-HUD-13 | Info plate fits the longest date line; BetterHud horizontal shift of the top-left info HUD | HUD | 6 | done |
| PAR-PROC-02 | Workbench crafting: the plain 2,255 shaped/shapeless recipes as CE recipe YAML | PROC | 6 | done |
| PAR-PROC-21 | Ore piece items stack by TFC item_size (16): drops.yml ore items get max_stack_size | PROC | 6 | done |
| PAR-PROC-23 | Material items in-game verification: ce item give for a sample of 20, names, textures, stack sizes | PROC | 6 | done |
| PAR-SURV-02 | Food tooltip: expiry/rotten, traits, nutrition lines | SURV | 6 | done |
| PAR-SURV-03 | Eating completion: undefined foods, peaceful, rotten advancement hook, in-game check | SURV | 6 | done |
| PAR-INFRA-15 | Ore overlay datagen: ores take the host rock visual, overlay item models, always-on allocator rule, profiles level-0..2 only | INFRA | 6 | done |
| PAR-INFRA-32 | Renderer-cube mining time: TFC break time independent of the client's swing rate, measured on a full-speed client | INFRA | 6 | done |
| PAR-INFRA-33 | CE block sound volume and pitch: hit and level-event break sounds at the TFC SoundType's values, devices too | INFRA | 6 | done |
| PAR-PHYS-06 | Isolated raw rock pops: a placed #breaks_when_isolated block with no supporting neighbour breaks and drops itself (WorldTracker isolated check) | PHYS | 6 | done |
| PAR-PROC-03 | Advanced crafting: TFC advanced recipes, food combining, casting crafting, vanilla recipe leakage | PROC | 7 | done |
| PAR-PROC-04 | Quern: block, handstone, grinding, GUI-less interaction | PROC | 7 | done |
| PAR-PROC-05 | Scraping hides on a log | PROC | 7 | done |
| PAR-PROC-24 | Our tfp:mineable/* block tags as server tags, so CE can build tools whose tool component names them (gem saw, knife, hammer, scythe) | PROC | 7 | done |
| PAR-PROC-25 | Workbench crafting in-game verification: 10 sampled CE crafts in a crafting table | PROC | 7 | done |
| PAR-SURV-04 | Thirst completion: thirst effects, rain drinking, salt water, open-water drinking decision | SURV | 7 | done |
| PAR-INFRA-16 | Ore overlay runtime: packet-only face overlays, neighbour-driven refresh, Folia-safe, feel checklist | INFRA | 7 | done |
| PAR-INFRA-31 | Catch-up settings in config.yml: catchup.* keys through TfpConfig and ConfigCodec | INFRA | 7 | done |
| PAR-INFRA-35 | CE hit sound volume for template-built blocks (rock, soil, sand): the block sound table resolves tfp:settings/sound | INFRA | 7 | done |
| PAR-HUD-14 | Server config for TFC's HUD display styles: health text style, climate temperature unit, XP bar options | HUD | 7 | done |
| PAR-HUD-15 | HUD season shows TFC's 12 hemisphere-adjusted season names; info plate widened for them | HUD | 7 | done |
| PAR-FLUID-01 | Fluids as container contents: registry of TFC fluids, colours, debug fill | FLUID | 8 | done |
| PAR-SURV-05 | Health and damage: TFC health scale, physical damage types, entity and armour resistances | SURV | 8 | done |
| PAR-SURV-06 | Item size and weight on Paper: container limits, overburdened and exhausted, tooltip | SURV | 8 | done |
| PAR-SURV-07 | Player tab screens: calendar, nutrition and climate pages opened from outside the inventory | SURV | 8 | done |
| PAR-SURV-10 | TFC damage types in the server registry (tfp:dehydration and the others), dehydration hits through it | SURV | 8 | done |
| PAR-REL-12 | Visual and overlay budget guards: profile-vs-pool unit test, overlay entities-per-chunk itest budget | REL | 8 | done |
| PAR-INFRA-34 | Verify the ore overlay datagen in game: host-rock look of buried ores, composite ore block item icons, pick-block | INFRA | 8 | done |
| PAR-PROC-26 | Durability material items carry minecraft:damage 0 (vanilla damageable); gem saw in-game check | PROC | 8 | done |
| PAR-PROC-27 | Tool item build guards: self-test gives every item with a minecraft:tool component; datagen check that tool rules name only shipped block tags | PROC | 8 | done |
| PAR-PROC-29 | Scraping block CE entry: tfc:scraping hitbox row and demand, configuration/scraping, in-game check | PROC | 8 | done |
| PAR-PROC-30 | minecraft:bricks is craftable in TFC: stop repurposing it as a visual state; bricks craft in-game re-check | PROC | 8 | done |
| PAR-GATHER-12 | CraftEngine blocks for loose rocks and groundcover, so pickup and stacking work in the world | GATHER | 22 | blocked |
| PAR-FARM-26 | CraftEngine farmland blocks per soil with hydration visual states (renamed from a nameless second PAR-FARM-19 row) | FARM | 28 | done |
| PAR-PROC-28 | Advanced crafting in-game verification and self-test: knife/sandwich crafts, vanilla recipe leakage over all recipes | PROC | 41 | done |
| PAR-HUD-17 | Wire the HUD display styles: hud.* keys in config.yml, health text and climate temperature placeholders follow HudStyles | HUD | 8 | done |
| PAR-HUD-18 | /tfp debug chunk climate lines follow hud.climate-temperature-style | HUD | 9 | done |
| PAR-PROC-31 | Vanilla full-cube blocks made by TFC barrel, loom, heating and glassworking recipes: stop repurposing them; guard over every recipe type | PROC | 9 | done |
| PAR-INFRA-37 | Recapture the ore overlay feel shots PAR-INFRA-16 missed: down face, distance set, 26.3 night/torch, tool timing, cave walk, fresh-world budget | INFRA | 8 | done |
| PAR-INFRA-38 | TfpCommands.reply: no blocking console wait when the reply future needs the caller's own thread | INFRA | 8 | done |
| PAR-INFRA-39 | Ore overlay adapter wiring through the IntegrationProvider SPI instead of Class.forName | INFRA | 8 | done |
| PAR-INFRA-40 | DeviceService.route: one diagnostic line when a device click is dropped (no kind, no block entity record) | INFRA | 8 | done |
| PAR-INFRA-41 | Ore overlay specks as bright as TFC's: down-face specks read dark brown (0.38 of TFC relative to the rock), side faces 26% dark | INFRA | 9 | done |
| PAR-INFRA-42 | Carrier policy matches our worldgen: lily pad, sea pickle and other placed placeholders are not presence absent; guard test | INFRA | 9 | done |
| PAR-FLUID-02 | Buckets: wooden bucket, red and blue steel buckets, fluid placement policy | FLUID | 9 | done |
| PAR-FLUID-03 | Salt water, spring water and river water in the world | FLUID | 9 | done |
| PAR-HUD-16 | HUD season per player: tfp_season follows the viewer's hemisphere (southern players see the opposite month's name) | HUD | 9 | done |
| PAR-PROC-08 | Barrel block: fluids, sealing, sealed and instant recipes, preservation | PROC | 9 | done |
| PAR-PROC-22 | Basket: TFC's bundle item (TFCBundleItem) with its size limits | PROC | 9 | done |
| PAR-SURV-08 | Alcohol and intoxication | SURV | 9 | done |
| PAR-SURV-12 | Dehydration self-test: a tfp:dehydration hit through the registry, armour reduces it | SURV | 9 | done |
| PAR-FLUID-04 | Hot spring healing and bubble columns | FLUID | 10 | done |
| PAR-FLUID-06 | River current in-game verification: a boat and a dropped item drift downstream | FLUID | 10 | done |
| PAR-FLUID-07 | Open-water drinking reads WorldWaterService: salt water in every salty TFC biome, river water fresh | FLUID | 10 | done |
| PAR-FLUID-08 | Bucket config keys in config.yml and the bucket look after every fill | FLUID | 10 | done |
| PAR-FLUID-09 | Bucket in-game verification: fill a wooden bucket from a river and place it | FLUID | 10 | done |
| PAR-HEAT-01 | Item heat glow: hot items glow by heat level on vanilla clients | HEAT | 10 | done |
| PAR-HEAT-02 | Heat tooltip in TFC colours and metal info lines (melting point, workable, weldable) | HEAT | 10 | done |
| PAR-HUD-19 | Health and thirst bar states: hurt/regeneration glow, absorption in the fill and the yellow surplus bar, thirst overheat tint | HUD | 10 | done |
| PAR-SURV-09 | Death, respawn and regeneration rules | SURV | 10 | done |
| PAR-INFRA-43 | Button and mud carriers vs our worldgen placeholders: no small-ground-piece or charcoal-pile carrier draws over placed loose-rock, stick or mud placeholders | INFRA | 10 | done |
| PAR-INFRA-44 | Heavy weighted pressure plate is a TFC player block (wrought-iron recipe): no transparent carrier may draw placed plates empty | INFRA | 10 | done |
| PAR-PROC-32 | Barrel CE blocks: tfp:wood/barrel/<wood> hitbox demands, configuration/barrel, in-game brining check | PROC | 10 | done |
| PAR-PROC-33 | Basket in-game verification: small items go in, a log is refused, bundle tooltip at GUI scale 2 and 3 | PROC | 10 | done |
| PAR-CLIM-01 | Calendar config, sleep skipping and doDaylightCycle sync | CLIMATE | 11 | done |
| PAR-CLIM-02 | Precipitation per position on Paper and the in-game snow-vs-rain check | CLIMATE | 11 | done |
| PAR-FLUID-10 | Vanilla bucket from a spring pool gives tfp:bucket/spring_water; waterlogged blocks in oceans give fresh water | FLUID | 11 | done |
| PAR-SURV-14 | Survival config keys in config.yml: keepNutritionAfterDeath, peaceful passive regeneration, naturalRegenerationModifier | SURV | 11 | done |
| PAR-FLUID-11 | Hot spring in-game verification: health rises standing in a hot spring | FLUID | 11 | done |
| PAR-FLUID-12 | Bucket config keys: buckets: section in config.yml, typed config and BucketModule reads the current value | FLUID | 11 | done |
| PAR-HEAT-03 | Hot items: burn damage while held, item entities cool in water and snow | HEAT | 11 | done |
| PAR-HEAT-14 | Item bars: the heat bar on hot items (TFC BarSystem HEAT) as a packet-time durability-style bar, with the shared item-bar layer | HEAT | 11 | done |
| PAR-PROC-11 | Composter | PROC | 11 | done |
| PAR-CLIM-03 | Snow and ice accumulation: lazy-vs-stepped equivalence and server check | CLIMATE | 12 | done |
| PAR-CLIM-15 | Self-test weather.precip_sample and PrecipitationService in ModuleServices | CLIMATE | 12 | done |
| PAR-CLIM-16 | Calendar sleep-skip self-test: calendar.sleep_skip on Paper and Folia | CLIMATE | 12 | done |
| PAR-CLIM-17 | Console /tfp debug chunk <x> <z> answers over RCON and reads precipitation through vanilla's rain gates | CLIMATE | 12 | done |
| PAR-FLUID-13 | Bucket double-use guard keyed on day time: buckets stop working when the daylight cycle is off | FLUID | 12 | | done |
| PAR-FLUID-14 | /tfp debug water names hot spring pool water spring | FLUID | 12 | done |
| PAR-INFRA-45 | InstallerTest teardown flake: rebuildWaitsUntilBetterHudIsNotReloading fails deleting @TempDir plugins | INFRA | 12 | done |
| PAR-DATA-16 | Pistons cannot move water sources: 1-tick piston pulses drop the water of waterlogged blocks (TFC's MC-130183 fix) | DATA | 12 | done |
| PAR-HEAT-04 | Heating recipes on devices: the 884 heating recipes as a shared service | HEAT | 12 | done |
| PAR-HEAT-05 | Fire starting: firestarter use-duration, flint and pyrite, StartFireEvent world rules | HEAT | 12 | done |
| PAR-HEAT-15 | Item bar config key: item-bars.heat in config.yml and TfpConfig, read on /tfp reload | HEAT | 12 | done |
| PAR-HEAT-16 | Item heat of list ingredients: metal tools and parts (item_heat parts_*) cannot be heated | HEAT | 12 | done |
| PAR-PROC-09 | Barrel GUI, fluid level visual, rain fill, barrel rack | PROC | 12 | done |
| PAR-PROC-34 | Composter CE block: tfc:composter hitbox rows and demands, configuration/compost, in-game check | PROC | 12 | done |
| PAR-CLIM-04 | Snow piles, ice piles, icicles and sea ice as CraftEngine blocks | CLIMATE | 13 | done |
| PAR-CLIM-12 | Climate-driven weather cycle: rain and thunder from the climate model replace vanilla's weather cycle; vanilla precipitation ticking off; local rain per player | CLIMATE | 13 | done |
| PAR-CLIM-18 | Snow catch-up after a calendar skip for chunks that stay loaded (/tfp time add, sleep skip) | CLIMATE | 13 | done |
| PAR-DATA-17 | Self-test piston.no_water_dupe: a real 1-tick piston pulse moves no water source, on Paper and Folia | DATA | 13 | done |
| PAR-DATA-18 | Vanilla rules left from PAR-DATA-10: mob equipment and enchantments, dispenser behaviours, bone meal and milk tests | DATA | 39 | done |
| PAR-HEAT-06 | Firepit block: placement, fuel, lazy temperature, ash, extinguish by rain and water | HEAT | 13 | done |
| PAR-HEAT-17 | Self-test heat.cook_meat: TFC HeatingItemTest on a real Paper and Folia server | HEAT | 13 | done |
| PAR-INFRA-46 | Static info tooltip lines: damage type and resistances, fuel, sluice/pan deposits, rock-category and glass-type lore | INFRA | 13 | done |
| PAR-PROC-10 | Log pile and charcoal pit | PROC | 13 | done |
| PAR-PROC-14 | Salads | PROC | 13 | done |
| PAR-INFRA-48 | NeighbourRunnerTest flake: chainOfTenPlacementsConvergesWithOneCommitPerChangedBlock sees 14 provisional writes instead of 10 | INFRA | 13 | done |
| PAR-PROC-35 | Barrel wiring: the block opens the barrel screen, buckets reach the tank, fluid surface visual, lazy rain fill, in-game check (rack and tap: PAR-PROC-38) | PROC | 13 | done |
| PAR-CLIM-05 | Seasonal grass tint: decide band resolution and make the colour change visible | CLIMATE | 14 | done |
| PAR-CLIM-13 | TFC time command completion: `/tfp time set` dayLength, monthLength, rain and clear, and the calendar tick rate | CLIMATE | 14 | done |
| PAR-CLIM-19 | Snow catch-up in-game verification: snow piles on plants, ice on a pool and the July melt from one room | CLIMATE | 14 | done |
| PAR-CLIM-20 | Climate weather cycle follow-up: in-game check (local rain per player), itest weather.cycle, PrecipitationService honours the weather flag | CLIMATE | 14 | done |
| PAR-HEAT-07 | Firepit GUI: fuel slots, input, outputs, temperature bar | HEAT | 14 | done |
| PAR-HEAT-10 | Torches: burnout to dead torch, relight, wall torches | HEAT | 14 | done |
| PAR-HEAT-18 | Fire starting in-game verification: a firestarter lights a stick and log pile into a firepit (self-test fire.start) | HEAT | 14 | blocked |
| PAR-HEAT-19 | Firepit loaded in game: configuration/devices/firepit.yml, TFC firepit models, self-test firepit.cook and the in-game check | HEAT | 14 | done |
| PAR-INFRA-23 | Hitbox carrier in-game verification: outlines, stand heights and client prediction of every carrier class | INFRA | 14 | done |
| PAR-INFRA-49 | Block coverage gate: every TFC blockstate is a loaded CE block, a family block, an owned id of a named parity item or an NP entry (reports/tfc-block-coverage.json) | INFRA | 14 | done |
| PAR-INFRA-50 | Feature item tooltips no item owns: item-list contents of sealed device items, crucible contents, fertilized egg and hatch day, javelin thrown damage, pan deposit, flower cutting, lamp fluid, glass batch steps | INFRA | 14 | done |
| PAR-INFRA-54 | Datagen summary counts CraftEngine block states (ce_states) and the hitbox report lists hide mappings sorted | INFRA | 14 | done |
| PAR-PROC-18 | Powderkeg | PROC | 14 | done |
| PAR-PROC-36 | Salad completion: salad screen art, eating and tooltip read the salad's own food data, in-game check | PROC | 14 | done |
| PAR-PROC-37 | Log pile, burning log pile and charcoal pile CE blocks: hitbox rows and demands, configuration/charcoal, support rule, in-game check | PROC | 14 | done |
| PAR-PROC-38 | Barrel rack, sideways barrel and tap pouring, and their in-game check | PROC | 14 | done |
| PAR-CLIM-06 | Seasonal packet rewrite JMH budget and visual checklist | CLIMATE | 15 | done |
| PAR-HEAT-08 | Grill on firepit: placement, five slots, cooking, GUI | HEAT | 15 | done |
| PAR-HEAT-09 | Cooking pot on firepit: boiling, soups and jams, pot fluid, GUI | HEAT | 15 | done |
| PAR-HEAT-20 | Firepit screen art and block wiring: datagen art for tfp:gui/firepit and its thermometer, FirepitDevice opens the screen, in-game check | HEAT | 15 | done |
| PAR-HEAT-11 | Candles, candle cakes and jack-o'-lanterns: burnout, lighting, carving | HEAT | 15 | done |
| PAR-CLIM-21 | Snow and ice stop with the weather flag off (SnowService live step and catch-up), as TFC onTickChunk | CLIMATE | 15 | done |
| PAR-HEAT-21 | Torches loaded in game: hitbox carriers, configuration/light/torch.yml, the dead torch look and the in-game burnout check | HEAT | 15 | | done |
| PAR-HEAT-22 | Dropped torches (go out in water, light leaves, thatch, log piles and pit kilns) and the torchTicks config key | HEAT | 15 | blocked |
| PAR-INFRA-51 | Held snow layer ghost on snow-layer carriers: the client predicts snow[n+1] on the forge, quern handstone, grill and piles | INFRA | 15 | blocked |
| PAR-CLIM-22 | Vanilla /time and the night skip on 26.x: CalendarModule.onTimeSkip throws NoSuchMethodError on TimeSkipEvent.getSkipReason() | CLIMATE | 15 | done |
| PAR-PROC-39 | Powderkeg CE block: tfc:powderkeg hitbox demands, configuration/powderkeg, in-game explosion check | PROC | 15 | done |
| PAR-PROC-40 | Charcoal sneak click adds two layers: ignore Paper's re-fired RIGHT_CLICK_BLOCK of the same click | PROC | 15 | done |
| PAR-INFRA-55 | Installer: rebuild the CraftEngine pack on the restart that applies a raised serverside-blocks (the first-install pack misses the blocks that could not allocate) | INFRA | 15 | done |
| PAR-INFRA-58 | Packet-layer boundary: move the PacketEvents-typed heat glow, item bar and block sound handlers under dev.tfp.paper.packet so ArchitectureTest.onlyThePacketLayerTouchesPacketEvents passes | INFRA | 15 | done |
| PAR-PROC-41 | Sealed barrel item keeps its contents: breaking a sealed barrel drops the barrel with its items and fluid (TFC BarrelComponent), placing it restores them | PROC | 15 | blocked |
| PAR-CLIM-26 | Client snow look: freezing seasonal variants carry a below-0.15 biome temperature | CLIMATE | 15 | done |
| PAR-CLIM-27 | Climate instrument blocks: CraftEngine entries, hitbox demands, models and animated parts | CLIMATE | 35 | done |
| PAR-CLIM-28 | Climate instruments: moving parts, device sources and support rules | CLIMATE | 36 | done |
| PAR-CLIM-29 | Climate instrument sources: blast furnace, bloomery and the barrel recipe timer | CLIMATE | 37 | open |
| PAR-CLIM-30 | Climate fog on vanilla clients: the client spike, foggy datapack variants and the seasonal remap hook | CLIMATE | 38 | done |
| PAR-HEAT-23 | Firepit look in game: TFC's lit and unlit models on their own carriers (axis z turned 90), and the in-game firepit check | HEAT | 16 | done |
| PAR-HEAT-28 | Torch burnout on chunk load leaves the client's block light at 14 until the chunk is sent again | HEAT | 16 | done |
| PAR-HEAT-29 | Dead torch outlines closer to TFC: widen the hitbox candidate dump to every small no-collision vanilla block and re-pick the dead torch carriers | HEAT | 16 | done |
| PAR-HEAT-35 | Firepit drawn by entity_renderer item displays on one hidden daylight detector hitbox carrier (instead of four model carrier states) | HEAT | 16 | done |
| PAR-CLIM-14 | Climate fog: TFC's foggy mornings (ClimateModel.getFog) on vanilla clients through biome fog distance, or a recorded substitute | CLIMATE | 16 | done |
| PAR-CLIM-23 | Verify vanilla /time and the night skip move the calendar in game on Paper 26.3 and 1.21.11 (PAR-CLIM-22 server logs) | CLIMATE | 16 | done |
| PAR-HEAT-12 | Firebox, stove and stove pot | HEAT | 16 | done |
| PAR-HEAT-26 | Candles loaded in game: hitbox carriers, configuration/light/candle/candle.yml and the in-game candle burnout check | HEAT | 16 | done |
| PAR-INFRA-52 | Grill and pot block sounds in game: NETHER_WART hit 0.25/0.5 and one break 1.0/0.8 per player on 1.21.11 and 26.x | INFRA | 16 | blocked |
| PAR-PHYS-01 | TFC falling blocks: canFallThrough, landing, damage | PHYS | 16 | done |
| PAR-WORLD-01 | Rendered region and river maps compared with TFC | WORLD | 16 | done |
| PAR-WORLD-02 | Bukkit biome binding and climate call sites on the real chunk data | WORLD | 16 | done |
| PAR-INFRA-56 | GUI button tooltips without the size/weight line: ItemSizeTooltipContributor skips items with the tfp:gui_button marker (firepit thermometer, pot, grill, barrel, powderkeg, knapping, tabs) | INFRA | 16 | done |
| PAR-INFRA-57 | Item-list tooltip summary: ContentsPdc writes tfp:view so sealed vessel, powderkeg, crucible and barrel items show their contents lines; deferred in-game barrel contents check of PAR-INFRA-50 | INFRA | 16 | open |
| PAR-INFRA-59 | Flaky OreOverlayServiceTest.blockChangeFastPathRidesWithThePacketAndThePatchAddsNothing: the fast path sometimes sends no spawn under a loaded full ./gradlew check | INFRA | 16 | done |
| PAR-HEAT-24 | Grill loaded in game: configuration/devices/grill.yml, TFC grill models, the grill screen art and the in-game grill check | HEAT | 17 | done |
| PAR-HEAT-33 | Firepit block state follows calendar jumps: the lit look turns off when a time skip burns the fuel out, smoke level in step after a douse | HEAT | 17 | done |
| PAR-INFRA-36 | Verify falling ores keep their face overlays in game (collapse and falling rich ore) | INFRA | 17 | blocked |
| PAR-PHYS-02 | Collapse runner: mining triggers collapses decided async, applied as falling blocks | PHYS | 17 | done |
| PAR-PHYS-03 | Landslides and gravity soils | PHYS | 17 | done |
| PAR-WORLD-03 | Worldgen performance: JMH chunk budget, vanilla baseline and exposed-gem decision | WORLD | 17 | done |
| PAR-WORLD-04 | TFC soil and gravel block set in generation: grass, dirt, coarse dirt, clay, peat, kaolin, mud, rock gravel | WORLD | 17 | done |
| PAR-WORLD-42 | Remaining soil blocks: duff and clay duff, grass path, muddy roots per soil | WORLD | 18 | done |
| PAR-WORLD-41 | Terrain writes the TFC soil blocks: soil variant per surface rule and the RockPalette surface ids in TfcTerrain | WORLD | 27 | done |
| PAR-PROC-44 | Barrel wiring in-game verification: GUI from the block, five buckets in and one out, half-tank surface, lazy rain fill (the checks PAR-PROC-35 closed without) | PROC | 17 | done |
| PAR-PROC-45 | Newer vanilla recipe removal in-game verification: copper tools/armour and spears do not craft, kept recipes still do (the check PAR-PROC-43 closed without) | PROC | 19 | done |
| PAR-SURV-15 | TFC exhaustion on Paper: passive hunger drain on the vanilla food bar, vanilla action exhaustion x0.4, thirst lost on every 4 exhaustion | SURV | 17 | blocked |
| PAR-SURV-16 | Food items and food tooltip in-game verification: the checks PAR-SURV-01 and PAR-SURV-02 closed without (sampled gives, TFC tooltip lines) | SURV | 17 | open |
| PAR-CLIM-24 | Self-test calendar.sleep_skip on 26.x: build the NIGHT_SKIP event without TimeSkipEvent$SkipReason (NoClassDefFoundError on Paper 26.3) | CLIMATE | 17 | done |
| PAR-CLIM-25 | Vanilla /time set <marker> on 26.x answers "already at time marker" although the time and the calendar moved (skip amount 0 after DaylightSync) | CLIMATE | 17 | done |
| PAR-HEAT-36 | Firepit carrier culls the top face of the block below: the floor shows through the gaps of TFC's firepit model | HEAT | 17 | done |
| PAR-HEAT-25 | Pot loaded in game: configuration/devices/pot.yml, pot screen art (tfp:gui/pot, thermometer, bubbles) and the in-game soup check | HEAT | 18 | open |
| PAR-GATHER-05 | Soil interactions: hoeing, paths, mud, rooted dirt, grass spread and rock spawning | GATHER | 18 | done |
| PAR-GATHER-11 | Grass spread, grass death and rising loose rocks: the lazy sweep of TFC's grass random tick | GATHER | 19 | done |
| PAR-GATHER-06 | Raw rock drops and mossy rock spreading | GATHER | 18 | done |
| PAR-PHYS-05 | Explosion collapses | PHYS | 18 | done |
| PAR-WORLD-05 | Ice sheets, sea ice and icebergs at generation; lava flows; remaining biome surface builders | WORLD | 18 | done |
| PAR-HEAT-30 | Firepit, grill and pot world rules: fuel thrown on top is gathered, a lit firepit burns entities standing on it (hot floor) | HEAT | 18 | done |
| PAR-HEAT-34 | Grill and pot burn-out after a calendar jump: the lit look turns off without a click, as the firepit since PAR-HEAT-33 | HEAT | 18 | done |
| PAR-INFRA-60 | Device automation: hoppers, droppers and pipes insert into and pull from CE device blocks by side (TFC SidedHandler, *EnableAutomation), wired for the done devices | INFRA | 18 | done |
| PAR-PROC-42 | Barrel world rules: redstone seals and unseals (barrelEnableRedstoneSeal), items dropped into an open upright barrel are gathered | PROC | 18 | done |
| PAR-INFRA-62 | Flaky CaseRunnerTest.aSkipThrownAtRunTimeIsASkipWithItsReason: the async-skip case times out after 200 ms under a loaded full ./gradlew check | INFRA | 18 | done |
| PAR-PROC-43 | Vanilla recipes newer than TFC's 1.21.1 follow TFC's removal policy: copper tools and armour, spears and other post-1.21.1 tool/armour recipes disabled | PROC | 18 | done |
| PAR-TOOL-01 | Stone tools: rock tool heads and tools as CE items, crafting from knapped heads | TOOL | 19 | done |
| PAR-TOOL-03 | In-game verification of metal tools and armour | TOOL | 19 | done |
| PAR-WORLD-06 | Cave tunnels and canyons (vanilla carvers with TFC carveBlock) | WORLD | 19 | done |
| PAR-WORLD-08 | Disc, pipe and kaolin veins; montane second pass; ore distribution statistics | WORLD | 19 | done |
| PAR-HEAT-31 | Firebox block loaded in game (full-cube pool, copper sounds, in-game screen check), stove and stove pot | HEAT | 19 | open |
| PAR-INFRA-63 | Device automation follow-ups: droppers and hopper minecarts into devices, screen-aware moves, per-device settle after a move, config.yml automation keys | INFRA | 19 | done |
| PAR-INFRA-64 | Device automation, remaining parts: hopper minecarts, screen-aware moves, barrel fluid settle after a hopper insert, config.yml automation keys with reload | INFRA | 20 | done |
| PAR-INFRA-66 | Device automation, screens and barrel: the device screens' own edit/reload instead of the 8-block pause, barrel fluid settle after a hopper insert | INFRA | 21 | open |
| PAR-PROC-20 | Knapping in-game verification: axe head from two granite loose rocks, clay and leather screens | PROC | 20 | done |
| PAR-TOOL-02 | Tool mechanics on Paper: TFC mining speed, durability rules, forging efficiency | TOOL | 20 | done |
| PAR-TOOL-04 | Swords, maces, macuahuitl: attack damage per TFC tier | TOOL | 20 | done |
| PAR-WORLD-07 | Aquifer barrier noise and the grass rule under carved surfaces | WORLD | 20 | done |
| PAR-WORLD-09 | Groundcover, loose rocks, deposits and boulders as CE blocks in generation | WORLD | 20 | done |
| PAR-WORLD-19 | Ore overlay S1 rerun on a world with caves: census, exposed-vein reference screenshots, GPU client cost | WORLD | 20 | done |
| PAR-WORLD-28 | Wire the cave and canyon tunnel masks into the terrain fill, and the in-game cave check | WORLD | 20 | done |
| PAR-WORLD-29 | Wire the aquifer barrier noise and the fluid support rule into the terrain fill, and the floating-fluid scan | WORLD | 21 | done |
| PAR-INFRA-61 | Static info tooltip lines in-game verification: damage type, resistances, fuel and rock lore (the check PAR-INFRA-46 closed without) | INFRA | 20 | done |
| PAR-GATHER-01 | Pick up loose rocks and groundcover with a right-click | GATHER | 21 | done |
| PAR-POT-01 | Placed items: put up to four items on the ground (and the straw/log stages of a pit kiln) | POTTERY | 21 | done |
| PAR-PROC-12 | Panning | PROC | 21 | done |
| PAR-WORLD-10 | Wood blocks: logs, wood, stripped, leaves, saplings as CE blocks | WORLD | 21 | done |
| PAR-WORLD-24 | Noise and noodle caves: TFC's density caves (cave cheese, cave layer, spaghetti, pillars, noodles) in the terrain fill, and the surface over carved blocks | WORLD | 21 | done |
| PAR-WORLD-31 | Groundcover CE blocks: loose rocks, small ores, groundcover types and deposits (datagen, hitbox demands, visuals) | WORLD | 21 | done |
| PAR-GATHER-03 | Tree felling: the whole tree falls when the trunk is cut with an axe | GATHER | 22 | done |
| PAR-GATHER-04 | Leaves: slow decay, movement slowdown, fallen leaves; plant movement slowdown | GATHER | 22 | done |
| PAR-WORLD-44 | Wood plant blocks: saplings, potted saplings, fallen leaves and twigs as CE blocks (datagen, carriers, visuals) | WORLD | 22 | done |
| PAR-GATHER-07 | Item placement interactions: vanilla stick, bone, feather, flint, rotten flesh and salt place as TFC groundcover; a snow item over plants places a snow pile | GATHER | 22 | done |
| PAR-GATHER-08 | Axe stripping: TFC logs and woods to their stripped forms, golden bamboo block to stripped bamboo | GATHER | 22 | done |
| PAR-INFRA-47 | Block flammability: TFC's flammable(flammability, fireSpreadSpeed) and ignitedByLava as CraftEngine burn settings on every generated block | INFRA | 22 | done |
| PAR-PHYS-04 | Support beams: blocks, connections, collapse prevention | PHYS | 22 | blocked |
| PAR-PROC-13 | Sluice | PROC | 22 | done |
| PAR-INFRA-29 | Loot delegation check: loose rocks and leaves drop by their TFC tables in the self-test | INFRA | 22 | blocked |
| PAR-WORLD-26 | Amethyst geodes: tfc:geode (hardened basalt shell, quartzite layers, amethyst ore) in the vein step | WORLD | 22 | done |
| PAR-WORLD-30 | Vein step wiring: biome vein passes, kaolin disc, projected, near-lava and rock-writing veins in TfcTerrain | WORLD | 22 | done |
| PAR-WORLD-32 | Wire groundcover, boulders, cave loose rocks and the new soil features into TfcTerrain with CE blocks, and the in-game groundcover check | WORLD | 22 | done |
| PAR-POT-02 | Unfired and fired pottery items, clay knapping outputs | POTTERY | 23 | done |
| PAR-TOOL-05 | Javelins: throwing, damage, pickup, javelin AI for skeletons | TOOL | 23 | done |
| PAR-TOOL-06 | Knife, scythe and hoe area behaviours; remaining tool items (saw, chisel, propick, tuyere, shears heads) | TOOL | 23 | done |
| PAR-WORLD-11 | Tree generation: forest placement on every chunk, dead forests, mangroves | WORLD | 23 | done |
| PAR-GATHER-09 | Natural logs: the branch_direction property on log and wood blocks, set by tree generation | GATHER | 24 | done |
| PAR-POT-03 | Pit kiln: straw and log stages, lighting, lazy firing | POTTERY | 24 | done |
| PAR-POT-04 | Small vessel: inventory, melting contents into metal, pouring | POTTERY | 24 | done |
| PAR-TOOL-07 | Shields per metal | TOOL | 24 | done |
| PAR-WORLD-12 | Plant blocks: all TFC plant shapes as CE blocks in generation | WORLD | 24 | done |
| PAR-TOOL-11 | Obsidian tools: obsidian axe, hammer, hoe, javelin, knife and shovel with their heads, knapped from obsidian shards | TOOL | 24 | done |
| PAR-GATHER-10 | Plant and thatch movement slowdown: TFC's per-plant speed factors registered with the slow-block service | GATHER | 25 | done |
| PAR-GATHER-02 | Harvest plants, leaves, twigs and fallen leaves by TFC loot tables | GATHER | 25 | done |
| PAR-GATHER-13 | Snow item over plants: provide the SnowPilePlacer service from the weather blocks | GATHER | 26 | done |
| PAR-GATHER-14 | Rising loose rocks on grass: the rock raised by TFC's grass random tick in spring frost | GATHER | 26 | done |
| PAR-GATHER-15 | Leaves placed by a player are persistent: TFC's getStateForPlacement sets persistent for a placed leaf block | GATHER | 27 | done |
| PAR-POT-05 | Molds: filling from a vessel/crucible, solidifying, breaking out the casting | POTTERY | 25 | done |
| PAR-POT-10 | Small vessel screen art: the textured four-slot GUI | POTTERY | 25 | open |
| PAR-POT-11 | item_heat data for fired pottery: vessels, molds, jugs | POTTERY | 25 | done |
| PAR-WORLD-13 | Remaining plant features: vines, kelp, epiphytes, creeping plants, cacti, algae, atoll corals | WORLD | 25 | done |
| PAR-WORLD-14 | Volcanic features: springs, rivulets, flood-fill lakes, calderas of other cones | WORLD | 25 | done |
| PAR-WORLD-25 | TFC corals and sea pickle as CE blocks: 5 colours x (coral, coral fan, wall fan) live and dead, the salt-water sea pickle, dying out of water, drops; replace the vanilla coral placeholders in generation | WORLD | 25 | done |
| PAR-FARM-12 | Wild plant block rules: survival per shape, cactus damage, tall halves, plant stacking, flower shearing, long growth of vines, kelp, kelp trees and branching cacti | FARM | 26 | done |
| PAR-POT-06 | Large vessel (and glazed): nine slots, sealing, preservation, automation | POTTERY | 26 | open |
| PAR-POT-07 | Jugs and glass bottles: fill, drink, pour, break | POTTERY | 26 | done |
| PAR-WORLD-15 | Volcanic and spring CE blocks: hardened rock, magma blocks, spring water look | WORLD | 26 | done |
| PAR-WORLD-27 | Wire VolcanicFeatures into the Paper terrain: rivulets, crater lakes, tuya and stratovolcano fissures, pumice and obsidian patches, springs; spring water of crater lakes | WORLD | 26 | done |
| PAR-WORLD-16 | Spawn and world settings: spawn offset, forced TFC gamerules, structures off, dimension policy | WORLD | 26 | done |
| PAR-FARM-01 | Farmland block: hydration from water (async), nutrients, hoe overlay readout | FARM | 27 | done |
| PAR-FLUID-05 | Fluid container tooltip in-game verification: debug fill of a jug shows the fluid line | FLUID | 27 | done |
| PAR-METAL-01 | Alloy display and fluid alloy glue for vessels and crucibles | METAL | 27 | done |
| PAR-POT-12 | Jug fluid overlay and the four glass bottle items | POTTERY | 27 | open |
| PAR-POT-08 | Drying bricks, mud bricks and thatch blocks | POTTERY | 27 | open |
| PAR-POT-09 | Bowls and powder bowls (wooden and ceramic bowl blocks) | POTTERY | 27 | done |
| PAR-CLIM-11 | Climate in worlds without the TFC generator: biome-based climate model for the Nether, the End and vanilla worlds (temperature, rainfall, consumers) | CLIMATE | 27 | done |
| PAR-SURV-13 | Intoxication in-game verification: beer from a jug, drunk chat | SURV | 27 | open |
| PAR-WORLD-45 | Place CE hardened rock and per-rock magma in the volcanic features; TFC magma behaviour on the CE magma block | WORLD | 27 | done |
| PAR-FARM-02 | Fertilizers | FARM | 28 | done |
| PAR-FARM-03 | Crop blocks: all 29 crops in every shape, seeds, dead and wild crops | FARM | 28 | done |
| PAR-METAL-02 | Fire ingot mold and bell casting; casting crafting | METAL | 28 | done |
| PAR-METAL-03 | Anvil blocks: rock anvils and metal anvils with items shown on top | METAL | 28 | done |
| PAR-METAL-22 | Small vessel stores a mixed alloy: fills and melts write tfp:alloy, the vessel tooltip shows the mix | METAL | 28 | done |
| PAR-WORLD-22 | TFC world generator settings as server config (flat bedrock, spawn distance and centre, climate scales, continentalness, grass density); close the spawn-settings and river open gaps | WORLD | 28 | done |
| PAR-WORLD-43 | Wire every biome's own surface builder (BiomeSurfaces) and the full surface block set into the Paper terrain | WORLD | 28 | done |
| PAR-FARM-04 | Crop growth glue: lazy calendar growth, climate checks, death, harvest yields | FARM | 29 | done |
| PAR-FARM-06 | Stationary berry bushes: lifecycle, picking, propagation | FARM | 29 | done |
| PAR-METAL-04 | Anvil plan selection GUI | METAL | 29 | done |
| PAR-METAL-08 | Charcoal forge block entity: creation from a charcoal pile, lighting, continuous heating of held items | METAL | 29 | done |
| PAR-SURV-11 | Item size in-game check: anvil refused by a chest, two anvils overburden, size tooltip | SURV | 29 | open |
| PAR-WORLD-23 | TFC rock layers: the rock layer settings walk and per-rock rock settings replace PlaceholderRocks (rock stacking, sand colour, karst surface rocks) | WORLD | 29 | done |
| PAR-FARM-05 | Wild crops and spreading crops (pumpkin, melon), rotten pumpkins | FARM | 30 | done |
| PAR-FARM-07 | Spreading bushes, canes and cranberries | FARM | 30 | done |
| PAR-METAL-05 | Anvil forging GUI: step buttons, target and progress markers, hammering, forging bonus | METAL | 30 | done |
| PAR-PROC-16 | Glassworking: blowpipe operations (blow, roll, stretch, pinch, flatten, saw) | PROC | 30 | open |
| PAR-FARM-08 | Fruit trees: saplings, growing branches, seasonal leaves, fruit | FARM | 31 | done |
| PAR-METAL-06 | Welding on the anvil (flux) | METAL | 31 | done |
| PAR-METAL-09 | Charcoal forge GUI and insulation/chimney multiblock scan | METAL | 31 | done |
| PAR-METAL-21 | Forging tooltips: forging bonus with the smith's name, and 'Worked' on partly forged items | METAL | 31 | done |
| PAR-PROC-17 | Glass basin and hot poured glass; poured glass blocks and glass items | PROC | 31 | open |
| PAR-DATA-01 | Commands under /tfp: player, count block, clear world, add trim, weather hooks | DATA | 32 | done |
| PAR-FARM-09 | Bananas | FARM | 32 | done |
| PAR-FARM-10 | Sapling growth into trees | FARM | 32 | done |
| PAR-METAL-11 | Bellows: push, air boost, crank, animation | METAL | 32 | done |
| PAR-CLIM-07 | Weather command and TFC weather state (`/tfp weather`) | CLIMATE | 33 | open |
| PAR-FARM-11 | Plant regrowth, plant growth/spread, flower cuttings | FARM | 33 | done |
| PAR-FARM-13 | Plant info and fertilizer tooltips: climate range, nutrients, growth time and lifecycle on seeds and saplings; N/P/K on fertilizers | FARM | 33 | done |
| PAR-METAL-12 | Crucible block and GUI | METAL | 33 | done |
| PAR-TOOL-08 | Prospector's pick: scan, result message, `/tfp propick` | TOOL | 33 | done |
| PAR-CLIM-08 | Lightning rules: enableLightning, log stripping, fire start | CLIMATE | 34 | done |
| PAR-CLIM-09 | Climate instruments: thermometer, anemometer, vane, calendar clock | CLIMATE | 34 | done |
| PAR-DATA-02 | Advancements: triggers, awards and presentation | DATA | 34 | done |
| PAR-METAL-13 | Channels and mold tables: pouring flow | METAL | 34 | done |
| PAR-HEAT-32 | Firebox kiln and heat consumers: placed items heat in an insulated chamber, the crucible above heats (self-test), hot floor, comparator | HEAT | 34 | open |
| PAR-DATA-03 | Advancement criteria wiring across features | DATA | 35 | open |
| PAR-DATA-04 | Field guide: decide the form and convert the book | DATA | 35 | open |
| PAR-HEAT-13 | Fire behaviour: fire arrows, dousing, fire spread limits | HEAT | 35 | done |
| PAR-METAL-14 | Bloomery multiblock and bloom | METAL | 35 | done |
| PAR-DATA-05 | Localisation: Paper lang loading, all TFC locales, plugin strings | DATA | 36 | done |
| PAR-METAL-15 | Blast furnace, tuyere and steel path | METAL | 36 | open |
| PAR-METAL-16 | Ingot and double-ingot piles | METAL | 36 | done |
| PAR-TOOL-10 | Bows and glow arrows | TOOL | 36 | done |
| PAR-DATA-06 | Configuration parity: every TFC ServerConfig/CommonConfig key in config.yml | DATA | 37 | done |
| PAR-DATA-07 | Damage types and mob effects | DATA | 37 | open |
| PAR-DATA-08 | Sounds: TFC sound events into the pack and played by features | DATA | 37 | open |
| PAR-METAL-18 | Bells (bronze, brass) | METAL | 37 | done |
| PAR-DATA-09 | Particles: action-driven TFC particles with vanilla equivalents | DATA | 38 | done |
| PAR-DATA-10 | Dispenser behaviours, bonemeal, vanilla rule overrides | DATA | 38 | done |
| PAR-DATA-11 | Paintings, armour trims, misc data registries | DATA | 38 | open |
| PAR-FAUNA-01 | Entity model import: TFC bbmodels and Java animations converted to BetterModel | FAUNA | 38 | done |
| PAR-DATA-12 | Block info readout (Jade-like tooltips) on the actionbar | DATA | 39 | open |
| PAR-DATA-13 | TFC pin drift check in CI | DATA | 39 | done |
| PAR-FAUNA-02 | Entity models for Java-only TFC models via the rig generator | FAUNA | 39 | done |
| PAR-FAUNA-21 | Animal framework completion: species behaviour SPI, goal replacement, model re-apply checks | FAUNA | 39 | done |
| PAR-DECO-01 | Wood families loaded in CraftEngine: planks, stairs, slabs, fences, walls with our block_state templates | DECO | 40 | done |
| PAR-DECO-10 | Ropes, rope anchors and rope knots | DECO | 40 | open |
| PAR-FAUNA-03 | Entity texture variants, gendered and aged textures, render review | FAUNA | 40 | done |
| PAR-FAUNA-04 | Spawning by climate on Paper | FAUNA | 40 | done |
| PAR-DECO-17 | Wood workbenches: the 20 tfc:wood/workbench blocks as CE blocks that open the 3x3 crafting grid | DECO | 41 | open |
| PAR-FAUNA-05 | Livestock: feeding, familiarity and breeding interactions | FAUNA | 41 | done |
| PAR-PROC-06 | Loom: weaving cloth, burlap, wool, silk | PROC | 41 | done |
| PAR-PROC-07 | Sewing table GUI | PROC | 41 | open |
| PAR-PROC-15 | Scribing table | PROC | 41 | done |
| PAR-FAUNA-06 | Animal products: milk, wool, eggs; sheared look | FAUNA | 42 | done |
| PAR-FAUNA-08 | Livestock AI and roster: graze, follow food, herd; all 18 domestic species | FAUNA | 42 | done |
| PAR-FAUNA-09 | Prey AI and wild animals | FAUNA | 42 | done |
| PAR-FAUNA-11 | Aquatic animals: fish, squid, jellyfish, dolphins, orcas, manatees; mob buckets | FAUNA | 42 | done |
| PAR-FAUNA-22 | Familiarity readout: sneak-looking at a TFC animal within 5 blocks shows its familiarity and pregnancy on the action bar (NP-UI-01 substitute for IngameOverlays.FAMILIARITY) | FAUNA | 42 | done |
| PAR-FAUNA-07 | Nest box: laying, hatching, GUI | FAUNA | 43 | done |
| PAR-FAUNA-10 | Predator AI: hunting, home range, sleep schedule, packs | FAUNA | 43 | done |
| PAR-FAUNA-12 | Amphibians: turtles, penguins, seals, crocodiles, frogs (play dead, land/water) | FAUNA | 43 | done |
| PAR-FAUNA-13 | Pests and critters: rats raiding containers, lobsters, crayfish, isopods | FAUNA | 43 | done |
| PAR-FAUNA-25 | Mob bucket items: the 20 TFC fish buckets as CE items (follow-up of PAR-FAUNA-11) | FAUNA | 43 | done |
| PAR-HUD-20 | Ink splatter: the full-screen ink and glow-ink overlay while the player has tfc:ink / tfc:glow_ink (squid and octopoteuthis hits) | HUD | 43 | open |
| PAR-FAUNA-14 | Pets: dogs and cats with commands | FAUNA | 44 | done |
| PAR-FAUNA-15 | Horses, donkeys and mules: riding, breeding, chests | FAUNA | 44 | open |
| PAR-FAUNA-17 | Hunting drops, butchering, hides | FAUNA | 44 | done |
| PAR-FAUNA-18 | Fishing: rod, hook, bait | FAUNA | 44 | open |
| PAR-FAUNA-23 | Fertilized egg incubation bar (TFC BarSystem EGG) on the shared item-bar layer | FAUNA | 44 | open |
| PAR-FAUNA-24 | Wild raids and armadillo scutes: fox berry picking, rabbit garden raids, calendar scutes | FAUNA | 44 | open |
| PAR-FAUNA-27 | Nest box block and ground-laying hand-off (follow-up of PAR-FAUNA-07) | FAUNA | 44 | done |
| PAR-DECO-02 | Wood logic: doors, trapdoors, fence gates, buttons, pressure plates | DECO | 45 | done |
| PAR-FAUNA-16 | Camels (dromedary, bactrian) | FAUNA | 45 | open |
| PAR-FAUNA-19 | Lobster traps | FAUNA | 45 | open |
| PAR-FAUNA-20 | Pluckable feathers, animal sounds | FAUNA | 45 | open |
| PAR-FAUNA-29 | Pack predator taming and temptation, predator sleep sound (follow-up of PAR-FAUNA-10) | FAUNA | 45 | open |
| PAR-DECO-03 | Signs and hanging signs (per wood, per metal hanger) | DECO | 46 | open |
| PAR-DECO-04 | Chests, trapped chests and barrel racks per wood | DECO | 46 | open |
| PAR-DECO-05 | Tool racks, shelves, bookshelves, lecterns | DECO | 46 | open |
| PAR-DECO-06 | Rock families: bricks, cobble, smooth, stairs, slabs, walls, aqueducts, spikes, pressure plates, buttons | DECO | 46 | open |
| PAR-FAUNA-30 | Crocodile without the aggressive swim goal, diving in water (follow-up of PAR-FAUNA-12) | FAUNA | 46 | open |
| PAR-FAUNA-31 | Amphibian play-dead pose: penguin and turtle models (follow-up of PAR-FAUNA-12) | FAUNA | 46 | open |
| PAR-FAUNA-32 | Pest infestation of large vessels and nest boxes (follow-up of PAR-FAUNA-13) | FAUNA | 46 | open |
| PAR-DECO-07 | Mud bricks, alabaster, aggregate, fire bricks, wattle and daub, thatch | DECO | 47 | done |
| PAR-DECO-08 | Sand, sandstone families, hardened clay, halite, coal, calcite, misc natural blocks | DECO | 47 | done |
| PAR-DECO-09 | Metal decoration blocks: metal blocks (and weathered), bars, chains, grates, trapdoors | DECO | 47 | done |
| PAR-TOOL-09 | Chisel: smoothing, slabs and stairs with mode cycling | TOOL | 47 | open |
| PAR-INFRA-53 | Hitbox carrier in-game verification of the classes added by family items: piles, rocks, ores, groundcover, placed items, containers, seals, heavy_core | INFRA | 47 | open |
| PAR-DECO-11 | Boats, chest boats and minecarts | DECO | 48 | open |
| PAR-DECO-12 | Misc blocks: crates, buoys, cakes, golden bamboo, palm mosaic, tree roots, light block | DECO | 48 | open |
| PAR-METAL-17 | Metal weathering of copper-family blocks | METAL | 48 | open |
| PAR-METAL-19 | Metal lamps: fuel, light, lit state | METAL | 48 | open |
| PAR-WORLD-20 | Underground decoration and erosion: cave spikes and columns, calcite, cave vegetation, hanging roots, underground lakes, the erosion pass | WORLD | 48 | open |
| PAR-DECO-13 | Potted plants and krummholz | DECO | 49 | open |
| PAR-DECO-14 | Thatch bed: sleeping and spawn setting | DECO | 49 | open |
| PAR-DECO-15 | Visual budget re-check after all block families | DECO | 49 | open |
| PAR-MECH-01 | Rotation network blocks: axle, encased axle, gearbox, clutch, creative motor | MECH | 49 | done |
| PAR-DECO-16 | Fire brick extras and smooth mud bricks: reinforced fire bricks, fire brick shelf, fireproof door, fire clay block | DECO | 49 | open |
| PAR-HEAT-27 | Candle cake bite and pumpkin carving hooked to the cake and pumpkin blocks | HEAT | 49 | open |
| PAR-MECH-02 | Rotation visuals: spinning axles and gears | MECH | 50 | done |
| PAR-MECH-11 | Rotation blocks: axle carriers in the hitbox allocation, startup re-add, in-game check of PAR-MECH-01 | MECH | 50 | done |
| PAR-MECH-05 | Crankshaft and powered bellows | MECH | 50 | open |
| PAR-MECH-06 | Quern automation | MECH | 50 | done |
| PAR-MECH-07 | Trip hammer | MECH | 50 | open |
| PAR-WORLD-21 | Untracked surface features: forest-floor patches, tide pools, guano islands, bamboo, sea stack patches and five volcanic ids | WORLD | 50 | open |
| PAR-WORLD-46 | Write the CE plant blocks in generation: TfcTerrain places tfp:plant/* through PlantBlocks.ceRef, placeholders only as fallback | WORLD | 27 | done |
| PAR-WORLD-47 | Remaining plant shapes as CE blocks: water plants (waterlogged), creeping, epiphytes, vines, weeping/twisting columns, kelp, cacti, bamboo, intertidal plant-likes | WORLD | 27 | done |
| PAR-WORLD-48 | Plant lifecycle looks, climate tint and soil rules: TFC's seasonal plant stages (tfc:plant model loader), per-climate grass/foliage colour, TFC canSurvive per plant type | WORLD | 28 | done |
| PAR-WORLD-49 | Surface builder over every exposed floor: cave floors, arch and overhang floors get TFC's surface states (whole-column scan) | WORLD | 23 | done |
| PAR-WORLD-50 | CE coral and sea pickle blocks: datagen for the 30 corals and tfp:sea_pickle on their vanilla twin carriers, items, loot, names; write them in generation; drops on support loss; wall fan placement | WORLD | 27 | blocked |
| PAR-WORLD-51 | Per-world TFC settings in every climate consumer (HUD, snow, precipitation, weather, seasonal remap, plant effects, chunk climate) | WORLD | 29 | done |
| PAR-WORLD-52 | A world's own rock_layer_settings in every rock consumer (terrain fill, volcanoes, groundcover, karst), not only TFC's preset | WORLD | 30 | done |
| PAR-WORLD-53 | Visual budget for wood plants: the tripwire pool has 3 free states, saplings, fallen leaves and twigs need 101 | WORLD | 22 | done |
| PAR-POT-13 | Placed item block: hitbox demand, datagen wiring, config keys, use-duration spike, in-game check | POTTERY | 22 | open |
| PAR-WORLD-60 | Volcanic feature blocks with rock index -1 refuse chunks (VolcanoBlocks.rockBlockId) | WORLD | 28 | done |
| PAR-WORLD-59 | Plant pools into the hitbox selector: PlantBlockGen and PlantShapeGen stop choosing tripwire/sugar_cane/sapling/kelp themselves | WORLD | 28 | done |
| PAR-WORLD-61 | Plant stage looks at packet time: blooming/dormant client states per plant from the selector's left-over pool states, chosen per viewer by PlantLifecycle | WORLD | 29 | open |
| PAR-WORLD-62 | Ore overlay default radius from the PAR-WORLD-19 census | WORLD | 29 | done |
| PAR-WORLD-54 | Bubble columns above the CE magma blocks: drag-down columns that survive vanilla's support check, and the hot spring tracker | WORLD | 30 | done |
| PAR-MECH-03 | Water wheel | MECH | 51 | done |
| PAR-MECH-04 | Windmill and blades | MECH | 51 | done |
| PAR-MECH-08 | Power loom | MECH | 51 | open |
| PAR-MECH-09 | Pump and pipes | MECH | 51 | done |
| PAR-MECH-12 | Spinning shafts on the wire: speed hook, packet sender, axle without a static element, in-game check | MECH | 51 | done |
| PAR-MECH-13 | Powered quern in game: handstone spin, grind sounds and particles, hopper and axle screenshots | MECH | 52 | open |
| PAR-DATA-14 | Vanilla rule hooks no item owns: lava + water hardened rocks, one-layer snow and snow on leaves, vines on TFC leaves, tadpoles, intoxicated chat, predators in boats, TFC mob armour, piglin gold, aqueduct and sluice sources, bamboo growth | DATA | 51 | open |
| PAR-MECH-10 | Bladed axle | MECH | 52 | blocked |
| PAR-MECH-14 | Pump in game: a crankshaft powers a pump that moves water 10 pipes up (PAR-MECH-09 in-game check and crankshaft wiring) | MECH | 52 | open |
| PAR-MECH-15 | Axle carriers from the hitbox allocation: TFC axle outline rows, demands, RotationBlocks reads the agreed carriers | MECH | 52 | done |
| PAR-MECH-16 | Rotation in game: PAR-MECH-01 in-game steps and the rotation.stress itest on Paper and Folia | MECH | 53 | open |
| PAR-MECH-17 | Water wheel and windmill as rotation network sources: source states in core/rotation, the WaterWheelNetwork and WindmillNetwork services | MECH | 53 | open |
| PAR-MECH-18 | Water wheel and windmill in game: CE blocks, wheel and blade items, spinning models, in-game checks of PAR-MECH-03 and PAR-MECH-04 | MECH | 54 | open |
| PAR-MECH-19 | Spinning shafts in game: axles turn at the right speed and hand on 1.21.11 and 26.x (PAR-MECH-12 in-game check) | MECH | 53 | open |
| PAR-MECH-20 | Bladed axle as TFC: the axle with a steel blade, its network node and spinning blade, the trip hammer's drive | MECH | 53 | open |
| PAR-REL-02 | Performance budgets in CI (JMH) and threading gate | REL | 52 | done |
| PAR-REL-03 | Dependency and version updates (MC, Paper, Folia, CE, BetterModel, BetterHud) | REL | 52 | done |
| PAR-REL-04 | Stone-age run self-test | REL | 52 | done |
| PAR-REL-05 | Metal run self-test | REL | 53 | open |
| PAR-REL-06 | Husbandry run self-test and 200-animal load test | REL | 53 | blocked |
| PAR-REL-10 | Art backlog: replace every placeholder texture | REL | 53 | blocked |
| PAR-REL-11 | Design docs and skills kept current | REL | 53 | done |
| PAR-DATA-15 | TFC's ten creative tabs as CraftEngine item browser categories, in TFC's order with TFC's icons and names | DATA | 53 | open |
| PAR-REL-07 | Performance audit with 50 bots | REL | 54 | open |
| PAR-FAUNA-26 | Animal AI under load: livestock avoid and prey flee sensing within the tick budget; prey moveTo on Folia | FAUNA | 54 | blocked |
| PAR-REL-08 | Full playthrough and visual QA | REL | 55 | open |
| PAR-REL-09 | Release packaging | REL | 56 | blocked |
| PAR-FARM-14 | Stationary berry bush blocks: CraftEngine states, models, hitbox carriers, loot, worldgen placement | FARM | 30 | done |
| PAR-FARM-18 | Worldgen placement of stationary berry bushes | FARM | 31 | done |
| PAR-FARM-19 | Worldgen placement of spreading bushes and cranberries | FARM | 32 | done |
| PAR-FARM-15 | Spreading bush blocks: CraftEngine states, models, hitbox carriers, loot, worldgen placement | FARM | 31 | done |
| PAR-FARM-16 | Fruit tree blocks: CraftEngine states, models, hitbox carriers, loot, splicing | FARM | 32 | done |
| PAR-FARM-17 | Banana blocks: CraftEngine states, models, hitbox carriers, loot, worldgen placement | FARM | 33 | done |
| PAR-METAL-23 | Crucible molds: pour into and out of the crucible, output slot, held crucible tooltip | METAL | 34 | done |
| PAR-METAL-25 | Charcoal forge: melting into extra-slot molds, the grilled trait on outputs, picking up dropped fuel | METAL | 34 | done |
| PAR-METAL-24 | Crucible channel nubs: the crucible draws TFC's crucible_connection toward adjacent channels | METAL | 35 | open |
| PAR-FARM-20 | Verification: crops grow, die and yield in game (itest crop.grow and screenshots) | FARM | 34 | done |
| PAR-FARM-23 | Fruit and wild spreading crop blocks: tfp:fruit pumpkin/melon (and rotten), hitbox rows, loot, wild crop worldgen fruit | FARM | 31 | open |
| PAR-FARM-24 | Wire SpreadingFruit into the crop device: fruit placement at full growth, crowding limit, fruit sides | FARM | 31 | open |
| PAR-FARM-21 | Flower cutting item and plant block items: tfp:flower_cutting content | FARM | 34 | done |
| PAR-FARM-25 | CraftEngine blocks and runtime for the growing plant shapes: vines, columns, kelp, kelp trees, branching cacti | FARM | 35 | done |
| PAR-FARM-20 | Verification: crops grow, die and yield in game (itest crop.grow and screenshots) | FARM | 34 | done |
| PAR-PROC-46 | Panning in-game verification: deposit pickup, water hint, 6 s pan, small ores | PROC | 33 | open |
| PAR-PROC-47 | Sluice in-game verification: placement of both halves, water, soil intake, loot, break | PROC | 33 | open |
| PAR-PROC-48 | Loom in-game verification and the in-progress cloth visual | PROC | 42 | open |
| PAR-PROC-49 | Scribing table and sewing table blocks (tfp:wood/scribing_table/<wood>, tfp:wood/sewing_table/<wood>) | PROC | 41 | done |
| PAR-PROC-50 | Scribing table in-game verification and TFC screen art | PROC | 42 | open |
| PAR-INFRA-65 | Block flammability in-game verification and the flammability.spread self-test case | INFRA | 48 | done |
| PAR-DECO-18 | Sandstone stairs, slabs and walls and the calcite spike | DECO | 49 | open |
| PAR-POT-14 | Pit kiln block: CraftEngine entry, hitboxes and look per stage, config keys, self-test kiln.fire, in-game check | POTTERY | 25 | open |
| PAR-FARM-27 | Worldgen placement of fruit trees, bananas and wild spreading crop fruit | FARM | 34 | done |
| PAR-FARM-28 | Fruit tree splicing and hoe overlay lines for fruit trees and bananas | FARM | 34 | done |
| PAR-FARM-23 | Fruit and wild spreading crop blocks: tfp:fruit pumpkin/melon (and rotten), hitbox rows, loot, wild crop worldgen fruit | FARM | 31 | done |
| PAR-FARM-24 | Wire SpreadingFruit into the crop device: fruit placement at full growth, crowding limit, fruit sides | FARM | 31 | done |

| PAR-DECO-19 | Alabaster and mud brick stairs, slabs and walls; wattle and daub | DECO | 49 | open |
| PAR-DECO-20 | Wood log, stripped log, wood and leaves block models missing from the pack: the blocks draw as missing-texture cubes in game | DECO | 21 | open |
| PAR-WORLD-55 | In-game verification of PAR-WORLD-11 and PAR-WORLD-44: dead forests and mangroves; wood plant blocks | WORLD | 24 | open |
| PAR-WORLD-56 | Per-world TFC settings in the remaining climate readers (fog, crops, farmland, wild crops, rising rocks, regrowth, water terrain, calendar sample) | WORLD | 30 | done |
| PAR-FARM-29 | Growing plant shapes part 2: vine, kelp tree and branching cactus growth, survival, per-shape outlines, worldgen wiring | FARM | 36 | done |
| PAR-FARM-30 | Growing plant shapes part 3: per-shape outlines and the saguaro fruit block | FARM | 37 | done |
| PAR-METAL-26 | Bellows visuals: pressed animation, TFC model and the blow sound | METAL | 33 | open |
| PAR-PHYS-07 | Collapse follow-ups: Folia stress self-test, TFC rock-slide sounds in the pack, collapse server config keys | PHYS | 18 | open |
| PAR-FAUNA-28 | 200-animal load: staggered check phase and goal spikes within the tick budget (follow-up of PAR-FAUNA-26) | FAUNA | 55 | blocked |
| PAR-WORLD-58 | Waterlogged groundcover: TFC's FLUID property on the CE loose rocks, small ores and groundcover, wet hitbox rows, and the worldgen writing them under water | WORLD | 23 | open |
| PAR-WORLD-57 | In-game verification of PAR-WORLD-31, PAR-WORLD-32 and PAR-WORLD-42: groundcover, boulders and cave rocks in a fresh world; the remaining soil blocks | WORLD | 24 | open |
| PAR-DECO-21 | Metal bars and chains (follow-up of PAR-DECO-09) | DECO | 49 | open |
| PAR-FAUNA-33 | 200-animal load: spread the path starts of the livestock and prey goals (single-call spikes) (follow-up of PAR-FAUNA-28) | FAUNA | 56 | open |
| PAR-POT-15 | Bowl automation: hoppers fill and empty powder bowls, the powder layer follows (follow-up of PAR-POT-09) | POTTERY | 28 | open |
