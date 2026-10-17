/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/Plant.java @ c3b4d9536 (the plants' block types, BlockType and
 * getMaxAgeForType), with the canSurvive / mayPlaceOn rules of TFCBushBlock, PlantBlock (createDry), ShortGrassBlock
 * (incl. createBeachGrass), TFCTallGrassBlock, TFCPassableCactusBlock, TFCCactusBedBlock, FloatingWaterPlantBlock,
 * WaterPlantBlock (TFCSeagrassBlock) and TallWaterPlantBlock in the same package, and the block tags they test
 * (src/generated/resources/data/tfc/tags/block/{bush,grass,dry_plant,sea_bush,halophyte}_plantable_on.json, halophyte.json)
 */
package dev.tfp.core.worldgen.plant;

import dev.tfp.core.worldgen.tree.TreeGround;
import java.util.Map;

/**
 * TFC's plant block types ({@code Plant.BlockType}) and which plant has which. A type decides whether the plant has an age
 * property (drawn at worldgen) and what it survives on; {@link #survivable()} marks the types whose survival rule is ported.
 */
public enum PlantType {
    STANDARD, SHORT_SHRUB, SHRUB, FLOWERBED, CACTUSBED, CACTUS_FLOWER, CACTUS, PASSABLE_CACTUS, DRY, CREEPING, CREEPING_STONE,
    EPIPHYTE, PERCHED_EPIPHYTE, SHORT_GRASS, BEACH_GRASS, TALL_GRASS, TALL_SHRUB, VINE, WEEPING, WEEPING_TOP, TWISTING,
    TWISTING_TOP, TWISTING_TOP_SHORT, TWISTING_SOLID, TWISTING_SOLID_TOP, BRANCHING_CACTUS, BRANCHING_CACTUS_TOP,
    OCEAN_ROCK_CREEPING, OCEAN_CREEPING, OCEAN_ROTATABLE, KELP, KELP_TOP, KELP_TREE, KELP_TREE_FLOWER, FLOATING,
    FLOATING_FRESH, TALL_WATER, TALL_WATER_FRESH, WATER, WATER_FRESH, GRASS_WATER, GRASS_WATER_FRESH, BAMBOO_SAPLING, BAMBOO;

