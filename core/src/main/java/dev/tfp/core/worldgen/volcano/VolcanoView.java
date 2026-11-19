/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.volcano;

import dev.tfp.core.worldgen.tree.TreeView;

/**
 * What the volcano and hot spring decisions read of the world: the {@link TreeView} terrain (heightmaps, biome, block
 * classes) plus the rock of the rock layers at any position. Every answer is a pure function of the seed.
 */
public interface VolcanoView extends TreeView {
    /**
     * The rock index ({@code PlaceholderRocks.names()} order) of the rock layers at a position, whatever block the terrain
     * holds there: TFC's {@code RockData.getRock(x, y, z)}.
     */
    int rockAt(int x, int y, int z);

    /**
     * Whether the terrain block at a position is {@code minecraft:packed_ice}, the only block TFC's glacial springs start
     * in. The terrain holds none until the glacier builders place it, so the default is false.
     */
    default boolean packedIce(int x, int y, int z) {
        return false;
    }

    /** Whether the terrain block at a position is {@code minecraft:blue_ice}, the block of TFC's ice sheet springs. */
    default boolean blueIce(int x, int y, int z) {
        return false;
    }
}
