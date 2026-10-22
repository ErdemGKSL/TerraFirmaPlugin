# Parity backlog (plans/parity)

The master backlog for reaching full TerraFirmaCraft parity. Every piece of work left between this repository and TFC (pinned
`c3b4d9536b82e5a9746dd793c586720a74108057`) is one **item file** here. The parity loop walks the items wave by wave; items of the
same wave touch disjoint files, so up to four workers can code one wave in parallel. The project owner drives the loop through
this folder (HUD and charcoal forge work included: there is no out-of-loop category).

## Files

| file | what it is |
|---|---|
| `README.md` | this page: how the folder works, legend, coverage matrix, waves |
| `PROTOCOL.md` | the exact rules a worker follows to take, do and close one item |
| `STATUS.md` | one row per item (id, title, area, wave, status); updated by the worker in the same commit as the item file |
| `INVENTORY.md` | the TFC feature inventory with the current parity status of each feature and the item that closes it, features already at parity, and the features impossible on vanilla clients with their closest equivalent |
| `items/<AREA>/<ID>.md` | one work item; YAML front matter holds every field the loop parses |
| `NOT-PARITY.md` | what stays outside parity on purpose (owner decisions), what is impossible on vanilla clients (with the substitute to build) and what is deferred; entries are `audit: skip` and are never re-added as items |

Older planning files stay as history and context: `plans/0*-m*.md`, `plans/90-cross-cutting.md`, `plans/gaps.md`,
`plans/hud-layered-redesign.md`. Their open entries were migrated here; each item lists the old ids in `aliases`. When an old
plan and an item disagree, the item wins.

## Item fields

| field | meaning |
|---|---|
| `id` | `PAR-<AREA>-<nn>`, stable, never renumbered |
| `title` | one line |
| `area` | folder under `items/` |
| `status` | `open`, `done` or `blocked` (blocked items say why in the first `plan` line) |
| `wave` | the wave it belongs to (0 = already done before this folder existed) |
| `deps` | item ids that must be `done` first; all of them are in earlier waves |
| `size` | `S` (a day or less) or `M` (a focused PR); there are no `L` items, they were split |
| `aliases` | old ids from the phase plans and `plans/gaps.md` |
| `tfc_refs` | paths in the TFC checkout (`/opt/tfc`), all verified to exist at the pin |
| `files_owned` | globs the item may change; the proof that two items of one wave are independent |
| `ui` | `true` when the result is visible in game; then a screenshot check is part of done |
| `in_game_steps` | exact RCON/chat commands to show the result (player name `Bot` in the headless harness) |
| `plan` | concrete steps, starting with what exists now; impossible TFC behaviour names the closest equivalent |
| `tests` | tests to write |
| `done_criteria` | when the item may be set to `done` |

## Status legend

Item status: `open` (can be taken when its deps are done), `done`, `blocked` (needs an owner decision or an external fix; the
first plan line says which).

Feature status in `INVENTORY.md`: **done** (rules, glue and visuals in game), **partial** (some layers exist, usually the core
rules with golden tests; glue or visuals missing), **placeholder** (generated or placed with stand-in blocks/visuals), **not
started**, **impossible-on-vanilla** (needs a client mod; the closest planned equivalent is named).

## Totals

227 items: open 216, done 8, blocked 3. Sizes: S 84, M 143. Visible in game (`ui: true`): 179. Waves: 56 (plus wave 0 for items already done). Blocked on owner decisions: `PAR-CLIM-05`, `PAR-DATA-04`, `PAR-REL-09`.

## Coverage matrix (TFC area -> items -> status)

