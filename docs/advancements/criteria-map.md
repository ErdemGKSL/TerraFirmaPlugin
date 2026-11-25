# TFC advancement criteria map (PAR-DATA-03)

Every criterion of TFC's 115 advancements (`data/tfc/advancement/**`, 438 criteria, pinned commit `c3b4d9536`) and the route that earns it in this plugin. The table is `paper/.../advancement/criteria/CriteriaTable.java`, read by `CriteriaMap`; `CriteriaMapTest` checks every row against the TFC checkout. Ids are ours (`tfc:` -> `tfp:`); a criterion is `<advancement>#<name>`. Awards go through `Advancements.fire`.

| Route | Criteria | Earned when | Event source |
|---|---|---|---|
| inventory | 248 (incl. `story/find_rock`, also wired by PAR-DATA-02) | an item (id) or tag enters the inventory | `PlayerInventorySlotChangeEvent`, item id through `Tags.itemId`, tags through `Tags` |
| consume | 20 (`world/fruit`) | the player ate the fruit | `NutritionFoodEatenEvent.itemId` |
| nutrition | 2 (`world/eat_rotten_food`, `world/full_nutrition`) | a rotten meal; a non-rotten meal reaching average nutrition 0.999 | `NutritionFoodEatenEvent` |
| biome | 140 (`world/adventuring_time`, `coral_reef`, `trench`, `volcano`) | the player enters a 4x4x4 cell whose biome is `tfp:<criterion name>` | `PlayerMoveEvent` (throttled by cell) |
| position | 2 (`world/globe_trotter#high/low`) | block z >= 9999 / z <= -9999 | `PlayerMoveEvent` |
| kill | 3 (`world/hunter`, `bear_hunter`, `glow_hunter`) | the player kills an animal (`#tfp:animals`), a bear (`#tfp:bears`) or `tfp:octopoteuthis` | `EntityDeathEvent` (species from the animal PDC) |
| tick | 2 (`story/root`, `world/root`) | vanilla `minecraft:tick` in the datapack | none needed |
| event | 4 (`world/fishing`, `world/advanced_fishing`, `world/greatest_hunter`, `story/the_future`) | TFC's own trigger earned from a Bukkit event (PAR-DATA-19): pulling in a hooked entity of `#tfp:small_fish` / `#tfp:needs_large_fishing_bait`, a javelin hit on a `tfp:rabbit` at >= 50 blocks horizontal, the calendar date after the real date | `PlayerFishEvent`, `EntityDamageByEntityEvent`, a once-a-minute global check of the clock |
| pending | 17 | TFC's own trigger; the feature publishes no event yet | follow-up PAR-DATA-23 |

## Pending criteria (follow-up PAR-DATA-23)

| Criterion | TFC trigger |
|---|---|
| `tfp:story/basin_pour#special_condition` | `tfc:basin_pour` |
| `tfp:story/firepit#special_condition` | `tfc:firepit_created` |
| `tfp:story/forge#special_condition` | `tfc:lit` |
| `tfp:story/grill#special_condition` | `tfc:firepit_created` |
| `tfp:story/perfectly_forged#special_condition` | `tfc:perfectly_forged` |
| `tfp:story/pit_kiln#special_condition` | `tfc:lit` |
| `tfp:story/pot#special_condition` | `tfc:firepit_created` |
| `tfp:story/smooth_stone#special_condition` | `tfc:chiseled` |
| `tfp:story/stone_anvil#special_condition` | `tfc:rock_anvil` |
| `tfp:story/table_pour#special_condition` | `tfc:table_pour` |
| `tfp:story/windmill#special_condition` | `tfc:max_windmill` |
| `tfp:world/familiarity#special_condition` | `tfc:fed_animal` |
| `tfp:world/full_fertilizer#special_condition` | `tfc:full_fertilizer` |
| `tfp:world/full_powderkeg#special_condition` | `tfc:full_powderkeg` |
| `tfp:world/lava_lamp#special_condition` | `tfc:lava_lamp` |
| `tfp:world/powderkeg#special_condition` | `tfc:lit` |
| `tfp:world/rotten_compost#special_condition` | `tfc:rotten_compost_kill` |

## Inventory rows

