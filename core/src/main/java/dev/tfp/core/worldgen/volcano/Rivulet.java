/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/RivuletFeature.java @ c3b4d9536, with
 * src/main/java/net/dries007/tfc/world/feature/BlockStateMapConfig.java (getState) and
 * src/main/resources/data/tfc/worldgen/configured_feature/{volcano,tuya,ocean_ridge,stratovolcano,sulfur}_rivulet.json
 */
package dev.tfp.core.worldgen.volcano;

import dev.tfp.core.worldgen.chunk.PlaceholderRocks;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TFC's rivulets: a path down a slope from a start column (a main branch and side branches, each step to a lower or level
 * neighbour column), cut one block deep, with two blocks of magma (or sulfur ore) under the cut where the rock is one the
 * config replaces. Pure: it reads and writes a {@link VolcanoLevel} only.
 */
final class Rivulet {
    /** {@code Direction.Plane.HORIZONTAL}: north, east, south, west, as {@code (dx, dz)}. */
    private static final int[][] HORIZONTAL = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    private Rivulet() {}

    /**
     * {@code BlockStateMapConfig}: every TFC rivulet config lists the raw and the hardened block of each rock with the same
     * result.
     *
     * @param result the kind placed ({@link VolcanicKind#MAGMA} or {@link VolcanicKind#SULFUR_ORE})
     * @param with   by rock index, the rock of the block that replaces the raw or hardened rock, or -1
     */
    record Config(VolcanicKind result, int[] with) {
        /** {@code volcano_rivulet} and {@code tuya_rivulet}: basalt to basalt magma. */
        static final Config BASALT = magma("basalt", "basalt");
        /** {@code ocean_ridge_rivulet}: the seven igneous rocks to their magma. */
        static final Config OCEAN_RIDGE = magma("rhyolite", "rhyolite", "basalt", "basalt", "andesite", "andesite", "dacite", "dacite",
            "granite", "granite", "diorite", "diorite", "gabbro", "gabbro");
        /** {@code stratovolcano_rivulet}: the extrusive rocks to their magma, tuff to andesite magma. */
        static final Config STRATOVOLCANO = magma("andesite", "andesite", "dacite", "dacite", "basalt", "basalt", "rhyolite", "rhyolite",
            "tuff", "andesite");
        /** {@code sulfur_rivulet}: the extrusive rocks and tuff to their sulfur ore. */
        static final Config SULFUR = of(VolcanicKind.SULFUR_ORE, "andesite", "andesite", "dacite", "dacite", "basalt", "basalt",
            "rhyolite", "rhyolite", "tuff", "tuff");

        private static Config magma(String... pairs) {
            return of(VolcanicKind.MAGMA, pairs);
        }

        private static Config of(VolcanicKind result, String... pairs) {
            final List<String> names = PlaceholderRocks.names();
            final int[] with = new int[names.size()];
            Arrays.fill(with, -1);
            for (int i = 0; i < pairs.length; i += 2) {
                with[names.indexOf(pairs[i])] = names.indexOf(pairs[i + 1]);
            }
            return new Config(result, with);
        }

        /** The replaced rocks and their results by name, in rock order (for the golden tests). */
        Map<String, String> states() {
            final List<String> names = PlaceholderRocks.names();
            final Map<String, String> out = new LinkedHashMap<>();
            for (int i = 0; i < with.length; i++) {
                if (with[i] >= 0) {
                    out.put(names.get(i), names.get(with[i]));
                }
            }
            return out;
        }
    }

    /** A {@code BlockPos} with vanilla's {@code Vec3i.hashCode}, so a {@link HashSet} of them iterates in TFC's order. */
    private record P(int x, int y, int z) {
        @Override
        public int hashCode() {
            return (y + z * 31) * 31 + x;
        }
    }

