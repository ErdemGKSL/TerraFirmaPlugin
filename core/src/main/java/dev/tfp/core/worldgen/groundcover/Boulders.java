/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/BouldersFeature.java @ c3b4d9536, with
 * src/main/java/net/dries007/tfc/world/feature/BabyBoulderFeature.java, BoulderConfig.java,
 * src/main/java/net/dries007/tfc/world/placement/FlatEnoughPlacement.java, the data of
 * src/main/resources/data/tfc/worldgen/{configured_feature,placed_feature}/{raw,cobble,mossy}_boulder{,_small,_small_patch}.json,
 * the tag data/tfc/tags/worldgen/placed_feature/feature/boulders.json (order) and the large_features biome tags that list
 * it, and the vanilla 1.21 steps they name (rarity_filter, in_square, heightmap OCEAN_FLOOR_WG, random_patch).
 */
package dev.tfp.core.worldgen.groundcover;

import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.ore.Metaballs3D;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * TFC's boulders ({@code #tfc:feature/boulders}, the first large features): three metaball boulders and three patches of baby
 * boulders, of the surface rock's raw rock, cobble or mossy cobble, on flat enough ground. The flatness test reads the
 * terrain heights: a block is solid and dry below the column's ocean floor (ledger). The boulders of the 3 x 3 chunks around
 * the generating chunk are evaluated; only the blocks inside it are returned. Pure and thread-safe.
 */
public final class Boulders {
    /** The block of a boulder piece. */
    public enum Material {
        /** {@code tfc:rock/raw/<rock>}. */
        RAW,
        /** {@code tfc:rock/cobble/<rock>}. */
        COBBLE,
        /** {@code tfc:rock/mossy_cobble/<rock>}. */
        MOSSY_COBBLE
    }

    /** One boulder block: the rock index of the origin column's surface rock and the block of that rock. */
    public record Block(int x, int y, int z, int rock, Material material, String feature) {
        /** Whether the block lies in the chunk {@code (chunkX, chunkZ)}. */
        public boolean inChunk(int chunkX, int chunkZ) {
            return x >> 4 == chunkX && z >> 4 == chunkZ;
        }
    }

    /**
     * One placed boulder feature.
     *
     * @param feature  the placed feature id
     * @param blocks   the {@code BoulderConfig} block list of every rock (one block, or two picked at random per block)
     * @param baby     a {@code baby_boulder} patch instead of one metaball boulder
     * @param rarity   {@code rarity_filter} chance
     * @param flatness the {@code flat_enough} threshold
     */
    public record Feature(String feature, List<Material> blocks, boolean baby, int rarity, float flatness) {}

    /** The features of {@code #tfc:feature/boulders}, in tag order. */
    public static final List<Feature> FEATURES = List.of(
        new Feature("tfc:raw_boulder", List.of(Material.RAW), false, 22, 0.4f),
        new Feature("tfc:cobble_boulder", List.of(Material.RAW, Material.COBBLE), false, 22, 0.4f),
        new Feature("tfc:mossy_boulder", List.of(Material.COBBLE, Material.MOSSY_COBBLE), false, 22, 0.4f),
        new Feature("tfc:raw_boulder_small_patch", List.of(Material.RAW), true, 36, 0.2f),
        new Feature("tfc:cobble_boulder_small_patch", List.of(Material.RAW, Material.COBBLE), true, 36, 0.2f),
        new Feature("tfc:mossy_boulder_small_patch", List.of(Material.COBBLE, Material.MOSSY_COBBLE), true, 36, 0.2f));

    /** The biomes whose large features tag lists {@code #tfc:feature/boulders}. */
    public static final Set<String> BIOME_NAMES = Set.of("canyons", "drumlins", "glaciated_shield_volcano", "highlands",
        "ice_sheet", "ice_sheet_edge", "ice_sheet_tuyas", "ice_sheet_tuyas_edge", "inverted_patterned_ground", "knob_and_kettle",
        "low_canyons", "patterned_ground", "plateau", "plateau_lake", "plateau_wide", "rocky_plateau", "rolling_hills",
        "stone_circles", "tuyas");

