/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/HotSpringFeature.java @ c3b4d9536, with
 * src/main/java/net/dries007/tfc/world/feature/HotSpringConfig.java (the defaults), src/main/java/net/dries007/tfc/util/Helpers.java
 * (fork, uniqueRandomSample, getRandomValue) and the configs
 * src/main/resources/data/tfc/worldgen/configured_feature/{,empty_,lava_,sapphire_,emerald_,sapphire_empty_,emerald_empty_}hot_spring.json
 */
package dev.tfp.core.worldgen.volcano;

import dev.tfp.core.worldgen.Mth;
import dev.tfp.core.worldgen.chunk.PlaceholderRocks;
import dev.tfp.core.worldgen.deposit.Metaballs2D;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * TFC's hot springs: a metaball-shaped pool of a fluid (spring water, air for the empty springs, lava under the oceans) one
 * block deep in the surface, edged and bedded with hardened rock and gravel, with a few fissures of the same fluid below it.
 * Pure: it reads and writes a {@link VolcanoLevel} only.
 */
final class HotSpring {
    /** {@code TFCChunkGenerator.SEA_LEVEL_Y}. */
    private static final int SEA_LEVEL_Y = 63;
    /**
     * The igneous rocks of the lava spring's {@code replaces_on_fluid_contact}, in the config's order. TFC picks one of the
     * entries with {@code Helpers.getRandomValue} over a {@code HashMap} keyed by blocks (identity hash order, which varies
     * between runs); this port indexes the config's order (ledger).
     */
    private static final int[] MAGMA_ROCKS = rocks("rhyolite", "basalt", "andesite", "dacite", "granite", "diorite", "gabbro");

    private HotSpring() {}

    private static int[] rocks(String... names) {
        final int[] out = new int[names.length];
        for (int i = 0; i < names.length; i++) {
            out[i] = PlaceholderRocks.names().indexOf(names[i]);
        }
        return out;
    }

    /**
     * {@code HotSpringConfig} (the wall state is unused by the feature).
     *
     * @param fluid           air, spring water or lava
     * @param magma           whether the config has the lava spring's {@code replaces_on_fluid_contact}
     */
    record Config(Fissure.State fluid, int radius, Fissure.Decoration decoration, boolean allowUnderwater, boolean magma) {
        static final Config HOT_SPRING = new Config(Fissure.State.SPRING_WATER, 14, null, false, false);
        static final Config EMPTY = new Config(Fissure.State.AIR, 14, null, false, false);
        static final Config SAPPHIRE = new Config(Fissure.State.SPRING_WATER, 14, new Fissure.Decoration(Gem.SAPPHIRE, 3, 5, 5), false, false);
        static final Config EMERALD = new Config(Fissure.State.SPRING_WATER, 14, new Fissure.Decoration(Gem.EMERALD, 3, 5, 5), false, false);
        static final Config SAPPHIRE_EMPTY = new Config(Fissure.State.AIR, 14, new Fissure.Decoration(Gem.SAPPHIRE, 3, 5, 2), false, false);
        static final Config EMERALD_EMPTY = new Config(Fissure.State.AIR, 14, new Fissure.Decoration(Gem.EMERALD, 3, 5, 2), false, false);
        static final Config LAVA = new Config(Fissure.State.LAVA, 10, null, true, true);
    }