    /** {@code RivuletFeature.place} from the column {@code (px, pz)}, draw for draw. Returns whether a rivulet was placed. */
    static boolean place(VolcanoLevel level, XoroshiroRandomSource rand, int px, int pz, Config config) {
        final VolcanoView view = level.view();
        // Leeway so we can check outside this box
        final int minX = (px >> 4 << 4) - 14;
        final int minZ = (pz >> 4 << 4) - 14;
        final int maxX = (px >> 4 << 4) + 15 + 14;
        final int maxZ = (pz >> 4 << 4) + 15 + 14;

        // Basic pathfinding down the slope
        final Set<P> chosen = new HashSet<>();
        final ArrayDeque<P> branches = new ArrayDeque<>();

        final P startPos = new P(px, view.oceanFloor(px, pz), pz);
        if (level.liquid(px, startPos.y - 1, pz)) {
            return false;
        }
        branches.add(startPos);

        boolean mainBranch = true;

        while (!branches.isEmpty()) {
            // Explore this branch
            final P branchStartPos = branches.removeFirst();
            int maxLength = 5 + rand.nextInt(8);
            if (mainBranch) {
                mainBranch = false;
                maxLength += 12;
            }

            P lastPos = branchStartPos;

            for (int i = 0; i < maxLength; i++) {
                chosen.add(branchStartPos);

                int chosenDirection = -1;
                int chosenHeight = 0;
                int possibleDirections = 0;
                for (int direction = 0; direction < 4; direction++) {
                    // Check positions in each direction
                    final int mx = lastPos.x + HORIZONTAL[direction][0];
                    final int mz = lastPos.z + HORIZONTAL[direction][1];
                    if (mx < minX || mx > maxX || mz < minZ || mz > maxZ || level.liquid(mx, lastPos.y - 1, mz)) {
                        continue; // Outside of the bounding box, skip!
                    }
                    final int height = view.oceanFloor(mx, mz);
                    if (height <= lastPos.y) {
                        // Check that the block below is solid
                        if (level.isSolid(mx, height - 1, mz)) {
                            // Valid next position
                            possibleDirections++;
                            if (chosenDirection == -1 || rand.nextInt(possibleDirections) == 0) {
                                chosenHeight = height;
                                chosenDirection = direction;
                            }
                        }
                    }
                }

                if (possibleDirections == 0) {
                    break; // Nowhere to go from here
                }
                if (possibleDirections > 1 && rand.nextInt(3) == 0) {
                    branches.add(lastPos); // Opportunity for a new branch
                }

                // One direction, so proceed
                final P next = new P(lastPos.x + HORIZONTAL[chosenDirection][0], chosenHeight, lastPos.z + HORIZONTAL[chosenDirection][1]);
                if (!chosen.contains(next) && !level.liquid(next.x, next.y - 1, next.z)) {
                    lastPos = next;
                    chosen.add(lastPos);
                } else {
                    break; // Already reached this position, skip
                }
            }
        }

        if (chosen.isEmpty()) {
            return false;
        }
        // We have found a path and can generate a magma rivulet
        for (P chosenPos : chosen) {
            // At each position, break the top block, and replace two blocks underneath with magma
            // The recorded positions are one above the topmost block due to how getHeight works
            int y = chosenPos.y;
            while (!level.isAirOrEmptyFluid(chosenPos.x, y, chosenPos.z)) {
                level.set(chosenPos.x, y, chosenPos.z, getReplaceState(level, chosenPos.x, y, chosenPos.z));
                y++;
            }
            y = chosenPos.y - 1;
            level.set(chosenPos.x, y, chosenPos.z, getReplaceState(level, chosenPos.x, y, chosenPos.z));
            y--;
            int rock = level.rawRock(chosenPos.x, y, chosenPos.z);
            if (rock < 0) {
                rock = level.hardenedRock(chosenPos.x, y, chosenPos.z);
            }
            if (rock >= 0 && config.with()[rock] >= 0) {
                level.set(chosenPos.x, y, chosenPos.z, config.result(), config.with()[rock], null);
                level.set(chosenPos.x, y - 1, chosenPos.z, config.result(), config.with()[rock], null);
            }
        }
        return true;
    }

    /** The fluid block of the first horizontal neighbour that holds a fluid, else air. */
    private static VolcanicKind getReplaceState(VolcanoLevel level, int x, int y, int z) {
        for (int[] d : HORIZONTAL) {
            final VolcanicKind fluid = level.fluidKind(x + d[0], y, z + d[1]);
            if (fluid != null) {
                return fluid;
            }
        }
        return VolcanicKind.AIR;
    }
}
