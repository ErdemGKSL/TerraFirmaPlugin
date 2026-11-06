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
    /** ServerLevel.destroyBlockProgress in the Paper 1.21.11 jar: {@code Mth.clamp(progress, 0, 10) / 10f}. */
    static float paperProgress(int stage) {
        return Math.clamp(stage, 0, 10) / 10f;
    }

    @Test
    void paperProgressEncodingMapsBackToTheVanillaStage() {
        for (int s = 0; s <= 10; s++) {
            int expected = s > 9 ? -1 : s;
            assertThat(OreOverlayListener.stage(paperProgress(s))).as("stage %d", s).isEqualTo(expected);
        }
        // The clamp sends an abort/finish (-1) as 0.0f, the same value as stage 0: the progress alone cannot clear it.
        assertThat(paperProgress(-1)).isEqualTo(0.0f);
        assertThat(OreOverlayListener.stage(paperProgress(-1))).isEqualTo(0);
        // the abort sequence that clears it again: OreOverlayServiceTest#paperAbortSequenceEndsWithThePlainOverlay
    }

    @Test
    void outOfRangeProgressIsUncracked() {
        assertThat(OreOverlayListener.stage(-0.5f)).isEqualTo(-1);
        assertThat(OreOverlayListener.stage(1.5f)).isEqualTo(-1);
        assertThat(OreOverlayListener.stage(0.999f)).isEqualTo(9);
    }
}
