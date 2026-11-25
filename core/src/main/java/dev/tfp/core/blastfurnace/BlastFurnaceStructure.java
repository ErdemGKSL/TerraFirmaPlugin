/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/BlastFurnaceBlock.java @ c3b4d9536 (BLAST_FURNACE_CHIMNEY,
 *       getChimneyLevels)
 */
package dev.tfp.core.blastfurnace;

import java.util.Objects;

/**
 * TFC's blast furnace chimney, pure. Offsets are relative to the blast furnace block; chimney level {@code i} (1 for the
 * first) is the layer {@code i} blocks above it. A level is formed when its centre is air or molten metal and the eight
 * blocks around the centre are blast furnace insulation ({@code #tfc:blast_furnace_insulation}).
 */
public final class BlastFurnaceStructure {
    /** The eight blocks around a chimney centre, TFC's {@code MultiBlock} order. */
    private static final int[][] RING = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}, {1, 1}, {1, -1}, {-1, -1}, {-1, 1}};

    private BlastFurnaceStructure() {
    }

    /** The world around the blast furnace block. */
    public interface World {
        /** Whether the block at the offset is in {@code #tfc:blast_furnace_insulation}. */
        boolean insulation(int dx, int dy, int dz);

        /** Whether the block at the offset is air or molten metal (the chimney centre). */
        boolean centre(int dx, int dy, int dz);
    }

    /** Whether chimney level {@code level} (1 or more) is formed. */
    public static boolean levelFormed(World world, int level) {
        Objects.requireNonNull(world, "world");
        if (level < 1) {
            throw new IllegalArgumentException("level must be >= 1: " + level);
        }
        if (!world.centre(0, level, 0)) {
            return false;
        }
        for (int[] d : RING) {
            if (!world.insulation(d[0], level, d[1])) {
                return false;
            }
        }
        return true;
    }

    /** TFC {@code getChimneyLevels}: the leading formed levels, at most {@code maxHeight}. Stops at the first gap. */
    public static int chimneyLevels(World world, int maxHeight) {
        for (int i = 0; i < maxHeight; i++) {
            if (!levelFormed(world, i + 1)) {
                return i;
            }
        }
        return maxHeight;
    }
}
