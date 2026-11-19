/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.berry;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** PAR-FARM-08: the stored state of a fruit tree block. */
class FruitTreeStateTest {
    @Test
    void codecRoundTrips() {
        FruitTreeState s = new FruitTreeState(-12, 70, 345, 1_000L, 2_000L, 61, -3.5f, false);
        assertThat(FruitTreeState.CODEC.decode(FruitTreeState.CODEC.encode(s))).isEqualTo(s);
        assertThat(FruitTreeState.CODEC.fromBytes(FruitTreeState.CODEC.toBytes(s))).isEqualTo(s);
    }

    @Test
    void withersKeepTheRest() {
        FruitTreeState s = new FruitTreeState(1, 2, 3, 4L, 5L, 6, 7f, true);
        assertThat(s.withLastUpdate(9L)).isEqualTo(new FruitTreeState(1, 2, 3, 9L, 5L, 6, 7f, true));
        assertThat(s.withLastPicked(9L).lastUpdate()).isEqualTo(4L);
        assertThat(s.withStem(7, 8, 9).stemY()).isEqualTo(8);
        assertThat(s.withEnvironment(10, 1f, false).northern()).isFalse();
    }
}
