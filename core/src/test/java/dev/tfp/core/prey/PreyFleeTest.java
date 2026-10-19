/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.prey;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * Property checks for {@link PreyFlee}: every retreat draw stays in the TFC range, the same seed gives the same
 * retreat, the flee target is always {@code 15 * scale} blocks from the prey, and the stop rule is monotone in the distance.
 * The bounds come from {@code PreyAi.RETREAT_DURATION} and {@code PreyAi.AVOID_RANGE} at c3b4d9536.
 */
class PreyFleeTest {

    @Test
    void everyRetreatDrawIsInsideTheInclusiveRange() {
        // the offset range is 0 to 300 inclusive, so both ends are reachable and nothing leaves [140, 440]
        final Random random = new Random(7L);
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int i = 0; i < 20_000; i++) {
            final int ticks = PreyFlee.retreatTicks(random);
            min = Math.min(min, ticks);
            max = Math.max(max, ticks);
            assertThat(ticks).isBetween(PreyFlee.RETREAT_MIN_TICKS, PreyFlee.RETREAT_MAX_TICKS);
        }
        assertThat(min).isEqualTo(PreyFlee.RETREAT_MIN_TICKS);
        assertThat(max).isEqualTo(PreyFlee.RETREAT_MAX_TICKS);
    }

    @Test
    void sameSeedGivesTheSameRetreat() {
        assertThat(PreyFlee.retreatTicks(new Random(42L))).isEqualTo(PreyFlee.retreatTicks(new Random(42L)));
    }

    @Test
    void fleeTargetIsRadiusScaleFromThePreyAtAnyOffset() {
        final Position prey = new Position(3.5, 70, -2.25);
        final Position threat = new Position(-1, 70, 4);
        for (double scale : new double[] {0.1, 0.5, 1.0}) {
            for (int i = 0; i < 64; i++) {
                final double offset = -Math.PI + i * (2 * Math.PI / 64);
                final Position target = PreyFlee.fleeTarget(prey, threat, scale, offset);
                assertThat(Math.hypot(target.x() - prey.x(), target.z() - prey.z()))
                    .isCloseTo(PreyFlee.FLEE_RADIUS_BLOCKS * scale, org.assertj.core.api.Assertions.within(1e-9));
                assertThat(target.y()).isEqualTo(prey.y());
            }
        }
    }

    @Test
    void stopRuleIsMonotoneInTheDistance() {
        final FleeState flee = PreyFlee.startFlee(0, "tfc:boar", 300);
        boolean stopped = false;
        for (double d = 0; d <= 900; d += 0.5) {
            final boolean stop = PreyFlee.wantsToStopFleeing(flee, 0, d * d);
            // once it stops at a distance it keeps stopping further out
            if (stopped) {
                assertThat(stop).isTrue();
            }
            stopped = stop;
        }
        assertThat(stopped).isTrue();
    }

    @Test
    void offsetOutsideTheRangeIsRejected() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> PreyFlee.fleeTarget(new Position(0, 0, 0), new Position(1, 0, 0), 0.0, 0.0))
            .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> PreyFlee.fleeTarget(new Position(0, 0, 0), new Position(1, 0, 0), 1.5, 0.0))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
