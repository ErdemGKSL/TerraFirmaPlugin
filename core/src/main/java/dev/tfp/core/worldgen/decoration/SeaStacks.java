/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/SeaStacksFeature.java @ c3b4d9536
 *       src/main/resources/data/tfc/worldgen/{placed_feature,configured_feature}/{sea_stacks_patch,sea_stacks}.json @ c3b4d9536
 *       src/generated/resources/data/tfc/tags/block/sea_stack_rocks.json @ c3b4d9536
 *       src/main/java/net/dries007/tfc/util/Helpers.java @ c3b4d9536 (fork)
 *       and Minecraft 1.21's RarityFilter, InSquarePlacement, HeightmapPlacement, BlockPredicateFilter, RandomPatchFeature
 */
package dev.tfp.core.worldgen.decoration;

import dev.tfp.core.worldgen.deposit.Metaballs2D;
import dev.tfp.core.worldgen.groundcover.FeatureRandom;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * PAR-WORLD-65: TFC's {@code tfc:sea_stacks_patch} (the large feature of the {@code sea_stacks} biome), with TFC's draws in
 * TFC's order. Placement: {@code rarity_filter} 120, {@code in_square}, {@code OCEAN_FLOOR_WG}; then a {@code random_patch} of
 * 6 tries (xz spread 8, y spread 2), each placing {@code tfc:sea_stacks} at the ocean floor of its column when that is
 * {@code tfc:replaceable}. A stack: under water at or below the sea level, of the column's surface rock's hardened rock when
 * that is in {@code #tfc:sea_stack_rocks}; a metaball column 5..14 high above an inverted foot. Thread-safe.
 */
public final class SeaStacks {
    public static final String ID = "tfc:sea_stacks_patch";
    /** {@code #tfc:sea_stack_rocks}: the rocks whose hardened rock makes a stack. */
    public static final List<String> SEA_STACK_ROCKS = List.of("basalt", "limestone", "marble", "rhyolite");

    private static final int RARITY = 120;
    private static final int TRIES = 6;
    private static final int XZ_SPREAD = 8;
    private static final int Y_SPREAD = 2;

    private final FeatureRandom random;
    private final IntPredicate seaStackRock;

    /** @param seaStackRock whether a rock index is in {@link #SEA_STACK_ROCKS} */
    public SeaStacks(long levelSeed, IntPredicate seaStackRock) {
        this.random = new FeatureRandom(levelSeed, ID);
        this.seaStackRock = seaStackRock;
    }

    /** The patch of origin chunk {@code (chunkX, chunkZ)} into the level. */
    public void place(int chunkX, int chunkZ, DecorationLevel level) {
        placed(random.chunk(chunkX, chunkZ), chunkX << 4, chunkZ << 4, level, seaStackRock);
    }

    /** One chunk's placed patch with TFC's draws in TFC's order. */
    public static void placed(XoroshiroRandomSource random, int blockX, int blockZ, DecorationLevel level, IntPredicate seaStackRock) {
        if (!(random.nextFloat() < 1f / RARITY)) {
            return;
        }
        final int ox = random.nextInt(16) + blockX;
        final int oz = random.nextInt(16) + blockZ;
        final int oy = level.oceanFloor(ox, oz);
        if (oy <= level.terrain().minY()) {
            return;
        }
        final int xz = XZ_SPREAD + 1;
        final int yy = Y_SPREAD + 1;
        for (int i = 0; i < TRIES; i++) {
            final int x = ox + random.nextInt(xz) - random.nextInt(xz);
            random.nextInt(yy);
            random.nextInt(yy);
            final int z = oz + random.nextInt(xz) - random.nextInt(xz);
            // the placed tfc:sea_stacks: OCEAN_FLOOR_WG (the patch's y offset is dropped), then tfc:replaceable
            final int y = level.oceanFloor(x, z);
            if (y > level.terrain().minY() && level.worldgenReplaceable(x, y, z)) {
                feature(random, level, x, y, z, seaStackRock);
            }
        }
    }

    /** {@code SeaStacksFeature.place}. */
    public static boolean feature(XoroshiroRandomSource random, DecorationLevel level, int x, int y, int z, IntPredicate seaStackRock) {
        if (!level.fluid(x, y, z) || y > level.terrain().seaLevel()) {
            return false;
        }
        final int rock = level.terrain().surfaceRock(x, z);
        if (!seaStackRock.test(rock)) {
            return false;
        }
        final DecorationLevel.Block block = DecorationLevel.Block.rock(DecorationLevel.Kind.HARDENED, rock);
        final int upOffset = TidePools.nextInt(random, 2, 6);
        final int radius = TidePools.nextInt(random, 3, 6);
        final int height = TidePools.nextInt(random, 5, 15);
        stack(level, x, y + upOffset, z, block, radius, height, random, false);
        stack(level, x, y + upOffset - 1, z, block, radius / 2, upOffset, random, true);
        return true;
    }

    private static void stack(DecorationLevel level, int ox, int oy, int oz, DecorationLevel.Block block, int radius, int height,
                              XoroshiroRandomSource random, boolean inverted) {
        final List<Long> accepted = new ArrayList<>();
        if (inverted) {
            for (int y = -height; y <= 0; y++) {
                layer(level, ox, oy, oz, block, radius, height, random, y, accepted);
            }
        } else {
            for (int y = height - 1; y >= 0; y--) {
                layer(level, ox, oy, oz, block, radius, height, random, y, accepted);
            }
        }
    }

    private static void layer(DecorationLevel level, int ox, int oy, int oz, DecorationLevel.Block block, int radius, int height,
                              XoroshiroRandomSource random, int y, List<Long> accepted) {
        final int actualRadius = (int) (radius * Math.abs(height - y) / (float) height);
        // Helpers.fork: a new XoroshiroRandomSource of two longs
        final Metaballs2D noise = Metaballs2D.simple(new XoroshiroRandomSource(random.nextLong(), random.nextLong()), actualRadius);
        for (int x = ox - radius; x <= ox + radius; ++x) {
            for (int z = oz - radius; z <= oz + radius; ++z) {
                final long code = (long) x << 32 | (long) z & 0xFFFFFFFFL;
                final boolean inList = accepted.contains(code);
                if ((noise.inside(x - ox, z - oz) || inList) && level.canBeReplaced(x, oy + y, z)) {
                    level.set(x, oy + y, z, block);
                    if (!inList) {
                        accepted.add(code);
                    }
                    if (actualRadius == 1 && random.nextFloat() < 0.4f) {
                        return;
                    }
                }
            }
        }
    }
}
