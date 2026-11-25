/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/surface/SurfaceManager.java @ c3b4d9536 (the start height of a column), with
 * SurfaceBuilderContext.java (buildSurface: the lowest height of the pass)
 */
package dev.tfp.core.worldgen.surface;

import dev.tfp.core.worldgen.random.XoroshiroRandomSource;

/**
 * The surface of one block column: TFC's {@code SurfaceManager} and {@code SurfaceBuilderContext.buildSurface}, for one column.
 * The pass starts one block above the highest block of the column (the top water block, if there is water) and runs down to
 * twenty blocks below the ocean floor, as TFC does. Pure: the result depends only on the inputs and the random source.
 *
 * <p>The heights are TFC's: {@code ChunkAccess.getHeight} is the height of the top block (not the first free one), so the pass
 * starts at {@code top + 1}, the ocean floor is the top rock block, and the water of a column fills up to
 * {@code seaLevel - 1}.
 */
public final class ColumnSurface {
    /** TFC's {@code iterate down to at least the ocean floor and below}: the depth below the floor the pass reaches. */
    private static final int DEPTH_BELOW_FLOOR = 20;

    private final int endY;
    private final int baseY;
    private final SurfaceBlock[] blocks;

    private ColumnSurface(int endY, int baseY, SurfaceBlock[] blocks) {
        this.endY = endY;
        this.baseY = baseY;
        this.blocks = blocks;
    }

    /**
     * TFC's random source of a chunk's surface pass: {@code new XoroshiroRandomSource(chunkX * 1842639486192314L, chunkZ *
     * 579238196380231L)}. The columns of the chunk share it, in TFC's order: x outer, z inner.
     */
    public static XoroshiroRandomSource chunkRandom(int chunkX, int chunkZ) {
        return new XoroshiroRandomSource(chunkX * 1842639486192314L, chunkZ * 579238196380231L);
    }

    /** Builds the surface of a column for a builder that draws nothing from the chunk random. */
    public static ColumnSurface build(SurfaceBuilder builder, SurfaceColumn column, int topY, int minY) {
        return build(builder, column, topY, minY, null);
    }

    /**
     * Builds the surface of a column.
     *
     * @param builder the builder of the column's biome
     * @param column  the column's inputs
     * @param topY    the highest rock block of the column, the surface of the rock
     * @param minY    the lowest height of the world; the pass does not go below it
     * @param random  the chunk's random source ({@link #chunkRandom}); the lowlands and the icebergs draw from it
     */
    public static ColumnSurface build(SurfaceBuilder builder, SurfaceColumn column, int topY, int minY, XoroshiroRandomSource random) {
        return build(builder, column, topY, minY, random, null);
    }

    /**
     * Builds the surface of a column with air gaps below its top (PAR-WORLD-49): TFC's builders scan the whole column and
     * reset the surface depth at every air block, so the floors of caves, arches and overhangs get surface blocks too.
     *
     * @param open what an opened height at or below {@code topY} holds: {@link SurfacePass.Fill#AIR}, {@link
     *             SurfacePass.Fill#WATER} or {@link SurfacePass.Fill#OTHER}; null for a height that holds rock. Only
     *             the terrain density and the density caves count: the carvers run after the surface pass.
     */
    public static ColumnSurface build(SurfaceBuilder builder, SurfaceColumn column, int topY, int minY, XoroshiroRandomSource random,
                                      java.util.function.IntFunction<SurfacePass.Fill> open) {
        final int seaLevel = column.seaLevel();
        // getHeight(WORLD_SURFACE_WG) is the top block, water included; getHeight(OCEAN_FLOOR_WG) is the top rock block
        final int worldSurface = Math.max(topY, seaLevel - 1);
        final int oceanFloor = topY;
        final int startY = worldSurface + 1;
        final int endY = Math.max(minY, Math.min(startY, oceanFloor) - DEPTH_BELOW_FLOOR);
        final SurfacePass pass = new SurfacePass(column, topY, oceanFloor, Math.max(startY, endY), endY, minY, random, open);
        builder.build(pass, startY, endY);
        return new ColumnSurface(endY, pass.baseY(), pass.blocks());
    }

    /** @return The block written at {@code y}, or null where the surface leaves the block as it was (rock, water or air). */
    public SurfaceBlock block(int y) {
        final int i = y - baseY;
        return i < 0 || i >= blocks.length ? null : blocks[i];
    }

    /**
     * @return The coarse kind of the block written at {@code y}, or null where the surface leaves the block alone or the
     *     block has no coarse kind.
     */
    public SurfaceKind kind(int y) {
        final SurfaceBlock block = block(y);
        return block == null ? null : block.kind();
    }

    /** @return The lowest height the pass visited ({@code actualMinSurfaceHeight}). Some builders write below it. */
    public int endY() {
        return endY;
    }

    /** @return The lowest height that may hold a written block. */
    public int lowestY() {
        return baseY;
    }

    /** @return The highest height that may hold a written block (an iceberg rises above the water). */
    public int highestY() {
        return baseY + blocks.length - 1;
    }
}
