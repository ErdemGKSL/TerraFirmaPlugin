/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/LooseRockFeature.java @ c3b4d9536 (place), with the data of
 * src/main/resources/data/tfc/worldgen/{configured_feature,placed_feature}/underground_loose_rocks.json, the tag
 * data/tfc/tags/worldgen/placed_feature/in_biome/underground_decoration.json (listed by every TFC biome) and the vanilla
 * 1.21 placement steps it names (rarity_filter, count, height_range uniform, environment_scan, random_offset).
 */
package dev.tfp.core.worldgen.groundcover;

import dev.tfp.core.worldgen.chunk.PlaceholderRocks;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * TFC's {@code tfc:underground_loose_rocks}: loose rocks on cave floors. One chunk in 20 tries 25 heights in [-32, 59] of its
 * lowest corner column (the placed feature has no {@code in_square}), scans down through air at most 12 blocks to a solid
 * block and puts a loose rock of the column's surface rock on it. Every position is in the chunk's own corner column, so the
 * feature reads only the generating chunk ({@link CaveView}). Pure and thread-safe.
 */
public final class UndergroundLooseRocks {
    /** The placed feature id, which seeds the chunk random. */
    public static final String FEATURE = "tfc:underground_loose_rocks";
    static final int RARITY = 20;
    static final int COUNT = 25;
    static final int MIN_Y = -32;
    static final int MAX_Y = 59;
    /** {@code environment_scan max_steps}. */
    static final int MAX_STEPS = 12;

    /** The blocks of the generating chunk as the terrain and carvers left them, asked only inside the chunk. */
    public interface CaveView {
        /** {@code matching_blocks minecraft:air} (cave air and fluids are not). */
        boolean air(int x, int y, int z);

        /** {@code BlockPredicate.solid()}: the block's legacy solid flag. */
        boolean solid(int x, int y, int z);

        /** Whether the block is in {@code #minecraft:ice}, where a loose rock does not generate. */
        boolean ice(int x, int y, int z);
    }

    private final FeatureRandom random;

    public UndergroundLooseRocks(long levelSeed) {
        this.random = new FeatureRandom(levelSeed, FEATURE);
    }

    /** The cave floor loose rocks of chunk {@code (chunkX, chunkZ)}, added to {@code out} in TFC's order. */
    public void place(int chunkX, int chunkZ, GroundView view, CaveView cave, List<Groundcover> out) {
        place(random.chunk(chunkX, chunkZ), chunkX << 4, chunkZ << 4, view, cave, out);
    }

    /**
     * One chunk's feature with TFC's draws in TFC's order: the rarity float, then per count the uniform height
     * ({@code nextInt(92) - 32}), the scan and offset (no draws) and {@code LooseRockFeature.place} (the mossy coin when the
     * rainfall is above 250, the count when the rock is placed). Vanilla's placement stream runs each position through every
     * step and the feature before drawing the next height.
     */
    public static void place(XoroshiroRandomSource random, int blockX, int blockZ, GroundView view, CaveView cave, List<Groundcover> out) {
        if (!(random.nextFloat() < 1f / RARITY)) {
            return;
        }
        final Set<Long> placed = new HashSet<>();
        for (int i = 0; i < COUNT; i++) {
            final int startY = random.nextInt(MAX_Y - MIN_Y + 1) + MIN_Y;
            final int floor = scan(blockX, startY, blockZ, view.minY(), cave, placed);
            if (floor == Integer.MIN_VALUE) {
                continue;
            }
            // random_offset: xz 0, y +1, all constants (no draws)
            final int y = floor + 1;
            final int rock = view.surfaceRock(blockX, blockZ);
            // the block there is air (the scan came down through it), so its fluid is empty and the mossy coin is drawn
            final boolean mossy = view.averageRainfall(blockX, blockZ) > LooseRocks.MOSSY_RAINFALL && random.nextBoolean();
            // isWorldgenReplaceable (a rock placed earlier is not), canSurvive (the floor is solid), canGenerateOn (not ice)
            final long key = LooseRocks.key(blockX, y, blockZ);
            if (placed.contains(key) || cave.ice(blockX, floor, blockZ)) {
                continue;
            }
            placed.add(key);
            final String name = PlaceholderRocks.names().get(rock);
            out.add(new Groundcover(blockX, y, blockZ, mossy ? Groundcover.Kind.MOSSY_LOOSE_ROCK : Groundcover.Kind.LOOSE_ROCK,
                (mossy ? "tfp:rock/mossy_loose/" : "tfp:rock/loose/") + name, 1 + random.nextInt(2), false));
        }
    }

    /**
     * Vanilla's {@code EnvironmentScanPlacement} down from {@code y}: the start must be air; then up to 12 times, stop at a
     * solid block or move down, stopping when the new block is not air; finally the block reached must be solid. A loose rock
     * placed earlier is neither air nor solid (no collision). Below {@code minY} the scan fails.
     *
     * @return the y of the solid block found, or {@link Integer#MIN_VALUE}
     */
    static int scan(int x, int y, int z, int minY, CaveView cave, Set<Long> placed) {
        if (!air(x, y, z, cave, placed)) {
            return Integer.MIN_VALUE;
        }
        int cursor = y;
        for (int i = 0; i < MAX_STEPS; i++) {
            if (solid(x, cursor, z, cave, placed)) {
                return cursor;
            }
            cursor--;
            if (cursor < minY) {
                return Integer.MIN_VALUE;
            }
            if (!air(x, cursor, z, cave, placed)) {
                break;
            }
        }
        return solid(x, cursor, z, cave, placed) ? cursor : Integer.MIN_VALUE;
    }

    private static boolean air(int x, int y, int z, CaveView cave, Set<Long> placed) {
        return !placed.contains(LooseRocks.key(x, y, z)) && cave.air(x, y, z);
    }

    private static boolean solid(int x, int y, int z, CaveView cave, Set<Long> placed) {
        return !placed.contains(LooseRocks.key(x, y, z)) && cave.solid(x, y, z);
    }
}
