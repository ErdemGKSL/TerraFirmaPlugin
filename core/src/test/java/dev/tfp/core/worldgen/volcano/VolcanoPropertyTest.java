/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.volcano;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.chunk.ChunkData;
import dev.tfp.core.worldgen.chunk.PlaceholderRocks;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** Properties of the volcano and hot spring decisions (M2-GEN-14) over {@link VolcanoTestView}'s world. */
class VolcanoPropertyTest {
    private static final long[] SEEDS = {1L, 42L, -9876543210L, 7L};

    /** The test world with one biome everywhere. */
    private static VolcanoTestView only(int biome) {
        return new VolcanoTestView() {
            @Override
            public int biome(int x, int z) {
                return biome;
            }
        };
    }

    private static List<VolcanoBlock> chunks(Volcanoes v, VolcanoView view, int from, int to) {
        final List<VolcanoBlock> out = new ArrayList<>();
        for (int cx = from; cx < to; cx++) {
            for (int cz = from; cz < to; cz++) {
                out.addAll(v.decorate(cx, cz, view));
            }
        }
        return out;
    }

    @Test
    void decisionsAreDeterministicPerSeedAndDifferBetweenSeeds() {
        final VolcanoView view = only(TfcBiomes.CANYONS);
        final List<List<VolcanoBlock>> bySeed = new ArrayList<>();
        for (long seed : SEEDS) {
            final List<VolcanoBlock> a = chunks(new Volcanoes(seed), view, -12, 12);
            final List<VolcanoBlock> b = chunks(new Volcanoes(seed), view, -12, 12);
            assertThat(a).isEqualTo(b);
            bySeed.add(a);
        }
        assertThat(bySeed.get(0)).isNotEqualTo(bySeed.get(1));
        assertThat(bySeed).anySatisfy(blocks -> assertThat(blocks).isNotEmpty());
    }

    @Test
    void fourThreadsSharingOneWorldAgree() throws Exception {
        final Volcanoes v = new Volcanoes(42L);
        final VolcanoView view = only(TfcBiomes.CANYONS);
        final List<VolcanoBlock> expected = chunks(new Volcanoes(42L), view, -8, 8);
        final ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            final List<Future<List<VolcanoBlock>>> futures = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                futures.add(pool.submit(() -> chunks(v, view, -8, 8)));
            }
            for (Future<List<VolcanoBlock>> f : futures) {
                assertThat(f.get()).isEqualTo(expected);
            }
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void blocksHaveValidKindsRocksAndGems() {
        final int rocks = PlaceholderRocks.count();
        for (int biome : new int[] {TfcBiomes.CANYONS, TfcBiomes.HIGHLANDS, TfcBiomes.DEEP_OCEAN_TRENCH, TfcBiomes.VOLCANIC_MOUNTAIN_ISLANDS}) {
            for (long seed : SEEDS) {
                for (VolcanoBlock b : chunks(new Volcanoes(seed), only(biome), -6, 6)) {
                    assertThat(b.kind()).isIn((Object[]) VolcanoKind.values());
                    if (b.kind().hasRock()) {
                        assertThat(b.rock()).isBetween(0, rocks - 1);
                    } else {
                        assertThat(b.rock()).isEqualTo(-1);
                    }
                    assertThat(b.gem() != null).isEqualTo(b.kind() == VolcanoKind.GEM_ORE);
                    if (b.kind() == VolcanoKind.MAGMA) {
                        // only the lava spring's igneous replacers
                        assertThat(PlaceholderRocks.names().get(b.rock())).isIn("rhyolite", "basalt", "andesite", "dacite", "granite", "diorite", "gabbro");
                    }
                }
            }
        }
    }

