/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.deposit;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.groundcover.SyntheticView;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** M2-GEN-11: properties of the deposits (ore deposits in riverbeds, clay and peat discs) over many chunks and seeds. */
class DepositPropertyTest {
    private static final long[] SEEDS = {0L, 3L, 42L, -123456789L};
    private static final Deposits.MaterialAt MATERIALS = SyntheticView::material;

    private static List<DepositBlock> chunk(long seed, int chunkX, int chunkZ, int biome) {
        return new Deposits(seed).chunk(chunkX, chunkZ, new SyntheticView(biome), MATERIALS);
    }

    @Test
    void depositsAreDeterministicPerSeedAndAcrossThreads() {
        for (long seed : SEEDS) {
            final List<List<DepositBlock>> serial = IntStream.range(0, 36).mapToObj(i -> chunk(seed, i % 6 - 3, i / 6 - 3, TfcBiomes.RIVER)).toList();
            final List<List<DepositBlock>> parallel = IntStream.range(0, 36).parallel()
                .mapToObj(i -> chunk(seed, i % 6 - 3, i / 6 - 3, TfcBiomes.RIVER)).toList();
            assertThat(parallel).isEqualTo(serial);
        }
    }

    @Test
    void riverbedsGetOreDepositsInsideTheirChunk() {
        final Set<String> minerals = new HashSet<>();
        final Set<DepositKind> kinds = EnumSet.noneOf(DepositKind.class);
        for (long seed : SEEDS) {
            for (int cx = -5; cx < 5; cx++) {
                for (int cz = -5; cz < 5; cz++) {
                    final SyntheticView view = new SyntheticView(TfcBiomes.RIVER);
                    final Set<Long> seen = new HashSet<>();
                    for (DepositBlock b : chunk(seed, cx, cz, TfcBiomes.RIVER)) {
                        assertThat(b.x() >> 4).isEqualTo(cx);
                        assertThat(b.z() >> 4).isEqualTo(cz);
                        assertThat(seen.add(((long) b.x() << 40) ^ ((long) b.y() << 20) ^ b.z())).as("one block per position").isTrue();
                        assertThat(b.replaced()).isEqualTo(SyntheticView.material(b.x(), b.y(), b.z()));
                        kinds.add(b.kind());
                        switch (b.kind()) {
                            case ORE_DEPOSIT -> {
                                minerals.add(b.mineral());
                                if (b.feature().endsWith("_deep_deposit")) {
                                    // A uniform origin in [40, 63], three blocks thick
                                    assertThat(b.replaced()).isEqualTo(DepositMaterial.RAW_ROCK);
                                    assertThat(b.y()).isBetween(40 - 3, 63 + 3);
                                } else {
                                    // On the riverbed: the origin is the ocean floor of a column within 3 blocks
                                    assertThat(b.replaced()).isEqualTo(DepositMaterial.GRAVEL);
                                    assertThat(b.y()).isBetween(58 + 1 - 2, 68 + 1 + 2);
                                }
                            }
                            case CLAY, PEAT -> assertThat(b.replaced()).isIn(DepositMaterial.DIRT, DepositMaterial.MUD);
                            case CLAY_GRASS, PEAT_GRASS -> assertThat(b.replaced()).isEqualTo(DepositMaterial.GRASS);
                        }
                        if (b.kind() != DepositKind.ORE_DEPOSIT) {
                            assertThat(b.mineral()).isNull();
                            // Soil discs sit on the world surface, at most 7 blocks deep
                            assertThat(b.y()).isBetween(64 - 7, 69 + 7);
                        }
                    }
                }
            }
        }
        assertThat(minerals).containsExactlyInAnyOrder("native_copper", "native_gold", "native_silver", "cassiterite");
        assertThat(kinds).contains(DepositKind.ORE_DEPOSIT, DepositKind.CLAY, DepositKind.CLAY_GRASS);
    }

    @Test
    void oreDepositsOnlyInRiverAndLakeBiomes() {
        for (long seed : SEEDS) {
            for (int cx = -4; cx < 4; cx++) {
                for (int cz = -4; cz < 4; cz++) {
                    assertThat(chunk(seed, cx, cz, TfcBiomes.PLAINS)).allMatch(b -> b.kind() != DepositKind.ORE_DEPOSIT);
                    assertThat(chunk(seed, cx, cz, TfcBiomes.OCEAN)).isEmpty();
                }
            }
        }
        final Deposits.Feature copper = Deposits.FEATURES.getFirst();
        assertThat(Deposits.placesIn(copper, TfcBiomes.RIVER)).isTrue();
        assertThat(Deposits.placesIn(copper, TfcBiomes.LAKE)).isTrue();
        assertThat(Deposits.placesIn(copper, TfcBiomes.PLAINS)).isFalse();
        assertThat(Deposits.placesIn(Deposits.FEATURES.getLast(), TfcBiomes.PLAINS)).isTrue();
        assertThat(Deposits.placesIn(Deposits.FEATURES.getLast(), TfcBiomes.SHORE)).isFalse();
    }

    @Test
    void discShapesStayWithinTheirRadiusAndHeight() {
        for (Deposits.Feature feature : Deposits.FEATURES) {
            final SoilDisc disc = feature.disc();
            for (int i = 0; i < 50; i++) {
                final XoroshiroRandomSource random = new XoroshiroRandomSource(i * 31L + feature.feature().hashCode());
                final int[] visited = {0};
                disc.shape(random, 100, 50, -100, (x, y, z) -> {
                    assertThat(Math.abs(x - 100)).isLessThanOrEqualTo(disc.maxRadius());
                    assertThat(Math.abs(z + 100)).isLessThanOrEqualTo(disc.maxRadius());
                    assertThat(Math.abs(y - 50)).isLessThanOrEqualTo(disc.height());
                    visited[0]++;
                });
                assertThat(visited[0]).isLessThanOrEqualTo((2 * disc.maxRadius() + 1) * (2 * disc.maxRadius() + 1) * (2 * disc.height() + 1));
            }
        }
        final Metaballs2D balls = Metaballs2D.simple(new XoroshiroRandomSource(9L), 10);
        assertThat(balls.balls()).isBetween(3, 7);
        assertThat(balls.inside(0, 0) || balls.sample(0, 0) > 0).isTrue();
    }
}
