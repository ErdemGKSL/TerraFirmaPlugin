/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/resources/data/tfc/loot_table/blocks/plant/*.json @ c3b4d9536
 * (also wood/leaves, wood/twig, wood/fallen_leaves and groundcover/stick.json of the same loot table tree)
 */
package dev.tfp.core.gather;

import static java.util.stream.Collectors.toMap;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The loot tables of TFC's plants, leaves, twigs, fallen leaves and the groundcover stick, transcribed one table per block
 * at the pinned commit (c3b4d9536). Each method mirrors one JSON file: a pool per entry, in file order.
 *
 * <p>Generated data: do not edit by hand. {@code HarvestGoldenTest} checks every table against values computed from the raw
 * TFC JSON, so a transcription slip fails the build.
 */
public final class HarvestTables {
    private HarvestTables() {}

    private static final List<HarvestTable> ALL = List.of(
            t_tfc_plant_allium(),
            t_tfc_plant_anemone_green(),
            t_tfc_plant_anemone_large_orange(),
            t_tfc_plant_anemone_large_purple(),
            t_tfc_plant_anemone_purple(),
            t_tfc_plant_anthurium(),
            t_tfc_plant_arrowhead(),
            t_tfc_plant_artists_conk(),
            t_tfc_plant_arundo(),
            t_tfc_plant_aspen_krummholz(),
            t_tfc_plant_athyrium_fern(),
            t_tfc_plant_azalea(),
            t_tfc_plant_badderlocks(),
            t_tfc_plant_banana_plant(),
            t_tfc_plant_banana_sapling(),
            t_tfc_plant_barnacles(),
            t_tfc_plant_barrel_cactus(),
            t_tfc_plant_beachgrass(),
            t_tfc_plant_bear_grass(),
            t_tfc_plant_bird_nest_fern(),
            t_tfc_plant_black_orchid(),
            t_tfc_plant_blackberry_bush(),
            t_tfc_plant_blackberry_bush_cane(),
            t_tfc_plant_blood_lily(),
            t_tfc_plant_blue_ginger(),
            t_tfc_plant_blue_orchid(),
            t_tfc_plant_blueberry_bush(),
            t_tfc_plant_blueberry_bush_cane(),
            t_tfc_plant_bluegrass(),
            t_tfc_plant_bromegrass(),
            t_tfc_plant_bunchberry_bush(),
            t_tfc_plant_bur_reed(),
            t_tfc_plant_buttercup(),
            t_tfc_plant_butterfly_milkweed(),
            t_tfc_plant_calendula(),
            t_tfc_plant_canna(),
            t_tfc_plant_cattail(),
            t_tfc_plant_cherry_branch(),
            t_tfc_plant_cherry_growing_branch(),
            t_tfc_plant_cherry_leaves(),
            t_tfc_plant_cherry_sapling(),
            t_tfc_plant_cloudberry_bush(),
            t_tfc_plant_cobblestone_lichen(),
            t_tfc_plant_coontail(),
            t_tfc_plant_cordgrass(),
            t_tfc_plant_cornflower(),
            t_tfc_plant_cranberry_bush(),
            t_tfc_plant_cycad(),
            t_tfc_plant_dandelion(),
            t_tfc_plant_dead_banana_plant(),
            t_tfc_plant_dead_berry_bush(),
            t_tfc_plant_dead_bush(),
            t_tfc_plant_dead_cane(),
            t_tfc_plant_desert_flame(),
            t_tfc_plant_douglas_fir_krummholz(),
            t_tfc_plant_dry_grass(),
            t_tfc_plant_dry_phragmite(),
            t_tfc_plant_duckweed(),
            t_tfc_plant_edelweiss(),
            t_tfc_plant_eel_grass(),
            t_tfc_plant_elderberry_bush(),
            t_tfc_plant_elderberry_bush_cane(),
            t_tfc_plant_elegant_sunburst_lichen(),
            t_tfc_plant_fan_palm(),
            t_tfc_plant_field_horsetail(),
            t_tfc_plant_flame_vine(),
            t_tfc_plant_fountain_grass(),
            t_tfc_plant_foxglove(),
            t_tfc_plant_giant_kelp_flower(),
            t_tfc_plant_golden_bamboo(),
            t_tfc_plant_golden_bamboo_sapling(),
            t_tfc_plant_goldenrod(),
            t_tfc_plant_gooseberry_bush(),
            t_tfc_plant_grape_hyacinth(),
            t_tfc_plant_green_algae(),
            t_tfc_plant_green_apple_branch(),
            t_tfc_plant_green_apple_growing_branch(),
            t_tfc_plant_green_apple_leaves(),
            t_tfc_plant_green_apple_sapling(),
            t_tfc_plant_gutweed(),
            t_tfc_plant_guzmania(),
            t_tfc_plant_hanging_vines(),
            t_tfc_plant_heather(),
            t_tfc_plant_heliconia(),
            t_tfc_plant_hibiscus(),
            t_tfc_plant_houstonia(),
            t_tfc_plant_ivy(),
            t_tfc_plant_jungle_vines(),
            t_tfc_plant_kangaroo_paw(),
            t_tfc_plant_king_fern(),
            t_tfc_plant_kinnikinnick(),
            t_tfc_plant_labrador_tea(),
            t_tfc_plant_lady_fern(),
            t_tfc_plant_laminaria(),
            t_tfc_plant_leafy_kelp(),
            t_tfc_plant_lemon_branch(),
            t_tfc_plant_lemon_growing_branch(),
            t_tfc_plant_lemon_leaves(),
            t_tfc_plant_lemon_sapling(),
            t_tfc_plant_liana(),
            t_tfc_plant_licorice_fern(),
            t_tfc_plant_lilac(),
            t_tfc_plant_lily_of_the_valley(),
            t_tfc_plant_lotus(),
            t_tfc_plant_maiden_pink(),
            t_tfc_plant_manatee_grass(),
            t_tfc_plant_marigold(),
            t_tfc_plant_meads_milkweed(),
            t_tfc_plant_milfoil(),
            t_tfc_plant_morning_glory(),
            t_tfc_plant_moss(),
            t_tfc_plant_moss_campion(),
            t_tfc_plant_mountain_hullwort(),
            t_tfc_plant_mussels(),
            t_tfc_plant_nasturtium(),
            t_tfc_plant_olive_branch(),
            t_tfc_plant_olive_growing_branch(),
            t_tfc_plant_olive_leaves(),
            t_tfc_plant_olive_sapling(),
            t_tfc_plant_orange_branch(),
            t_tfc_plant_orange_growing_branch(),
            t_tfc_plant_orange_leaves(),
            t_tfc_plant_orange_sapling(),
            t_tfc_plant_orchard_grass(),
            t_tfc_plant_ostrich_fern(),
            t_tfc_plant_oxeye_daisy(),
            t_tfc_plant_palash(),
            t_tfc_plant_pampas_grass(),
            t_tfc_plant_peach_branch(),
            t_tfc_plant_peach_growing_branch(),
            t_tfc_plant_peach_leaves(),
            t_tfc_plant_peach_sapling(),
            t_tfc_plant_penwortel(),
            t_tfc_plant_perovskia(),
            t_tfc_plant_philodendron(),
            t_tfc_plant_phragmite(),
            t_tfc_plant_pickerelweed(),
            t_tfc_plant_pine_krummholz(),
            t_tfc_plant_pistia(),
            t_tfc_plant_plum_branch(),
            t_tfc_plant_plum_growing_branch(),
            t_tfc_plant_plum_leaves(),
            t_tfc_plant_plum_sapling(),
            t_tfc_plant_poppy(),
            t_tfc_plant_prickly_pear(),
            t_tfc_plant_prickly_pear_purple(),
            t_tfc_plant_primrose(),
            t_tfc_plant_pulsatilla(),
            t_tfc_plant_purple_water_lily(),
            t_tfc_plant_qantu(),
            t_tfc_plant_raddia_grass(),
            t_tfc_plant_ramirezella(),
            t_tfc_plant_ramunda(),
            t_tfc_plant_raspberry_bush(),
            t_tfc_plant_raspberry_bush_cane(),
            t_tfc_plant_red_algae(),
            t_tfc_plant_red_apple_branch(),
            t_tfc_plant_red_apple_growing_branch(),
            t_tfc_plant_red_apple_leaves(),
            t_tfc_plant_red_apple_sapling(),
            t_tfc_plant_red_oat_grass(),
            t_tfc_plant_red_sealing_wax_palm(),
            t_tfc_plant_reindeer_lichen(),
            t_tfc_plant_rose(),
            t_tfc_plant_ryegrass(),
            t_tfc_plant_sacred_datura(),
            t_tfc_plant_sagebrush(),
            t_tfc_plant_sago(),
            t_tfc_plant_saguaro(),
            t_tfc_plant_saguaro_fruit(),
            t_tfc_plant_saguaro_plant(),
            t_tfc_plant_sapphire_tower(),
            t_tfc_plant_sargassum(),
            t_tfc_plant_scutch_grass(),
            t_tfc_plant_sea_lavender(),
            t_tfc_plant_sea_palm(),
            t_tfc_plant_shawiash(),
            t_tfc_plant_silken_pincushion_cactus(),
            t_tfc_plant_silver_bromeliad(),
            t_tfc_plant_silver_spurflower(),
            t_tfc_plant_snapdragon_pink(),
            t_tfc_plant_snapdragon_red(),
            t_tfc_plant_snapdragon_white(),
            t_tfc_plant_snapdragon_yellow(),
            t_tfc_plant_snowberry_bush(),
            t_tfc_plant_spanish_moss(),
            t_tfc_plant_spruce_krummholz(),
            t_tfc_plant_star_grass(),
            t_tfc_plant_starfish(),
            t_tfc_plant_strawberry_bush(),
            t_tfc_plant_strelitzia(),
            t_tfc_plant_sunflower(),
            t_tfc_plant_switchgrass(),
            t_tfc_plant_sword_fern(),
            t_tfc_plant_tall_fescue_grass(),
            t_tfc_plant_tank_bromeliad(),
            t_tfc_plant_timothy_grass(),
            t_tfc_plant_toquilla_palm(),
            t_tfc_plant_tree_fern(),
            t_tfc_plant_trillium(),
            t_tfc_plant_tropical_milkweed(),
            t_tfc_plant_tulip_orange(),
            t_tfc_plant_tulip_pink(),
            t_tfc_plant_tulip_red(),
            t_tfc_plant_tulip_white(),
            t_tfc_plant_turtle_grass(),
            t_tfc_plant_vriesea(),
            t_tfc_plant_water_canna(),
            t_tfc_plant_water_taro(),
            t_tfc_plant_white_cedar_krummholz(),
            t_tfc_plant_white_water_lily(),
            t_tfc_plant_winged_kelp(),
            t_tfc_plant_wintergreen_berry_bush(),
            t_tfc_plant_yellow_saxifrage(),
            t_tfc_plant_yellow_water_lily(),
            t_tfc_plant_yucca(),
            t_tfc_wood_leaves_acacia(),
            t_tfc_wood_leaves_ash(),
            t_tfc_wood_leaves_aspen(),
            t_tfc_wood_leaves_birch(),
            t_tfc_wood_leaves_blackwood(),
            t_tfc_wood_leaves_chestnut(),
            t_tfc_wood_leaves_douglas_fir(),
            t_tfc_wood_leaves_hickory(),
            t_tfc_wood_leaves_kapok(),
            t_tfc_wood_leaves_mangrove(),
            t_tfc_wood_leaves_maple(),
            t_tfc_wood_leaves_oak(),
            t_tfc_wood_leaves_palm(),
            t_tfc_wood_leaves_pine(),
            t_tfc_wood_leaves_rosewood(),
            t_tfc_wood_leaves_sequoia(),
            t_tfc_wood_leaves_spruce(),
            t_tfc_wood_leaves_sycamore(),
            t_tfc_wood_leaves_white_cedar(),
            t_tfc_wood_leaves_willow(),
            t_tfc_wood_twig_acacia(),
            t_tfc_wood_twig_ash(),
            t_tfc_wood_twig_aspen(),
            t_tfc_wood_twig_birch(),
            t_tfc_wood_twig_blackwood(),
            t_tfc_wood_twig_chestnut(),
            t_tfc_wood_twig_douglas_fir(),
            t_tfc_wood_twig_hickory(),
            t_tfc_wood_twig_kapok(),
            t_tfc_wood_twig_mangrove(),
            t_tfc_wood_twig_maple(),
            t_tfc_wood_twig_oak(),
            t_tfc_wood_twig_palm(),
            t_tfc_wood_twig_pine(),
            t_tfc_wood_twig_rosewood(),
            t_tfc_wood_twig_sequoia(),
            t_tfc_wood_twig_spruce(),
            t_tfc_wood_twig_sycamore(),
            t_tfc_wood_twig_white_cedar(),
            t_tfc_wood_twig_willow(),
            t_tfc_wood_fallen_leaves_acacia(),
            t_tfc_wood_fallen_leaves_ash(),
            t_tfc_wood_fallen_leaves_aspen(),
            t_tfc_wood_fallen_leaves_birch(),
            t_tfc_wood_fallen_leaves_blackwood(),
            t_tfc_wood_fallen_leaves_chestnut(),
            t_tfc_wood_fallen_leaves_douglas_fir(),
            t_tfc_wood_fallen_leaves_hickory(),
            t_tfc_wood_fallen_leaves_kapok(),
            t_tfc_wood_fallen_leaves_mangrove(),
            t_tfc_wood_fallen_leaves_maple(),
            t_tfc_wood_fallen_leaves_oak(),
            t_tfc_wood_fallen_leaves_palm(),
            t_tfc_wood_fallen_leaves_pine(),
            t_tfc_wood_fallen_leaves_rosewood(),
            t_tfc_wood_fallen_leaves_sequoia(),
            t_tfc_wood_fallen_leaves_spruce(),
            t_tfc_wood_fallen_leaves_sycamore(),
            t_tfc_wood_fallen_leaves_white_cedar(),
            t_tfc_wood_fallen_leaves_willow(),
            t_tfc_groundcover_stick()
        );

