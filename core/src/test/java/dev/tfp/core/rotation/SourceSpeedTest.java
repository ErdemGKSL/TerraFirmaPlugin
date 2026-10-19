/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/rotation/WaterWheelBlockEntity.java @ c3b4d9536 (clientTick, lines 77-79)
 */
package dev.tfp.core.rotation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SourceSpeedTest {

    /**
     * TFC water wheel ramp: {@code target > current ? min(target, current + step) : max(target, current - step)}, once per tick.
     * With step 0.25f, from 0 up to 1 takes four ticks, each at most 0.25 more, and stops exactly at the target. From 1 down to 0
     * it takes four ticks in the same way. A target equal to the current speed is held.
     */
    @Test
    void rampMovesByAtMostTheStepAndStopsAtTheTarget() {
        float speed = 0f;
        float[] up = new float[5];
        for (int i = 0; i < 5; i++) {
            speed = SourceSpeed.ramp(speed, 1f, 0.25f);
            up[i] = speed;
        }
        assertThat(up).containsExactly(0.25f, 0.5f, 0.75f, 1f, 1f);

        float[] down = new float[5];
        for (int i = 0; i < 5; i++) {
            speed = SourceSpeed.ramp(speed, 0f, 0.25f);
            down[i] = speed;
        }
        assertThat(down).containsExactly(0.75f, 0.5f, 0.25f, 0f, 0f);

        assertThat(SourceSpeed.ramp(0.5f, 0.5f, 0.25f)).isEqualTo(0.5f);
    }
}
