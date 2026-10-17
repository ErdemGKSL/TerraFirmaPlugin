/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.groundcover;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonParser;
import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.chunk.PlaceholderRocks;
import dev.tfp.core.worldgen.ore.ClusterVeinConfig;
import dev.tfp.core.worldgen.ore.Indicator;
import dev.tfp.core.worldgen.ore.OreGrade;
import dev.tfp.core.worldgen.ore.OreVeins;
import dev.tfp.core.worldgen.ore.VeinBlock;
import dev.tfp.core.worldgen.ore.VeinData;
import dev.tfp.core.worldgen.ore.Weighted;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** M2-GEN-11: properties of the loose rocks, shore groundcover and vein indicators over many chunks and seeds. */
class GroundcoverPropertyTest {
    private static final long[] SEEDS = {0L, 1L, 42L, -987654321L};
    private static final GroundView SHORE_VIEW = new SyntheticView(TfcBiomes.SHORE);
    private static final GroundView PLAINS_VIEW = new SyntheticView(TfcBiomes.PLAINS);

    private static List<Groundcover> looseRocks(long seed, int chunkX, int chunkZ) {
        final List<Groundcover> out = new ArrayList<>();
        new LooseRocks(seed).place(chunkX, chunkZ, SHORE_VIEW, out);
        return out;
    }

    private static List<Groundcover> shore(long seed, int chunkX, int chunkZ, GroundView view) {
        final List<Groundcover> out = new ArrayList<>();
        new ShoreGroundcover(seed).place(chunkX, chunkZ, view, out);
        return out;
    }

    /** Every groundcover lies in its chunk, on the ocean floor, with a kind, block id and count that fit together. */
    private static void assertOnSurface(List<Groundcover> covers, int chunkX, int chunkZ, GroundView view) {
        for (Groundcover g : covers) {
            assertThat(g.inChunk(chunkX, chunkZ)).as("%s in chunk %d,%d", g, chunkX, chunkZ).isTrue();
            assertThat(g.y()).as("%s on the ocean floor", g).isEqualTo(view.oceanFloor(g.x(), g.z()));
            assertThat(g.waterlogged()).isEqualTo(view.water(g.x(), g.y(), g.z()));
            switch (g.kind()) {
                case LOOSE_ROCK -> assertThat(g.block()).isEqualTo("tfp:rock/loose/" + PlaceholderRocks.names().get(view.surfaceRock(g.x(), g.z())));
                case MOSSY_LOOSE_ROCK -> {
                    assertThat(g.block()).isEqualTo("tfp:rock/mossy_loose/" + PlaceholderRocks.names().get(view.surfaceRock(g.x(), g.z())));
                    assertThat(g.waterlogged()).isFalse();
                    assertThat(view.averageRainfall(g.x(), g.z())).isGreaterThan(250f);
                }
                case SMALL_ORE -> assertThat(g.block()).startsWith("tfp:ore/small_");
                default -> assertThat(g.block()).startsWith("tfp:groundcover/");
            }
            if (g.kind() == Groundcover.Kind.LOOSE_ROCK || g.kind() == Groundcover.Kind.MOSSY_LOOSE_ROCK) {
                assertThat(g.count()).isBetween(1, 2);
                assertThat(view.onIce(g.x(), g.z())).isFalse();
            } else {
                assertThat(g.count()).isEqualTo(1);
            }
        }
    }

    @Test
    void looseRocksAreDeterministicPerSeedAndAcrossThreads() {
        for (long seed : SEEDS) {
            final List<List<Groundcover>> serial = IntStream.range(0, 64).mapToObj(i -> looseRocks(seed, (i & 7) - 4, (i >> 3) - 4)).toList();
            final List<List<Groundcover>> parallel = IntStream.range(0, 64).parallel()
                .mapToObj(i -> looseRocks(seed, (i & 7) - 4, (i >> 3) - 4)).toList();
            assertThat(parallel).isEqualTo(serial);
        }
        assertThat(looseRocks(1L, 3, -2)).isNotEqualTo(looseRocks(2L, 3, -2));
    }

