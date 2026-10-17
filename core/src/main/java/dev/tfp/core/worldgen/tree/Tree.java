/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.tree;

import java.util.List;

/**
 * A tree the forest decided to place: its root, wood, variant and every block it set, in TFC's order. The blocks are the ones
 * TFC's feature would set over the terrain and the forest's earlier trees (a template block on a log is already left out).
 * Immutable.
 *
 * @param x       the root x (the column the forest picked)
 * @param y       the root y: the first position above the column's top solid block ({@code OCEAN_FLOOR_WG})
 * @param z       the root z
 * @param wood    the tree's wood
 * @param variant which of the forest entry's features placed it
 * @param blocks  the blocks, relative to the root, in the order TFC sets them
 */
public record Tree(int x, int y, int z, Wood wood, Variant variant, List<TreeBlock> blocks) {
    /** Which feature of a forest entry a tree comes from. */
    public enum Variant { NORMAL, DEAD, OLD_GROWTH, KRUMMHOLZ }

    public Tree {
        blocks = List.copyOf(blocks);
    }

    /** Whether one of this tree's blocks lies in chunk {@code (chunkX, chunkZ)}. */
    public boolean inChunk(TreeBlock block, int chunkX, int chunkZ) {
        return (x + block.dx()) >> 4 == chunkX && (z + block.dz()) >> 4 == chunkZ;
    }
}
