/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.surface;

/**
 * The block kind that a surface rule writes into a block column, in place of the rock layer. The kinds are the materials of
 * TFC's surface states with their soil variant dropped: every variant of grass is {@link #GRASS}, every variant of dirt is
 * {@link #DIRT}, and so on. The Paper side maps each kind to a block (rock to the CraftEngine rock palette, the rest to
 * vanilla until the TFC block set is wired).
 */
public enum SurfaceKind {
    /** The raw rock of the column's rock layer, TFC's {@code RAW}. It is the placeholder rock, so nothing changes. */
    ROCK_SURFACE,
    GRASS,
    DIRT,
    COARSE_DIRT,
    MUD,
    /** Rock gravel in TFC; the kind carries no rock, so Paper uses vanilla gravel until the TFC gravels exist. */
    GRAVEL,
    SAND,
    SANDSTONE,
    SNOW,
    PACKED_ICE
}
