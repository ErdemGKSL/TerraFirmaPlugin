/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/rotation/WaterWheelBlockEntity.java @ c3b4d9536 (serverTick and
 *       clientTick: the stepped reference below replays them tick by tick)
 */
package dev.tfp.core.waterwheel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.power.WaterWheel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

class WaterWheelSampleTest {

    /** TFC's per-tick loop: clientTick every tick, and at every 40th tick the sample after it. */
    private static float[] stepped(List<Optional<Float>> samples) {
        float speed = 0f;
        float target = 0f;
        boolean obstructed = false;
        float[] out = new float[samples.size()];
        for (int s = 0; s < samples.size(); s++) {
            for (int t = 0; t < WaterWheel.SAMPLE_INTERVAL_TICKS; t++) {
                speed = obstructed ? 0f
                    : target > speed ? Math.min(target, speed + WaterWheel.LERP_SPEED) : Math.max(target, speed - WaterWheel.LERP_SPEED);
            }
            Optional<Float> flow = samples.get(s);
            if (flow.isEmpty()) {
                obstructed = true;
            } else {
                obstructed = false;
                target = flow.get() * WaterWheel.MAX_SPEED / WaterWheel.MAX_FLOW;
            }
            out[s] = speed;
        }
        return out;
    }

    /** Lazy-vs-stepped: one closed-form step per sample equals 40 TFC ticks, over random flows and obstructions. */
    @Test
    void lazySamplesMatchTheSteppedTicks() {
        Random random = new Random(42);
        List<Optional<Float>> samples = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            samples.add(random.nextInt(8) == 0 ? Optional.empty() : Optional.of(random.nextFloat() * 20f - 10f));
        }
        float[] expected = stepped(samples);
        WaterWheelSample lazy = WaterWheelSample.FRESH;
        for (int i = 0; i < samples.size(); i++) {
            lazy = lazy.sample(WaterWheel.SAMPLE_INTERVAL_TICKS, samples.get(i));
            assertThat(lazy.speed()).as("sample %d", i).isCloseTo(expected[i], within(1e-5f));
        }
    }

    /** Full flow from rest: TFC needs 400 ticks (10 samples) of LERP_SPEED to reach MAX_SPEED; the first sample only sets it. */
    @Test
    void fullFlowReachesMaxSpeedAfterTenMoreSamples() {
        WaterWheelSample s = WaterWheelSample.FRESH.sample(40, Optional.of(-10f));
        assertThat(s.speed()).isZero();
        assertThat(s.target()).isCloseTo(-WaterWheel.MAX_SPEED, within(1e-7f));
        for (int i = 0; i < 9; i++) {
            s = s.sample(40, Optional.of(-10f));
        }
        assertThat(s.speed()).isCloseTo(-WaterWheel.MAX_SPEED * 0.9f, within(1e-6f));
        s = s.sample(40, Optional.of(-10f));
        assertThat(s.speed()).isCloseTo(-WaterWheel.MAX_SPEED, within(1e-7f));
        assertThat(s.sample(40, Optional.of(-10f)).speed()).isEqualTo(-WaterWheel.MAX_SPEED);
    }

    /** TFC: an obstructed wheel's speed is set to 0 each tick, its target kept; clearing it ramps up from 0 again. */
    @Test
    void obstructionStopsAndKeepsTheTarget() {
        WaterWheelSample s = new WaterWheelSample(0.05f, 0.05f, false).sample(40, Optional.empty());
        assertThat(s.obstructed()).isTrue();
        assertThat(s.speed()).isEqualTo(0.05f); // the ramp before the sample ran unobstructed
        assertThat(s.target()).isEqualTo(0.05f);
        s = s.sample(40, Optional.of(10f));
        assertThat(s.speed()).isZero();
        assertThat(s.obstructed()).isFalse();
        s = s.sample(40, Optional.of(10f));
        assertThat(s.speed()).isCloseTo(40 * WaterWheel.LERP_SPEED, within(1e-7f));
    }

    @Test
    void skippedSampleOnlyRamps() {
        WaterWheelSample s = new WaterWheelSample(0f, WaterWheel.MAX_SPEED, false).skipped(40);
        assertThat(s.target()).isEqualTo(WaterWheel.MAX_SPEED);
        assertThat(s.obstructed()).isFalse();
        assertThat(s.speed()).isCloseTo(40 * WaterWheel.LERP_SPEED, within(1e-7f));
        assertThatThrownBy(() -> s.advance(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
