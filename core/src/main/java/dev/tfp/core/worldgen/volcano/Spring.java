/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/SpringFeature.java @ c3b4d9536, with
 * src/main/resources/data/tfc/worldgen/configured_feature/{water_spring,lava_spring,water_surface_spring,
 * extra_water_surface_spring,lava_surface_spring,glacial_spring,ice_sheet_spring}.json, the vanilla 1.21
 * SpringConfiguration defaults (requires_block_below true, rock_count 4, hole_count 1) and
 * src/main/java/net/dries007/tfc/world/settings/RockLayerSettings.java (getHardened)
 */
package dev.tfp.core.worldgen.volcano;

import dev.tfp.core.worldgen.chunk.PlaceholderRocks;

/**
 * TFC's springs: a fluid source in a wall of raw rock (or glacier ice), where the block above and below are that rock, four
 * of the six neighbours are and one is air. The raw rock above the source turns into hardened rock. Pure: it reads and
 * writes a {@link VolcanoLevel} only, and draws nothing.
 */
final class Spring {
    /** {@code Helpers.DIRECTIONS}: down, up, north, south, west, east. */
    private static final int[][] DIRECTIONS = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};

    private Spring() {}

    /** The {@code valid_blocks} of a spring config. */
    enum Valid {
        /** Every raw rock ({@code tfc:rock/raw/<rock>}). */
        RAW_ROCK,
        /** {@code minecraft:packed_ice}. */
        PACKED_ICE,
        /** {@code minecraft:blue_ice}. */
        BLUE_ICE;

        boolean test(VolcanoLevel level, int x, int y, int z) {
            return switch (this) {
                case RAW_ROCK -> level.rawRock(x, y, z) >= 0;
                case PACKED_ICE -> level.isPackedIce(x, y, z);
                case BLUE_ICE -> level.isBlueIce(x, y, z);
            };
        }

        /** The number of blocks in the config's list. */
        int size() {
            return this == RAW_ROCK ? PlaceholderRocks.names().size() : 1;
        }
    }

    /** {@code SpringConfiguration}: the fluid source placed and the blocks it springs from. */
    record Config(VolcanicKind state, Valid validBlocks, boolean requiresBlockBelow, int rockCount, int holeCount) {
        static final Config WATER = new Config(VolcanicKind.WATER, Valid.RAW_ROCK, true, 4, 1);
        static final Config LAVA = new Config(VolcanicKind.LAVA, Valid.RAW_ROCK, true, 4, 1);
        static final Config GLACIAL = new Config(VolcanicKind.WATER, Valid.PACKED_ICE, true, 4, 1);
        static final Config ICE_SHEET = new Config(VolcanicKind.WATER, Valid.BLUE_ICE, true, 4, 1);
    }

    /** {@code SpringFeature.place} at {@code (x, y, z)}. Returns whether a spring was placed. */
    static boolean place(VolcanoLevel level, int x, int y, int z, Config config) {
        final Valid valid = config.validBlocks();
        if (!valid.test(level, x, y + 1, z)) {
            return false;
        }
        if (config.requiresBlockBelow() && !valid.test(level, x, y - 1, z)) {
            return false;
        }
        if (!level.isAir(x, y, z) && !valid.test(level, x, y, z)) {
            return false;
        }
        int rockCount = 0;
        int holeCount = 0;
        for (int[] d : DIRECTIONS) {
            if (valid.test(level, x + d[0], y + d[1], z + d[2])) {
                rockCount++;
            }
            if (level.isAir(x + d[0], y + d[1], z + d[2])) {
                holeCount++;
            }
        }
        if (rockCount != config.rockCount() || holeCount != config.holeCount()) {
            return false;
        }
        // Read before the fluid is set, as the position above is another block
        final int rockAbove = level.rawRock(x, y + 1, z);
        level.set(x, y, z, config.state());
        if (rockAbove >= 0) {
            level.set(x, y + 1, z, VolcanicKind.HARDENED_ROCK, rockAbove, null);
        }
        return true;
    }
}