    @Test
    void looseRocksStayOnTheSurfaceOfTheirChunk() {
        final Set<Groundcover.Kind> kinds = EnumSet.noneOf(Groundcover.Kind.class);
        int total = 0;
        for (long seed : SEEDS) {
            for (int cx = -6; cx < 6; cx++) {
                for (int cz = -6; cz < 6; cz++) {
                    final List<Groundcover> rocks = looseRocks(seed, cx, cz);
                    assertOnSurface(rocks, cx, cz, SHORE_VIEW);
                    rocks.forEach(r -> kinds.add(r.kind()));
                    total += rocks.size();
                    // One patch per chunk, 8 tries each, from the 3 x 3 chunks around: at most 72 rocks
                    assertThat(rocks.size()).isLessThanOrEqualTo(72);
                }
            }
        }
        assertThat(kinds).containsExactlyInAnyOrder(Groundcover.Kind.LOOSE_ROCK, Groundcover.Kind.MOSSY_LOOSE_ROCK);
        // About 8 tries per chunk, most on open ground
        assertThat(total / (SEEDS.length * 144.0)).isBetween(3.0, 8.0);
    }

    @Test
    void aChunksRocksAreThePatchRocksInsideIt() {
        final long seed = 77L;
        final FeatureRandom random = new FeatureRandom(seed, LooseRocks.FEATURE);
        final Map<Long, List<Groundcover>> byChunk = new HashMap<>();
        for (int cx = -3; cx <= 3; cx++) {
            for (int cz = -3; cz <= 3; cz++) {
                LooseRocks.patch(random.chunk(cx, cz), cx << 4, cz << 4, SHORE_VIEW,
                    g -> byChunk.computeIfAbsent(((long) (g.x() >> 4) << 32) | ((g.z() >> 4) & 0xFFFFFFFFL), k -> new ArrayList<>()).add(g));
            }
        }
        for (int cx = -2; cx <= 2; cx++) {
            for (int cz = -2; cz <= 2; cz++) {
                final List<Groundcover> expected = byChunk.getOrDefault(((long) cx << 32) | (cz & 0xFFFFFFFFL), List.of());
                assertThat(new HashSet<>(looseRocks(seed, cx, cz))).isEqualTo(new HashSet<>(expected));
            }
        }
    }

    @Test
    void shoreGroundcoverOnlyInShoreBiomesAndWithinItsRules() {
        final Set<Groundcover.Kind> kinds = EnumSet.noneOf(Groundcover.Kind.class);
        for (long seed : SEEDS) {
            for (int cx = -6; cx < 6; cx++) {
                for (int cz = -6; cz < 6; cz++) {
                    assertThat(shore(seed, cx, cz, PLAINS_VIEW)).isEmpty();
                    final List<Groundcover> covers = shore(seed, cx, cz, SHORE_VIEW);
                    assertOnSurface(covers, cx, cz, SHORE_VIEW);
                    for (Groundcover g : covers) {
                        kinds.add(g.kind());
                        final int above = (int) (g.y() - SHORE_VIEW.tideLevel(g.x(), g.z()));
                        switch (g.kind()) {
                            case CLAM, MOLLUSK, MUSSEL -> {
                                assertThat(g.waterlogged()).isTrue();
                                assertThat(SHORE_VIEW.saltWater(g.x(), g.z())).isTrue();
                            }
                            case DRIFTWOOD -> assertThat(above).isEqualTo(1);
                            case STICK -> assertThat(above).isBetween(1, 10);
                            case SEAWEED -> { }
                            default -> throw new AssertionError("not a shore kind: " + g);
                        }
                    }
                    assertThat(shore(seed, cx, cz, SHORE_VIEW)).isEqualTo(covers);
                }
            }
        }
        assertThat(kinds).containsExactlyInAnyOrder(Groundcover.Kind.DRIFTWOOD, Groundcover.Kind.CLAM, Groundcover.Kind.MOLLUSK,
            Groundcover.Kind.MUSSEL, Groundcover.Kind.SEAWEED, Groundcover.Kind.STICK);
        assertThat(ShoreGroundcover.decorates(TfcBiomes.SHORE)).isTrue();
        assertThat(ShoreGroundcover.decorates(TfcBiomes.PLAINS)).isFalse();
        assertThat(ShoreGroundcover.decorates(-1)).isFalse();
        assertThat(SaltWater.isSalty(TfcBiomes.OCEAN)).isTrue();
        assertThat(SaltWater.isSalty(TfcBiomes.SHORE)).isTrue();
        assertThat(SaltWater.isSalty(TfcBiomes.RIVER)).isFalse();
        assertThat(SaltWater.isSalty(TfcBiomes.LAKE)).isFalse();
    }

