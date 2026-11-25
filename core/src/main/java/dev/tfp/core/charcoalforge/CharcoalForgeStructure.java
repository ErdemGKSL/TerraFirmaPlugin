/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/CharcoalForgeBlock.java @ c3b4d9536 (FORGE_MULTIBLOCK,
 *       isValid),
 *       src/main/java/net/dries007/tfc/util/MultiBlock.java @ c3b4d9536 (match, matchEachDirection, matchHorizontal,
 *       matchOneOf)
 */
package dev.tfp.core.charcoalforge;

import java.util.Objects;

/**
 * TFC's {@code CharcoalForgeBlock.isValid}: the multiblock a charcoal forge needs to burn. Pure; the caller answers three
 * questions about the world around the forge, each relative to the forge block.
 *
 * <ol>
 *   <li>The block above is air or in {@code #tfc:charcoal_forge_invisible} (the crucible).</li>
 *   <li>A chimney: at least one of nine positions sees the sky, the block above the forge, its four horizontal
 *       neighbours and the four blocks two steps out (TFC {@code matchOneOf(origin.above(), ...)}).</li>
 *   <li>The four horizontal neighbours and the block below are in {@code #tfc:charcoal_forge_insulation}.</li>
 * </ol>
 */
public final class CharcoalForgeStructure {
    /** The horizontal unit offsets in TFC's order: north, east, south, west. */
    private static final int[][] HORIZONTAL = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    private CharcoalForgeStructure() {
    }

    /** The world around a forge, offsets relative to the forge block. Called from {@link #isValid} only. */
    public interface World {
        /** Whether the block above the forge is air or {@code #tfc:charcoal_forge_invisible}. */
        boolean topClear();

        /** TFC {@code canSeeSky(pos)} at the offset. */
        boolean canSeeSky(int dx, int dy, int dz);

        /** Whether the block at the offset is in {@code #tfc:charcoal_forge_insulation}. */
        boolean insulation(int dx, int dy, int dz);
    }

    /** TFC {@code CharcoalForgeBlock.isValid(level, pos)}. */
    public static boolean isValid(World world) {
        Objects.requireNonNull(world, "world");
        if (!world.topClear()) {
            return false;
        }
        if (!chimney(world)) {
            return false;
        }
        for (int[] h : HORIZONTAL) {
            if (!world.insulation(h[0], 0, h[1])) {
                return false;
            }
        }
        return world.insulation(0, -1, 0);
    }

    /** TFC's {@code matchOneOf(origin.above(), match(origin), matchHorizontal 1, matchHorizontal 2)}. */
    static boolean chimney(World world) {
        if (world.canSeeSky(0, 1, 0)) {
            return true;
        }
        for (int reach = 1; reach <= 2; reach++) {
            for (int[] h : HORIZONTAL) {
                if (world.canSeeSky(h[0] * reach, 1, h[1] * reach)) {
                    return true;
                }
            }
        }
        return false;
    }
}
