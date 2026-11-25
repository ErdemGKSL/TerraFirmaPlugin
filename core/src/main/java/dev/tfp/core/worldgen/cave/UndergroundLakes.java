/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/resources/data/tfc/worldgen/placed_feature/underground_flood_fill_lake.json @ c3b4d9536
 *       src/main/resources/data/tfc/worldgen/configured_feature/flood_fill_lake.json @ c3b4d9536
 *       src/main/resources/data/tfc/tags/worldgen/placed_feature/in_biome/{underground_lakes,all_lakes}.json @ c3b4d9536
 *       and Minecraft 1.21's RarityFilter, InSquarePlacement, HeightRangePlacement (UniformHeight)
 */
package dev.tfp.core.worldgen.cave;

import dev.tfp.core.worldgen.groundcover.FeatureRandom;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import dev.tfp.core.worldgen.volcano.Caldera;
import dev.tfp.core.worldgen.volcano.VolcanicBlock;
import dev.tfp.core.worldgen.volcano.VolcanoView;
import java.util.List;

/**
 * PAR-WORLD-64: TFC's {@code tfc:underground_flood_fill_lake} (every biome, through {@code in_biome/all_lakes} or
 * {@code in_biome/underground_lakes}): {@code rarity_filter} 3, {@code in_square}, a uniform height of -56 to 63, then the
 * water flood-fill lake ({@link Caldera#waterLake}) from there: it sinks to the cave floor (above y 11) and fills the cave
 * pocket, layer by layer, when it stays bounded within the chunk and 14 blocks around. Thread-safe.
 */
public final class UndergroundLakes {
    public static final String FEATURE = "tfc:underground_flood_fill_lake";
    static final int RARITY = 3;
    static final int MIN_Y = -56;
    static final int MAX_Y = 63;

    private final FeatureRandom random;

    public UndergroundLakes(long levelSeed) {
        this.random = new FeatureRandom(levelSeed, FEATURE);
    }

    /** The lake of origin chunk {@code (chunkX, chunkZ)}: its blocks, or an empty list. */
    public List<VolcanicBlock> place(int chunkX, int chunkZ, VolcanoView view) {
        return placed(random.chunk(chunkX, chunkZ), chunkX << 4, chunkZ << 4, view);
    }

    /** One chunk's placed feature with TFC's draws in TFC's order. */
    public static List<VolcanicBlock> placed(XoroshiroRandomSource random, int blockX, int blockZ, VolcanoView view) {
        if (!(random.nextFloat() < 1f / RARITY)) {
            return List.of();
        }
        final int x = random.nextInt(16) + blockX;
        final int z = random.nextInt(16) + blockZ;
        final int y = random.nextInt(MAX_Y - MIN_Y + 1) + MIN_Y;
        return Caldera.waterLake(view, x, y, z);
    }
}
