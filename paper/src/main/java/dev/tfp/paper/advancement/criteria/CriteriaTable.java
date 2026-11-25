/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/resources/data/tfc/advancement/**\/*.json @ c3b4d9536 (criteria and their conditions)
 */
package dev.tfp.paper.advancement.criteria;

/**
 * PAR-DATA-03: TFC's advancement criteria with the condition each one waits for, one line per criterion
 * ({@code <advancement path> <criterion> <condition>}), written from the pinned TFC data by a scratch script and checked
 * against the {@code /opt/tfc} checkout by {@code CriteriaMapTest}. {@code tfc:} ids are ours as {@code tfp:}; a leading
 * {@code #} is an item/entity tag. Read by {@link CriteriaMap}.
 */
final class CriteriaTable {
    private CriteriaTable() {}

    /** {@code minecraft:inventory_changed}: an item (id) or a tag (#tag) enters the inventory. */
    static final String INVENTORY = """
        story/barrel barrels #tfp:barrels
        story/bellows bellows tfp:bellows
        story/black_steel tools/black_steel #tfp:tools/black_steel
        story/blast_furnace blast_furnace tfp:blast_furnace
        story/bloomery bloomery tfp:bloomery
        story/blowpipe tools/blowpipe #c:tools/blowpipe
        story/blue_steel tools/blue_steel #tfp:tools/blue_steel
        story/brass_mechanisms brass_mechanisms tfp:brass_mechanisms
        story/brass_plated_block metal/block/brass tfp:metal/block/brass
        story/bronze_age tools/bronze #tfp:tools/bronze
        story/bronze_age tools/bismuth_bronze #tfp:tools/bismuth_bronze
        story/bronze_age tools/black_bronze #tfp:tools/black_bronze
        story/cast_iron metal/ingot/cast_iron tfp:metal/ingot/cast_iron
        story/charcoal charcoal minecraft:charcoal
        story/chisel tools/chisel #c:tools/chisel
        story/copper_age tools/copper #tfp:tools/copper
        story/crankshaft crankshaft tfp:crankshaft
        story/crucible crucible tfp:crucible
        story/find_clay clay_ball minecraft:clay_ball
        story/find_rock rock_knapping #tfp:rock_knapping
        story/fire_clay fire_clay tfp:fire_clay
        story/firestarter firestarter tfp:firestarter
        story/flint_and_steel flint_and_steel minecraft:flint_and_steel
        story/flux welding_flux #tfp:welding_flux
        story/gear_box gear_boxes #tfp:gear_boxes
        story/get_straw straw tfp:straw
        story/get_unknown metal/ingot/unknown tfp:metal/ingot/unknown
        story/glass_bottle glass_bottles #tfp:glass_bottles
        story/glass_colors glass minecraft:glass
        story/glass_colors tinted_glass minecraft:tinted_glass
        story/glass_colors white minecraft:white_stained_glass
        story/glass_colors orange minecraft:orange_stained_glass
        story/glass_colors magenta minecraft:magenta_stained_glass
        story/glass_colors light_blue minecraft:light_blue_stained_glass
        story/glass_colors yellow minecraft:yellow_stained_glass
        story/glass_colors lime minecraft:lime_stained_glass
        story/glass_colors pink minecraft:pink_stained_glass
        story/glass_colors gray minecraft:gray_stained_glass
        story/glass_colors light_gray minecraft:light_gray_stained_glass
        story/glass_colors cyan minecraft:cyan_stained_glass
        story/glass_colors purple minecraft:purple_stained_glass
        story/glass_colors blue minecraft:blue_stained_glass
        story/glass_colors brown minecraft:brown_stained_glass
        story/glass_colors green minecraft:green_stained_glass
        story/glass_colors red minecraft:red_stained_glass
        story/glass_colors black minecraft:black_stained_glass
        story/glassworking_tools paddle tfp:paddle
        story/glassworking_tools jacks tfp:jacks
        story/glassworking_tools wool_cloth tfp:wool_cloth
        story/glassworking_tools gem_saw tfp:gem_saw
        story/iron_age metal/ingot/wrought_iron tfp:metal/ingot/wrought_iron
        story/iron_armor metal/sword/wrought_iron tfp:metal/sword/wrought_iron
        story/iron_armor metal/shield/wrought_iron tfp:metal/shield/wrought_iron
        story/iron_armor metal/helmet/wrought_iron tfp:metal/helmet/wrought_iron
        story/iron_armor metal/chestplate/wrought_iron tfp:metal/chestplate/wrought_iron
        story/iron_armor metal/greaves/wrought_iron tfp:metal/greaves/wrought_iron
        story/iron_armor metal/boots/wrought_iron tfp:metal/boots/wrought_iron
        story/iron_bloom raw_iron_bloom tfp:raw_iron_bloom
        story/knap_clay unfired_pottery #tfp:unfired_pottery
        story/leather leather minecraft:leather
        story/lens compass minecraft:compass
        story/lens daylight_detector minecraft:daylight_detector
        story/lens spyglass minecraft:spyglass
        story/logging logs #minecraft:logs
        story/metal_anvil metal/anvil/copper tfp:metal/anvil/copper
        story/mold fired_molds #tfp:fired_molds
        story/papyrus papyrus tfp:papyrus
        story/pickaxe pickaxes #minecraft:pickaxes
        story/plated_block metal_plated_blocks #tfp:metal_plated_blocks
        story/preserves foods/preserves #tfp:foods/preserves
        story/propick tools/propick #c:tools/propick
        story/pump steel_pump tfp:steel_pump
        story/quern handstone tfp:handstone
        story/quern quern tfp:quern
        story/raw_stone stones #c:stones
        story/red_steel tools/red_steel #tfp:tools/red_steel
        story/saw tools/saw #c:tools/saw
        story/scraped_hide scraped_hide #tfp:scraped_hide
        story/sheet sheets #c:sheets
        story/shield tools/shield #c:tools/shield
        story/steel_age tools/steel #tfp:tools/steel
        story/stone_age tools/stone #tfp:tools/stone
        story/trip_hammer trip_hammer tfp:trip_hammer
        story/water_wheel water_wheels #tfp:water_wheels
        story/welding double_ingots #c:double_ingots
        world/all_crops cassava tfp:seeds/cassava
        world/all_crops green_bean tfp:seeds/green_bean
        world/all_crops lentil tfp:seeds/lentil
        world/all_crops peanut tfp:seeds/peanut
        world/all_crops soybean tfp:seeds/soybean
        world/all_crops barley tfp:seeds/barley
        world/all_crops oat tfp:seeds/oat
        world/all_crops rye tfp:seeds/rye
        world/all_crops maize tfp:seeds/maize
        world/all_crops wheat tfp:seeds/wheat
        world/all_crops rice tfp:seeds/rice
        world/all_crops beet tfp:seeds/beet
        world/all_crops cabbage tfp:seeds/cabbage
        world/all_crops carrot tfp:seeds/carrot
        world/all_crops garlic tfp:seeds/garlic
        world/all_crops onion tfp:seeds/onion
        world/all_crops potato tfp:seeds/potato
        world/all_crops squash tfp:seeds/squash
        world/all_crops tomato tfp:seeds/tomato
        world/all_crops red_bell_pepper tfp:seeds/red_bell_pepper
        world/all_crops yellow_bell_pepper tfp:seeds/yellow_bell_pepper
        world/all_crops pumpkin tfp:seeds/pumpkin
        world/all_crops melon tfp:seeds/melon
        world/all_crops canola tfp:seeds/canola
        world/all_crops radish tfp:seeds/radish
        world/all_crops alfalfa tfp:seeds/alfalfa
        world/all_crops jute tfp:seeds/jute
        world/all_crops papyrus tfp:seeds/papyrus
        world/all_crops sugarcane tfp:seeds/sugarcane
        world/all_fish cod tfp:food/cod
        world/all_fish calamari tfp:food/calamari
        world/all_fish shellfish tfp:food/shellfish
        world/all_fish tropical_fish tfp:food/tropical_fish
        world/all_fish bluegill tfp:food/bluegill
        world/all_fish crappie tfp:food/crappie
        world/all_fish lake_trout tfp:food/lake_trout
        world/all_fish largemouth_bass tfp:food/largemouth_bass
        world/all_fish rainbow_trout tfp:food/rainbow_trout
        world/all_fish salmon tfp:food/salmon
        world/all_fish smallmouth_bass tfp:food/smallmouth_bass
        world/all_fish arctic_char tfp:food/arctic_char
        world/all_fish burbot tfp:food/burbot
        world/all_fish muksun tfp:food/muksun
        world/all_fish northern_pike tfp:food/northern_pike
        world/all_fish spotted_gudgeon tfp:food/spotted_gudgeon
        world/all_fish tilapia tfp:food/tilapia
        world/all_fish peacock_bass tfp:food/peacock_bass
        world/all_fish pacu tfp:food/pacu
        world/all_fish red_piranha tfp:food/red_piranha
        world/artist white minecraft:white_dye
        world/artist orange minecraft:orange_dye
        world/artist magenta minecraft:magenta_dye
        world/artist light_blue minecraft:light_blue_dye
        world/artist yellow minecraft:yellow_dye
        world/artist lime minecraft:lime_dye
        world/artist pink minecraft:pink_dye
        world/artist gray minecraft:gray_dye
        world/artist light_gray minecraft:light_gray_dye
        world/artist cyan minecraft:cyan_dye
        world/artist purple minecraft:purple_dye
        world/artist blue minecraft:blue_dye
        world/artist brown minecraft:brown_dye
        world/artist green minecraft:green_dye
        world/artist red minecraft:red_dye
        world/artist black minecraft:black_dye
        world/bed beds #minecraft:beds
        world/bread foods/bread #c:foods/bread
        world/coal ore/lignite tfp:ore/lignite
        world/coal ore/bituminous_coal tfp:ore/bituminous_coal
        world/compost compost tfp:compost
        world/diamond ore/diamond tfp:ore/diamond
        world/dune plant/barrel_cactus tfp:plant/barrel_cactus
        world/gemologist amethyst tfp:ore/amethyst
        world/gemologist diamond tfp:ore/diamond
        world/gemologist emerald tfp:ore/emerald
        world/gemologist lapis_lazuli tfp:ore/lapis_lazuli
        world/gemologist opal tfp:ore/opal
        world/gemologist pyrite tfp:ore/pyrite
        world/gemologist ruby tfp:ore/ruby
        world/gemologist sapphire tfp:ore/sapphire
        world/gemologist topaz tfp:ore/topaz
        world/graphite ore/graphite tfp:ore/graphite
        world/guano groundcover/guano tfp:groundcover/guano
        world/iron hematite tfp:ore/small_hematite
        world/iron limonite tfp:ore/small_limonite
        world/iron magnetite tfp:ore/small_magnetite
        world/kaolinite kaolin_clay tfp:kaolin_clay
        world/loom looms #tfp:looms
        world/metallurgist bismuth tfp:metal/ingot/bismuth
        world/metallurgist bismuth_bronze tfp:metal/ingot/bismuth_bronze
        world/metallurgist black_bronze tfp:metal/ingot/black_bronze
        world/metallurgist bronze tfp:metal/ingot/bronze
        world/metallurgist brass tfp:metal/ingot/brass
        world/metallurgist copper tfp:metal/ingot/copper
        world/metallurgist gold tfp:metal/ingot/gold
        world/metallurgist nickel tfp:metal/ingot/nickel
        world/metallurgist rose_gold tfp:metal/ingot/rose_gold
        world/metallurgist silver tfp:metal/ingot/silver
        world/metallurgist tin tfp:metal/ingot/tin
        world/metallurgist zinc tfp:metal/ingot/zinc
        world/metallurgist sterling_silver tfp:metal/ingot/sterling_silver
        world/metallurgist wrought_iron tfp:metal/ingot/wrought_iron
        world/metallurgist cast_iron tfp:metal/ingot/cast_iron
        world/metallurgist pig_iron tfp:metal/ingot/pig_iron
        world/metallurgist steel tfp:metal/ingot/steel
        world/metallurgist black_steel tfp:metal/ingot/black_steel
        world/metallurgist blue_steel tfp:metal/ingot/blue_steel
        world/metallurgist red_steel tfp:metal/ingot/red_steel
        world/metallurgist weak_steel tfp:metal/ingot/weak_steel
        world/metallurgist weak_blue_steel tfp:metal/ingot/weak_blue_steel
        world/metallurgist weak_red_steel tfp:metal/ingot/weak_red_steel
        world/metallurgist high_carbon_steel tfp:metal/ingot/high_carbon_steel
        world/metallurgist high_carbon_black_steel tfp:metal/ingot/high_carbon_black_steel
        world/metallurgist high_carbon_blue_steel tfp:metal/ingot/high_carbon_blue_steel
        world/metallurgist high_carbon_red_steel tfp:metal/ingot/high_carbon_red_steel
        world/metallurgist unknown tfp:metal/ingot/unknown
        world/minerologist bituminous_coal tfp:ore/bituminous_coal
        world/minerologist lignite tfp:ore/lignite
        world/minerologist graphite tfp:ore/graphite
        world/minerologist cinnabar tfp:ore/cinnabar
        world/minerologist cryolite tfp:ore/cryolite
        world/minerologist saltpeter tfp:ore/saltpeter
        world/minerologist sulfur tfp:ore/sulfur
        world/minerologist sylvite tfp:ore/sylvite
        world/minerologist borax tfp:ore/borax
        world/minerologist gypsum tfp:ore/gypsum
        world/minerologist lapis_lazuli tfp:ore/lapis_lazuli
        world/minerologist halite tfp:ore/halite
        world/minerologist diamond tfp:ore/diamond
        world/minerologist emerald tfp:ore/emerald
        world/minerologist amethyst tfp:ore/amethyst
        world/minerologist opal tfp:ore/opal
        world/mud_bricks mud_brick_items #tfp:mud_brick_items
        world/nickel small tfp:ore/small_garnierite
        world/nickel poor tfp:ore/poor_garnierite
        world/nickel normal tfp:ore/normal_garnierite
        world/nickel rich tfp:ore/rich_garnierite
        world/nugget small_ore_pieces #tfp:small_ore_pieces
        world/pan pan/empty tfp:pan/empty
        world/saplings acacia tfp:wood/sapling/acacia
        world/saplings ash tfp:wood/sapling/ash
        world/saplings aspen tfp:wood/sapling/aspen
        world/saplings birch tfp:wood/sapling/birch
        world/saplings blackwood tfp:wood/sapling/blackwood
        world/saplings chestnut tfp:wood/sapling/chestnut
        world/saplings douglas_fir tfp:wood/sapling/douglas_fir
        world/saplings hickory tfp:wood/sapling/hickory
        world/saplings kapok tfp:wood/sapling/kapok
        world/saplings mangrove tfp:wood/sapling/mangrove
        world/saplings maple tfp:wood/sapling/maple
        world/saplings oak tfp:wood/sapling/oak
        world/saplings palm tfp:wood/sapling/palm
        world/saplings pine tfp:wood/sapling/pine
        world/saplings rosewood tfp:wood/sapling/rosewood
        world/saplings sequoia tfp:wood/sapling/sequoia
        world/saplings spruce tfp:wood/sapling/spruce
        world/saplings sycamore tfp:wood/sapling/sycamore
        world/saplings white_cedar tfp:wood/sapling/white_cedar
        world/saplings willow tfp:wood/sapling/willow
        world/seeds seeds #c:seeds
        world/spindle spindle tfp:spindle
        world/sylvite ore/sylvite tfp:ore/sylvite
        world/wattle wattle tfp:wattle
        """;

