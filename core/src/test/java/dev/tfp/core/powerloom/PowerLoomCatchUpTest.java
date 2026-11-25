/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.powerloom;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.machine.PowerLoomRules;
import dev.tfp.core.powerloom.PowerLoomCatchUp.Result;
import dev.tfp.core.rotation.Rotation;
import dev.tfp.core.world.Direction;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * PAR-MECH-08: the lazy catch-up against TFC's per-tick power loom. The reference is {@link PowerLoomRules#tick} stepped one
 * tick at a time (the golden test of that class pins it to TFC's code); the catch-up must equal it however the ticks are split
 * between looks.
 */
class PowerLoomCatchUpTest {
    /** Two yarn woven in three steps, 0.1 rad per tick: pushes at 16, 48, 80 (32 and 64 are refused), finished at 100. */
    private static final PowerLoomState LOOM = PowerLoomState.FRESH.withRecipe(3, 2, 2).withSpeed(0.1f);

    private static PowerLoomState stepped(PowerLoomState start, long from, long to) {
        PowerLoomState state = start;
        for (long t = from + 1; t <= to; t++) {
            Rotation rotation = state.speed() == 0f ? null : Rotation.of(Direction.NORTH, state.speed());
            PowerLoomRules.Tick tick = PowerLoomRules.tick(state.recipe(), state.slotCount(), rotation, state.rules(), t);
            state = state.with(tick.state(), tick.pushed() ? 1 : 0);
            if (tick.finished()) {
                state = state.withRecipe(0, 0, 0);
            }
        }
        return state;
    }

    @Test
    void theGoldenWeaveFinishesAtTickOneHundredAfterThreePushes() {
        Result r = PowerLoomCatchUp.advance(LOOM, 0, 100);
        assertThat(r.finishedAt()).isEqualTo(100);
        assertThat(r.pushes()).isEqualTo(3);
        assertThat(r.state().progress()).isEqualTo(3);
        assertThat(r.state().steps()).isZero(); // the yarn is used up: no recipe after the finish
        assertThat(r.state().pending()).isFalse();
    }

    @Test
    void theFirstPushIsAtTickSixteenAndItsStepCountsAtThirtySix() {
        Result at16 = PowerLoomCatchUp.advance(LOOM, 0, 16);
        assertThat(at16.pushes()).isEqualTo(1);
        assertThat(at16.state().pending()).isTrue();
        assertThat(at16.state().lastPushed()).isEqualTo(16);
        assertThat(PowerLoomCatchUp.advance(LOOM, 0, 35).state().progress()).isZero();
        assertThat(PowerLoomCatchUp.advance(LOOM, 0, 36).state().progress()).isEqualTo(1);
        // the push at 32 is refused by the waiting step but still resets the ticker
        Result at32 = PowerLoomCatchUp.advance(LOOM, 0, 32);
        assertThat(at32.pushes()).isEqualTo(1);
        assertThat(at32.state().ticker()).isZero();
    }

    @Test
    void anyTimeSplitGivesTheSameStateAsSteppingOneTickAtATime() {
        Random random = new Random(7);
        for (int run = 0; run < 200; run++) {
            float speed = 0.02f + random.nextFloat() * 0.3f;
            int steps = 1 + random.nextInt(6);
            int count = 1 + random.nextInt(4);
            PowerLoomState start = PowerLoomState.FRESH.withRecipe(steps, count, count).withSpeed(random.nextBoolean() ? speed : -speed);
            long horizon = 1 + random.nextInt(900);
            PowerLoomState expected = stepped(start, 0, horizon);
            PowerLoomState lazy = start;
            long at = 0;
            while (at < horizon) {
                long next = Math.min(horizon, at + 1 + random.nextInt((int) PowerLoomCatchUp.MAX_GAP));
                lazy = PowerLoomCatchUp.advance(lazy, at, next).state();
                at = next;
            }
            assertThat(lazy).as("run %d (speed %s, %d steps, %d yarn)", run, speed, steps, count).isEqualTo(expected);
        }
    }

    @Test
    void negativeSpeedWeavesAsFastAsPositive() {
        Result forward = PowerLoomCatchUp.advance(LOOM, 0, 100);
        Result backward = PowerLoomCatchUp.advance(LOOM.withSpeed(-0.1f), 0, 100);
        assertThat(backward.finishedAt()).isEqualTo(forward.finishedAt());
        assertThat(backward.pushes()).isEqualTo(forward.pushes());
    }

    @Test
    void withoutRotationOrYarnNothingIsPushed() {
        assertThat(PowerLoomCatchUp.advance(LOOM.withSpeed(0f), 0, 90).pushes()).isZero();
        PowerLoomState missing = PowerLoomState.FRESH.withRecipe(3, 2, 1).withSpeed(0.1f); // one yarn of two
        assertThat(PowerLoomCatchUp.advance(missing, 0, 90).pushes()).isZero();
        assertThat(PowerLoomCatchUp.advance(PowerLoomState.FRESH.withSpeed(0.1f), 0, 90).pushes()).isZero(); // no recipe
    }

    @Test
    void aWaitingStepStillSettlesWithoutRotation() {
        PowerLoomState pending = PowerLoomCatchUp.advance(LOOM, 0, 16).state().withSpeed(0f);
        assertThat(PowerLoomCatchUp.advance(pending, 16, 40).state().progress()).isEqualTo(1);
        assertThat(PowerLoomCatchUp.advance(pending, 16, 40).state().pending()).isFalse();
    }

    @Test
    void aGapLongerThanTheLimitIsTimeTheLoomWasNotTicked() {
        long gap = PowerLoomCatchUp.MAX_GAP + 1;
        Result r = PowerLoomCatchUp.advance(LOOM, 0, gap);
        assertThat(r.pushes()).isZero();
        assertThat(r.state().ticker()).isZero();
        // a waiting step still counts: 20 ticks passed
        PowerLoomState pending = PowerLoomCatchUp.advance(LOOM, 0, 16).state();
        assertThat(PowerLoomCatchUp.advance(pending, 16, 16 + gap).state().progress()).isEqualTo(1);
        // the limit itself is stepped
        assertThat(PowerLoomCatchUp.advance(LOOM, 0, PowerLoomCatchUp.MAX_GAP).pushes()).isGreaterThan(0);
    }

    @Test
    void noElapsedTimeChangesNothing() {
        assertThat(PowerLoomCatchUp.advance(LOOM, 50, 50)).isEqualTo(new Result(LOOM, 0, -1));
        assertThat(PowerLoomCatchUp.advance(LOOM, 50, 10)).isEqualTo(new Result(LOOM, 0, -1));
    }

    @Test
    void theCodecRoundTripsEveryField() {
        PowerLoomState s = new PowerLoomState(2, 123456789012L, true, 0.5f, -0.07f, 3, 4, 4, 9);
        assertThat(PowerLoomState.CODEC.decode(PowerLoomState.CODEC.encode(s))).isEqualTo(s);
        assertThat(PowerLoomState.CODEC.decode(PowerLoomState.CODEC.encode(PowerLoomState.FRESH))).isEqualTo(PowerLoomState.FRESH);
    }

    @Test
    void theSinkFaceIsTheClockwiseSideOfTheFacing() {
        assertThat(PowerLoomFacing.sinkFace("north")).hasValue(Direction.EAST);
        assertThat(PowerLoomFacing.sinkFace("east")).hasValue(Direction.SOUTH);
        assertThat(PowerLoomFacing.sinkFace("south")).hasValue(Direction.WEST);
        assertThat(PowerLoomFacing.sinkFace("west")).hasValue(Direction.NORTH);
        assertThat(PowerLoomFacing.sinkFace("up")).isEmpty();
        assertThat(PowerLoomFacing.sinkFace(null)).isEmpty();
    }

    @Test
    void activeMeansTurningOrWaiting() {
        assertThat(PowerLoomState.FRESH.active()).isFalse();
        assertThat(LOOM.active()).isTrue();
        assertThat(PowerLoomCatchUp.advance(LOOM, 0, 16).state().withSpeed(0f).active()).isTrue();
    }
}
