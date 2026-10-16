/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/rock/Rock.java @ c3b4d9536
 */
package dev.tfp.datagen;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Block properties TFC sets in Java (not in data), as datagen needs them for CraftEngine settings.
 * Sources (all @ c3b4d9536): {@code common/blocks/rock/Rock.java} (rock → display category, map colour, per
 * block type base hardness, resistance 10), {@code RockDisplayCategory.java} / {@code RockCategory.java}
 * (hardness modifier), {@code Ore.java} (ore hardness = category(6.5), resistance 10),
 * {@code common/blocks/TFCBlocks.java} (magma 0.5, light 6), {@code soil/SoilBlockType.java} (dirt 1.4,
 * clay 1.5), {@code soil/SandBlockType.java} (sand 0.5, map colours).
 * Map colours are vanilla {@code MapColor} ids.
 */
final class TfcTables {
    private TfcTables() {
    }

    /** Rock → (hardness modifier, map colour id). */
    record RockInfo(String modifier, int mapColor) {
        BigDecimal hardness(String base) {
            return new BigDecimal(base).add(new BigDecimal(modifier));
        }
    }

    private static final String IGNEOUS_EXTRUSIVE = "0";
    private static final String IGNEOUS_INTRUSIVE = "0.2";
    private static final String METAMORPHIC = "-0.2";
    private static final String SEDIMENTARY = "-0.4";

    static final Map<String, RockInfo> ROCKS = Map.ofEntries(
        Map.entry("granite", new RockInfo(IGNEOUS_INTRUSIVE, 60)),       // RAW_IRON
        Map.entry("diorite", new RockInfo(IGNEOUS_INTRUSIVE, 6)),        // METAL
        Map.entry("gabbro", new RockInfo(IGNEOUS_INTRUSIVE, 21)),        // COLOR_GRAY
        Map.entry("shale", new RockInfo(SEDIMENTARY, 21)),               // COLOR_GRAY
        Map.entry("claystone", new RockInfo(SEDIMENTARY, 40)),           // TERRACOTTA_YELLOW
        Map.entry("limestone", new RockInfo(SEDIMENTARY, 36)),           // TERRACOTTA_WHITE
        Map.entry("conglomerate", new RockInfo(SEDIMENTARY, 44)),        // TERRACOTTA_LIGHT_GRAY
        Map.entry("dolomite", new RockInfo(SEDIMENTARY, 21)),            // COLOR_GRAY
        Map.entry("chert", new RockInfo(SEDIMENTARY, 37)),               // TERRACOTTA_ORANGE
        Map.entry("chalk", new RockInfo(SEDIMENTARY, 14)),               // QUARTZ
        Map.entry("tuff", new RockInfo(SEDIMENTARY, 43)),                // TERRACOTTA_GRAY
        Map.entry("rhyolite", new RockInfo(IGNEOUS_EXTRUSIVE, 44)),      // TERRACOTTA_LIGHT_GRAY
        Map.entry("basalt", new RockInfo(IGNEOUS_EXTRUSIVE, 29)),        // COLOR_BLACK
        Map.entry("andesite", new RockInfo(IGNEOUS_EXTRUSIVE, 45)),      // TERRACOTTA_CYAN
        Map.entry("dacite", new RockInfo(IGNEOUS_EXTRUSIVE, 11)),        // STONE
        Map.entry("quartzite", new RockInfo(METAMORPHIC, 36)),           // TERRACOTTA_WHITE
        Map.entry("slate", new RockInfo(METAMORPHIC, 13)),               // WOOD
        Map.entry("phyllite", new RockInfo(METAMORPHIC, 39)),            // TERRACOTTA_LIGHT_BLUE
        Map.entry("schist", new RockInfo(METAMORPHIC, 41)),              // TERRACOTTA_LIGHT_GREEN
        Map.entry("gneiss", new RockInfo(METAMORPHIC, 44)),              // TERRACOTTA_LIGHT_GRAY
        Map.entry("marble", new RockInfo(METAMORPHIC, 3)));              // WOOL

    /** Rock.BlockType → base hardness before the category modifier (full-cube types only). */
    static final Map<String, String> ROCK_BLOCK_BASE_HARDNESS = Map.ofEntries(
        Map.entry("raw", "6.5"),
        Map.entry("hardened", "8"),
        Map.entry("smooth", "6.5"),
        Map.entry("cobble", "5.5"),
        Map.entry("bricks", "6.5"),
        Map.entry("gravel", "2.0"),
        Map.entry("cracked_bricks", "6.5"),
        Map.entry("mossy_bricks", "6.5"),
        Map.entry("mossy_cobble", "6.5"),
        Map.entry("chiseled", "8"));

    static final BigDecimal ROCK_RESISTANCE = new BigDecimal("10");
    static final String ORE_BASE_HARDNESS = "6.5";

    static final BigDecimal MAGMA_STRENGTH = new BigDecimal("0.5");
    static final int MAGMA_LIGHT = 6;
    static final int MAGMA_MAP_COLOR = 35; // NETHER

    static final int STONE_MAP_COLOR = 11;
    static final int DIRT_MAP_COLOR = 10;

    /** SoilBlockType → strength (hardness = resistance). */
    static final Map<String, BigDecimal> SOIL_STRENGTH = Map.of(
        "dirt", new BigDecimal("1.4"),
        "clay", new BigDecimal("1.5"));

    static final BigDecimal SAND_STRENGTH = new BigDecimal("0.5");

    /** SandBlockType → map colour id. */
    static final Map<String, Integer> SAND_MAP_COLOR = Map.of(
        "brown", 10,   // DIRT
        "white", 14,   // QUARTZ
        "black", 51,   // TERRACOTTA_BLACK
        "red", 50,     // TERRACOTTA_RED
        "yellow", 2,   // SAND
        "green", 27,   // COLOR_GREEN
        "pink", 42);   // TERRACOTTA_PINK
}
