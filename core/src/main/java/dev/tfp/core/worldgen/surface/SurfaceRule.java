/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.surface;

/**
 * A surface state: the block that one position of a column takes, given the column and the height. TFC's
 * {@code SurfaceState}. Rules are pure and thread-safe.
 */
@FunctionalInterface
public interface SurfaceRule {
    /**
     * @param column The column, with its climate.
     * @param y      The block height of the position, absolute.
     * @return The block at {@code (column.x(), y, column.z())}.
     */
    SurfaceBlock block(SurfaceColumn column, int y);
}