| Criterion | Item or tag |
|---|---|
| `tfp:story/barrel#barrels` | `#tfp:barrels` |
| `tfp:story/bellows#bellows` | `tfp:bellows` |
| `tfp:story/black_steel#tools/black_steel` | `#tfp:tools/black_steel` |
| `tfp:story/blast_furnace#blast_furnace` | `tfp:blast_furnace` |
| `tfp:story/bloomery#bloomery` | `tfp:bloomery` |
| `tfp:story/blowpipe#tools/blowpipe` | `#c:tools/blowpipe` |
| `tfp:story/blue_steel#tools/blue_steel` | `#tfp:tools/blue_steel` |
| `tfp:story/brass_mechanisms#brass_mechanisms` | `tfp:brass_mechanisms` |
| `tfp:story/brass_plated_block#metal/block/brass` | `tfp:metal/block/brass` |
| `tfp:story/bronze_age#tools/bronze` | `#tfp:tools/bronze` |
| `tfp:story/bronze_age#tools/bismuth_bronze` | `#tfp:tools/bismuth_bronze` |
| `tfp:story/bronze_age#tools/black_bronze` | `#tfp:tools/black_bronze` |
| `tfp:story/cast_iron#metal/ingot/cast_iron` | `tfp:metal/ingot/cast_iron` |
| `tfp:story/charcoal#charcoal` | `minecraft:charcoal` |
| `tfp:story/chisel#tools/chisel` | `#c:tools/chisel` |
| `tfp:story/copper_age#tools/copper` | `#tfp:tools/copper` |
| `tfp:story/crankshaft#crankshaft` | `tfp:crankshaft` |
| `tfp:story/crucible#crucible` | `tfp:crucible` |
| `tfp:story/find_clay#clay_ball` | `minecraft:clay_ball` |
| `tfp:story/find_rock#rock_knapping` | `#tfp:rock_knapping` |
| `tfp:story/fire_clay#fire_clay` | `tfp:fire_clay` |
| `tfp:story/firestarter#firestarter` | `tfp:firestarter` |
| `tfp:story/flint_and_steel#flint_and_steel` | `minecraft:flint_and_steel` |
| `tfp:story/flux#welding_flux` | `#tfp:welding_flux` |
| `tfp:story/gear_box#gear_boxes` | `#tfp:gear_boxes` |
| `tfp:story/get_straw#straw` | `tfp:straw` |
| `tfp:story/get_unknown#metal/ingot/unknown` | `tfp:metal/ingot/unknown` |
| `tfp:story/glass_bottle#glass_bottles` | `#tfp:glass_bottles` |
| `tfp:story/glass_colors#glass` | `minecraft:glass` |
| `tfp:story/glass_colors#tinted_glass` | `minecraft:tinted_glass` |
| `tfp:story/glass_colors#white` | `minecraft:white_stained_glass` |
| `tfp:story/glass_colors#orange` | `minecraft:orange_stained_glass` |
| `tfp:story/glass_colors#magenta` | `minecraft:magenta_stained_glass` |
| `tfp:story/glass_colors#light_blue` | `minecraft:light_blue_stained_glass` |
| `tfp:story/glass_colors#yellow` | `minecraft:yellow_stained_glass` |
| `tfp:story/glass_colors#lime` | `minecraft:lime_stained_glass` |
| `tfp:story/glass_colors#pink` | `minecraft:pink_stained_glass` |
| `tfp:story/glass_colors#gray` | `minecraft:gray_stained_glass` |
| `tfp:story/glass_colors#light_gray` | `minecraft:light_gray_stained_glass` |
| `tfp:story/glass_colors#cyan` | `minecraft:cyan_stained_glass` |
| `tfp:story/glass_colors#purple` | `minecraft:purple_stained_glass` |
| `tfp:story/glass_colors#blue` | `minecraft:blue_stained_glass` |
| `tfp:story/glass_colors#brown` | `minecraft:brown_stained_glass` |
| `tfp:story/glass_colors#green` | `minecraft:green_stained_glass` |
| `tfp:story/glass_colors#red` | `minecraft:red_stained_glass` |
| `tfp:story/glass_colors#black` | `minecraft:black_stained_glass` |
| `tfp:story/glassworking_tools#paddle` | `tfp:paddle` |
| `tfp:story/glassworking_tools#jacks` | `tfp:jacks` |
| `tfp:story/glassworking_tools#wool_cloth` | `tfp:wool_cloth` |
| `tfp:story/glassworking_tools#gem_saw` | `tfp:gem_saw` |
| `tfp:story/iron_age#metal/ingot/wrought_iron` | `tfp:metal/ingot/wrought_iron` |
| `tfp:story/iron_armor#metal/sword/wrought_iron` | `tfp:metal/sword/wrought_iron` |
| `tfp:story/iron_armor#metal/shield/wrought_iron` | `tfp:metal/shield/wrought_iron` |
| `tfp:story/iron_armor#metal/helmet/wrought_iron` | `tfp:metal/helmet/wrought_iron` |
| `tfp:story/iron_armor#metal/chestplate/wrought_iron` | `tfp:metal/chestplate/wrought_iron` |
| `tfp:story/iron_armor#metal/greaves/wrought_iron` | `tfp:metal/greaves/wrought_iron` |
| `tfp:story/iron_armor#metal/boots/wrought_iron` | `tfp:metal/boots/wrought_iron` |
| `tfp:story/iron_bloom#raw_iron_bloom` | `tfp:raw_iron_bloom` |
| `tfp:story/knap_clay#unfired_pottery` | `#tfp:unfired_pottery` |
| `tfp:story/leather#leather` | `minecraft:leather` |
| `tfp:story/lens#compass` | `minecraft:compass` |
| `tfp:story/lens#daylight_detector` | `minecraft:daylight_detector` |
| `tfp:story/lens#spyglass` | `minecraft:spyglass` |
| `tfp:story/logging#logs` | `#minecraft:logs` |
| `tfp:story/metal_anvil#metal/anvil/copper` | `tfp:metal/anvil/copper` |
| `tfp:story/mold#fired_molds` | `#tfp:fired_molds` |
| `tfp:story/papyrus#papyrus` | `tfp:papyrus` |
| `tfp:story/pickaxe#pickaxes` | `#minecraft:pickaxes` |
| `tfp:story/plated_block#metal_plated_blocks` | `#tfp:metal_plated_blocks` |
| `tfp:story/preserves#foods/preserves` | `#tfp:foods/preserves` |
| `tfp:story/propick#tools/propick` | `#c:tools/propick` |
| `tfp:story/pump#steel_pump` | `tfp:steel_pump` |
| `tfp:story/quern#handstone` | `tfp:handstone` |
| `tfp:story/quern#quern` | `tfp:quern` |
| `tfp:story/raw_stone#stones` | `#c:stones` |
| `tfp:story/red_steel#tools/red_steel` | `#tfp:tools/red_steel` |
| `tfp:story/saw#tools/saw` | `#c:tools/saw` |
| `tfp:story/scraped_hide#scraped_hide` | `#tfp:scraped_hide` |
| `tfp:story/sheet#sheets` | `#c:sheets` |
| `tfp:story/shield#tools/shield` | `#c:tools/shield` |
| `tfp:story/steel_age#tools/steel` | `#tfp:tools/steel` |
| `tfp:story/stone_age#tools/stone` | `#tfp:tools/stone` |
| `tfp:story/trip_hammer#trip_hammer` | `tfp:trip_hammer` |
| `tfp:story/water_wheel#water_wheels` | `#tfp:water_wheels` |
| `tfp:story/welding#double_ingots` | `#c:double_ingots` |
| `tfp:world/all_crops#cassava` | `tfp:seeds/cassava` |
| `tfp:world/all_crops#green_bean` | `tfp:seeds/green_bean` |
| `tfp:world/all_crops#lentil` | `tfp:seeds/lentil` |
| `tfp:world/all_crops#peanut` | `tfp:seeds/peanut` |
| `tfp:world/all_crops#soybean` | `tfp:seeds/soybean` |
| `tfp:world/all_crops#barley` | `tfp:seeds/barley` |
| `tfp:world/all_crops#oat` | `tfp:seeds/oat` |
| `tfp:world/all_crops#rye` | `tfp:seeds/rye` |
| `tfp:world/all_crops#maize` | `tfp:seeds/maize` |
| `tfp:world/all_crops#wheat` | `tfp:seeds/wheat` |
| `tfp:world/all_crops#rice` | `tfp:seeds/rice` |
| `tfp:world/all_crops#beet` | `tfp:seeds/beet` |
| `tfp:world/all_crops#cabbage` | `tfp:seeds/cabbage` |
| `tfp:world/all_crops#carrot` | `tfp:seeds/carrot` |
| `tfp:world/all_crops#garlic` | `tfp:seeds/garlic` |
| `tfp:world/all_crops#onion` | `tfp:seeds/onion` |
| `tfp:world/all_crops#potato` | `tfp:seeds/potato` |
| `tfp:world/all_crops#squash` | `tfp:seeds/squash` |
| `tfp:world/all_crops#tomato` | `tfp:seeds/tomato` |
| `tfp:world/all_crops#red_bell_pepper` | `tfp:seeds/red_bell_pepper` |
| `tfp:world/all_crops#yellow_bell_pepper` | `tfp:seeds/yellow_bell_pepper` |
| `tfp:world/all_crops#pumpkin` | `tfp:seeds/pumpkin` |
| `tfp:world/all_crops#melon` | `tfp:seeds/melon` |
| `tfp:world/all_crops#canola` | `tfp:seeds/canola` |
| `tfp:world/all_crops#radish` | `tfp:seeds/radish` |
| `tfp:world/all_crops#alfalfa` | `tfp:seeds/alfalfa` |
| `tfp:world/all_crops#jute` | `tfp:seeds/jute` |
| `tfp:world/all_crops#papyrus` | `tfp:seeds/papyrus` |
| `tfp:world/all_crops#sugarcane` | `tfp:seeds/sugarcane` |
| `tfp:world/all_fish#cod` | `tfp:food/cod` |
| `tfp:world/all_fish#calamari` | `tfp:food/calamari` |
| `tfp:world/all_fish#shellfish` | `tfp:food/shellfish` |
| `tfp:world/all_fish#tropical_fish` | `tfp:food/tropical_fish` |
| `tfp:world/all_fish#bluegill` | `tfp:food/bluegill` |
| `tfp:world/all_fish#crappie` | `tfp:food/crappie` |
| `tfp:world/all_fish#lake_trout` | `tfp:food/lake_trout` |
| `tfp:world/all_fish#largemouth_bass` | `tfp:food/largemouth_bass` |
| `tfp:world/all_fish#rainbow_trout` | `tfp:food/rainbow_trout` |
| `tfp:world/all_fish#salmon` | `tfp:food/salmon` |
| `tfp:world/all_fish#smallmouth_bass` | `tfp:food/smallmouth_bass` |
| `tfp:world/all_fish#arctic_char` | `tfp:food/arctic_char` |
| `tfp:world/all_fish#burbot` | `tfp:food/burbot` |
| `tfp:world/all_fish#muksun` | `tfp:food/muksun` |
| `tfp:world/all_fish#northern_pike` | `tfp:food/northern_pike` |
| `tfp:world/all_fish#spotted_gudgeon` | `tfp:food/spotted_gudgeon` |
| `tfp:world/all_fish#tilapia` | `tfp:food/tilapia` |
| `tfp:world/all_fish#peacock_bass` | `tfp:food/peacock_bass` |
| `tfp:world/all_fish#pacu` | `tfp:food/pacu` |
| `tfp:world/all_fish#red_piranha` | `tfp:food/red_piranha` |
| `tfp:world/artist#white` | `minecraft:white_dye` |
| `tfp:world/artist#orange` | `minecraft:orange_dye` |
| `tfp:world/artist#magenta` | `minecraft:magenta_dye` |
| `tfp:world/artist#light_blue` | `minecraft:light_blue_dye` |
| `tfp:world/artist#yellow` | `minecraft:yellow_dye` |
| `tfp:world/artist#lime` | `minecraft:lime_dye` |
| `tfp:world/artist#pink` | `minecraft:pink_dye` |
| `tfp:world/artist#gray` | `minecraft:gray_dye` |
| `tfp:world/artist#light_gray` | `minecraft:light_gray_dye` |
| `tfp:world/artist#cyan` | `minecraft:cyan_dye` |
| `tfp:world/artist#purple` | `minecraft:purple_dye` |
| `tfp:world/artist#blue` | `minecraft:blue_dye` |
| `tfp:world/artist#brown` | `minecraft:brown_dye` |
| `tfp:world/artist#green` | `minecraft:green_dye` |
| `tfp:world/artist#red` | `minecraft:red_dye` |
| `tfp:world/artist#black` | `minecraft:black_dye` |
| `tfp:world/bed#beds` | `#minecraft:beds` |
| `tfp:world/bread#foods/bread` | `#c:foods/bread` |
| `tfp:world/coal#ore/lignite` | `tfp:ore/lignite` |
| `tfp:world/coal#ore/bituminous_coal` | `tfp:ore/bituminous_coal` |
| `tfp:world/compost#compost` | `tfp:compost` |
| `tfp:world/diamond#ore/diamond` | `tfp:ore/diamond` |
| `tfp:world/dune#plant/barrel_cactus` | `tfp:plant/barrel_cactus` |
| `tfp:world/gemologist#amethyst` | `tfp:ore/amethyst` |
| `tfp:world/gemologist#diamond` | `tfp:ore/diamond` |
| `tfp:world/gemologist#emerald` | `tfp:ore/emerald` |
| `tfp:world/gemologist#lapis_lazuli` | `tfp:ore/lapis_lazuli` |
| `tfp:world/gemologist#opal` | `tfp:ore/opal` |
| `tfp:world/gemologist#pyrite` | `tfp:ore/pyrite` |
| `tfp:world/gemologist#ruby` | `tfp:ore/ruby` |
| `tfp:world/gemologist#sapphire` | `tfp:ore/sapphire` |
| `tfp:world/gemologist#topaz` | `tfp:ore/topaz` |
| `tfp:world/graphite#ore/graphite` | `tfp:ore/graphite` |
| `tfp:world/guano#groundcover/guano` | `tfp:groundcover/guano` |
| `tfp:world/iron#hematite` | `tfp:ore/small_hematite` |
| `tfp:world/iron#limonite` | `tfp:ore/small_limonite` |
| `tfp:world/iron#magnetite` | `tfp:ore/small_magnetite` |
| `tfp:world/kaolinite#kaolin_clay` | `tfp:kaolin_clay` |
| `tfp:world/loom#looms` | `#tfp:looms` |
| `tfp:world/metallurgist#bismuth` | `tfp:metal/ingot/bismuth` |
| `tfp:world/metallurgist#bismuth_bronze` | `tfp:metal/ingot/bismuth_bronze` |
| `tfp:world/metallurgist#black_bronze` | `tfp:metal/ingot/black_bronze` |
| `tfp:world/metallurgist#bronze` | `tfp:metal/ingot/bronze` |
| `tfp:world/metallurgist#brass` | `tfp:metal/ingot/brass` |
| `tfp:world/metallurgist#copper` | `tfp:metal/ingot/copper` |
| `tfp:world/metallurgist#gold` | `tfp:metal/ingot/gold` |
| `tfp:world/metallurgist#nickel` | `tfp:metal/ingot/nickel` |
| `tfp:world/metallurgist#rose_gold` | `tfp:metal/ingot/rose_gold` |
| `tfp:world/metallurgist#silver` | `tfp:metal/ingot/silver` |
| `tfp:world/metallurgist#tin` | `tfp:metal/ingot/tin` |
| `tfp:world/metallurgist#zinc` | `tfp:metal/ingot/zinc` |
| `tfp:world/metallurgist#sterling_silver` | `tfp:metal/ingot/sterling_silver` |
| `tfp:world/metallurgist#wrought_iron` | `tfp:metal/ingot/wrought_iron` |
| `tfp:world/metallurgist#cast_iron` | `tfp:metal/ingot/cast_iron` |
| `tfp:world/metallurgist#pig_iron` | `tfp:metal/ingot/pig_iron` |
| `tfp:world/metallurgist#steel` | `tfp:metal/ingot/steel` |
| `tfp:world/metallurgist#black_steel` | `tfp:metal/ingot/black_steel` |
| `tfp:world/metallurgist#blue_steel` | `tfp:metal/ingot/blue_steel` |
| `tfp:world/metallurgist#red_steel` | `tfp:metal/ingot/red_steel` |
| `tfp:world/metallurgist#weak_steel` | `tfp:metal/ingot/weak_steel` |
| `tfp:world/metallurgist#weak_blue_steel` | `tfp:metal/ingot/weak_blue_steel` |
| `tfp:world/metallurgist#weak_red_steel` | `tfp:metal/ingot/weak_red_steel` |
| `tfp:world/metallurgist#high_carbon_steel` | `tfp:metal/ingot/high_carbon_steel` |
| `tfp:world/metallurgist#high_carbon_black_steel` | `tfp:metal/ingot/high_carbon_black_steel` |
| `tfp:world/metallurgist#high_carbon_blue_steel` | `tfp:metal/ingot/high_carbon_blue_steel` |
| `tfp:world/metallurgist#high_carbon_red_steel` | `tfp:metal/ingot/high_carbon_red_steel` |
| `tfp:world/metallurgist#unknown` | `tfp:metal/ingot/unknown` |
| `tfp:world/minerologist#bituminous_coal` | `tfp:ore/bituminous_coal` |
| `tfp:world/minerologist#lignite` | `tfp:ore/lignite` |
| `tfp:world/minerologist#graphite` | `tfp:ore/graphite` |
| `tfp:world/minerologist#cinnabar` | `tfp:ore/cinnabar` |
| `tfp:world/minerologist#cryolite` | `tfp:ore/cryolite` |
| `tfp:world/minerologist#saltpeter` | `tfp:ore/saltpeter` |
| `tfp:world/minerologist#sulfur` | `tfp:ore/sulfur` |
| `tfp:world/minerologist#sylvite` | `tfp:ore/sylvite` |
| `tfp:world/minerologist#borax` | `tfp:ore/borax` |
| `tfp:world/minerologist#gypsum` | `tfp:ore/gypsum` |
| `tfp:world/minerologist#lapis_lazuli` | `tfp:ore/lapis_lazuli` |
| `tfp:world/minerologist#halite` | `tfp:ore/halite` |
| `tfp:world/minerologist#diamond` | `tfp:ore/diamond` |
| `tfp:world/minerologist#emerald` | `tfp:ore/emerald` |
| `tfp:world/minerologist#amethyst` | `tfp:ore/amethyst` |
| `tfp:world/minerologist#opal` | `tfp:ore/opal` |
| `tfp:world/mud_bricks#mud_brick_items` | `#tfp:mud_brick_items` |
| `tfp:world/nickel#small` | `tfp:ore/small_garnierite` |
| `tfp:world/nickel#poor` | `tfp:ore/poor_garnierite` |
| `tfp:world/nickel#normal` | `tfp:ore/normal_garnierite` |
| `tfp:world/nickel#rich` | `tfp:ore/rich_garnierite` |
| `tfp:world/nugget#small_ore_pieces` | `#tfp:small_ore_pieces` |
| `tfp:world/pan#pan/empty` | `tfp:pan/empty` |
| `tfp:world/saplings#acacia` | `tfp:wood/sapling/acacia` |
| `tfp:world/saplings#ash` | `tfp:wood/sapling/ash` |
| `tfp:world/saplings#aspen` | `tfp:wood/sapling/aspen` |
| `tfp:world/saplings#birch` | `tfp:wood/sapling/birch` |
| `tfp:world/saplings#blackwood` | `tfp:wood/sapling/blackwood` |
| `tfp:world/saplings#chestnut` | `tfp:wood/sapling/chestnut` |
| `tfp:world/saplings#douglas_fir` | `tfp:wood/sapling/douglas_fir` |
| `tfp:world/saplings#hickory` | `tfp:wood/sapling/hickory` |
| `tfp:world/saplings#kapok` | `tfp:wood/sapling/kapok` |
| `tfp:world/saplings#mangrove` | `tfp:wood/sapling/mangrove` |
| `tfp:world/saplings#maple` | `tfp:wood/sapling/maple` |
| `tfp:world/saplings#oak` | `tfp:wood/sapling/oak` |
| `tfp:world/saplings#palm` | `tfp:wood/sapling/palm` |
| `tfp:world/saplings#pine` | `tfp:wood/sapling/pine` |
| `tfp:world/saplings#rosewood` | `tfp:wood/sapling/rosewood` |
| `tfp:world/saplings#sequoia` | `tfp:wood/sapling/sequoia` |
| `tfp:world/saplings#spruce` | `tfp:wood/sapling/spruce` |
| `tfp:world/saplings#sycamore` | `tfp:wood/sapling/sycamore` |
| `tfp:world/saplings#white_cedar` | `tfp:wood/sapling/white_cedar` |
| `tfp:world/saplings#willow` | `tfp:wood/sapling/willow` |
| `tfp:world/seeds#seeds` | `#c:seeds` |
| `tfp:world/spindle#spindle` | `tfp:spindle` |
| `tfp:world/sylvite#ore/sylvite` | `tfp:ore/sylvite` |
| `tfp:world/wattle#wattle` | `tfp:wattle` |

