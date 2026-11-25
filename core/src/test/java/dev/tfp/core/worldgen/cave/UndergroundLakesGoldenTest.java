/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.cave;

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
 * PAR-WORLD-64: golden values of TFC's {@code tfc:underground_flood_fill_lake} at the pinned commit (c3b4d9536).
 *
 * <p>The expected values came from a scratch oracle (kept outside the repo) that runs TFC's {@code FloodFillLakeFeature}
 * body (water, no replaced fluids, overfill) under the placement (rarity 3, in_square, uniform -56..63) on Minecraft
 * 1.21.11's own {@code XoroshiroRandomSource}, {@code BlockPos}, {@code Direction} and {@code BoundingBox} from the Paper
 * 1.21.11 server jar, over the synthetic caves below (walled 8 x 8 cells with a floor per cell, some with water at the
 * bottom, some with a gap in a wall). Each case seeds {@code new XoroshiroRandomSource(seed ^ cx * 341873128712L ^ cz *
 * 132897987541L)} for chunk {@code (cx, cz)} in [-6, 5] and hashes (FNV-1a 64) the lines
 * {@code "cx cz|<sorted x,y,z of the lake;...>|<next long>"}.
 */
class UndergroundLakesGoldenTest {
    static final class Caves implements VolcanoView {
        @Override
        public TreeGround ground(int x, int y, int z) {
            if (y >= 70) {
                return TreeGround.AIR;
            }
            final int cx = Math.floorDiv(x, 8);
            final int cz = Math.floorDiv(z, 8);
            final int lx = Math.floorMod(x, 8);
            final int lz = Math.floorMod(z, 8);
            final boolean gap = Math.floorMod(cx * 3 + cz * 5, 5) == 0 && lx == 0 && lz == 3;
            if ((lx == 0 || lz == 0) && !gap) {
                return TreeGround.STONE;
            }
            final int floor = -60 + Math.floorMod(cx * 37 + cz * 11, 70);
            if (y < floor || y > 20) {
                return TreeGround.STONE;
            }
            if (Math.floorMod(cx * 5 + cz * 3, 7) == 0 && y < floor + 3) {
                return TreeGround.WATER;
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
            return 70;
        }

        @Override
        public int worldSurface(int x, int z) {
            return 70;
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
            return 10f;
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
            return 62.0;
        }
    }

    private record Golden(long seed, String hash, int blocks, int lakes) {
    }

    private static List<Golden> goldens() throws IOException {
        final List<Golden> out = new ArrayList<>();
        try (InputStream in = UndergroundLakesGoldenTest.class.getResourceAsStream("/dev/tfp/core/worldgen/cave/underground_lake.tsv")) {
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (!line.isBlank() && !line.startsWith("#")) {
                    final String[] f = line.split("\t");
                    out.add(new Golden(Long.parseLong(f[0]), f[1], Integer.parseInt(f[2]), Integer.parseInt(f[3])));
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
    void undergroundLakesMatchTheOracle() throws IOException {
        final List<Golden> goldens = goldens();
        assertThat(goldens).hasSize(5);
        final Caves caves = new Caves();
        for (Golden g : goldens) {
            final StringBuilder out = new StringBuilder();
            int blocks = 0;
            int lakes = 0;
            for (int cx = -6; cx < 6; cx++) {
                for (int cz = -6; cz < 6; cz++) {
                    final XoroshiroRandomSource r = new XoroshiroRandomSource(g.seed() ^ cx * 341873128712L ^ cz * 132897987541L);
                    final List<VolcanicBlock> lake = UndergroundLakes.placed(r, cx << 4, cz << 4, caves);
                    final List<String> positions = new ArrayList<>();
                    for (VolcanicBlock b : lake) {
                        assertThat(b.kind()).isEqualTo(VolcanicKind.WATER);
                        positions.add(b.x() + "," + b.y() + "," + b.z());
                    }
                    Collections.sort(positions);
                    blocks += positions.size();
                    lakes += positions.isEmpty() ? 0 : 1;
                    out.append(cx).append(' ').append(cz).append('|').append(String.join(";", positions)).append('|')
                        .append(r.nextLong()).append('\n');
                }
            }
            assertThat(Long.toHexString(fnv(out.toString()))).as("seed " + g.seed()).isEqualTo(g.hash());
            assertThat(blocks).as("seed " + g.seed()).isEqualTo(g.blocks());
            assertThat(lakes).as("seed " + g.seed()).isEqualTo(g.lakes());
        }
    }

    @Test
    void caveVegetationBiomesAreTheLandBiomes() {
        final List<String> names = IntStream.range(0, TfcBiomes.COUNT).mapToObj(i -> TfcBiomes.name(i).toLowerCase(Locale.ROOT)).toList();
        assertThat(names).containsAll(CaveVegetation.WITHOUT);
        assertThat(IntStream.range(0, TfcBiomes.COUNT).filter(CaveVegetation::placesIn).count()).isEqualTo(99);
        assertThat(CaveVegetation.placesIn(TfcBiomes.PLAINS)).isTrue();
        assertThat(CaveVegetation.placesIn(TfcBiomes.OCEAN)).isFalse();
    }
}