| area | scope | items (status) | open / done / blocked | TFC features covered, status now |
|---|---|---|---|---|
| INFRA | Shared frameworks (modules, devices, GUI, item data, tooltips, recipes, tags, loot, neighbour updates, catch-up, hitbox carriers) | INFRA-01 (open), INFRA-02 (open), INFRA-03 (open), INFRA-04 (open), INFRA-05 (open), INFRA-06 (open), INFRA-07 (open), INFRA-08 (open), INFRA-09 (open), INFRA-10 (open), INFRA-11 (open), INFRA-13 (open), INFRA-15 (open), INFRA-16 (open), INFRA-17 (open) | 15 / 0 / 0 | not started 4, partial 8; ore overlay (not started 2), renderer-cube feel (not started 1) |
| CLIMATE | Calendar, climate, weather, seasons, instruments | CLIM-01 (open), CLIM-02 (open), CLIM-03 (open), CLIM-04 (open), CLIM-05 (blocked), CLIM-06 (open), CLIM-07 (open), CLIM-08 (open), CLIM-09 (open) | 8 / 0 / 1 | not started 2, partial 6, placeholder 1 |
| WORLD | World generation | WORLD-01 (open), WORLD-02 (open), WORLD-03 (open), WORLD-04 (open), WORLD-05 (open), WORLD-06 (open), WORLD-07 (open), WORLD-08 (open), WORLD-09 (open), WORLD-10 (open), WORLD-11 (open), WORLD-12 (open), WORLD-13 (open), WORLD-14 (open), WORLD-15 (open), WORLD-16 (open), WORLD-17 (open) | 17 / 0 / 0 | not started 1, partial 11, placeholder 4; ore exposure census (not started 1) |
| SURV | Player survival: food, nutrition, thirst, health, size/weight, player tabs | SURV-01 (open), SURV-02 (open), SURV-03 (open), SURV-04 (open), SURV-05 (open), SURV-06 (open), SURV-07 (open), SURV-08 (open), SURV-09 (open) | 9 / 0 / 0 | not started 3, partial 6 |
| HUD | HUD (BetterHud) | HUD-01 (done), HUD-02 (done), HUD-03 (done), HUD-04 (open), HUD-05 (open), HUD-06 (open), HUD-07 (open), HUD-08 (open) | 5 / 3 / 0 | done 3, not started 1, partial 4 |
| HEAT | Heat, fire, firepit/grill/pot, torches, candles, firebox | HEAT-01 (open), HEAT-02 (open), HEAT-03 (open), HEAT-04 (open), HEAT-05 (open), HEAT-06 (open), HEAT-07 (open), HEAT-08 (open), HEAT-09 (open), HEAT-10 (open), HEAT-11 (open), HEAT-12 (open), HEAT-13 (open) | 13 / 0 / 0 | not started 5, partial 8 |
| GATHER | Gathering, harvesting, tree felling, soil and rock interactions | GATHER-01 (open), GATHER-02 (open), GATHER-03 (open), GATHER-04 (open), GATHER-05 (open), GATHER-06 (open) | 6 / 0 / 0 | not started 3, partial 3 |
| TOOL | Tools, weapons, armour, javelins, propick, chisel | TOOL-01 (open), TOOL-02 (open), TOOL-03 (open), TOOL-04 (open), TOOL-05 (open), TOOL-06 (open), TOOL-07 (open), TOOL-08 (open), TOOL-09 (open), TOOL-10 (open) | 10 / 0 / 0 | not started 2, partial 7, placeholder 1 |
| POTTERY | Pottery, placed items, pit kiln, vessels, molds, jugs, drying | POT-01 (open), POT-02 (open), POT-03 (open), POT-04 (open), POT-05 (open), POT-06 (open), POT-07 (open), POT-08 (open), POT-09 (open) | 9 / 0 / 0 | not started 3, partial 6 |
| PROC | Processing devices and crafting: knapping, workbench, quern, scraping, loom, sewing, barrel, charcoal, compost, panning, sluice, glass, powderkeg, materials | PROC-01 (open), PROC-02 (open), PROC-03 (open), PROC-04 (open), PROC-05 (open), PROC-06 (open), PROC-07 (open), PROC-08 (open), PROC-09 (open), PROC-10 (open), PROC-11 (open), PROC-12 (open), PROC-13 (open), PROC-14 (open), PROC-15 (open), PROC-16 (open), PROC-17 (open), PROC-18 (open), PROC-19 (open) | 19 / 0 / 0 | not started 5, partial 14 |
| METAL | Metallurgy: alloys, casting, anvil, forge, bellows, crucible, channels, bloomery, blast furnace, piles, weathering, lamps, bells | METAL-01 (open), METAL-02 (open), METAL-03 (open), METAL-04 (open), METAL-05 (open), METAL-06 (open), METAL-07 (done), METAL-08 (open), METAL-09 (open), METAL-10 (open), METAL-11 (open), METAL-12 (open), METAL-13 (open), METAL-14 (open), METAL-15 (open), METAL-16 (open), METAL-17 (open), METAL-18 (open), METAL-19 (open) | 18 / 1 / 0 | done 1, not started 3, partial 14, placeholder 1 |
| PHYS | Rock and soil physics: falling blocks, collapse, landslide, support beams | PHYS-01 (open), PHYS-02 (open), PHYS-03 (open), PHYS-04 (open), PHYS-05 (open) | 5 / 0 / 0 | not started 2, partial 3 |
| FAUNA | Animals: models, spawning, livestock, products, wildlife, aquatic, pests, pets, mounts, hunting, fishing | FAUNA-01 (open), FAUNA-02 (open), FAUNA-03 (open), FAUNA-04 (open), FAUNA-05 (open), FAUNA-06 (open), FAUNA-07 (open), FAUNA-08 (open), FAUNA-09 (open), FAUNA-10 (open), FAUNA-11 (open), FAUNA-12 (open), FAUNA-13 (open), FAUNA-14 (open), FAUNA-15 (open), FAUNA-16 (open), FAUNA-17 (open), FAUNA-18 (open), FAUNA-19 (open), FAUNA-20 (open), FAUNA-21 (open) | 21 / 0 / 0 | not started 4, partial 17 |
| DECO | Building and decoration families, vehicles, ropes, misc blocks | DECO-01 (open), DECO-02 (open), DECO-03 (open), DECO-04 (open), DECO-05 (open), DECO-06 (open), DECO-07 (open), DECO-08 (open), DECO-09 (open), DECO-10 (open), DECO-11 (open), DECO-12 (open), DECO-13 (open), DECO-14 (open), DECO-15 (open) | 15 / 0 / 0 | not started 9, partial 2, placeholder 4 |
| FLUID | Fluids, buckets, world water | FLUID-01 (open), FLUID-02 (open), FLUID-03 (open), FLUID-04 (open) | 4 / 0 / 0 | impossible-on-vanilla 1, not started 2, partial 1 |
| MECH | Mechanical power and machines | MECH-01 (open), MECH-02 (open), MECH-03 (open), MECH-04 (open), MECH-05 (open), MECH-06 (open), MECH-07 (open), MECH-08 (open), MECH-09 (open), MECH-10 (open) | 10 / 0 / 0 | not started 2, partial 8 |
| FARM | Farming, crops, bushes, fruit trees, saplings, plant regrowth | FARM-01 (open), FARM-02 (open), FARM-03 (open), FARM-04 (open), FARM-05 (open), FARM-06 (open), FARM-07 (open), FARM-08 (open), FARM-09 (open), FARM-10 (open), FARM-11 (open) | 11 / 0 / 0 | not started 4, partial 7 |
| DATA | Commands, advancements, field guide, i18n, config, damage types, sounds, particles, vanilla overrides | DATA-01 (open), DATA-02 (open), DATA-03 (open), DATA-04 (blocked), DATA-05 (open), DATA-06 (open), DATA-07 (open), DATA-08 (open), DATA-09 (open), DATA-10 (open), DATA-11 (open), DATA-12 (open), DATA-13 (open) | 12 / 0 / 1 | impossible-on-vanilla 1, not started 5, partial 7 |
| REL | Test infrastructure, performance, QA, release, art, docs | REL-01 (open), REL-02 (open), REL-03 (open), REL-04 (open), REL-05 (open), REL-06 (open), REL-07 (open), REL-08 (open), REL-09 (blocked), REL-10 (open), REL-11 (open), REL-12 (open) | 11 / 0 / 1 | not started 5, partial 6; budget guards (not started 1) |