    /** {@code HotSpringFeature.place} at origin {@code (px, py, pz)}, draw for draw. Returns whether a pool was placed. */
    static boolean place(VolcanoLevel level, XoroshiroRandomSource random, int px, int py, int pz, Config config) {
        final VolcanoView view = level.view();
        final Metaballs2D noise = Metaballs2D.simple(new XoroshiroRandomSource(random.nextLong(), random.nextLong()), config.radius());
        final VolcanoLevel.Pos mutablePos = new VolcanoLevel.Pos();
        final int rock = view.rockAt(px, 0, pz);
        final Fissure.State rockState = new Fissure.State(VolcanoKind.HARDENED_ROCK, rock);
        final Fissure.State gravelState = new Fissure.State(VolcanoKind.GRAVEL, rock);
        final boolean hasFluid = config.fluid().kind() != VolcanoKind.AIR;

        final boolean useFilledEmptyCheck = !hasFluid;
        final Set<Long> filledEmptyPositions = new HashSet<>();
        final List<int[]> fissureStartPositions = new ArrayList<>();
        // The replacer of the hardened rock: never a key of the map (its keys are raw rocks), so always a random entry
        final int magmaRock = config.magma() ? MAGMA_ROCKS[random.nextInt(MAGMA_ROCKS.length)] : -1;

        boolean touchedWater = false;

        for (int x = -config.radius(); x <= config.radius(); x++) {
            for (int z = -config.radius(); z <= config.radius(); z++) {
                final int localX = px + x;
                final int localZ = pz + z;
                int y = view.oceanFloor(localX, localZ) - 1;

                // Disallow underwater locations
                if ((!config.allowUnderwater() && y <= SEA_LEVEL_Y) || !noise.inside(x, z)) {
                    continue;
                }

                mutablePos.set(localX, y, localZ);

                // On sea ice, either disallow placement or move the y downwards (the terrain has no sea ice)
                if (level.isColdOceanBlock(mutablePos.x, mutablePos.y, mutablePos.z)) {
                    if (config.allowUnderwater()) {
                        mutablePos.y = 50;
                        while (level.isAirOrEmptyFluid(mutablePos.x, mutablePos.y, mutablePos.z)) {
                            mutablePos.move(0, -1, 0);
                        }
                        y = mutablePos.y;
                    } else {
                        continue;
                    }
                }

                mutablePos.move(0, 1, 0);
                if (!isEmptyBlock(config, level, mutablePos.x, mutablePos.y, mutablePos.z)) {
                    if (level.canBeReplaced(mutablePos.x, mutablePos.y, mutablePos.z)) {
                        // TFC replaces the block with its own fluid's block: for the fluids of this level, the same block
                        mutablePos.move(0, 1, 0);
                    } else {
                        continue; // Solid, non-replaceable block above. So don't replace with hot spring blocks
                    }
                }

                boolean edge = false;

                // Similar to the loop below, except water above the position also matters
                mutablePos.set(localX, y, localZ).move(0, 1, 0);
                if (level.liquid(mutablePos.x, mutablePos.y, mutablePos.z)) {
                    touchedWater = true;
                }

                for (int[] d : HORIZONTAL) {
                    mutablePos.set(localX, y, localZ).move(d[0], 0, d[1]);
                    if (level.liquid(mutablePos.x, mutablePos.y, mutablePos.z)) {
                        touchedWater = true;
                    }
                    if (isEmptyBlock(config, level, mutablePos.x, mutablePos.y, mutablePos.z)
                        && (!useFilledEmptyCheck || !filledEmptyPositions.contains(VolcanoLevel.key(mutablePos.x, mutablePos.y, mutablePos.z)))) {
                        edge = true;
                        break;
                    }
                }

                // surface depth is deeper near the center of the hot spring. Range: [0.3, 1.0]
                final float centerFactor = 1 - 0.7f * Mth.clamp(2 * (x * x + z * z) / (float) (config.radius() * config.radius()), 0, 1);
                final int surfaceDepth = (int) ((8 + random.nextInt(3)) * centerFactor);
                if (edge) {
                    final int startY = random.nextInt(12) == 0 ? -1 : 0; // Creates holes which allow the water to flow, rarely
                    if (startY == -1) {
                        mutablePos.set(localX, y, localZ);
                        level.set(mutablePos.x, mutablePos.y, mutablePos.z, VolcanoKind.AIR);
                    }
                } else {
                    mutablePos.set(localX, y, localZ);
                    fissureStartPositions.add(mutablePos.immutable());

                    // if we are touching water and allow water, use magma, else place the fluid
                    if (touchedWater && config.allowUnderwater() && magmaRock >= 0) {
                        // IWeighted.get: magma (weight 1) below 1 of 3, else hardened rock (weight 2)
                        final boolean magma = random.nextDouble() * 3 < 1;
                        level.set(mutablePos.x, mutablePos.y, mutablePos.z, magma ? VolcanoKind.MAGMA : VolcanoKind.HARDENED_ROCK, magmaRock, null);
                    } else {
                        level.set(mutablePos.x, mutablePos.y, mutablePos.z, config.fluid().kind());
                    }
                    if (useFilledEmptyCheck) {
                        filledEmptyPositions.add(VolcanoLevel.key(mutablePos.x, mutablePos.y, mutablePos.z));
                    }

                    mutablePos.set(localX, y - 1, localZ);
                    setFissureBaseBlock(config, level, mutablePos, gravelState);
                }

                for (int dy = edge ? 0 : -2; dy >= -surfaceDepth; dy--) {
                    mutablePos.set(localX, y + dy, localZ);
                    if (!setFissureBaseBlock(config, level, mutablePos, rockState)) {
                        break;
                    }
                }
            }
        }

        if (fissureStartPositions.isEmpty()) {
            return false;
        }

        final int fissureStarts = 1 + random.nextInt(1 + random.nextInt(Math.clamp(fissureStartPositions.size(), 1, 7)));
        final List<int[]> selected = uniqueRandomSample(fissureStartPositions, fissureStarts, random);
        final int[] pos = {px, py, pz};
        for (int[] start : selected) {
            Fissure.placeFissure(level, start, pos, mutablePos, random, config.fluid(), rockState, 10, 22, 6, 16, 12, config.decoration());
        }
        return true;
    }

    /** The horizontal directions in vanilla's order (north, east, south, west), as {@code (dx, dz)}. */
    private static final int[][] HORIZONTAL = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    private static boolean setFissureBaseBlock(Config config, VolcanoLevel level, VolcanoLevel.Pos pos, Fissure.State state) {
        if (isEmptyBlock(config, level, pos.x, pos.y, pos.z)) {
            return false;
        }
        level.set(pos.x, pos.y, pos.z, state.kind(), state.rock(), null);
        return true;
    }

    private static boolean isEmptyBlock(Config config, VolcanoLevel level, int x, int y, int z) {
        return config.allowUnderwater() ? level.isAirOrEmptyFluid(x, y, z) : level.isAir(x, y, z);
    }

    /** {@code Helpers.uniqueRandomSample}: shuffles the tail of the list in place and returns it. */
    static <T> List<T> uniqueRandomSample(List<T> list, int n, XoroshiroRandomSource r) {
        final int length = list.size();
        if (length < n) {
            throw new IllegalArgumentException("Cannot select n=" + n + " from a list of size = " + length);
        }
        for (int i = length - 1; i >= length - n; --i) {
            Collections.swap(list, i, r.nextInt(i + 1));
        }
        return list.subList(length - n, length);
    }
}
