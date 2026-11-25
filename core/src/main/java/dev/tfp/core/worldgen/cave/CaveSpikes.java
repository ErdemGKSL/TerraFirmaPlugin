/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/cave/CaveSpikesFeature.java @ c3b4d9536
 *       src/main/java/net/dries007/tfc/world/feature/cave/LargeCaveSpikesFeature.java @ c3b4d9536
 */
package dev.tfp.core.worldgen.cave;

import dev.tfp.core.worldgen.random.XoroshiroRandomSource;

/**
 * PAR-WORLD-20: TFC's {@code tfc:cave_spike} and {@code tfc:large_cave_spike} features: rock spikes of the wall's rock
 * hanging from a cave ceiling or standing on its floor, with TFC's draws in TFC's order. Every TFC rock has a spike, so
 * {@code RockSettings.spike()} is always present. Pure apart from the {@link CaveLevel} it writes.
 */
public final class CaveSpikes {
    private CaveSpikes() {
    }

    /** {@code CaveSpikesFeature.place} ({@code large}: {@code LargeCaveSpikesFeature}). */
    public static void place(CaveLevel level, int x, int y, int z, XoroshiroRandomSource random, boolean large) {
        // The direction that the spike is pointed: +1 up, -1 down
        int dir = random.nextBoolean() ? 1 : -1;
        CaveCell wall = level.get(x, y - dir, z);
        if (wall.rawOrHardened()) {
            spike(level, x, y, z, dir, random, wall.rock(), large);
        } else {
            // Switch directions and try again (TFC reads pos.relative(direction) of the new direction: the same wall side)
            dir = -dir;
            wall = level.get(x, y + dir, z);
            if (wall.rawOrHardened()) {
                spike(level, x, y, z, dir, random, wall.rock(), large);
            }
        }
    }

    private static void spike(CaveLevel level, int x, int y, int z, int dir, XoroshiroRandomSource random, int rock, boolean large) {
        if (large) {
            large(level, x, y, z, dir, random, rock);
        } else {
            small(level, x, y, z, dir, random.nextFloat(), rock);
        }
    }

    /** {@code placeSmallSpike(level, pos, spike, raw, direction, sizeWeight)}. */
    static void small(CaveLevel level, int x, int y, int z, int dir, float sizeWeight, int rock) {
        // Replace the block above from raw -> hardened, if necessary
        if (level.get(x, y + 1, z).baseStone()) {
            level.set(x, y + 1, z, CaveCell.hardened(rock));
        }
        if (sizeWeight < 0.2f) {
            replace(level, x, y, z, rock, CaveCell.Part.MIDDLE);
            replace(level, x, y + dir, z, rock, CaveCell.Part.TIP);
        } else if (sizeWeight < 0.7f) {
            replace(level, x, y, z, rock, CaveCell.Part.BASE);
            replace(level, x, y + dir, z, rock, CaveCell.Part.MIDDLE);
            replace(level, x, y + 2 * dir, z, rock, CaveCell.Part.TIP);
        } else {
            replaceWithoutFluid(level, x, y, z, CaveCell.hardened(rock));
            replace(level, x, y + dir, z, rock, CaveCell.Part.BASE);
            replace(level, x, y + 2 * dir, z, rock, CaveCell.Part.MIDDLE);
            replace(level, x, y + 3 * dir, z, rock, CaveCell.Part.TIP);
        }
    }

    /** {@code LargeCaveSpikesFeature.place}: a cone of hardened rock with small spikes on its outside. */
    static void large(CaveLevel level, int px, int py, int pz, int dir, XoroshiroRandomSource random, int rock) {
        final CaveCell raw = CaveCell.hardened(rock);
        final int height = 6 + random.nextInt(11);
        final int radius = 2 + random.nextInt(1);
        int maxHeightReached = 0;
        for (int y = -3; y <= height; y++) {
            float radiusSquared = radius * (1 - 1.5f * Math.abs(y) / height);
            if (radiusSquared < 0) {
                continue;
            }
            radiusSquared *= radiusSquared;
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    final int bx = px + x;
                    final int by = py + y * dir;
                    final int bz = pz + z;
                    final float actualRadius = ((x * x) + (z * z)) / radiusSquared;
                    if (actualRadius < 0.7) {
                        replaceWithoutFluid(level, bx, by, bz, raw);
                        if (x == 0 && z == 0) {
                            maxHeightReached = y;
                        }
                    } else if (actualRadius < 0.85 && random.nextBoolean()) {
                        // Only fill in if continuing downwards
                        if (raw.equals(level.get(bx, by - dir, bz))) {
                            replaceWithoutFluid(level, bx, by, bz, raw);
                        }
                    } else if (actualRadius < 1 && random.nextInt(3) == 0 && y > 0) {
                        small(level, bx, by, bz, dir, random.nextFloat(), rock);
                    }
                }
            }
        }
        small(level, px, py + dir * (maxHeightReached - 1), pz, dir, 1.0f, rock);
    }

    /** {@code replaceBlock}: cave air or a fluid becomes the spike part, holding the fluid. */
    static void replace(CaveLevel level, int x, int y, int z, int rock, CaveCell.Part part) {
        final CaveCell at = level.get(x, y, z);
        if (at.kind() == CaveCell.Kind.CAVE_AIR) {
            level.set(x, y, z, CaveCell.spike(rock, part, CaveCell.Fluid.EMPTY));
        } else if (at.kind() == CaveCell.Kind.FLUID) {
            final CaveCell.Fluid fluid = at.fluid() == CaveCell.Fluid.RIVER_WATER ? CaveCell.Fluid.WATER : at.fluid();
            level.set(x, y, z, CaveCell.spike(rock, part, fluid));
        }
    }

    /** {@code replaceBlockWithoutFluid}: cave air or a fluid becomes {@code state}. */
    static void replaceWithoutFluid(CaveLevel level, int x, int y, int z, CaveCell state) {
        final CaveCell at = level.get(x, y, z);
        if (at.kind() == CaveCell.Kind.CAVE_AIR || at.kind() == CaveCell.Kind.FLUID) {
            level.set(x, y, z, state);
        }
    }
}
