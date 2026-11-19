/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.groundcover;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/** PAR-WORLD-09: the boulders, the underground loose rocks and guano. */
class BoulderCaveGuanoTest {
    private static final long[] SEEDS = {0L, 7L, -987654321L};

    /** A flat view: every column's top solid block at y 70. */
    private static class Flat extends SyntheticView {
        Flat(int biome) {
            super(biome);
        }

        @Override
        public int oceanFloor(int x, int z) {
            return 71;
        }

        @Override
        public int worldSurface(int x, int z) {
            return 71;
        }
    }

    @Test
    void boulderBiomesAreTheTagListsOfTheTable() throws IOException {
        final Set<String> expected = new TreeSet<>();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("dev/tfp/core/worldgen/groundcover_deposit.tsv")) {
            assertThat(in).isNotNull();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                final String[] cells = line.split("\t", -1);
                if (cells[0].equals("boulder")) {
                    final Set<String> biomes = new TreeSet<>();
                    for (String b : cells[12].split(";")) {
                        biomes.add(b.substring("large_features/".length()));
                    }
                    if (expected.isEmpty()) {
                        expected.addAll(biomes);
                    }
                    assertThat(biomes).as(cells[1]).isEqualTo(expected);
                    final String id = cells[1];
                    final Boulders.Feature f = Boulders.FEATURES.stream().filter(x -> x.feature().equals(id)).findFirst().orElseThrow();
                    assertThat(f.rarity()).as(id).isEqualTo(Integer.parseInt(cells[7]));
                    assertThat(f.baby()).as(id).isEqualTo(cells[4].equals("baby_boulder"));
                }
            }
        }
        assertThat(new TreeSet<>(Boulders.BIOME_NAMES)).isEqualTo(expected);
    }

    @Test
    void flatEnoughNeedsSolidGroundAroundTheColumn() {
        // the heightmap y is the air above the ground: depth 0 counts no solid block, depth 1 counts 25 of 25
        assertThat(Boulders.flatEnough(new Flat(TfcBiomes.HIGHLANDS), 0, 0, 0.4f)).isEqualTo(70);
        // a one-column spike over ground at 60: 1 of 25 solid at the spike's top depths
        final GroundView spike = new Flat(TfcBiomes.HIGHLANDS) {
            @Override
            public int oceanFloor(int x, int z) {
                return x == 0 && z == 0 ? 71 : 61;
            }
        };
        assertThat(Boulders.flatEnough(spike, 0, 0, 0.4f)).isEqualTo(Integer.MIN_VALUE);
        assertThat(Boulders.flatEnough(spike, 0, 0, 0.03f)).isEqualTo(70);
        assertThat(Boulders.flatEnough(spike, 0, 0, 0.04f)).isEqualTo(Integer.MIN_VALUE);
    }

    @Test
    void bouldersAreDeterministicInChunkAndOfTheirFeatureBlocks() {
        for (long seed : SEEDS) {
            final GroundView view = new Flat(TfcBiomes.HIGHLANDS);
            final Boulders boulders = new Boulders(seed);
            int total = 0;
            for (int cx = -6; cx < 6; cx++) {
                for (int cz = -6; cz < 6; cz++) {
                    final List<Boulders.Block> a = new ArrayList<>();
                    final List<Boulders.Block> b = new ArrayList<>();
                    boulders.place(cx, cz, view, a::add);
                    new Boulders(seed).place(cx, cz, view, b::add);
                    assertThat(a).isEqualTo(b);
                    for (Boulders.Block block : a) {
                        assertThat(block.inChunk(cx, cz)).isTrue();
                        final Boulders.Feature f = Boulders.FEATURES.stream().filter(x -> x.feature().equals(block.feature()))
                            .findFirst().orElseThrow();
                        assertThat(f.blocks()).contains(block.material());
                        assertThat(block.rock()).isBetween(0, 20);
                        // a boulder of size <= 9 around a ground origin at 71
                        assertThat(block.y()).isBetween(71 - 3 - 9, 71 + 9);
                    }
                    total += a.size();
                }
            }
            assertThat(total).as("boulders in 144 highland chunks").isPositive();
            // no biome listing them: nothing
            final List<Boulders.Block> none = new ArrayList<>();
            boulders.place(0, 0, new Flat(TfcBiomes.PLAINS), none::add);
            assertThat(none).isEmpty();
        }
    }

    @Test
    void babyBouldersHaveTfcShapes() {
        final Boulders.Feature feature = Boulders.FEATURES.get(3);
        final GroundView view = new Flat(TfcBiomes.HIGHLANDS);
        final Set<Integer> sizes = new HashSet<>();
        for (int i = 0; i < 300; i++) {
            final List<Boulders.Block> out = new ArrayList<>();
            Boulders.baby(new XoroshiroRandomSource(i), feature, 0, 71, 0, view, out::add);
            sizes.add(out.size());
            for (Boulders.Block b : out) {
                assertThat(Math.abs(b.x()) + Math.abs(b.z())).isLessThanOrEqualTo(1);
                assertThat(b.y()).isBetween(69, 74);
            }
            // cross: origin + up to 6 neighbours; pillar: 4..6 + 2..4; stack: 2
            assertThat(out.size()).isBetween(1, 10);
        }
        assertThat(sizes).contains(2, 6, 7, 10);
    }

    /** A cave of air from y 0 to 20 over stone, in a chunk of stone. */
    private static final UndergroundLooseRocks.CaveView CAVE = new UndergroundLooseRocks.CaveView() {
        @Override
        public boolean air(int x, int y, int z) {
            return y >= 0 && y <= 20;
        }

        @Override
        public boolean solid(int x, int y, int z) {
            return !air(x, y, z);
        }

        @Override
        public boolean ice(int x, int y, int z) {
            return false;
        }
    };

    @Test
    void undergroundLooseRocksLieOnTheCaveFloorOfTheCornerColumn() {
        final GroundView view = new SyntheticView(TfcBiomes.PLAINS);
        int chunks = 0;
        for (long seed : SEEDS) {
            final UndergroundLooseRocks rocks = new UndergroundLooseRocks(seed);
            for (int cx = -20; cx < 20; cx++) {
                for (int cz = -20; cz < 20; cz++) {
                    final List<Groundcover> out = new ArrayList<>();
                    rocks.place(cx, cz, view, CAVE, out);
                    if (!out.isEmpty()) {
                        chunks++;
                    }
                    // all 25 starts land on the one floor block: at most one rock
                    assertThat(out).hasSizeLessThanOrEqualTo(1);
                    for (Groundcover g : out) {
                        assertThat(g.x()).isEqualTo(cx << 4);
                        assertThat(g.z()).isEqualTo(cz << 4);
                        assertThat(g.y()).isEqualTo(0);
                        assertThat(g.kind()).isIn(Groundcover.Kind.LOOSE_ROCK, Groundcover.Kind.MOSSY_LOOSE_ROCK);
                        assertThat(g.count()).isBetween(1, 2);
                    }
                }
            }
        }
        // rarity 20, and some of the 25 starts (y 0..12 of [-32, 59]) must reach the floor: about 1 chunk in 20
        assertThat(chunks).isBetween(3 * 1600 / 20 * 7 / 10, 3 * 1600 / 20 * 13 / 10);
    }

    @Test
    void environmentScanFollowsVanilla() {
        final Set<Long> placed = new HashSet<>();
        // starts in the cave: down to the floor (y -1) within 12 steps
        assertThat(UndergroundLooseRocks.scan(0, 5, 0, -64, CAVE, placed)).isEqualTo(-1);
        assertThat(UndergroundLooseRocks.scan(0, 11, 0, -64, CAVE, placed)).isEqualTo(-1);
        // 13 blocks above the floor: 12 steps end in air
        assertThat(UndergroundLooseRocks.scan(0, 12, 0, -64, CAVE, placed)).isEqualTo(Integer.MIN_VALUE);
        // a start in stone is not air
        assertThat(UndergroundLooseRocks.scan(0, -5, 0, -64, CAVE, placed)).isEqualTo(Integer.MIN_VALUE);
        // a rock already on the floor stops the scan without a solid block
        placed.add(LooseRocks.key(0, 0, 0));
        assertThat(UndergroundLooseRocks.scan(0, 5, 0, -64, CAVE, placed)).isEqualTo(Integer.MIN_VALUE);
    }

    @Test
    void guanoNeedsStoneAboveTheTide() {
        int withStone = 0;
        for (long seed : SEEDS) {
            for (int cx = -30; cx < 30; cx++) {
                final XoroshiroRandomSource a = new FeatureRandom(seed, "tfc:guano_patch").chunk(cx, 0);
                final XoroshiroRandomSource b = new FeatureRandom(seed, "tfc:guano_patch").chunk(cx, 0);
                final List<Groundcover> stone = new ArrayList<>();
                final List<Groundcover> none = new ArrayList<>();
                final GroundView high = new Flat(TfcBiomes.SHORE) {
                    @Override
                    public boolean topBlockStone(int x, int z) {
                        return true;
                    }

                    @Override
                    public double tideLevel(int x, int z) {
                        return 60;
                    }

                    @Override
                    public float averageTemperature(int x, int z) {
                        return 0;
                    }

                    @Override
                    public float groundwater(int x, int z) {
                        return 300;
                    }
                };
                ShoreGroundcover.guano(a, cx << 4, 0, high, stone::add);
                ShoreGroundcover.guano(b, cx << 4, 0, new Flat(TfcBiomes.SHORE), none::add);
                assertThat(none).isEmpty();
                withStone += stone.size();
                for (Groundcover g : stone) {
                    assertThat(g.kind()).isEqualTo(Groundcover.Kind.GUANO);
                    assertThat(g.y()).isEqualTo(71);
                    assertThat(g.waterlogged()).isFalse();
                }
                assertThat(new HashSet<>(stone.stream().map(g -> LooseRocks.key(g.x(), g.y(), g.z())).toList())).hasSize(stone.size());
            }
        }
        assertThat(withStone).isPositive();
    }
}