## Consume rows

| Criterion | Item |
|---|---|
| `tfp:world/fruit#blackberry` | `tfp:food/blackberry` |
| `tfp:world/fruit#raspberry` | `tfp:food/raspberry` |
| `tfp:world/fruit#blueberry` | `tfp:food/blueberry` |
| `tfp:world/fruit#elderberry` | `tfp:food/elderberry` |
| `tfp:world/fruit#snowberry` | `tfp:food/snowberry` |
| `tfp:world/fruit#bunchberry` | `tfp:food/bunchberry` |
| `tfp:world/fruit#gooseberry` | `tfp:food/gooseberry` |
| `tfp:world/fruit#cloudberry` | `tfp:food/cloudberry` |
| `tfp:world/fruit#strawberry` | `tfp:food/strawberry` |
| `tfp:world/fruit#wintergreen_berry` | `tfp:food/wintergreen_berry` |
| `tfp:world/fruit#cranberry` | `tfp:food/cranberry` |
| `tfp:world/fruit#banana` | `tfp:food/banana` |
| `tfp:world/fruit#cherry` | `tfp:food/cherry` |
| `tfp:world/fruit#green_apple` | `tfp:food/green_apple` |
| `tfp:world/fruit#red_apple` | `tfp:food/red_apple` |
| `tfp:world/fruit#lemon` | `tfp:food/lemon` |
| `tfp:world/fruit#olive` | `tfp:food/olive` |
| `tfp:world/fruit#orange` | `tfp:food/orange` |
| `tfp:world/fruit#peach` | `tfp:food/peach` |
| `tfp:world/fruit#plum` | `tfp:food/plum` |