    /** The block type of each TFC plant, by the plant's name ({@code allium} for {@code tfc:plant/allium}). */
    private static final Map<String, PlantType> PLANTS = Map.ofEntries(
        Map.entry("athyrium_fern", STANDARD),
        Map.entry("canna", STANDARD),
        Map.entry("goldenrod", STANDARD),
        Map.entry("pampas_grass", TALL_GRASS),
        Map.entry("perovskia", DRY),
        Map.entry("rose", TALL_GRASS),
        Map.entry("beachgrass", BEACH_GRASS),
        Map.entry("bluegrass", SHORT_GRASS),
        Map.entry("bromegrass", SHORT_GRASS),
        Map.entry("fountain_grass", SHORT_GRASS),
        Map.entry("manatee_grass", GRASS_WATER),
        Map.entry("orchard_grass", SHORT_GRASS),
        Map.entry("ryegrass", SHORT_GRASS),
        Map.entry("scutch_grass", SHORT_GRASS),
        Map.entry("star_grass", GRASS_WATER),
        Map.entry("timothy_grass", SHORT_GRASS),
        Map.entry("raddia_grass", SHORT_GRASS),
        Map.entry("red_oat_grass", SHORT_GRASS),
        Map.entry("anemone_green", OCEAN_CREEPING),
        Map.entry("anemone_purple", OCEAN_CREEPING),
        Map.entry("anemone_large_orange", OCEAN_ROTATABLE),
        Map.entry("anemone_large_purple", OCEAN_ROTATABLE),
        Map.entry("barnacles", OCEAN_ROCK_CREEPING),
        Map.entry("mussels", OCEAN_ROCK_CREEPING),
        Map.entry("starfish", OCEAN_ROTATABLE),
        Map.entry("allium", STANDARD),
        Map.entry("anthurium", STANDARD),
        Map.entry("arrowhead", TALL_WATER_FRESH),
        Map.entry("azalea", TALL_SHRUB),
        Map.entry("badderlocks", TALL_WATER),
        Map.entry("barrel_cactus", CACTUS),
        Map.entry("bear_grass", TALL_GRASS),
        Map.entry("bird_nest_fern", PERCHED_EPIPHYTE),
        Map.entry("blood_lily", STANDARD),
        Map.entry("blue_ginger", STANDARD),
        Map.entry("blue_orchid", STANDARD),
        Map.entry("bur_reed", TALL_WATER_FRESH),
        Map.entry("buttercup", FLOWERBED),
        Map.entry("butterfly_milkweed", STANDARD),
        Map.entry("black_orchid", STANDARD),
        Map.entry("calendula", STANDARD),
        Map.entry("cattail", TALL_WATER_FRESH),
        Map.entry("cobblestone_lichen", CREEPING_STONE),
        Map.entry("coontail", GRASS_WATER_FRESH),
        Map.entry("cordgrass", TALL_WATER),
        Map.entry("cornflower", STANDARD),
        Map.entry("dandelion", STANDARD),
        Map.entry("dead_bush", DRY),
        Map.entry("desert_flame", STANDARD),
        Map.entry("dry_grass", DRY),
        Map.entry("duckweed", FLOATING_FRESH),
        Map.entry("edelweiss", STANDARD),
        Map.entry("eel_grass", GRASS_WATER_FRESH),
        Map.entry("elegant_sunburst_lichen", CREEPING_STONE),
        Map.entry("fan_palm", TALL_GRASS),
        Map.entry("field_horsetail", STANDARD),
        Map.entry("foxglove", TALL_GRASS),
        Map.entry("grape_hyacinth", STANDARD),
        Map.entry("green_algae", FLOATING_FRESH),
        Map.entry("gutweed", WATER),
        Map.entry("guzmania", EPIPHYTE),
        Map.entry("heliconia", STANDARD),
        Map.entry("heather", STANDARD),
        Map.entry("hibiscus", TALL_SHRUB),
        Map.entry("houstonia", STANDARD),
        Map.entry("kangaroo_paw", STANDARD),
        Map.entry("king_fern", TALL_GRASS),
        Map.entry("kinnikinnick", SHORT_SHRUB),
        Map.entry("labrador_tea", STANDARD),
        Map.entry("lady_fern", STANDARD),
        Map.entry("laminaria", WATER),
        Map.entry("licorice_fern", EPIPHYTE),
        Map.entry("artists_conk", EPIPHYTE),
        Map.entry("lily_of_the_valley", STANDARD),
        Map.entry("lilac", TALL_GRASS),
        Map.entry("lotus", FLOATING_FRESH),
        Map.entry("maiden_pink", FLOWERBED),
        Map.entry("marigold", TALL_WATER_FRESH),
        Map.entry("meads_milkweed", STANDARD),
        Map.entry("milfoil", WATER_FRESH),
        Map.entry("morning_glory", CREEPING),
        Map.entry("mountain_hullwort", TALL_SHRUB),
        Map.entry("moss", CREEPING),
        Map.entry("moss_campion", DRY),
        Map.entry("nasturtium", STANDARD),
        Map.entry("ostrich_fern", TALL_GRASS),
        Map.entry("oxeye_daisy", STANDARD),
        Map.entry("palash", TALL_SHRUB),
        Map.entry("penwortel", SHRUB),
        Map.entry("philodendron", CREEPING),
        Map.entry("phragmite", TALL_WATER_FRESH),
        Map.entry("pickerelweed", TALL_WATER_FRESH),
        Map.entry("pistia", FLOATING_FRESH),
        Map.entry("poppy", STANDARD),
        Map.entry("prickly_pear", PASSABLE_CACTUS),
        Map.entry("prickly_pear_purple", PASSABLE_CACTUS),
        Map.entry("primrose", STANDARD),
        Map.entry("pulsatilla", STANDARD),
        Map.entry("qantu", SHRUB),
        Map.entry("ramirezella", EPIPHYTE),
        Map.entry("ramunda", STANDARD),
        Map.entry("red_algae", FLOATING),
        Map.entry("reindeer_lichen", CREEPING),
        Map.entry("red_sealing_wax_palm", TALL_GRASS),
        Map.entry("sacred_datura", STANDARD),
        Map.entry("sagebrush", DRY),
        Map.entry("sago", WATER_FRESH),
        Map.entry("saguaro_fruit", CACTUS_FLOWER),
        Map.entry("sapphire_tower", TALL_GRASS),
        Map.entry("sargassum", FLOATING),
        Map.entry("sea_lavender", TALL_WATER),
        Map.entry("sea_palm", DRY),
        Map.entry("shawiash", SHRUB),
        Map.entry("silken_pincushion_cactus", CACTUSBED),
        Map.entry("silver_bromeliad", PERCHED_EPIPHYTE),
        Map.entry("silver_spurflower", STANDARD),
        Map.entry("snapdragon_pink", STANDARD),
        Map.entry("snapdragon_red", STANDARD),
        Map.entry("snapdragon_white", STANDARD),
        Map.entry("snapdragon_yellow", STANDARD),
        Map.entry("strelitzia", STANDARD),
        Map.entry("sunflower", TALL_GRASS),
        Map.entry("switchgrass", TALL_GRASS),
        Map.entry("sword_fern", STANDARD),
        Map.entry("tall_fescue_grass", TALL_GRASS),
        Map.entry("tank_bromeliad", PERCHED_EPIPHYTE),
        Map.entry("toquilla_palm", TALL_GRASS),
        Map.entry("trillium", STANDARD),
        Map.entry("tropical_milkweed", STANDARD),
        Map.entry("tulip_orange", STANDARD),
        Map.entry("tulip_pink", STANDARD),
        Map.entry("tulip_red", STANDARD),
        Map.entry("tulip_white", STANDARD),
        Map.entry("turtle_grass", GRASS_WATER),
        Map.entry("vriesea", EPIPHYTE),
        Map.entry("water_canna", FLOATING_FRESH),
        Map.entry("white_water_lily", FLOATING_FRESH),
        Map.entry("purple_water_lily", FLOATING_FRESH),
        Map.entry("yellow_water_lily", FLOATING_FRESH),
        Map.entry("water_taro", TALL_WATER_FRESH),
        Map.entry("yellow_saxifrage", FLOWERBED),
        Map.entry("yucca", DRY),
        Map.entry("hanging_vines_plant", WEEPING),
        Map.entry("hanging_vines", WEEPING_TOP),
        Map.entry("spanish_moss_plant", WEEPING),
        Map.entry("spanish_moss", WEEPING_TOP),
        Map.entry("liana_plant", WEEPING),
        Map.entry("liana", WEEPING_TOP),
        Map.entry("flame_vine_plant", WEEPING),
        Map.entry("flame_vine", WEEPING_TOP),
        Map.entry("tree_fern_plant", TWISTING_SOLID),
        Map.entry("tree_fern", TWISTING_SOLID_TOP),
        Map.entry("cycad_plant", TWISTING_SOLID),
        Map.entry("cycad", TWISTING_SOLID_TOP),
        Map.entry("arundo_plant", TWISTING),
        Map.entry("arundo", TWISTING_TOP),
        Map.entry("dry_phragmite_plant", TWISTING),
        Map.entry("dry_phragmite", TWISTING_TOP_SHORT),
        Map.entry("winged_kelp_plant", KELP),
        Map.entry("winged_kelp", KELP_TOP),
        Map.entry("leafy_kelp_plant", KELP),
        Map.entry("leafy_kelp", KELP_TOP),
        Map.entry("giant_kelp_plant", KELP_TREE),
        Map.entry("giant_kelp_flower", KELP_TREE_FLOWER),
        Map.entry("ivy", CREEPING),
        Map.entry("jungle_vines", VINE),
        Map.entry("saguaro_plant", BRANCHING_CACTUS),
        Map.entry("saguaro", BRANCHING_CACTUS_TOP),
        Map.entry("golden_bamboo", BAMBOO),
        Map.entry("golden_bamboo_sapling", BAMBOO_SAPLING));

