/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/TidePoolFeature.java @ c3b4d9536
 *       src/main/resources/data/tfc/worldgen/placed_feature/{tide_pool,big_tide_pool}.json @ c3b4d9536
 *       src/main/resources/data/tfc/tags/worldgen/placed_feature/feature/tide_pool_decorations.json @ c3b4d9536
 *       src/generated/resources/data/tfc/tags/block/tide_pool_blocks.json @ c3b4d9536
 *       and Minecraft 1.21's RarityFilter, CountPlacement, InSquarePlacement, HeightmapPlacement, BlockPos.withinManhattan,
 *       Mth.nextInt
 */
package dev.tfp.core.worldgen.decoration;

import dev.tfp.core.worldgen.groundcover.FeatureRandom;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.List;

/**
 * PAR-WORLD-65: TFC's tide pools ({@code #tfc:feature/tide_pool_decorations}: {@code tfc:tide_pool}, {@code tfc:big_tide_pool}),
 * with TFC's draws in TFC's order. Placement: {@code rarity_filter}, {@code count}, {@code in_square}, {@code OCEAN_FLOOR_WG}.
 * The pool: an origin between y 62 and 67, a raw rock rim (90%, offset 0..2 on each axis; only when both offsets are not 0),
 * then a diamond of cobble and salt water, some salt water holding a shell groundcover (15%). Thread-safe.
 */
public final class TidePools {
    /** A placed tide pool feature: its id, {@code rarity_filter} chance and {@code count}. */
    public record Pool(String id, int rarity, int count) {
    }

    /** The tag's features in tag order. */
    public static final List<Pool> POOLS = List.of(new Pool("tfc:tide_pool", 5, 10), new Pool("tfc:big_tide_pool", 15, 40));

    /** {@code #tfc:tide_pool_blocks} in tag order (TFC ids as ours). */
    public static final List<String> TIDE_POOL_BLOCKS = List.of("tfp:groundcover/clam", "tfp:groundcover/mollusk",
        "tfp:groundcover/mussel", "tfp:groundcover/sea_urchin");

    /** {@code Helpers.DIRECTIONS} ({@code Direction.values()}): down, up, north, south, west, east. */
    private static final int[] DX = {0, 0, 0, 0, -1, 1};
    private static final int[] DY = {-1, 1, 0, 0, 0, 0};
    private static final int[] DZ = {0, 0, -1, 1, 0, 0};

    private final List<FeatureRandom> randoms;

    public TidePools(long levelSeed) {
        randoms = POOLS.stream().map(p -> new FeatureRandom(levelSeed, p.id())).toList();
    }

    /** Both tide pool features of origin chunk {@code (chunkX, chunkZ)}, in tag order, into the level. */
    public void place(int chunkX, int chunkZ, DecorationLevel level) {
        for (int i = 0; i < POOLS.size(); i++) {
            placed(randoms.get(i).chunk(chunkX, chunkZ), POOLS.get(i), chunkX << 4, chunkZ << 4, level);
        }
    }

    /** One chunk's placed feature: rarity, count, in_square, heightmap, then the pool at each position. */
    public static void placed(XoroshiroRandomSource random, Pool pool, int blockX, int blockZ, DecorationLevel level) {
        if (!(random.nextFloat() < 1f / pool.rarity())) {
            return;
        }
        for (int c = 0; c < pool.count(); c++) {
            final int x = random.nextInt(16) + blockX;
            final int z = random.nextInt(16) + blockZ;
            final int y = level.oceanFloor(x, z);
            if (y > level.terrain().minY()) {
                feature(random, level, x, y, z);
            }
        }
    }

    /** {@code Mth.nextInt(random, min, max)}. */
    static int nextInt(XoroshiroRandomSource random, int min, int max) {
        return min >= max ? min : random.nextInt(max - min + 1) + min;
    }