## Kill rows

| Criterion | Entity |
|---|---|
| `tfp:world/bear_hunter#special_condition` | `#tfp:bears` |
| `tfp:world/glow_hunter#special_condition` | `tfp:octopoteuthis` |
| `tfp:world/hunter#special_condition` | `#tfp:animals` |

## Biome rows

`tfp:<name>` for each of: active_shield_volcano, ancient_shield_volcano, badlands, burren_badlands, burren_badlands_tall, burren_plains, burren_plateau, burren_roche_moutonee, buttes, canyons, cenote_canyons, cenote_highlands, cenote_hills, cenote_plains, cenote_plateau, cenote_rolling_hills, coastal_dunes, collisional_mountains, deep_ocean, deep_ocean_atolls, deep_ocean_trench, doline_canyons, doline_highlands, doline_hills, doline_plains, doline_plateau, doline_rolling_hills, dormant_shield_volcano, drumlins, dune_sea, embayments, extinct_shield_volcano, extreme_doline_mountains, extreme_doline_plateau, glacially_carved_mountains, glacially_carved_oceanic_mountains, glacially_carved_volcanic_mountains, glacially_carved_volcanic_oceanic_mountains, glaciated_mountains, glaciated_oceanic_mountains, glaciated_shield_volcano, glaciated_volcanic_mountains, glaciated_volcanic_oceanic_mountains, grassy_dunes, guano_island, highlands, hills, hoodoos, ice_sheet, ice_sheet_edge, ice_sheet_mountains, ice_sheet_mountains_edge, ice_sheet_oceanic, ice_sheet_oceanic_mountains, ice_sheet_oceanic_mountains_edge, ice_sheet_shield_volcano, ice_sheet_shore, ice_sheet_tuyas, ice_sheet_tuyas_edge, ice_sheet_volcanic_mountains, ice_sheet_volcanic_oceanic_mountains, inverted_patterned_ground, knob_and_kettle, lake, low_canyons, lowlands, meltwater_lake, mesas, mountain_lake, mountains, mud_flats, ocean, ocean_atolls, ocean_reef, ocean_ridge, oceanic_mountain_lake, oceanic_mountains, oceanic_volcanic_arc, old_mountain_lake, old_mountains, old_shield_volcano_shore, patterned_ground, plains, plateau, plateau_lake, plateau_wide, rift_lake, rift_valley, river, river_valley, rocky_plateau, rocky_shores, rolling_hills, salt_flats, salt_marsh, sea_stacks, setback_cliffs, shield_volcano_shore, shilin_canyons, shilin_highlands, shilin_hills, shilin_plains, shilin_plateau, shore, stair_step_canyons, stone_circles, subglacial_lake, sunken_shield_volcano, terrace_lower, terrace_upper, tidal_flats, tower_karst_bay, tower_karst_canyons, tower_karst_highlands, tower_karst_hills, tower_karst_lake, tower_karst_plains, tuyas, volcanic_island, volcanic_mountain_islands, volcanic_mountain_lake, volcanic_mountains, volcanic_oceanic_mountain_lake, volcanic_oceanic_mountains, whorled_canyons.
