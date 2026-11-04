/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.oreoverlay.census;

/**
 * A read-only view of one chunk plus a one-block border taken from its neighbours (local x and z in [-1, 16]), as cell codes of
 * {@link ExposureRule}. Built from chunk snapshots on the platform side; any thread once built.
 */
public interface ChunkView {
    /** Lowest block y of the world. */
    int minY();

    /** Number of block layers ({@code maxY - minY}). */
    int height();

    /**
     * The cell code at local {@code x, z} in [-1, 16] and {@code y} in [{@link #minY()}, {@code minY + height}). The census
     * treats cells below the world as {@link ExposureRule#SOLID} and above it as {@link ExposureRule#OPEN} without asking.
     */
    int cell(int x, int y, int z);

    /**
     * The lowest free y of the column at local {@code x, z} in [-1, 16]: one above the highest non-air block (the
     * {@code WORLD_SURFACE} heightmap), {@link #minY()} for an empty column.
     */
    int surfaceY(int x, int z);
}