    /** The {@code tfc:halophyte} plants: tall water plants that grow on {@code minecraft:dirt} only. */
    private static final java.util.Set<String> HALOPHYTES = java.util.Set.of("sea_lavender", "cordgrass");

    /** The block type of a plant id ({@code tfp:plant/allium}), or null when it is not a TFC plant. */
    public static PlantType of(String id) {
        final int slash = id.lastIndexOf('/');
        if (!id.startsWith(PlantState.NS + "plant/") || slash < 0) {
            return null;
        }
        return PLANTS.get(id.substring(slash + 1));
    }

    /** Whether a plant id is in {@code tfc:halophyte}. */
    static boolean isHalophyte(String id) {
        return HALOPHYTES.contains(id.substring(id.lastIndexOf('/') + 1));
    }

    /** {@code Plant.getMaxAgeForType} is 3: the block has {@code age} 0..3, which worldgen draws. */
    public boolean hasAge() {
        return switch (this) {
            case GRASS_WATER, GRASS_WATER_FRESH, BEACH_GRASS, SHORT_GRASS, TALL_GRASS, FLOWERBED, CACTUSBED, FLOATING_FRESH,
                 FLOATING, KELP_TREE, KELP_TREE_FLOWER, TWISTING_TOP, TWISTING_SOLID_TOP, BRANCHING_CACTUS_TOP, KELP_TOP,
                 WEEPING_TOP, TALL_WATER_FRESH, TALL_SHRUB -> true;
            default -> false;
        };
    }