    /** A vein type that covers every chunk with malachite in the raw rocks between y 40 and 70, with an indicator. */
    private static ClusterVeinConfig malachite(int indicatorRarity) {
        final Map<String, Weighted<VeinBlock>> blocks = new HashMap<>();
        for (String rock : PlaceholderRocks.names()) {
            blocks.put("tfp:rock/raw/" + rock, Weighted.of(List.of(new VeinBlock("tfp:ore/normal_malachite/" + rock,
                VeinBlock.Kind.ORE, "malachite", OreGrade.NORMAL, "test_malachite")), List.of(1.0)));
        }
        return new ClusterVeinConfig("test_malachite", 1, 1f, 40, 70, 12, 123L, blocks,
            new Indicator(35, indicatorRarity, 1, 0, Weighted.of(List.of("tfp:ore/small_malachite"), List.of(1.0))));
    }

    private static List<Groundcover> indicators(long seed, int chunkX, int chunkZ, int rarity, OreVeins.RockAt rocks) {
        final List<String> rockIds = PlaceholderRocks.names().stream().map(n -> "tfp:rock/raw/" + n).toList();
        final OreVeins ores = new OreVeins(seed, List.of(malachite(rarity)), rockIds);
        final List<Groundcover> out = new ArrayList<>();
        new VeinIndicators(ores).place(chunkX, chunkZ, SHORE_VIEW, rocks, out);
        return out;
    }

    @Test
    void indicatorsLieAboveTheirVeins() {
        final OreVeins.RockAt rock = (x, y, z) -> y <= SyntheticView.top(x, z) ? 3 : -1;
        int total = 0;
        for (long seed : SEEDS) {
            for (int cx = -3; cx < 3; cx++) {
                for (int cz = -3; cz < 3; cz++) {
                    final List<Groundcover> covers = indicators(seed, cx, cz, 4, rock);
                    assertOnSurface(covers, cx, cz, SHORE_VIEW);
                    for (Groundcover g : covers) {
                        assertThat(g.kind()).isEqualTo(Groundcover.Kind.SMALL_ORE);
                        assertThat(g.block()).isEqualTo("tfp:ore/small_malachite");
                        // The vein's ores are at most at y 70, and the indicator within 35 of them
                        assertThat(g.y()).isLessThan(70 + 35);
                    }
                    assertThat(indicators(seed, cx, cz, 4, rock)).isEqualTo(covers);
                    total += covers.size();
                }
            }
        }
        assertThat(total).isPositive();
        // No rock anywhere: no ore, so no indicator
        assertThat(indicators(5L, 0, 0, 1, (x, y, z) -> -1)).isEmpty();
        // Rarity 0: never
        assertThat(indicators(5L, 0, 0, 0, rock)).isEmpty();
    }

    @Test
    void veinDataReadsTheIndicator() {
        final String json = """
            {"type": "tfp:cluster_vein", "config": {"rarity": 20, "density": 0.3, "min_y": 0, "max_y": 60, "size": 20,
             "random_name": "x", "blocks": [{"replace": ["tfp:rock/raw/granite"], "with": [{"block": "tfp:ore/normal_malachite/granite"}]}],
             "indicator": {"rarity": 25, "depth": 35, "underground_rarity": 1, "underground_count": 0,
               "blocks": [{"block": "tfp:ore/small_malachite"}]}}}""";
        final ClusterVeinConfig config = VeinData.parse("x", JsonParser.parseString(json).getAsJsonObject()).orElseThrow();
        assertThat(config.indicator().rarity()).isEqualTo(25);
        assertThat(config.indicator().depth()).isEqualTo(35);
        assertThat(config.indicator().blocks().values()).containsExactly("tfp:ore/small_malachite");
        final String bad = json.replace("\"depth\": 35", "\"depth\": 35, \"colour\": 1");
        assertThatThrownBy(() -> VeinData.parse("x", JsonParser.parseString(bad).getAsJsonObject()))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
