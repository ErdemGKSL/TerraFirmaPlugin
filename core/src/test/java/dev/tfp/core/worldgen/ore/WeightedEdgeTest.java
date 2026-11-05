/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.ore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * PAR-WORLD-18: the weighted pick at the top of its range. The 1.21 {@code XoroshiroRandomSource.nextDouble} converts its 53
 * bits through a float, so a draw can round up to exactly 1.0, and {@code 1.0 * total} has no running total strictly above it.
 * (A draw below 1.0 cannot reach the total: {@code r * total} with {@code r <= 1 - 2^-53} rounds to at most the double below
 * the total.) TFC's {@code backingMap.higherEntry(value)} is null there and its {@code getValue()} throws; we return the last
 * element (ledger divergence).
 */
class WeightedEdgeTest {
    private static final Weighted<String> ABC = Weighted.of(List.of("a", "b", "c"), List.of(1.0, 1.0, 1.0));

    @Test
    void drawOfOneReturnsTheLastElement() {
        assertThat(ABC.get(1.0)).isEqualTo("c");
        assertThat(Weighted.of(List.of("x", "y"), List.of(0.25, 0.75)).get(1.0)).isEqualTo("y");
    }

    @Test
    void drawJustBelowOneReturnsTheLastElement() {
        final double r = Math.nextDown(1.0);
        assertThat(r * 3.0).isLessThan(3.0);
        assertThat(ABC.get(r)).isEqualTo("c");
    }

    @Test
    void floatNextDoubleCanReturnExactlyOne() {
        // XoroshiroRandomSource.nextDouble of 1.21.11: (double) ((float) nextBits(53) * 1.110223E-16f)
        final long topBits = (1L << 53) - 1;
        final double draw = (double) ((float) topBits * 1.110223E-16f);
        assertThat(draw).isEqualTo(1.0);
        assertThat(ABC.get(draw)).isEqualTo("c");
    }

    @Test
    void ordinaryDrawsAreUnchanged() {
        assertThat(ABC.get(0.0)).isEqualTo("a");
        assertThat(ABC.get(0.34)).isEqualTo("b");
        assertThat(ABC.get(0.99)).isEqualTo("c");
        assertThat(ABC.get(1.0 / 3.0)).isEqualTo("b");
    }

    @Test
    void allZeroWeightsStillThrow() {
        final Weighted<String> none = Weighted.of(List.of("a", "b"), List.of(0.0, 0.0));
        assertThatThrownBy(() -> none.get(0.5)).isInstanceOf(IllegalStateException.class);
    }

    /** The chunk the PAR-WORLD-17 census saw refused: seed 1, chunk -62,-166. Every rock at every vein position decides. */
    @Test
    void seedOneChunkMinus62Minus166Decides() {
        final OreVeins veins = new OreVeins(1L, VeinTestData.tagOrdered(), VeinTestData.rockIds());
        final OreVeins.Chunk chunk = veins.chunk(-62, -166);
        final int rocks = VeinTestData.rockIds().size();
        assertThatCode(() -> {
            for (OreVeins.Box box : chunk.boxes()) {
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    for (int y = box.minY(); y <= box.maxY(); y++) {
                        for (int z = box.minZ(); z <= box.maxZ(); z++) {
                            for (int rock = 0; rock < rocks; rock++) {
                                chunk.decide(x, y, z, rock);
                            }
                        }
                    }
                }
            }
        }).doesNotThrowAnyException();
    }
}
