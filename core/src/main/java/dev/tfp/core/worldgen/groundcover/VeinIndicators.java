/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/vein/VeinFeature.java @ c3b4d9536 (place: the above-ground indicator
 * of each column, after the column's ores), with Indicator.java of the same package (getStateToGenerate).
 */
package dev.tfp.core.worldgen.groundcover;

import dev.tfp.core.worldgen.ore.ClusterVeinConfig;
import dev.tfp.core.worldgen.ore.Indicator;
import dev.tfp.core.worldgen.ore.OreVeins;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.List;
import java.util.function.IntSupplier;

/**
 * The surface indicators of the ore veins: the small ore pieces that TFC scatters on the ground above a vein. For every column
 * of a vein's box where the vein placed ore, one roll in the indicator's rarity picks a column up to 14 blocks away; when the
 * ground there is within the indicator's depth of the column's highest ore, the indicator block goes on the ocean floor. Pure
 * and thread-safe.
 *
 * <p>Divergences (ledger): each column rolls from a positional random of its vein type ({@code at(x, veinY, z)}) instead of the
 * vein's sequential random, so the roll comes before the ores of the column are known (the outcome is the same: a column
 * without ore places nothing); the highest ore is the highest block of the vein type in the column, whichever vein of that
 * type placed it; and the underground indicators are not placed (there are no caves).
 */
public final class VeinIndicators {
    private final OreVeins ores;
    private final FeatureRandom[] randoms;

    public VeinIndicators(OreVeins ores) {
        this.ores = ores;
        final List<ClusterVeinConfig> configs = ores.configs();
        this.randoms = new FeatureRandom[configs.size()];
        for (int i = 0; i < randoms.length; i++) {
            randoms[i] = new FeatureRandom(ores.levelSeed(), "tfc:vein/" + configs.get(i).name() + "/indicator");
        }
    }

    /**
     * The indicators inside chunk {@code (chunkX, chunkZ)}: those of the vein columns of the 3 x 3 chunks around it whose
     * indicator lands in it.
     *
     * @param rocks the rock index at a position, negative where there is no rock (it decides the ores of a column)
     */
    public void place(int chunkX, int chunkZ, GroundView view, OreVeins.RockAt rocks, List<Groundcover> out) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                final OreVeins.Chunk chunk = ores.chunk(chunkX + dx, chunkZ + dz);
                for (OreVeins.Box box : chunk.boxes()) {
                    final Indicator indicator = ores.configs().get(box.config()).indicator();
                    if (indicator == null || indicator.rarity() <= 0) {
                        continue;
                    }
                    for (int x = box.minX(); x <= box.maxX(); x++) {
                        for (int z = box.minZ(); z <= box.maxZ(); z++) {
                            final int columnX = x;
                            final int columnZ = z;
                            final Groundcover g = indicator(randoms[box.config()].at(x, box.vein().y(), z), indicator, x, z,
                                chunkX, chunkZ, () -> chunk.highestOre(box.config(), columnX, columnZ, rocks), view);
                            if (g != null) {
                                out.add(g);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * The above-ground indicator of one vein column, with TFC's draws in TFC's order: the rarity roll, the x offset (two
     * draws), the z offset (two draws), then, when the ground is within the depth, the weighted pick.
     *
     * @param chunkX    only an indicator landing in this chunk is decided further (and returned)
     * @param maxVeinY  the column's highest ore of the vein, or {@link Integer#MIN_VALUE} for none; asked only when needed
     * @return the indicator, or null
     */
    public static Groundcover indicator(XoroshiroRandomSource random, Indicator indicator, int x, int z, int chunkX, int chunkZ,
                                        IntSupplier maxVeinY, GroundView view) {
        return indicator(random, indicator, x, z, chunkX, chunkZ, false, maxVeinY, view);
    }

    /** TFC's indicator block for a column whose highest ore is {@code maxVeinY}, wherever the indicator lands. */
    public static Groundcover indicator(XoroshiroRandomSource random, Indicator indicator, int x, int z, int maxVeinY, GroundView view) {
        return indicator(random, indicator, x, z, 0, 0, true, () -> maxVeinY, view);
    }

    private static Groundcover indicator(XoroshiroRandomSource random, Indicator indicator, int x, int z, int chunkX, int chunkZ,
                                         boolean anyChunk, IntSupplier maxVeinY, GroundView view) {
        if (!(indicator.rarity() > 0 && random.nextInt(indicator.rarity()) == 0)) {
            return null;
        }
        // Pick a random position within the 3x3 chunk area
        final int indicatorX = x + random.nextInt(15) - random.nextInt(15);
        final int indicatorZ = z + random.nextInt(15) - random.nextInt(15);
        if (!anyChunk && (indicatorX >> 4 != chunkX || indicatorZ >> 4 != chunkZ)) {
            return null;
        }
        final int veinY = maxVeinY.getAsInt();
        if (veinY == Integer.MIN_VALUE) {
            return null;
        }
        final int indicatorY = view.oceanFloor(indicatorX, indicatorZ);
        if (Math.abs(indicatorY - veinY) < indicator.depth()) {
            final String block = indicator.blocks().get(random.nextDouble());
            // isWorldgenReplaceable (the ocean floor holds air or water) and canSurvive (a full block below)
            if (block != null) {
                return new Groundcover(indicatorX, indicatorY, indicatorZ, Groundcover.Kind.SMALL_ORE, block, 1,
                    view.water(indicatorX, indicatorY, indicatorZ));
            }
        }
        return null;
    }
}
