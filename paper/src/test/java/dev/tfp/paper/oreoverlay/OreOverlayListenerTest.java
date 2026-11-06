/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.oreoverlay;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The crack stage of {@code BlockBreakProgressUpdateEvent#getProgress} in Paper 1.21.11's encoding. */
class OreOverlayListenerTest {
    @Test
    void paperProgressEncodingMapsBackToTheVanillaStage() {
        // ServerLevel.destroyBlockProgress fires the event with progress = stage / 10f (i2f, ldc 10.0f, fdiv)
        for (int s = -1; s <= 10; s++) {
            int expected = s < 0 || s > 9 ? -1 : s;
            assertThat(OreOverlayListener.stage(s / 10f)).as("stage %d", s).isEqualTo(expected);
        }
    }

    @Test
    void outOfRangeProgressIsUncracked() {
        assertThat(OreOverlayListener.stage(-0.5f)).isEqualTo(-1);
        assertThat(OreOverlayListener.stage(1.5f)).isEqualTo(-1);
        assertThat(OreOverlayListener.stage(0.999f)).isEqualTo(9);
    }
}
