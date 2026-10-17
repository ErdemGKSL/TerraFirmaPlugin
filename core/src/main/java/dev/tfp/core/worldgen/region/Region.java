/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/region/Region.java @ c3b4d9536 (the land, island, shore, mountain, volcanic and
 * barrier flags, and the annotations of the in-scope tasks; rivers, lakes, biomes and biome altitude are not ported yet)
 */
package dev.tfp.core.worldgen.region;

import dev.tfp.core.worldgen.noise.Cellular2D;
import dev.tfp.core.worldgen.noise.FastNoiseLite;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * One region: the grid points of the square that a continent cell and its surroundings cover, stored in a flat array.
 * Points are null outside the cell. A region is built once by {@link RegionGenerator} and then only read.
 */
public final class Region {
    private final double cellX;
    private final double cellY;
    private final double noise;
    private int minX;
    private int minZ;
    private int maxX;
    private int maxZ;
    private int sizeX;
    private int sizeZ;
    private Point[] data;

    Region(Cellular2D.Cell cell) {
        this.cellX = cell.x();
        this.cellY = cell.y();
        this.noise = cell.noise();

        final int cellX = FastNoiseLite.FastRound(cell.x());
        final int cellZ = FastNoiseLite.FastRound(cell.y());

        this.minX = cellX - Units.REGION_RADIUS_IN_GRID;
        this.minZ = cellZ - Units.REGION_RADIUS_IN_GRID;
        this.maxX = cellX + Units.REGION_RADIUS_IN_GRID;
        this.maxZ = cellZ + Units.REGION_RADIUS_IN_GRID;

        this.sizeX = 1 + maxX - minX;
        this.sizeZ = 1 + maxZ - minZ;

        this.data = new Point[0]; // Must initialize via `setRegionArea()` first
    }

    /** @return An iterator through all points present within this region, skipping the empty slots. */
    public Iterable<Point> points() {
        return () -> new Iterator<>() {
            private int index = -1;
            private Point next = advance();

            private Point advance() {
                do {
                    index++;
                } while (index < data.length && data[index] == null);
                return index < data.length ? data[index] : null;
            }

            @Override
            public boolean hasNext() {
                return next != null;
            }

            @Override
            public Point next() {
                if (next == null) {
                    throw new NoSuchElementException();
                }
                final Point result = next;
                next = advance();
                return result;
            }
        };
    }

    /** @return A point chosen by {@code random}, possibly null. */
    public Point random(XoroshiroRandomSource random) {
        return data[random.nextInt(data.length)];
    }

    /** @return The point at the grid coordinates, or null if they are outside the region's bounding box or the cell. */
    public Point at(int gridX, int gridZ) {
        return isIn(gridX, gridZ) ? data[index(gridX, gridZ)] : null;
    }

    /** @return The point at {@code index} (a {@link Point#index}). */
    public Point atIndex(int index) {
        return data[index];
    }

    /**
     * @return The point at {@code index} shifted by {@code (dx, dz)}, or null if that is outside the bounding box.
     */
    public Point atOffset(int index, int dx, int dz) {
        final int localX = dx + (index % sizeX);
        final int localZ = dz + (index / sizeX);
        return localX >= 0 && localX < sizeX && localZ >= 0 && localZ < sizeZ
            ? data[localX + sizeX * localZ]
            : null;
    }

    public double noise() {
        return noise;
    }

    public double cellX() {
        return cellX;
    }

    public double cellY() {
        return cellY;
    }

    public int minX() {
        return minX;
    }

    public int minZ() {
        return minZ;
    }

    public int maxX() {
        return maxX;
    }