Feature-level status per TFC feature: `INVENTORY.md`.

## Waves

Rules the generator enforced: every item's deps are in earlier waves; within a wave no two items have overlapping
`files_owned` globs (prefix test on the literal part of each glob); at most 4 items per wave; items are placed by priority
(shared frameworks first, then the stone-age path, then the metal age, fauna, decoration, machines, release). A worker may
start an item of a later wave early when its deps are done and no running item owns an overlapping glob (PROTOCOL.md step 2).

Blocked items keep their wave so that the order stays stable; their dependents wait.

- **Wave 0 (already done)**: `PAR-HUD-01` Two-layer HUD emitted by datagen (HudArt, offset-left layouts, trim compensation); `PAR-HUD-02` Two-layer HUD art: one master per bar, bg and fill derived by hud_layers.py; `PAR-HUD-03` `/tfp debug hunger <name> <0..20>` for exact low-food HUD states; `PAR-METAL-07` Charcoal forge visuals: TFC heat-level models on a snow-layer carrier
- **Wave 1**: `PAR-INFRA-01` Feature module SPI: register listeners, services and debug subcommands without editing TfpPlugin; `PAR-INFRA-04` Item data components on PDC: fluid contents and item lists (vessels, jugs, buckets, molds); `PAR-INFRA-07` Tag runtime: TFC item/block/fluid/entity tags resolved for CE and vanilla ids; `PAR-REL-01` Test infrastructure fixes: headless client pack URL, snow-free test site, CE shutdown warning
- **Wave 2**: `PAR-INFRA-02` Device block framework: CraftEngine block behaviour routing, per-block state and lazy catch-up hooks; `PAR-INFRA-03` GUI framework: textured chest GUIs, title glyphs, buttons, progress bars, alignment; `PAR-INFRA-05` Item tooltip pipeline: several decorations per item, TFC styles and colours; `PAR-INFRA-08` Device datagen: shared CE templates for device blocks (states, loot self, hitbox carriers, entity_renderer)
- **Wave 3**: `PAR-HUD-04` Two-layer HUD in-game verification at GUI scale 2 and 3 (full, low, submerged); `PAR-INFRA-06` Recipe runtime on Paper: ItemStack ingredient matching, indexed lookups, output modifiers; `PAR-INFRA-09` Block and entity loot runtime: TFC loot tables evaluated on Paper; `PAR-METAL-10` Charcoal forge visual verification: night, neighbouring full blocks, animation frames, lit-display flicker; `PAR-INFRA-13` Hitbox carrier selector: every CE block gets the vanilla state whose collision and selection shape best match TFC's (added later as a fifth wave-3 item so it precedes every block family generator; files disjoint)
- **Wave 4**: `PAR-HUD-05` HUD vs vanilla armour row: no overlap with armour worn, health text inside its bar; `PAR-HUD-08` Held item temperature popup with real values; `PAR-INFRA-10` Neighbour-update runner: block shape updates decided async, applied sync; `PAR-INFRA-11` Catch-up trigger service: chunk load, interaction and visible-area sweep for lazy blocks; `PAR-WORLD-17` Ore overlay spike S1: measure exposed ore faces per chunk and the overlay entity budget (added 2026-10-22 as a fifth wave-4 item; files disjoint)
- **Wave 5**: `PAR-HUD-06` HUD polish: health frame asymmetry, ambient temperature bar and info plate, pulse animation; `PAR-PROC-01` Knapping GUI: 5x5 grid for stone, clay, fire clay, leather and goat horn; `PAR-PROC-19` Misc materials as CE items: ores, gems, powders, fibres, cloth, paper, glue, straw, mortar, spawn eggs; `PAR-SURV-01` All TFC food items generated as CE items with food_stamp and food data; `PAR-INFRA-17` Renderer-cube feel (spike S2): destroy_stages, particles, sounds and mining for display-drawn full blocks (added 2026-10-22 as a fifth wave-5 item; files disjoint)
- **Wave 6**: `PAR-HUD-07` HUD per MC version and TFC display styles: 26.x sprite list, XP bar style, health display style; `PAR-PROC-02` Workbench crafting: the plain 2,255 shaped/shapeless recipes as CE recipe YAML; `PAR-SURV-02` Food tooltip: expiry/rotten, traits, nutrition lines; `PAR-SURV-03` Eating completion: undefined foods, peaceful, rotten advancement hook, in-game check; `PAR-INFRA-15` Ore overlay datagen: ores take the host rock visual, overlay item models, always-on allocator rule, profiles level-0..2 only (added 2026-10-22 as a fifth wave-6 item; files disjoint)
- **Wave 7**: `PAR-PROC-03` Advanced crafting: TFC advanced recipes, food combining, casting crafting, vanilla recipe leakage; `PAR-PROC-04` Quern: block, handstone, grinding, GUI-less interaction; `PAR-PROC-05` Scraping hides on a log; `PAR-SURV-04` Thirst completion: thirst effects, rain drinking, salt water, open-water drinking decision; `PAR-INFRA-16` Ore overlay runtime: packet-only face overlays, neighbour-driven refresh, Folia-safe, feel checklist (added 2026-10-22 as a fifth wave-7 item; files disjoint)
- **Wave 8**: `PAR-FLUID-01` Fluids as container contents: registry of TFC fluids, colours, debug fill; `PAR-SURV-05` Health and damage: TFC health scale, physical damage types, entity and armour resistances; `PAR-SURV-06` Item size and weight on Paper: container limits, overburdened and exhausted, tooltip; `PAR-SURV-07` Player tab screens: calendar, nutrition and climate pages opened from outside the inventory; `PAR-REL-12` Visual and overlay budget guards: profile-vs-pool unit test, overlay entities-per-chunk itest budget (added 2026-10-22 as a fifth wave-8 item; files disjoint)
- **Wave 9**: `PAR-FLUID-02` Buckets: wooden bucket, red and blue steel buckets, fluid placement policy; `PAR-FLUID-03` Salt water, spring water and river water in the world; `PAR-PROC-08` Barrel block: fluids, sealing, sealed and instant recipes, preservation; `PAR-SURV-08` Alcohol and intoxication
- **Wave 10**: `PAR-FLUID-04` Hot spring healing and bubble columns; `PAR-HEAT-01` Item heat glow: hot items glow by heat level on vanilla clients; `PAR-HEAT-02` Heat tooltip in TFC colours and metal info lines (melting point, workable, weldable); `PAR-SURV-09` Death, respawn and regeneration rules
- **Wave 11**: `PAR-CLIM-01` Calendar config, sleep skipping and doDaylightCycle sync; `PAR-CLIM-02` Precipitation per position on Paper and the in-game snow-vs-rain check; `PAR-HEAT-03` Hot items: burn damage while held, item entities cool in water and snow; `PAR-PROC-11` Composter
- **Wave 12**: `PAR-CLIM-03` Snow and ice accumulation: lazy-vs-stepped equivalence and server check; `PAR-HEAT-04` Heating recipes on devices: the 884 heating recipes as a shared service; `PAR-HEAT-05` Fire starting: firestarter use-duration, flint and pyrite, StartFireEvent world rules; `PAR-PROC-09` Barrel GUI, fluid level visual, rain fill, barrel rack
- **Wave 13**: `PAR-CLIM-04` Snow piles, ice piles, icicles and sea ice as CraftEngine blocks; `PAR-HEAT-06` Firepit block: placement, fuel, lazy temperature, ash, extinguish by rain and water; `PAR-PROC-10` Log pile and charcoal pit; `PAR-PROC-14` Salads
- **Wave 14**: `PAR-CLIM-05` Seasonal grass tint: decide band resolution and make the colour change visible *(blocked)*; `PAR-HEAT-07` Firepit GUI: fuel slots, input, outputs, temperature bar; `PAR-HEAT-10` Torches: burnout to dead torch, relight, wall torches; `PAR-PROC-18` Powderkeg
- **Wave 15**: `PAR-CLIM-06` Seasonal packet rewrite JMH budget and visual checklist; `PAR-HEAT-08` Grill on firepit: placement, five slots, cooking, GUI; `PAR-HEAT-09` Cooking pot on firepit: boiling, soups and jams, pot fluid, GUI; `PAR-HEAT-11` Candles, candle cakes and jack-o'-lanterns: burnout, lighting, carving
- **Wave 16**: `PAR-HEAT-12` Firebox, stove and stove pot; `PAR-PHYS-01` TFC falling blocks: canFallThrough, landing, damage; `PAR-WORLD-01` Rendered region and river maps compared with TFC; `PAR-WORLD-02` Bukkit biome binding and climate call sites on the real chunk data
- **Wave 17**: `PAR-PHYS-02` Collapse runner: mining triggers collapses decided async, applied as falling blocks; `PAR-PHYS-03` Landslides and gravity soils; `PAR-WORLD-03` Worldgen performance: JMH chunk budget, vanilla baseline and exposed-gem decision; `PAR-WORLD-04` TFC soil and gravel block set in generation: grass, dirt, coarse dirt, clay, peat, kaolin, mud, rock gravel
- **Wave 18**: `PAR-GATHER-05` Soil interactions: hoeing, paths, mud, rooted dirt, grass spread and rock spawning; `PAR-GATHER-06` Raw rock drops and mossy rock spreading; `PAR-PHYS-05` Explosion collapses; `PAR-WORLD-05` Ice sheets, sea ice and icebergs at generation; lava flows; remaining biome surface builders
- **Wave 19**: `PAR-TOOL-01` Stone tools: rock tool heads and tools as CE items, crafting from knapped heads; `PAR-TOOL-03` In-game verification of metal tools and armour; `PAR-WORLD-06` Cave tunnels and canyons (vanilla carvers with TFC carveBlock); `PAR-WORLD-08` Disc, pipe and kaolin veins; montane second pass; ore distribution statistics
- **Wave 20**: `PAR-TOOL-02` Tool mechanics on Paper: TFC mining speed, durability rules, forging efficiency; `PAR-TOOL-04` Swords, maces, macuahuitl: attack damage per TFC tier; `PAR-WORLD-07` Aquifer barrier noise and the grass rule under carved surfaces; `PAR-WORLD-09` Groundcover, loose rocks, deposits and boulders as CE blocks in generation
- **Wave 21**: `PAR-GATHER-01` Pick up loose rocks and groundcover with a right-click; `PAR-POT-01` Placed items: put up to four items on the ground (and the straw/log stages of a pit kiln); `PAR-PROC-12` Panning; `PAR-WORLD-10` Wood blocks: logs, wood, stripped, leaves, saplings as CE blocks
- **Wave 22**: `PAR-GATHER-03` Tree felling: the whole tree falls when the trunk is cut with an axe; `PAR-GATHER-04` Leaves: slow decay, movement slowdown, fallen leaves; plant movement slowdown; `PAR-PHYS-04` Support beams: blocks, connections, collapse prevention; `PAR-PROC-13` Sluice
- **Wave 23**: `PAR-POT-02` Unfired and fired pottery items, clay knapping outputs; `PAR-TOOL-05` Javelins: throwing, damage, pickup, javelin AI for skeletons; `PAR-TOOL-06` Knife, scythe and hoe area behaviours; remaining tool items (saw, chisel, propick, tuyere, shears heads); `PAR-WORLD-11` Tree generation: forest placement on every chunk, dead forests, mangroves
- **Wave 24**: `PAR-POT-03` Pit kiln: straw and log stages, lighting, lazy firing; `PAR-POT-04` Small vessel: inventory, melting contents into metal, pouring; `PAR-TOOL-07` Shields per metal; `PAR-WORLD-12` Plant blocks: all TFC plant shapes as CE blocks in generation
- **Wave 25**: `PAR-GATHER-02` Harvest plants, leaves, twigs and fallen leaves by TFC loot tables; `PAR-POT-05` Molds: filling from a vessel/crucible, solidifying, breaking out the casting; `PAR-WORLD-13` Remaining plant features: vines, kelp, epiphytes, creeping plants, cacti, algae, atoll corals; `PAR-WORLD-14` Volcanic features: springs, rivulets, flood-fill lakes, calderas of other cones
- **Wave 26**: `PAR-POT-06` Large vessel (and glazed): nine slots, sealing, preservation, automation; `PAR-POT-07` Jugs and glass bottles: fill, drink, pour, break; `PAR-WORLD-15` Volcanic and spring CE blocks: hardened rock, magma blocks, spring water look; `PAR-WORLD-16` Spawn and world settings: spawn offset, forced TFC gamerules, structures off, dimension policy
- **Wave 27**: `PAR-FARM-01` Farmland block: hydration from water (async), nutrients, hoe overlay readout; `PAR-METAL-01` Alloy display and fluid alloy glue for vessels and crucibles; `PAR-POT-08` Drying bricks, mud bricks and thatch blocks; `PAR-POT-09` Bowls and powder bowls (wooden and ceramic bowl blocks)
- **Wave 28**: `PAR-FARM-02` Fertilizers; `PAR-FARM-03` Crop blocks: all 29 crops in every shape, seeds, dead and wild crops; `PAR-METAL-02` Fire ingot mold and bell casting; casting crafting; `PAR-METAL-03` Anvil blocks: rock anvils and metal anvils with items shown on top
- **Wave 29**: `PAR-FARM-04` Crop growth glue: lazy calendar growth, climate checks, death, harvest yields; `PAR-FARM-06` Stationary berry bushes: lifecycle, picking, propagation; `PAR-METAL-04` Anvil plan selection GUI; `PAR-METAL-08` Charcoal forge block entity: creation from a charcoal pile, lighting, continuous heating of held items
- **Wave 30**: `PAR-FARM-05` Wild crops and spreading crops (pumpkin, melon), rotten pumpkins; `PAR-FARM-07` Spreading bushes, canes and cranberries; `PAR-METAL-05` Anvil forging GUI: step buttons, target and progress markers, hammering, forging bonus; `PAR-PROC-16` Glassworking: blowpipe operations (blow, roll, stretch, pinch, flatten, saw)
- **Wave 31**: `PAR-FARM-08` Fruit trees: saplings, growing branches, seasonal leaves, fruit; `PAR-METAL-06` Welding on the anvil (flux); `PAR-METAL-09` Charcoal forge GUI and insulation/chimney multiblock scan; `PAR-PROC-17` Glass basin and hot poured glass; poured glass blocks and glass items
- **Wave 32**: `PAR-DATA-01` Commands under /tfp: player, count block, clear world, add trim, weather hooks; `PAR-FARM-09` Bananas; `PAR-FARM-10` Sapling growth into trees; `PAR-METAL-11` Bellows: push, air boost, crank, animation
- **Wave 33**: `PAR-CLIM-07` Weather command and TFC weather state (`/tfp weather`); `PAR-FARM-11` Plant regrowth, plant growth/spread, flower cuttings; `PAR-METAL-12` Crucible block and GUI; `PAR-TOOL-08` Prospector's pick: scan, result message, `/tfp propick`
- **Wave 34**: `PAR-CLIM-08` Lightning rules: enableLightning, log stripping, fire start; `PAR-CLIM-09` Climate instruments: thermometer, anemometer, vane, calendar clock; `PAR-DATA-02` Advancements: triggers, awards and presentation; `PAR-METAL-13` Channels and mold tables: pouring flow
- **Wave 35**: `PAR-DATA-03` Advancement criteria wiring across features; `PAR-DATA-04` Field guide: decide the form and convert the book *(blocked)*; `PAR-HEAT-13` Fire behaviour: fire arrows, dousing, fire spread limits; `PAR-METAL-14` Bloomery multiblock and bloom
- **Wave 36**: `PAR-DATA-05` Localisation: Paper lang loading, all TFC locales, plugin strings; `PAR-METAL-15` Blast furnace, tuyere and steel path; `PAR-METAL-16` Ingot and double-ingot piles; `PAR-TOOL-10` Bows and glow arrows
- **Wave 37**: `PAR-DATA-06` Configuration parity: every TFC ServerConfig/CommonConfig key in config.yml; `PAR-DATA-07` Damage types and mob effects; `PAR-DATA-08` Sounds: TFC sound events into the pack and played by features; `PAR-METAL-18` Bells (bronze, brass)
- **Wave 38**: `PAR-DATA-09` Particles: action-driven TFC particles with vanilla equivalents; `PAR-DATA-10` Dispenser behaviours, bonemeal, vanilla rule overrides; `PAR-DATA-11` Paintings, armour trims, misc data registries; `PAR-FAUNA-01` Entity model import: TFC bbmodels and Java animations converted to BetterModel
- **Wave 39**: `PAR-DATA-12` Block info readout (Jade-like tooltips) on the actionbar; `PAR-DATA-13` TFC pin drift check in CI; `PAR-FAUNA-02` Entity models for Java-only TFC models via the rig generator; `PAR-FAUNA-21` Animal framework completion: species behaviour SPI, goal replacement, model re-apply checks
- **Wave 40**: `PAR-DECO-01` Wood families loaded in CraftEngine: planks, stairs, slabs, fences, walls with our block_state templates; `PAR-DECO-10` Ropes, rope anchors and rope knots; `PAR-FAUNA-03` Entity texture variants, gendered and aged textures, render review; `PAR-FAUNA-04` Spawning by climate on Paper
- **Wave 41**: `PAR-FAUNA-05` Livestock: feeding, familiarity and breeding interactions; `PAR-PROC-06` Loom: weaving cloth, burlap, wool, silk; `PAR-PROC-07` Sewing table GUI; `PAR-PROC-15` Scribing table
- **Wave 42**: `PAR-FAUNA-06` Animal products: milk, wool, eggs; sheared look; `PAR-FAUNA-08` Livestock AI and roster: graze, follow food, herd; all 18 domestic species; `PAR-FAUNA-09` Prey AI and wild animals; `PAR-FAUNA-11` Aquatic animals: fish, squid, jellyfish, dolphins, orcas, manatees; mob buckets
- **Wave 43**: `PAR-FAUNA-07` Nest box: laying, hatching, GUI; `PAR-FAUNA-10` Predator AI: hunting, home range, sleep schedule, packs; `PAR-FAUNA-12` Amphibians: turtles, penguins, seals, crocodiles, frogs (play dead, land/water); `PAR-FAUNA-13` Pests and critters: rats raiding containers, lobsters, crayfish, isopods
- **Wave 44**: `PAR-FAUNA-14` Pets: dogs and cats with commands; `PAR-FAUNA-15` Horses, donkeys and mules: riding, breeding, chests; `PAR-FAUNA-17` Hunting drops, butchering, hides; `PAR-FAUNA-18` Fishing: rod, hook, bait
- **Wave 45**: `PAR-DECO-02` Wood logic: doors, trapdoors, fence gates, buttons, pressure plates; `PAR-FAUNA-16` Camels (dromedary, bactrian); `PAR-FAUNA-19` Lobster traps; `PAR-FAUNA-20` Pluckable feathers, animal sounds
- **Wave 46**: `PAR-DECO-03` Signs and hanging signs (per wood, per metal hanger); `PAR-DECO-04` Chests, trapped chests and barrel racks per wood; `PAR-DECO-05` Tool racks, shelves, bookshelves, lecterns; `PAR-DECO-06` Rock families: bricks, cobble, smooth, stairs, slabs, walls, aqueducts, spikes, pressure plates, buttons
- **Wave 47**: `PAR-DECO-07` Mud bricks, alabaster, aggregate, fire bricks, wattle and daub, thatch; `PAR-DECO-08` Sand, sandstone families, hardened clay, halite, coal, calcite, misc natural blocks; `PAR-DECO-09` Metal decoration blocks: metal blocks (and weathered), bars, chains, grates, trapdoors; `PAR-TOOL-09` Chisel: smoothing, slabs and stairs with mode cycling
- **Wave 48**: `PAR-DECO-11` Boats, chest boats and minecarts; `PAR-DECO-12` Misc blocks: crates, buoys, cakes, golden bamboo, palm mosaic, tree roots, light block; `PAR-METAL-17` Metal weathering of copper-family blocks; `PAR-METAL-19` Metal lamps: fuel, light, lit state
- **Wave 49**: `PAR-DECO-13` Potted plants and krummholz; `PAR-DECO-14` Thatch bed: sleeping and spawn setting; `PAR-DECO-15` Visual budget re-check after all block families; `PAR-MECH-01` Rotation network blocks: axle, encased axle, gearbox, clutch, creative motor
- **Wave 50**: `PAR-MECH-02` Rotation visuals: spinning axles and gears; `PAR-MECH-05` Crankshaft and powered bellows; `PAR-MECH-06` Quern automation; `PAR-MECH-07` Trip hammer
- **Wave 51**: `PAR-MECH-03` Water wheel; `PAR-MECH-04` Windmill and blades; `PAR-MECH-08` Power loom; `PAR-MECH-09` Pump and pipes
- **Wave 52**: `PAR-MECH-10` Bladed axle; `PAR-REL-02` Performance budgets in CI (JMH) and threading gate; `PAR-REL-03` Dependency and version updates (MC, Paper, Folia, CE, BetterModel, BetterHud); `PAR-REL-04` Stone-age run self-test
- **Wave 53**: `PAR-REL-05` Metal run self-test; `PAR-REL-06` Husbandry run self-test and 200-animal load test; `PAR-REL-10` Art backlog: replace every placeholder texture; `PAR-REL-11` Design docs and skills kept current
- **Wave 54**: `PAR-REL-07` Performance audit with 50 bots
- **Wave 55**: `PAR-REL-08` Full playthrough and visual QA
- **Wave 56**: `PAR-REL-09` Release packaging *(blocked)*

## Regenerating

The folder was produced by a generator kept outside the repo (item data in Python); after that, the item files are the source
of truth and are edited by hand by workers (status, plan notes). New items: copy an item file, take the next free number in
the area, pick a wave after all deps whose items own no overlapping glob (or append a new wave at the end), and add the row to
`STATUS.md` and the coverage matrix above.