    @Test
    void lavaOnlyWhereTfcPlacesIt() {
        // No cones and no lava spring: hot springs of spring water or air only, never lava or magma
        for (int biome : new int[] {TfcBiomes.HIGHLANDS, TfcBiomes.OLD_MOUNTAINS, TfcBiomes.PLATEAU}) {
            for (long seed : SEEDS) {
                assertThat(chunks(new Volcanoes(seed), only(biome), -10, 10))
                    .noneMatch(b -> b.kind() == VolcanoKind.LAVA || b.kind() == VolcanoKind.MAGMA);
            }
        }
        // A biome without any of these features places nothing
        for (long seed : SEEDS) {
            assertThat(chunks(new Volcanoes(seed), only(TfcBiomes.PLAINS), -10, 10)).isEmpty();
        }
        // In cone biomes, lava comes from the caldera and fissure of the chunk's own cone center, within reach of it
        int withLava = 0;
        for (long seed : SEEDS) {
            final Volcanoes v = new Volcanoes(seed);
            final VolcanoView view = only(TfcBiomes.VOLCANIC_MOUNTAIN_ISLANDS);
            for (int cx = -15; cx < 15; cx++) {
                for (int cz = -15; cz < 15; cz++) {
                    final List<VolcanoBlock> lava = v.decorate(cx, cz, view).stream().filter(b -> b.kind() == VolcanoKind.LAVA).toList();
                    if (lava.isEmpty()) {
                        continue;
                    }
                    withLava++;
                    final long center = v.volcanoCenter(cx, cz, view);
                    assertThat(center).isNotEqualTo(Long.MIN_VALUE);
                    for (VolcanoBlock b : lava) {
                        assertThat(Math.abs(b.x() - CinderCones.unpackX(center))).isLessThanOrEqualTo(30);
                        assertThat(Math.abs(b.z() - CinderCones.unpackZ(center))).isLessThanOrEqualTo(30);
                    }
                }
            }
        }
        assertThat(withLava).isPositive();
    }

    @Test
    void placeGivesOnlyTheChunksOwnBlocks() {
        final Volcanoes v = new Volcanoes(1L);
        final VolcanoView view = only(TfcBiomes.CANYONS);
        for (int cx = -4; cx < 4; cx++) {
            for (int cz = -4; cz < 4; cz++) {
                final int fx = cx;
                final int fz = cz;
                v.place(cx, cz, view, b -> assertThat(b.inChunk(fx, fz)).isTrue());
            }
        }
    }

    @Test
    void coneHeightsStayBetweenTheInputAndTheConeTop() {
        for (long seed : SEEDS) {
            final CinderCones cones = new CinderCones(seed);
            for (int x = -3000; x < 3000; x += 37) {
                for (int z = -3000; z < 3000; z += 41) {
                    final int biome = VolcanoTestView.biomeAt(x, z);
                    final double in = 60 + Math.floorMod(x + z, 30);
                    final double sampled = cones.sampleHeight(in, x, z, VolcanoTestView::biomeAt);
                    final double adjusted = cones.adjustHeight(in, x, z, VolcanoTestView::biomeAt);
                    if (sampled == CinderCones.NOT_PRESENT) {
                        assertThat(adjusted).isEqualTo(in);
                    } else {
                        assertThat(adjusted).isEqualTo(sampled);
                        // the cone's own biome is at its cell center; its base and scale bound the height
                        assertThat(adjusted).isBetween(Math.min(in, ChunkData.SEA_LEVEL_Y), Math.max(in, ChunkData.SEA_LEVEL_Y + 14 + 36 + 1e-9));
                    }
                    assertThat(cones.easing(x, z, CinderCones.frequency(biome))).isBetween(0f, 1f);
                }
            }
            // A world without cone biomes keeps every height
            for (int x = -500; x < 500; x += 13) {
                assertThat(cones.adjustHeight(70.5, x, -x, (bx, bz) -> TfcBiomes.PLAINS)).isEqualTo(70.5);
            }
        }
    }

    @Test
    void coneBiomeTablesMatchTfc() {
        assertThat(CinderCones.hasCinderCones(TfcBiomes.ACTIVE_SHIELD_VOLCANO)).isTrue();
        assertThat(CinderCones.hasCinderCones(TfcBiomes.PLAINS)).isFalse();
        assertThat(CinderCones.frequency(TfcBiomes.RIFT_VALLEY)).isEqualTo(1f / 7f);
        assertThat(CinderCones.baseHeight(TfcBiomes.DOLINE_CANYONS)).isEqualTo(14);
        assertThat(CinderCones.scaleHeight(TfcBiomes.CANYONS)).isEqualTo(36);
        assertThat(Volcanoes.springs(TfcBiomes.DEEP_OCEAN_TRENCH)).isEqualTo(Volcanoes.Springs.LAVA);
        assertThat(Volcanoes.springs(TfcBiomes.ROCKY_PLATEAU)).isEqualTo(Volcanoes.Springs.EMPTY);
        assertThat(Volcanoes.springs(TfcBiomes.VOLCANIC_MOUNTAINS)).isEqualTo(Volcanoes.Springs.ACTIVE);
        assertThat(Volcanoes.springs(TfcBiomes.ACTIVE_SHIELD_VOLCANO)).isEqualTo(Volcanoes.Springs.NONE);
    }
}
