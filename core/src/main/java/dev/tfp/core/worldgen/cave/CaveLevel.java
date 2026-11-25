/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.cave;

import dev.tfp.core.worldgen.groundcover.GroundView;

/**
 * PAR-WORLD-20: the blocks a cave feature reads and writes ({@code WorldGenLevel} of the feature step). A position outside
 * what the level holds reads as {@link CaveCell#OTHER} and ignores writes. Implementations are single-threaded (one chunk on
 * one worldgen worker).
 */
public interface CaveLevel {
    CaveCell get(int x, int y, int z);

    void set(int x, int y, int z, CaveCell cell);

    /** {@code ChunkData.getRockData().getRock(pos)}: the rock index of the rock layer at a position. */
    int rockAt(int x, int y, int z);

    /** Whether the cave carvers carved the position to air ({@code CarvingMask} of {@code GenerationStep.Carving.AIR}). */
    boolean carved(int x, int y, int z);

    /** The column climate and the world's heights, for the {@code tfc:climate} placements. */
    GroundView ground();

    /** The world's top (exclusive). */
    int maxY();
}
