/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.lake;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import dev.tfp.core.worldgen.tree.ForestType;
import dev.tfp.core.worldgen.tree.TreeGround;
import dev.tfp.core.worldgen.volcano.VolcanicBlock;
import dev.tfp.core.worldgen.volcano.VolcanicKind;
import dev.tfp.core.worldgen.volcano.VolcanoView;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * PAR-WORLD-67: golden values of TFC's surface {@code tfc:flood_fill_lake} at the pinned commit (c3b4d9536).
 *
 * <p>The expected values came from a scratch oracle (kept outside the repo) that runs TFC's {@code FloodFillLakeFeature}
 * body (water, no replaced fluids, overfill, grass floor to dirt) under the placement (rarity 5, in_square,
 * {@code WORLD_SURFACE_WG}, {@code tfc:climate} min temperature -17 with TFC's elevation adjustment, min groundwater 125)
 * on Minecraft 1.21.11's own {@code XoroshiroRandomSource}, {@code BlockPos}, {@code Direction}, {@code BoundingBox} and
 * {@code Mth} from the Paper 1.21.11 server jar, over the synthetic surface below (12 x 12 basins with a rim at 72 and a
 * floor at 60 to 72, some with a gap in the rim, some with water or a grass floor, some under a floating leaf canopy; a
 * climate varying per column). Each case seeds {@code new XoroshiroRandomSource(seed ^ cx * 341873128712L ^ cz *
 * 132897987541L)} for chunk {@code (cx, cz)} in [-6, 5] and hashes (FNV-1a 64) the lines
 * {@code "cx cz|<sorted x,y,z of the water;...>|<sorted x,y,z of the dirt;...>|<next long>"}.
 */
class SurfaceLakesGoldenTest {
    static class Basins implements VolcanoView {
        static int top(int x, int z) {
            final int cx = Math.floorDiv(x, 12);
            final int cz = Math.floorDiv(z, 12);
            final int lx = Math.floorMod(x, 12);
            final int lz = Math.floorMod(z, 12);
            final boolean gap = Math.floorMod(cx * 3 + cz * 5, 4) == 0 && lx == 0 && lz == 5;
            if ((lx == 0 || lz == 0) && !gap) {
                return 72;
            }
            return 60 + Math.floorMod(cx * 37 + cz * 11, 13);
        }

        @Override
        public TreeGround ground(int x, int y, int z) {
            final int cx = Math.floorDiv(x, 12);
            final int cz = Math.floorDiv(z, 12);
            final int lx = Math.floorMod(x, 12);
            final int lz = Math.floorMod(z, 12);
            final int t = top(x, z);
            if (y < t) {
                return TreeGround.STONE;
            }
            if (y == t) {
                return Math.floorMod(cx + cz * 2, 3) == 0 ? TreeGround.GRASS : TreeGround.STONE;
            }
            if (Math.floorMod(cx * 5 + cz * 3, 7) == 0 && y <= t + 2 && t < 72) {
                return TreeGround.WATER;
            }
            if (Math.floorMod(cx * 2 + cz * 7, 9) == 0 && y == 75 && lx >= 3 && lx <= 5 && lz >= 2 && lz <= 8) {
                return TreeGround.LEAVES;
            }
            return TreeGround.AIR;
        }

        @Override
        public int rockAt(int x, int y, int z) {
            return 0;
        }

        @Override
        public ForestType forestType(int chunkX, int chunkZ) {
            return ForestType.GRASSLAND;
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
            return top(x, z) + 1;
        }

        @Override
        public int worldSurface(int x, int z) {
            for (int y = 100; y > -64; y--) {
                if (ground(x, y, z) != TreeGround.AIR) {
                    return y + 1;
                }
            }
            return -64;
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
            return TfcBiomes.PLAINS;
        }

        @Override
        public float averageTemperature(int x, int z) {
            return -30f + Math.floorMod(x * 7 + z * 13, 60);
        }

        @Override
        public float averageRainfall(int x, int z) {
            return 200f;
        }

        @Override
        public float groundwater(int x, int z) {
            return (float) Math.floorMod(x * 3 + z * 5, 400);
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

    private record Golden(long seed, String hash, int water, int dirt, int lakes) {
    }

    private static List<Golden> goldens() throws IOException {
        final List<Golden> out = new ArrayList<>();
        try (InputStream in = SurfaceLakesGoldenTest.class.getResourceAsStream("/dev/tfp/core/worldgen/lake/surface_lake.tsv")) {
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (!line.isBlank() && !line.startsWith("#")) {
                    final String[] f = line.split("\t");
                    out.add(new Golden(Long.parseLong(f[0]), f[1], Integer.parseInt(f[2]), Integer.parseInt(f[3]),
                        Integer.parseInt(f[4])));
                }
            }
        }
        return out;
    }

    private static long fnv(String s) {
        long h = 0xcbf29ce484222325L;
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            h ^= b & 0xff;
            h *= 0x100000001b3L;
        }
        return h;
    }

    @Test
    void surfaceLakesMatchTheOracle() throws IOException {
        final List<Golden> goldens = goldens();
        assertThat(goldens).hasSize(5);
        final Basins basins = new Basins();
        for (Golden g : goldens) {
            final StringBuilder out = new StringBuilder();
            int water = 0;
            int dirt = 0;
            int lakes = 0;
            for (int cx = -6; cx < 6; cx++) {
                for (int cz = -6; cz < 6; cz++) {
                    final XoroshiroRandomSource r = new XoroshiroRandomSource(g.seed() ^ cx * 341873128712L ^ cz * 132897987541L);
                    final List<VolcanicBlock> lake = SurfaceLakes.placed(r, cx << 4, cz << 4, basins);
                    final List<String> w = new ArrayList<>();
                    final List<String> d = new ArrayList<>();
                    for (VolcanicBlock b : lake) {
                        assertThat(b.kind()).isIn(VolcanicKind.WATER, VolcanicKind.DIRT);
                        (b.kind() == VolcanicKind.WATER ? w : d).add(b.x() + "," + b.y() + "," + b.z());
                    }
                    Collections.sort(w);
                    Collections.sort(d);
                    water += w.size();
                    dirt += d.size();
                    lakes += w.isEmpty() ? 0 : 1;
                    out.append(cx).append(' ').append(cz).append('|').append(String.join(";", w)).append('|')
                        .append(String.join(";", d)).append('|').append(r.nextLong()).append('\n');
                }
            }
            assertThat(Long.toHexString(fnv(out.toString()))).as("seed " + g.seed()).isEqualTo(g.hash());
            assertThat(water).as("seed " + g.seed()).isEqualTo(g.water());
            assertThat(dirt).as("seed " + g.seed()).isEqualTo(g.dirt());
            assertThat(lakes).as("seed " + g.seed()).isEqualTo(g.lakes());
        }
    }

    @Test
    void lakesPlaceInEveryBiomeButTheBurrenAndShilinKarsts() {
        final List<String> names = IntStream.range(0, TfcBiomes.COUNT).mapToObj(i -> TfcBiomes.name(i).toLowerCase(Locale.ROOT)).toList();
        assertThat(names).containsAll(SurfaceLakes.WITHOUT);
        assertThat(IntStream.range(0, TfcBiomes.COUNT).filter(SurfaceLakes::placesIn).count()).isEqualTo(115);
        assertThat(SurfaceLakes.placesIn(TfcBiomes.PLAINS)).isTrue();
        assertThat(SurfaceLakes.placesIn(TfcBiomes.OCEAN)).isTrue();
        assertThat(SurfaceLakes.placesIn(TfcBiomes.BURREN_PLAINS)).isFalse();
        assertThat(SurfaceLakes.placesIn(-1)).isFalse();
    }

    @Test
    void originChunkFiltersOnItsBiome() {
        final SurfaceLakes lakes = new SurfaceLakes(42L);
        final Basins karst = new Basins() {
            @Override
            public int biome(int x, int z) {
                return TfcBiomes.SHILIN_PLAINS;
            }
        };
        for (int cx = -4; cx < 4; cx++) {
            for (int cz = -4; cz < 4; cz++) {
                assertThat(lakes.place(cx, cz, karst)).isEmpty();
            }
        }
        int placed = 0;
        for (int cx = -4; cx < 4; cx++) {
            for (int cz = -4; cz < 4; cz++) {
                placed += lakes.place(cx, cz, new Basins()).isEmpty() ? 0 : 1;
            }
        }
        assertThat(placed).isPositive();
    }
}
