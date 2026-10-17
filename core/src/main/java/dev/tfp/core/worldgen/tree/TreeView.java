/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.tree;

import dev.tfp.core.worldgen.groundcover.GroundView;

/**
 * What the tree decisions read of the world, for any position: the {@link GroundView} heightmaps, biome and climate, plus the
 * terrain block class of a position and the forest type of a chunk. The terrain is the noise stage's, so every answer is a
 * pure function of the seed; trees placed by the forest are tracked by the decision itself, not here.
 */
public interface TreeView extends GroundView {
    /** The terrain block class at a position (air above the surface, water up to the sea level, the ground below). */
    TreeGround ground(int x, int y, int z);

    /** The forest type of chunk {@code (chunkX, chunkZ)} ({@code ChunkData.getForestType}). */
    ForestType forestType(int chunkX, int chunkZ);
}
