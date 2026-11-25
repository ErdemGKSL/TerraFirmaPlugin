/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/FloodFillLakeFeature.java @ c3b4d9536, with
 * src/main/java/net/dries007/tfc/world/feature/FloodFillLakeConfig.java (shouldReplace) and
 * src/main/resources/data/tfc/worldgen/configured_feature/{volcano_caldera,tuya_caldera,fuji_caldera,fuji_spring,
 * tahoma_caldera,tahoma_spring,kelimutu_lava,kelimutu_spring_water,kelimutu_water,wizard_island_lava,wizard_island_spring}.json
 * (all overfill; lava and spring water replace water, the Kelimutu water lake replaces no fluid)
 */
package dev.tfp.core.worldgen.volcano;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * TFC's flood fill lakes ({@code volcano_caldera} and the crater lakes of the tuyas and stratovolcanoes): a lake of lava,
 * spring water or water in a crater. From the crater floor it fills the layer
 * above, then overfills layer by layer while each layer stays bounded inside the chunk and 14 blocks around it, so the lava
 * rises to the crater's rim. Fewer than 20 blocks fill nothing. Pure: it reads and writes a {@link VolcanoLevel} only.
 */
public final class Caldera {
    /** {@code Direction.values()}: down, up, north, south, west, east. */
    private static final int[][] DIRECTIONS = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};

    private Caldera() {}

    /**
     * PAR-WORLD-64: {@code tfc:flood_fill_lake} ({@code state: minecraft:water}, {@code replace_fluids} empty, overfill) at
     * origin {@code (x, y, z)} over the terrain of a view, as the underground flood-fill lake places it: the filled water
     * blocks (and the dirt under a grass floor), or nothing when the lake is unbounded or below 20 blocks. No random draws.
     */
    public static List<VolcanicBlock> waterLake(VolcanoView view, int x, int y, int z) {
        final VolcanoLevel level = new VolcanoLevel(view);
        place(level, x, y, z, Config.WATER);
        return level.volcanicBlocks();
    }

    /**
     * {@code FloodFillLakeConfig} (every TFC config overfills).
     *
     * @param state        the fluid placed
     * @param replaceWater whether {@code replace_fluids} holds {@code minecraft:water}
     */
    record Config(VolcanicKind state, boolean replaceWater, boolean overfill) {
        static final Config LAVA = new Config(VolcanicKind.LAVA, true, true);
        static final Config SPRING_WATER = new Config(VolcanicKind.SPRING_WATER, true, true);
        static final Config WATER = new Config(VolcanicKind.WATER, false, true);
    }

    /** {@code volcano_caldera} at origin {@code (x, y, z)}. Returns whether lava was placed. */
    static boolean place(VolcanoLevel level, int x, int y, int z) {
        return place(level, x, y, z, Config.LAVA);
    }

    /** {@code FloodFillLakeFeature.place} at origin {@code (x, y, z)}. Returns whether the fluid was placed. */
    static boolean place(VolcanoLevel level, int x, int y, int z, Config config) {
        final int chunkX = x >> 4;
        final int chunkZ = z >> 4;
        // Leeway so we can check outside this box
        final Box box = new Box((chunkX << 4) - 14, (chunkZ << 4) - 14, (chunkX << 4) + 15 + 14, (chunkZ << 4) + 15 + 14);
        final Set<Long> filled = new LinkedHashSet<>();

        // First, make sure we're currently at the lowest point in the column
        int py = y;
        while (level.isAir(x, py, z) && py > 11) {
            py--;
        }

        // Initial placement is surface level, so start filling one block above
        if (floodFill(level, x, py + 1, z, box, filled, config)) {
            // Minimum size, don't fill awkward tiny lakes
            if (filled.size() >= 20) {
                for (long p : filled) {
                    final int fx = unpackX(p);
                    final int fy = unpackY(p);
                    final int fz = unpackZ(p);
                    level.set(fx, fy, fz, config.state());
                }
                for (long p : filled) {
                    // If we're at the bottom
                    final int fx = unpackX(p);
                    final int fy = unpackY(p) - 1;
                    final int fz = unpackZ(p);
                    if (!filled.contains(VolcanoLevel.key(fx, fy, fz)) && level.isGrass(fx, fy, fz)) {
                        level.set(fx, fy, fz, VolcanoKind.DIRT);
                    }
                }
                return true;
            }
        }
        return false;
    }

    private record Box(int minX, int minZ, int maxX, int maxZ) {
        boolean isInside(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }
    }

    private static boolean floodFill(VolcanoLevel level, int x, int y, int z, Box box, Set<Long> filled, Config config) {
        if (!floodFillLayer(level, x, y, z, box, filled, config)) {
            return false; // Failed the initial flood fill, exit early
        }
        if (!config.overfill()) {
            return true; // No overfilling, result is valid
        }
        // Initial result is valid, overfill upwards
        final Set<Long> nextFilled = new LinkedHashSet<>(filled);
        int startY = y + 1;
        final int prevSize = filled.size();
        while (floodFillLayer(level, x, startY, z, box, nextFilled, config)) {
            filled.addAll(nextFilled);
            if (prevSize == filled.size()) {
                // The last move upwards added no new filled area (TFC never updates prevSize)
                return true;
            }
            startY++;
        }
        return true;
    }

    private static boolean floodFillLayer(VolcanoLevel level, int x, int y, int z, Box box, Set<Long> filled, Config config) {
        // First check the start position, this must be fillable
        if (!isFloodFillable(level, x, y, z, config)) {
            return false;
        }
        final Deque<Long> queue = new ArrayDeque<>();
        final int maximumY = y;
        final long start = VolcanoLevel.key(x, y, z);
        filled.add(start);
        queue.addFirst(start);
        while (!queue.isEmpty()) {
            final long at = queue.removeFirst();
            final int ax = unpackX(at);
            final int ay = unpackY(at);
            final int az = unpackZ(at);
            for (int[] d : DIRECTIONS) {
                final int mx = ax + d[0];
                final int my = ay + d[1];
                final int mz = az + d[2];
                final long key = VolcanoLevel.key(mx, my, mz);
                if (!filled.contains(key) && my <= maximumY) {
                    if (isFloodFillable(level, mx, my, mz, config)) {
                        if (box.isInside(mx, mz)) {
                            // Valid flood fill location
                            queue.addFirst(key);
                            filled.add(key);
                        } else {
                            return false; // Invalid boundary condition (distance)
                        }
                    } else if (level.cancelsFloodFill(mx, my, mz)) {
                        return false; // Invalid boundary condition (border block)
                    }
                }
            }
        }
        return !filled.isEmpty();
    }

    /** Not solid, not leaves, and no fluid or one of {@code replace_fluids} ({@code minecraft:water} or none). */
    private static boolean isFloodFillable(VolcanoLevel level, int x, int y, int z, Config config) {
        if (level.isSolid(x, y, z) || level.isLeaves(x, y, z)) {
            return false;
        }
        final VolcanoLevel.Fluid fluid = level.fluid(x, y, z);
        return fluid == VolcanoLevel.Fluid.EMPTY || (fluid == VolcanoLevel.Fluid.WATER && config.replaceWater());
    }

    static int unpackX(long key) {
        return (int) (key >> 38);
    }

    static int unpackY(long key) {
        return (int) (key << 52 >> 52);
    }

    static int unpackZ(long key) {
        return (int) (key << 26 >> 38);
    }
}
