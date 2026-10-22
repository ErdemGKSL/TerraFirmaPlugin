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
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * M5-ENT-01 lazy-vs-stepped: the stage read from the record at any tick equals the stage after running the 20-tick
 * check ({@link AnimalCheck#decide}) on every check tick of the same span, and the check reports a stage change exactly
 * when the stepped stage changes.
 */
class AnimalAgeLazyTest {
    private static final long DAY = LivestockRules.PLAYER_TICKS_IN_DEFAULT_DAY;

    @Test
    void lazyStageEqualsSteppedStage() {
        Random rnd = new Random(42);
        String[] species = {"tfc:cow", "tfc:chicken", "tfc:sheep", "tfc:rabbit", "tfc:deer"};
        for (int i = 0; i < 200; i++) {
            String id = species[rnd.nextInt(species.length)];
            long start = rnd.nextInt(1_000_000);
            long birth = start - rnd.nextInt((int) (20 * DAY));
            long oldTick = rnd.nextBoolean() ? AnimalState.NO_OLD_TICK : start + rnd.nextInt((int) (40 * DAY));
            AnimalState state = AnimalState.spawned(rnd.nextBoolean(), birth, start).withOldTick(oldTick);
            AnimalRecord record = new AnimalRecord(id, state, 4 + rnd.nextInt(15), "");
            long end = start + rnd.nextInt((int) (60 * DAY));

            AnimalRecord stepped = record;
            LivestockAge applied = null;
            int changes = 0;
            for (long t = start; t <= end; t += AnimalCheck.PERIOD_TICKS) {
                AnimalCheck.Decision d = AnimalCheck.decide(stepped, t, 0, applied);
                if (d.stageChanged()) {
                    changes++;
                }
                assertThat(d.age()).as("%s at %d", id, t).isEqualTo(AnimalCheck.ageAt(record, t));
                stepped = d.record();
                applied = d.age();
            }
            assertThat(stepped).as("no uses, so the check never writes").isEqualTo(record);
            assertThat(changes).isBetween(1, 3);
            // One lazy read after the whole gap gives the same stage as the last stepped check.
            long last = start + (end - start) / AnimalCheck.PERIOD_TICKS * AnimalCheck.PERIOD_TICKS;
            assertThat(AnimalCheck.decide(record, last, 0, null).age()).isEqualTo(applied);
        }
    }

    @Test
    void wildAdultsStayAdult() {
        AnimalRecord deer = new AnimalRecord("tfc:deer", AnimalState.spawned(true, 0, 0), AnimalScale.NO_SIZE, "");
        assertThat(AnimalCheck.ageAt(deer, 0)).isEqualTo(LivestockAge.ADULT);
        assertThat(AnimalCheck.ageAt(deer, 1_000L * DAY)).isEqualTo(LivestockAge.ADULT);
        assertThat(AnimalCheck.decide(deer, 1_000L * DAY, 0, LivestockAge.ADULT).recordChanged()).isFalse();
    }

    @Test
    void wildBabyIsAChildUntilItsGrowTickAndTheCheckThenClearsTheFlag() {
        long grow = 12_000L;
        AnimalRecord fawn = new AnimalRecord("tfc:deer", AnimalState.spawned(false, 0, 0), AnimalScale.NO_SIZE, "",
                true, grow);
        AnimalCheck.Decision before = AnimalCheck.decide(fawn, grow - 1, 0, null);
        assertThat(before.age()).isEqualTo(LivestockAge.CHILD);
        assertThat(before.recordChanged()).isFalse();
        assertThat(before.record()).isSameAs(fawn);

        AnimalCheck.Decision at = AnimalCheck.decide(fawn, grow, 0, LivestockAge.CHILD);
        assertThat(at.age()).isEqualTo(LivestockAge.ADULT);
        assertThat(at.recordChanged()).isTrue();
        assertThat(at.stageChanged()).isTrue();
        assertThat(at.record().baby()).isFalse();
        assertThat(at.record().growTick()).isEqualTo(AnimalRecord.NO_GROW_TICK);
        // The child factor: half size while a baby, full size after.
        assertThat(AnimalScale.modelMultiplier(fawn.geneticSize(), before.age())).isEqualTo(0.5f);
        assertThat(AnimalScale.modelMultiplier(fawn.geneticSize(), at.age())).isEqualTo(1f);
    }

    @Test
    void wildBabyCodecRoundTripsAndVersionOneReadsAsNotABaby() {
        AnimalRecord fawn = new AnimalRecord("tfc:deer", AnimalState.spawned(false, 5, 5), AnimalScale.NO_SIZE, "",
                true, 8_000L);
        assertThat(AnimalRecord.fromMap(fawn.toMap())).contains(fawn);

        java.util.Map<String, Object> v1 = new java.util.LinkedHashMap<>(fawn.toMap());
        v1.put(AnimalRecord.V, 1);
        v1.remove(AnimalRecord.BABY);
        v1.remove(AnimalRecord.GROW_TICK);
        assertThat(AnimalRecord.fromMap(v1)).get().satisfies(r -> {
            assertThat(r.baby()).isFalse();
            assertThat(r.growTick()).isEqualTo(AnimalRecord.NO_GROW_TICK);
        });
    }

    @Test
    void oldAgeMarkIsWrittenOnceAndThenReadLazily() {
        LivestockSpecies cow = LivestockSpecies.COW;
        long now = 400 * DAY;
        AnimalState worn = AnimalState.spawned(false, 0, 0).withUses(cow.usesToElderly() + 1);
        AnimalRecord record = new AnimalRecord("tfc:cow", worn, 16, "");

        AnimalCheck.Decision first = AnimalCheck.decide(record, now, 2, LivestockAge.ADULT);
        assertThat(first.recordChanged()).isTrue();
        assertThat(first.stageChanged()).isFalse();
        assertThat(first.record().state().oldTick()).isEqualTo(now + 3 * LivestockRules.TICKS_IN_DAY);

        AnimalCheck.Decision again = AnimalCheck.decide(first.record(), now + 20, 4, first.age());
        assertThat(again.recordChanged()).isFalse();
        assertThat(again.record()).isSameAs(first.record());

        long later = first.record().state().oldTick() + 1;
        AnimalCheck.Decision old = AnimalCheck.decide(first.record(), later, 0, LivestockAge.ADULT);
        assertThat(old.age()).isEqualTo(LivestockAge.OLD);
        assertThat(old.stageChanged()).isTrue();
    }
}
