/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/resources/data/tfc/worldgen/placed_feature/flood_fill_lake.json @ c3b4d9536
 *       src/main/resources/data/tfc/worldgen/configured_feature/flood_fill_lake.json @ c3b4d9536
 *       src/main/resources/data/tfc/tags/worldgen/placed_feature/feature/surface_flood_fill_lakes.json @ c3b4d9536
 *       src/main/resources/data/tfc/tags/worldgen/placed_feature/in_biome/{all_lakes,large_features/*}.json @ c3b4d9536
 *       and Minecraft 1.21's RarityFilter, InSquarePlacement, HeightmapPlacement (WORLD_SURFACE_WG)
 */
package dev.tfp.core.worldgen.lake;

import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.groundcover.ClimateFilter;
import dev.tfp.core.worldgen.groundcover.FeatureRandom;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import dev.tfp.core.worldgen.volcano.Caldera;
import dev.tfp.core.worldgen.volcano.VolcanicBlock;
import dev.tfp.core.worldgen.volcano.VolcanoView;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * PAR-WORLD-67: TFC's surface {@code tfc:flood_fill_lake} ({@code #tfc:feature/surface_flood_fill_lakes}, the last of the
 * large features of every biome but the burren and shilin karsts): {@code rarity_filter} 5, {@code in_square}, the
 * {@code WORLD_SURFACE_WG} height, {@code tfc:climate} (temperature at least -17, groundwater at least 125, not fuzzy), then
 * the water flood-fill lake ({@link Caldera#waterLake}): from the column's top block it fills the natural depression above,
 * layer by layer, while it stays bounded within the chunk and 14 blocks around; at least 20 blocks, and a grass floor turns
 * into its dirt. TFC freezes nothing at generation ({@code tfc:ice_and_snow} places nothing), so no ice. Thread-safe.
 */
public final class SurfaceLakes {
    /** The placed feature id (shared with {@code in_biome/all_lakes}). */
    public static final String FEATURE = "tfc:flood_fill_lake";
    static final int RARITY = 5;
    static final ClimateFilter CLIMATE = ClimateFilter.of(-17f, Float.POSITIVE_INFINITY, 125f, Float.POSITIVE_INFINITY);
    /** The biomes whose {@code large_features} do not list the lakes (directly or through {@code all_lakes}). */
    static final Set<String> WITHOUT = Set.of("burren_badlands", "burren_badlands_tall", "burren_plains", "burren_plateau",
        "burren_roche_moutonee", "shilin_canyons", "shilin_highlands", "shilin_hills", "shilin_plains", "shilin_plateau");

    private final FeatureRandom random;

    public SurfaceLakes(long levelSeed) {
        this.random = new FeatureRandom(levelSeed, FEATURE);
    }

    /** Whether a TFC biome id lists the feature in its large features. */
    public static boolean placesIn(int biome) {
        return biome >= 0 && biome < TfcBiomes.COUNT && !WITHOUT.contains(TfcBiomes.name(biome).toLowerCase(Locale.ROOT));
    }

    /**
     * The lake of origin chunk {@code (chunkX, chunkZ)} when its biome (at the chunk's centre) lists it: its blocks (water,
     * then the dirt under a grass floor), or an empty list.
     */
    public List<VolcanicBlock> place(int chunkX, int chunkZ, VolcanoView view) {
        if (!placesIn(view.biome((chunkX << 4) + 8, (chunkZ << 4) + 8))) {
            return List.of();
        }
        return placed(random.chunk(chunkX, chunkZ), chunkX << 4, chunkZ << 4, view);
    }

    /** One chunk's placed feature with TFC's draws in TFC's order. */
    public static List<VolcanicBlock> placed(XoroshiroRandomSource random, int blockX, int blockZ, VolcanoView view) {
        if (!(random.nextFloat() < 1f / RARITY)) {
            return List.of();
        }
        final int x = random.nextInt(16) + blockX;
        final int z = random.nextInt(16) + blockZ;
        final int y = view.worldSurface(x, z);
        // HeightmapPlacement: only above the bottom of the world
        if (y <= view.minY() || !CLIMATE.isValid(view, x, y, z, random)) {
            return List.of();
        }
        return Caldera.waterLake(view, x, y, z);
    }
}
