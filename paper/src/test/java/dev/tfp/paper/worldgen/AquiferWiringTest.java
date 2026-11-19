/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.aquifer.AquiferBarrierNoise;
import dev.tfp.core.worldgen.carver.Aquifer;
import dev.tfp.core.worldgen.carver.Carve;
import dev.tfp.core.worldgen.carver.TunnelCarvers;
import dev.tfp.core.worldgen.random.PositionalRandom;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * PAR-WORLD-29: the terrain fill's caves use the world's aquifer barrier noise, and no carved water or lava is left with
 * cave air below or beside it, across chunk borders ({@link TfcTerrain#settledCave}).
 */
class AquiferWiringTest {
    private static final long SEED = 7L;
    private static final int MIN_Y = -64;
    private static final int HEIGHT = 384;
    private static final int SEA = 63;
    private static final int TERRAIN_TOP = 120;

    @Test
    void barrierIsTheWorldsNoise() {
        final PositionalRandom fork = PositionalRandom.fork(new XoroshiroRandomSource(SEED));
        final AquiferBarrierNoise barrier = AquiferBarrierNoise.create(fork);
        boolean nonZero = false;
        for (int i = 0; i < 16 && !nonZero; i++) {
            nonZero = barrier.getValue(i * 13.0, i * 3.5, i * 7.0) != 0.0;
        }
        assertThat(nonZero).isTrue();
        assertThat(barrier.getValue(5, 2.5, 9)).isEqualTo(AquiferBarrierNoise.forSeed(SEED).getValue(5, 2.5, 9));
    }

    @Test
    void noCarvedFluidHasAirBelowOrBeside() {
        final PositionalRandom fork = PositionalRandom.fork(new XoroshiroRandomSource(SEED));
        final AquiferBarrierNoise barrier = AquiferBarrierNoise.create(fork);
        final Map<Long, TfcTerrain.CaveChunk> chunks = new HashMap<>();
        final TfcTerrain.CaveChunks caves = (cx, cz) -> chunks.computeIfAbsent(((long) cx << 32) | (cz & 0xFFFFFFFFL), k -> {
            final int[] surface = new int[16];
            for (int i = 0; i < 16; i++) {
                surface[i] = 30 + Math.floorMod(cx * 31 + cz * 17 + i * 11, 70); // aquifers at many levels
            }
            return new TfcTerrain.CaveChunk(cx, cz, TunnelCarvers.mask(fork, cx, cz, MIN_Y, HEIGHT),
                new Aquifer(cx, cz, MIN_Y, SEA, fork, barrier, surface), (x, y, z) -> y > MIN_Y && y <= TERRAIN_TOP);
        });
        int fluids = 0;
        int dammed = 0;
        for (int cx = 0; cx < 10; cx++) {
            for (int cz = 0; cz < 10; cz++) {
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        final int x = (cx << 4) + lx;
                        final int z = (cz << 4) + lz;
                        for (int y = MIN_Y + 1; y <= TERRAIN_TOP; y++) {
                            final Carve raw = caves.of(cx, cz).carve(x, y, z);
                            final Carve carve = TfcTerrain.settledCave(caves, x, y, z);
                            if (raw != carve) {
                                dammed++;
                                assertThat(carve).isEqualTo(Carve.KEEP);
                            }
                            if (carve != Carve.WATER && carve != Carve.LAVA) {
                                continue;
                            }
                            fluids++;
                            assertThat(TfcTerrain.settledCave(caves, x, y - 1, z)).isNotEqualTo(Carve.AIR);
                            assertThat(TfcTerrain.settledCave(caves, x + 1, y, z)).isNotEqualTo(Carve.AIR);
                            assertThat(TfcTerrain.settledCave(caves, x - 1, y, z)).isNotEqualTo(Carve.AIR);
                            assertThat(TfcTerrain.settledCave(caves, x, y, z + 1)).isNotEqualTo(Carve.AIR);
                            assertThat(TfcTerrain.settledCave(caves, x, y, z - 1)).isNotEqualTo(Carve.AIR);
                        }
                    }
                }
            }
        }
        assertThat(fluids).isPositive();
        System.out.println("PAR-WORLD-29 scan: fluids " + fluids + ", dammed " + dammed);
    }
}
