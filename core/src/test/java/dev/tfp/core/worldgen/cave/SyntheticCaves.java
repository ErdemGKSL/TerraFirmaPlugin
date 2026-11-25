/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.cave;

import dev.tfp.core.worldgen.groundcover.GroundView;
import java.util.HashMap;
import java.util.Map;

/**
 * PAR-WORLD-20: the synthetic cave world of the oracle (2 x 2 x 2 cells hashed into kinds, rock by y and x) with a log of
 * every write in order, in the oracle's text form. Must equal {@code CaveOracle.world} of the scratch oracle.
 */
final class SyntheticCaves implements CaveLevel {
    final Map<Long, CaveCell> writes = new HashMap<>();
    final StringBuilder log = new StringBuilder();
    float temperature = 10f;

    static CaveCell world(int x, int y, int z) {
        final int h = Math.floorMod(((x >> 1) * 73856093) ^ ((y >> 1) * 19349663) ^ ((z >> 1) * 83492791), 100);
        final int rock = Math.floorMod((y >> 3) + (x >> 4), 5);
        if (h < 40) {
            return CaveCell.CAVE_AIR;
        } else if (h < 45) {
            return CaveCell.fluid(CaveCell.Fluid.WATER);
        } else if (h < 47) {
            return CaveCell.fluid(CaveCell.Fluid.SALT_WATER);
        } else if (h < 48) {
            return CaveCell.fluid(CaveCell.Fluid.RIVER_WATER);
        } else if (h < 50) {
            return CaveCell.fluid(CaveCell.Fluid.LAVA);
        } else if (h < 53) {
            return CaveCell.AIR;
        } else if (h < 55) {
            return CaveCell.ICE;
        } else if (h < 60) {
            return CaveCell.hardened(rock);
        } else if (h < 62) {
            return CaveCell.OTHER;
        }
        return CaveCell.raw(rock);
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | (y & 0xFFF);
    }

    static String enc(CaveCell c) {
        return c.kind() + "," + c.rock() + "," + (c.part() == null ? "null" : c.part().name()) + "," + c.fluid() + "," + c.tip();
    }

    void reset() {
        writes.clear();
        log.setLength(0);
    }

    @Override
    public CaveCell get(int x, int y, int z) {
        final CaveCell w = writes.get(key(x, y, z));
        return w != null ? w : world(x, y, z);
    }

    @Override
    public void set(int x, int y, int z, CaveCell cell) {
        writes.put(key(x, y, z), cell);
        log.append(x).append(',').append(y).append(',').append(z).append('=').append(enc(cell)).append(';');
    }

    @Override
    public int rockAt(int x, int y, int z) {
        return Math.floorMod((y >> 3) + (x >> 4), 5);
    }

    @Override
    public boolean carved(int x, int y, int z) {
        return world(x, y, z).kind() == CaveCell.Kind.CAVE_AIR;
    }

    @Override
    public int maxY() {
        return 320;
    }

    @Override
    public GroundView ground() {
        return new GroundView() {
            @Override
            public int minY() {
                return -64;
            }

            @Override
            public int seaLevel() {
                return 63;
            }

            @Override
            public int oceanFloor(int x, int z) {
                return 0;
            }

            @Override
            public int worldSurface(int x, int z) {
                return 100;
            }

            @Override
            public boolean onIce(int x, int z) {
                return false;
            }

            @Override
            public boolean saltWater(int x, int z) {
                return false;
            }

            @Override
            public int surfaceRock(int x, int z) {
                return 0;
            }

            @Override
            public int biome(int x, int z) {
                return 0;
            }

            @Override
            public float averageTemperature(int x, int z) {
                return temperature;
            }

            @Override
            public float averageRainfall(int x, int z) {
                return 200f;
            }

            @Override
            public float groundwater(int x, int z) {
                return 200f;
            }

            @Override
            public float rainVariance(int x, int z) {
                return 0f;
            }

            @Override
            public double tideLevel(int x, int z) {
                return 0;
            }
        };
    }
}
