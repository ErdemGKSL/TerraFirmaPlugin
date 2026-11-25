/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.quern.powered;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PoweredQuernTest {
    /** TFC creative motor at step 8: 30 rpm = 2 pi * 30 / 1200 rad per tick. */
    private static final float RPM30 = (float) (2 * Math.PI * 30 / 1200);

    /** TFC's own arithmetic, transcribed: recipeTimer = 90; each tick timer -= speed * (90 / TWO_PI); finish when <= 0. */
    private static int tfcTicks(float speed) {
        float timer = 90;
        float per = Math.abs(speed) * (90 / 6.2831855f);
        int n = 0;
        while (timer > 0) {
            timer -= per;
            n++;
        }
        return n;
    }

    @Test
    void grindTicksEqualTfcsPerTickSubtraction() {
        for (float rpm : new float[] {1f, 3.75f, 7.5f, 15f, 22.5f, 30f, 37.5f, 60f}) {
            float speed = (float) (2 * Math.PI * rpm / 1200);
            assertThat(PoweredQuern.grindTicks(speed)).as("%s rpm", rpm).isEqualTo(tfcTicks(speed));
            assertThat(PoweredQuern.grindTicks(-speed)).as("reversed %s rpm", rpm).isEqualTo(tfcTicks(speed));
        }
    }

    @Test
    void thirtyRpmGrindsInAboutFortyTicks() {
        // 90 units at 2.25 per tick
        assertThat(PoweredQuern.grindTicks(RPM30)).isBetween(40, 41);
    }

    @Test
    void manualRateIsNinetyTicks() {
        assertThat(PoweredQuern.ticksFor(90, PoweredQuern.MANUAL_PER_TICK)).isEqualTo(90);
    }

    @Test
    void aStandingNetworkNeverFinishes() {
        assertThat(PoweredQuern.grindTicks(0f)).isEqualTo(-1);
        assertThat(PoweredQuern.ticksFor(90, 0f)).isEqualTo(-1);
        assertThat(PoweredQuern.ticksFor(90, Float.NaN)).isEqualTo(-1);
        assertThat(PoweredQuern.ticksFor(90, 1e-9f)).isEqualTo(-1);
        assertThat(PoweredQuern.ticksFor(0, 1f)).isZero();
    }

    @Test
    void retargetKeepsTheTimerAndChangesTheRate() {
        // 90 units at 2.25: planned finish 40. At tick 20 the timer has 45 left; at twice the rate that is 10 more ticks.
        assertThat(PoweredQuern.retarget(40, 20, 2.25f, 4.5f)).isEqualTo(30);
        // the axle lost: carries on by hand at 1 per tick: 45 more ticks
        assertThat(PoweredQuern.retarget(40, 20, 2.25f, PoweredQuern.MANUAL_PER_TICK)).isEqualTo(65);
        assertThat(PoweredQuern.retarget(40, 20, 2.25f, 0f)).isEqualTo(-1);
    }

    @Test
    void storedStartMakesTheManualQuernFinishOnTime() {
        // QuernState.finishTick() = grindStart + 90
        assertThat(PoweredQuern.startFor(140) + PoweredQuern.GRIND_UNITS).isEqualTo(140);
    }

    @Test
    void catchUpChainsWithinTheWindowAndRestartsFromNowBeyondIt() {
        assertThat(PoweredQuern.nextStart(100, 100)).isEqualTo(100);
        assertThat(PoweredQuern.nextStart(100, 109)).isEqualTo(100);
        assertThat(PoweredQuern.nextStart(100, 100 + PoweredQuern.CHAIN_WINDOW)).isEqualTo(100);
        assertThat(PoweredQuern.nextStart(100, 100 + PoweredQuern.CHAIN_WINDOW + 1)).isEqualTo(100 + PoweredQuern.CHAIN_WINDOW + 1);
        // never before the finish: a calendar that ran slower than the server
        assertThat(PoweredQuern.nextStart(100, 90)).isEqualTo(90);
    }

    @Test
    void connectedNeedsARotationAndAHandstone() {
        assertThat(PoweredQuern.connected(true, true)).isTrue();
        assertThat(PoweredQuern.connected(true, false)).isFalse();
        assertThat(PoweredQuern.connected(false, true)).isFalse();
    }
}
