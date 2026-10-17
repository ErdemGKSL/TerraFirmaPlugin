/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.plant;

/**
 * One block a plant, wild crop or coral feature decided: its position and state. Immutable.
 *
 * @param x     the block x
 * @param y     the block y
 * @param z     the block z
 * @param state what goes there
 */
public record PlantBlock(int x, int y, int z, PlantState state) {
    /** Whether the block lies in the chunk {@code (chunkX, chunkZ)}. */
    public boolean inChunk(int chunkX, int chunkZ) {
        return x >> 4 == chunkX && z >> 4 == chunkZ;
    }
}