    /** {@code TidePoolFeature.place}: whether it placed any block. */
    public static boolean feature(XoroshiroRandomSource random, DecorationLevel level, int ox, int oy, int oz) {
        if (oy > 67 || oy < 62) {
            return false;
        }
        boolean placedAny = false;
        final boolean hasRim = random.nextDouble() < 0.9D;
        final int rimOffsetX = hasRim ? nextInt(random, 0, 2) : 0;
        final int rimOffsetZ = hasRim ? nextInt(random, 0, 2) : 0;
        final boolean willPlaceRim = hasRim && rimOffsetX != 0 && rimOffsetZ != 0;
        final int xSize = nextInt(random, 3, 7);
        final int zSize = nextInt(random, 3, 7);
        final int maxLength = Math.max(xSize, zSize);
        final int rock = level.terrain().rock(ox, oy, oz);
        final DecorationLevel.Block raw = DecorationLevel.Block.rock(DecorationLevel.Kind.RAW, rock);
        final DecorationLevel.Block cobble = DecorationLevel.Block.rock(DecorationLevel.Kind.COBBLE, rock);
        final Manhattan positions = new Manhattan(ox, oy, oz, xSize, 0, zSize);
        while (positions.next()) {
            final int x = positions.x();
            final int y = positions.y();
            final int z = positions.z();
            if (Math.abs(x - ox) + Math.abs(y - oy) + Math.abs(z - oz) > maxLength) {
                break;
            }
            if (clear(level, x, y, z, rock)) {
                if (willPlaceRim) {
                    placedAny = true;
                    level.set(x, y, z, raw);
                    if (random.nextFloat() < 0.01f && level.air(x, y + 1, z)) {
                        level.set(x, y + 1, z, new DecorationLevel.Block(DecorationLevel.Kind.COVER, -1, "tfp:groundcover/guano", null));
                    }
                }
                final int px = x + rimOffsetX;
                final int pz = z + rimOffsetZ;
                if (clear(level, px, y, pz, rock)) {
                    placedAny = true;
                    DecorationLevel.Block toPlace = cobble;
                    if (random.nextBoolean()) {
                        toPlace = DecorationLevel.Block.SALT_WATER;
                        if (random.nextFloat() < 0.15f) {
                            // Helpers.randomBlock over the tag, filled with salt water (every groundcover holds it)
                            final String cover = TIDE_POOL_BLOCKS.get(random.nextInt(TIDE_POOL_BLOCKS.size()));
                            toPlace = new DecorationLevel.Block(DecorationLevel.Kind.SALT_WATER_COVER, -1, cover, null);
                        }
                    }
                    level.set(px, y, pz, toPlace);
                }
            }
        }
        return placedAny;
    }

    /** {@code isClear}: not cobble of the rock and no fluid; air above, nothing around or below is air. */
    private static boolean clear(DecorationLevel level, int x, int y, int z, int rock) {
        if (level.cobble(x, y, z, rock) || level.fluid(x, y, z)) {
            return false;
        }
        for (int d = 0; d < 6; d++) {
            final boolean airAbove = level.air(x + DX[d], y + DY[d], z + DZ[d]);
            final boolean up = d == 1;
            if (airAbove && !up || !airAbove && up) {
                return false;
            }
        }
        return true;
    }

    /** {@code BlockPos.withinManhattan(center, xSize, ySize, zSize)}: positions by growing Manhattan distance, vanilla's order. */
    static final class Manhattan {
        private final int cx;
        private final int cy;
        private final int cz;
        private final int xSize;
        private final int ySize;
        private final int zSize;
        private final int maxDepth;
        private int currentDepth;
        private int maxX;
        private int maxY;
        private int ix;
        private int iy;
        private boolean zMirror;
        private int px;
        private int py;
        private int pz;

        Manhattan(int cx, int cy, int cz, int xSize, int ySize, int zSize) {
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            this.xSize = xSize;
            this.ySize = ySize;
            this.zSize = zSize;
            this.maxDepth = xSize + ySize + zSize;
        }

        boolean next() {
            if (zMirror) {
                zMirror = false;
                pz = cz - (pz - cz);
                return true;
            }
            while (true) {
                if (iy > maxY) {
                    ++ix;
                    if (ix > maxX) {
                        ++currentDepth;
                        if (currentDepth > maxDepth) {
                            return false;
                        }
                        maxX = Math.min(xSize, currentDepth);
                        ix = -maxX;
                    }
                    maxY = Math.min(ySize, currentDepth - Math.abs(ix));
                    iy = -maxY;
                }
                final int dx = ix;
                final int dy = iy;
                ++iy;
                final int dz = currentDepth - Math.abs(dx) - Math.abs(dy);
                if (dz <= zSize) {
                    zMirror = dz != 0;
                    px = cx + dx;
                    py = cy + dy;
                    pz = cz + dz;
                    return true;
                }
            }
        }

        int x() {
            return px;
        }

        int y() {
            return py;
        }

        int z() {
            return pz;
        }
    }
}
