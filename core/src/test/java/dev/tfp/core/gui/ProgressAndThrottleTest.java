/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.gui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** PAR-INFRA-03: progress frames against TFC's thermometer and the title refresh throttle. */
class ProgressAndThrottleTest {
    /** TFC FirepitScreen.renderBg with Heat.scaleTemperatureForGui (BRILLIANT_WHITE max 1600): the indicator's top y. */
    private static Integer tfcIndicatorTop(float temperature) {
        int temp = Math.max(0, Math.min(51, (int) (51 * temperature / 1600f)));
        return temp > 0 ? 76 - Math.min(51, temp) : null;
    }

    @Test
    void thermometerFramesPutTheIndicatorWhereTfcDrawsIt() {
        GuiImage strip = GuiImages.THERMOMETER;
        for (float t = -20f; t <= 2000f; t += 0.5f) {
            int frame = ProgressFrames.thermometer(t);
            Integer tfc = tfcIndicatorTop(t);
            if (tfc == null) {
                assertThat(frame).as("t=" + t).isZero();
            } else {
                assertThat(strip.y() + ProgressFrames.thermometerOffset(frame)).as("t=" + t).isEqualTo(tfc);
            }
        }
        assertThat(ProgressFrames.thermometer(1600f)).isEqualTo(51);
        assertThat(ProgressFrames.thermometer(31.3f)).isZero();
        assertThat(ProgressFrames.thermometer(31.4f)).isEqualTo(1);
        assertThatThrownBy(() -> ProgressFrames.thermometerOffset(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fillBarsScaleLikeTfcsIntCasts() {
        assertThat(ProgressFrames.fill(0, 10, 25)).isZero();
        assertThat(ProgressFrames.fill(10, 10, 25)).isEqualTo(24);
        assertThat(ProgressFrames.fill(5, 10, 25)).isEqualTo(12);
        assertThat(ProgressFrames.fill(20, 10, 25)).isEqualTo(24);
        assertThat(ProgressFrames.fill(-1, 10, 25)).isZero();
        assertThat(ProgressFrames.fill(1, 0, 25)).isZero();
        assertThat(ProgressFrames.fillPixels(12, 25, 24)).isEqualTo(12);
        assertThat(ProgressFrames.fillPixels(24, 25, 24)).isEqualTo(24);
        assertThatThrownBy(() -> ProgressFrames.fill(1, 1, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theThrottleSendsAtMostFourTitlesASecondAndAlwaysTheLatest() {
        TitleRefreshThrottle<String> t = new TitleRefreshThrottle<>(TitleRefreshThrottle.DEFAULT_INTERVAL_MILLIS);
        t.opened("a", 1000);
        assertThat(t.submit("a", 1010)).isInstanceOf(TitleRefreshThrottle.Idle.class);
        TitleRefreshThrottle.Decision<String> d = t.submit("b", 1100);
        assertThat(d).isEqualTo(new TitleRefreshThrottle.Schedule<String>(150));
        assertThat(t.submit("c", 1200)).isInstanceOf(TitleRefreshThrottle.Idle.class);
        assertThat(t.submit("d", 1240)).isInstanceOf(TitleRefreshThrottle.Idle.class);
        assertThat(t.flush(1250)).isEqualTo("d");
        assertThat(t.flushScheduled()).isFalse();
        // a change long after the last send goes out at once
        assertThat(t.submit("e", 2000)).isEqualTo(new TitleRefreshThrottle.SendNow<>("e"));
        // flip and flip back inside the interval: the flush finds the screen already right
        assertThat(t.submit("f", 2010)).isInstanceOf(TitleRefreshThrottle.Schedule.class);
        assertThat(t.submit("e", 2020)).isInstanceOf(TitleRefreshThrottle.Idle.class);
        assertThat(t.flush(2250)).isNull();
    }

    @Test
    void sendsNeverComeCloserThanTheInterval() {
        TitleRefreshThrottle<Integer> t = new TitleRefreshThrottle<>(250);
        t.opened(0, 0);
        long lastSend = 0;
        long flushAt = -1;
        int sends = 0;
        for (long now = 1; now <= 10_000; now += 7) {
            if (flushAt >= 0 && now >= flushAt) {
                if (t.flush(now) != null) {
                    assertThat(now - lastSend).isGreaterThanOrEqualTo(250);
                    lastSend = now;
                    sends++;
                }
                flushAt = -1;
            }
            switch (t.submit((int) now, now)) {
                case TitleRefreshThrottle.SendNow<Integer> s -> {
                    assertThat(now - lastSend).isGreaterThanOrEqualTo(250);
                    lastSend = now;
                    sends++;
                }
                case TitleRefreshThrottle.Schedule<Integer> s -> flushAt = now + s.delayMillis();
                case TitleRefreshThrottle.Idle<Integer> i -> {
                }
            }
        }
        assertThat(sends).isBetween(35, 41);
    }
}
