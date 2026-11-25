/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.bellows;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** PAR-METAL-11: the stroke, the receiver offsets and the persisted data. */
class BellowsMotionTest {
    @Test
    void strokeGrowsThenShrinksThenRests() {
        assertThat(BellowsMotion.extension(100, 100)).isCloseTo(0.125f, within(1e-5f));
        assertThat(BellowsMotion.extension(105, 100)).isCloseTo(0.375f, within(1e-5f));
        assertThat(BellowsMotion.extension(109, 100)).isCloseTo(0.575f, within(1e-5f));
        assertThat(BellowsMotion.extension(110, 100)).isCloseTo(0.625f, within(1e-5f));
        assertThat(BellowsMotion.extension(115, 100)).isCloseTo(0.375f, within(1e-5f));
        assertThat(BellowsMotion.extension(119, 100)).isCloseTo(0.175f, within(1e-5f));
        assertThat(BellowsMotion.extension(120, 100)).isCloseTo(BellowsMotion.MIN_EXTENSION, within(1e-5f));
        assertThat(BellowsMotion.extension(5000, 100)).isCloseTo(BellowsMotion.MIN_EXTENSION, within(1e-5f));
    }

    @Test
    void offsetsFollowTheFacingAndItsClockwiseSide() {
        BellowsMotion.Offset forge = new BellowsMotion.Offset(1, 0, -1);
        assertThat(forge.delta("north")).containsExactly(0, -1, -1);
        assertThat(forge.delta("east")).containsExactly(1, -1, 0);
        assertThat(forge.delta("south")).containsExactly(0, -1, 1);
        assertThat(forge.delta("west")).containsExactly(-1, -1, 0);
        BellowsMotion.Offset side = new BellowsMotion.Offset(2, 1, 3);
        assertThat(side.delta("north")).containsExactly(1, 3, -2); // clockwise of north is east
        assertThat(side.delta("south")).containsExactly(-1, 3, 2);
        assertThat(BellowsMotion.DEFAULT_OFFSETS).hasSize(2);
    }

    @Test
    void dataRoundTrips() {
        BellowsData d = BellowsData.FRESH.withPush(12345L);
        assertThat(BellowsData.CODEC.decode(BellowsData.CODEC.encode(d))).isEqualTo(d);
    }
}
