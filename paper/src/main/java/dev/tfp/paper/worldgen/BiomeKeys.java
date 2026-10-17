/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.region.Units;
import java.util.List;
import java.util.function.IntBinaryOperator;

/**
 * Placeholder biome mapping for the Bukkit {@code BiomeProvider} (M2-GEN-07). The TFC biome ids of {@link TfcBiomes} map to
 * a vanilla biome by a coarse rule, because the TFC biomes are not datapack biomes yet. The seasonal variants of M2-SEA-02
 * replace this on the client. A documented divergence in the ledger.
 */
public final class BiomeKeys {
    /** The vanilla biome keys this mapping can return. */
    public static final List<String> VANILLA = List.of(
        "plains", "windswept_hills", "desert", "badlands", "snowy_plains", "ocean", "deep_ocean", "river");

    private BiomeKeys() {}

    /**
     * @param tfcId a TFC biome id, {@code 0 <= tfcId < TfcBiomes.COUNT}
     * @return the vanilla biome key that stands in for it
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