    public int maxZ() {
        return maxZ;
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeZ() {
        return sizeZ;
    }

    /** @return An estimate for the region's size, for pre-sizing bit sets. */
    public int size() {
        return sizeX * sizeZ;
    }

    @Override
    public String toString() {
        return "Region [%d, %d] x [%d, %d] at cell (%f, %f)".formatted(minX, maxX, minZ, maxZ, cellX, cellY);
    }

    /** Used by region generation: the point at the grid coordinates, which must be in the region. */
    Point atOrThrow(int gridX, int gridZ) {
        final Point point = data[index(gridX, gridZ)];
        if (point == null) {
            throw new IllegalStateException("Region %s does not contain point at (%d, %d)".formatted(this, gridX, gridZ));
        }
        return point;
    }

    /** @return An index into {@link #data} for the global grid coordinates. */
    int index(int gridX, int gridZ) {
        if (!isIn(gridX, gridZ)) {
            throw new IllegalArgumentException("Point (" + gridX + ", " + gridZ + ") not in region [" + minX + ", " + maxX + "] x [" + minZ + ", " + maxZ + "]");
        }
        final int localX = gridX - minX;
        final int localZ = gridZ - minZ;
        return localX + sizeX * localZ;
    }

    /** Used by the initialisation step to shrink the point array to the cell's extent. */
    void setRegionArea(int minX, int minZ, int maxX, int maxZ) {
        this.sizeX = 1 + maxX - minX;
        this.sizeZ = 1 + maxZ - minZ;
        this.data = new Point[sizeX * sizeZ];
        this.minX = minX;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxZ = maxZ;
    }

    /** Marks the point at the grid coordinates as part of the cell. */
    void init(int gridX, int gridZ) {
        final int index = index(gridX, gridZ);
        data[index] = new Point(gridX, gridZ, index);
    }

    private boolean isIn(int gridX, int gridZ) {
        return gridX >= minX && gridX <= maxX && gridZ >= minZ && gridZ <= maxZ;
    }

    /**
     * One grid point of a region. The annotations are written by the tasks in {@link RegionGenerator.Task} order; the
     * byte fields keep TFC's widths, so distances saturate exactly as in TFC.
     */
    public static final class Point {
        static final short FLAG_LAND = 0b1;
        static final short FLAG_ISLAND = 0b10;
        static final short FLAG_MOUNTAIN = 0b100;
        static final short FLAG_COASTAL_MOUNTAIN = 0b1000;
        static final short FLAG_VOLCANIC = 0b10000;
        static final short FLAG_BARRIER_ISLAND = 0b100000;

        /** Distance to the nearest ocean. Negative values are ocean; {@code -2} is an ocean adjacent to land. */
        public byte distanceToOcean = 0;
        /** Distance to the nearest deep ocean (ocean depth above the continental shelf). */
        public byte distanceToDeepOcean = 0;
        /** Distance to the nearest edge of the region. Some tasks stay away from it to avoid seams between regions. */
        public byte distanceToEdge = 0;
        public byte distanceToWestCoast = 0;
        public byte distanceToLand = 0;
        public byte baseLandHeight = 0;
        public byte oceanDepth = 0;

        public float rainfall;
        public float rainfallVariance;
        public float temperature;
        public float divergence;

        /** The rock id: the upper bits are the rock layer seed, the lower two bits the {@link ChooseRocks} supertype. */
        public int rock = 0;
        public byte hotSpotAge = 0;

        private short flags;

        public final int x, z;
        public final int index;

        Point(int x, int z, int index) {
            this.x = x;
            this.z = z;
            this.index = index;
        }

        public boolean land() {
            return (flags & FLAG_LAND) != 0;
        }

        public boolean island() {
            return (flags & FLAG_ISLAND) != 0;
        }

        public boolean shore() {
            return distanceToOcean == -2;
        }

        public boolean mountain() {
            return (flags & FLAG_MOUNTAIN) != 0;
        }

        public boolean coastalMountain() {
            return (flags & FLAG_COASTAL_MOUNTAIN) != 0;
        }

        public boolean volcanic() {
            return (flags & FLAG_VOLCANIC) != 0;
        }

        public boolean barrierIsland() {
            return (flags & FLAG_BARRIER_ISLAND) != 0;
        }

        void setLand() {
            flags |= FLAG_LAND;
        }

        void setIsland() {
            flags |= FLAG_ISLAND;
        }

        void setShore() {
            distanceToOcean = -2;
        }

        void setMountain() {
            flags |= FLAG_MOUNTAIN;
        }

        void setCoastalMountain() {
            flags |= FLAG_COASTAL_MOUNTAIN;
        }

        void setVolcanic() {
            flags |= FLAG_VOLCANIC;
        }

        void setBarrierIsland() {
            flags |= FLAG_BARRIER_ISLAND;
        }
    }
}