    /** {@code minecraft:consume_item}: the player ate this item. */
    static final String CONSUME = """
        world/fruit blackberry tfp:food/blackberry
        world/fruit raspberry tfp:food/raspberry
        world/fruit blueberry tfp:food/blueberry
        world/fruit elderberry tfp:food/elderberry
        world/fruit snowberry tfp:food/snowberry
        world/fruit bunchberry tfp:food/bunchberry
        world/fruit gooseberry tfp:food/gooseberry
        world/fruit cloudberry tfp:food/cloudberry
        world/fruit strawberry tfp:food/strawberry
        world/fruit wintergreen_berry tfp:food/wintergreen_berry
        world/fruit cranberry tfp:food/cranberry
        world/fruit banana tfp:food/banana
        world/fruit cherry tfp:food/cherry
        world/fruit green_apple tfp:food/green_apple
        world/fruit red_apple tfp:food/red_apple
        world/fruit lemon tfp:food/lemon
        world/fruit olive tfp:food/olive
        world/fruit orange tfp:food/orange
        world/fruit peach tfp:food/peach
        world/fruit plum tfp:food/plum
        """;

    /** {@code minecraft:location}: the player stands in the biome named like the criterion ({@code tfp:<criterion>}). */
    static final String BIOMES = """
        world/adventuring_time badlands
        world/adventuring_time canyons
        world/adventuring_time low_canyons
        world/adventuring_time plains
        world/adventuring_time plateau
        world/adventuring_time plateau_wide
        world/adventuring_time hills
        world/adventuring_time rolling_hills
        world/adventuring_time highlands
        world/adventuring_time lake
        world/adventuring_time lowlands
        world/adventuring_time salt_marsh
        world/adventuring_time mountains
        world/adventuring_time volcanic_mountains
        world/adventuring_time old_mountains
        world/adventuring_time oceanic_mountains
        world/adventuring_time volcanic_oceanic_mountains
        world/adventuring_time ocean
        world/adventuring_time ocean_reef
        world/adventuring_time deep_ocean
        world/adventuring_time deep_ocean_trench
        world/adventuring_time river
        world/adventuring_time guano_island
        world/adventuring_time shore
        world/adventuring_time tidal_flats
        world/adventuring_time sea_stacks
        world/adventuring_time terrace_upper
        world/adventuring_time terrace_lower
        world/adventuring_time setback_cliffs
        world/adventuring_time coastal_dunes
        world/adventuring_time rocky_shores
        world/adventuring_time embayments
        world/adventuring_time salt_flats
        world/adventuring_time mud_flats
        world/adventuring_time dune_sea
        world/adventuring_time grassy_dunes
        world/adventuring_time whorled_canyons
        world/adventuring_time stair_step_canyons
        world/adventuring_time mesas
        world/adventuring_time buttes
        world/adventuring_time hoodoos
        world/adventuring_time rocky_plateau
        world/adventuring_time tower_karst_plains
        world/adventuring_time burren_plains
        world/adventuring_time shilin_plains
        world/adventuring_time doline_plains
        world/adventuring_time cenote_plains
        world/adventuring_time tower_karst_canyons
        world/adventuring_time doline_canyons
        world/adventuring_time cenote_canyons
        world/adventuring_time shilin_canyons
        world/adventuring_time tower_karst_hills
        world/adventuring_time shilin_hills
        world/adventuring_time doline_hills
        world/adventuring_time cenote_hills
        world/adventuring_time tower_karst_highlands
        world/adventuring_time burren_badlands_tall
        world/adventuring_time shilin_highlands
        world/adventuring_time doline_highlands
        world/adventuring_time cenote_highlands
        world/adventuring_time extreme_doline_plateau
        world/adventuring_time burren_plateau
        world/adventuring_time shilin_plateau
        world/adventuring_time doline_plateau
        world/adventuring_time cenote_plateau
        world/adventuring_time tower_karst_lake
        world/adventuring_time tower_karst_bay
        world/adventuring_time extreme_doline_mountains
        world/adventuring_time burren_badlands
        world/adventuring_time doline_rolling_hills
        world/adventuring_time cenote_rolling_hills
        world/adventuring_time burren_roche_moutonee
        world/adventuring_time active_shield_volcano
        world/adventuring_time dormant_shield_volcano
        world/adventuring_time extinct_shield_volcano
        world/adventuring_time ancient_shield_volcano
        world/adventuring_time sunken_shield_volcano
        world/adventuring_time shield_volcano_shore
        world/adventuring_time old_shield_volcano_shore
        world/adventuring_time mountain_lake
        world/adventuring_time volcanic_mountain_lake
        world/adventuring_time old_mountain_lake
        world/adventuring_time oceanic_mountain_lake
        world/adventuring_time volcanic_oceanic_mountain_lake
        world/adventuring_time plateau_lake
        world/adventuring_time ice_sheet
        world/adventuring_time ice_sheet_mountains
        world/adventuring_time ice_sheet_oceanic_mountains
        world/adventuring_time ice_sheet_shield_volcano
        world/adventuring_time ice_sheet_tuyas
        world/adventuring_time subglacial_lake
        world/adventuring_time ice_sheet_edge
        world/adventuring_time ice_sheet_tuyas_edge
        world/adventuring_time ice_sheet_mountains_edge
        world/adventuring_time ice_sheet_oceanic_mountains_edge
        world/adventuring_time meltwater_lake
        world/adventuring_time ice_sheet_oceanic
        world/adventuring_time ice_sheet_shore
        world/adventuring_time glaciated_shield_volcano
        world/adventuring_time glaciated_mountains
        world/adventuring_time glaciated_oceanic_mountains
        world/adventuring_time glacially_carved_mountains
        world/adventuring_time glacially_carved_oceanic_mountains
        world/adventuring_time drumlins
        world/adventuring_time tuyas
        world/adventuring_time knob_and_kettle
        world/adventuring_time patterned_ground
        world/adventuring_time inverted_patterned_ground
        world/adventuring_time stone_circles
        world/adventuring_time oceanic_volcanic_arc
        world/adventuring_time ocean_ridge
        world/adventuring_time rift_valley
        world/adventuring_time rift_lake
        world/adventuring_time collisional_mountains
        world/adventuring_time volcanic_island
        world/adventuring_time volcanic_mountain_islands
        world/adventuring_time ice_sheet_volcanic_mountains
        world/adventuring_time glaciated_volcanic_mountains
        world/adventuring_time glacially_carved_volcanic_mountains
        world/adventuring_time ice_sheet_volcanic_oceanic_mountains
        world/adventuring_time glaciated_volcanic_oceanic_mountains
        world/adventuring_time glacially_carved_volcanic_oceanic_mountains
        world/adventuring_time ocean_atolls
        world/adventuring_time deep_ocean_atolls
        world/adventuring_time river_valley
        world/coral_reef ocean_reef
        world/trench deep_ocean_trench
        world/volcano volcanic_mountains
        world/volcano volcanic_oceanic_mountains
        world/volcano volcanic_mountain_lake
        world/volcano volcanic_oceanic_mountain_lake
        world/volcano oceanic_volcanic_arc
        world/volcano volcanic_island
        world/volcano volcanic_mountain_islands
        world/volcano ice_sheet_volcanic_mountains
        world/volcano glaciated_volcanic_mountains
        world/volcano glacially_carved_volcanic_mountains
        world/volcano ice_sheet_volcanic_oceanic_mountains
        world/volcano glaciated_volcanic_oceanic_mountains
        world/volcano glacially_carved_volcanic_oceanic_mountains
        """;

