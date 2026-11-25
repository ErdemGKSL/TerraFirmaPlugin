/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/ErosionFeature.java @ c3b4d9536
 */
package dev.tfp.core.worldgen.erosion;

/**
 * PAR-WORLD-20: TFC's {@code tfc:erosion} pass (the {@code in_biome/erosion} tag, every biome): per column of the chunk, top
 * down from the world-surface height, a run of landslide-prone blocks above a block they could fall through is either
 * supported (its lowest block becomes the hardened rock of the rock layer there) or, when it is one block thick with air
 * above, replaced by the aquifer's air or fluid; and every raw rock directly above a fragile block becomes hardened. So the
 * generated terrain does not collapse on its first update. No random draws; reads and writes only this chunk's columns.
 */
public final class Erosion {
    /** The placed feature of the {@code in_biome/erosion} tag. */
    public static final String FEATURE = "tfc:erosion";

    private Erosion() {
    }

    /** One column of the generating chunk ({@code ChunkAccess} and the generator's rock data and aquifer). */
    public interface Column {
        /** {@code BlockState.isAir()}. */
        boolean air(int y);

        /** Whether a {@code LandslideRecipe} matches the block (a landslide-prone soil, sand, gravel, cobble). */
        boolean landslides(int y);

        /** {@code TFCFallingBlockEntity.canFallThrough}: air, fluids and the blocks a falling block breaks or passes. */
        boolean fragile(int y);

        /** {@code RockLayerSettings.getHardened(block)}: the rock index whose hardened block this block hardens to, or -1. */
        int hardenable(int y);

        /** {@code RockData.getRock(x, y, z)}: the rock index of the rock layer at {@code y}. */
        int rockAt(int y);

        /** Writes the hardened block of {@code rock} at {@code y}. */
        void setHardened(int y, int rock);

        /**
         * {@code aquifer.computeSubstance(point, -1)} at {@code y}: writes the air or fluid the aquifer has there and returns
         * true, or returns false (and writes nothing) when the aquifer gives no substance (a barrier).
         */
        boolean setSubstance(int y);

        /** Whether {@link #setSubstance} would write something at {@code y} (the aquifer's state is computed first). */
        boolean hasSubstance(int y);
    }

    /** {@code ErosionFeature.place} for one column, from {@code baseHeight} ({@code WORLD_SURFACE_WG}) down to {@code minY}. */
    public static void column(Column c, int baseHeight, int minY) {
        boolean prevBlockCanLandslide = false;
        int lastSafeY = baseHeight;
        int prevBlockHardened = -1;
        for (int y = baseHeight; y >= minY; y--) {
            final boolean air = c.air(y);
            final boolean recipe = !air && c.landslides(y);
            final boolean stateAtIsFragile = air || c.fragile(y);
            if (prevBlockCanLandslide) {
                if (!recipe) {
                    // This block is sturdy: the column stops here, unless it can collapse through it
                    if (stateAtIsFragile) {
                        if (lastSafeY > y + 2) {
                            // More than one block to collapse, so support it instead
                            c.setHardened(y + 1, c.rockAt(y + 1));
                        } else {
                            // Delete the block above when the one above that is air, else support it
                            final boolean blockAboveIsAir = c.air(y + 2);
                            if (blockAboveIsAir && c.hasSubstance(y + 1)) {
                                c.setSubstance(y + 1);
                            } else {
                                c.setHardened(y + 1, c.rockAt(y + 1));
                            }
                        }
                    }
                    prevBlockCanLandslide = false;
                    lastSafeY = y;
                }
            } else {
                if (!recipe) {
                    lastSafeY = y;
                } else {
                    prevBlockCanLandslide = true;
                }
            }
            // Update stone from raw -> hardened
            if (stateAtIsFragile) {
                if (prevBlockHardened >= 0) {
                    c.setHardened(y + 1, prevBlockHardened);
                }
                prevBlockHardened = -1;
            } else {
                prevBlockHardened = c.hardenable(y);
            }
        }
    }
}
