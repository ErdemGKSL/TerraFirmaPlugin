/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/surface/SurfaceStates.java @ c3b4d9536 (the blocks the surface states name), with
 * SoilSurfaceState.java (the soil blocks by type and variant)
 */
package dev.tfp.core.worldgen.surface;

/**
 * A block that a surface builder writes, with TFC's full identity: the soil variant, the sand colour, the fixed rocks. The
 * blocks whose rock comes from the column's rock layer carry no rock ({@link #RAW}, {@link #COBBLE}, {@link #GRAVEL},
 * {@link #ROCK_SAND}, {@link #ROCK_SANDSTONE}, {@link #RIVER_SAND}): the writer resolves them with the rock at the position
 * (or at sea level for the river sand). {@link #kind()} is the coarse {@link SurfaceKind} of the block, which the first
 * terrain wiring maps to vanilla blocks.
 */
public enum SurfaceBlock {
    /** The raw rock at the position, TFC's {@code RAW}; the rock layer already holds it. */
    RAW(SurfaceKind.ROCK_SURFACE, true),
    /** The cobble of the rock at the position. */
    COBBLE(SurfaceKind.ROCK_SURFACE, false),
    /** The gravel of the rock at the position. */
    GRAVEL(SurfaceKind.GRAVEL, false),
    /** The sand colour of the rock at the position ({@code RockSettings.sand}). */
    ROCK_SAND(SurfaceKind.SAND, false),
    /** The sandstone colour of the rock at the position ({@code RockSettings.sandstone}). */
    ROCK_SANDSTONE(SurfaceKind.SANDSTONE, false),
    /** The sand colour of the rock at sea level, TFC's {@code RIVER_SAND}. */
    RIVER_SAND(SurfaceKind.SAND, false),

    BASALT(SurfaceKind.ROCK_SURFACE, true),
    BASALT_COBBLE(SurfaceKind.ROCK_SURFACE, false),
    BASALT_GRAVEL(SurfaceKind.GRAVEL, false),
    TUFF(SurfaceKind.ROCK_SURFACE, true),
    TUFF_GRAVEL(SurfaceKind.GRAVEL, false),

    SNOW(SurfaceKind.SNOW, false),
    PACKED_ICE(SurfaceKind.PACKED_ICE, false),
    BLUE_ICE(SurfaceKind.PACKED_ICE, false),
    /** TFC's sea ice block, on the salt water at sea level. It has no coarse kind: the first wiring leaves the water. */
    SEA_ICE(null, false),

    /** TFC's hardened clay, the {@code DRY_MUD} state. */
    HARDENED_CLAY(SurfaceKind.COARSE_DIRT, false),
    /** TFC's halite, the {@code SALTED_EARTH} state. */
    HALITE(SurfaceKind.ROCK_SURFACE, false),

    YELLOW_SAND(SurfaceKind.SAND, false),
    RED_SAND(SurfaceKind.SAND, false),
    BROWN_SAND(SurfaceKind.SAND, false),
    WHITE_SAND(SurfaceKind.SAND, false),
    BLACK_SAND(SurfaceKind.SAND, false),
    GREEN_SAND(SurfaceKind.SAND, false),
    PINK_SAND(SurfaceKind.SAND, false),
    YELLOW_SANDSTONE(SurfaceKind.SANDSTONE, false),
    RED_SANDSTONE(SurfaceKind.SANDSTONE, false),
    BROWN_SANDSTONE(SurfaceKind.SANDSTONE, false),
    WHITE_SANDSTONE(SurfaceKind.SANDSTONE, false),
    BLACK_SANDSTONE(SurfaceKind.SANDSTONE, false),
    GREEN_SANDSTONE(SurfaceKind.SANDSTONE, false),
    PINK_SANDSTONE(SurfaceKind.SANDSTONE, false),

    GRASS_ENTISOL(SurfaceKind.GRASS, false),
    GRASS_ARIDISOL(SurfaceKind.GRASS, false),
    GRASS_OXISOL(SurfaceKind.GRASS, false),
    GRASS_FLUVISOL(SurfaceKind.GRASS, false),
    GRASS_ANDISOL(SurfaceKind.GRASS, false),
    DIRT_ENTISOL(SurfaceKind.DIRT, false),
    DIRT_ARIDISOL(SurfaceKind.DIRT, false),
    DIRT_OXISOL(SurfaceKind.DIRT, false),
    DIRT_FLUVISOL(SurfaceKind.DIRT, false),
    DIRT_ANDISOL(SurfaceKind.DIRT, false),
    COARSE_DIRT_ARIDISOL(SurfaceKind.COARSE_DIRT, false),
    COARSE_DIRT_FLUVISOL(SurfaceKind.COARSE_DIRT, false),
    COARSE_DIRT_ANDISOL(SurfaceKind.COARSE_DIRT, false),
    MUD_ENTISOL(SurfaceKind.MUD, false),
    MUD_ARIDISOL(SurfaceKind.MUD, false),
    MUD_OXISOL(SurfaceKind.MUD, false),
    MUD_FLUVISOL(SurfaceKind.MUD, false);

    private final SurfaceKind kind;
    private final boolean rawRock;

    SurfaceBlock(SurfaceKind kind, boolean rawRock) {
        this.kind = kind;
        this.rawRock = rawRock;
    }

    /** @return The coarse kind of the block, or null where the coarse kinds have nothing for it. */
    public SurfaceKind kind() {
        return kind;
    }

    /**
     * @return Whether the block is the raw rock of a rock type: TFC's {@code isDefaultBlock}, the blocks a surface builder
     *     still treats as rock after it wrote them.
     */
    public boolean isRawRock() {
        return rawRock;
    }
}
