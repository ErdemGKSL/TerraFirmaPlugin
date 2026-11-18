/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.region.Units;
import java.util.List;
import java.util.Locale;
import java.util.function.IntBinaryOperator;
import java.util.stream.IntStream;

/**
 * The biomes of the Bukkit {@code BiomeProvider} (M2-GEN-07, PAR-WORLD-02). Every TFC biome id of {@link TfcBiomes} is a
 * datapack base biome {@code tfp:<name>} (datagen {@code SeasonalBiomes}, in the bootstrap datapack), so F3 and the server
 * name the TFC biome. The seasonal colour variants ({@code tfp:seasonal/<name>/t<T>r<R>}) are only sent to clients by the
 * packet layer.
 *
 * <p>The vanilla mapping ({@link #VANILLA}, {@link #vanillaKey}) is only the fallback for a server whose registry lacks the
 * datapack (the bootstrap datapack not enabled yet): the terrain then stands in a coarse vanilla biome and logs it.
 */
public final class BiomeKeys {
    /** Our datapack namespace of the base biomes. */
    public static final String NAMESPACE = "tfp";

    /** The base biome paths by TFC biome id: the TFC name in lower case ({@code plains}, {@code deep_ocean}, ...). */
    public static final List<String> BASE = IntStream.range(0, TfcBiomes.COUNT)
        .mapToObj(id -> TfcBiomes.name(id).toLowerCase(Locale.ROOT)).toList();

    /** The vanilla biome keys the fallback mapping can return. */
    public static final List<String> VANILLA = List.of(
        "plains", "windswept_hills", "desert", "badlands", "snowy_plains", "ocean", "deep_ocean", "river");

    private BiomeKeys() {}

    /**
     * @param tfcId a TFC biome id, {@code 0 <= tfcId < TfcBiomes.COUNT}
     * @return the path of its datapack base biome, e.g. {@code plains}
     * @throws IllegalArgumentException for an id outside the TFC range
     */
    public static String basePath(int tfcId) {
        if (tfcId < 0 || tfcId >= TfcBiomes.COUNT) {
            throw new IllegalArgumentException("TFC biome id out of range: " + tfcId);
        }
        return BASE.get(tfcId);
    }

    /** The registry id of a TFC biome's base biome, e.g. {@code tfp:plains}. */
    public static String baseId(int tfcId) {
        return NAMESPACE + ":" + basePath(tfcId);
    }

    /** The registry id of a TFC biome's vanilla fallback, e.g. {@code minecraft:plains}. */
    public static String fallbackId(int tfcId) {
        return "minecraft:" + vanillaKey(tfcId);
    }

    /**
     * @param tfcId a TFC biome id, {@code 0 <= tfcId < TfcBiomes.COUNT}
     * @return the vanilla biome key that stands in for it when the datapack base biome is missing
     * @throws IllegalArgumentException for an id outside the TFC range
     */
    public static String vanillaKey(int tfcId) {
        if (tfcId < 0 || tfcId >= TfcBiomes.COUNT) {
            throw new IllegalArgumentException("TFC biome id out of range: " + tfcId);
        }
        if (TfcBiomes.isOcean(tfcId)) {
            return name(tfcId).contains("DEEP") ? "deep_ocean" : "ocean";
        }
        if (TfcBiomes.isLake(tfcId)) {
            return "river";
        }
        final String name = name(tfcId);
        if (TfcBiomes.isFlatIceSheet(tfcId) || name.startsWith("ICE_SHEET") || name.startsWith("GLACIA")) {
            return "snowy_plains";
        }
        if (name.contains("DUNE") || name.contains("SALT_FLATS")) {
            return "desert";
        }
        if (name.contains("BADLANDS") || name.contains("MESA") || name.contains("BUTTE") || name.contains("HOODOO")
                || name.contains("CANYON")) {
            return "badlands";
        }
        if (name.contains("MOUNTAIN") || name.contains("HIGHLAND") || name.contains("HILL") || name.contains("PLATEAU")
                || name.contains("VOLCANIC") || name.contains("SHIELD") || name.contains("KARST") || name.contains("TOWER")) {
            return "windswept_hills";
        }
        return "plains";
    }

    /**
     * The TFC biome id at a block position: the region point at the position's grid cell (nearest grid point, not TFC's
     * biome layers, which are M2-GEN-06). Negative coordinates floor, as {@link Units#blockToGrid} does.
     *
     * @param gridBiome the biome id of a grid point, by its grid coordinates
     */
    public static int biomeIdAt(IntBinaryOperator gridBiome, int x, int z) {
        return gridBiome.applyAsInt(Units.blockToGrid(x), Units.blockToGrid(z));
    }

    private static String name(int tfcId) {
        return TfcBiomes.name(tfcId);
    }
}
