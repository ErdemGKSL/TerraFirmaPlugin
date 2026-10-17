/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/LooseRockFeature.java @ c3b4d9536 (place), with the data of
 * src/main/resources/data/tfc/worldgen/configured_feature/surface_loose_rocks.json and placed_feature/surface_loose_rocks.json,
 * placed_feature/loose_rock.json, and the vanilla 1.21 steps they name (in_square, random_patch, heightmap OCEAN_FLOOR_WG).
 */
package dev.tfp.core.worldgen.groundcover;

import dev.tfp.core.worldgen.chunk.PlaceholderRocks;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * TFC's surface loose rocks ({@code tfc:surface_loose_rocks}, in every biome's top layer modification): once per chunk, a
 * patch of 8 tries within 7 blocks of a random column, each try on the column's ocean floor. A try places a loose rock of the
 * column's surface rock when the position is air or water and the ground is not ice; with more than 250 mm of rain, half the
 * dry rocks are mossy; the count is 1 or 2. Pure and thread-safe.
 *
 * <p>A patch reaches at most 7 blocks into the next chunk, so a chunk's rocks come from the patches of the 3 x 3 chunks
 * around it; only the positions inside the chunk are returned. Each patch only sees its own rocks as occupied (ledger).
 */
public final class LooseRocks {
    /** The placed feature id, which seeds the patch random. */
    public static final String FEATURE = "tfc:surface_loose_rocks";
    static final int TRIES = 8;
    static final int XZ_SPREAD = 7;
    static final int Y_SPREAD = 1;
    /** {@code data.getAverageRainfall(pos) > 250f}: above this, a dry rock is mossy with a coin flip. */
    static final float MOSSY_RAINFALL = 250f;

    private final FeatureRandom random;

    public LooseRocks(long levelSeed) {
        this.random = new FeatureRandom(levelSeed, FEATURE);
    }

    /** The loose rocks inside chunk {@code (chunkX, chunkZ)}, added to {@code out} in patch order. */
    public void place(int chunkX, int chunkZ, GroundView view, List<Groundcover> out) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                final int cx = chunkX + dx;
                final int cz = chunkZ + dz;
                patch(random.chunk(cx, cz), cx << 4, cz << 4, view, rock -> {
                    if (rock.inChunk(chunkX, chunkZ)) {
                        out.add(rock);
                    }
                });
            }
        }
    }

    /**
     * One chunk's patch, with TFC's draws in TFC's order: {@code in_square} (x, then z), then per try the
     * {@code random_patch} offsets (x, y, z; the y offset is drawn and dropped, as the heightmap sets the y), then
     * {@code LooseRockFeature.place}: the mossy coin when the rainfall is above 250, and the count when the rock is placed.
     *
     * @param blockX the chunk's lowest block x
     * @param blockZ the chunk's lowest block z
     * @param out    receives every rock of the patch, inside the chunk or not
     */
    public static void patch(XoroshiroRandomSource random, int blockX, int blockZ, GroundView view, Consumer<Groundcover> out) {
        final int originX = blockX + random.nextInt(16);
        final int originZ = blockZ + random.nextInt(16);
        final Set<Long> placed = new HashSet<>();
        final int xz = XZ_SPREAD + 1;
        final int dy = Y_SPREAD + 1;
        for (int i = 0; i < TRIES; i++) {
            final int x = originX + random.nextInt(xz) - random.nextInt(xz);
            random.nextInt(dy);
            random.nextInt(dy);
            final int z = originZ + random.nextInt(xz) - random.nextInt(xz);
            final int y = view.oceanFloor(x, z);
            if (y <= view.minY()) {
                continue;
            }
            final Groundcover rock = place(random, x, y, z, view, placed);
            if (rock != null) {
                out.accept(rock);
            }
        }
    }

    /** {@code LooseRockFeature.place} at a position on the ocean floor; {@code placed} holds the patch's earlier rocks. */
    private static Groundcover place(XoroshiroRandomSource random, int x, int y, int z, GroundView view, Set<Long> placed) {
        final int rock = view.surfaceRock(x, z);
        final long key = key(x, y, z);
        final boolean occupied = placed.contains(key);
        // The fluid of the block there: water, or the water a rock placed earlier was filled with
        final boolean fluid = view.water(x, y, z);
        final boolean mossy = view.averageRainfall(x, z) > MOSSY_RAINFALL && random.nextBoolean() && !fluid;
        // isWorldgenReplaceable: air or a fluid block; canSurvive: the floor below is a full block; canGenerateOn: not ice
        if (!occupied && !view.onIce(x, z)) {
            placed.add(key);
            final String name = PlaceholderRocks.names().get(rock);
            return new Groundcover(x, y, z, mossy ? Groundcover.Kind.MOSSY_LOOSE_ROCK : Groundcover.Kind.LOOSE_ROCK,
                (mossy ? "tfp:rock/mossy_loose/" : "tfp:rock/loose/") + name, 1 + random.nextInt(2), fluid);
        }
        return null;
    }

    static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | ((long) y & 0xFFFL);
    }
}
