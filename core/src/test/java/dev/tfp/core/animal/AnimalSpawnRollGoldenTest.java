/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.animal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.livestock.AnimalState;
import dev.tfp.core.livestock.LivestockAge;
import dev.tfp.core.livestock.LivestockRules;
import dev.tfp.core.livestock.LivestockSpecies;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * M5-ENT-01: TFC's {@code initCommonAnimalData} + {@code getRandomGrowth} with scripted draws. Each script lists the
 * draws in TFC's order (nextBoolean, nextFloat, nextInt(growth bound), nextInt(15)); the test also checks the bounds TFC
 * passes to {@code nextInt}.
 */
class AnimalSpawnRollGoldenTest {
    private static final long NOW = 10_000_000L;
    private static final int DAY = 28_800; // PLAYER_TICKS_IN_DEFAULT_DAY

    /** Returns the scripted values in order and records every nextInt bound. */
    private static final class Script implements AnimalSpawnRoll.Draws {
        private final Deque<Object> values = new ArrayDeque<>();
        final List<Integer> bounds = new ArrayList<>();

        Script(Object... v) {
            values.addAll(List.of(v));
        }

        @Override
        public boolean nextBoolean() {
            return (Boolean) values.removeFirst();
        }

        @Override
        public float nextFloat() {
            return (Float) values.removeFirst();
        }

        @Override
        public int nextInt(int bound) {
            bounds.add(bound);
            int v = (Integer) values.removeFirst();
            assertThat(v).isBetween(0, bound - 1);
            return v;
        }

        void assertDone() {
            assertThat(values).as("unused draws").isEmpty();
        }
    }

    @Test
    void cowAtExactlyFivePercentIsAdult() {
        Script s = new Script(true, 0.05f, 7, 0);
        AnimalSpawnRoll.Rolled r = AnimalSpawnRoll.roll(s, LivestockSpecies.COW.adulthoodDays(), NOW);
        s.assertDone();
        assertThat(s.bounds).containsExactly(192, 15);
        assertThat(r.state().birthTick()).isEqualTo(NOW - (192L + 7) * DAY);
        assertThat(r.state().male()).isTrue();
        assertThat(r.geneticSize()).isEqualTo(4);
        assertThat(r.state()).isEqualTo(AnimalState.spawned(true, NOW - 199L * DAY, NOW));
        assertThat(LivestockRules.ageAt(r.state(), LivestockSpecies.COW, NOW)).isEqualTo(LivestockAge.ADULT);
    }

    @Test
    void cowJustBelowFivePercentIsAChildOfAtMostTenDays() {
        Script s = new Script(false, Math.nextDown(0.05f), 10 * DAY - 1, 14);
        AnimalSpawnRoll.Rolled r = AnimalSpawnRoll.roll(s, LivestockSpecies.COW.adulthoodDays(), NOW);
        s.assertDone();
        assertThat(s.bounds).containsExactly(10 * DAY, 15);
        assertThat(r.state().birthTick()).isEqualTo(NOW - (10L * DAY - 1));
        assertThat(r.state().male()).isFalse();
        assertThat(r.geneticSize()).isEqualTo(18);
        assertThat(LivestockRules.ageAt(r.state(), LivestockSpecies.COW, NOW)).isEqualTo(LivestockAge.CHILD);
    }

    @Test
    void chickenBabyBoundIsTenDaysAndSheepAdultSpansTwiceAdulthood() {
        Script chick = new Script(true, 0f, 0, 5);
        AnimalSpawnRoll.Rolled c = AnimalSpawnRoll.roll(chick, LivestockSpecies.CHICKEN.adulthoodDays(), NOW);
        assertThat(chick.bounds).containsExactly(10 * DAY, 15);
        assertThat(c.state().birthTick()).isEqualTo(NOW);
        assertThat(c.geneticSize()).isEqualTo(9);

        Script sheep = new Script(true, 0.99f, 55, 3);
        AnimalSpawnRoll.Rolled sh = AnimalSpawnRoll.roll(sheep, LivestockSpecies.SHEEP.adulthoodDays(), NOW);
        assertThat(sheep.bounds).containsExactly(56, 15);
        assertThat(sh.state().birthTick()).isEqualTo(NOW - 111L * DAY);
    }

    @Test
    void oneDayToAdultClampsBothBranchesToOne() {
        Script baby = new Script(true, 0.01f, DAY - 1, 0);
        assertThat(AnimalSpawnRoll.roll(baby, 1, NOW).state().birthTick()).isEqualTo(NOW - (DAY - 1));
        assertThat(baby.bounds).containsExactly(DAY, 15);

        Script adult = new Script(true, 0.5f, 0, 0);
        assertThat(AnimalSpawnRoll.roll(adult, 1, NOW).state().birthTick()).isEqualTo(NOW - DAY);
        assertThat(adult.bounds).containsExactly(1, 15);
    }

    @Test
    void spawnedStateIsTfcInitCommonAnimalData() {
        AnimalState s = AnimalSpawnRoll.roll(new Script(true, 0.5f, 3, 2), 24, NOW).state();
        assertThat(s.familiarity()).isZero();
        assertThat(s.uses()).isZero();
        assertThat(s.oldTick()).isEqualTo(AnimalState.NO_OLD_TICK);
        assertThat(s.fertilized()).isFalse();
        assertThat(s.lastFedTick()).isEqualTo(AnimalState.NEVER);
    }

    @Test
    void hintsForceTheBranchWithoutDrawingTheFloat() {
        Script child = new Script(true, 5 * DAY, 0);
        AnimalSpawnRoll.Rolled c = AnimalSpawnRoll.roll(child, 192, NOW, AnimalSpawnRoll.AgeHint.CHILD, false);
        child.assertDone();
        assertThat(c.state().male()).isFalse();
        assertThat(c.state().birthTick()).isEqualTo(NOW - 5L * DAY);

        Script old = new Script(false, 0, 0);
        AnimalSpawnRoll.Rolled o = AnimalSpawnRoll.roll(old, 192, NOW, AnimalSpawnRoll.AgeHint.OLD, null);
        old.assertDone();
        assertThat(LivestockRules.ageAt(o.state(), LivestockSpecies.COW, NOW)).isEqualTo(LivestockAge.OLD);
    }
}
