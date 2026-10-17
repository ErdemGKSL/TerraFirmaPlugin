/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.surface;

/**
 * A surface state: the block kind that one position of a column takes, given the column and the height. TFC's
 * {@code SurfaceState}, in kind space. Rules are pure and thread-safe.
 */
@FunctionalInterface
public interface SurfaceRule {
    /**
     * @param column The column, with its climate and slope.
     * @param y      The block height of the position, absolute.
     * @return The kind of the block at {@code (column.x(), y, column.z())}.
     */
    SurfaceKind kind(SurfaceColumn column, int y);
}