    /** {@code FlatEnoughPlacement} defaults: radius 2, max depth 4. */
    static final int FLAT_RADIUS = 2;
    static final int FLAT_MAX_DEPTH = 4;
    /** The baby patches' {@code random_patch}: 6 tries spreading 5 (y spread 1). */
    static final int BABY_TRIES = 6;
    static final int BABY_XZ_SPREAD = 5;

    private static final boolean[] DECORATED = decorated();

    private final FeatureRandom[] randoms;

    public Boulders(long levelSeed) {
        this.randoms = new FeatureRandom[FEATURES.size()];
        for (int i = 0; i < randoms.length; i++) {
            randoms[i] = new FeatureRandom(levelSeed, FEATURES.get(i).feature());
        }
    }

    /** Whether a biome lists the boulders. */
    public static boolean decorates(int biome) {
        return biome >= 0 && biome < DECORATED.length && DECORATED[biome];
    }

    /**
     * The boulder blocks inside chunk {@code (chunkX, chunkZ)}: the features in tag order, each over the 3 x 3 chunks around
     * it (x outer), when the biome at the origin chunk's centre lists them. A later block on the same position wins, as
     * {@code setBlock} does.
     */
    public void place(int chunkX, int chunkZ, GroundView view, Consumer<Block> out) {
        for (int f = 0; f < FEATURES.size(); f++) {
            final Feature feature = FEATURES.get(f);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    final int cx = chunkX + dx;
                    final int cz = chunkZ + dz;
                    if (!decorates(view.biome((cx << 4) + 8, (cz << 4) + 8))) {
                        continue;
                    }
                    place(randoms[f].chunk(cx, cz), feature, cx << 4, cz << 4, view, b -> {
                        if (b.inChunk(chunkX, chunkZ)) {
                            out.accept(b);
                        }
                    });
                }
            }
        }
    }

    /**
     * One chunk's feature with TFC's draws in TFC's order: the rarity float, {@code in_square} (x, then z), then for a boulder
     * the heightmap and flatness (no draws) and the boulder; for a baby patch, per try the {@code random_patch} offsets
     * (x, y, z), the heightmap and flatness and the baby boulder.
     */
    public static void place(XoroshiroRandomSource random, Feature feature, int blockX, int blockZ, GroundView view, Consumer<Block> out) {
        if (!(random.nextFloat() < 1f / feature.rarity())) {
            return;
        }
        final int originX = blockX + random.nextInt(16);
        final int originZ = blockZ + random.nextInt(16);
        if (!feature.baby()) {
            final int y = flatEnough(view, originX, originZ, feature.flatness());
            if (y != Integer.MIN_VALUE) {
                boulder(random, feature, originX, y, originZ, view, out);
            }
            return;
        }
        final int xz = BABY_XZ_SPREAD + 1;
        for (int i = 0; i < BABY_TRIES; i++) {
            final int x = originX + random.nextInt(xz) - random.nextInt(xz);
            random.nextInt(2);
            random.nextInt(2);
            final int z = originZ + random.nextInt(xz) - random.nextInt(xz);
            final int y = flatEnough(view, x, z, feature.flatness());
            if (y != Integer.MIN_VALUE) {
                baby(random, feature, x, y, z, view, out);
            }
        }
    }

    /**
     * The {@code OCEAN_FLOOR_WG} height of a column moved down by {@code FlatEnoughPlacement}: the first of the 4 depths whose
     * 5 x 5 square has more than {@code flatness} of its blocks solid and dry, or {@link Integer#MIN_VALUE}.
     */
    static int flatEnough(GroundView view, int x, int z, float flatness) {
        final int top = view.oceanFloor(x, z);
        if (top <= view.minY()) {
            return Integer.MIN_VALUE;
        }
        for (int d = 0; d < FLAT_MAX_DEPTH; d++) {
            final int y = top - d;
            int flatAmount = 0;
            for (int dx = -FLAT_RADIUS; dx <= FLAT_RADIUS; dx++) {
                for (int dz = -FLAT_RADIUS; dz <= FLAT_RADIUS; dz++) {
                    if (y < view.oceanFloor(x + dx, z + dz)) {
                        flatAmount++;
                    }
                }
            }
            if (flatAmount / ((1f + 2 * FLAT_RADIUS) * (1f + 2 * FLAT_RADIUS)) > flatness) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    /** The picker of {@code BouldersFeature.place}: the only block, or one of two with a {@code nextInt(2)} per block. */
    private static Material pick(XoroshiroRandomSource random, Feature feature) {
        final List<Material> blocks = feature.blocks();
        return blocks.size() == 1 ? blocks.getFirst() : blocks.get(random.nextInt(blocks.size()));
    }

    /**
     * {@code BouldersFeature.place}: size {@code 6 + nextInt(4)}, a {@code Metaballs3D} of a fork of the random (6 to 7 balls,
     * sizes {@code -0.12 size} to {@code 0.3 size}, radius {@code 0.3 size}, computed in float as TFC does), then x, y, z
     * outer to inner, one pick per block inside.
     */
    static void boulder(XoroshiroRandomSource random, Feature feature, int ox, int oy, int oz, GroundView view, Consumer<Block> out) {
        final int rock = view.surfaceRock(ox, oz);
        final int size = 6 + random.nextInt(4);
        final long lo = random.nextLong();
        final Metaballs3D noise = new Metaballs3D(new XoroshiroRandomSource(lo, random.nextLong()), 6, 8,
            -0.12f * size, 0.3f * size, 0.3f * size);
        final List<Block> blocks = new ArrayList<>();
        for (int x = -size; x <= size; x++) {
            for (int y = -size; y <= size; y++) {
                for (int z = -size; z <= size; z++) {
                    if (noise.inside(x, y, z)) {
                        blocks.add(new Block(ox + x, oy + y, oz + z, rock, pick(random, feature), feature.feature()));
                    }
                }
            }
        }
        blocks.forEach(out);
    }

    /** {@code BabyBoulderFeature.place}: a cross, a two-column pillar or two stacked blocks, by two coin floats. */
    static void baby(XoroshiroRandomSource random, Feature feature, int ox, int oy, int oz, GroundView view, Consumer<Block> out) {
        final int rock = view.surfaceRock(ox, oz);
        final String id = feature.feature();
        if (random.nextFloat() < 0.5f) {
            out.accept(new Block(ox, oy, oz, rock, pick(random, feature), id));
            // Helpers.DIRECTIONS = Direction.values(): down, up, north, south, west, east
            for (int[] d : new int[][] {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}}) {
                if (random.nextFloat() < 0.8f) {
                    out.accept(new Block(ox + d[0], oy + d[1], oz + d[2], rock, pick(random, feature), id));
                }
            }
        } else if (random.nextFloat() < 0.5f) {
            final int height = 4 + random.nextInt(3);
            for (int i = 0; i < height; i++) {
                out.accept(new Block(ox, oy - 2 + i, oz, rock, pick(random, feature), id));
            }
            // Direction.Plane.HORIZONTAL.getRandomDirection: north, east, south, west
            final int[][] horizontal = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
            final int[] d = horizontal[random.nextInt(4)];
            for (int i = 0; i < height - 2; i++) {
                out.accept(new Block(ox + d[0], oy - 2 + i, oz + d[1], rock, pick(random, feature), id));
            }
        } else {
            out.accept(new Block(ox, oy, oz, rock, pick(random, feature), id));
            out.accept(new Block(ox, oy - 1, oz, rock, pick(random, feature), id));
        }
    }

    private static boolean[] decorated() {
        final boolean[] out = new boolean[TfcBiomes.COUNT];
        for (int id = 0; id < TfcBiomes.COUNT; id++) {
            out[id] = BIOME_NAMES.contains(TfcBiomes.name(id).toLowerCase(Locale.ROOT));
        }
        return out;
    }
}