    /** Whether the block has a {@code part} (lower and upper halves). */
    public boolean isTall() {
        return this == TALL_GRASS || this == TALL_SHRUB || this == PASSABLE_CACTUS || this == TALL_WATER || this == TALL_WATER_FRESH;
    }

    /** The water a fluid-logged type holds: salt water, fresh water, or null for a type that holds none. */
    public PlantState.Fluid fluidProperty() {
        return switch (this) {
            case TALL_WATER, WATER, GRASS_WATER -> PlantState.Fluid.SALT_WATER;
            case TALL_WATER_FRESH, WATER_FRESH, GRASS_WATER_FRESH -> PlantState.Fluid.WATER;
            default -> null;
        };
    }

    /** Whether {@link #survives} implements the type's {@code canSurvive}. The other types' features are not placed. */
    public boolean survivable() {
        return switch (this) {
            case STANDARD, SHORT_SHRUB, SHRUB, FLOWERBED, CACTUSBED, PASSABLE_CACTUS, DRY, SHORT_GRASS, BEACH_GRASS, TALL_GRASS,
                 TALL_SHRUB, FLOATING, FLOATING_FRESH, TALL_WATER, TALL_WATER_FRESH, WATER, WATER_FRESH, GRASS_WATER,
                 GRASS_WATER_FRESH -> true;
            default -> false;
        };
    }

    /**
     * The type's {@code canSurvive} for the lower (or only) block of a plant in {@code state}, given the block below and the
     * fluid below.
     *
     * @param state      the plant's state (its fluid matters for the water types)
     * @param below      the terrain block below, or null when the block below is a placed plant
     * @param fluidBelow the fluid of the block below
     */
    boolean survives(PlantState state, TreeGround below, PlantState.Fluid fluidBelow) {
        return switch (this) {
            // TFCBushBlock.mayPlaceOn: tfc:bush_plantable_on (TFCTallGrassBlock's lower half too)
            case STANDARD, SHORT_SHRUB, SHRUB, FLOWERBED, TALL_GRASS, TALL_SHRUB -> below != null && below.bushPlantableOn();
            // ShortGrassBlock.mayPlaceOn: tfc:grass_plantable_on (dirt, farmland, clay, peat: only dirt is in the terrain)
            case SHORT_GRASS -> below != null && below.bushPlantableOn();
            // ShortGrassBlock.createBeachGrass: minecraft:sand or c:sands
            case BEACH_GRASS -> below != null && below.isSand();
            // PlantBlock.createDry, TFCPassableCactusBlock (lower), TFCCactusBedBlock: tfc:dry_plant_plantable_on
            case DRY, PASSABLE_CACTUS, CACTUSBED -> below != null && dryPlantableOn(below);
            // FloatingWaterPlantBlock: the fluid below is in the plant's fluid tag
            case FLOATING -> fluidBelow == PlantState.Fluid.SALT_WATER;
            case FLOATING_FRESH -> fluidBelow == PlantState.Fluid.WATER;
            // WaterPlantBlock (and TFCSeagrassBlock): tfc:sea_bush_plantable_on and a fluid
            case WATER, WATER_FRESH, GRASS_WATER, GRASS_WATER_FRESH ->
                below != null && below.seaBushPlantableOn() && state.fluid() != PlantState.Fluid.EMPTY;
            // TallWaterPlantBlock (lower): a fluid; tfc:halophyte_plantable_on for halophytes, else tfc:sea_bush_plantable_on
            case TALL_WATER, TALL_WATER_FRESH -> state.fluid() != PlantState.Fluid.EMPTY && below != null
                && (isHalophyte(state.block()) ? below.treeGrowsOn() : below.seaBushPlantableOn());
            default -> false;
        };
    }

    /**
     * {@code tfc:dry_plant_plantable_on}: {@code minecraft:sand}, {@code c:sands}, {@code c:gravels}, {@code tfc:coarse_dirt},
     * {@code tfc:bush_plantable_on} and the raw sandstones.
     */
    static boolean dryPlantableOn(TreeGround ground) {
        return ground.isSand() || ground.isGravel() || ground == TreeGround.COARSE_DIRT || ground.bushPlantableOn()
            || ground == TreeGround.SANDSTONE;
    }
}
