/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.surface;

/**
 * A configured surface builder: TFC's {@code SurfaceBuilder}, which writes the top layers of one column. Implementations are
 * immutable and thread-safe; the per-column state lives in the {@link SurfacePass}.
 */
public interface SurfaceBuilder {
    /**
     * Writes the surface of the column between {@code startY} (inclusive, the top) and {@code endY} (inclusive).
     *
     * @param pass   the column's state; the builder reads {@link SurfacePass#fill} and writes {@link SurfacePass#set}
     * @param startY the highest height to visit
     * @param endY   the lowest height to visit
     */
    void build(SurfacePass pass, int startY, int endY);
}
