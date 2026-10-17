/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.ore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** Properties of the ore decision over the shipped vein data. */
class OreVeinPropertyTest {
    private static final int[][] CHUNKS = {{0, 0}, {-1, -1}, {5, -3}, {-9, 7}, {17, 23}, {-31, -2}, {2, 40}, {-64, 64}};
    private static final int MIN_Y = -64;
    private static final int MAX_Y = 320;

    /** Every decision of a chunk's grid, as the block id or "." for rock; the rock index varies with the position. */
    private static List<String> decisions(OreVeins veins, int cx, int cz, int rocks) {
        final OreVeins.Chunk chunk = veins.chunk(cx, cz);
        final List<String> out = new ArrayList<>();
        for (int lx = 0; lx < 16; lx += 3) {
            for (int lz = 0; lz < 16; lz += 3) {
                for (int y = MIN_Y; y < MAX_Y; y++) {
                    final int x = (cx << 4) + lx;
                    final int z = (cz << 4) + lz;
                    final VeinBlock b = chunk.decide(x, y, z, Math.floorMod(x * 31 + y * 7 + z, rocks));
                    out.add(b == null ? "." : b.id());
                }
            }
        }
        return out;
    }

    @Test
    void decisionsAreDeterministicPerSeedAndAcrossThreads() throws Exception {
        final List<ClusterVeinConfig> configs = VeinTestData.tagOrdered();
        final List<String> rocks = VeinTestData.rockIds();
        final ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            for (long seed : new long[] {0L, 42L, -987654321L}) {
                final OreVeins a = new OreVeins(seed, configs, rocks);
                final OreVeins b = new OreVeins(seed, configs, rocks);
                final List<Future<List<String>>> parallel = new ArrayList<>();
                for (int[] c : CHUNKS) {
                    parallel.add(pool.submit(() -> decisions(a, c[0], c[1], rocks.size())));
                }
                for (int i = CHUNKS.length - 1; i >= 0; i--) {
                    final int[] c = CHUNKS[i];
                    assertThat(decisions(b, c[0], c[1], rocks.size())).as("seed %d chunk %d,%d", seed, c[0], c[1])
                        .isEqualTo(parallel.get(i).get());
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void differentSeedsGiveDifferentVeins() {
        final List<ClusterVeinConfig> configs = VeinTestData.tagOrdered();
        final List<String> rocks = VeinTestData.rockIds();
        final List<String> s1 = new ArrayList<>();
        final List<String> s2 = new ArrayList<>();
        for (int[] c : CHUNKS) {
            s1.addAll(decisions(new OreVeins(1L, configs, rocks), c[0], c[1], rocks.size()));
            s2.addAll(decisions(new OreVeins(2L, configs, rocks), c[0], c[1], rocks.size()));
        }
        assertThat(s1).isNotEqualTo(s2);
    }

    @Test
    void outputsAreListedBlocksInsideTheHeightRangeWithAGrade() {
        final List<ClusterVeinConfig> configs = VeinTestData.tagOrdered();
        final List<String> rocks = VeinTestData.rockIds();
        final Map<String, ClusterVeinConfig> byName = new HashMap<>();
        configs.forEach(c -> byName.put(c.name(), c));
        final Map<OreGrade, Integer> grades = new EnumMap<>(OreGrade.class);
        int ores = 0;
        for (long seed : new long[] {3L, 77L}) {
            final OreVeins veins = new OreVeins(seed, configs, rocks);
            for (int cx = -6; cx < 6; cx++) {
                for (int cz = -6; cz < 6; cz++) {
                    final OreVeins.Chunk chunk = veins.chunk(cx, cz);
                    for (int lx = 0; lx < 16; lx++) {
                        for (int lz = 0; lz < 16; lz += 2) {
                            for (int y = MIN_Y; y < MAX_Y; y++) {
                                final int x = (cx << 4) + lx;
                                final int z = (cz << 4) + lz;
                                final int rock = Math.floorMod(x + 3 * z + y, rocks.size());
                                final VeinBlock b = chunk.decide(x, y, z, rock);
                                if (b == null) {
                                    continue;
                                }
                                final ClusterVeinConfig config = byName.get(b.vein());
                                assertThat(y).as("%s at y", b.id()).isBetween(config.minY(), config.maxY());
                                assertThat(config.blocks().get(rocks.get(rock)).values()).as("%s replaces %s", b.vein(), rocks.get(rock)).contains(b);
                                assertThat(b.grade()).isIn((Object[]) OreGrade.values());
                                assertThat(b.kind()).isIn(VeinBlock.Kind.ORE, VeinBlock.Kind.DEPOSIT);
                                assertThat(b.id()).endsWith("/" + rocks.get(rock).substring("tfp:rock/raw/".length()));
                                if (b.kind() == VeinBlock.Kind.ORE) {
                                    assertThat(veins.oreIndex(b)).isBetween(0, veins.oreIds().size() - 1);
                                    ores++;
                                } else {
                                    assertThat(veins.oreIndex(b)).isEqualTo(-1);
                                }
                                grades.merge(b.grade(), 1, Integer::sum);
                            }
                        }
                    }
                }
            }
        }
        assertThat(ores).as("ore blocks found").isGreaterThan(1000);
        assertThat(grades.keySet()).as("every grade occurs").containsExactlyInAnyOrder(OreGrade.values());
    }

    @Test
    void graderatiosFollowTheWeights() {
        // normal_malachite on marble: poor 35, normal 40, rich 25 (TFC's data)
        final ClusterVeinConfig malachite = VeinTestData.clusterConfigs().get("normal_malachite");
        final Weighted<VeinBlock> w = malachite.blocks().get("tfp:rock/raw/marble");
        final Map<OreGrade, Integer> counts = new EnumMap<>(OreGrade.class);
        final int n = 100_000;
        for (int i = 0; i < n; i++) {
            counts.merge(w.get((i + 0.5) / n).grade(), 1, Integer::sum);
        }
        assertThat(counts.get(OreGrade.POOR)).isEqualTo(35_000);
        assertThat(counts.get(OreGrade.NORMAL)).isEqualTo(40_000);
        assertThat(counts.get(OreGrade.RICH)).isEqualTo(25_000);
    }

    @Test
    void decideRefusesPositionsOutsideTheChunk() {
        final OreVeins veins = new OreVeins(0L, VeinTestData.tagOrdered(), VeinTestData.rockIds());
        assertThatThrownBy(() -> veins.chunk(0, 0).decide(16, 0, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> veins.chunk(0, 0).decide(0, 0, -1, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void veinBlockIdsParse() {
        assertThat(VeinBlock.parse("tfp:ore/rich_native_copper/basalt", "v"))
            .isEqualTo(new VeinBlock("tfp:ore/rich_native_copper/basalt", VeinBlock.Kind.ORE, "native_copper", OreGrade.RICH, "v"));
        assertThat(VeinBlock.parse("tfp:ore/cinnabar/quartzite", "v").grade()).isEqualTo(OreGrade.UNGRADED);
        assertThat(VeinBlock.parse("tfp:deposit/cassiterite/granite", "v").kind()).isEqualTo(VeinBlock.Kind.DEPOSIT);
        assertThat(VeinBlock.parse("tfp:ore/poor_hematite/shale", "v").mineral()).isEqualTo("hematite");
    }

    @Test
    void tagOrderListsTheClusterVeins() {
        final List<ClusterVeinConfig> ordered = VeinTestData.tagOrdered();
        assertThat(ordered).hasSize(33);
        assertThat(ordered.getFirst().name()).isEqualTo("surface_native_copper");
        // every ore id of the data is in the palette
        final OreVeins veins = new OreVeins(0L, ordered, VeinTestData.rockIds());
        assertThat(veins.oreIds()).allMatch(id -> id.startsWith("tfp:ore/")).doesNotHaveDuplicates();
        assertThat(veins.oreIds()).contains("tfp:ore/poor_malachite/marble", "tfp:ore/rich_native_gold/granite", "tfp:ore/cryolite/granite");
    }
}