    /** {@code minecraft:player_killed_entity}: the player killed an entity of this type or tag. */
    static final String KILLS = """
        world/bear_hunter special_condition #tfp:bears
        world/glow_hunter special_condition tfp:octopoteuthis
        world/hunter special_condition #tfp:animals
        """;

    /** TFC's own triggers whose feature has no event yet: follow-up {@code PAR-DATA-19}. */
    static final String PENDING = """
        story/basin_pour special_condition tfc:basin_pour
        story/firepit special_condition tfc:firepit_created
        story/forge special_condition tfc:lit
        story/grill special_condition tfc:firepit_created
        story/perfectly_forged special_condition tfc:perfectly_forged
        story/pit_kiln special_condition tfc:lit
        story/pot special_condition tfc:firepit_created
        story/smooth_stone special_condition tfc:chiseled
        story/stone_anvil special_condition tfc:rock_anvil
        story/table_pour special_condition tfc:table_pour
        story/the_future special_condition tfc:present_day
        story/windmill special_condition tfc:max_windmill
        world/advanced_fishing special_condition tfc:hooked_entity
        world/familiarity special_condition tfc:fed_animal
        world/fishing special_condition tfc:hooked_entity
        world/full_fertilizer special_condition tfc:full_fertilizer
        world/full_powderkeg special_condition tfc:full_powderkeg
        world/greatest_hunter special_condition tfc:stab_entity
        world/lava_lamp special_condition tfc:lava_lamp
        world/powderkeg special_condition tfc:lit
        world/rotten_compost special_condition tfc:rotten_compost_kill
        """;
}
