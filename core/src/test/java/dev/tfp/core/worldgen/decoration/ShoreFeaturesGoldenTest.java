/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.decoration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import dev.tfp.core.worldgen.tree.ForestType;
import dev.tfp.core.worldgen.tree.TreeGround;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * PAR-WORLD-65: golden values of TFC's tide pools, sea stack patches and bamboo at the pinned commit (c3b4d9536).
 *
 * <p>The expected values came from a scratch oracle (kept outside the repo) that runs TFC's {@code TidePoolFeature},
 * {@code SeaStacksFeature} (with TFC's {@code Metaballs2D} and {@code Helpers.fork}) and {@code TFCBambooFeature} bodies and
 * the vanilla placements around them (rarity, count, in_square, heightmap, random_patch, noise_based_count over
 * {@code Biome.BIOME_INFO_NOISE}, random_offset with {@code Mth.normal}) on Minecraft 1.21.11's own
 * {@code XoroshiroRandomSource}, {@code BlockPos.withinManhattan} and {@code Mth.nextInt} from the Paper 1.21.11 server jar
 * of the run-paper tooling, over the synthetic terrain below and the same block overlay as {@link DecorationLevel}. Each
 * case seeds {@code new XoroshiroRandomSource(seed ^ cx * 341873128712L ^ cz * 132897987541L ^ f * 0x9E3779B97F4A7C15L)} for
 * chunk {@code (cx, cz)} in [-6, 5] and feature {@code f}, over a fresh level, and hashes (FNV-1a 64) the lines
 * {@code "cx cz|x,y,z=kind[:rock][:id][:stalk];...|<next long>"}.
 */
class ShoreFeaturesGoldenTest {
    private static final ForestType[] FORESTS = {ForestType.SECONDARY_BAMBOO, ForestType.EDGE_BAMBOO, ForestType.PRIMARY_DIVERSE,
        ForestType.GRASSLAND, ForestType.DEAD_BAMBOO, ForestType.SECONDARY_MONOCULTURE};

    /** The oracle's terrain: surface 60 to 68 with salt water up to 62, grass above the sea, sand below it. */
    static class Terrain implements DecorationLevel.Terrain {
        static int s(int x, int z) {
            return 60 + Math.floorMod(x * 31 + z * 17, 9);
        }

        @Override
        public TreeGround ground(int x, int y, int z) {
            final int s = s(x, z);
            if (y < s - 4) {
                return TreeGround.STONE;
            }
            if (y < s - 1) {
                return TreeGround.DIRT;
            }
            if (y == s - 1) {
                return s - 1 >= 63 ? TreeGround.GRASS : TreeGround.SAND;
            }
            return y <= 62 ? TreeGround.SALT_WATER : TreeGround.AIR;
        }

        @Override
        public ForestType forestType(int chunkX, int chunkZ) {
            return FORESTS[Math.floorMod(chunkX * 3 + chunkZ * 7, 6)];
        }

        @Override
        public int rock(int x, int y, int z) {
            return Math.floorMod(x + 3 * z + y, 7);
        }

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
            return s(x, z);
        }

        @Override
        public int worldSurface(int x, int z) {
            return Math.max(s(x, z), 63);
        }

        @Override
        public boolean onIce(int x, int z) {
            return false;
        }

        @Override
        public boolean saltWater(int x, int z) {
            return true;
        }

        @Override
        public int surfaceRock(int x, int z) {
            return Math.floorMod(x * 5 + z, 7);
        }

        @Override
        public int biome(int x, int z) {
            return 0;
        }

        @Override
        public float averageTemperature(int x, int z) {
            return 5 + Math.floorMod(x * 7 + z * 3, 40);
        }

        @Override
        public float averageRainfall(int x, int z) {
            return groundwater(x, z);
        }

        @Override
        public float groundwater(int x, int z) {
            return 250 + Math.floorMod(x * 11 + z * 5, 260);
        }

        @Override
        public float rainVariance(int x, int z) {
            return 0f;
        }

        @Override
        public double tideLevel(int x, int z) {
            return 62.0;
        }
    }

    private static final Set<Integer> STACK_ROCKS = Set.of(0, 2, 5);
    private static final List<String> FEATURES = List.of("tide_pool", "big_tide_pool", "sea_stacks_patch", "bamboo",
        "bamboo_golden", "rare_bamboo", "rare_bamboo_golden");

    private record Golden(long seed, String feature, String hash, int blocks) {
    }

    private static List<Golden> goldens() throws IOException {
        final List<Golden> out = new ArrayList<>();
        try (InputStream in = ShoreFeaturesGoldenTest.class.getResourceAsStream("/dev/tfp/core/worldgen/decoration/shore_features.tsv")) {
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (!line.isBlank() && !line.startsWith("#")) {
                    final String[] f = line.split("\t");
                    out.add(new Golden(Long.parseLong(f[0]), f[1], f[2], Integer.parseInt(f[3])));
                }
            }
        }
        return out;
    }

    static String describe(DecorationLevel.Block b) {
        return b.kind().name() + (b.rock() >= 0 ? ":" + b.rock() : "") + (b.id() != null ? ":" + b.id() : "")
            + (b.stalk() != null ? ":" + b.stalk().name() : "");
    }

    private static void run(int f, XoroshiroRandomSource r, int bx, int bz, DecorationLevel level) {
        switch (f) {
            case 0, 1 -> TidePools.placed(r, TidePools.POOLS.get(f), bx, bz, level);
            case 2 -> SeaStacks.placed(r, bx, bz, level, STACK_ROCKS::contains);
            default -> Bamboo.placed(new Bamboo.Gaussian(r), Bamboo.FEATURES.get(f - 3), bx, bz, level);
        }
    }

    @Test
    void featuresMatchTheOracle() throws IOException {
        assertThat(TidePools.POOLS.stream().map(p -> p.id().substring(4)).toList()).isEqualTo(FEATURES.subList(0, 2));
        assertThat(SeaStacks.ID.substring(4)).isEqualTo(FEATURES.get(2));
        assertThat(Bamboo.FEATURES.stream().map(p -> p.id().substring(4)).toList()).isEqualTo(FEATURES.subList(3, 7));
        final List<Golden> goldens = goldens();
        assertThat(goldens).hasSize(35);
        final Terrain terrain = new Terrain();
        for (Golden g : goldens) {
            final int f = FEATURES.indexOf(g.feature());
            final StringBuilder out = new StringBuilder();
            int blocks = 0;
            for (int cx = -6; cx < 6; cx++) {
                for (int cz = -6; cz < 6; cz++) {
                    final XoroshiroRandomSource r = new XoroshiroRandomSource(
                        g.seed() ^ cx * 341873128712L ^ cz * 132897987541L ^ f * 0x9E3779B97F4A7C15L);
                    final DecorationLevel level = new DecorationLevel(terrain);
                    run(f, r, cx << 4, cz << 4, level);
                    final StringBuilder line = new StringBuilder();
                    for (DecorationLevel.Placed p : level.blocks()) {
                        line.append(p.x()).append(',').append(p.y()).append(',').append(p.z()).append('=')
                            .append(describe(p.block())).append(';');
                    }
                    blocks += level.blocks().size();
                    out.append(cx).append(' ').append(cz).append('|').append(line).append('|').append(r.nextLong()).append('\n');
                }
            }
            assertThat(Long.toHexString(DecorationGoldenTest.fnv(out.toString()))).as(g.seed() + " " + g.feature()).isEqualTo(g.hash());
            assertThat(blocks).as(g.seed() + " " + g.feature()).isEqualTo(g.blocks());
        }
        // every feature placed blocks in some case
        for (String feature : FEATURES) {
            assertThat(goldens.stream().filter(g -> g.feature().equals(feature)).mapToInt(Golden::blocks).sum()).as(feature).isPositive();
        }
    }

    @Test
    void manhattanOrderMatchesVanilla() {
        // BlockPos.withinManhattan(origin, 1, 0, 1): the centre, then distance 1 by x from -1 with the z mirror, then distance 2
        final TidePools.Manhattan m = new TidePools.Manhattan(0, 0, 0, 1, 0, 1);
        final List<String> out = new ArrayList<>();
        while (m.next()) {
            out.add(m.x() + "," + m.z());
        }
        assertThat(out).containsExactly("0,0", "-1,0", "0,1", "0,-1", "1,0", "-1,1", "-1,-1", "1,1", "1,-1");
    }

    @Test
    void tidePoolsOnlyBetween62And67() {
        final DecorationLevel level = new DecorationLevel(new Terrain());
        assertThat(TidePools.feature(new XoroshiroRandomSource(1L), level, 0, 61, 0)).isFalse();
        assertThat(TidePools.feature(new XoroshiroRandomSource(1L), level, 0, 68, 0)).isFalse();
        assertThat(level.blocks()).isEmpty();
    }

    @Test
    void bambooNeedsPlantableGround() {
        final DecorationLevel level = new DecorationLevel(new Terrain());
        // a sand column below the sea: the origin above the water is air but the water below is not plantable
        int x = 0;
        while (Terrain.s(x, 0) > 62) {
            x++;
        }
        assertThat(Bamboo.feature(new XoroshiroRandomSource(3L), Bamboo.FEATURES.get(0), level, x, 63, 0)).isTrue();
        assertThat(level.blocks()).isEmpty();
    }
}
