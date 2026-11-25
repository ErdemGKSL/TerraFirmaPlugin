/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.wildraid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/**
 * PAR-FAUNA-24: the rules of the fox berry goal, the rabbit garden raid and the armadillo scutes against TFC @ c3b4d9536
 * ({@code TFCFox.TFCFoxEatBerriesGoal}, {@code TFCRabbit.RaidGardenGoal}, {@code TFCArmadillo}). The constants are TFC's
 * source constants; the search order is vanilla's {@code MoveToBlockGoal.findNearestBlock}, worked out by hand for the first
 * rings.
 */
class RaidRulesGoldenTest {
    @Test
    void foxGoalConstantsAndGates() {
        assertThat(FoxBerry.SPEED).isEqualTo(1.2);
        assertThat(FoxBerry.RANGE).isEqualTo(12);
        assertThat(FoxBerry.VERTICAL).isEqualTo(1);
        assertThat(FoxBerry.canStart(true, false)).isTrue();
        assertThat(FoxBerry.canStart(false, false)).as("a fox with an item in its mouth").isFalse();
        assertThat(FoxBerry.canStart(true, true)).as("asleep").isFalse();
        assertThat(FoxBerry.mayPick(true, true, true)).isTrue();
        assertThat(FoxBerry.mayPick(false, true, true)).as("mob griefing off").isFalse();
        assertThat(FoxBerry.mayPick(true, false, true)).as("not fruiting").isFalse();
        assertThat(FoxBerry.mayPick(true, true, false)).as("mouth full").isFalse();
        assertThat(FoxBerry.searchDelay(0)).isEqualTo(200);
        assertThat(FoxBerry.searchDelay(199)).isEqualTo(399);
        assertThatThrownBy(() -> FoxBerry.searchDelay(200)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void searchOrderIsVanillasForTheFirstRings() {
        List<RaidSearch.Offset> o = RaidSearch.offsets(3, 1);
        // k = 0 (dy -1): ring 0, then ring 1 as i = 0, 1, -1 with j = +-1 for the middle column and 0, 1, -1 for the sides
        assertThat(o.subList(0, 9)).containsExactly(
            new RaidSearch.Offset(0, -1, 0),
            new RaidSearch.Offset(0, -1, 1), new RaidSearch.Offset(0, -1, -1),
            new RaidSearch.Offset(1, -1, 0), new RaidSearch.Offset(1, -1, 1), new RaidSearch.Offset(1, -1, -1),
            new RaidSearch.Offset(-1, -1, 0), new RaidSearch.Offset(-1, -1, 1), new RaidSearch.Offset(-1, -1, -1));
    }

    @Test
    void everyCellOnceAndTheVerticalLevelsInVanillaOrder() {
        for (int[] c : new int[][] {{FoxBerry.RANGE, FoxBerry.VERTICAL, 529}, {RabbitRaid.RANGE, RabbitRaid.VERTICAL, 961}}) {
            List<RaidSearch.Offset> o = RaidSearch.offsets(c[0], c[1]);
            assertThat(o).hasSize(3 * c[2]).doesNotHaveDuplicates();
            // k = 0, 1, -1 give dy = -1, 0, -2: the block under the animal, its own block, two below
            assertThat(o.get(0).dy()).isEqualTo(-1);
            assertThat(o.get(c[2]).dy()).isEqualTo(0);
            assertThat(o.get(2 * c[2]).dy()).isEqualTo(-2);
            int reach = c[0] - 1;
            assertThat(o).allMatch(x -> Math.abs(x.dx()) <= reach && Math.abs(x.dz()) <= reach);
        }
        assertThat(RaidSearch.offsets(1, 0)).containsExactly(new RaidSearch.Offset(0, -1, 0));
    }

    @Test
    void rabbitConstants() {
        assertThat(RabbitRaid.SPEED).isEqualTo(0.7);
        assertThat(RabbitRaid.RANGE).isEqualTo(16);
        assertThat(RabbitRaid.VERTICAL).isEqualTo(1);
        assertThat(RabbitRaid.MORE_CARROT_TICKS).isEqualTo(40);
        assertThat(RabbitRaid.RESTART_DELAY).isEqualTo(10);
        assertThat(RabbitRaid.validTarget(true, true, false, true)).isTrue();
        assertThat(RabbitRaid.validTarget(false, true, false, true)).as("no farmland").isFalse();
        assertThat(RabbitRaid.validTarget(true, false, false, true)).as("not hungry").isFalse();
        assertThat(RabbitRaid.validTarget(true, true, true, true)).as("already found one").isFalse();
        assertThat(RabbitRaid.validTarget(true, true, false, false)).as("no crop above").isFalse();
    }

    @Test
    void theLazyCountdownEqualsTheSteppedOne() {
        for (long raid = 0; raid < 2_000; raid++) {
            // TFC customServerAiStep: if (moreCarrotTicks > 0) { moreCarrotTicks -= nextInt(3); if (< 0) = 0 }
            SplittableRandom random = new SplittableRandom(raid);
            int moreCarrotTicks = RabbitRaid.MORE_CARROT_TICKS;
            int steps = 0;
            while (moreCarrotTicks > 0) {
                moreCarrotTicks -= random.nextInt(3);
                steps++;
            }
            assertThat(RabbitRaid.stepsUntilHungry(raid)).as("raid at %d", raid).isEqualTo(steps);
            assertThat(RabbitRaid.wantsMoreFood(raid, raid + steps - 1)).isFalse();
            assertThat(RabbitRaid.wantsMoreFood(raid, raid + steps)).isTrue();
        }
    }

    @Test
    void theCountdownRunsTwentyToAFewScoreStepsWithAMeanOfAboutForty() {
        long total = 0;
        int min = Integer.MAX_VALUE;
        int n = 5_000;
        for (long raid = 0; raid < n; raid++) {
            int s = RabbitRaid.stepsUntilHungry(raid);
            min = Math.min(min, s);
            total += s;
        }
        assertThat(min).isGreaterThanOrEqualTo(20);
        assertThat(total / (double) n).isBetween(39.0, 42.0);
    }

    @Test
    void aRabbitThatNeverRaidedIsAlwaysHungry() {
        assertThat(RabbitRaid.wantsMoreFood(RabbitRaid.NEVER, 0)).isTrue();
        assertThat(RabbitRaid.wantsMoreFood(RabbitRaid.NEVER, Long.MAX_VALUE)).isTrue();
        assertThat(RabbitRaid.wantsMoreFood(1_000, 1_000)).as("just raided").isFalse();
    }

    @Test
    void armadilloScuteTimer() {
        assertThat(ArmadilloScutes.PRODUCE_TICKS).as("30 * PLAYER_TICKS_IN_DEFAULT_HOUR (1200)").isEqualTo(36_000);
        assertThat(ArmadilloScutes.SPAWN_DRAW_BOUND).as("CALENDAR_TICKS_IN_HOUR * 20").isEqualTo(20_000);
        assertThat(ArmadilloScutes.cooldown(1_000, 20_000)).isEqualTo(17_000);
        assertThat(ArmadilloScutes.cooldown(1_000, 37_000)).isZero();
        assertThat(ArmadilloScutes.cooldown(1_000, 99_000)).as("never negative").isZero();
        assertThat(ArmadilloScutes.due(true, 1_000, 36_999)).isFalse();
        assertThat(ArmadilloScutes.due(true, 1_000, 37_000)).isTrue();
        assertThat(ArmadilloScutes.due(false, 1_000, 99_000)).as("a baby gives none").isFalse();
        assertThat(ArmadilloScutes.due(true, ArmadilloScutes.UNSET, 36_000)).as("a bred armadillo of day 0").isTrue();
        assertThat(ArmadilloScutes.spawnProducedTick(50_000, 0)).isEqualTo(50_000);
        assertThat(ArmadilloScutes.spawnProducedTick(50_000, 19_999)).isEqualTo(30_001);
        assertThatThrownBy(() -> ArmadilloScutes.spawnProducedTick(50_000, 20_000)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aSpawnedArmadilloDropsItsFirstScuteWithinThirtyThousandTicks() {
        // produced = now - draw, draw in [0, 20000): the first scute is due 36000 - draw .. 16000 + 1 ticks after the spawn
        long now = 1_000_000;
        assertThat(ArmadilloScutes.cooldown(ArmadilloScutes.spawnProducedTick(now, 0), now)).isEqualTo(36_000);
        assertThat(ArmadilloScutes.cooldown(ArmadilloScutes.spawnProducedTick(now, 19_999), now)).isEqualTo(16_001);
    }
}
