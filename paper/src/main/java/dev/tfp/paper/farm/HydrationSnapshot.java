/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/soil/FarmlandBlock.java @ c3b4d9536 (findMinCostWater, isSourceBlockPresent,
 *       getHydrationMultiplier)
 */
package dev.tfp.paper.farm;

import dev.tfp.core.farm.FarmlandRules;
import java.util.function.Predicate;
import org.bukkit.block.Block;

/**
 * What the water search of one farmland reads from the world, captured on the region thread (the 9 x 2 x 9 columns of
 * {@code findMinCostWater}, the fluid above the farmland and the soil below), so the decision itself ({@link #minCostWater})
 * is pure and may run on any thread ("decide async, apply sync").
 *
 * @param hydrating    {@code [dx + 4][dy + 1][dz + 4]}: the block there holds a hydrating fluid source, dy in {-1, 0}
 * @param waterloggedAbove the block above holds a hydrating fluid
 * @param soil         the soil block's hydration class
 */
public record HydrationSnapshot(boolean[][][] hydrating, boolean waterloggedAbove, FarmlandRules.SoilHydration soil) {
    private static final int SPAN = 2 * FarmlandRules.SOURCE_RADIUS + 1;

    /**
     * Captures the snapshot around {@code farmland}. Region thread; blocks of unloaded or foreign chunks read as dry (TFC's
     * {@code isAreaLoaded} gate).
     *
     * @param hydratingBlock whether a block is a hydrating fluid ({@code #tfc:hydrating}), at least vanilla water
     * @param soilOf         the soil class of the block under the farmland
     */
    public static HydrationSnapshot capture(Block farmland, Predicate<Block> hydratingBlock,
            java.util.function.Function<Block, FarmlandRules.SoilHydration> soilOf) {
        boolean[][][] grid = new boolean[SPAN][2][SPAN];
        for (int dx = -FarmlandRules.SOURCE_RADIUS; dx <= FarmlandRules.SOURCE_RADIUS; dx++) {
            for (int dz = -FarmlandRules.SOURCE_RADIUS; dz <= FarmlandRules.SOURCE_RADIUS; dz++) {
                int x = farmland.getX() + dx;
                int z = farmland.getZ() + dz;
                if (!farmland.getWorld().isChunkLoaded(x >> 4, z >> 4)) {
                    continue;
                }
                for (int dy = -1; dy <= 0; dy++) {
                    grid[dx + FarmlandRules.SOURCE_RADIUS][dy + 1][dz + FarmlandRules.SOURCE_RADIUS] =
                        hydratingBlock.test(farmland.getWorld().getBlockAt(x, farmland.getY() + dy, z));
                }
            }
        }
        return new HydrationSnapshot(grid, hydratingBlock.test(farmland.getRelative(org.bukkit.block.BlockFace.UP)),
            soilOf.apply(farmland.getRelative(org.bukkit.block.BlockFace.DOWN)));
    }

    /** {@code findMinCostWater} over the snapshot. Pure. */
    public int minCostWater() {
        return FarmlandRules.minCostWater((dx, dy, dz) -> hydrating[dx + FarmlandRules.SOURCE_RADIUS][dy + 1][dz + FarmlandRules.SOURCE_RADIUS]);
    }
}
