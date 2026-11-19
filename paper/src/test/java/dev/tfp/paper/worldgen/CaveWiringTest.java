/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.carver.Aquifer;
import dev.tfp.core.worldgen.carver.Carve;
import dev.tfp.core.worldgen.carver.CaveCarver;
import dev.tfp.core.worldgen.carver.TunnelCarvers;
import dev.tfp.core.worldgen.carver.TunnelMask;
import dev.tfp.core.worldgen.random.PositionalRandom;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** PAR-WORLD-28: the terrain fill's cave carves (TFC's carver pass over the chunk's tunnel mask) and the carved column top. */
class CaveWiringTest {
    private static final long SEED = 42L;
    private static final int MIN_Y = -64;
    private static final int HEIGHT = 384;
    private static final int SEA = 63;
    /** The fake terrain: replaceable blocks up to this y, open above. */
    private static final int TERRAIN_TOP = 80;
    private static final Aquifer.BarrierNoise NO_BARRIER = (x, y, z) -> 0.0;

    private static Aquifer aquifer(PositionalRandom fork, int cx, int cz) {
        final int[] surface = new int[16];
        Arrays.fill(surface, TERRAIN_TOP);
        return new Aquifer(cx, cz, MIN_Y, SEA, fork, NO_BARRIER, surface);
    }

    @Test
    void carvesExactlyTheMaskedReplaceablePositions() {
        final PositionalRandom fork = PositionalRandom.fork(new XoroshiroRandomSource(SEED));
        int cx = 0;
        TunnelMask mask = TunnelCarvers.mask(fork, cx, 0, MIN_Y, HEIGHT);
        while (mask.isEmpty() || !hasLowPositions(mask)) {
            cx++;
            mask = TunnelCarvers.mask(fork, cx, 0, MIN_Y, HEIGHT);
        }
        final TfcTerrain.CaveChunk caves = new TfcTerrain.CaveChunk(cx, 0, mask, aquifer(fork, cx, 0),
            (x, y, z) -> y > MIN_Y && y <= TERRAIN_TOP);
        final Aquifer expectedAquifer = aquifer(fork, cx, 0);
        final Map<Carve, Integer> counts = new EnumMap<>(Carve.class);
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int y = MIN_Y; y < MIN_Y + HEIGHT; y++) {
                    final int x = (cx << 4) + lx;
                    final Carve carve = caves.carve(x, y, lz);
                    final boolean carvable = mask.contains(lx, y, lz) && y > MIN_Y && y <= TERRAIN_TOP;
                    if (!carvable) {
                        assertThat(carve).as("%d %d %d", x, y, lz).isEqualTo(Carve.KEEP);
                        continue;
                    }
                    if (y <= MIN_Y + CaveCarver.LAVA_ABOVE_BOTTOM) {
                        assertThat(carve).isEqualTo(Carve.LAVA);
                    }
                    assertThat(carve).isEqualTo(CaveCarver.carve(expectedAquifer, x, y, lz, true));
                    // the cached decision is stable
                    assertThat(caves.carve(x, y, lz)).isEqualTo(carve);
                    counts.merge(carve, 1, Integer::sum);
                }
            }
        }
        assertThat(counts.getOrDefault(Carve.AIR, 0)).isPositive();
        assertThat(counts.getOrDefault(Carve.LAVA, 0)).isPositive();
    }

    private static boolean hasLowPositions(TunnelMask mask) {
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int y = MIN_Y + 1; y <= MIN_Y + CaveCarver.LAVA_ABOVE_BOTTOM; y++) {
                    if (mask.contains(lx, y, lz)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Test
    void carvedTopSkipsOpenedBlocks() {
        // a cave breaks the surface from 70 down to 64: the column's top (what the decorations stand on) is 63
        assertThat(TfcTerrain.carvedTop(70, MIN_Y, y -> y >= 64 && y <= 70)).isEqualTo(63);
        assertThat(TfcTerrain.carvedTop(70, MIN_Y, y -> y < 60)).isEqualTo(70);
        assertThat(TfcTerrain.carvedTop(70, MIN_Y, y -> true)).isEqualTo(MIN_Y);
    }
}