    private static final Map<String, HarvestTable> BY_ID = ALL.stream().collect(toMap(HarvestTable::blockId, t -> t));

    /** Every table, one per TFC loot table file, in a fixed order. */
    public static List<HarvestTable> all() {
        return ALL;
    }

    /** The loot table of the block with this id, for example {@code tfc:plant/allium}. */
    public static Optional<HarvestTable> find(String blockId) {
        return Optional.ofNullable(BY_ID.get(blockId));
    }

    private static HarvestTable t_tfc_plant_allium() {
        return table("tfc:plant/allium",
                item("tfc:plant/allium", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_anemone_green() {
        return table("tfc:plant/anemone_green",
                item("tfc:plant/anemone_green", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_anemone_large_orange() {
        return table("tfc:plant/anemone_large_orange",
                item("tfc:plant/anemone_large_orange", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_anemone_large_purple() {
        return table("tfc:plant/anemone_large_purple",
                item("tfc:plant/anemone_large_purple", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_anemone_purple() {
        return table("tfc:plant/anemone_purple",
                item("tfc:plant/anemone_purple", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_anthurium() {
        return table("tfc:plant/anthurium",
                item("tfc:plant/anthurium", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_arrowhead() {
        return table("tfc:plant/arrowhead",
                item("tfc:plant/arrowhead", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_artists_conk() {
        return table("tfc:plant/artists_conk",
                item("tfc:plant/artists_conk", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_arundo() {
        return table("tfc:plant/arundo",
                item("tfc:plant/arundo", List.of(), List.of()));
    }

    private static HarvestTable t_tfc_plant_aspen_krummholz() {
        return table("tfc:plant/aspen_krummholz",
                alt(List.of(), item("tfc:plant/aspen_krummholz", when(state(Map.of("tip", "true")), tool(ToolTag.AXE)), List.of()), item("tfc:wood/sapling/aspen", when(chance(0.02)), List.of()), item("minecraft:stick", List.of(), List.of(setCount(List.of(), 1, 3)))));
    }

    private static HarvestTable t_tfc_plant_athyrium_fern() {
        return table("tfc:plant/athyrium_fern",
                item("tfc:plant/athyrium_fern", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_azalea() {
        return table("tfc:plant/azalea",
                item("tfc:plant/azalea", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_badderlocks() {
        return table("tfc:plant/badderlocks",
                item("tfc:plant/badderlocks", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_banana_plant() {
        return table("tfc:plant/banana_plant",
                item("tfc:plant/banana_sapling", when(state(Map.of("stage", "2"))), List.of(setCount(List.of(), 1, 2))));
    }

    private static HarvestTable t_tfc_plant_banana_sapling() {
        return table("tfc:plant/banana_sapling",
                item("tfc:plant/banana_sapling", List.of(), List.of()));
    }

    private static HarvestTable t_tfc_plant_barnacles() {
        return table("tfc:plant/barnacles",
                item("tfc:plant/barnacles", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_barrel_cactus() {
        return table("tfc:plant/barrel_cactus",
                item("tfc:plant/barrel_cactus", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_beachgrass() {
        return table("tfc:plant/beachgrass",
                alt(List.of(), item("tfc:plant/beachgrass", when(tool(ToolTag.SHEAR)), List.of()), item("tfc:straw", when(tool(ToolTag.SHARP)), List.of())));
    }

    private static HarvestTable t_tfc_plant_bear_grass() {
        return table("tfc:plant/bear_grass",
                item("tfc:plant/bear_grass", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_bird_nest_fern() {
        return table("tfc:plant/bird_nest_fern",
                item("tfc:plant/bird_nest_fern", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_black_orchid() {
        return table("tfc:plant/black_orchid",
                item("tfc:plant/black_orchid", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_blackberry_bush() {
        return table("tfc:plant/blackberry_bush",
                alt(List.of(), item("tfc:plant/blackberry_bush", when(tool(ToolTag.SHARP), state(Map.of("stage", "2"))), List.of()), item("tfc:plant/blackberry_bush", when(tool(ToolTag.SHARP), chance(0.5)), List.of())),
                item("minecraft:stick", List.of(), List.of()));
    }

    private static HarvestTable t_tfc_plant_blackberry_bush_cane() {
        return table("tfc:plant/blackberry_bush_cane",
                item("minecraft:stick", List.of(), List.of()));
    }

    private static HarvestTable t_tfc_plant_blood_lily() {
        return table("tfc:plant/blood_lily",
                item("tfc:plant/blood_lily", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_blue_ginger() {
        return table("tfc:plant/blue_ginger",
                item("tfc:plant/blue_ginger", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_blue_orchid() {
        return table("tfc:plant/blue_orchid",
                item("tfc:plant/blue_orchid", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_blueberry_bush() {
        return table("tfc:plant/blueberry_bush",
                alt(List.of(), item("tfc:plant/blueberry_bush", when(tool(ToolTag.SHARP), state(Map.of("stage", "2"))), List.of()), item("tfc:plant/blueberry_bush", when(tool(ToolTag.SHARP), chance(0.5)), List.of())),
                item("minecraft:stick", List.of(), List.of()));
    }

    private static HarvestTable t_tfc_plant_blueberry_bush_cane() {
        return table("tfc:plant/blueberry_bush_cane",
                item("minecraft:stick", List.of(), List.of()));
    }

    private static HarvestTable t_tfc_plant_bluegrass() {
        return table("tfc:plant/bluegrass",
                alt(List.of(), item("tfc:plant/bluegrass", when(tool(ToolTag.SHEAR)), List.of()), item("tfc:straw", when(tool(ToolTag.SHARP)), List.of())));
    }

    private static HarvestTable t_tfc_plant_bromegrass() {
        return table("tfc:plant/bromegrass",
                alt(List.of(), item("tfc:plant/bromegrass", when(tool(ToolTag.SHEAR)), List.of()), item("tfc:straw", when(tool(ToolTag.SHARP)), List.of())));
    }

    private static HarvestTable t_tfc_plant_bunchberry_bush() {
        return table("tfc:plant/bunchberry_bush",
                item("tfc:plant/bunchberry_bush", when(tool(ToolTag.SHARP)), List.of()));
    }

    private static HarvestTable t_tfc_plant_bur_reed() {
        return table("tfc:plant/bur_reed",
                item("tfc:plant/bur_reed", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_buttercup() {
        return table("tfc:plant/buttercup",
                item("tfc:plant/buttercup", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of(setCount(when(state(Map.of("age", "1"))), 2, 2), setCount(when(state(Map.of("age", "2"))), 3, 3), setCount(when(state(Map.of("age", "3"))), 4, 4))));
    }

    private static HarvestTable t_tfc_plant_butterfly_milkweed() {
        return table("tfc:plant/butterfly_milkweed",
                item("tfc:plant/butterfly_milkweed", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_calendula() {
        return table("tfc:plant/calendula",
                item("tfc:plant/calendula", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_canna() {
        return table("tfc:plant/canna",
                item("tfc:plant/canna", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_cattail() {
        return table("tfc:plant/cattail",
                alt(List.of(), item("tfc:food/cattail_root", when(tool(ToolTag.SHARP), chance(0.3), state(Map.of("part", "lower"))), List.of()), item("tfc:plant/cattail", when(tool(ToolTag.SHEAR), state(Map.of("part", "lower"))), List.of())));
    }

    private static HarvestTable t_tfc_plant_cherry_branch() {
        return table("tfc:plant/cherry_branch",
                item("tfc:plant/cherry_sapling", when(allOf(anyOf(state(Map.of("up", "true", "west", "true")), state(Map.of("east", "true", "up", "true")), state(Map.of("north", "true", "up", "true")), state(Map.of("south", "true", "up", "true"))), tool(ToolTag.AXE))), List.of()),
                item("minecraft:stick", List.of(), List.of(setCount(List.of(), 1, 4))));
    }

    private static HarvestTable t_tfc_plant_cherry_growing_branch() {
        return table("tfc:plant/cherry_growing_branch",
                item("minecraft:stick", List.of(), List.of(setCount(List.of(), 1, 4))));
    }

    private static HarvestTable t_tfc_plant_cherry_leaves() {
        return table("tfc:plant/cherry_leaves",
                item("tfc:food/cherry", when(state(Map.of("lifecycle", "fruiting"))), List.of()),
                item("tfc:plant/cherry_leaves", when(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH))), List.of()),
                alt(when(not(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH)))), item("minecraft:stick", when(tool(ToolTag.SHARP), chance(0.2)), List.of(setCount(List.of(), 1, 2))), item("minecraft:stick", when(chance(0.05)), List.of(setCount(List.of(), 1, 2)))));
    }

    private static HarvestTable t_tfc_plant_cherry_sapling() {
        return table("tfc:plant/cherry_sapling",
                item("tfc:plant/cherry_sapling", List.of(), List.of(setCount(when(state(Map.of("saplings", "1"))), 1, 1), setCount(when(state(Map.of("saplings", "2"))), 2, 2), setCount(when(state(Map.of("saplings", "3"))), 3, 3), setCount(when(state(Map.of("saplings", "4"))), 4, 4))));
    }

    private static HarvestTable t_tfc_plant_cloudberry_bush() {
        return table("tfc:plant/cloudberry_bush",
                item("tfc:plant/cloudberry_bush", when(tool(ToolTag.SHARP)), List.of()));
    }

    private static HarvestTable t_tfc_plant_cobblestone_lichen() {
        return table("tfc:plant/cobblestone_lichen",
                item("tfc:plant/cobblestone_lichen", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_coontail() {
        return table("tfc:plant/coontail",
                item("tfc:plant/coontail", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_cordgrass() {
        return table("tfc:plant/cordgrass",
                item("tfc:plant/cordgrass", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_cornflower() {
        return table("tfc:plant/cornflower",
                item("tfc:plant/cornflower", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_cranberry_bush() {
        return table("tfc:plant/cranberry_bush",
                item("tfc:plant/cranberry_bush", when(tool(ToolTag.SHARP)), List.of()));
    }

    private static HarvestTable t_tfc_plant_cycad() {
        return table("tfc:plant/cycad",
                item("tfc:plant/cycad", when(tool(ToolTag.SHARP)), List.of()));
    }

    private static HarvestTable t_tfc_plant_dandelion() {
        return table("tfc:plant/dandelion",
                item("tfc:plant/dandelion", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_dead_banana_plant() {
        return table("tfc:plant/dead_banana_plant",
                item("minecraft:stick", List.of(), List.of()),
                item("tfc:plant/banana_sapling", when(state(Map.of("stage", "2"))), List.of(setCount(List.of(), 1, 2))));
    }

    private static HarvestTable t_tfc_plant_dead_berry_bush() {
        return table("tfc:plant/dead_berry_bush",
                item("minecraft:stick", List.of(), List.of()));
    }

    private static HarvestTable t_tfc_plant_dead_bush() {
        return table("tfc:plant/dead_bush",
                item("tfc:plant/dead_bush", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_dead_cane() {
        return table("tfc:plant/dead_cane",
                item("minecraft:stick", List.of(), List.of()));
    }

    private static HarvestTable t_tfc_plant_desert_flame() {
        return table("tfc:plant/desert_flame",
                item("tfc:plant/desert_flame", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_douglas_fir_krummholz() {
        return table("tfc:plant/douglas_fir_krummholz",
                alt(List.of(), item("tfc:plant/douglas_fir_krummholz", when(state(Map.of("tip", "true")), tool(ToolTag.AXE)), List.of()), item("tfc:wood/sapling/douglas_fir", when(chance(0.02)), List.of()), item("minecraft:stick", List.of(), List.of(setCount(List.of(), 1, 3)))));
    }

    private static HarvestTable t_tfc_plant_dry_grass() {
        return table("tfc:plant/dry_grass",
                item("tfc:plant/dry_grass", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_dry_phragmite() {
        return table("tfc:plant/dry_phragmite",
                item("tfc:plant/dry_phragmite", List.of(), List.of()));
    }

    private static HarvestTable t_tfc_plant_duckweed() {
        return table("tfc:plant/duckweed",
                item("tfc:plant/duckweed", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_edelweiss() {
        return table("tfc:plant/edelweiss",
                item("tfc:plant/edelweiss", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_eel_grass() {
        return table("tfc:plant/eel_grass",
                item("tfc:plant/eel_grass", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_elderberry_bush() {
        return table("tfc:plant/elderberry_bush",
                alt(List.of(), item("tfc:plant/elderberry_bush", when(tool(ToolTag.SHARP), state(Map.of("stage", "2"))), List.of()), item("tfc:plant/elderberry_bush", when(tool(ToolTag.SHARP), chance(0.5)), List.of())),
                item("minecraft:stick", List.of(), List.of()));
    }

    private static HarvestTable t_tfc_plant_elderberry_bush_cane() {
        return table("tfc:plant/elderberry_bush_cane",
                item("minecraft:stick", List.of(), List.of()));
    }

    private static HarvestTable t_tfc_plant_elegant_sunburst_lichen() {
        return table("tfc:plant/elegant_sunburst_lichen",
                item("tfc:plant/elegant_sunburst_lichen", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_fan_palm() {
        return table("tfc:plant/fan_palm",
                item("tfc:plant/fan_palm", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_field_horsetail() {
        return table("tfc:plant/field_horsetail",
                item("tfc:plant/field_horsetail", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_flame_vine() {
        return table("tfc:plant/flame_vine",
                item("tfc:plant/flame_vine", when(tool(ToolTag.SHARP)), List.of()));
    }

    private static HarvestTable t_tfc_plant_fountain_grass() {
        return table("tfc:plant/fountain_grass",
                alt(List.of(), item("tfc:plant/fountain_grass", when(tool(ToolTag.SHEAR)), List.of()), item("tfc:straw", when(tool(ToolTag.SHARP)), List.of())));
    }

    private static HarvestTable t_tfc_plant_foxglove() {
        return table("tfc:plant/foxglove",
                item("tfc:plant/foxglove", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_giant_kelp_flower() {
        return table("tfc:plant/giant_kelp_flower",
                item("tfc:plant/giant_kelp_flower", List.of(), List.of()));
    }

    private static HarvestTable t_tfc_plant_golden_bamboo() {
        return table("tfc:plant/golden_bamboo",
                item("tfc:plant/golden_bamboo", List.of(), List.of()));
    }

    private static HarvestTable t_tfc_plant_golden_bamboo_sapling() {
        return table("tfc:plant/golden_bamboo_sapling",
                item("tfc:plant/golden_bamboo", List.of(), List.of()));
    }

    private static HarvestTable t_tfc_plant_goldenrod() {
        return table("tfc:plant/goldenrod",
                item("tfc:plant/goldenrod", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_gooseberry_bush() {
        return table("tfc:plant/gooseberry_bush",
                item("tfc:plant/gooseberry_bush", when(tool(ToolTag.SHARP)), List.of()));
    }

    private static HarvestTable t_tfc_plant_grape_hyacinth() {
        return table("tfc:plant/grape_hyacinth",
                item("tfc:plant/grape_hyacinth", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_green_algae() {
        return table("tfc:plant/green_algae",
                item("tfc:plant/green_algae", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_green_apple_branch() {
        return table("tfc:plant/green_apple_branch",
                item("tfc:plant/green_apple_sapling", when(allOf(anyOf(state(Map.of("up", "true", "west", "true")), state(Map.of("east", "true", "up", "true")), state(Map.of("north", "true", "up", "true")), state(Map.of("south", "true", "up", "true"))), tool(ToolTag.AXE))), List.of()),
                item("minecraft:stick", List.of(), List.of(setCount(List.of(), 1, 4))));
    }

    private static HarvestTable t_tfc_plant_green_apple_growing_branch() {
        return table("tfc:plant/green_apple_growing_branch",
                item("minecraft:stick", List.of(), List.of(setCount(List.of(), 1, 4))));
    }

    private static HarvestTable t_tfc_plant_green_apple_leaves() {
        return table("tfc:plant/green_apple_leaves",
                item("tfc:food/green_apple", when(state(Map.of("lifecycle", "fruiting"))), List.of()),
                item("tfc:plant/green_apple_leaves", when(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH))), List.of()),
                alt(when(not(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH)))), item("minecraft:stick", when(tool(ToolTag.SHARP), chance(0.2)), List.of(setCount(List.of(), 1, 2))), item("minecraft:stick", when(chance(0.05)), List.of(setCount(List.of(), 1, 2)))));
    }

    private static HarvestTable t_tfc_plant_green_apple_sapling() {
        return table("tfc:plant/green_apple_sapling",
                item("tfc:plant/green_apple_sapling", List.of(), List.of(setCount(when(state(Map.of("saplings", "1"))), 1, 1), setCount(when(state(Map.of("saplings", "2"))), 2, 2), setCount(when(state(Map.of("saplings", "3"))), 3, 3), setCount(when(state(Map.of("saplings", "4"))), 4, 4))));
    }

    private static HarvestTable t_tfc_plant_gutweed() {
        return table("tfc:plant/gutweed",
                alt(List.of(), item("tfc:food/fresh_seaweed", when(tool(ToolTag.SHARP), chance(0.3)), List.of()), item("tfc:plant/gutweed", when(tool(ToolTag.SHEAR)), List.of())));
    }

    private static HarvestTable t_tfc_plant_guzmania() {
        return table("tfc:plant/guzmania",
                item("tfc:plant/guzmania", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_hanging_vines() {
        return table("tfc:plant/hanging_vines",
                item("tfc:plant/hanging_vines", when(tool(ToolTag.SHARP)), List.of()));
    }

    private static HarvestTable t_tfc_plant_heather() {
        return table("tfc:plant/heather",
                item("tfc:plant/heather", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_heliconia() {
        return table("tfc:plant/heliconia",
                item("tfc:plant/heliconia", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_hibiscus() {
        return table("tfc:plant/hibiscus",
                item("tfc:plant/hibiscus", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_houstonia() {
        return table("tfc:plant/houstonia",
                item("tfc:plant/houstonia", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_ivy() {
        return table("tfc:plant/ivy",
                item("tfc:plant/ivy", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_jungle_vines() {
        return table("tfc:plant/jungle_vines",
                item("tfc:plant/jungle_vines", when(tool(ToolTag.SHARP)), List.of()));
    }

    private static HarvestTable t_tfc_plant_kangaroo_paw() {
        return table("tfc:plant/kangaroo_paw",
                item("tfc:plant/kangaroo_paw", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_king_fern() {
        return table("tfc:plant/king_fern",
                item("tfc:plant/king_fern", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_kinnikinnick() {
        return table("tfc:plant/kinnikinnick",
                item("tfc:plant/kinnikinnick", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_labrador_tea() {
        return table("tfc:plant/labrador_tea",
                item("tfc:plant/labrador_tea", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_lady_fern() {
        return table("tfc:plant/lady_fern",
                item("tfc:plant/lady_fern", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_laminaria() {
        return table("tfc:plant/laminaria",
                alt(List.of(), item("tfc:food/fresh_seaweed", when(tool(ToolTag.SHARP), chance(0.3)), List.of()), item("tfc:plant/laminaria", when(tool(ToolTag.SHEAR)), List.of())));
    }

    private static HarvestTable t_tfc_plant_leafy_kelp() {
        return table("tfc:plant/leafy_kelp",
                item("tfc:plant/leafy_kelp", List.of(), List.of()));
    }

    private static HarvestTable t_tfc_plant_lemon_branch() {
        return table("tfc:plant/lemon_branch",
                item("tfc:plant/lemon_sapling", when(allOf(anyOf(state(Map.of("up", "true", "west", "true")), state(Map.of("east", "true", "up", "true")), state(Map.of("north", "true", "up", "true")), state(Map.of("south", "true", "up", "true"))), tool(ToolTag.AXE))), List.of()),
                item("minecraft:stick", List.of(), List.of(setCount(List.of(), 1, 4))));
    }

    private static HarvestTable t_tfc_plant_lemon_growing_branch() {
        return table("tfc:plant/lemon_growing_branch",
                item("minecraft:stick", List.of(), List.of(setCount(List.of(), 1, 4))));
    }

    private static HarvestTable t_tfc_plant_lemon_leaves() {
        return table("tfc:plant/lemon_leaves",
                item("tfc:food/lemon", when(state(Map.of("lifecycle", "fruiting"))), List.of()),
                item("tfc:plant/lemon_leaves", when(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH))), List.of()),
                alt(when(not(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH)))), item("minecraft:stick", when(tool(ToolTag.SHARP), chance(0.2)), List.of(setCount(List.of(), 1, 2))), item("minecraft:stick", when(chance(0.05)), List.of(setCount(List.of(), 1, 2)))));
    }

    private static HarvestTable t_tfc_plant_lemon_sapling() {
        return table("tfc:plant/lemon_sapling",
                item("tfc:plant/lemon_sapling", List.of(), List.of(setCount(when(state(Map.of("saplings", "1"))), 1, 1), setCount(when(state(Map.of("saplings", "2"))), 2, 2), setCount(when(state(Map.of("saplings", "3"))), 3, 3), setCount(when(state(Map.of("saplings", "4"))), 4, 4))));
    }

    private static HarvestTable t_tfc_plant_liana() {
        return table("tfc:plant/liana",
                item("tfc:plant/liana", when(tool(ToolTag.SHARP)), List.of()));
    }

    private static HarvestTable t_tfc_plant_licorice_fern() {
        return table("tfc:plant/licorice_fern",
                item("tfc:plant/licorice_fern", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_lilac() {
        return table("tfc:plant/lilac",
                item("tfc:plant/lilac", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_lily_of_the_valley() {
        return table("tfc:plant/lily_of_the_valley",
                item("tfc:plant/lily_of_the_valley", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_lotus() {
        return table("tfc:plant/lotus",
                item("tfc:plant/lotus", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_maiden_pink() {
        return table("tfc:plant/maiden_pink",
                item("tfc:plant/maiden_pink", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of(setCount(when(state(Map.of("age", "1"))), 2, 2), setCount(when(state(Map.of("age", "2"))), 3, 3), setCount(when(state(Map.of("age", "3"))), 4, 4))));
    }

    private static HarvestTable t_tfc_plant_manatee_grass() {
        return table("tfc:plant/manatee_grass",
                item("tfc:plant/manatee_grass", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_marigold() {
        return table("tfc:plant/marigold",
                item("tfc:plant/marigold", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_meads_milkweed() {
        return table("tfc:plant/meads_milkweed",
                item("tfc:plant/meads_milkweed", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_milfoil() {
        return table("tfc:plant/milfoil",
                alt(List.of(), item("tfc:food/fresh_seaweed", when(tool(ToolTag.SHARP), chance(0.3)), List.of()), item("tfc:plant/milfoil", when(tool(ToolTag.SHEAR)), List.of())));
    }

    private static HarvestTable t_tfc_plant_morning_glory() {
        return table("tfc:plant/morning_glory",
                item("tfc:plant/morning_glory", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_moss() {
        return table("tfc:plant/moss",
                item("tfc:plant/moss", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_moss_campion() {
        return table("tfc:plant/moss_campion",
                item("tfc:plant/moss_campion", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_mountain_hullwort() {
        return table("tfc:plant/mountain_hullwort",
                item("tfc:plant/mountain_hullwort", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_mussels() {
        return table("tfc:plant/mussels",
                item("tfc:plant/mussels", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_nasturtium() {
        return table("tfc:plant/nasturtium",
                item("tfc:plant/nasturtium", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_olive_branch() {
        return table("tfc:plant/olive_branch",
                item("tfc:plant/olive_sapling", when(allOf(anyOf(state(Map.of("up", "true", "west", "true")), state(Map.of("east", "true", "up", "true")), state(Map.of("north", "true", "up", "true")), state(Map.of("south", "true", "up", "true"))), tool(ToolTag.AXE))), List.of()),
                item("minecraft:stick", List.of(), List.of(setCount(List.of(), 1, 4))));
    }

    private static HarvestTable t_tfc_plant_olive_growing_branch() {
        return table("tfc:plant/olive_growing_branch",
                item("minecraft:stick", List.of(), List.of(setCount(List.of(), 1, 4))));
    }

    private static HarvestTable t_tfc_plant_olive_leaves() {
        return table("tfc:plant/olive_leaves",
                item("tfc:food/olive", when(state(Map.of("lifecycle", "fruiting"))), List.of()),
                item("tfc:plant/olive_leaves", when(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH))), List.of()),
                alt(when(not(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH)))), item("minecraft:stick", when(tool(ToolTag.SHARP), chance(0.2)), List.of(setCount(List.of(), 1, 2))), item("minecraft:stick", when(chance(0.05)), List.of(setCount(List.of(), 1, 2)))));
    }

    private static HarvestTable t_tfc_plant_olive_sapling() {
        return table("tfc:plant/olive_sapling",
                item("tfc:plant/olive_sapling", List.of(), List.of(setCount(when(state(Map.of("saplings", "1"))), 1, 1), setCount(when(state(Map.of("saplings", "2"))), 2, 2), setCount(when(state(Map.of("saplings", "3"))), 3, 3), setCount(when(state(Map.of("saplings", "4"))), 4, 4))));
    }

    private static HarvestTable t_tfc_plant_orange_branch() {
        return table("tfc:plant/orange_branch",
                item("tfc:plant/orange_sapling", when(allOf(anyOf(state(Map.of("up", "true", "west", "true")), state(Map.of("east", "true", "up", "true")), state(Map.of("north", "true", "up", "true")), state(Map.of("south", "true", "up", "true"))), tool(ToolTag.AXE))), List.of()),
                item("minecraft:stick", List.of(), List.of(setCount(List.of(), 1, 4))));
    }

    private static HarvestTable t_tfc_plant_orange_growing_branch() {
        return table("tfc:plant/orange_growing_branch",
                item("minecraft:stick", List.of(), List.of(setCount(List.of(), 1, 4))));
    }

    private static HarvestTable t_tfc_plant_orange_leaves() {
        return table("tfc:plant/orange_leaves",
                item("tfc:food/orange", when(state(Map.of("lifecycle", "fruiting"))), List.of()),
                item("tfc:plant/orange_leaves", when(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH))), List.of()),
                alt(when(not(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH)))), item("minecraft:stick", when(tool(ToolTag.SHARP), chance(0.2)), List.of(setCount(List.of(), 1, 2))), item("minecraft:stick", when(chance(0.05)), List.of(setCount(List.of(), 1, 2)))));
    }

    private static HarvestTable t_tfc_plant_orange_sapling() {
        return table("tfc:plant/orange_sapling",
                item("tfc:plant/orange_sapling", List.of(), List.of(setCount(when(state(Map.of("saplings", "1"))), 1, 1), setCount(when(state(Map.of("saplings", "2"))), 2, 2), setCount(when(state(Map.of("saplings", "3"))), 3, 3), setCount(when(state(Map.of("saplings", "4"))), 4, 4))));
    }

    private static HarvestTable t_tfc_plant_orchard_grass() {
        return table("tfc:plant/orchard_grass",
                alt(List.of(), item("tfc:plant/orchard_grass", when(tool(ToolTag.SHEAR)), List.of()), item("tfc:straw", when(tool(ToolTag.SHARP)), List.of())));
    }

    private static HarvestTable t_tfc_plant_ostrich_fern() {
        return table("tfc:plant/ostrich_fern",
                item("tfc:plant/ostrich_fern", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_oxeye_daisy() {
        return table("tfc:plant/oxeye_daisy",
                item("tfc:plant/oxeye_daisy", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_palash() {
        return table("tfc:plant/palash",
                item("tfc:plant/palash", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_pampas_grass() {
        return table("tfc:plant/pampas_grass",
                alt(List.of(), item("tfc:plant/pampas_grass", when(tool(ToolTag.SHEAR)), List.of()), item("tfc:straw", when(tool(ToolTag.SHARP)), List.of())));
    }

    private static HarvestTable t_tfc_plant_peach_branch() {
        return table("tfc:plant/peach_branch",
                item("tfc:plant/peach_sapling", when(allOf(anyOf(state(Map.of("up", "true", "west", "true")), state(Map.of("east", "true", "up", "true")), state(Map.of("north", "true", "up", "true")), state(Map.of("south", "true", "up", "true"))), tool(ToolTag.AXE))), List.of()),
                item("minecraft:stick", List.of(), List.of(setCount(List.of(), 1, 4))));
    }

    private static HarvestTable t_tfc_plant_peach_growing_branch() {
        return table("tfc:plant/peach_growing_branch",
                item("minecraft:stick", List.of(), List.of(setCount(List.of(), 1, 4))));
    }

    private static HarvestTable t_tfc_plant_peach_leaves() {
        return table("tfc:plant/peach_leaves",
                item("tfc:food/peach", when(state(Map.of("lifecycle", "fruiting"))), List.of()),
                item("tfc:plant/peach_leaves", when(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH))), List.of()),
                alt(when(not(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH)))), item("minecraft:stick", when(tool(ToolTag.SHARP), chance(0.2)), List.of(setCount(List.of(), 1, 2))), item("minecraft:stick", when(chance(0.05)), List.of(setCount(List.of(), 1, 2)))));
    }

    private static HarvestTable t_tfc_plant_peach_sapling() {
        return table("tfc:plant/peach_sapling",
                item("tfc:plant/peach_sapling", List.of(), List.of(setCount(when(state(Map.of("saplings", "1"))), 1, 1), setCount(when(state(Map.of("saplings", "2"))), 2, 2), setCount(when(state(Map.of("saplings", "3"))), 3, 3), setCount(when(state(Map.of("saplings", "4"))), 4, 4))));
    }

    private static HarvestTable t_tfc_plant_penwortel() {
        return table("tfc:plant/penwortel",
                item("tfc:plant/penwortel", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_perovskia() {
        return table("tfc:plant/perovskia",
                item("tfc:plant/perovskia", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_philodendron() {
        return table("tfc:plant/philodendron",
                item("tfc:plant/philodendron", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_phragmite() {
        return table("tfc:plant/phragmite",
                item("tfc:plant/phragmite", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_pickerelweed() {
        return table("tfc:plant/pickerelweed",
                item("tfc:plant/pickerelweed", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_pine_krummholz() {
        return table("tfc:plant/pine_krummholz",
                alt(List.of(), item("tfc:plant/pine_krummholz", when(state(Map.of("tip", "true")), tool(ToolTag.AXE)), List.of()), item("tfc:wood/sapling/pine", when(chance(0.02)), List.of()), item("minecraft:stick", List.of(), List.of(setCount(List.of(), 1, 3)))));
    }

    private static HarvestTable t_tfc_plant_pistia() {
        return table("tfc:plant/pistia",
                item("tfc:plant/pistia", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_plum_branch() {
        return table("tfc:plant/plum_branch",
                item("tfc:plant/plum_sapling", when(allOf(anyOf(state(Map.of("up", "true", "west", "true")), state(Map.of("east", "true", "up", "true")), state(Map.of("north", "true", "up", "true")), state(Map.of("south", "true", "up", "true"))), tool(ToolTag.AXE))), List.of()),
                item("minecraft:stick", List.of(), List.of(setCount(List.of(), 1, 4))));
    }

    private static HarvestTable t_tfc_plant_plum_growing_branch() {
        return table("tfc:plant/plum_growing_branch",
                item("minecraft:stick", List.of(), List.of(setCount(List.of(), 1, 4))));
    }

    private static HarvestTable t_tfc_plant_plum_leaves() {
        return table("tfc:plant/plum_leaves",
                item("tfc:food/plum", when(state(Map.of("lifecycle", "fruiting"))), List.of()),
                item("tfc:plant/plum_leaves", when(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH))), List.of()),
                alt(when(not(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH)))), item("minecraft:stick", when(tool(ToolTag.SHARP), chance(0.2)), List.of(setCount(List.of(), 1, 2))), item("minecraft:stick", when(chance(0.05)), List.of(setCount(List.of(), 1, 2)))));
    }

    private static HarvestTable t_tfc_plant_plum_sapling() {
        return table("tfc:plant/plum_sapling",
                item("tfc:plant/plum_sapling", List.of(), List.of(setCount(when(state(Map.of("saplings", "1"))), 1, 1), setCount(when(state(Map.of("saplings", "2"))), 2, 2), setCount(when(state(Map.of("saplings", "3"))), 3, 3), setCount(when(state(Map.of("saplings", "4"))), 4, 4))));
    }

    private static HarvestTable t_tfc_plant_poppy() {
        return table("tfc:plant/poppy",
                item("tfc:plant/poppy", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_prickly_pear() {
        return table("tfc:plant/prickly_pear",
                item("tfc:plant/prickly_pear", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_prickly_pear_purple() {
        return table("tfc:plant/prickly_pear_purple",
                item("tfc:plant/prickly_pear_purple", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_primrose() {
        return table("tfc:plant/primrose",
                item("tfc:plant/primrose", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_pulsatilla() {
        return table("tfc:plant/pulsatilla",
                item("tfc:plant/pulsatilla", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_purple_water_lily() {
        return table("tfc:plant/purple_water_lily",
                item("tfc:plant/purple_water_lily", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_qantu() {
        return table("tfc:plant/qantu",
                item("tfc:plant/qantu", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_raddia_grass() {
        return table("tfc:plant/raddia_grass",
                alt(List.of(), item("tfc:plant/raddia_grass", when(tool(ToolTag.SHEAR)), List.of()), item("tfc:straw", when(tool(ToolTag.SHARP)), List.of())));
    }

    private static HarvestTable t_tfc_plant_ramirezella() {
        return table("tfc:plant/ramirezella",
                item("tfc:plant/ramirezella", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_ramunda() {
        return table("tfc:plant/ramunda",
                item("tfc:plant/ramunda", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_raspberry_bush() {
        return table("tfc:plant/raspberry_bush",
                alt(List.of(), item("tfc:plant/raspberry_bush", when(tool(ToolTag.SHARP), state(Map.of("stage", "2"))), List.of()), item("tfc:plant/raspberry_bush", when(tool(ToolTag.SHARP), chance(0.5)), List.of())),
                item("minecraft:stick", List.of(), List.of()));
    }

    private static HarvestTable t_tfc_plant_raspberry_bush_cane() {
        return table("tfc:plant/raspberry_bush_cane",
                item("minecraft:stick", List.of(), List.of()));
    }

    private static HarvestTable t_tfc_plant_red_algae() {
        return table("tfc:plant/red_algae",
                item("tfc:plant/red_algae", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_red_apple_branch() {
        return table("tfc:plant/red_apple_branch",
                item("tfc:plant/red_apple_sapling", when(allOf(anyOf(state(Map.of("up", "true", "west", "true")), state(Map.of("east", "true", "up", "true")), state(Map.of("north", "true", "up", "true")), state(Map.of("south", "true", "up", "true"))), tool(ToolTag.AXE))), List.of()),
                item("minecraft:stick", List.of(), List.of(setCount(List.of(), 1, 4))));
    }

    private static HarvestTable t_tfc_plant_red_apple_growing_branch() {
        return table("tfc:plant/red_apple_growing_branch",
                item("minecraft:stick", List.of(), List.of(setCount(List.of(), 1, 4))));
    }

    private static HarvestTable t_tfc_plant_red_apple_leaves() {
        return table("tfc:plant/red_apple_leaves",
                item("tfc:food/red_apple", when(state(Map.of("lifecycle", "fruiting"))), List.of()),
                item("tfc:plant/red_apple_leaves", when(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH))), List.of()),
                alt(when(not(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH)))), item("minecraft:stick", when(tool(ToolTag.SHARP), chance(0.2)), List.of(setCount(List.of(), 1, 2))), item("minecraft:stick", when(chance(0.05)), List.of(setCount(List.of(), 1, 2)))));
    }

    private static HarvestTable t_tfc_plant_red_apple_sapling() {
        return table("tfc:plant/red_apple_sapling",
                item("tfc:plant/red_apple_sapling", List.of(), List.of(setCount(when(state(Map.of("saplings", "1"))), 1, 1), setCount(when(state(Map.of("saplings", "2"))), 2, 2), setCount(when(state(Map.of("saplings", "3"))), 3, 3), setCount(when(state(Map.of("saplings", "4"))), 4, 4))));
    }

    private static HarvestTable t_tfc_plant_red_oat_grass() {
        return table("tfc:plant/red_oat_grass",
                alt(List.of(), item("tfc:plant/red_oat_grass", when(tool(ToolTag.SHEAR)), List.of()), item("tfc:straw", when(tool(ToolTag.SHARP)), List.of())));
    }

    private static HarvestTable t_tfc_plant_red_sealing_wax_palm() {
        return table("tfc:plant/red_sealing_wax_palm",
                item("tfc:plant/red_sealing_wax_palm", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_reindeer_lichen() {
        return table("tfc:plant/reindeer_lichen",
                item("tfc:plant/reindeer_lichen", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_rose() {
        return table("tfc:plant/rose",
                item("tfc:plant/rose", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_ryegrass() {
        return table("tfc:plant/ryegrass",
                alt(List.of(), item("tfc:plant/ryegrass", when(tool(ToolTag.SHEAR)), List.of()), item("tfc:straw", when(tool(ToolTag.SHARP)), List.of())));
    }

    private static HarvestTable t_tfc_plant_sacred_datura() {
        return table("tfc:plant/sacred_datura",
                item("tfc:plant/sacred_datura", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_sagebrush() {
        return table("tfc:plant/sagebrush",
                item("tfc:plant/sagebrush", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_sago() {
        return table("tfc:plant/sago",
                alt(List.of(), item("tfc:food/fresh_seaweed", when(tool(ToolTag.SHARP), chance(0.3)), List.of()), item("tfc:plant/sago", when(tool(ToolTag.SHEAR)), List.of())));
    }

    private static HarvestTable t_tfc_plant_saguaro() {
        return table("tfc:plant/saguaro",
                item("tfc:cactus_wood", List.of(), List.of()));
    }

    private static HarvestTable t_tfc_plant_saguaro_fruit() {
        return table("tfc:plant/saguaro_fruit",
                item("tfc:plant/saguaro_fruit", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_saguaro_plant() {
        return table("tfc:plant/saguaro_plant",
                item("tfc:cactus_wood", List.of(), List.of()));
    }

    private static HarvestTable t_tfc_plant_sapphire_tower() {
        return table("tfc:plant/sapphire_tower",
                item("tfc:plant/sapphire_tower", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_sargassum() {
        return table("tfc:plant/sargassum",
                item("tfc:plant/sargassum", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_scutch_grass() {
        return table("tfc:plant/scutch_grass",
                alt(List.of(), item("tfc:plant/scutch_grass", when(tool(ToolTag.SHEAR)), List.of()), item("tfc:straw", when(tool(ToolTag.SHARP)), List.of())));
    }

    private static HarvestTable t_tfc_plant_sea_lavender() {
        return table("tfc:plant/sea_lavender",
                item("tfc:plant/sea_lavender", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_sea_palm() {
        return table("tfc:plant/sea_palm",
                item("tfc:plant/sea_palm", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_shawiash() {
        return table("tfc:plant/shawiash",
                item("tfc:plant/shawiash", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_silken_pincushion_cactus() {
        return table("tfc:plant/silken_pincushion_cactus",
                item("tfc:plant/silken_pincushion_cactus", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of(setCount(when(state(Map.of("age", "1"))), 2, 2), setCount(when(state(Map.of("age", "2"))), 3, 3), setCount(when(state(Map.of("age", "3"))), 4, 4))));
    }

    private static HarvestTable t_tfc_plant_silver_bromeliad() {
        return table("tfc:plant/silver_bromeliad",
                item("tfc:plant/silver_bromeliad", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_silver_spurflower() {
        return table("tfc:plant/silver_spurflower",
                item("tfc:plant/silver_spurflower", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_snapdragon_pink() {
        return table("tfc:plant/snapdragon_pink",
                item("tfc:plant/snapdragon_pink", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_snapdragon_red() {
        return table("tfc:plant/snapdragon_red",
                item("tfc:plant/snapdragon_red", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_snapdragon_white() {
        return table("tfc:plant/snapdragon_white",
                item("tfc:plant/snapdragon_white", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_snapdragon_yellow() {
        return table("tfc:plant/snapdragon_yellow",
                item("tfc:plant/snapdragon_yellow", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_snowberry_bush() {
        return table("tfc:plant/snowberry_bush",
                item("tfc:plant/snowberry_bush", when(tool(ToolTag.SHARP)), List.of()));
    }

    private static HarvestTable t_tfc_plant_spanish_moss() {
        return table("tfc:plant/spanish_moss",
                item("tfc:plant/spanish_moss", when(tool(ToolTag.SHARP)), List.of()));
    }

    private static HarvestTable t_tfc_plant_spruce_krummholz() {
        return table("tfc:plant/spruce_krummholz",
                alt(List.of(), item("tfc:plant/spruce_krummholz", when(state(Map.of("tip", "true")), tool(ToolTag.AXE)), List.of()), item("tfc:wood/sapling/spruce", when(chance(0.02)), List.of()), item("minecraft:stick", List.of(), List.of(setCount(List.of(), 1, 3)))));
    }

    private static HarvestTable t_tfc_plant_star_grass() {
        return table("tfc:plant/star_grass",
                item("tfc:plant/star_grass", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_starfish() {
        return table("tfc:plant/starfish",
                item("tfc:plant/starfish", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_strawberry_bush() {
        return table("tfc:plant/strawberry_bush",
                item("tfc:plant/strawberry_bush", when(tool(ToolTag.SHARP)), List.of()));
    }

    private static HarvestTable t_tfc_plant_strelitzia() {
        return table("tfc:plant/strelitzia",
                item("tfc:plant/strelitzia", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_sunflower() {
        return table("tfc:plant/sunflower",
                item("tfc:plant/sunflower", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_switchgrass() {
        return table("tfc:plant/switchgrass",
                alt(List.of(), item("tfc:plant/switchgrass", when(tool(ToolTag.SHEAR)), List.of()), item("tfc:straw", when(tool(ToolTag.SHARP)), List.of())));
    }

    private static HarvestTable t_tfc_plant_sword_fern() {
        return table("tfc:plant/sword_fern",
                item("tfc:plant/sword_fern", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_tall_fescue_grass() {
        return table("tfc:plant/tall_fescue_grass",
                alt(List.of(), item("tfc:plant/tall_fescue_grass", when(tool(ToolTag.SHEAR)), List.of()), item("tfc:straw", when(tool(ToolTag.SHARP)), List.of())));
    }

    private static HarvestTable t_tfc_plant_tank_bromeliad() {
        return table("tfc:plant/tank_bromeliad",
                item("tfc:plant/tank_bromeliad", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_timothy_grass() {
        return table("tfc:plant/timothy_grass",
                alt(List.of(), item("tfc:plant/timothy_grass", when(tool(ToolTag.SHEAR)), List.of()), item("tfc:straw", when(tool(ToolTag.SHARP)), List.of())));
    }

    private static HarvestTable t_tfc_plant_toquilla_palm() {
        return table("tfc:plant/toquilla_palm",
                item("tfc:plant/toquilla_palm", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_tree_fern() {
        return table("tfc:plant/tree_fern",
                item("tfc:plant/tree_fern", List.of(), List.of()));
    }

    private static HarvestTable t_tfc_plant_trillium() {
        return table("tfc:plant/trillium",
                item("tfc:plant/trillium", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_tropical_milkweed() {
        return table("tfc:plant/tropical_milkweed",
                item("tfc:plant/tropical_milkweed", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_tulip_orange() {
        return table("tfc:plant/tulip_orange",
                item("tfc:plant/tulip_orange", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_tulip_pink() {
        return table("tfc:plant/tulip_pink",
                item("tfc:plant/tulip_pink", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_tulip_red() {
        return table("tfc:plant/tulip_red",
                item("tfc:plant/tulip_red", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_tulip_white() {
        return table("tfc:plant/tulip_white",
                item("tfc:plant/tulip_white", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_turtle_grass() {
        return table("tfc:plant/turtle_grass",
                item("tfc:plant/turtle_grass", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_vriesea() {
        return table("tfc:plant/vriesea",
                item("tfc:plant/vriesea", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_water_canna() {
        return table("tfc:plant/water_canna",
                item("tfc:plant/water_canna", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_water_taro() {
        return table("tfc:plant/water_taro",
                alt(List.of(), item("tfc:food/taro_root", when(tool(ToolTag.SHARP), chance(0.3), state(Map.of("part", "lower"))), List.of()), item("tfc:plant/water_taro", when(tool(ToolTag.SHEAR), state(Map.of("part", "lower"))), List.of())));
    }

    private static HarvestTable t_tfc_plant_white_cedar_krummholz() {
        return table("tfc:plant/white_cedar_krummholz",
                alt(List.of(), item("tfc:plant/white_cedar_krummholz", when(state(Map.of("tip", "true")), tool(ToolTag.AXE)), List.of()), item("tfc:wood/sapling/white_cedar", when(chance(0.02)), List.of()), item("minecraft:stick", List.of(), List.of(setCount(List.of(), 1, 3)))));
    }

    private static HarvestTable t_tfc_plant_white_water_lily() {
        return table("tfc:plant/white_water_lily",
                item("tfc:plant/white_water_lily", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_winged_kelp() {
        return table("tfc:plant/winged_kelp",
                item("tfc:plant/winged_kelp", List.of(), List.of()));
    }

    private static HarvestTable t_tfc_plant_wintergreen_berry_bush() {
        return table("tfc:plant/wintergreen_berry_bush",
                item("tfc:plant/wintergreen_berry_bush", when(tool(ToolTag.SHARP)), List.of()));
    }

    private static HarvestTable t_tfc_plant_yellow_saxifrage() {
        return table("tfc:plant/yellow_saxifrage",
                item("tfc:plant/yellow_saxifrage", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of(setCount(when(state(Map.of("age", "1"))), 2, 2), setCount(when(state(Map.of("age", "2"))), 3, 3), setCount(when(state(Map.of("age", "3"))), 4, 4))));
    }

    private static HarvestTable t_tfc_plant_yellow_water_lily() {
        return table("tfc:plant/yellow_water_lily",
                item("tfc:plant/yellow_water_lily", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_plant_yucca() {
        return table("tfc:plant/yucca",
                item("tfc:plant/yucca", when(anyOf(tool(ToolTag.SHARP), tool(ToolTag.SHEAR))), List.of()));
    }

    private static HarvestTable t_tfc_wood_leaves_acacia() {
        return table("tfc:wood/leaves/acacia",
                item("tfc:wood/leaves/acacia", when(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH))), List.of()),
                item("tfc:wood/sapling/acacia", when(chance(0.0292)), List.of()),
                alt(when(not(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH)))), item("minecraft:stick", when(tool(ToolTag.SHARP), chance(0.2)), List.of(setCount(List.of(), 1, 2))), item("minecraft:stick", when(chance(0.05)), List.of(setCount(List.of(), 1, 2)))));
    }

    private static HarvestTable t_tfc_wood_leaves_ash() {
        return table("tfc:wood/leaves/ash",
                item("tfc:wood/leaves/ash", when(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH))), List.of()),
                item("tfc:wood/sapling/ash", when(chance(0.0428)), List.of()),
                alt(when(not(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH)))), item("minecraft:stick", when(tool(ToolTag.SHARP), chance(0.2)), List.of(setCount(List.of(), 1, 2))), item("minecraft:stick", when(chance(0.05)), List.of(setCount(List.of(), 1, 2)))));
    }

    private static HarvestTable t_tfc_wood_leaves_aspen() {
        return table("tfc:wood/leaves/aspen",
                item("tfc:wood/leaves/aspen", when(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH))), List.of()),
                item("tfc:wood/sapling/aspen", when(chance(0.0428)), List.of()),
                alt(when(not(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH)))), item("minecraft:stick", when(tool(ToolTag.SHARP), chance(0.2)), List.of(setCount(List.of(), 1, 2))), item("minecraft:stick", when(chance(0.05)), List.of(setCount(List.of(), 1, 2)))));
    }

    private static HarvestTable t_tfc_wood_leaves_birch() {
        return table("tfc:wood/leaves/birch",
                item("tfc:wood/leaves/birch", when(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH))), List.of()),
                item("tfc:wood/sapling/birch", when(chance(0.0311)), List.of()),
                alt(when(not(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH)))), item("minecraft:stick", when(tool(ToolTag.SHARP), chance(0.2)), List.of(setCount(List.of(), 1, 2))), item("minecraft:stick", when(chance(0.05)), List.of(setCount(List.of(), 1, 2)))));
    }

    private static HarvestTable t_tfc_wood_leaves_blackwood() {
        return table("tfc:wood/leaves/blackwood",
                item("tfc:wood/leaves/blackwood", when(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH))), List.of()),
                item("tfc:wood/sapling/blackwood", when(chance(0.078)), List.of()),
                alt(when(not(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH)))), item("minecraft:stick", when(tool(ToolTag.SHARP), chance(0.2)), List.of(setCount(List.of(), 1, 2))), item("minecraft:stick", when(chance(0.05)), List.of(setCount(List.of(), 1, 2)))));
    }

    private static HarvestTable t_tfc_wood_leaves_chestnut() {
        return table("tfc:wood/leaves/chestnut",
                item("tfc:wood/leaves/chestnut", when(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH))), List.of()),
                item("tfc:wood/sapling/chestnut", when(chance(0.0112)), List.of()),
                alt(when(not(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH)))), item("minecraft:stick", when(tool(ToolTag.SHARP), chance(0.2)), List.of(setCount(List.of(), 1, 2))), item("minecraft:stick", when(chance(0.05)), List.of(setCount(List.of(), 1, 2)))));
    }

    private static HarvestTable t_tfc_wood_leaves_douglas_fir() {
        return table("tfc:wood/leaves/douglas_fir",
                item("tfc:wood/leaves/douglas_fir", when(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH))), List.of()),
                item("tfc:wood/sapling/douglas_fir", when(chance(0.0132)), List.of()),
                alt(when(not(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH)))), item("minecraft:stick", when(tool(ToolTag.SHARP), chance(0.2)), List.of(setCount(List.of(), 1, 2))), item("minecraft:stick", when(chance(0.05)), List.of(setCount(List.of(), 1, 2)))));
    }

    private static HarvestTable t_tfc_wood_leaves_hickory() {
        return table("tfc:wood/leaves/hickory",
                item("tfc:wood/leaves/hickory", when(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH))), List.of()),
                item("tfc:wood/sapling/hickory", when(chance(0.014)), List.of()),
                alt(when(not(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH)))), item("minecraft:stick", when(tool(ToolTag.SHARP), chance(0.2)), List.of(setCount(List.of(), 1, 2))), item("minecraft:stick", when(chance(0.05)), List.of(setCount(List.of(), 1, 2)))));
    }

    private static HarvestTable t_tfc_wood_leaves_kapok() {
        return table("tfc:wood/leaves/kapok",
                item("tfc:wood/leaves/kapok", when(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH))), List.of()),
                item("tfc:wood/sapling/kapok", when(chance(0.0115)), List.of()),
                alt(when(not(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH)))), item("minecraft:stick", when(tool(ToolTag.SHARP), chance(0.2)), List.of(setCount(List.of(), 1, 2))), item("minecraft:stick", when(chance(0.05)), List.of(setCount(List.of(), 1, 2)))));
    }

    private static HarvestTable t_tfc_wood_leaves_mangrove() {
        return table("tfc:wood/leaves/mangrove",
                item("tfc:wood/leaves/mangrove", when(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH))), List.of()),
                item("tfc:wood/sapling/mangrove", when(chance(0.0447)), List.of()),
                alt(when(not(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH)))), item("minecraft:stick", when(tool(ToolTag.SHARP), chance(0.2)), List.of(setCount(List.of(), 1, 2))), item("minecraft:stick", when(chance(0.05)), List.of(setCount(List.of(), 1, 2)))));
    }

    private static HarvestTable t_tfc_wood_leaves_maple() {
        return table("tfc:wood/leaves/maple",
                item("tfc:wood/leaves/maple", when(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH))), List.of()),
                item("tfc:wood/sapling/maple", when(chance(0.0201)), List.of()),
                alt(when(not(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH)))), item("minecraft:stick", when(tool(ToolTag.SHARP), chance(0.2)), List.of(setCount(List.of(), 1, 2))), item("minecraft:stick", when(chance(0.05)), List.of(setCount(List.of(), 1, 2)))));
    }

    private static HarvestTable t_tfc_wood_leaves_oak() {
        return table("tfc:wood/leaves/oak",
                item("tfc:wood/leaves/oak", when(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH))), List.of()),
                item("tfc:wood/sapling/oak", when(chance(0.013)), List.of()),
                alt(when(not(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH)))), item("minecraft:stick", when(tool(ToolTag.SHARP), chance(0.2)), List.of(setCount(List.of(), 1, 2))), item("minecraft:stick", when(chance(0.05)), List.of(setCount(List.of(), 1, 2)))));
    }

    private static HarvestTable t_tfc_wood_leaves_palm() {
        return table("tfc:wood/leaves/palm",
                item("tfc:wood/leaves/palm", when(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH))), List.of()),
                item("tfc:wood/sapling/palm", when(chance(0.0911)), List.of()),
                alt(when(not(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH)))), item("minecraft:stick", when(tool(ToolTag.SHARP), chance(0.2)), List.of(setCount(List.of(), 1, 2))), item("minecraft:stick", when(chance(0.05)), List.of(setCount(List.of(), 1, 2)))));
    }

    private static HarvestTable t_tfc_wood_leaves_pine() {
        return table("tfc:wood/leaves/pine",
                item("tfc:wood/leaves/pine", when(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH))), List.of()),
                item("tfc:wood/sapling/pine", when(chance(0.0248)), List.of()),
                alt(when(not(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH)))), item("minecraft:stick", when(tool(ToolTag.SHARP), chance(0.2)), List.of(setCount(List.of(), 1, 2))), item("minecraft:stick", when(chance(0.05)), List.of(setCount(List.of(), 1, 2)))));
    }

    private static HarvestTable t_tfc_wood_leaves_rosewood() {
        return table("tfc:wood/leaves/rosewood",
                item("tfc:wood/leaves/rosewood", when(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH))), List.of()),
                item("tfc:wood/sapling/rosewood", when(chance(0.0193)), List.of()),
                alt(when(not(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH)))), item("minecraft:stick", when(tool(ToolTag.SHARP), chance(0.2)), List.of(setCount(List.of(), 1, 2))), item("minecraft:stick", when(chance(0.05)), List.of(setCount(List.of(), 1, 2)))));
    }

    private static HarvestTable t_tfc_wood_leaves_sequoia() {
        return table("tfc:wood/leaves/sequoia",
                item("tfc:wood/leaves/sequoia", when(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH))), List.of()),
                item("tfc:wood/sapling/sequoia", when(chance(0.0238)), List.of()),
                alt(when(not(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH)))), item("minecraft:stick", when(tool(ToolTag.SHARP), chance(0.2)), List.of(setCount(List.of(), 1, 2))), item("minecraft:stick", when(chance(0.05)), List.of(setCount(List.of(), 1, 2)))));
    }

    private static HarvestTable t_tfc_wood_leaves_spruce() {
        return table("tfc:wood/leaves/spruce",
                item("tfc:wood/leaves/spruce", when(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH))), List.of()),
                item("tfc:wood/sapling/spruce", when(chance(0.0318)), List.of()),
                alt(when(not(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH)))), item("minecraft:stick", when(tool(ToolTag.SHARP), chance(0.2)), List.of(setCount(List.of(), 1, 2))), item("minecraft:stick", when(chance(0.05)), List.of(setCount(List.of(), 1, 2)))));
    }

    private static HarvestTable t_tfc_wood_leaves_sycamore() {
        return table("tfc:wood/leaves/sycamore",
                item("tfc:wood/leaves/sycamore", when(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH))), List.of()),
                item("tfc:wood/sapling/sycamore", when(chance(0.0175)), List.of()),
                alt(when(not(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH)))), item("minecraft:stick", when(tool(ToolTag.SHARP), chance(0.2)), List.of(setCount(List.of(), 1, 2))), item("minecraft:stick", when(chance(0.05)), List.of(setCount(List.of(), 1, 2)))));
    }

    private static HarvestTable t_tfc_wood_leaves_white_cedar() {
        return table("tfc:wood/leaves/white_cedar",
                item("tfc:wood/leaves/white_cedar", when(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH))), List.of()),
                item("tfc:wood/sapling/white_cedar", when(chance(0.0318)), List.of()),
                alt(when(not(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH)))), item("minecraft:stick", when(tool(ToolTag.SHARP), chance(0.2)), List.of(setCount(List.of(), 1, 2))), item("minecraft:stick", when(chance(0.05)), List.of(setCount(List.of(), 1, 2)))));
    }

    private static HarvestTable t_tfc_wood_leaves_willow() {
        return table("tfc:wood/leaves/willow",
                item("tfc:wood/leaves/willow", when(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH))), List.of()),
                item("tfc:wood/sapling/willow", when(chance(0.0143)), List.of()),
                alt(when(not(anyOf(tool(ToolTag.SHEAR), tool(ToolTag.SILK_TOUCH)))), item("minecraft:stick", when(tool(ToolTag.SHARP), chance(0.2)), List.of(setCount(List.of(), 1, 2))), item("minecraft:stick", when(chance(0.05)), List.of(setCount(List.of(), 1, 2)))));
    }

    private static HarvestTable t_tfc_wood_twig_acacia() {
        return table("tfc:wood/twig/acacia",
                item("tfc:wood/twig/acacia", List.of(), List.of()));
    }

    private static HarvestTable t_tfc_wood_twig_ash() {
        return table("tfc:wood/twig/ash",
                item("tfc:wood/twig/ash", List.of(), List.of()));
    }

    private static HarvestTable t_tfc_wood_twig_aspen() {
        return table("tfc:wood/twig/aspen",
                item("tfc:wood/twig/aspen", List.of(), List.of()));
    }

    private static HarvestTable t_tfc_wood_twig_birch() {
        return table("tfc:wood/twig/birch",
                item("tfc:wood/twig/birch", List.of(), List.of()));
    }

    private static HarvestTable t_tfc_wood_twig_blackwood() {
        return table("tfc:wood/twig/blackwood",
                item("tfc:wood/twig/blackwood", List.of(), List.of()));
    }

    private static HarvestTable t_tfc_wood_twig_chestnut() {
        return table("tfc:wood/twig/chestnut",
                item("tfc:wood/twig/chestnut", List.of(), List.of()));
    }

    private static HarvestTable t_tfc_wood_twig_douglas_fir() {
        return table("tfc:wood/twig/douglas_fir",
                item("tfc:wood/twig/douglas_fir", List.of(), List.of()));
    }

    private static HarvestTable t_tfc_wood_twig_hickory() {
        return table("tfc:wood/twig/hickory",
                item("tfc:wood/twig/hickory", List.of(), List.of()));
    }

    private static HarvestTable t_tfc_wood_twig_kapok() {
        return table("tfc:wood/twig/kapok",
                item("tfc:wood/twig/kapok", List.of(), List.of()));
    }

    private static HarvestTable t_tfc_wood_twig_mangrove() {
        return table("tfc:wood/twig/mangrove",
                item("tfc:wood/twig/mangrove", List.of(), List.of()));
    }

    private static HarvestTable t_tfc_wood_twig_maple() {
        return table("tfc:wood/twig/maple",
                item("tfc:wood/twig/maple", List.of(), List.of()));
    }

    private static HarvestTable t_tfc_wood_twig_oak() {
        return table("tfc:wood/twig/oak",
                item("tfc:wood/twig/oak", List.of(), List.of()));
    }

    private static HarvestTable t_tfc_wood_twig_palm() {
        return table("tfc:wood/twig/palm",
                item("tfc:wood/twig/palm", List.of(), List.of()));
    }

    private static HarvestTable t_tfc_wood_twig_pine() {
        return table("tfc:wood/twig/pine",
                item("tfc:wood/twig/pine", List.of(), List.of()));
    }

    private static HarvestTable t_tfc_wood_twig_rosewood() {
        return table("tfc:wood/twig/rosewood",
                item("tfc:wood/twig/rosewood", List.of(), List.of()));
    }

    private static HarvestTable t_tfc_wood_twig_sequoia() {
        return table("tfc:wood/twig/sequoia",
                item("tfc:wood/twig/sequoia", List.of(), List.of()));
    }

    private static HarvestTable t_tfc_wood_twig_spruce() {
        return table("tfc:wood/twig/spruce",
                item("tfc:wood/twig/spruce", List.of(), List.of()));
    }

    private static HarvestTable t_tfc_wood_twig_sycamore() {
        return table("tfc:wood/twig/sycamore",
                item("tfc:wood/twig/sycamore", List.of(), List.of()));
    }

    private static HarvestTable t_tfc_wood_twig_white_cedar() {
        return table("tfc:wood/twig/white_cedar",
                item("tfc:wood/twig/white_cedar", List.of(), List.of()));
    }

    private static HarvestTable t_tfc_wood_twig_willow() {
        return table("tfc:wood/twig/willow",
                item("tfc:wood/twig/willow", List.of(), List.of()));
    }

    private static HarvestTable t_tfc_wood_fallen_leaves_acacia() {
        return table("tfc:wood/fallen_leaves/acacia",
                item("tfc:wood/fallen_leaves/acacia", when(state(Map.of("layers", "1"))), List.of(setCount(List.of(), 1, 1))),
                item("tfc:wood/fallen_leaves/acacia", when(state(Map.of("layers", "2"))), List.of(setCount(List.of(), 2, 2))),
                item("tfc:wood/fallen_leaves/acacia", when(state(Map.of("layers", "3"))), List.of(setCount(List.of(), 3, 3))),
                item("tfc:wood/fallen_leaves/acacia", when(state(Map.of("layers", "4"))), List.of(setCount(List.of(), 4, 4))),
                item("tfc:wood/fallen_leaves/acacia", when(state(Map.of("layers", "5"))), List.of(setCount(List.of(), 5, 5))),
                item("tfc:wood/fallen_leaves/acacia", when(state(Map.of("layers", "6"))), List.of(setCount(List.of(), 6, 6))),
                item("tfc:wood/fallen_leaves/acacia", when(state(Map.of("layers", "7"))), List.of(setCount(List.of(), 7, 7))),
                item("tfc:wood/fallen_leaves/acacia", when(state(Map.of("layers", "8"))), List.of(setCount(List.of(), 8, 8))));
    }

    private static HarvestTable t_tfc_wood_fallen_leaves_ash() {
        return table("tfc:wood/fallen_leaves/ash",
                item("tfc:wood/fallen_leaves/ash", when(state(Map.of("layers", "1"))), List.of(setCount(List.of(), 1, 1))),
                item("tfc:wood/fallen_leaves/ash", when(state(Map.of("layers", "2"))), List.of(setCount(List.of(), 2, 2))),
                item("tfc:wood/fallen_leaves/ash", when(state(Map.of("layers", "3"))), List.of(setCount(List.of(), 3, 3))),
                item("tfc:wood/fallen_leaves/ash", when(state(Map.of("layers", "4"))), List.of(setCount(List.of(), 4, 4))),
                item("tfc:wood/fallen_leaves/ash", when(state(Map.of("layers", "5"))), List.of(setCount(List.of(), 5, 5))),
                item("tfc:wood/fallen_leaves/ash", when(state(Map.of("layers", "6"))), List.of(setCount(List.of(), 6, 6))),
                item("tfc:wood/fallen_leaves/ash", when(state(Map.of("layers", "7"))), List.of(setCount(List.of(), 7, 7))),
                item("tfc:wood/fallen_leaves/ash", when(state(Map.of("layers", "8"))), List.of(setCount(List.of(), 8, 8))));
    }

    private static HarvestTable t_tfc_wood_fallen_leaves_aspen() {
        return table("tfc:wood/fallen_leaves/aspen",
                item("tfc:wood/fallen_leaves/aspen", when(state(Map.of("layers", "1"))), List.of(setCount(List.of(), 1, 1))),
                item("tfc:wood/fallen_leaves/aspen", when(state(Map.of("layers", "2"))), List.of(setCount(List.of(), 2, 2))),
                item("tfc:wood/fallen_leaves/aspen", when(state(Map.of("layers", "3"))), List.of(setCount(List.of(), 3, 3))),
                item("tfc:wood/fallen_leaves/aspen", when(state(Map.of("layers", "4"))), List.of(setCount(List.of(), 4, 4))),
                item("tfc:wood/fallen_leaves/aspen", when(state(Map.of("layers", "5"))), List.of(setCount(List.of(), 5, 5))),
                item("tfc:wood/fallen_leaves/aspen", when(state(Map.of("layers", "6"))), List.of(setCount(List.of(), 6, 6))),
                item("tfc:wood/fallen_leaves/aspen", when(state(Map.of("layers", "7"))), List.of(setCount(List.of(), 7, 7))),
                item("tfc:wood/fallen_leaves/aspen", when(state(Map.of("layers", "8"))), List.of(setCount(List.of(), 8, 8))));
    }

    private static HarvestTable t_tfc_wood_fallen_leaves_birch() {
        return table("tfc:wood/fallen_leaves/birch",
                item("tfc:wood/fallen_leaves/birch", when(state(Map.of("layers", "1"))), List.of(setCount(List.of(), 1, 1))),
                item("tfc:wood/fallen_leaves/birch", when(state(Map.of("layers", "2"))), List.of(setCount(List.of(), 2, 2))),
                item("tfc:wood/fallen_leaves/birch", when(state(Map.of("layers", "3"))), List.of(setCount(List.of(), 3, 3))),
                item("tfc:wood/fallen_leaves/birch", when(state(Map.of("layers", "4"))), List.of(setCount(List.of(), 4, 4))),
                item("tfc:wood/fallen_leaves/birch", when(state(Map.of("layers", "5"))), List.of(setCount(List.of(), 5, 5))),
                item("tfc:wood/fallen_leaves/birch", when(state(Map.of("layers", "6"))), List.of(setCount(List.of(), 6, 6))),
                item("tfc:wood/fallen_leaves/birch", when(state(Map.of("layers", "7"))), List.of(setCount(List.of(), 7, 7))),
                item("tfc:wood/fallen_leaves/birch", when(state(Map.of("layers", "8"))), List.of(setCount(List.of(), 8, 8))));
    }

    private static HarvestTable t_tfc_wood_fallen_leaves_blackwood() {
        return table("tfc:wood/fallen_leaves/blackwood",
                item("tfc:wood/fallen_leaves/blackwood", when(state(Map.of("layers", "1"))), List.of(setCount(List.of(), 1, 1))),
                item("tfc:wood/fallen_leaves/blackwood", when(state(Map.of("layers", "2"))), List.of(setCount(List.of(), 2, 2))),
                item("tfc:wood/fallen_leaves/blackwood", when(state(Map.of("layers", "3"))), List.of(setCount(List.of(), 3, 3))),
                item("tfc:wood/fallen_leaves/blackwood", when(state(Map.of("layers", "4"))), List.of(setCount(List.of(), 4, 4))),
                item("tfc:wood/fallen_leaves/blackwood", when(state(Map.of("layers", "5"))), List.of(setCount(List.of(), 5, 5))),
                item("tfc:wood/fallen_leaves/blackwood", when(state(Map.of("layers", "6"))), List.of(setCount(List.of(), 6, 6))),
                item("tfc:wood/fallen_leaves/blackwood", when(state(Map.of("layers", "7"))), List.of(setCount(List.of(), 7, 7))),
                item("tfc:wood/fallen_leaves/blackwood", when(state(Map.of("layers", "8"))), List.of(setCount(List.of(), 8, 8))));
    }

    private static HarvestTable t_tfc_wood_fallen_leaves_chestnut() {
        return table("tfc:wood/fallen_leaves/chestnut",
                item("tfc:wood/fallen_leaves/chestnut", when(state(Map.of("layers", "1"))), List.of(setCount(List.of(), 1, 1))),
                item("tfc:wood/fallen_leaves/chestnut", when(state(Map.of("layers", "2"))), List.of(setCount(List.of(), 2, 2))),
                item("tfc:wood/fallen_leaves/chestnut", when(state(Map.of("layers", "3"))), List.of(setCount(List.of(), 3, 3))),
                item("tfc:wood/fallen_leaves/chestnut", when(state(Map.of("layers", "4"))), List.of(setCount(List.of(), 4, 4))),
                item("tfc:wood/fallen_leaves/chestnut", when(state(Map.of("layers", "5"))), List.of(setCount(List.of(), 5, 5))),
                item("tfc:wood/fallen_leaves/chestnut", when(state(Map.of("layers", "6"))), List.of(setCount(List.of(), 6, 6))),
                item("tfc:wood/fallen_leaves/chestnut", when(state(Map.of("layers", "7"))), List.of(setCount(List.of(), 7, 7))),
                item("tfc:wood/fallen_leaves/chestnut", when(state(Map.of("layers", "8"))), List.of(setCount(List.of(), 8, 8))));
    }

    private static HarvestTable t_tfc_wood_fallen_leaves_douglas_fir() {
        return table("tfc:wood/fallen_leaves/douglas_fir",
                item("tfc:wood/fallen_leaves/douglas_fir", when(state(Map.of("layers", "1"))), List.of(setCount(List.of(), 1, 1))),
                item("tfc:wood/fallen_leaves/douglas_fir", when(state(Map.of("layers", "2"))), List.of(setCount(List.of(), 2, 2))),
                item("tfc:wood/fallen_leaves/douglas_fir", when(state(Map.of("layers", "3"))), List.of(setCount(List.of(), 3, 3))),
                item("tfc:wood/fallen_leaves/douglas_fir", when(state(Map.of("layers", "4"))), List.of(setCount(List.of(), 4, 4))),
                item("tfc:wood/fallen_leaves/douglas_fir", when(state(Map.of("layers", "5"))), List.of(setCount(List.of(), 5, 5))),
                item("tfc:wood/fallen_leaves/douglas_fir", when(state(Map.of("layers", "6"))), List.of(setCount(List.of(), 6, 6))),
                item("tfc:wood/fallen_leaves/douglas_fir", when(state(Map.of("layers", "7"))), List.of(setCount(List.of(), 7, 7))),
                item("tfc:wood/fallen_leaves/douglas_fir", when(state(Map.of("layers", "8"))), List.of(setCount(List.of(), 8, 8))));
    }

    private static HarvestTable t_tfc_wood_fallen_leaves_hickory() {
        return table("tfc:wood/fallen_leaves/hickory",
                item("tfc:wood/fallen_leaves/hickory", when(state(Map.of("layers", "1"))), List.of(setCount(List.of(), 1, 1))),
                item("tfc:wood/fallen_leaves/hickory", when(state(Map.of("layers", "2"))), List.of(setCount(List.of(), 2, 2))),
                item("tfc:wood/fallen_leaves/hickory", when(state(Map.of("layers", "3"))), List.of(setCount(List.of(), 3, 3))),
                item("tfc:wood/fallen_leaves/hickory", when(state(Map.of("layers", "4"))), List.of(setCount(List.of(), 4, 4))),
                item("tfc:wood/fallen_leaves/hickory", when(state(Map.of("layers", "5"))), List.of(setCount(List.of(), 5, 5))),
                item("tfc:wood/fallen_leaves/hickory", when(state(Map.of("layers", "6"))), List.of(setCount(List.of(), 6, 6))),
                item("tfc:wood/fallen_leaves/hickory", when(state(Map.of("layers", "7"))), List.of(setCount(List.of(), 7, 7))),
                item("tfc:wood/fallen_leaves/hickory", when(state(Map.of("layers", "8"))), List.of(setCount(List.of(), 8, 8))));
    }

    private static HarvestTable t_tfc_wood_fallen_leaves_kapok() {
        return table("tfc:wood/fallen_leaves/kapok",
                item("tfc:wood/fallen_leaves/kapok", when(state(Map.of("layers", "1"))), List.of(setCount(List.of(), 1, 1))),
                item("tfc:wood/fallen_leaves/kapok", when(state(Map.of("layers", "2"))), List.of(setCount(List.of(), 2, 2))),
                item("tfc:wood/fallen_leaves/kapok", when(state(Map.of("layers", "3"))), List.of(setCount(List.of(), 3, 3))),
                item("tfc:wood/fallen_leaves/kapok", when(state(Map.of("layers", "4"))), List.of(setCount(List.of(), 4, 4))),
                item("tfc:wood/fallen_leaves/kapok", when(state(Map.of("layers", "5"))), List.of(setCount(List.of(), 5, 5))),
                item("tfc:wood/fallen_leaves/kapok", when(state(Map.of("layers", "6"))), List.of(setCount(List.of(), 6, 6))),
                item("tfc:wood/fallen_leaves/kapok", when(state(Map.of("layers", "7"))), List.of(setCount(List.of(), 7, 7))),
                item("tfc:wood/fallen_leaves/kapok", when(state(Map.of("layers", "8"))), List.of(setCount(List.of(), 8, 8))));
    }

    private static HarvestTable t_tfc_wood_fallen_leaves_mangrove() {
        return table("tfc:wood/fallen_leaves/mangrove",
                item("tfc:wood/fallen_leaves/mangrove", when(state(Map.of("layers", "1"))), List.of(setCount(List.of(), 1, 1))),
                item("tfc:wood/fallen_leaves/mangrove", when(state(Map.of("layers", "2"))), List.of(setCount(List.of(), 2, 2))),
                item("tfc:wood/fallen_leaves/mangrove", when(state(Map.of("layers", "3"))), List.of(setCount(List.of(), 3, 3))),
                item("tfc:wood/fallen_leaves/mangrove", when(state(Map.of("layers", "4"))), List.of(setCount(List.of(), 4, 4))),
                item("tfc:wood/fallen_leaves/mangrove", when(state(Map.of("layers", "5"))), List.of(setCount(List.of(), 5, 5))),
                item("tfc:wood/fallen_leaves/mangrove", when(state(Map.of("layers", "6"))), List.of(setCount(List.of(), 6, 6))),
                item("tfc:wood/fallen_leaves/mangrove", when(state(Map.of("layers", "7"))), List.of(setCount(List.of(), 7, 7))),
                item("tfc:wood/fallen_leaves/mangrove", when(state(Map.of("layers", "8"))), List.of(setCount(List.of(), 8, 8))));
    }

    private static HarvestTable t_tfc_wood_fallen_leaves_maple() {
        return table("tfc:wood/fallen_leaves/maple",
                item("tfc:wood/fallen_leaves/maple", when(state(Map.of("layers", "1"))), List.of(setCount(List.of(), 1, 1))),
                item("tfc:wood/fallen_leaves/maple", when(state(Map.of("layers", "2"))), List.of(setCount(List.of(), 2, 2))),
                item("tfc:wood/fallen_leaves/maple", when(state(Map.of("layers", "3"))), List.of(setCount(List.of(), 3, 3))),
                item("tfc:wood/fallen_leaves/maple", when(state(Map.of("layers", "4"))), List.of(setCount(List.of(), 4, 4))),
                item("tfc:wood/fallen_leaves/maple", when(state(Map.of("layers", "5"))), List.of(setCount(List.of(), 5, 5))),
                item("tfc:wood/fallen_leaves/maple", when(state(Map.of("layers", "6"))), List.of(setCount(List.of(), 6, 6))),
                item("tfc:wood/fallen_leaves/maple", when(state(Map.of("layers", "7"))), List.of(setCount(List.of(), 7, 7))),
                item("tfc:wood/fallen_leaves/maple", when(state(Map.of("layers", "8"))), List.of(setCount(List.of(), 8, 8))));
    }

    private static HarvestTable t_tfc_wood_fallen_leaves_oak() {
        return table("tfc:wood/fallen_leaves/oak",
                item("tfc:wood/fallen_leaves/oak", when(state(Map.of("layers", "1"))), List.of(setCount(List.of(), 1, 1))),
                item("tfc:wood/fallen_leaves/oak", when(state(Map.of("layers", "2"))), List.of(setCount(List.of(), 2, 2))),
                item("tfc:wood/fallen_leaves/oak", when(state(Map.of("layers", "3"))), List.of(setCount(List.of(), 3, 3))),
                item("tfc:wood/fallen_leaves/oak", when(state(Map.of("layers", "4"))), List.of(setCount(List.of(), 4, 4))),
                item("tfc:wood/fallen_leaves/oak", when(state(Map.of("layers", "5"))), List.of(setCount(List.of(), 5, 5))),
                item("tfc:wood/fallen_leaves/oak", when(state(Map.of("layers", "6"))), List.of(setCount(List.of(), 6, 6))),
                item("tfc:wood/fallen_leaves/oak", when(state(Map.of("layers", "7"))), List.of(setCount(List.of(), 7, 7))),
                item("tfc:wood/fallen_leaves/oak", when(state(Map.of("layers", "8"))), List.of(setCount(List.of(), 8, 8))));
    }

    private static HarvestTable t_tfc_wood_fallen_leaves_palm() {
        return table("tfc:wood/fallen_leaves/palm",
                item("tfc:wood/fallen_leaves/palm", when(state(Map.of("layers", "1"))), List.of(setCount(List.of(), 1, 1))),
                item("tfc:wood/fallen_leaves/palm", when(state(Map.of("layers", "2"))), List.of(setCount(List.of(), 2, 2))),
                item("tfc:wood/fallen_leaves/palm", when(state(Map.of("layers", "3"))), List.of(setCount(List.of(), 3, 3))),
                item("tfc:wood/fallen_leaves/palm", when(state(Map.of("layers", "4"))), List.of(setCount(List.of(), 4, 4))),
                item("tfc:wood/fallen_leaves/palm", when(state(Map.of("layers", "5"))), List.of(setCount(List.of(), 5, 5))),
                item("tfc:wood/fallen_leaves/palm", when(state(Map.of("layers", "6"))), List.of(setCount(List.of(), 6, 6))),
                item("tfc:wood/fallen_leaves/palm", when(state(Map.of("layers", "7"))), List.of(setCount(List.of(), 7, 7))),
                item("tfc:wood/fallen_leaves/palm", when(state(Map.of("layers", "8"))), List.of(setCount(List.of(), 8, 8))));
    }

    private static HarvestTable t_tfc_wood_fallen_leaves_pine() {
        return table("tfc:wood/fallen_leaves/pine",
                item("tfc:wood/fallen_leaves/pine", when(state(Map.of("layers", "1"))), List.of(setCount(List.of(), 1, 1))),
                item("tfc:wood/fallen_leaves/pine", when(state(Map.of("layers", "2"))), List.of(setCount(List.of(), 2, 2))),
                item("tfc:wood/fallen_leaves/pine", when(state(Map.of("layers", "3"))), List.of(setCount(List.of(), 3, 3))),
                item("tfc:wood/fallen_leaves/pine", when(state(Map.of("layers", "4"))), List.of(setCount(List.of(), 4, 4))),
                item("tfc:wood/fallen_leaves/pine", when(state(Map.of("layers", "5"))), List.of(setCount(List.of(), 5, 5))),
                item("tfc:wood/fallen_leaves/pine", when(state(Map.of("layers", "6"))), List.of(setCount(List.of(), 6, 6))),
                item("tfc:wood/fallen_leaves/pine", when(state(Map.of("layers", "7"))), List.of(setCount(List.of(), 7, 7))),
                item("tfc:wood/fallen_leaves/pine", when(state(Map.of("layers", "8"))), List.of(setCount(List.of(), 8, 8))));
    }

    private static HarvestTable t_tfc_wood_fallen_leaves_rosewood() {
        return table("tfc:wood/fallen_leaves/rosewood",
                item("tfc:wood/fallen_leaves/rosewood", when(state(Map.of("layers", "1"))), List.of(setCount(List.of(), 1, 1))),
                item("tfc:wood/fallen_leaves/rosewood", when(state(Map.of("layers", "2"))), List.of(setCount(List.of(), 2, 2))),
                item("tfc:wood/fallen_leaves/rosewood", when(state(Map.of("layers", "3"))), List.of(setCount(List.of(), 3, 3))),
                item("tfc:wood/fallen_leaves/rosewood", when(state(Map.of("layers", "4"))), List.of(setCount(List.of(), 4, 4))),
                item("tfc:wood/fallen_leaves/rosewood", when(state(Map.of("layers", "5"))), List.of(setCount(List.of(), 5, 5))),
                item("tfc:wood/fallen_leaves/rosewood", when(state(Map.of("layers", "6"))), List.of(setCount(List.of(), 6, 6))),
                item("tfc:wood/fallen_leaves/rosewood", when(state(Map.of("layers", "7"))), List.of(setCount(List.of(), 7, 7))),
                item("tfc:wood/fallen_leaves/rosewood", when(state(Map.of("layers", "8"))), List.of(setCount(List.of(), 8, 8))));
    }

    private static HarvestTable t_tfc_wood_fallen_leaves_sequoia() {
        return table("tfc:wood/fallen_leaves/sequoia",
                item("tfc:wood/fallen_leaves/sequoia", when(state(Map.of("layers", "1"))), List.of(setCount(List.of(), 1, 1))),
                item("tfc:wood/fallen_leaves/sequoia", when(state(Map.of("layers", "2"))), List.of(setCount(List.of(), 2, 2))),
                item("tfc:wood/fallen_leaves/sequoia", when(state(Map.of("layers", "3"))), List.of(setCount(List.of(), 3, 3))),
                item("tfc:wood/fallen_leaves/sequoia", when(state(Map.of("layers", "4"))), List.of(setCount(List.of(), 4, 4))),
                item("tfc:wood/fallen_leaves/sequoia", when(state(Map.of("layers", "5"))), List.of(setCount(List.of(), 5, 5))),
                item("tfc:wood/fallen_leaves/sequoia", when(state(Map.of("layers", "6"))), List.of(setCount(List.of(), 6, 6))),
                item("tfc:wood/fallen_leaves/sequoia", when(state(Map.of("layers", "7"))), List.of(setCount(List.of(), 7, 7))),
                item("tfc:wood/fallen_leaves/sequoia", when(state(Map.of("layers", "8"))), List.of(setCount(List.of(), 8, 8))));
    }

    private static HarvestTable t_tfc_wood_fallen_leaves_spruce() {
        return table("tfc:wood/fallen_leaves/spruce",
                item("tfc:wood/fallen_leaves/spruce", when(state(Map.of("layers", "1"))), List.of(setCount(List.of(), 1, 1))),
                item("tfc:wood/fallen_leaves/spruce", when(state(Map.of("layers", "2"))), List.of(setCount(List.of(), 2, 2))),
                item("tfc:wood/fallen_leaves/spruce", when(state(Map.of("layers", "3"))), List.of(setCount(List.of(), 3, 3))),
                item("tfc:wood/fallen_leaves/spruce", when(state(Map.of("layers", "4"))), List.of(setCount(List.of(), 4, 4))),
                item("tfc:wood/fallen_leaves/spruce", when(state(Map.of("layers", "5"))), List.of(setCount(List.of(), 5, 5))),
                item("tfc:wood/fallen_leaves/spruce", when(state(Map.of("layers", "6"))), List.of(setCount(List.of(), 6, 6))),
                item("tfc:wood/fallen_leaves/spruce", when(state(Map.of("layers", "7"))), List.of(setCount(List.of(), 7, 7))),
                item("tfc:wood/fallen_leaves/spruce", when(state(Map.of("layers", "8"))), List.of(setCount(List.of(), 8, 8))));
    }

    private static HarvestTable t_tfc_wood_fallen_leaves_sycamore() {
        return table("tfc:wood/fallen_leaves/sycamore",
                item("tfc:wood/fallen_leaves/sycamore", when(state(Map.of("layers", "1"))), List.of(setCount(List.of(), 1, 1))),
                item("tfc:wood/fallen_leaves/sycamore", when(state(Map.of("layers", "2"))), List.of(setCount(List.of(), 2, 2))),
                item("tfc:wood/fallen_leaves/sycamore", when(state(Map.of("layers", "3"))), List.of(setCount(List.of(), 3, 3))),
                item("tfc:wood/fallen_leaves/sycamore", when(state(Map.of("layers", "4"))), List.of(setCount(List.of(), 4, 4))),
                item("tfc:wood/fallen_leaves/sycamore", when(state(Map.of("layers", "5"))), List.of(setCount(List.of(), 5, 5))),
                item("tfc:wood/fallen_leaves/sycamore", when(state(Map.of("layers", "6"))), List.of(setCount(List.of(), 6, 6))),
                item("tfc:wood/fallen_leaves/sycamore", when(state(Map.of("layers", "7"))), List.of(setCount(List.of(), 7, 7))),
                item("tfc:wood/fallen_leaves/sycamore", when(state(Map.of("layers", "8"))), List.of(setCount(List.of(), 8, 8))));
    }

    private static HarvestTable t_tfc_wood_fallen_leaves_white_cedar() {
        return table("tfc:wood/fallen_leaves/white_cedar",
                item("tfc:wood/fallen_leaves/white_cedar", when(state(Map.of("layers", "1"))), List.of(setCount(List.of(), 1, 1))),
                item("tfc:wood/fallen_leaves/white_cedar", when(state(Map.of("layers", "2"))), List.of(setCount(List.of(), 2, 2))),
                item("tfc:wood/fallen_leaves/white_cedar", when(state(Map.of("layers", "3"))), List.of(setCount(List.of(), 3, 3))),
                item("tfc:wood/fallen_leaves/white_cedar", when(state(Map.of("layers", "4"))), List.of(setCount(List.of(), 4, 4))),
                item("tfc:wood/fallen_leaves/white_cedar", when(state(Map.of("layers", "5"))), List.of(setCount(List.of(), 5, 5))),
                item("tfc:wood/fallen_leaves/white_cedar", when(state(Map.of("layers", "6"))), List.of(setCount(List.of(), 6, 6))),
                item("tfc:wood/fallen_leaves/white_cedar", when(state(Map.of("layers", "7"))), List.of(setCount(List.of(), 7, 7))),
                item("tfc:wood/fallen_leaves/white_cedar", when(state(Map.of("layers", "8"))), List.of(setCount(List.of(), 8, 8))));
    }

    private static HarvestTable t_tfc_wood_fallen_leaves_willow() {
        return table("tfc:wood/fallen_leaves/willow",
                item("tfc:wood/fallen_leaves/willow", when(state(Map.of("layers", "1"))), List.of(setCount(List.of(), 1, 1))),
                item("tfc:wood/fallen_leaves/willow", when(state(Map.of("layers", "2"))), List.of(setCount(List.of(), 2, 2))),
                item("tfc:wood/fallen_leaves/willow", when(state(Map.of("layers", "3"))), List.of(setCount(List.of(), 3, 3))),
                item("tfc:wood/fallen_leaves/willow", when(state(Map.of("layers", "4"))), List.of(setCount(List.of(), 4, 4))),
                item("tfc:wood/fallen_leaves/willow", when(state(Map.of("layers", "5"))), List.of(setCount(List.of(), 5, 5))),
                item("tfc:wood/fallen_leaves/willow", when(state(Map.of("layers", "6"))), List.of(setCount(List.of(), 6, 6))),
                item("tfc:wood/fallen_leaves/willow", when(state(Map.of("layers", "7"))), List.of(setCount(List.of(), 7, 7))),
                item("tfc:wood/fallen_leaves/willow", when(state(Map.of("layers", "8"))), List.of(setCount(List.of(), 8, 8))));
    }

    private static HarvestTable t_tfc_groundcover_stick() {
        return table("tfc:groundcover/stick",
                item("minecraft:stick", List.of(), List.of()));
    }

    private static HarvestTable table(String blockId, LootEntry... entries) {
        return new HarvestTable(blockId, List.of(entries));
    }

    private static LootEntry item(String itemId, List<HarvestCondition> conditions, List<LootEntry.SetCount> counts) {
        return new LootEntry.Item(itemId, conditions, counts);
    }

    private static LootEntry alt(List<HarvestCondition> conditions, LootEntry... children) {
        return new LootEntry.Alternatives(conditions, List.of(children));
    }

    private static LootEntry.SetCount setCount(List<HarvestCondition> conditions, int min, int max) {
        return new LootEntry.SetCount(conditions, min, max);
    }

    private static List<HarvestCondition> when(HarvestCondition... conditions) {
        return List.of(conditions);
    }

    private static HarvestCondition tool(ToolTag tag) {
        return new HarvestCondition.HasTool(tag);
    }

    private static HarvestCondition chance(double chance) {
        return new HarvestCondition.Chance(chance);
    }

    private static HarvestCondition state(Map<String, String> properties) {
        return new HarvestCondition.StateIs(properties);
    }

    private static HarvestCondition anyOf(HarvestCondition... terms) {
        return new HarvestCondition.AnyOf(List.of(terms));
    }

    private static HarvestCondition allOf(HarvestCondition... terms) {
        return new HarvestCondition.AllOf(List.of(terms));
    }

    private static HarvestCondition not(HarvestCondition term) {
        return new HarvestCondition.Not(term);
    }
}
