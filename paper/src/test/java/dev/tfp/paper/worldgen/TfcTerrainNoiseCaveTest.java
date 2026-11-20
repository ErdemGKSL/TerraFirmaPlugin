/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.carver.Aquifer;
import dev.tfp.core.worldgen.carver.Carve;
import dev.tfp.core.worldgen.carver.TunnelCarvers;
import dev.tfp.core.worldgen.carver.TunnelMask;
import dev.tfp.core.worldgen.density.NoiseCaves;
import dev.tfp.core.worldgen.random.PositionalRandom;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** PAR-WORLD-24: the density caves in the terrain's cave decision. */
class TfcTerrainNoiseCaveTest {
    private static final int MIN_Y = -64;
    private static final int HEIGHT = 384;
    private static final int TOP = 80;

    private static Aquifer aquifer(PositionalRandom fork, int cx, int cz) {
        final int[] surface = new int[16];
        Arrays.fill(surface, TOP);
        return new Aquifer(cx, cz, MIN_Y, 63, fork, (x, y, z) -> 0.0, surface);
    }

    @Test
    void aBlockIsACaveOnlyWhereTheCaveDensityIsNotPositive() {
        final Aquifer aquifer = aquifer(PositionalRandom.fork(new XoroshiroRandomSource(3L)), 0, 0);
        assertThat(TfcTerrain.noiseCarve(0.5, 0.01, aquifer, 3, 10, 3)).isEqualTo(Carve.KEEP);
        assertThat(TfcTerrain.noiseCarve(0.5, -0.2, aquifer, 3, 10, 3)).isEqualTo(aquifer.sample(3, 10, 3, -0.2));
        // the terrain density bounds the value the aquifer sees: min(terrain, caves)
        assertThat(TfcTerrain.noiseCarve(-0.4, -0.2, aquifer, 3, 10, 3)).isEqualTo(aquifer.sample(3, 10, 3, -0.4));
    }

    @Test
    void densityCavesComeBeforeTheCarversAndCoverTfcsOpenMask() {
        final long seed = 42L;
        final PositionalRandom fork = PositionalRandom.fork(new XoroshiroRandomSource(seed));
        final NoiseCaves caves = new NoiseCaves(fork);
        final Map<Carve, Integer> counts = new EnumMap<>(Carve.class);
        int open = 0;
        for (int cx = 0; cx < 3; cx++) {
            final NoiseCaves.Chunk grid = caves.chunk(cx, 0);
            final TunnelMask mask = TunnelCarvers.mask(fork, cx, 0, MIN_Y, HEIGHT);
            final Aquifer aquifer = aquifer(fork, cx, 0);
            final Aquifer reference = aquifer(fork, cx, 0);
            final TfcTerrain.CaveChunk chunk = new TfcTerrain.CaveChunk(cx, 0, mask, aquifer, (x, y, z) -> y > MIN_Y && y <= TOP,
                MIN_Y, HEIGHT, (x, y, z) -> y <= MIN_Y || y > TOP ? Carve.KEEP
                    : TfcTerrain.noiseCarve(1.0, grid.density(x, y, z), aquifer, x, y, z));
            for (int i = 0; i < 256; i++) {
                final int x = (cx << 4) + (i & 15);
                final int z = i >> 4;
                for (int y = MIN_Y + 1; y <= TOP; y++) {
                    final double density = grid.density(x, y, z);
                    final Carve carve = chunk.carve(x, y, z);
                    if (density <= 0) {
                        open++;
                        assertThat(carve).isEqualTo(reference.sample(x, y, z, Math.min(1.0, density)));
                        assertThat(chunk.noise(x, y, z)).isEqualTo(carve);
                    } else {
                        assertThat(chunk.noise(x, y, z)).isEqualTo(Carve.KEEP);
                    }
                    counts.merge(carve, 1, Integer::sum);
                }
            }
        }
        assertThat(open).isPositive();
        assertThat(counts.getOrDefault(Carve.AIR, 0) + counts.getOrDefault(Carve.WATER, 0)).isPositive();
    }

    @Test
    void theNoiseSamplerForkIsTheAquifersAsInTfc() {
        // TFC's NoiseSampler wires the cave noises and the aquifer on one factory: same seed, same caves
        final NoiseCaves a = NoiseCaves.forSeed(5L);
        final NoiseCaves b = new NoiseCaves(PositionalRandom.fork(new XoroshiroRandomSource(5L)));
        assertThat(a.chunk(2, 2).density(40, 0, 40)).isEqualTo(b.chunk(2, 2).density(40, 0, 40));
    }
}
